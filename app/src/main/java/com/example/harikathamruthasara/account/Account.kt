package com.example.harikathamruthasara.account

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.harikathamruthasara.HkApp
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Who is signed in (null = nobody: everything is local only). */
data class UserInfo(
    val uid: String, val name: String?, val email: String?, val phone: String?, val photo: String?,
    val anonymous: Boolean, val providers: List<String>,
) {
    /** what to show: name, else email, else phone */
    val label: String? get() = name?.takeIf { it.isNotBlank() } ?: email ?: phone
}

/** Why a sign-in failed, for a friendly message in the reader's language. */
enum class AuthError { NETWORK, WRONG_PASSWORD, WEAK_PASSWORD, EXISTS, BAD_CODE, CANCELLED, OTHER }

class AuthFailure(val kind: AuthError, cause: Throwable? = null) : Exception(cause)

/**
 * Sign-in with Google, email + password, phone number (SMS code) or as a guest, all through Firebase Auth.
 * A guest who later signs in keeps everything: the guest account is upgraded (linked) when possible, and in any
 * case the phone's data is merged into the account by [CloudSync].
 *
 * If the build has no Firebase config (google-services.json), [available] is false and the app stays local-only.
 */
class Account(private val app: HkApp) {
    val available: Boolean = FirebaseApp.getApps(app).isNotEmpty()
    private val auth: FirebaseAuth? = if (available) FirebaseAuth.getInstance() else null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _user = MutableStateFlow(auth?.currentUser?.toInfo())
    val user: StateFlow<UserInfo?> = _user

    /** The Google web client id: present once Google sign-in is switched on for the Firebase project. */
    val googleClientId: String? = app.resources.getIdentifier("default_web_client_id", "string", app.packageName)
        .takeIf { it != 0 }?.let { app.getString(it) }
    val googleAvailable get() = available && googleClientId != null

    init {
        auth?.addAuthStateListener { a -> _user.value = a.currentUser?.toInfo() }
    }

    private fun com.google.firebase.auth.FirebaseUser.toInfo() = UserInfo(
        uid, displayName, email, phoneNumber, photoUrl?.toString(), isAnonymous,
        providerData.map { it.providerId }.filter { it != "firebase" },
    )

    private fun refresh() { _user.value = auth?.currentUser?.toInfo() }

    private fun map(e: Throwable): AuthFailure = when (e) {
        is AuthFailure -> e
        is FirebaseNetworkException -> AuthFailure(AuthError.NETWORK, e)
        is FirebaseAuthWeakPasswordException -> AuthFailure(AuthError.WEAK_PASSWORD, e)
        is FirebaseAuthUserCollisionException -> AuthFailure(AuthError.EXISTS, e)
        is FirebaseAuthInvalidCredentialsException ->
            if (e.errorCode.contains("CODE", ignoreCase = true)) AuthFailure(AuthError.BAD_CODE, e) else AuthFailure(AuthError.WRONG_PASSWORD, e)
        is androidx.credentials.exceptions.GetCredentialCancellationException -> AuthFailure(AuthError.CANCELLED, e)
        else -> AuthFailure(AuthError.OTHER, e)
    }

    /** Signs in with [cred]; a guest is upgraded in place, so the same cloud data carries on. */
    private suspend fun signInWith(cred: AuthCredential) {
        val a = auth ?: throw AuthFailure(AuthError.OTHER)
        val cur = a.currentUser
        if (cur != null && cur.isAnonymous) {
            try { cur.linkWithCredential(cred).await(); refresh(); return }
            catch (_: FirebaseAuthUserCollisionException) { /* that account exists: sign in to it; CloudSync merges */ }
        }
        a.signInWithCredential(cred).await()
        refresh()
    }

    private suspend fun <T> guard(block: suspend () -> T): Result<T> =
        try { Result.success(block()) } catch (e: Throwable) { Result.failure(map(e)) }

