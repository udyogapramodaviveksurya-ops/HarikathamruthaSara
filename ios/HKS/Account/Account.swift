import Foundation
import UIKit
import Observation
import CryptoKit
import AuthenticationServices
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore
import FirebaseMessaging
import GoogleSignIn

struct UserInfo: Equatable {
    let uid: String; let name: String?; let email: String?; let phone: String?; let photo: URL?; let anonymous: Bool; let providers: [String]
    var label: String? { (name?.isEmpty == false ? name : nil) ?? email ?? phone }
}

enum AuthErrorKind { case network, wrongPassword, weakPassword, exists, badCode, cancelled, needsRecentLogin, other }
struct AuthFailure: Error { let kind: AuthErrorKind }

/// Sign-in through Firebase Auth: Apple, Google, email + password, phone (SMS) or guest. A guest who signs in is
/// upgraded in place when possible; in any case the phone's data is merged into the account by CloudSync.
@MainActor @Observable
final class Account {
    private(set) var user: UserInfo?
    @ObservationIgnored let available = FirebaseApp.app() != nil
    @ObservationIgnored private var auth: Auth? { available ? Auth.auth() : nil }
    @ObservationIgnored private var handle: AuthStateDidChangeListenerHandle?
    @ObservationIgnored var beforeSignOut: () -> Void = {}
    @ObservationIgnored var deleteCloud: (String) async -> Void = { _ in }
    @ObservationIgnored var currentNonce: String?

    var googleAvailable: Bool { available && FirebaseApp.app()?.options.clientID != nil }

    init() {
        guard available else { return }
        user = Auth.auth().currentUser.map(Self.info)
        handle = Auth.auth().addStateDidChangeListener { [weak self] _, u in
            MainActor.assumeIsolated { self?.user = u.map(Self.info) }
        }
    }

    private static func info(_ u: User) -> UserInfo {
        UserInfo(uid: u.uid, name: u.displayName, email: u.email, phone: u.phoneNumber, photo: u.photoURL, anonymous: u.isAnonymous, providers: u.providerData.map(\.providerID))
    }
    private func refresh() { user = auth?.currentUser.map(Self.info) }

    private func map(_ e: Error) -> AuthFailure {
        if let a = e as? AuthFailure { return a }
        let n = e as NSError
        if n.domain == ASAuthorizationError.errorDomain, n.code == ASAuthorizationError.canceled.rawValue { return AuthFailure(kind: .cancelled) }
        if n.domain == "com.google.GIDSignIn", n.code == -5 { return AuthFailure(kind: .cancelled) }
        switch AuthErrorCode(rawValue: n.code) {
        case .networkError: return AuthFailure(kind: .network)
        case .weakPassword: return AuthFailure(kind: .weakPassword)
        case .emailAlreadyInUse, .credentialAlreadyInUse, .accountExistsWithDifferentCredential: return AuthFailure(kind: .exists)
        case .wrongPassword, .invalidCredential, .invalidEmail, .userNotFound: return AuthFailure(kind: .wrongPassword)
        case .invalidVerificationCode, .invalidVerificationID, .missingVerificationCode: return AuthFailure(kind: .badCode)
        case .requiresRecentLogin: return AuthFailure(kind: .needsRecentLogin)
        default: return AuthFailure(kind: .other)
        }
    }

    private func signInWith(_ cred: AuthCredential) async throws {
        guard let a = auth else { throw AuthFailure(kind: .other) }
        if let cur = a.currentUser, cur.isAnonymous {
            do { _ = try await cur.link(with: cred); try? await cur.reload(); refresh(); return }
            catch { if (error as NSError).code != AuthErrorCode.credentialAlreadyInUse.rawValue { throw error } }
        }
        _ = try await a.signIn(with: cred)
        refresh()
    }

    private func guarded(_ f: () async throws -> Void) async -> AuthFailure? {
        do { try await f(); return nil } catch { return map(error) }
    }

