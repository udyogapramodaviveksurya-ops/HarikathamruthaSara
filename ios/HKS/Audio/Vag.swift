import Foundation
import Compression
import Observation

enum DownloadState: Equatable { case running(Double), failed(String) }

/// One line of a padya: the half-padya take it is sung in, its range in seconds and each word's start/end.
struct VagLine { let file: URL; let start: Double; let end: Double; let words: [(Double, Double)] }

/// Downloads run in a background URLSession, so they carry on when the phone is locked or the app is closed, over Wi-Fi
/// or mobile data, and resume by themselves after a dropped connection.
final class BackgroundDownloads: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    static let shared = BackgroundDownloads()
    static let identifier = "com.prathosh.hks.downloads"
    var finishEvents: (() -> Void)?
    var onProgress: (@MainActor (String, Double) -> Void)?
    var onFinished: (@MainActor (String, URL?, String?) -> Void)?

    private(set) lazy var session: URLSession = {
        let c = URLSessionConfiguration.background(withIdentifier: Self.identifier)
        c.isDiscretionary = false
        c.sessionSendsLaunchEvents = true
        c.allowsCellularAccess = true
        c.allowsExpensiveNetworkAccess = true
        c.allowsConstrainedNetworkAccess = true
        c.waitsForConnectivity = true
        c.httpMaximumConnectionsPerHost = 3
        c.timeoutIntervalForResource = 60 * 60 * 24
        return URLSession(configuration: c, delegate: self, delegateQueue: nil)
    }()

    func start(_ url: URL, tag: String) {
        let t = session.downloadTask(with: url)
        t.taskDescription = tag
        t.resume()
    }

    func runningTags() async -> [String] {
        await session.allTasks.filter { $0.state == .running || $0.state == .suspended }.compactMap(\.taskDescription)
    }

    func urlSession(_ s: URLSession, downloadTask t: URLSessionDownloadTask, didWriteData b: Int64, totalBytesWritten w: Int64, totalBytesExpectedToWrite e: Int64) {
        guard e > 0, let tag = t.taskDescription, let cb = onProgress else { return }
        let f = Double(w) / Double(e)
        Task { @MainActor in cb(tag, f) }
    }

    func urlSession(_ s: URLSession, downloadTask t: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard let tag = t.taskDescription else { return }
        let code = (t.response as? HTTPURLResponse)?.statusCode ?? 0
        guard code == 200 else {
            let cb = onFinished
            Task { @MainActor in cb?(tag, nil, "Server said \(code)") }
            return
        }
        // the system deletes `location` when this returns
        let keep = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        do { try FileManager.default.moveItem(at: location, to: keep) } catch {
            let cb = onFinished
            Task { @MainActor in cb?(tag, nil, error.localizedDescription) }
            return
        }
        let cb = onFinished
        Task { @MainActor in cb?(tag, keep, nil) }
    }

    func urlSession(_ s: URLSession, task t: URLSessionTask, didCompleteWithError error: Error?) {
        guard let error, let tag = t.taskDescription else { return }
        let cb = onFinished, msg = error.localizedDescription
        Task { @MainActor in cb?(tag, nil, msg) }
    }

    func urlSessionDidFinishEvents(forBackgroundURLSession s: URLSession) {
        DispatchQueue.main.async { self.finishEvents?(); self.finishEvents = nil }
    }
}

/// Chanted audio made with Vāgdhenu: one zip per sandhi (takes + manifest.json) from the catalog.
@MainActor @Observable
final class VagLibrary {
    static let outdated = "outdated-pack", notOnServer = "not-on-server", packVersion = 3

