import Foundation
import Observation

struct VerseStat: Equatable, Sendable {
    var listens = 0, bestScore = 0, bestFullScore = 0, recites = 0
    var stars: Int { bestFullScore >= 90 ? 3 : bestScore >= 70 ? 2 : listens >= 10 ? 1 : 0 }
}

struct Level: Equatable, Sendable { let minPoints: Int; let emoji: String }
let LEVELS = [Level(minPoints: 0, emoji: "🌱"), Level(minPoints: 100, emoji: "🌿"), Level(minPoints: 300, emoji: "🪴"), Level(minPoints: 700, emoji: "🌸"), Level(minPoints: 1500, emoji: "🪔"), Level(minPoints: 3000, emoji: "🌕"), Level(minPoints: 6000, emoji: "🙏")]

struct Badge: Equatable, Sendable { let id: String; let emoji: String }
let BADGES = [("first_listen","👣"),("first_recite","🎤"),("perfect","💯"),("streak3","🔥"),("streak7","🪔"),("streak30","🌕"),("japa108","📿"),("early","🌅"),("goal","🎯"),("quiz10","🧠"),("letters","🔤"),("sandhi1","🌺"),("master10","🏵️"),("master50","👑")].map { Badge(id: $0.0, emoji: $0.1) }

enum Reward: Equatable, Sendable, Identifiable {
    case badge(Badge), levelUp(Level), goalMet(Int)
    var id: String { switch self { case .badge(let b): "b" + b.id; case .levelUp(let l): "l\(l.minPoints)"; case .goalMet(let m): "g\(m)" } }
}

struct ProgressSnapshot: Equatable, Sendable {
    var points = 0, streak = 0, bestStreak = 0, todaySeconds = 0, todayRepeats = 0
    var history: [Int: Int] = [:]
    var badges: Set<String> = []
    var verses: [String: VerseStat] = [:]
    var lastSandhi = 1, lastVerse = 0, quizRun = 0, lettersRight = 0

    var level: Level { LEVELS.last { points >= $0.minPoints }! }
    var nextLevel: Level? { LEVELS.first { $0.minPoints > points } }
    func stat(_ key: String) -> VerseStat { verses[key] ?? VerseStat() }
    var mastered: Int { verses.values.filter { $0.stars == 3 }.count }
}

func today() -> Int {
    let now = Date()
    return (Int(now.timeIntervalSince1970 * 1000) + TimeZone.current.secondsFromGMT(for: now) * 1000) / 86_400_000
}

/// Same JSON file format as Android (progress.json), which is also what is stored in the cloud.
@MainActor @Observable
final class ProgressStore {
    private(set) var state: ProgressSnapshot
    /// Rewards waiting for their celebration dialog.
    var rewards: [Reward] = []
    @ObservationIgnored var onReward: ((Reward) -> Void)?

    @ObservationIgnored private let settings: SettingsStore
    @ObservationIgnored private let file = Paths.support.appendingPathComponent("progress.json")
    @ObservationIgnored private var saveTask: Task<Void, Never>?
    @ObservationIgnored private var lastActiveDay: Int
    @ObservationIgnored private var goalMetDay = -1
    @ObservationIgnored private var posAt: Int
    @ObservationIgnored private var pendingAwards: [String] = []

