import SwiftUI
import AuthenticationServices

struct SettingsScreen: View {
    @Environment(HkApp.self) private var app
    @Environment(Nav.self) private var nav
    @Environment(\.cs) private var cs
    @Environment(\.tour) private var tour
    @Environment(\.openURL) private var open
    @State private var confirmReset = false
    @State private var toursReset = false

    var body: some View {
        let s = app.settings.value, S = app.S, A = app.A, T = app.T, t = cs.tones
        let _ = app.vag.version
        let have = app.vag.downloadedCount()
        ScrollView {
            VStack(spacing: 16) {
                AccountCard()
                group(A.groupPersonal) {
                    let dark = s.darkMode == .system ? S.themeSystem : s.darkMode == .light ? S.themeLight : S.themeDark
                    row("🎨", t.marigold, plain(S.sectionLook), plain(dark)) { nav.go(.sub(.look)) }
                    divider
                    let extra = alsoScripts.filter { s.alsoScripts.contains($0.0) }.map { "+" + $0.1 }.joined(separator: " ")
                    row("🌐", t.sky, plain(S.language), [s.lang?.native ?? "", extra].filter { !$0.isEmpty }.joined(separator: "  ")) { nav.go(.sub(.language)) }
                    divider
                    row("🔔", t.lotus, plain(A.notifications), A.nOn([s.reminderOn, s.notifyStreak, s.notifyAchievements, s.notifyDownloads, s.notifyUpdates].filter { $0 }.count)) { nav.go(.sub(.notify)) }
                    divider
                    row("🎯", t.leaf, plain(S.sectionGoal), S.minutesN(s.dailyGoalMinutes) + (s.reminderOn ? "  ·  ⏰ \(pad2(s.reminderHour)):\(pad2(s.reminderMinute))" : "")) { nav.go(.sub(.goal)) }
                }
                group(A.groupLearning) {
                    row("🎶", t.peacock, plain(S.sectionVag), "⬇ \(have)/33  ·  ⏩ " + x2(s.speed)) { nav.go(.sub(.chant)) }
                    divider
                    row("📖", t.berry, plain(S.sectionReading), "Aa " + String(format: "%.1f×", s.fontScale)) { nav.go(.sub(.reading)) }
                    divider
                    row("🎤", t.marigold, plain(S.sectionCheck), plain(S.strictName(s.strictness))) { nav.go(.sub(.check)) }
                }
                group(A.groupMore) {
                    row("🧭", t.sky, plain(A.guides), toursReset ? T.replayDone : plain(T.replayAll)) { tour?.resetAll(); toursReset = true }
                    divider
                    row("☁️", t.leaf, plain(S.sectionServer), app.source.state.revision > 0 ? "rev \(app.source.state.revision)" : "") { nav.go(.sub(.source)) }
                    divider
                    row("📜", t.lotus, plain(S.about), plain(S.creditsTitle)) { nav.go(.credits) }
                    divider
                    row("🔒", t.peacock, app.I.privacyPolicy, nil) { open(HK.privacyURL) }
                    divider
                    row("⚠️", Tone(container: cs.errorContainer, on: cs.onErrorContainer, accent: cs.error), plain(S.resetProgress), nil, danger: true) { confirmReset = true }
                }
                Offering(size: 16).padding(.top, 4).padding(.bottom, 16)
            }.padding(16).readable()
        }
        .bar(S.settingsTitle)
        .alert(S.resetQ, isPresented: $confirmReset) {
            Button(S.reset, role: .destructive) { app.progress.reset() }
            Button(S.cancel, role: .cancel) {}
        } message: { Text(S.resetBody) }
    }

    private var divider: some View { Divider().overlay(cs.outlineVariant.opacity(0.6)).padding(.leading, 72).padding(.trailing, 16) }

