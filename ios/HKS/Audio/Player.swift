import Foundation
import AVFoundation
import AudioToolbox
import Observation
import UIKit

/// Audio plays through one shared session. Chant = playback (heard even with the ring switch on silent). On the two
/// listening screens the session is play-and-record so the chant and the microphone can follow each other quickly.
@MainActor
enum AudioRoute {
    private static var voice = false
    static func usePlayback() {
        voice = false
        let s = AVAudioSession.sharedInstance()
        try? s.setCategory(.playback, mode: .default)
        try? s.setActive(true)
    }
    static func useVoice() {
        voice = true
        let s = AVAudioSession.sharedInstance()
        try? s.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetoothHFP])
        try? s.setActive(true)
    }
    /// Raw microphone (no automatic gain or echo processing) while listening, normal output afterwards.
    static func listening(_ on: Bool) { try? AVAudioSession.sharedInstance().setMode(on ? .measurement : .default) }
    static func ensureForOutput() { if !voice { usePlayback() } }
}

/// Short feedback chimes, replacing Android's ToneGenerator.
enum Chime {
    static func good() { AudioServicesPlaySystemSound(1057) }
    static func bad() { AudioServicesPlaySystemSound(1053) }
    static func beep() { AudioServicesPlaySystemSound(1113) }
}

@MainActor
final class ClipPlayer {
    private static let stopEarly = 0.02, fadeIn = 0.02, fadeOut = 0.04
    private var player: AVAudioPlayer?
    private var loaded: URL?

    private func ensure(_ url: URL) throws -> AVAudioPlayer {
        if let p = player, loaded == url { return p }
        release()
        let p = try AVAudioPlayer(contentsOf: url)
        p.enableRate = true
        p.prepareToPlay()
        player = p; loaded = url
        return p
    }

    /// Plays [start, end) seconds of `url`; returns wall-clock ms. Throws CancellationError when the task is cancelled.
    @discardableResult
    func play(_ url: URL, start: Double, end: Double, speed: Double, onPosition: (Double) -> Void = { _ in }) async throws -> Int {
        AudioRoute.ensureForOutput()
        let p = try ensure(url)
        p.stop()
        p.rate = Float(speed)
        p.currentTime = start
        let fadesIn = start > 0, fadesOut = end < Double.greatestFiniteMagnitude / 2
        p.volume = fadesIn ? 0 : 1
        let t0 = Date()
        p.play()
        defer { if p.isPlaying { p.pause() }; p.volume = 1 }
        let stopAt = end - Self.stopEarly * speed
        // a range that starts or ends inside the take may cut through sound: fade a few ms there
        while true {
            let pos = p.currentTime
            onPosition(pos)
            if pos >= stopAt || !p.isPlaying { break }
            var v = 1.0
            if fadesIn { v = min(v, (pos - start) / Self.fadeIn) }
            if fadesOut { v = min(v, (stopAt - pos) / Self.fadeOut) }
            p.volume = Float(min(max(v, 0), 1))
            try await Task.sleep(for: .milliseconds(5))
        }
        return Int(Date().timeIntervalSince(t0) * 1000)
    }

    func playAsset(_ rel: String) async throws -> Bool {
        let u = Paths.asset(rel)
        guard FileManager.default.fileExists(atPath: u.path) else { return false }
        try await play(u, start: 0, end: .greatestFiniteMagnitude, speed: 1)
        return true
    }

    func pause() { if player?.isPlaying == true { player?.pause() } }
    func release() { player?.stop(); player = nil; loaded = nil }
}

enum StepKind { case word, line, part, whole, lines }

/// A piece of a verse spoken and repeated as one unit. Word indices are verse-global, [from, to).
final class Step {
    let words: [String]; let from: Int; let kind: StepKind; let n: Int
    init(_ words: [String], _ from: Int, _ kind: StepKind, _ n: Int) { self.words = words; self.from = from; self.kind = kind; self.n = n }
    var to: Int { from + words.count }
    func contains(_ g: Int) -> Bool { g >= from && g < to }
}

enum Planner {
    static func plan(_ v: Verse, _ unit: StudyUnit) -> [Step] {
        var starts: [Int] = []; var g = 0
        for l in v.lines { starts.append(g); g += l.words.count }
        func step(_ a: Int, _ b: Int, _ k: StepKind, _ n: Int) -> Step { Step(v.lines[a...b].flatMap(\.words), starts[a], k, n) }
        let last = v.lines.count - 1
        switch unit {
        case .line: return v.lines.indices.map { step($0, $0, .line, $0 + 1) }
        case .sentence:
            var out: [Step] = []; var a = 0
            for (i, l) in v.lines.enumerated() where l.sentenceEnd || i == last { out.append(step(a, i, .part, out.count + 1)); a = i + 1 }
            return out
        case .verse: return [step(0, last, .whole, 1)]
        case .buildup: return v.lines.indices.map { step(0, $0, $0 == 0 ? .line : .lines, $0 + 1) }
        }
    }
}

