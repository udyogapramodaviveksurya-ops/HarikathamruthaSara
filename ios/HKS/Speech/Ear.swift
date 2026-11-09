import Foundation
import AVFoundation
import CryptoKit
import Observation
import OnnxRuntimeBindings

enum EarState: Equatable { case missing, downloading(Double), failed(String), ready }

/// Runs the model off the main thread. One at a time; it is CPU-heavy.
private final class EarEngine: @unchecked Sendable {
    private let queue = DispatchQueue(label: "hks.ear", qos: .userInitiated)
    private var env: ORTEnv?
    private var session: ORTSession?

    func close() { queue.sync { session = nil } }

    func listen(_ samples: [Float], model: URL) async -> [[Float]]? {
        await withCheckedContinuation { cont in
            queue.async { cont.resume(returning: self.run(samples, model)) }
        }
    }

    private func run(_ samples: [Float], _ model: URL) -> [[Float]]? {
        guard samples.count >= 1600 else { return nil }
        do {
            if session == nil {
                let e = try env ?? ORTEnv(loggingLevel: .warning); env = e
                let o = try ORTSessionOptions()
                try o.setIntraOpNumThreads(Int32(min(max(ProcessInfo.processInfo.activeProcessorCount, 1), 4)))
                session = try ORTSession(env: e, modelPath: model.path, sessionOptions: o)
            }
            guard let s = session else { return nil }
            let data = samples.withUnsafeBytes { NSMutableData(bytes: $0.baseAddress, length: $0.count) }
            let input = try ORTValue(tensorData: data, elementType: .float, shape: [1, NSNumber(value: samples.count)])
            let names = try s.outputNames()
            let out = try s.run(withInputs: ["audio": input], outputNames: Set(names), runOptions: nil)
            guard let first = names.first, let v = out[first] else { return nil }
            let shape = try v.tensorTypeAndShapeInfo().shape.map(\.intValue)
            let raw = try v.tensorData() as Data
            guard shape.count == 3 else { return nil }
            let frames = shape[1], cols = shape[2]
            return raw.withUnsafeBytes { (b: UnsafeRawBufferPointer) in
                let f = b.bindMemory(to: Float.self)
                return (0..<frames).map { i in Array(f[(i * cols)..<((i + 1) * cols)]) }
            }
        } catch { return nil }
    }
}

/// The "ear": Kannada letter model, downloaded once (~122 MB), sha256-checked against the catalog, used offline.
@MainActor @Observable
final class EarStore {
    private(set) var state: EarState
    @ObservationIgnored var source: () async -> (URL, String)? = { nil }
    @ObservationIgnored var onDownloadEnded: (Bool) -> Void = { _ in }
    @ObservationIgnored private let dir: URL
    @ObservationIgnored private let file: URL
    @ObservationIgnored private let revFile: URL
    @ObservationIgnored private let engine = EarEngine()

    init() {
        dir = Paths.dir("ear")
        file = dir.appendingPathComponent("ear.onnx")
        revFile = dir.appendingPathComponent("rev.txt")
        let fm = FileManager.default
        state = fm.fileExists(atPath: file.path) && fm.fileExists(atPath: revFile.path) ? .ready : .missing
    }

    var isReady: Bool { state == .ready }
    var sizeMb: Int { ((try? FileManager.default.attributesOfItem(atPath: file.path)[.size] as? Int) ?? 0) / 1_000_000 }

    func handles(_ tag: String) -> Bool { tag.hasPrefix("ear:") }
    func restore(tags: [String]) { if tags.contains(where: handles) && !isReady { state = .downloading(0) } }
    func progress(_ f: Double) { if case .downloading = state { state = .downloading(f) } }

    func download() {
        if case .downloading = state { return }
        state = .downloading(0)
        Task {
            guard let (url, rev) = await source() else { state = .failed("The server has no listening model yet."); onDownloadEnded(false); return }
            BackgroundDownloads.shared.start(url, tag: "ear:\(rev)")
        }
    }

    func finished(tag: String, file tmp: URL?, error: String?) {
        guard let tmp else { state = .failed(error ?? "Download failed. Check internet."); onDownloadEnded(false); return }
        let rev = String(tag.dropFirst(4))
        Task.detached(priority: .utility) { [file, revFile, engine] in
            let sha = Self.sha256(tmp)
            let fm = FileManager.default
            guard sha == rev else {
                try? fm.removeItem(at: tmp)
                await MainActor.run { self.state = .failed("Download was damaged. Please try again."); self.onDownloadEnded(false) }
                return
            }
            engine.close()
            try? fm.removeItem(at: file)
            try? fm.moveItem(at: tmp, to: file)
            try? rev.write(to: revFile, atomically: true, encoding: .utf8)
            await MainActor.run { self.state = .ready; self.onDownloadEnded(true) }
        }
    }