    init(settings: SettingsStore) {
        self.settings = settings
        let o = (try? Data(contentsOf: file)).flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]
        state = Self.parse(o)
        lastActiveDay = (o["lastDay"] as? NSNumber)?.intValue ?? -1
        posAt = (o["posAt"] as? NSNumber)?.intValue ?? 0
        rollDay()
    }

    func addListen(_ key: String, seconds: Int) {
        mutate { s in
            var st = s.stat(key); st.listens += 1
            s.verses[key] = st; s.points += 1; s.todayRepeats += 1
            self.withSeconds(&s, seconds)
            self.award("first_listen")
            if s.todayRepeats >= 108 { self.award("japa108") }
        }
    }
    func addSeconds(_ seconds: Int) { mutate { self.withSeconds(&$0, seconds) } }

    func recordRecite(_ key: String, score: Int, fullVerse: Bool) {
        mutate { s in
            var st = s.stat(key)
            st.recites += 1; st.bestScore = max(st.bestScore, score)
            if fullVerse { st.bestFullScore = max(st.bestFullScore, score) }
            self.award("first_recite")
            if score >= 95 { self.award("perfect") }
            s.points += score >= 90 ? 10 : score >= 70 ? 5 : score >= 40 ? 2 : 1
            s.verses[key] = st
            self.withSeconds(&s, 5)
        }
    }
    func recordQuiz(_ correct: Bool) {
        mutate { s in
            let run = correct ? s.quizRun + 1 : 0
            if run >= 10 { self.award("quiz10") }
            s.points += correct ? 3 : 0; s.quizRun = run
        }
    }
    func recordLetter(_ correct: Bool) {
        mutate { s in
            let n = s.lettersRight + (correct ? 1 : 0)
            if n >= 30 { self.award("letters") }
            s.points += correct ? 1 : 0; s.lettersRight = n
        }
    }
    func setPosition(_ sandhi: Int, _ verse: Int) {
        if state.lastSandhi != sandhi || state.lastVerse != verse { posAt = Int(Date().timeIntervalSince1970 * 1000) }
        state.lastSandhi = sandhi; state.lastVerse = verse
        scheduleSave()
    }
    func reset() { state = ProgressSnapshot(); lastActiveDay = -1; scheduleSave() }

    func checkSandhiBadge(_ sandhi: Sandhi) {
        guard sandhi.id == 1 else { return }
        if sandhi.verses.filter({ !$0.isRefrain }).allSatisfy({ state.stat($0.key).stars >= 2 }) { mutate { _ in self.award("sandhi1") } }
    }

    private func award(_ id: String) { pendingAwards.append(id) }

    private func withSeconds(_ s: inout ProgressSnapshot, _ sec: Int) {
        let d = today()
        s.history[d, default: 0] += sec
        s.history = s.history.filter { $0.key > d - 90 }
        s.todaySeconds += sec
    }

    private func mutate(_ f: (inout ProgressSnapshot) -> Void) {
        rollDay()
        let before = state
        pendingAwards = []
        var s = before
        f(&s)
        touchStreak(&s)
        if s.streak >= 3 { award("streak3") }
        if s.streak >= 7 { award("streak7") }
        if s.streak >= 30 { award("streak30") }
        if Calendar.current.component(.hour, from: Date()) < 7 && s.todaySeconds > 60 { award("early") }
        if s.mastered >= 10 { award("master10") }
        if s.mastered >= 50 { award("master50") }
        var out: [Reward] = []
        let goal = settings.value.dailyGoalMinutes * 60
        if s.todaySeconds >= goal && goalMetDay != today() {
            goalMetDay = today()
            if before.todaySeconds < goal {
                s.points += 20
                out.append(.goalMet(settings.value.dailyGoalMinutes))
                award("goal")
            }
        }
        let new = Set(pendingAwards).subtracting(s.badges)
        if !new.isEmpty {
            s.badges.formUnion(new)
            out += BADGES.filter { new.contains($0.id) }.map { .badge($0) }
        }
        if s.level != before.level && s.points > before.points { out.append(.levelUp(s.level)) }
        state = s
        scheduleSave()
        rewards += out
        out.forEach { onReward?($0) }
    }

    /// New calendar day: reset today's counters and break the streak if a day was skipped.
    private func rollDay() {
        let d = today()
        if lastActiveDay >= 0, lastActiveDay < d - 1, state.streak > 0 { state.streak = 0 }
        let sec = state.history[d] ?? 0
        if state.todaySeconds != sec {
            let reps = sec == 0 ? 0 : state.todayRepeats
            state.todaySeconds = sec; state.todayRepeats = reps
        }
    }

    private func touchStreak(_ s: inout ProgressSnapshot) {
        let d = today()
        if lastActiveDay == d { return }
        let streak = lastActiveDay == d - 1 ? s.streak + 1 : 1
        lastActiveDay = d
        s.streak = streak; s.bestStreak = max(s.bestStreak, streak)
        if s.history[d] == nil { s.todayRepeats = 0 }
    }

    private func scheduleSave() {
        saveTask?.cancel()
        saveTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(1500))
            guard !Task.isCancelled else { return }
            self?.saveNow()
        }
    }

    func saveNow() {
        let json = exportJson()
        try? json.data(using: .utf8)?.write(to: file, options: .atomic)
    }

    func exportJson() -> String {
        let s = state
        let o: [String: Any] = [
            "points": s.points, "streak": s.streak, "best": s.bestStreak, "todayRepeats": s.todayRepeats, "lastDay": lastActiveDay, "posAt": posAt,
            "lastSandhi": s.lastSandhi, "lastVerse": s.lastVerse, "quizRun": s.quizRun, "letters": s.lettersRight,
            "badges": s.badges.sorted(),
            "history": Dictionary(uniqueKeysWithValues: s.history.map { (String($0.key), $0.value) }),
            "verses": s.verses.mapValues { [$0.listens, $0.bestScore, $0.bestFullScore, $0.recites] },
        ]
        return (try? JSONSerialization.data(withJSONObject: o, options: [.sortedKeys])).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
    }

    /// Merges progress from another device, never losing anything. Merging the same data twice changes nothing.
    func mergeFrom(_ json: String) {
        guard let d = json.data(using: .utf8), let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { return }
        let r = Self.parse(o)
        let rDay = (o["lastDay"] as? NSNumber)?.intValue ?? -1
        let rPos = (o["posAt"] as? NSNumber)?.intValue ?? 0
        let l = state
        var m = l
        m.history = l.history.merging(r.history, uniquingKeysWith: max)
        m.verses = l.verses.merging(r.verses) { a, b in
            VerseStat(listens: max(a.listens, b.listens), bestScore: max(a.bestScore, b.bestScore), bestFullScore: max(a.bestFullScore, b.bestFullScore), recites: max(a.recites, b.recites))
        }
        var day = lastActiveDay
        if rDay > lastActiveDay { m.streak = r.streak; day = rDay }
        else if rDay == lastActiveDay { m.streak = max(l.streak, r.streak) }
        let remotePos = rPos > posAt
        m.points = max(l.points, r.points)
        m.bestStreak = max(l.bestStreak, r.bestStreak, m.streak)
        m.todaySeconds = m.history[today()] ?? 0
        m.todayRepeats = max(l.todayRepeats, r.todayRepeats)
        m.badges = l.badges.union(r.badges)
        if remotePos { m.lastSandhi = r.lastSandhi; m.lastVerse = r.lastVerse; posAt = rPos }
        m.lettersRight = max(l.lettersRight, r.lettersRight)
        lastActiveDay = day
        if m != l { state = m; scheduleSave() }
    }

    private static func parse(_ o: [String: Any]) -> ProgressSnapshot {
        func i(_ k: String, _ d: Int = 0) -> Int { (o[k] as? NSNumber)?.intValue ?? d }
        var s = ProgressSnapshot()
        let h = o["history"] as? [String: Any] ?? [:]
        for (k, v) in h { if let d = Int(k), let n = v as? NSNumber { s.history[d] = n.intValue } }
        s.points = i("points"); s.streak = i("streak"); s.bestStreak = i("best")
        s.todaySeconds = s.history[today()] ?? 0; s.todayRepeats = i("todayRepeats")
        s.badges = Set(o["badges"] as? [String] ?? [])
        for (k, v) in o["verses"] as? [String: [Int]] ?? [:] where v.count == 4 {
            s.verses[k] = VerseStat(listens: v[0], bestScore: v[1], bestFullScore: v[2], recites: v[3])
        }
        s.lastSandhi = i("lastSandhi", 1); s.lastVerse = i("lastVerse"); s.quizRun = i("quizRun"); s.lettersRight = i("letters")
        return s
    }
}
