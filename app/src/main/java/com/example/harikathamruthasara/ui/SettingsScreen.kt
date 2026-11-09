package com.example.harikathamruthasara.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.account.SyncStatus
import com.example.harikathamruthasara.data.ColorStyle
import com.example.harikathamruthasara.data.DarkMode
import com.example.harikathamruthasara.data.Script
import com.example.harikathamruthasara.data.ScriptMode
import com.example.harikathamruthasara.data.Scripts
import com.example.harikathamruthasara.data.Settings
import com.example.harikathamruthasara.i18n.AccountText
import com.example.harikathamruthasara.i18n.Lang
import com.example.harikathamruthasara.i18n.strings
import com.example.harikathamruthasara.notify.Notifier
import com.example.harikathamruthasara.reminder.Reminders

/** The settings, split into small pages so no screen is crowded. */
enum class SettingsPage { LOOK, LANGUAGE, NOTIFY, GOAL, CHANT, READING, CHECK, SOURCE, ABOUT }

@Composable
fun accountText(): AccountText { val l = strings().lang; return remember(l) { AccountText(l) } }

/** "🎨 தோற்றம் & நிறங்கள் · Look & colours" → "தோற்றம் & நிறங்கள்" (rows have their own icon, and only the reader's language). */
private fun plain(x: String) = x.substringBefore(" · ").trimStart { !it.isLetterOrDigit() }.trim()

// ============================================================================ the settings home
@Composable
fun SettingsScreen(app: HkApp, nav: Nav) {
    val s by app.settings.flow.collectAsStateWithLifecycle()
    val S = strings()
    val A = accountText()
    val t = tones
    var confirmReset by remember { mutableStateOf(false) }
    val tourCtl = LocalTour.current
    var toursReset by remember { mutableStateOf(false) }
    val vv by app.vag.version.collectAsStateWithLifecycle()
    val vagHave = remember(vv) { app.vag.downloadedCount() }
    val src by app.source.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackBar(S.settingsTitle, nav) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).readable().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountCard(app) { nav.go(Screen.Account) }

            SettingsGroup(A.groupPersonal) {
                val dark = when (s.darkMode) { DarkMode.SYSTEM -> S.themeSystem; DarkMode.LIGHT -> S.themeLight; DarkMode.DARK -> S.themeDark }
                SettingsRow("🎨", t.marigold, plain(S.sectionLook), plain(dark) + if (s.colorStyle == ColorStyle.WALLPAPER) " · " + plain(S.colorsWallpaper) else "") { nav.go(Screen.SettingsSub(SettingsPage.LOOK)) }
                RowDivider()
                val extra = ALSO_SCRIPTS.filter { it.first in s.alsoScripts }.joinToString(" ") { "+" + it.second }
                SettingsRow("🌐", t.sky, plain(S.language), listOf(s.lang?.native ?: "", extra).filter { it.isNotBlank() }.joinToString("  ")) { nav.go(Screen.SettingsSub(SettingsPage.LANGUAGE)) }
                RowDivider()
                val on = listOf(s.reminderOn, s.notifyStreak, s.notifyAchievements, s.notifyDownloads, s.notifyUpdates).count { it }
                SettingsRow("🔔", t.lotus, plain(A.notifications), A.nOn(on)) { nav.go(Screen.SettingsSub(SettingsPage.NOTIFY)) }
                RowDivider()
                SettingsRow("🎯", t.leaf, plain(S.sectionGoal), S.minutesN(s.dailyGoalMinutes) + if (s.reminderOn) "  ·  ⏰ %02d:%02d".format(s.reminderHour, s.reminderMinute) else "") { nav.go(Screen.SettingsSub(SettingsPage.GOAL)) }
            }

            SettingsGroup(A.groupLearning) {
                SettingsRow("🎶", t.peacock, plain(S.sectionVag), "⬇ $vagHave/33  ·  ⏩ " + "%.2f×".format(s.speed)) { nav.go(Screen.SettingsSub(SettingsPage.CHANT)) }
                RowDivider()
                SettingsRow("📖", t.berry, plain(S.sectionReading), "Aa " + "%.1f×".format(s.fontScale)) { nav.go(Screen.SettingsSub(SettingsPage.READING)) }
                RowDivider()
                SettingsRow("🎤", t.marigold, plain(S.sectionCheck), plain(S.strictName(s.strictness))) { nav.go(Screen.SettingsSub(SettingsPage.CHECK)) }
            }

            SettingsGroup(A.groupMore) {
                SettingsRow("🧭", t.sky, plain(A.guides), if (toursReset) tourText().replayDone else plain(tourText().replayAll)) {
                    tourCtl?.resetAll(); toursReset = true
                }
                RowDivider()
                SettingsRow("☁️", t.leaf, plain(S.sectionServer), if (src.revision > 0) "rev ${src.revision}" else "") { nav.go(Screen.SettingsSub(SettingsPage.SOURCE)) }
                RowDivider()
                SettingsRow("📜", t.lotus, plain(S.about), plain(S.creditsTitle)) { nav.go(Screen.Credits) }   // straight to Credits (user: the About page was an extra step)
                RowDivider()
                SettingsRow("⚠️", Tone(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, MaterialTheme.colorScheme.error),
                    plain(S.resetProgress), null, danger = true) { confirmReset = true }
            }
            Offering(Modifier.padding(top = 4.dp), size = 16)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(S.resetQ) },
            text = { Text(S.resetBody) },
            confirmButton = { TextButton(onClick = { app.progress.reset(); confirmReset = false }) { Text(S.reset, color = HkColors.bad) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(S.cancel) } },
        )
    }
}