    private(set) var downloads: [Int: DownloadState] = [:]
    private(set) var version = 0
    @ObservationIgnored var source: ((Int) async -> (URL, String)?) = { _ in nil }
    @ObservationIgnored var isBusy: (Int) -> Bool = { _ in false }
    @ObservationIgnored var onReplaced: (Int) -> Void = { _ in }
    @ObservationIgnored var onVersionChange: () -> Void = {}
    /// (sandhi id, succeeded) once a download ends.
    @ObservationIgnored var onDownloadEnded: (Int, Bool) -> Void = { _, _ in }
    @ObservationIgnored private let root = Paths.dir("vag")
    @ObservationIgnored private var manifests: [Int: [String: Any]?] = [:]
    @ObservationIgnored private var pending: [Int: (URL, String)] = [:]
    @ObservationIgnored private var updating: Set<Int> = []

    func handles(_ tag: String) -> Bool { tag.hasPrefix("pack:") }

    /// Picks up downloads the system kept going while the app was closed.
    func restore(tags: [String]) {
        for t in tags where handles(t) {
            let p = t.split(separator: ":")
            if p.count == 3, let id = Int(p[1]) { downloads[id] = .running(0) }
        }
    }

    func progress(tag: String, _ f: Double) {
        let p = tag.split(separator: ":")
        if p.count == 3, let id = Int(p[1]), case .running = downloads[id] { downloads[id] = .running(f) }
    }

    func finished(tag: String, file: URL?, error: String?) {
        let p = tag.split(separator: ":")
        guard p.count == 3, let id = Int(p[1]) else { return }
        let rev = String(p[2])
        let isUpdate = updating.remove(id) != nil
        guard let file else { fail(id, isUpdate, error ?? "Download failed. Check internet."); return }
        let tmp = root.appendingPathComponent(String(format: "%02d.tmp", id))
        Task.detached(priority: .utility) {
            let fm = FileManager.default
            defer { try? fm.removeItem(at: file) }
            do {
                try? fm.removeItem(at: tmp)
                try fm.createDirectory(at: tmp, withIntermediateDirectories: true)
                try Unzip.extractFlat(zip: file, to: tmp)
                let d = try Data(contentsOf: tmp.appendingPathComponent("manifest.json"))
                let m = try JSONSerialization.jsonObject(with: d) as? [String: Any]
                let ver = (m?["version"] as? NSNumber)?.intValue ?? 0
                if ver != VagLibrary.packVersion { throw NSError(domain: "HKS", code: 1, userInfo: [NSLocalizedDescriptionKey: VagLibrary.outdated]) }
                await MainActor.run {
                    self.downloads[id] = nil
                    if isUpdate && self.isBusy(id) { self.pending[id] = (tmp, rev) } else { self.install(id, tmp, rev) }
                    self.onDownloadEnded(id, true)
                }
            } catch {
                try? fm.removeItem(at: tmp)
                let msg = error.localizedDescription
                await MainActor.run { self.fail(id, isUpdate, msg) }
            }
        }
    }

    private func fail(_ id: Int, _ update: Bool, _ msg: String) {
        downloads[id] = update ? nil : .failed(msg)
        onDownloadEnded(id, false)
    }

    private func dir(_ id: Int) -> URL { root.appendingPathComponent(String(format: "%02d", id), isDirectory: true) }

    func isDownloaded(_ id: Int) -> Bool { (manifest(id)?["version"] as? NSNumber)?.intValue == Self.packVersion }
    func downloadedCount() -> Int { (1...33).filter { isDownloaded($0) }.count }

    private func manifest(_ id: Int) -> [String: Any]? {
        if let m = manifests[id] { return m }
        let m = (try? Data(contentsOf: dir(id).appendingPathComponent("manifest.json"))).flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
        manifests[id] = .some(m)
        return m
    }

    func line(_ id: Int, padya: Int, line: Int) -> VagLine? {
        let key = String(format: "%03d_%d", padya, line)
        guard let m = (manifest(id)?["lines"] as? [String: Any])?[key] as? [String: Any], let name = m["f"] as? String else { return nil }
        let f = dir(id).appendingPathComponent(name + ".m4a")
        guard FileManager.default.fileExists(atPath: f.path) else { return nil }
        let ws = (m["words"] as? [[Double]] ?? []).map { ($0[0], $0[1]) }
        return VagLine(file: f, start: (m["start"] as? NSNumber)?.doubleValue ?? 0, end: (m["end"] as? NSNumber)?.doubleValue ?? 0, words: ws)
    }