    nonisolated private static func sha256(_ u: URL) -> String {
        guard let h = try? FileHandle(forReadingFrom: u) else { return "" }
        defer { try? h.close() }
        var s = SHA256()
        while autoreleasepool(invoking: {
            guard let d = try? h.read(upToCount: 1 << 20), !d.isEmpty else { return false }
            s.update(data: d); return true
        }) {}
        return s.finalize().map { String(format: "%02x", $0) }.joined()
    }

    func delete() {
        engine.close()
        try? FileManager.default.removeItem(at: file)
        try? FileManager.default.removeItem(at: revFile)
        state = .missing
    }

    func listen(_ samples: [Float]) async -> [[Float]]? {
        guard isReady else { return nil }
        return await engine.listen(samples, model: file)
    }
}

struct ListenState: Equatable {
    var listening = false, analyzing = false
    var committed = "", partial = ""
    var level: Float = 0
    var error: String?
    var text: String { [committed, partial].filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.joined(separator: " ") }
}

/// Listens with the microphone and the on-device ear. Same end-of-speech rules as Android's Listener.
@MainActor @Observable
final class Listener {
    private(set) var state = ListenState()
    @ObservationIgnored private(set) var lastLogProbs: [[Float]]?
    @ObservationIgnored var onSegmentDone: (() -> Void)?
    @ObservationIgnored var strs = Strs(.en)
    @ObservationIgnored private let ear: EarStore
    @ObservationIgnored private var attempt: Attempt?
    @ObservationIgnored private var generation = 0

    init(ear: EarStore) { self.ear = ear }

    func start(continuous: Bool, reset: Bool = true, maxSeconds: Int = 120, quickEnd: Bool = false) {
        attempt?.abort(); attempt = nil
        generation += 1
        lastLogProbs = nil
        if reset { state = ListenState(listening: true) } else { state.listening = true; state.analyzing = false; state.error = nil }
        guard ear.isReady else { reportError(strs.earMissing); return }
        let gen = generation
        Task {
            guard await Self.micAllowed() else { if gen == self.generation { self.reportError(self.strs.micNeeded) }; return }
            guard gen == self.generation else { return }
            AudioRoute.useVoice()
            AudioRoute.listening(true)
            let a = Attempt(continuous: continuous, maxSeconds: min(max(maxSeconds, 5), 120), quickEnd: quickEnd)
            a.onLevel = { [weak self] l in if gen == self?.generation { self?.state.level = l } }
            a.onSnapshot = { [weak self] snap in self?.live(snap, gen) }
            a.onEnd = { [weak self] r in self?.ended(r, gen) }
            self.attempt = a
            do { try a.begin() } catch { AudioRoute.listening(false); self.attempt = nil; self.reportError(self.strs.micBusy) }
        }
    }

    static func micAllowed() async -> Bool { await AVAudioApplication.requestRecordPermission() }
    static var micGranted: Bool { AVAudioApplication.shared.recordPermission == .granted }

    func stop() { attempt?.stop() }

    func cancel() {
        generation += 1
        attempt?.abort(); attempt = nil
        AudioRoute.listening(false)
        state.listening = false; state.analyzing = false; state.partial = ""; state.level = 0
    }

    @ObservationIgnored private var liveBusy = false
    private func live(_ snap: [Float], _ gen: Int) {
        guard !liveBusy else { return }
        liveBusy = true
        Task {
            let lp = await ear.listen(snap)
            liveBusy = false
            if gen == generation, state.listening, let lp { state.partial = Pronunciation.greedy(lp) }
        }
    }

    private func ended(_ samples: [Float]?, _ gen: Int) {
        guard gen == generation else { return }
        attempt = nil
        AudioRoute.listening(false)
        state.listening = false; state.level = 0; state.analyzing = samples != nil
        Task {
            var lp: [[Float]]?
            if let samples { lp = await ear.listen(samples) }
            guard gen == generation else { return }
            lastLogProbs = lp
            state.committed = lp.map(Pronunciation.greedy) ?? ""
            state.partial = ""; state.analyzing = false
            onSegmentDone?()
        }
    }

    private func reportError(_ m: String) {
        state.listening = false; state.analyzing = false; state.level = 0; state.partial = ""; state.error = m
        DispatchQueue.main.async { self.onSegmentDone?() }
    }
}

/// One microphone attempt. Audio arrives on a realtime thread, is resampled to 16 kHz mono and analysed on `queue`.
private final class Attempt: @unchecked Sendable {
    private let rate = 16000
    private let engine = AVAudioEngine()
    private let queue = DispatchQueue(label: "hks.listen", qos: .userInitiated)
    private let continuous: Bool, maxSeconds: Int, quickEnd: Bool

    var onLevel: (@MainActor (Float) -> Void)?
    var onSnapshot: (@MainActor ([Float]) -> Void)?
    var onEnd: (@MainActor ([Float]?) -> Void)?

