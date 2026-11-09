import SwiftUI
import UIKit
import BackgroundTasks
import UserNotifications
import FirebaseCore
import FirebaseAuth
import FirebaseMessaging
import GoogleSignIn

@MainActor @Observable
final class HkApp {
    let settings: SettingsStore
    let progress: ProgressStore
    let vag: VagLibrary
    let player: Player
    let ear: EarStore
    let account: Account
    let sync: CloudSync
    private(set) var source: Source!
    private(set) var library: [Sandhi]?

    @ObservationIgnored private var strsCache: (Lang, Strs, AccountText, TourText)?
    private func strings() -> (Lang, Strs, AccountText, TourText) {
        let l = settings.value.lang ?? .en
        if let c = strsCache, c.0 == l { return c }
        let c = (l, Strs(l), AccountText(l), TourText(l)); strsCache = c
        return c
    }
    var S: Strs { strings().1 }
    var A: AccountText { strings().2 }
    var T: TourText { strings().3 }
    var I: IosText { IosText(lang: settings.value.lang ?? .en) }
    var script: Script { (settings.value.lang ?? .en).script }
    func mine(_ kn: String) -> String { Scripts.render(kn, script, settings.value.tamilSuperscripts) }

    init() {
        settings = SettingsStore()
        progress = ProgressStore(settings: settings)
        vag = VagLibrary()
        player = Player(settings: settings, progress: progress, vag: vag)
        ear = EarStore()
        account = Account()
        sync = CloudSync(progress: progress, settings: settings, account: account)
        vag.isBusy = { [unowned self] id in self.player.playing && self.player.sandhi?.id == id }
        vag.onReplaced = { [unowned self] _ in if !self.player.playing { self.player.clip.release() } }
        source = Source(settings: settings, vag: vag) { [unowned self] in self.loadLibrary() }
        ear.source = { [unowned self] in await self.source.earLocation() }
        progress.onReward = { [unowned self] r in Notifier.reward(self, r) }
        source.onUpdatesFound = { [unowned self] st in Notifier.updatesReady(self, st) }
        vag.onDownloadEnded = { [unowned self] id, ok in
            let t = self.library?.first { $0.id == id }.map { "\(id). " + self.mine($0.title) } ?? "\(id)"
            Notifier.downloadEnded(self, title: t, ok: ok)
        }
        ear.onDownloadEnded = { [unowned self] ok in Notifier.downloadEnded(self, title: self.S.earName, ok: ok) }

        let d = BackgroundDownloads.shared
        d.onProgress = { [unowned self] tag, f in
            if self.vag.handles(tag) { self.vag.progress(tag: tag, f) } else if self.ear.handles(tag) { self.ear.progress(f) }
        }
        d.onFinished = { [unowned self] tag, file, err in
            if self.vag.handles(tag) { self.vag.finished(tag: tag, file: file, error: err) } else if self.ear.handles(tag) { self.ear.finished(tag: tag, file: file, error: err) }
        }
        Task {
            let tags = await d.runningTags()
            self.vag.restore(tags: tags); self.ear.restore(tags: tags)
        }
        loadLibrary()
        source.check()
        sync.userChanged()
    }

    private func loadLibrary() { Task.detached(priority: .userInitiated) { let l = Library.load(); await MainActor.run { self.library = l } } }

    func syncTopics() {
        guard account.available else { return }
        let m = Messaging.messaging()
        m.subscribe(toTopic: "all")
        if settings.value.notifyUpdates { m.subscribe(toTopic: "updates") } else { m.unsubscribe(fromTopic: "updates") }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    static let refreshTask = "com.prathosh.hks.refresh"
    @MainActor static weak var model: HkApp?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        if FirebaseApp.app() != nil { Messaging.messaging().delegate = self }
        application.registerForRemoteNotifications()
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.refreshTask, using: nil) { task in
            Task { @MainActor in
                Self.model?.source.check()
                if let m = Self.model { Notifier.refresh(m) }
                Self.scheduleRefresh()
                try? await Task.sleep(for: .seconds(10))
                task.setTaskCompleted(success: true)
            }
        }
        return true
    }

    @MainActor static func scheduleRefresh() {
        let r = BGAppRefreshTaskRequest(identifier: refreshTask)
        r.earliestBeginDate = Date(timeIntervalSinceNow: 3 * 3600)
        try? BGTaskScheduler.shared.submit(r)
    }

    func application(_ application: UIApplication, handleEventsForBackgroundURLSession identifier: String, completionHandler: @escaping () -> Void) {
        BackgroundDownloads.shared.finishEvents = completionHandler
        _ = BackgroundDownloads.shared.session
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        guard FirebaseApp.app() != nil else { return }
        Messaging.messaging().apnsToken = deviceToken
        Auth.auth().setAPNSToken(deviceToken, type: .unknown)
    }

    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any]) async -> UIBackgroundFetchResult {
        if FirebaseApp.app() != nil, Auth.auth().canHandleNotification(userInfo) { return .noData }
        await handlePush(userInfo)
        return .newData
    }

    /// The server's "updates" pushes look for new content straight away; language variants arrive as title_ta, body_ta…
    @MainActor private func handlePush(_ info: [AnyHashable: Any]) async {
        guard let app = Self.model else { return }
        let kind = info["kind"] as? String
        if kind == "updates" {
            app.source.check(force: true)
            if !app.settings.value.notifyUpdates { return }
        }
        guard info["aps"].flatMap({ ($0 as? [String: Any])?["alert"] }) == nil, let t = info["title"] as? String else { return }
        let l = app.settings.value.lang?.rawValue.lowercased() ?? "en"
        Notifier.post(id: "push\(t.hashValue)", title: info["title_\(l)"] as? String ?? t, body: info["body_\(l)"] as? String ?? info["body"] as? String ?? "")
    }

    func userNotificationCenter(_ c: UNUserNotificationCenter, willPresent n: UNNotification) async -> UNNotificationPresentationOptions { [.banner, .sound] }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        Task { @MainActor in Self.model?.sync.saveToken(fcmToken); Self.model?.syncTopics() }
    }
}

@main
struct HKSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @Environment(\.scenePhase) private var phase
    @State private var app: HkApp

    init() {
        if Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil { FirebaseApp.configure() }
        let m = HkApp()
        AppDelegate.model = m
        _app = State(initialValue: m)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .onOpenURL { url in
                    if app.account.available, Auth.auth().canHandle(url) { return }
                    _ = GIDSignIn.sharedInstance.handle(url)
                }
                .onChange(of: app.account.user) { app.sync.userChanged(); app.syncTopics() }
                .onChange(of: app.settings.value.notifyUpdates) { app.syncTopics() }
        }
        .onChange(of: phase) { _, p in
            switch p {
            case .background:
                // Audio plays only while the app is on screen (the owner's decision, same as Android).
                app.player.stopAndClose()
                app.progress.saveNow()
                Notifier.refresh(app)
                AppDelegate.scheduleRefresh()
            case .active:
                app.source.check()
                Notifier.refresh(app)
            default: break
            }
        }
    }
}
