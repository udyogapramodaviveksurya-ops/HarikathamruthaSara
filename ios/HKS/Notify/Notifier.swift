import Foundation
import UIKit
import UserNotifications

/// Local notifications. iOS cannot run code at an exact moment, so reminders and nudges are scheduled ahead and
/// rebuilt whenever the app is opened or sent to the background (a reminder is skipped on days the goal is already met).
@MainActor
enum Notifier {
    private static let center = UNUserNotificationCenter.current()

    static func requestPermission() async { _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge]) }
    static func allowed() async -> Bool {
        let s = await center.notificationSettings().authorizationStatus
        return s == .authorized || s == .provisional
    }
    static func openSystemSettings() { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } }

    private static var inBackground: Bool { UIApplication.shared.applicationState != .active }

    static func post(id: String, title: String, body: String) {
        let c = UNMutableNotificationContent(); c.title = title; c.body = body; c.sound = .default
        center.add(UNNotificationRequest(identifier: id, content: c, trigger: nil))
    }

    static func reward(_ app: HkApp, _ r: Reward) {
        guard inBackground, app.settings.value.notifyAchievements else { return }
        let S = app.S
        switch r {
        case .badge(let b): post(id: "ach", title: "\(b.emoji) \(S.newBadge)", body: "\(S.badgeTitle(b.id)) — \(S.badgeHint(b.id))")
        case .levelUp(let l): post(id: "ach", title: "\(l.emoji) \(S.levelUp)", body: S.nowLevel(S.levelNames[LEVELS.firstIndex(of: l)!]))
        case .goalMet(let m): post(id: "ach", title: "🎯 \(S.goalDone)", body: S.goalDoneBody(m) + " " + S.krishnarpanam + " 🙏")
        }
    }

    static func downloadEnded(_ app: HkApp, title: String, ok: Bool) {
        guard inBackground, app.settings.value.notifyDownloads else { return }
        ok ? post(id: "dl", title: app.A.downloadDone, body: app.A.downloadDoneBody(title)) : post(id: "dlf", title: app.A.downloadFail, body: app.A.downloadFailBody)
    }

    static func updatesReady(_ app: HkApp, _ st: SourceState) {
        guard inBackground, app.settings.value.notifyUpdates else { return }
        post(id: "upd", title: app.S.updatesTitle, body: app.S.updatesBody(st.textUpdate, st.packUpdates))
    }

    /// Rebuilds the daily reminders (next 14 days) and the evening-streak and come-back nudges.
    static func refresh(_ app: HkApp) {
        let ids = (0..<14).map { "rem\($0)" } + ["streak", "comeback"]
        center.removePendingNotificationRequests(withIdentifiers: ids)
        let s = app.settings.value, p = app.progress.state, cal = Calendar.current, now = Date()
        let startOfToday = cal.startOfDay(for: now)
        func schedule(_ id: String, _ when: DateComponents, _ title: String, _ body: String) {
            let c = UNMutableNotificationContent(); c.title = title; c.body = body; c.sound = .default
            center.add(UNNotificationRequest(identifier: id, content: c, trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: false)))
        }
        func at(_ dayOffset: Int, _ h: Int, _ m: Int) -> Date? {
            cal.date(byAdding: .day, value: dayOffset, to: startOfToday).flatMap { cal.date(bySettingHour: h, minute: m, second: 0, of: $0) }
        }
        let parts: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute]
        if s.reminderOn {
            for d in 0..<14 {
                if d == 0 && p.todaySeconds >= s.dailyGoalMinutes * 60 { continue }
                guard let t = at(d, s.reminderHour, s.reminderMinute), t > now else { continue }
                schedule("rem\(d)", cal.dateComponents(parts, from: t), app.S.reminderTitle, app.S.reminderBody(p.streak, s.dailyGoalMinutes))
            }
        }
        guard s.notifyStreak else { return }
        let d = today(), sec = p.history[d] ?? 0
        if p.streak > 0, sec == 0, let t = at(0, 19, 0), t > now {
            schedule("streak", cal.dateComponents(parts, from: t), app.A.streakTitle(p.streak), app.A.streakBody)
        }
        if let last = p.history.filter({ $0.value > 0 }).keys.max() {
            let offset = max(last + 3 - d, 0)
            if let t = at(offset, 11, 0).flatMap({ $0 > now ? $0 : at(offset + 1, 11, 0) }) {
                schedule("comeback", cal.dateComponents(parts, from: t), app.A.comeBackTitle, app.A.comeBackBody)
            }
        }
    }
}