enum Phase { case idle, speaking, yourTurn }

func hexName(_ text: String) -> String { text.utf16.map { String(format: "%04x", $0) }.joined(separator: "_") }
func letterAsset(_ t: String) -> String { "letters/\(hexName(t)).m4a" }
func wordAsset(_ t: String) -> String { "words/\(hexName(t)).m4a" }

/// Plays a padya step by step, with repeats and "your turn" gaps, in the Vāgdhenu chant (the only voice).
@MainActor @Observable
final class Player {
    var sandhi: Sandhi?
    var verseIdx = 0
    var steps: [Step] = []
    var stepIdx = 0
    var repeatNo = 1
    var playing = false
    var phase = Phase.idle
    var currentWord = -1
    var sleepAt: Date?
    var finishedSandhi = false
    var needsDownload = false

    var verse: Verse? { sandhi.flatMap { $0.verses.indices.contains(verseIdx) ? $0.verses[verseIdx] : nil } }
    var step: Step? { steps.indices.contains(stepIdx) ? steps[stepIdx] : nil }

    @ObservationIgnored let clip = ClipPlayer()
    @ObservationIgnored private let settings: SettingsStore
    @ObservationIgnored private let progress: ProgressStore
    @ObservationIgnored let vag: VagLibrary
    @ObservationIgnored private var job: Task<Void, Never>?
    @ObservationIgnored private var oneShot: Task<Void, Never>?
    @ObservationIgnored private var sleepJob: Task<Void, Never>?

    init(settings: SettingsStore, progress: ProgressStore, vag: VagLibrary) { self.settings = settings; self.progress = progress; self.vag = vag }

    private func plan(_ s: Sandhi, _ i: Int) -> [Step] { Planner.plan(s.verses[i], settings.value.unit) }

    func unitChanged() { restart { if let v = verse { steps = Planner.plan(v, settings.value.unit); stepIdx = 0 } } }

    func open(_ s: Sandhi, _ idx: Int) {
        if sandhi?.id == s.id && verseIdx == idx { return }
        restart(keepPlaying: false) { sandhi = s; verseIdx = idx; steps = plan(s, idx); stepIdx = 0 }
        progress.setPosition(s.id, idx)
    }

    func play() {
        guard verse != nil, job == nil else { return }
        playing = true; finishedSandhi = false; needsDownload = false
        startLoop()
    }

    private func startLoop() { job = Task { await loop() } }

    func pause() {
        job?.cancel(); job = nil
        oneShot?.cancel(); oneShot = nil
        clip.pause()
        playing = false; phase = .idle; currentWord = -1
        vag.applyPending()
    }

    func stopAndClose() { pause() }
    func toggle() { playing ? pause() : play() }
    func nextStep() { moveStep(1) }
    func prevStep() { moveStep(-1) }
    func goToStep(_ i: Int) { restart { stepIdx = min(max(i, 0), max(steps.count - 1, 0)) } }
    func nextVerse() { moveVerse(1) }
    func prevVerse() { moveVerse(-1) }

    func tapWord(_ g: Int) { if let i = steps.firstIndex(where: { $0.contains(g) }) { goToStep(i) } }

    func setSleepTimer(_ minutes: Int) {
        sleepJob?.cancel()
        guard minutes > 0 else { sleepAt = nil; return }
        sleepAt = Date().addingTimeInterval(Double(minutes) * 60)
        sleepJob = Task {
            try? await Task.sleep(for: .seconds(minutes * 60))
            guard !Task.isCancelled else { return }
            pause(); sleepAt = nil
        }
    }

    private func moveStep(_ d: Int) {
        let ni = stepIdx + d
        if steps.indices.contains(ni) { goToStep(ni) }
        else if d > 0 { moveVerse(1) } else { moveVerse(-1, toLast: true) }
    }

    private func moveVerse(_ d: Int, toLast: Bool = false) {
        guard let s = sandhi else { return }
        let ni = verseIdx + d
        guard s.verses.indices.contains(ni) else { return }
        let st = plan(s, ni)
        restart { verseIdx = ni; steps = st; stepIdx = toLast ? st.count - 1 : 0 }
        progress.setPosition(s.id, ni)
    }

    private func restart(keepPlaying: Bool = true, _ f: () -> Void) {
        let was = playing && keepPlaying
        job?.cancel(); job = nil
        clip.pause()
        f()
        repeatNo = 1; currentWord = -1; phase = .idle; playing = was; finishedSandhi = false; needsDownload = false
        if was { startLoop() }
    }