    private func group<C: View>(_ title: String, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.hk(14, .semibold)).foregroundStyle(cs.primary).padding(.leading, 8)
            VStack(spacing: 0, content: c).padding(.vertical, 4).card(cs.low)
        }
    }

    private func row(_ e: String, _ tone: Tone, _ title: String, _ summary: String?, danger: Bool = false, _ f: @escaping () -> Void) -> some View {
        Button(action: f) {
            HStack(spacing: 16) {
                Text(e).font(.hk(20)).frame(width: 40, height: 40).background(tone.container, in: Circle())
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(.hk(17, .semibold)).foregroundStyle(danger ? cs.error : cs.onSurface).multilineTextAlignment(.leading)
                    if let summary, !summary.isEmpty { Text(summary).font(.hk(13)).foregroundStyle(cs.onSurfaceVariant).lineLimit(1) }
                }.frame(maxWidth: .infinity, alignment: .leading)
                if !danger { Text("›").font(.hk(24)).foregroundStyle(cs.onSurfaceVariant) }
            }.padding(.horizontal, 16).padding(.vertical, 10).frame(minHeight: 64).contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}

func syncLine(_ A: AccountText, _ s: SyncStatus) -> String {
    switch s {
    case .off: return A.localOnly
    case .syncing: return A.syncing
    case .synced(let at):
        if Date().timeIntervalSince(at) < 60 { return A.synced }
        return A.synced + " · " + RelativeDateTimeFormatter().localizedString(for: at, relativeTo: Date())
    case .failed: return A.syncError
    }
}

private struct AccountCard: View {
    @Environment(HkApp.self) private var app
    @Environment(Nav.self) private var nav
    @Environment(\.cs) private var cs
    var body: some View {
        let A = app.A, u = app.account.user, signedIn = u != nil && !u!.anonymous, t = cs.tones.peacock
        BouncyCard(color: signedIn ? t.container : cs.primaryContainer, fg: signedIn ? t.on : cs.onPrimaryContainer, radius: 28, action: { nav.go(.account) }) {
            HStack(spacing: 14) {
                Avatar(label: u?.label, size: 56)
                VStack(alignment: .leading, spacing: 1) {
                    if signedIn {
                        Text(u!.label ?? A.signedIn).font(.hk(18, .bold)).lineLimit(1)
                        Text(syncLine(A, app.sync.status)).font(.hk(13))
                    } else {
                        Text(A.heroTitle).font(.hk(17, .bold))
                        Text(u?.anonymous == true ? A.guest + " · " + String(A.localOnly.dropFirst(2)) : A.localOnly).font(.hk(13))
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
                Text("›").font(.hk(28, .bold))
            }.padding(16)
        }
    }
}

struct SettingsPageScreen: View {
    let page: SettingsPage
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @State private var notifAllowed = true
    @State private var url = ""

    private func upd(_ f: (inout Settings) -> Void) { app.settings.update(f) }
    private func bind<T>(_ k: WritableKeyPath<Settings, T>) -> Binding<T> { Binding(get: { app.settings.value[keyPath: k] }, set: { v in upd { $0[keyPath: k] = v } }) }
    private func label(_ t: String) -> some View { Text(t).font(.hk(15, .semibold)).foregroundStyle(cs.primary).padding(.top, 6).frame(maxWidth: .infinity, alignment: .leading) }
    private func hint(_ t: String) -> some View { Text(t).font(.hk(13)).foregroundStyle(cs.onSurfaceVariant).frame(maxWidth: .infinity, alignment: .leading) }
    private func big(_ t: String) -> some View { Text(t).font(.system(size: 34, weight: .black)).foregroundStyle(cs.tones.marigold.accent).frame(maxWidth: .infinity) }

    private func choice(_ l: String, _ sel: Bool, sub: String? = nil, _ f: @escaping () -> Void) -> some View {
        let tone = cs.tones.peacock
        return Button(action: f) {
            HStack {
                VStack(alignment: .leading) {
                    Text(l).font(.hk(17, sel ? .bold : .medium))
                    if let sub { Text(sub).font(.hk(13)).opacity(0.85).multilineTextAlignment(.leading) }
                }.frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: sel ? "largecircle.fill.circle" : "circle").font(.system(size: 22)).foregroundStyle(sel ? tone.accent : cs.outline)
            }.padding(.horizontal, 16).padding(.vertical, 12).frame(minHeight: 52).foregroundStyle(sel ? tone.on : cs.onSurface).card(sel ? tone.container : cs.low, radius: 20)
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(sel ? tone.accent : cs.outlineVariant, lineWidth: sel ? 2 : 1))
        }.buttonStyle(Bouncy())
    }