    func rev(_ id: Int) -> String {
        ((try? String(contentsOf: dir(id).appendingPathComponent("rev.txt"), encoding: .utf8)) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func applyPending() {
        for (id, p) in pending where !isBusy(id) { pending[id] = nil; install(id, p.0, p.1) }
    }

    private func install(_ id: Int, _ tmp: URL, _ rev: String) {
        try? rev.write(to: tmp.appendingPathComponent("rev.txt"), atomically: true, encoding: .utf8)
        let target = dir(id)
        try? FileManager.default.removeItem(at: target)
        try? FileManager.default.moveItem(at: tmp, to: target)
        manifests[id] = nil
        onReplaced(id)
        bump()
    }

    private func bump() { version += 1; onVersionChange() }

    func download(_ id: Int, update: Bool = false) {
        if case .running = downloads[id] { return }
        if !update && isDownloaded(id) { return }
        if update && pending[id] != nil { return }
        downloads[id] = .running(0)
        if update { updating.insert(id) }
        Task {
            guard let (url, rev) = await source(id) else { fail(id, update, Self.notOnServer); updating.remove(id); return }
            BackgroundDownloads.shared.start(url, tag: "pack:\(id):\(rev)")
        }
    }

    func downloadAll() { (1...33).forEach { download($0) } }
    func delete(_ id: Int) { try? FileManager.default.removeItem(at: dir(id)); manifests[id] = nil; bump() }
    func deleteAll() { (1...33).forEach(delete) }
}

/// Minimal reader for the server's flat zip files: stored or deflated entries, names reduced to their last component.
enum Unzip {
    enum E: Error { case bad }
    static func extractFlat(zip: URL, to dir: URL) throws {
        let d = try Data(contentsOf: zip, options: .mappedIfSafe)
        func u16(_ o: Int) -> Int { Int(d[d.startIndex + o]) | Int(d[d.startIndex + o + 1]) << 8 }
        func u32(_ o: Int) -> Int { u16(o) | u16(o + 2) << 16 }
        guard d.count >= 22 else { throw E.bad }
        var eocd = d.count - 22
        while eocd >= 0 && u32(eocd) != 0x06054b50 { eocd -= 1 }
        guard eocd >= 0 else { throw E.bad }
        var p = u32(eocd + 16)
        for _ in 0..<u16(eocd + 10) {
            guard u32(p) == 0x02014b50 else { throw E.bad }
            let method = u16(p + 10), csize = u32(p + 20), usize = u32(p + 24), nlen = u16(p + 28), xlen = u16(p + 30), clen = u16(p + 32), local = u32(p + 42)
            let nameData = d.subdata(in: p + 46..<p + 46 + nlen)
            p += 46 + nlen + xlen + clen
            guard let full = String(data: nameData, encoding: .utf8), !full.hasSuffix("/") else { continue }
            let name = (full as NSString).lastPathComponent   // flat files only; never trust paths inside the zip
            guard !name.isEmpty, name != ".." else { continue }
            let start = local + 30 + u16(local + 26) + u16(local + 28)
            let comp = d.subdata(in: start..<start + csize)
            let out: Data
            if method == 0 { out = comp }
            else if method == 8 {
                var buf = Data(count: usize)
                let n = buf.withUnsafeMutableBytes { (b: UnsafeMutableRawBufferPointer) in
                    comp.withUnsafeBytes { (c: UnsafeRawBufferPointer) in
                        compression_decode_buffer(b.bindMemory(to: UInt8.self).baseAddress!, usize, c.bindMemory(to: UInt8.self).baseAddress!, csize, nil, COMPRESSION_ZLIB)
                    }
                }
                guard n == usize else { throw E.bad }
                out = buf
            } else { throw E.bad }
            try out.write(to: dir.appendingPathComponent(name))
        }
    }
}
