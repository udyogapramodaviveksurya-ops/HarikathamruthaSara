import Foundation
import Observation

enum StudyUnit: String, CaseIterable, Sendable { case sentence = "SENTENCE", verse = "VERSE", line = "LINE", buildup = "BUILDUP" }
enum GapMode: String, Sendable { case none = "NONE", fixed = "FIXED", echo = "ECHO" }
enum ScriptMode: String, Sendable { case both = "BOTH", kannada = "KANNADA", mine = "MINE" }
enum DarkMode: String, Sendable { case system = "SYSTEM", light = "LIGHT", dark = "DARK" }

let defaultServer = "https://huggingface.co/datasets/ssurya1992/harikathamrutha-vagdhenu/resolve/main/hks"

struct Settings: Equatable, Sendable {
    var unit = StudyUnit.sentence
    /// 0 = repeat forever until stopped
    var repeatCount = 3
    var gapMode = GapMode.echo
    var gapSeconds = 3
    var autoAdvance = true
    var scriptMode = ScriptMode.both
    var tamilSuperscripts = true
    var alsoScripts: Set<Script> = []
    var peekTamil = false
    var fontScale: Double = 1.0
    var dailyGoalMinutes = 15
    var reminderOn = false
    var reminderHour = 14
    var reminderMinute = 0
    var strictness = Strictness.normal
    var speakCorrections = true
    var keepScreenOn = true
    var speed: Double = 1.0
    var serverUrl = ""
    var autoUpdate = false
    var lang: Lang?
    /// Kept only so settings sync unchanged with Android, which also offers wallpaper colours.
    var colorStyle = "FESTIVAL"
    var darkMode = DarkMode.system
    var notifyStreak = true
    var notifyAchievements = true
    var notifyDownloads = true
    var notifyUpdates = true

    var server: String {
        var u = serverUrl.trimmingCharacters(in: .whitespaces)
        if u.isEmpty { u = defaultServer }
        while u.hasSuffix("/") { u.removeLast() }
        return u
    }
}

/// Settings are kept as typed key/value pairs using the same keys and JSON shape as Android's SharedPreferences,
/// so one account's settings sync across Android and iOS.
@MainActor @Observable
final class SettingsStore {
    private(set) var value: Settings
    @ObservationIgnored private var prefs: [String: [String: Any]]
    @ObservationIgnored private let defaults = UserDefaults.standard
    private static let key = "settings.typed.v1"
    private static let localOnly: Set<String> = ["changedAt", "syncedUid", "serverUrl", "autoUpdate"]