    private var title: String {
        let S = app.S, A = app.A
        switch page {
        case .look: return S.sectionLook; case .language: return "🌐 " + S.language; case .notify: return A.notifications; case .goal: return "🎯 " + S.sectionGoal
        case .chant: return S.sectionVag; case .reading: return "📖 " + S.sectionReading; case .check: return "🎤 " + S.sectionCheck; case .source: return S.sectionServer
        }
    }

    private var reminderSwitch: some View {
        let S = app.S, s = app.settings.value
        return SwitchRow(title: "⏰ " + plain(S.dailyReminder), sub: S.reminderAt("\(pad2(s.reminderHour)):\(pad2(s.reminderMinute))"), on: Binding(get: { app.settings.value.reminderOn }, set: { v in
            upd { $0.reminderOn = v }
            Task { if v { await Notifier.requestPermission(); notifAllowed = await Notifier.allowed() }; Notifier.refresh(app) }
        }))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                switch page {
                case .look: look
                case .language: language
                case .notify: notify
                case .goal: goal
                case .chant: chant
                case .reading: reading
                case .check: check
                case .source: source
                }
            }.padding(16).padding(.bottom, 24).readable()
        }
        .bar(title)
        .task { notifAllowed = await Notifier.allowed(); url = app.settings.value.serverUrl.isEmpty ? defaultServer : app.settings.value.serverUrl }
    }

    @ViewBuilder private var look: some View {
        let s = app.settings.value, S = app.S
        label("☀️ / 🌙")
        choice(S.themeSystem, s.darkMode == .system) { upd { $0.darkMode = .system } }
        choice(S.themeLight, s.darkMode == .light) { upd { $0.darkMode = .light } }
        choice(S.themeDark, s.darkMode == .dark) { upd { $0.darkMode = .dark } }
    }

    @ViewBuilder private var language: some View {
        let s = app.settings.value, S = app.S
        ForEach(Lang.allCases) { l in choice(l.native + "  ·  " + l.english, s.lang == l) { upd { $0.lang = l } } }
        hint(S.languageHint)
        label(S.alsoShowIn)
        AlsoScriptsPicker()
        if S.lang == .ta || s.alsoScripts.contains(.tamil) { SwitchRow(title: S.tamilSup, sub: Scripts.render("ಭಗವದ್ಭಕ್ತ", .tamil), on: bind(\.tamilSuperscripts)) }
    }

    @ViewBuilder private var notify: some View {
        let A = app.A
        if !notifAllowed {
            HStack {
                Text(A.nBlocked).font(.hk(14)).foregroundStyle(cs.onErrorContainer).frame(maxWidth: .infinity, alignment: .leading)
                FilledButton(title: A.nTurnOn) { Task { await Notifier.requestPermission(); notifAllowed = await Notifier.allowed(); if !notifAllowed { Notifier.openSystemSettings() } } }
            }.padding(14).card(cs.errorContainer, radius: 20)
        }
        reminderSwitch
        SwitchRow(title: A.nStreak, sub: A.nStreakSub, on: bind(\.notifyStreak))
        SwitchRow(title: A.nAchieve, sub: A.nAchieveSub, on: bind(\.notifyAchievements))
        SwitchRow(title: A.nDownloads, sub: A.nDownloadsSub, on: bind(\.notifyDownloads))
        SwitchRow(title: A.nUpdates, sub: A.nUpdatesSub, on: bind(\.notifyUpdates))
    }

    @ViewBuilder private var goal: some View {
        let s = app.settings.value, S = app.S
        label(S.dailyGoal)
        big(S.minutesN(s.dailyGoalMinutes))
        Slider(value: Binding(get: { Double(s.dailyGoalMinutes) }, set: { v in upd { $0.dailyGoalMinutes = Int(v / 5) * 5 } }), in: 5...60, step: 5)
        Divider()
        reminderSwitch
        if s.reminderOn {
            DatePicker("⏰ " + S.changeTime, selection: Binding(get: {
                Calendar.current.date(bySettingHour: app.settings.value.reminderHour, minute: app.settings.value.reminderMinute, second: 0, of: Date()) ?? Date()
            }, set: { d in
                let c = Calendar.current.dateComponents([.hour, .minute], from: d)
                upd { $0.reminderHour = c.hour ?? 14; $0.reminderMinute = c.minute ?? 0 }
                Notifier.refresh(app)
            }), displayedComponents: .hourAndMinute).foregroundStyle(cs.onSurface)
        }
    }

    @ViewBuilder private var chant: some View {
        let _ = app.vag.version
        let s = app.settings.value, S = app.S, have = app.vag.downloadedCount()
        hint(S.vagInfo(have, 33))
        Flow {
            FilledButton(title: "⬇ " + S.downloadAll(100), enabled: have < 33) { Task { await Notifier.requestPermission() }; app.vag.downloadAll() }
            OutlineButton(title: S.delete, enabled: have > 0) { app.vag.deleteAll() }
        }
        let running = app.vag.downloads.values.compactMap { d -> Double? in if case .running(let f) = d { return f } else { return nil } }
        if !running.isEmpty {
            HBar(value: running.reduce(0, +) / Double(running.count), height: 6, color: cs.primary, track: cs.outlineVariant)
            hint(S.downloading(Int(running.reduce(0, +) / Double(running.count) * 100)) + "  ×\(running.count)")
        }
        Divider().padding(.vertical, 6)
        label(S.speed)
        big(x2(s.speed))
        Slider(value: bind(\.speed), in: 0.5...1.5)
    }

    @ViewBuilder private var reading: some View {
        let s = app.settings.value, S = app.S
        if app.script != .kannada {
            label(S.showVerses)
            let me = Scripts.render("ಹರಿಕಥಾ", app.script)
            ForEach([(ScriptMode.both, "ಹರಿಕಥಾ + \(me)"), (.mine, me), (.kannada, "ಹರಿಕಥಾ")], id: \.0) { m, t in choice(t, s.scriptMode == m) { upd { $0.scriptMode = m } } }
            SwitchRow(title: S.kannadaPractice, sub: S.kannadaPracticeSub, on: bind(\.peekTamil))
            Divider()
        }
        label(S.textSize)
        Text("ಹರಿಕಥಾಮೃತಸಾರ").font(.hk(22 * s.fontScale)).foregroundStyle(cs.onSurface).frame(maxWidth: .infinity)
        Slider(value: bind(\.fontScale), in: 0.8...1.8)
        Divider()
        SwitchRow(title: S.keepScreenOn, sub: nil, on: bind(\.keepScreenOn))
    }

    @ViewBuilder private var check: some View {
        let s = app.settings.value, S = app.S
        EarBanner(showReady: true)
        label(S.strictness)
        ForEach(Strictness.allCases, id: \.self) { l in choice(S.strictName(l), s.strictness == l, sub: s.strictness == l ? S.strictSub(l) : nil) { upd { $0.strictness = l } } }
        Divider()
        SwitchRow(title: S.speakCorrections, sub: S.speakCorrectionsSub, on: bind(\.speakCorrections))
    }

    @ViewBuilder private var source: some View {
        let st = app.source.state, S = app.S
        hint(S.serverInfo)
        #if DEBUG
        TextField(S.serverAddress, text: $url).textFieldStyle(.roundedBorder).autocorrectionDisabled().textInputAutocapitalization(.never).keyboardType(.URL)
        #endif
        HStack(spacing: 8) {
            OutlineButton(title: S.checkNow) {
                let u = url.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                upd { $0.serverUrl = u == defaultServer ? "" : u }
                app.source.check(force: true)
            }
            Text(st.checking ? "…" : st.error != nil && st.revision == 0 ? S.serverUnreachable : st.revision > 0 ? S.serverStatus(st.revision, !st.hasUpdates) : "").font(.hk(12)).foregroundStyle(cs.onSurfaceVariant).frame(maxWidth: .infinity, alignment: .leading)
        }
        SwitchRow(title: S.autoUpdate, sub: S.autoUpdateSub, on: Binding(get: { app.settings.value.autoUpdate }, set: { v in upd { $0.autoUpdate = v }; if v { app.source.check(force: true) } }))
        UpdatesBanner()
    }
}