    // ---- Apple
    func newAppleNonce() -> String {
        var b = [UInt8](repeating: 0, count: 24)
        _ = SecRandomCopyBytes(kSecRandomDefault, b.count, &b)
        let raw = b.map { String(format: "%02x", $0) }.joined()
        currentNonce = raw
        return SHA256.hash(data: Data(raw.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    func signInApple(_ result: Result<ASAuthorization, Error>) async -> AuthFailure? {
        await guarded {
            let auth = try result.get()
            guard let c = auth.credential as? ASAuthorizationAppleIDCredential, let t = c.identityToken, let token = String(data: t, encoding: .utf8), let nonce = currentNonce else { throw AuthFailure(kind: .other) }
            let cred = OAuthProvider.appleCredential(withIDToken: token, rawNonce: nonce, fullName: c.fullName)
            try await signInWith(cred)
            if let n = c.fullName, let display = PersonNameComponentsFormatter().string(for: n), !display.isEmpty, self.auth?.currentUser?.displayName == nil {
                let rq = self.auth?.currentUser?.createProfileChangeRequest()
                rq?.displayName = display
                try? await rq?.commitChanges()
                refresh()
            }
        }
    }

    // ---- Google
    func signInGoogle() async -> AuthFailure? {
        await guarded {
            guard let id = FirebaseApp.app()?.options.clientID, let vc = Self.topController() else { throw AuthFailure(kind: .other) }
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: id)
            let r = try await GIDSignIn.sharedInstance.signIn(withPresenting: vc)
            guard let t = r.user.idToken?.tokenString else { throw AuthFailure(kind: .other) }
            try await signInWith(GoogleAuthProvider.credential(withIDToken: t, accessToken: r.user.accessToken.tokenString))
        }
    }

    static func topController() -> UIViewController? {
        var vc = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first?.rootViewController
        while let p = vc?.presentedViewController { vc = p }
        return vc
    }

    // ---- email
    func signInEmail(_ email: String, _ pass: String) async -> AuthFailure? {
        await guarded {
            guard let a = auth else { throw AuthFailure(kind: .other) }
            _ = try await a.signIn(withEmail: email.trimmingCharacters(in: .whitespaces), password: pass)
            refresh()
        }
    }

    func signUpEmail(_ name: String, _ email: String, _ pass: String) async -> AuthFailure? {
        await guarded {
            guard let a = auth else { throw AuthFailure(kind: .other) }
            let e = email.trimmingCharacters(in: .whitespaces)
            let u: User
            if let cur = a.currentUser, cur.isAnonymous { u = try await cur.link(with: EmailAuthProvider.credential(withEmail: e, password: pass)).user }
            else { u = try await a.createUser(withEmail: e, password: pass).user }
            if !name.trimmingCharacters(in: .whitespaces).isEmpty {
                let r = u.createProfileChangeRequest(); r.displayName = name.trimmingCharacters(in: .whitespaces)
                try await r.commitChanges()
            }
            try await u.reload()
            refresh()
        }
    }

    func resetPassword(_ email: String) async -> AuthFailure? {
        await guarded {
            guard let a = auth else { throw AuthFailure(kind: .other) }
            try await a.sendPasswordReset(withEmail: email.trimmingCharacters(in: .whitespaces))
        }
    }

    // ---- phone
    func sendPhoneCode(_ phone: String) async -> Result<String, AuthFailure> {
        do { return .success(try await PhoneAuthProvider.provider().verifyPhoneNumber(phone, uiDelegate: nil)) }
        catch { return .failure(map(error)) }
    }

    func verifyPhone(_ id: String, _ code: String) async -> AuthFailure? {
        await guarded { try await signInWith(PhoneAuthProvider.provider().credential(withVerificationID: id, verificationCode: code)) }
    }

    // ---- guest
    func signInGuest() async -> AuthFailure? {
        await guarded {
            guard let a = auth else { throw AuthFailure(kind: .other) }
            if a.currentUser == nil { _ = try await a.signInAnonymously() }
            refresh()
        }
    }

    // ---- leaving
    func signOut() {
        beforeSignOut()
        try? auth?.signOut()
        GIDSignIn.sharedInstance.signOut()
        refresh()
    }

    /// Deletes the cloud copy and the account (App Store guideline 5.1.1(v)). Apple users confirm with Apple once
    /// more, so the app's Sign in with Apple token can be revoked as Apple requires.
    func deleteAccount(appleAuthorization: ASAuthorization? = nil) async -> AuthFailure? {
        await guarded {
            guard let a = auth, let u = a.currentUser else { throw AuthFailure(kind: .other) }
            if let c = appleAuthorization?.credential as? ASAuthorizationAppleIDCredential, let t = c.identityToken, let token = String(data: t, encoding: .utf8), let nonce = currentNonce {
                _ = try await u.reauthenticate(with: OAuthProvider.appleCredential(withIDToken: token, rawNonce: nonce, fullName: nil))
                if let code = c.authorizationCode.flatMap({ String(data: $0, encoding: .utf8) }) { try await a.revokeToken(withAuthorizationCode: code) }
            }
            await deleteCloud(u.uid)
            beforeSignOut()
            try await u.delete()
            refresh()
        }
    }
    var usesApple: Bool { user?.providers.contains("apple.com") == true }
}

enum SyncStatus: Equatable { case off, syncing, synced(Date), failed }

/// Keeps progress and settings in Firestore at users/{uid}; same document shape as Android, so devices of both kinds share it.
@MainActor @Observable
final class CloudSync {
    private(set) var status = SyncStatus.off
    @ObservationIgnored private let progress: ProgressStore
    @ObservationIgnored private let settings: SettingsStore
    @ObservationIgnored private let account: Account
    @ObservationIgnored private var db: Firestore? { account.available ? Firestore.firestore() : nil }
    @ObservationIgnored private var listener: ListenerRegistration?
    @ObservationIgnored private var pushLoop: Task<Void, Never>?
    @ObservationIgnored private var uid: String?
    @ObservationIgnored private var lastProgress: String?
    @ObservationIgnored private var lastSettingsAt = -1

    init(progress: ProgressStore, settings: SettingsStore, account: Account) {
        self.progress = progress; self.settings = settings; self.account = account
        account.beforeSignOut = { [weak self] in self?.stop() }
        account.deleteCloud = { [weak self] u in try? await self?.doc(u)?.delete() }
    }

    private func doc(_ u: String) -> DocumentReference? { db?.collection("users").document(u) }

    /// Called whenever the signed-in user changes.
    func userChanged() {
        guard let u = account.user else { stop(); return }
        if u.uid != uid { start(u) }
    }

    private func start(_ u: UserInfo) {
        stop()
        uid = u.uid
        status = .syncing
        Task { await pullThenPush() }
        listener = doc(u.uid)?.addSnapshotListener { [weak self] snap, err in
            MainActor.assumeIsolated {
                guard let self else { return }
                if err != nil { self.status = .failed; return }
                guard let snap, !snap.metadata.hasPendingWrites else { return }
                self.apply(snap)
            }
        }
        pushLoop = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(4))
                if Task.isCancelled { return }
                await self?.push(force: false)
            }
        }
        Messaging.messaging().token { [weak self] t, _ in
            guard let t else { return }
            MainActor.assumeIsolated { self?.saveToken(t) }
        }
    }

    func stop() {
        listener?.remove(); listener = nil
        pushLoop?.cancel(); pushLoop = nil
        uid = nil; lastProgress = nil; lastSettingsAt = -1
        status = .off
    }

    private func pullThenPush() async {
        guard let u = uid, let d = doc(u) else { return }
        do {
            let snap = try await d.getDocument()
            apply(snap)
            await push(force: true)
        } catch { status = .failed }
    }

    private func apply(_ snap: DocumentSnapshot) {
        let data = snap.data() ?? [:]
        if let p = data["progress"] as? String { progress.mergeFrom(p) }
        let at = (data["settingsAt"] as? NSNumber)?.intValue ?? 0
        let first = settings.syncedUid != uid
        if let s = data["settings"] as? String, first || at > settings.changedAt { settings.importJson(s, at: max(at, 1)) }
        if let uid { settings.syncedUid = uid }
    }

    private func push(force: Bool) async {
        guard let u = uid, let d = doc(u) else { return }
        let p = progress.exportJson(), sAt = settings.changedAt
        if !force && p == lastProgress && sAt == lastSettingsAt { return }
        status = .syncing
        var data: [String: Any] = ["progress": p, "settings": settings.exportJson(), "settingsAt": sAt, "updatedAt": FieldValue.serverTimestamp()]
        if let i = account.user { if let v = i.name { data["name"] = v }; if let v = i.email { data["email"] = v }; if let v = i.phone { data["phone"] = v } }
        do {
            try await d.setData(data, merge: true)
            lastProgress = p; lastSettingsAt = sAt
            status = .synced(Date())
        } catch { status = .failed }
    }

    func syncNow() { guard account.user != nil else { return }; Task { await pullThenPush() } }

    func saveToken(_ t: String) {
        guard let u = uid, let d = doc(u) else { return }
        Task { try? await d.setData(["tokens": FieldValue.arrayUnion([t])], merge: true) }
    }
}