    init() {
        let raw = UserDefaults.standard.string(forKey: Self.key).flatMap { $0.data(using: .utf8) }
        let p = raw.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: [String: Any]] } ?? [:]
        prefs = p
        value = Self.read(p)
    }

    func update(_ f: (inout Settings) -> Void) {
        var s = value; f(&s)
        if s == value { return }
        value = s
        write(s)
        prefs["changedAt"] = ["l": Int(Date().timeIntervalSince1970 * 1000)]
        save()
    }

    var changedAt: Int { (prefs["changedAt"]?["l"] as? Int) ?? 0 }
    var syncedUid: String {
        get { (prefs["syncedUid"]?["s"] as? String) ?? "" }
        set { prefs["syncedUid"] = ["s": newValue]; save() }
    }

    func exportJson() -> String {
        let o = prefs.filter { !Self.localOnly.contains($0.key) }
        return (try? JSONSerialization.data(withJSONObject: o, options: [.sortedKeys])).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
    }

    func importJson(_ json: String, at: Int) {
        guard let d = json.data(using: .utf8), let o = try? JSONSerialization.jsonObject(with: d) as? [String: [String: Any]] else { return }
        for (k, v) in o where !Self.localOnly.contains(k) { prefs[k] = v }
        prefs["changedAt"] = ["l": at]
        save()
        value = Self.read(prefs)
    }

    private func save() {
        if let d = try? JSONSerialization.data(withJSONObject: prefs), let s = String(data: d, encoding: .utf8) { defaults.set(s, forKey: Self.key) }
    }

    private static func read(_ p: [String: [String: Any]]) -> Settings {
        func i(_ k: String, _ d: Int) -> Int { (p[k]?["i"] as? NSNumber)?.intValue ?? d }
        func f(_ k: String, _ d: Double) -> Double { (p[k]?["f"] as? NSNumber)?.doubleValue ?? d }
        func b(_ k: String, _ d: Bool) -> Bool { (p[k]?["b"] as? NSNumber)?.boolValue ?? d }
        func s(_ k: String) -> String? { p[k]?["s"] as? String }
        let d = Settings()
        var x = d
        x.unit = s("unit").flatMap(StudyUnit.init) ?? d.unit
        x.repeatCount = i("repeat", d.repeatCount)
        x.gapMode = s("gapMode").flatMap(GapMode.init) ?? d.gapMode
        x.gapSeconds = i("gapSec", d.gapSeconds)
        x.autoAdvance = b("autoAdvance", d.autoAdvance)
        x.scriptMode = s("script").flatMap(ScriptMode.init) ?? d.scriptMode
        x.tamilSuperscripts = b("sup", d.tamilSuperscripts)
        x.alsoScripts = Set((s("alsoScripts") ?? "").split(separator: ",").compactMap { Script(rawValue: String($0)) })
        x.peekTamil = b("peek", d.peekTamil)
        x.fontScale = f("font", d.fontScale)
        x.dailyGoalMinutes = i("goal", d.dailyGoalMinutes)
        x.reminderOn = b("remOn", d.reminderOn)
        x.reminderHour = i("remH", d.reminderHour)
        x.reminderMinute = i("remM", d.reminderMinute)
        x.strictness = s("strictness").flatMap(Strictness.init) ?? d.strictness
        x.speakCorrections = b("speakFix", d.speakCorrections)
        x.keepScreenOn = b("screenOn", d.keepScreenOn)
        x.speed = f("guruSpeed", d.speed)
        x.serverUrl = s("serverUrl") ?? d.serverUrl
        x.autoUpdate = b("autoUpdate", d.autoUpdate)
        x.lang = s("lang").flatMap(Lang.init)
        x.colorStyle = s("colorStyle") ?? d.colorStyle
        x.darkMode = s("darkMode").flatMap(DarkMode.init) ?? d.darkMode
        x.notifyStreak = b("nStreak", d.notifyStreak)
        x.notifyAchievements = b("nAchieve", d.notifyAchievements)
        x.notifyDownloads = b("nDownloads", d.notifyDownloads)
        x.notifyUpdates = b("nUpdates", d.notifyUpdates)
        return x
    }

    private func write(_ s: Settings) {
        func S(_ k: String, _ v: String) { prefs[k] = ["s": v] }
        func I(_ k: String, _ v: Int) { prefs[k] = ["i": v] }
        func B(_ k: String, _ v: Bool) { prefs[k] = ["b": v] }
        func F(_ k: String, _ v: Double) { prefs[k] = ["f": v] }
        S("unit", s.unit.rawValue); I("repeat", s.repeatCount); S("gapMode", s.gapMode.rawValue); I("gapSec", s.gapSeconds)
        B("autoAdvance", s.autoAdvance); S("script", s.scriptMode.rawValue); B("sup", s.tamilSuperscripts)
        S("alsoScripts", s.alsoScripts.map(\.rawValue).sorted().joined(separator: ",")); B("peek", s.peekTamil)
        F("font", s.fontScale); I("goal", s.dailyGoalMinutes); B("remOn", s.reminderOn); I("remH", s.reminderHour); I("remM", s.reminderMinute)
        S("strictness", s.strictness.rawValue); B("speakFix", s.speakCorrections); B("screenOn", s.keepScreenOn); F("guruSpeed", s.speed)
        S("serverUrl", s.serverUrl); B("autoUpdate", s.autoUpdate)
        if let l = s.lang { S("lang", l.rawValue) } else { prefs["lang"] = nil }
        S("colorStyle", s.colorStyle); S("darkMode", s.darkMode.rawValue)
        B("nStreak", s.notifyStreak); B("nAchieve", s.notifyAchievements); B("nDownloads", s.notifyDownloads); B("nUpdates", s.notifyUpdates)
    }
}