private enum Mode { case choose, email, phone }

struct AccountScreen: View {
    @Environment(HkApp.self) private var app
    @Environment(Nav.self) private var nav
    @Environment(\.cs) private var cs
    @Environment(\.colorScheme) private var scheme
    @State private var mode = Mode.choose
    @State private var busy = false
    @State private var error: String?
    @State private var info: String?
    @State private var confirmDelete = false
    @State private var appleDeleteDelegate: AppleDelete?

    private func message(_ e: AuthFailure?) -> String? {
        let A = app.A
        switch e?.kind {
        case nil, .cancelled: return nil
        case .network: return A.errNetwork
        case .wrongPassword: return A.errPassword
        case .weakPassword: return A.errWeak
        case .exists: return A.errExists
        case .badCode: return A.errCode
        case .needsRecentLogin: return app.I.signInAgainToDelete
        case .other: return A.errGeneric
        }
    }

    private func run(_ f: @escaping () async -> AuthFailure?) {
        busy = true; error = nil; info = nil
        Task {
            let e = await f()
            busy = false
            if let e { error = message(e) } else { mode = .choose }
        }
    }

    private func provider(_ badge: String, _ c: Color?, _ label: String, _ f: @escaping () -> Void) -> some View {
        Button(action: f) {
            HStack(spacing: 12) {
                Text(badge).font(.hk(22, .black)).foregroundStyle(c ?? cs.onSurface).frame(width: 32)
                Text(label).font(.hk(17, .semibold)).foregroundStyle(cs.onSurface).frame(maxWidth: .infinity, alignment: .leading)
            }.padding(.horizontal, 16).frame(minHeight: 56).card(cs.lowest, radius: 20).overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(cs.outlineVariant))
        }.buttonStyle(Bouncy())
    }

    /// Apple users re-confirm so the Sign in with Apple token can be revoked as part of deletion.
    private func deleteTapped() {
        if app.account.usesApple {
            let rq = ASAuthorizationAppleIDProvider().createRequest()
            rq.nonce = app.account.newAppleNonce()
            let d = AppleDelete { r in
                if case .success(let a) = r { run { await app.account.deleteAccount(appleAuthorization: a) } }
                else { error = app.A.errGeneric }
            }
            appleDeleteDelegate = d
            let c = ASAuthorizationController(authorizationRequests: [rq]); c.delegate = d; c.presentationContextProvider = d
            c.performRequests()
        } else { run { await app.account.deleteAccount() } }
    }

    var body: some View {
        let A = app.A, S = app.S, u = app.account.user, t = cs.tones
        ScrollView {
            VStack(spacing: 12) {
                if let u, !u.anonymous {
                    Avatar(label: u.label, size: 96)
                    Text(u.label ?? A.signedIn).font(.hk(24, .bold)).multilineTextAlignment(.center).foregroundStyle(cs.onSurface)
                    ForEach(Array(Set([u.email, u.phone].compactMap { $0 })), id: \.self) { Text($0).foregroundStyle(cs.onSurfaceVariant) }
                    HStack {
                        Text(syncLine(A, app.sync.status)).font(.hk(16, .semibold)).frame(maxWidth: .infinity, alignment: .leading)
                        Button(A.syncNow) { app.sync.syncNow() }.font(.hk(15, .semibold))
                    }.padding(14).foregroundStyle(t.peacock.on).card(t.peacock.container, radius: 20)
                    OutlineButton(title: A.signOut, fill: true, height: 52) { app.account.signOut() }.padding(.top, 8)
                    Button(A.deleteAccount) { confirmDelete = true }.foregroundStyle(cs.error).frame(minHeight: 44)
                } else {
                    BrandLogo(size: 96)
                    Text(A.heroTitle).font(.hk(24, .bold)).multilineTextAlignment(.center).foregroundStyle(cs.onSurface)
                    Text(u?.anonymous == true ? A.saveGuest : A.heroBody).multilineTextAlignment(.center).foregroundStyle(cs.onSurfaceVariant)
                    if !app.account.available {
                        Text(A.notReady).multilineTextAlignment(.center).padding(16).foregroundStyle(t.marigold.on).card(t.marigold.container, radius: 20)
                        FilledButton(title: S.ok, height: 52, fill: true) { nav.back() }
                    } else {
                        switch mode {
                        case .choose: chooser(u)
                        case .email: EmailForm(busy: busy, onBack: { mode = .choose; error = nil }, onSignIn: { e, p in run { await app.account.signInEmail(e, p) } }, onSignUp: { n, e, p in run { await app.account.signUpEmail(n, e, p) } }, onForgot: { e in
                            busy = true; error = nil
                            Task { let f = await app.account.resetPassword(e); if let f { error = message(f) } else { info = A.resetSent }; busy = false }
                        })
                        case .phone: PhoneForm(busy: $busy, onError: { error = message($0) }, onBack: { mode = .choose; error = nil }, onDone: { mode = .choose })
                        }
                    }
                }
                if busy { ProgressView() }
                if let error { Text(error).font(.hk(16, .semibold)).foregroundStyle(cs.error).multilineTextAlignment(.center) }
                if let info { Text(info).font(.hk(16, .semibold)).foregroundStyle(HK.good).multilineTextAlignment(.center) }
            }.padding(20).readable(520)
        }
        .bar(A.account)
        .scrollDismissesKeyboard(.interactively)
        .alert(A.deleteAccount, isPresented: $confirmDelete) {
            Button(A.deleteAccount, role: .destructive, action: deleteTapped)
            Button(S.cancel, role: .cancel) {}
        } message: { Text(A.deleteQ) }
    }

    @ViewBuilder private func chooser(_ u: UserInfo?) -> some View {
        let A = app.A
        // Sign in with Apple sits first: required beside Google sign-in (App Store guideline 4.8). The button's text is localized by iOS.
        SignInWithAppleButton(.continue) { rq in
            rq.requestedScopes = [.fullName, .email]
            rq.nonce = app.account.newAppleNonce()
        } onCompletion: { r in run { await app.account.signInApple(r) } }
        .signInWithAppleButtonStyle(scheme == .dark ? .white : .black).frame(height: 56).clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        if app.account.googleAvailable { provider("G", Color(hex: 0x4285F4), A.withGoogle) { run { await app.account.signInGoogle() } } }
        provider("✉️", nil, A.withEmail) { mode = .email; error = nil }
        provider("📱", nil, A.withPhone) { mode = .phone; error = nil }
        if u == nil {
            HStack { Rectangle().fill(cs.outlineVariant).frame(height: 1); Text(A.or).foregroundStyle(cs.onSurfaceVariant); Rectangle().fill(cs.outlineVariant).frame(height: 1) }.padding(.vertical, 4)
            Button { run { await app.account.signInGuest() } } label: { Text("👤 " + A.asGuest).font(.hk(16, .semibold)).frame(maxWidth: .infinity, minHeight: 52) }
            Text(A.guestNote).font(.hk(13)).foregroundStyle(cs.onSurfaceVariant).multilineTextAlignment(.center)
        }
    }
}

