import Foundation
import CryptoKit
import Observation
import FirebaseCore
import FirebaseFirestore

private let reportNoteMax = 2000

struct Published: Sendable { let rev: String; let url: String; let bytes: Int; let note: String }
struct Catalog: Sendable { let revision: Int; let text: Published?; let packs: [Int: Published]; let reports: String?; let ear: Published? }

struct SourceState: Equatable {
    var checking = false
    var lastCheck: Date = .distantPast
    var error: String?
    var revision = 0
    var textUpdate = false
    var packUpdates: [Int] = []
    var onServer: Set<Int> = []
    var updating = false
    var hasUpdates: Bool { textUpdate || !packUpdates.isEmpty }
}

/// The app's link to the source of truth: the catalog of corrected text and chant packs (see the server project).
@MainActor @Observable
final class Source {
    static let checkEvery: TimeInterval = 15 * 60
    private(set) var state = SourceState()
    @ObservationIgnored private var catalog: Catalog?
    @ObservationIgnored private let settings: SettingsStore
    @ObservationIgnored private let vag: VagLibrary
    @ObservationIgnored private let onTextChanged: () -> Void
    @ObservationIgnored private let cacheFile = Paths.support.appendingPathComponent("catalog.json")
    @ObservationIgnored var onUpdatesFound: ((SourceState) -> Void)?

    init(settings: SettingsStore, vag: VagLibrary, onTextChanged: @escaping () -> Void) {
        self.settings = settings; self.vag = vag; self.onTextChanged = onTextChanged
        if let d = try? Data(contentsOf: cacheFile) { catalog = Self.parse(d) }
        vag.source = { [weak self] n in
            guard let self else { return nil }
            let c: Catalog?
            if let have = self.catalog { c = have } else { c = await self.fetch() }
            guard let p = c?.packs[n], let u = self.url(p.url) else { return nil }
            return (u, p.rev)
        }
        vag.onVersionChange = { [weak self] in self?.refreshCompare() }
    }

    func earLocation() async -> (URL, String)? {
        let c: Catalog?
        if let have = catalog { c = have } else { c = await fetch() }
        guard let e = c?.ear, let u = url(e.url) else { return nil }
        return (u, e.rev)
    }

    private var catalogURL: URL? { URL(string: settings.value.server + "/catalog.json") }
    private func url(_ rel: String) -> URL? { catalogURL.flatMap { URL(string: rel, relativeTo: $0)?.absoluteURL } }

    private static func parse(_ d: Data) -> Catalog? {
        guard let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { return nil }
        func pub(_ p: [String: Any]?) -> Published? {
            guard let p, let r = p["rev"] as? String, let u = p["url"] as? String else { return nil }
            return Published(rev: r, url: u, bytes: (p["bytes"] as? NSNumber)?.intValue ?? 0, note: p["note"] as? String ?? "")
        }
        var packs: [Int: Published] = [:]
        for (k, v) in o["packs"] as? [String: [String: Any]] ?? [:] { if let n = Int(k), let p = pub(v) { packs[n] = p } }
        let rep = o["reports"] as? String
        return Catalog(revision: (o["revision"] as? NSNumber)?.intValue ?? 0, text: pub(o["text"] as? [String: Any]), packs: packs, reports: rep?.isEmpty == false ? rep : nil, ear: pub(o["ear"] as? [String: Any]))
    }

    private func get(_ u: URL) async throws -> Data {
        var r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        r.setValue("HKS-iOS", forHTTPHeaderField: "User-Agent")
        let (d, resp) = try await URLSession.shared.data(for: r)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard code == 200 else { throw NSError(domain: "HKS", code: code, userInfo: [NSLocalizedDescriptionKey: "Server said \(code)"]) }
        return d
    }

    private func fetch() async -> Catalog? {
        guard let u = catalogURL else { return nil }
        do {
            let d = try await get(u)
            guard let c = Self.parse(d) else { throw TextError.empty }
            catalog = c
            try? d.write(to: cacheFile, options: .atomic)
            return c
        } catch {
            state.error = error.localizedDescription
            return nil
        }
    }

