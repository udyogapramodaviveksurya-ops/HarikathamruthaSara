package com.example.harikathamruthasara.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.account.AuthError
import com.example.harikathamruthasara.account.AuthFailure
import com.example.harikathamruthasara.i18n.AccountText
import com.example.harikathamruthasara.i18n.strings
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.launch

private enum class Mode { CHOOSE, EMAIL, PHONE }

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun AccountText.message(e: Throwable?): String? = when ((e as? AuthFailure)?.kind) {
    null -> if (e == null) null else errGeneric
    AuthError.NETWORK -> errNetwork
    AuthError.WRONG_PASSWORD -> errPassword
    AuthError.WEAK_PASSWORD -> errWeak
    AuthError.EXISTS -> errExists
    AuthError.BAD_CODE -> errCode
    AuthError.CANCELLED -> null
    AuthError.OTHER -> errGeneric
}

/**
 * Sign in / create account / guest, and — once signed in — the profile with sync status, sign-out and delete.
 * [onboarding]: shown once after the language is chosen; leaving it in any way counts as "offered".
 */
@Composable
fun AccountScreen(app: HkApp, nav: Nav) {
    val A = accountText()
    val S = strings()
    val user by app.account.user.collectAsStateWithLifecycle()
    val sync by app.sync.status.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(Mode.CHOOSE) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val t = tones

    fun run(block: suspend () -> Result<Unit>) {
        busy = true; error = null; info = null
        scope.launch {
            val r = block()
            busy = false
            r.onSuccess { mode = Mode.CHOOSE }.onFailure { error = A.message(it) }
        }
    }

    Scaffold(topBar = {
        BackBar(A.account, nav)
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).readable(520.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val u = user
            if (u != null && !u.anonymous) {
                // ---------------------------------------------------------------- signed in
                Avatar(u.label, 96)
                Text(u.label ?: A.signedIn, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                listOfNotNull(u.email, u.phone).distinct().forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Surface(shape = MaterialTheme.shapes.medium, color = t.peacock.container, contentColor = t.peacock.on, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(syncLine(A, sync), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { app.sync.syncNow() }) { Text(A.syncNow) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { scope.launch { app.account.signOut(ctx) } }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(A.signOut, fontSize = 16.sp) }
                TextButton(onClick = { confirmDelete = true }) { Text(A.deleteAccount, color = MaterialTheme.colorScheme.error) }
            } else {
                // ---------------------------------------------------------------- signed out / guest
                BrandLogo(96.dp)
                Text(A.heroTitle, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(if (u?.anonymous == true) A.saveGuest else A.heroBody, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                if (!app.account.available) {
                    Surface(shape = MaterialTheme.shapes.medium, color = t.marigold.container, contentColor = t.marigold.on) {
                        Text(A.notReady, Modifier.padding(16.dp), textAlign = TextAlign.Center)
                    }
                    Button(onClick = { nav.back() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(S.ok) }
                } else AnimatedContent(mode, label = "mode") { m ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        when (m) {
                            Mode.CHOOSE -> {
                                if (app.account.googleAvailable) ProviderButton("G", Color(0xFF4285F4), A.withGoogle) {
                                    val act = ctx.activity() ?: return@ProviderButton
                                    run { app.account.signInGoogle(act) }
                                }
                                ProviderButton("✉️", null, A.withEmail) { mode = Mode.EMAIL }
                                ProviderButton("📱", null, A.withPhone) { mode = Mode.PHONE }
                                if (u == null) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                        HorizontalDivider(Modifier.weight(1f)); Text("  ${A.or}  ", color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(Modifier.weight(1f))
                                    }
                                    TextButton(onClick = { run { app.account.signInGuest() } }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("👤 " + A.asGuest, fontSize = 16.sp) }
                                    Text(A.guestNote, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                            Mode.EMAIL -> EmailForm(A, busy, onBack = { mode = Mode.CHOOSE; error = null },
                                onSignIn = { e, p -> run { app.account.signInEmail(e, p) } },
                                onSignUp = { n, e, p -> run { app.account.signUpEmail(n, e, p) } },
                                onForgot = { e ->
                                    busy = true; error = null
                                    scope.launch { app.account.resetPassword(e).onSuccess { info = A.resetSent }.onFailure { error = A.message(it) }; busy = false }
                                })
                            Mode.PHONE -> PhoneForm(app, A, busy, setBusy = { busy = it }, onError = { error = A.message(it) },
                                onBack = { mode = Mode.CHOOSE; error = null }, onDone = { mode = Mode.CHOOSE })
                        }
                    }
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(32.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) }
            info?.let { Text(it, color = HkColors.good, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(A.deleteAccount) }, text = { Text(A.deleteQ) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; run { app.account.deleteAccount() } }) { Text(A.deleteAccount, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(S.cancel) } },
    )
}


/** A big, full-width sign-in button with a round badge for the provider. */
@Composable
private fun ProviderButton(badge: String, badgeColor: Color?, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = cs.surfaceContainerLowest, border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(badge, fontSize = 22.sp, fontWeight = FontWeight.Black, color = badgeColor ?: cs.onSurface, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmailForm(
    A: AccountText, busy: Boolean, onBack: () -> Unit,
    onSignIn: (String, String) -> Unit, onSignUp: (String, String, String) -> Unit, onForgot: (String) -> Unit,
) {
    var create by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    if (create) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(A.yourName) }, singleLine = true)
    OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth(), label = { Text(A.email) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text(A.password) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    Button(onClick = { if (create) onSignUp(name, email, pass) else onSignIn(email, pass) }, enabled = !busy && email.contains('@') && pass.length >= 6,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (create) A.createAccount else A.signIn, fontSize = 17.sp) }
    TextButton(onClick = { create = !create }) { Text(if (create) A.haveAccount else A.newHere) }
    if (!create) TextButton(onClick = { onForgot(email) }, enabled = email.contains('@')) { Text(A.forgot) }
    TextButton(onClick = onBack) { Text("‹ " + tourText().prev) }
}

@Composable
private fun PhoneForm(
    app: HkApp, A: AccountText, busy: Boolean, setBusy: (Boolean) -> Unit, onError: (Throwable) -> Unit,
    onBack: () -> Unit, onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cc by rememberSaveable { mutableStateOf("+91") }
    var number by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var vid by rememberSaveable { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    val full = cc.trim() + number.filter { it.isDigit() }
    fun send(resend: Boolean) {
        val act = ctx.activity() ?: return
        setBusy(true)
        app.account.sendPhoneCode(act, full, if (resend) token else null,
            onCodeSent = { id, tk -> vid = id; token = tk; setBusy(false) },
            onDone = { r -> setBusy(false); r.onSuccess { onDone() }.onFailure(onError) })
    }
    if (vid == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(cc, { cc = it }, Modifier.width(92.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(number, { number = it }, Modifier.weight(1f), label = { Text(A.phoneNumber) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        }
        Button(onClick = { send(false) }, enabled = !busy && number.count { it.isDigit() } >= 6, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(A.sendCode, fontSize = 17.sp) }
    } else {
        Text(A.codeSent(full), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(code, { code = it.filter { c -> c.isDigit() }.take(6) }, Modifier.fillMaxWidth(), label = { Text(A.smsCode) }, singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Button(onClick = {
            setBusy(true)
            scope.launch { app.account.verifyPhoneCode(vid!!, code).onSuccess { onDone() }.onFailure(onError); setBusy(false) }
        }, enabled = !busy && code.length == 6, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(A.verify, fontSize = 17.sp) }
        TextButton(onClick = { send(true) }, enabled = !busy) { Text(A.sendCode) }
    }
    TextButton(onClick = { if (vid != null) { vid = null; code = "" } else onBack() }) { Text("‹ " + tourText().prev) }
}