private final class AppleDelete: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    let done: (Result<ASAuthorization, Error>) -> Void
    init(_ d: @escaping (Result<ASAuthorization, Error>) -> Void) { done = d }
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization a: ASAuthorization) { done(.success(a)) }
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError e: Error) { done(.failure(e)) }
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated { Account.topController()?.view.window ?? ASPresentationAnchor() }
    }
}

private struct EmailForm: View {
    let busy: Bool
    let onBack: () -> Void, onSignIn: (String, String) -> Void, onSignUp: (String, String, String) -> Void, onForgot: (String) -> Void
    @Environment(HkApp.self) private var app
    @State private var create = false
    @State private var name = ""
    @State private var email = ""
    @State private var pass = ""
    var body: some View {
        let A = app.A
        if create { TextField(A.yourName, text: $name).textContentType(.name).hkField() }
        TextField(A.email, text: $email).textContentType(.emailAddress).keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled().hkField()
        SecureField(A.password, text: $pass).textContentType(create ? .newPassword : .password).hkField()
        FilledButton(title: create ? A.createAccount : A.signIn, height: 52, fill: true, size: 17, enabled: !busy && email.contains("@") && pass.count >= 6) {
            if create { onSignUp(name, email, pass) } else { onSignIn(email, pass) }
        }
        Button(create ? A.haveAccount : A.newHere) { create.toggle() }.frame(minHeight: 44)
        if !create { Button(A.forgot) { onForgot(email) }.disabled(!email.contains("@")).frame(minHeight: 44) }
        Button("‹ " + app.T.prev, action: onBack).frame(minHeight: 44)
    }
}