    private func loop() async {
        do {
            while true {
                guard let v = verse, let st = step, let s0 = sandhi else { break }
                let s = settings.value
                if s.repeatCount != 0 && repeatNo > s.repeatCount {
                    if !advance() { break }
                    continue
                }
                phase = .speaking; currentWord = st.from
                let dur = try await say(s0.id, v, st) { [weak self] w in
                    guard let self, self.step === st, self.currentWord != w else { return }
                    self.currentWord = w
                }
                guard let dur else {
                    playing = false; phase = .idle; currentWord = -1; needsDownload = true; job = nil
                    return
                }
                if dur > 0 { progress.addListen(v.key, seconds: (dur + 500) / 1000) }
                let gap: Int
                switch s.gapMode {
                case .none: gap = 300
                case .fixed: gap = s.gapSeconds * 1000
                case .echo: gap = Int(Double(dur) * 1.15) + 700
                }
                phase = s.gapMode == .none ? .speaking : .yourTurn
                currentWord = -1
                try await Task.sleep(for: .milliseconds(max(gap, 300)))
                if s.gapMode != .none && gap >= 1000 { progress.addSeconds(gap / 1000) }
                repeatNo += 1
            }
        } catch { return }   // cancelled: whoever cancelled has set the state
        playing = false; phase = .idle; currentWord = -1; stepIdx = 0; repeatNo = 1
        finishedSandhi = sandhi != nil && verseIdx == sandhi!.verses.count - 1
        job = nil
        vag.applyPending()
    }

    private func advance() -> Bool {
        if stepIdx + 1 < steps.count { stepIdx += 1; repeatNo = 1; return true }
        guard let s = sandhi else { return false }
        progress.checkSandhiBadge(s)
        if settings.value.autoAdvance && verseIdx + 1 < s.verses.count {
            verseIdx += 1; steps = plan(s, verseIdx); stepIdx = 0; repeatNo = 1
            progress.setPosition(s.id, verseIdx)
            return true
        }
        return false
    }

    func usesVag(_ id: Int, _ v: Verse) -> Bool { vag.isDownloaded(id) && vag.line(id, padya: v.number, line: 0) != nil }

    /// Chants one step; lines of a step in the same half-padya play as one continuous range. nil = not downloaded.
    func say(_ id: Int, _ v: Verse, _ st: Step, onWord: @escaping (Int) -> Void = { _ in }) async throws -> Int? {
        guard vag.isDownloaded(id) else { return nil }
        let speed = settings.value.speed
        let l0 = v.words[st.from].line, l1 = v.words[st.to - 1].line
        var clips: [Int: VagLine] = [:]
        for li in l0...l1 {
            guard let c = vag.line(id, padya: v.number, line: li), c.words.count == v.lines[li].words.count else { return nil }
            clips[li] = c
        }
        let t0 = Date()
        if st.words.count == 1 {
            let w = v.words[st.from], c = clips[w.line]!, (a, b) = c.words[w.index]
            try await clip.play(c.file, start: max(a - 0.03, 0), end: b + 0.06, speed: speed) { _ in onWord(st.from) }
            return Int(Date().timeIntervalSince(t0) * 1000)
        }
        var runs: [[Int]] = []
        for li in l0...l1 {
            if let last = runs.last?.last, clips[last]!.file == clips[li]!.file { runs[runs.count - 1].append(li) } else { runs.append([li]) }
        }
        for (k, run) in runs.enumerated() {
            var marks: [(Double, Int)] = []
            for li in run {
                let base = v.words.first { $0.line == li }!.global
                for (wi, w) in clips[li]!.words.enumerated() { marks.append((w.0, base + wi)) }
            }
            let first = clips[run.first!]!, last = clips[run.last!]!
            try await clip.play(first.file, start: first.start, end: last.end, speed: speed) { pos in
                var w = 0
                while w + 1 < marks.count && pos >= marks[w + 1].0 { w += 1 }
                onWord(marks[w].1)
            }
            if k < runs.count - 1 { try await Task.sleep(for: .milliseconds(Int(1200 / speed))) }
        }
        return Int(Date().timeIntervalSince(t0) * 1000)
    }

    func sayAny(_ id: Int, _ v: Verse, _ st: Step) async throws -> Int { try await say(id, v, st) ?? 0 }

    private func once(_ f: @escaping () async throws -> Void) {
        pause()
        oneShot = Task { try? await f() }
    }

    func sayWord(_ id: Int, _ v: Verse, _ g: Int) {
        guard v.words.indices.contains(g) else { pause(); return }
        let w = v.words[g]
        once { _ = try await self.sayAny(id, v, Step([w.text], g, .word, g + 1)) }
    }

    func sayLine(_ id: Int, _ v: Verse, _ line: Int) {
        let ws = v.words.filter { $0.line == line }
        guard let f = ws.first else { pause(); return }
        once { _ = try await self.sayAny(id, v, Step(ws.map(\.text), f.global, .line, line + 1)) }
    }

    func sayLetter(_ t: String) { once { _ = try await self.clip.playAsset(letterAsset(t)) } }
    func sayPracticeWord(_ t: String) { once { _ = try await self.clip.playAsset(wordAsset(t)) } }
}