    private func localTextRev() -> String {
        let dir = Paths.dir("text")
        if FileManager.default.fileExists(atPath: dir.appendingPathComponent("hks.json").path),
           let r = try? String(contentsOf: dir.appendingPathComponent("rev.txt"), encoding: .utf8) { return r.trimmingCharacters(in: .whitespacesAndNewlines) }
        return bundledRev
    }

    @ObservationIgnored private lazy var bundledRev: String = {
        guard let d = try? Data(contentsOf: Paths.asset("hks.json")) else { return "" }
        return SHA256.hash(data: d).map { String(format: "%02x", $0) }.joined()
    }()

    private func compare(_ c: Catalog) {
        state.revision = c.revision
        state.textUpdate = c.text != nil && c.text!.rev != localTextRev()
        state.packUpdates = c.packs.filter { vag.isDownloaded($0.key) && vag.rev($0.key) != $0.value.rev }.keys.sorted()
        state.onServer = Set(c.packs.keys)
    }
    private func refreshCompare() { if let c = catalog { compare(c) } }

    /// Looks at the server's catalog. Throttled unless `force`.
    func check(force: Bool = false) {
        if state.checking || (!force && Date().timeIntervalSince(state.lastCheck) < Self.checkEvery) { return }
        state.checking = true
        Task {
            let had = state.hasUpdates
            let c = await fetch()
            if let c { compare(c); state.error = nil }
            state.checking = false
            state.lastCheck = Date()
            if c != nil && state.hasUpdates && !had { onUpdatesFound?(state) }
            if c != nil && settings.value.autoUpdate && state.hasUpdates { updateAll() }
        }
    }

    func updateAll() {
        guard let c = catalog else { check(force: true); return }
        if state.updating { return }
        state.updating = true
        Task {
            do {
                if state.textUpdate, let t = c.text { try await installText(t) }
                for n in state.packUpdates { vag.download(n, update: true) }
            } catch { state.error = error.localizedDescription }
            compare(c)
            state.updating = false
        }
    }

    private func installText(_ p: Published) async throws {
        guard let u = url(p.url) else { return }
        let d = try await get(u)
        _ = try Library.parse(d)   // never install text the app can't read
        let dir = Paths.dir("text")
        try d.write(to: dir.appendingPathComponent("hks.json"), options: .atomic)
        try p.rev.write(to: dir.appendingPathComponent("rev.txt"), atomically: true, encoding: .utf8)
        onTextChanged()
    }

    /// Sends a problem report. Goes to the create-only Firestore `reports` collection; a LAN dev server gets it directly.
    func report(sandhi: Int, padya: Int, line: Int?, kind: String, note: String) async -> Bool {
        let app = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let c: Catalog?
        if let have = catalog { c = have } else { c = await fetch() }
        if let rel = c?.reports, let u = url(rel), let h = u.host, Self.isLocal(h) {
            var r = URLRequest(url: u, timeoutInterval: 20); r.httpMethod = "POST"
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
            r.httpBody = try? JSONSerialization.data(withJSONObject: ["sandhi": sandhi, "padya": padya, "line": line as Any? ?? NSNull(), "kind": kind, "note": note, "voice": "VAG", "app": app])
            let resp = try? await URLSession.shared.data(for: r)
            return ((resp?.1 as? HTTPURLResponse)?.statusCode ?? 0) / 100 == 2
        }
        guard FirebaseApp.app() != nil else { return false }
        let data: [String: Any] = ["sandhi": sandhi, "padya": padya, "line": line as Any? ?? NSNull(), "kind": kind, "note": String(note.prefix(reportNoteMax)), "voice": "VAG", "app": app, "at": FieldValue.serverTimestamp()]
        return await withTaskGroup(of: Bool.self) { g in
            g.addTask { @MainActor in
                do { _ = try await Firestore.firestore().collection("reports").addDocument(data: data); return true } catch { return false }
            }
            g.addTask { try? await Task.sleep(for: .seconds(15)); return false }
            let first = await g.next() ?? false
            g.cancelAll()
            return first
        }
    }

    private static func isLocal(_ h: String) -> Bool {
        if h == "localhost" || h.hasSuffix(".local") { return true }
        let p = h.split(separator: ".").compactMap { Int($0) }
        guard p.count == 4 else { return false }
        return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && (16...31).contains(p[1]))
    }
}