/** The account at the top of Settings: who is signed in and whether the cloud copy is up to date. */
@Composable
private fun AccountCard(app: HkApp, onOpen: () -> Unit) {
    val A = accountText()
    val user by app.account.user.collectAsStateWithLifecycle()
    val sync by app.sync.status.collectAsStateWithLifecycle()
    val t = tones
    val u = user
    val signedIn = u != null && !u.anonymous
    BouncyCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = if (signedIn) t.peacock.container else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (signedIn) t.peacock.on else MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(u?.label, 56)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (signedIn) {
                    Text(u!!.label ?: A.signedIn, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(syncLine(A, sync), fontSize = 13.sp)
                } else {
                    Text(A.heroTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(if (u?.anonymous == true) A.guest + " · " + A.localOnly.drop(3) else A.localOnly, fontSize = 13.sp)
                }
            }
            Text("›", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun syncLine(A: AccountText, s: SyncStatus): String = when (s) {
    SyncStatus.Off -> A.localOnly
    SyncStatus.Syncing -> A.syncing
    is SyncStatus.Synced -> if (System.currentTimeMillis() - s.at < 60_000) A.synced
        else A.synced + " · " + DateUtils.getRelativeTimeSpanString(s.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    is SyncStatus.Failed -> A.syncError
}

/** A round avatar with the first letter of the name (no photo download needed). */
@Composable
fun Avatar(label: String?, size: Int) {
    val t = tones
    val tone = t[(label?.hashCode() ?: 0) and 0xff]
    Box(Modifier.size(size.dp).clip(CircleShape).background(tone.accent), contentAlignment = Alignment.Center) {
        Text(label?.trim()?.firstOrNull()?.uppercase() ?: "🙏", color = Color.White, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, Modifier.padding(start = 8.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

/** One settings entry: a coloured icon, a title, the current choice underneath, and a chevron. */
@Composable
private fun SettingsRow(emoji: String, tone: Tone, title: String, summary: String?, danger: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tone.container), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (!summary.isNullOrBlank()) Text(summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!danger) Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================================ the pages
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPageScreen(app: HkApp, nav: Nav, page: SettingsPage) {
    val s by app.settings.flow.collectAsStateWithLifecycle()
    val S = strings()
    val A = accountText()
    val ctx = LocalContext.current
    fun upd(f: (Settings) -> Settings) = app.settings.update(f)
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val title = when (page) {
        SettingsPage.LOOK -> S.sectionLook; SettingsPage.LANGUAGE -> "🌐 " + S.language; SettingsPage.NOTIFY -> A.notifications
        SettingsPage.GOAL -> "🎯 " + S.sectionGoal; SettingsPage.CHANT -> S.sectionVag; SettingsPage.READING -> "📖 " + S.sectionReading
        SettingsPage.CHECK -> "🎤 " + S.sectionCheck; SettingsPage.SOURCE -> S.sectionServer; SettingsPage.ABOUT -> "📜 " + S.about
    }
    Scaffold(topBar = { BackBar(title.substringBefore(" · "), nav) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).readable().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (page) {
                SettingsPage.LOOK -> {
                    Label("☀️ / 🌙")
                    Choice(S.themeSystem, s.darkMode == DarkMode.SYSTEM) { upd { it.copy(darkMode = DarkMode.SYSTEM) } }
                    Choice(S.themeLight, s.darkMode == DarkMode.LIGHT) { upd { it.copy(darkMode = DarkMode.LIGHT) } }
                    Choice(S.themeDark, s.darkMode == DarkMode.DARK) { upd { it.copy(darkMode = DarkMode.DARK) } }
                    if (canUseWallpaperColors) {
                        Label("🎨")
                        Choice(S.colorsFestival, s.colorStyle == ColorStyle.FESTIVAL) { upd { it.copy(colorStyle = ColorStyle.FESTIVAL) } }
                        Choice(S.colorsWallpaper, s.colorStyle == ColorStyle.WALLPAPER) { upd { it.copy(colorStyle = ColorStyle.WALLPAPER) } }
                    }
                }
                SettingsPage.LANGUAGE -> {
                    Lang.entries.forEach { l -> Choice(l.native + "  ·  " + l.english, s.lang == l) { upd { it.copy(lang = l) } } }
                    Hint(S.languageHint)
                    Label(S.alsoShowIn)
                    AlsoScriptsPicker(s.alsoScripts) { v -> upd { it.copy(alsoScripts = v) } }
                    if (S.lang == Lang.TA || Script.TAMIL in s.alsoScripts) {
                        SwitchRow(S.tamilSup, Scripts.render("ಭಗವದ್ಭಕ್ತ", Script.TAMIL), s.tamilSuperscripts) { v -> upd { it.copy(tamilSuperscripts = v) } }
                    }
                }
                SettingsPage.NOTIFY -> {
                    if (!Notifier.allowed(ctx)) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(A.nBlocked, Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                Button(onClick = {
                                    if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
                                }) { Text(A.nTurnOn) }
                            }
                        }
                    }
                    SwitchRow("⏰ " + plain(S.dailyReminder), S.reminderAt("%02d:%02d".format(s.reminderHour, s.reminderMinute)), s.reminderOn) { v ->
                        if (v && Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        upd { it.copy(reminderOn = v) }; Reminders.schedule(ctx)
                    }
                    SwitchRow(A.nStreak, A.nStreakSub, s.notifyStreak) { v -> upd { it.copy(notifyStreak = v) } }
                    SwitchRow(A.nAchieve, A.nAchieveSub, s.notifyAchievements) { v -> upd { it.copy(notifyAchievements = v) } }
                    SwitchRow(A.nDownloads, A.nDownloadsSub, s.notifyDownloads) { v -> upd { it.copy(notifyDownloads = v) } }
                    SwitchRow(A.nUpdates, A.nUpdatesSub, s.notifyUpdates) { v -> upd { it.copy(notifyUpdates = v) } }
                }
                SettingsPage.GOAL -> {
                    Label(S.dailyGoal)
                    BigValue(S.minutesN(s.dailyGoalMinutes))
                    Slider(value = s.dailyGoalMinutes.toFloat(), onValueChange = { v -> upd { it.copy(dailyGoalMinutes = (v / 5).toInt() * 5) } }, valueRange = 5f..60f, steps = 10)
                    HorizontalDivider()
                    SwitchRow("⏰ " + plain(S.dailyReminder), S.reminderAt("%02d:%02d".format(s.reminderHour, s.reminderMinute)), s.reminderOn) { v ->
                        if (v && Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        upd { it.copy(reminderOn = v) }; Reminders.schedule(ctx)
                    }
                    if (s.reminderOn) OutlinedButton(onClick = {
                        TimePickerDialog(ctx, { _, h, m -> upd { it.copy(reminderHour = h, reminderMinute = m) }; Reminders.schedule(ctx) }, s.reminderHour, s.reminderMinute, false).show()
                    }) { Text("⏰ " + S.changeTime) }
                }
                SettingsPage.CHANT -> {
                    val vv by app.vag.version.collectAsStateWithLifecycle()
                    val vagHave = remember(vv) { app.vag.downloadedCount() }
                    Hint(S.vagInfo(vagHave, 33))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { app.vag.downloadAll() }, enabled = vagHave < 33) { Text("⬇ " + S.downloadAll(100)) }
                        OutlinedButton(onClick = { app.vag.deleteAll() }, enabled = vagHave > 0) { Text(S.delete) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Label(S.speed)
                    BigValue("%.2f×".format(s.speed))
                    Slider(value = s.speed, onValueChange = { v -> upd { it.copy(speed = v) } }, valueRange = 0.5f..1.5f)
                }
                SettingsPage.READING -> {
                    if (S.lang.script != Script.KANNADA) {
                        Label(S.showVerses)
                        val me = Scripts.render("ಹರಿಕಥಾ", S.lang.script)
                        listOf(ScriptMode.BOTH to "ಹರಿಕಥಾ + $me", ScriptMode.MINE to me, ScriptMode.KANNADA to "ಹರಿಕಥಾ").forEach { (m, label) ->
                            Choice(label, s.scriptMode == m) { upd { it.copy(scriptMode = m) } }
                        }
                        SwitchRow(S.kannadaPractice, S.kannadaPracticeSub, s.peekTamil) { v -> upd { it.copy(peekTamil = v) } }
                        HorizontalDivider()
                    }
                    Label(S.textSize)
                    Text("ಹರಿಕಥಾಮೃತಸಾರ", fontSize = (22 * s.fontScale).sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Slider(value = s.fontScale, onValueChange = { v -> upd { it.copy(fontScale = v) } }, valueRange = 0.8f..1.8f)
                    HorizontalDivider()
                    SwitchRow(S.keepScreenOn, null, s.keepScreenOn) { v -> upd { it.copy(keepScreenOn = v) } }
                }
                SettingsPage.CHECK -> {
                    EarBanner(app, showReady = true)
                    Label(S.strictness)
                    com.example.harikathamruthasara.speech.Strictness.entries.forEach { lv ->
                        Choice(S.strictName(lv), s.strictness == lv, if (s.strictness == lv) S.strictSub(lv) else null) { upd { it.copy(strictness = lv) } }
                    }
                    HorizontalDivider()
                    SwitchRow(S.speakCorrections, S.speakCorrectionsSub, s.speakCorrections) { v -> upd { it.copy(speakCorrections = v) } }
                }
                SettingsPage.SOURCE -> ServerSettings(app)
                SettingsPage.ABOUT -> {
                    Text(S.aboutBody, fontSize = 15.sp, lineHeight = 22.sp)
                    Button(onClick = { nav.go(Screen.Credits) }) { Text(S.creditsTitle) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun Label(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
@Composable private fun Hint(t: String) = Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
@Composable private fun BigValue(t: String) = Text(t, Modifier.fillMaxWidth(), fontSize = 34.sp, fontWeight = FontWeight.Black, color = tones.marigold.accent, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

/** A big radio choice (with an optional explanation under the selected one). */
@Composable
private fun Choice(label: String, selected: Boolean, sub: String? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tone = tones.peacock
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
        color = if (selected) tone.container else cs.surfaceContainerLow, contentColor = if (selected) tone.on else cs.onSurface,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) tone.accent else cs.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                if (sub != null) Text(sub, fontSize = 13.sp, color = (if (selected) tone.on else cs.onSurfaceVariant).copy(alpha = 0.85f))
            }
            androidx.compose.material3.RadioButton(selected = selected, onClick = onClick, colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = tone.accent))
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(onClick = { onChange(!checked) }, color = Color.Transparent, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