    // ---------------------------------------------------------------- Google
    suspend fun signInGoogle(activity: Activity): Result<Unit> = guard {
        val id = googleClientId ?: throw AuthFailure(AuthError.OTHER)
        val req = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(id).build()).build()
        val c = CredentialManager.create(activity).getCredential(activity, req).credential
        if (c !is CustomCredential || c.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw AuthFailure(AuthError.OTHER)
        val g = GoogleIdTokenCredential.createFrom(c.data)
        signInWith(GoogleAuthProvider.getCredential(g.idToken, null))
    }

    // ---------------------------------------------------------------- email
    suspend fun signInEmail(email: String, password: String): Result<Unit> = guard {
        val a = auth ?: throw AuthFailure(AuthError.OTHER)
        val cur = a.currentUser
        if (cur != null && cur.isAnonymous) {
            // an existing email account: sign in to it (the guest's data is merged by CloudSync)
            a.signInWithEmailAndPassword(email.trim(), password).await()
        } else a.signInWithEmailAndPassword(email.trim(), password).await()
        refresh()
    }

    suspend fun signUpEmail(name: String, email: String, password: String): Result<Unit> = guard {
        val a = auth ?: throw AuthFailure(AuthError.OTHER)
        val cur = a.currentUser
        val user = if (cur != null && cur.isAnonymous) cur.linkWithCredential(EmailAuthProvider.getCredential(email.trim(), password)).await().user
        else a.createUserWithEmailAndPassword(email.trim(), password).await().user
        if (name.isNotBlank()) user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())?.await()
        user?.reload()?.await()
        refresh()
    }

    suspend fun resetPassword(email: String): Result<Unit> = guard {
        auth?.sendPasswordResetEmail(email.trim())?.await() ?: throw AuthFailure(AuthError.OTHER)
        Unit
    }

    // ---------------------------------------------------------------- phone (SMS code)
    /**
     * Sends the SMS code to [phone] (with country code, e.g. +91…). [onCodeSent] gets the verification id for
     * [verifyPhoneCode]; on many phones Android reads the SMS itself and [onDone] is called straight away.
     */
    fun sendPhoneCode(
        activity: Activity, phone: String, resend: PhoneAuthProvider.ForceResendingToken?,
        onCodeSent: (String, PhoneAuthProvider.ForceResendingToken) -> Unit,
        onDone: (Result<Unit>) -> Unit,
    ) {
        val a = auth ?: return onDone(Result.failure(AuthFailure(AuthError.OTHER)))
        val cb = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(cred: PhoneAuthCredential) { scope.launch { onDone(guard { signInWith(cred) }) } }
            override fun onVerificationFailed(e: FirebaseException) { onDone(Result.failure(map(e))) }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) { onCodeSent(id, token) }
        }
        val b = PhoneAuthOptions.newBuilder(a).setPhoneNumber(phone).setTimeout(60L, TimeUnit.SECONDS).setActivity(activity).setCallbacks(cb)
        if (resend != null) b.setForceResendingToken(resend)
        PhoneAuthProvider.verifyPhoneNumber(b.build())
    }

    suspend fun verifyPhoneCode(verificationId: String, code: String): Result<Unit> = guard {
        signInWith(PhoneAuthProvider.getCredential(verificationId, code.trim()))
    }

    // ---------------------------------------------------------------- guest
    suspend fun signInGuest(): Result<Unit> = guard {
        val a = auth ?: throw AuthFailure(AuthError.OTHER)
        if (a.currentUser == null) a.signInAnonymously().await()
        refresh()
    }

    // ---------------------------------------------------------------- leaving
    /** Signs out; the phone keeps its progress (it simply stops syncing). */
    suspend fun signOut(context: Context) {
        app.sync.stop()
        auth?.signOut()
        if (Build.VERSION.SDK_INT >= 23) runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        refresh()
    }

    /** Deletes the cloud copy and the account (Google Play requires apps with accounts to offer this). */
    suspend fun deleteAccount(): Result<Unit> = guard {
        val u = auth?.currentUser ?: throw AuthFailure(AuthError.OTHER)
        app.sync.deleteCloudData(u.uid)
        app.sync.stop()
        u.delete().await()
        refresh()
    }
}