private struct PhoneForm: View {
    @Binding var busy: Bool
    let onError: (AuthFailure) -> Void, onBack: () -> Void, onDone: () -> Void
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @State private var cc = "+91"
    @State private var number = ""
    @State private var code = ""
    @State private var vid: String?
    private var full: String { cc.trimmingCharacters(in: .whitespaces) + number.filter(\.isNumber) }
    private func send() {
        busy = true
        Task {
            switch await app.account.sendPhoneCode(full) {
            case .success(let id): vid = id
            case .failure(let e): onError(e)
            }
            busy = false
        }
    }
    var body: some View {
        let A = app.A
        if vid == nil {
            HStack(spacing: 8) {
                TextField("+91", text: $cc).keyboardType(.phonePad).hkField().frame(width: 92)
                TextField(A.phoneNumber, text: $number).keyboardType(.phonePad).textContentType(.telephoneNumber).hkField()
            }
            FilledButton(title: A.sendCode, height: 52, fill: true, size: 17, enabled: !busy && number.filter(\.isNumber).count >= 6, action: send)
        } else {
            Text(A.codeSent(full)).foregroundStyle(cs.onSurfaceVariant).multilineTextAlignment(.center)
            TextField(A.smsCode, text: $code).keyboardType(.numberPad).textContentType(.oneTimeCode).multilineTextAlignment(.center).font(.hk(24, .bold)).hkField()
                .onChange(of: code) { _, c in code = String(c.filter(\.isNumber).prefix(6)) }
            FilledButton(title: A.verify, height: 52, fill: true, size: 17, enabled: !busy && code.count == 6) {
                busy = true
                Task {
                    if let e = await app.account.verifyPhone(vid!, code) { onError(e) } else { onDone() }
                    busy = false
                }
            }
            Button(A.sendCode, action: send).disabled(busy).frame(minHeight: 44)
        }
        Button("‹ " + app.T.prev) { if vid != nil { vid = nil; code = "" } else { onBack() } }.frame(minHeight: 44)
    }
}

private extension View {
    func hkField() -> some View { padding(14).frame(minHeight: 52).overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.gray.opacity(0.5))) }
}