    private var data: [Float] = []
    private var chunk: [Float] = []
    private var noise = -1.0, spoke = false, silentMs = 0, peak = 0.0, firstSpeech = -1, lastSpeech = -1
    private var lastLive = Date.distantPast
    private var finished = false
    private var aborted = false

    init(continuous: Bool, maxSeconds: Int, quickEnd: Bool) { self.continuous = continuous; self.maxSeconds = maxSeconds; self.quickEnd = quickEnd }

    func begin() throws {
        let input = engine.inputNode
        let hw = input.outputFormat(forBus: 0)
        guard hw.sampleRate > 0, let target = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: Double(rate), channels: 1, interleaved: false),
              let conv = AVAudioConverter(from: hw, to: target) else { throw TextError.empty }
        input.installTap(onBus: 0, bufferSize: 1600, format: hw) { [weak self] buf, _ in
            guard let self else { return }
            let cap = AVAudioFrameCount(Double(buf.frameLength) * target.sampleRate / hw.sampleRate) + 32
            guard let out = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: cap) else { return }
            var given = false
            var err: NSError?
            conv.convert(to: out, error: &err) { _, st in
                if given { st.pointee = .noDataNow; return nil }
                given = true; st.pointee = .haveData; return buf
            }
            guard err == nil, let c = out.floatChannelData?[0] else { return }
            let s = Array(UnsafeBufferPointer(start: c, count: Int(out.frameLength)))
            self.queue.async { self.feed(s) }
        }
        engine.prepare()
        try engine.start()
    }

    func stop() { queue.async { self.finish() } }

    func abort() {
        queue.sync { aborted = true; finished = true }
        teardown()
    }

    private func teardown() { engine.inputNode.removeTap(onBus: 0); engine.stop() }

    private func feed(_ s: [Float]) {
        guard !finished else { return }
        chunk += s
        while chunk.count >= rate / 10, !finished {
            let c = Array(chunk.prefix(rate / 10))
            chunk.removeFirst(rate / 10)
            process(c)
        }
    }

    private func process(_ c: [Float]) {
        let r = c.count
        data += c
        let n = data.count
        let sum = c.reduce(0.0) { $0 + Double($1 * $1) }
        let rms = sqrt(sum / Double(r))
        if rms > 1e-4 {
            if noise < 0 { noise = rms }
            else if n <= rate * 3 / 10 { noise = (noise + rms) / 2 }
            else if rms < noise { noise = rms }
            else { noise *= 1.01 }
        }
        let speech = rms > max(0.002, noise * 3, quickEnd && spoke ? peak * 0.12 : 0)
        if speech { peak = max(peak, rms) }
        let l = Float(min(max((20 * log10(max(rms, 1e-5)) + 55) / 40, 0), 1))
        let cb = onLevel
        Task { @MainActor in cb?(l) }
        let endAfterMs = continuous ? 5000 : quickEnd ? 1100 : 2500
        if speech { spoke = true; silentMs = 0; if firstSpeech < 0 { firstSpeech = n - r }; lastSpeech = n }
        else if spoke { silentMs += r * 1000 / rate; if silentMs >= endAfterMs { finish(); return } }
        else if n > rate * 12 { finish(); return }
        if n >= rate * maxSeconds { finish(); return }
        if !quickEnd, n > rate, Date().timeIntervalSince(lastLive) > 1.5 {
            lastLive = Date()
            let snap = Array(data[(firstSpeech < 0 ? 0 : max(0, firstSpeech - rate * 3 / 10))...])
            let cb = onSnapshot
            Task { @MainActor in cb?(snap) }
        }
    }

    private func finish() {
        guard !finished else { return }
        finished = true
        teardown()
        let n = data.count
        var result: [Float]?
        if n > rate / 2 {
            var a = spoke ? max(0, firstSpeech - rate * 3 / 10) : 0
            var b = spoke ? min(n, lastSpeech + rate * 3 / 10) : n
            if quickEnd, let (x, y) = tightSpan(a, b) { a = x; b = y }
            result = Array(data[a..<b])
        }
        let cb = onEnd
        Task { @MainActor in cb?(result) }
    }

    /// The part louder than 8% of its loudest 20 ms, with 0.15 s around it; nil if too short.
    private func tightSpan(_ a: Int, _ b: Int) -> (Int, Int)? {
        let win = rate / 50
        guard b - win > a else { return nil }
        let rms: [Double] = stride(from: a, to: b - win, by: win).map { i in
            var s = 0.0; for k in i..<i + win { s += Double(data[k] * data[k]) }; return sqrt(s / Double(win))
        }
        guard let top = rms.max() else { return nil }
        let loud = rms.indices.filter { rms[$0] > top * 0.08 }
        guard let f = loud.first, let l = loud.last else { return nil }
        let x = max(a, a + f * win - rate * 15 / 100), y = min(b, a + (l + 1) * win + rate * 15 / 100)
        return y - x >= rate / 5 ? (x, y) : nil
    }
}
