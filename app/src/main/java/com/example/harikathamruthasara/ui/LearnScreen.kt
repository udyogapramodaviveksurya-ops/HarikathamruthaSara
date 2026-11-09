package com.example.harikathamruthasara.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.audio.DownloadState
import com.example.harikathamruthasara.audio.Phase
import com.example.harikathamruthasara.data.GapMode
import com.example.harikathamruthasara.data.Script
import com.example.harikathamruthasara.data.ScriptMode
import com.example.harikathamruthasara.data.Scripts
import com.example.harikathamruthasara.data.StudyUnit
import com.example.harikathamruthasara.i18n.Strs
import com.example.harikathamruthasara.i18n.strings

fun Strs.unitName(u: StudyUnit) = when (u) {
    StudyUnit.LINE -> unitLine; StudyUnit.SENTENCE -> unitHalf
    StudyUnit.VERSE -> unitVerse; StudyUnit.BUILDUP -> unitBuild
}

fun Strs.unitSub(u: StudyUnit) = when (u) {
    StudyUnit.LINE -> unitLineSub; StudyUnit.SENTENCE -> unitHalfSub
    StudyUnit.VERSE -> unitVerseSub; StudyUnit.BUILDUP -> unitBuildSub
}

private fun Strs.gapName(g: GapMode, sec: Int) = when (g) { GapMode.NONE -> noPause; GapMode.FIXED -> pauseSec(sec); GapMode.ECHO -> myTurn }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(app: HkApp, nav: Nav) {
    val st by app.player.state.collectAsStateWithLifecycle()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val progress by app.progress.state.collectAsStateWithLifecycle()
    app.vag.version.collectAsStateWithLifecycle().value
    val sandhi = st.sandhi
    val verse = st.verse
    if (sandhi == null || verse == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val player = app.player
    val ctx = LocalContext.current
    val S = strings()
    var chores by rememberSaveable { mutableStateOf(false) }
    var peekWord by remember(verse.key) { mutableIntStateOf(-1) }
    var reporting by remember { mutableStateOf(false) }

    val view = LocalView.current
    // Leaving this page stops the chant (locking or leaving the app: MainActivity.onStop). Not a screen rotation.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(Unit) {
        onDispose { if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) player.stopAndClose() }
    }
    DisposableEffect(st.playing, settings.keepScreenOn) {
        view.keepScreenOn = st.playing && settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { player.play() }
    val onPlay: () -> Unit = {
        if (!st.playing && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else player.toggle()
    }

    val step = st.step
    val focusLine = when {
        st.currentWord >= 0 -> verse.words.getOrNull(st.currentWord)?.line ?: -1
        step != null -> verse.words.getOrNull(step.from)?.line ?: -1
        else -> -1
    }
    val look: (Int) -> WordLook = { g ->
        when {
            g == st.currentWord -> WordLook.CURRENT
            step != null && g in step -> WordLook.IN_STEP
            else -> WordLook.NORMAL
        }
    }

    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    val tour = learnTour()
    AutoTour(tour)

    if (reporting) ReportDialog(app, sandhi.id, verse.number, focusLine.takeIf { it >= 0 }?.plus(1)) { reporting = false }
    picker?.let { pk ->
        PickerSheet(pk, app, S, chores, { chores = it }, onClose = { picker = null },
            onReport = { picker = null; player.pause(); reporting = true })
    }
    Scaffold(
        topBar = {
            BackBar("${sandhi.id}. ${mine(sandhi.title, settings.tamilSuperscripts)}", nav, if (S.lang.script != Script.KANNADA) sandhi.title else null) {
                TourHelpButton(tour)
                IconButton(onClick = { picker = Picker.MORE }, Modifier.tourTarget(TT.LEARN_MORE)) { Icon(Icons.Rounded.Tune, S.options) }
            }
        },
        bottomBar = {
            PlayerDock(
                app, S, onPlay = onPlay,
                onRecite = { player.pause(); nav.go(Screen.Recite(sandhi.id, st.verseIdx)) },
                onPick = { picker = it },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // padya navigator: big and obvious
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).tourTarget(TT.LEARN_NAV),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = { player.prevVerse() }, enabled = st.verseIdx > 0, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, S.back, Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(S.verseLabel(verse), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(S.verseOf(st.verseIdx + 1, sandhi.verses.size), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Stars(progress.stat(verse.key).stars, size = 14)
                    }
                }
                FilledTonalIconButton(onClick = { player.nextVerse() }, enabled = st.verseIdx < sandhi.verses.lastIndex, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, S.next, Modifier.size(32.dp))
                }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).readable(820.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VagBanner(app, sandhi.id)
                if (st.finishedSandhi) {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${S.sandhiDone}: ${mine(sandhi.title, settings.tamilSuperscripts)}", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Offering(Modifier.padding(top = 6.dp))
                        }
                    }
                }
                if (chores) {
                    ChoresView(app)
                } else {
                    Card(Modifier.tourTarget(TT.LEARN_VERSE), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = androidx.compose.foundation.BorderStroke(2.dp, tones[sandhi.id - 1].container)) {
                        VerseView(
                            verse, settings.scriptMode, settings.tamilSuperscripts, settings.fontScale, look,
                            Modifier.padding(18.dp),
                            hideTamil = settings.peekTamil,
                            focusLine = focusLine,
                            onWordTap = { g -> peekWord = g; player.tapWord(g) },
                        )
                    }
                    AnimatedVisibility(peekWord in verse.words.indices) {
                        val w = verse.words.getOrNull(peekWord)
                        if (w != null) {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(w.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        if (S.lang.script != Script.KANNADA) Text(mine(w.text, settings.tamilSuperscripts), fontSize = 18.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    IconButton(onClick = { player.sayWord(sandhi.id, verse, w.global) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, S.hear) }
                                    IconButton(onClick = { peekWord = -1 }) { Icon(Icons.Filled.Close, S.cancel) }
                                }
                            }
                        }
                    }
                    if (app.vag.isDownloaded(sandhi.id)) {
                        Text(if (player.usesVag(sandhi.id, verse)) S.vagCredit else S.vagNotReady, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(S.dashTip, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.tapTip, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SwitchLine(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Label above a group of options, so long words never get squeezed. */
@Composable
private fun OptLabel(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun Opt(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp)) })
}

/** The focused settings sheets opened from the dock's chips. */
private enum class Picker { UNIT, REPEAT, AFTER, SPEED, MORE }

/** "அரை பத்யம் · Half padya" → "அரை பத்யம்": chips show only the reader's own language, to stay short. */
internal fun short(x: String) = x.substringBefore(" · ")

/**
 * The player dock: always visible, never needs dragging. Status, big transport controls, and one row of chips that
 * show the current choices; a tap on a chip opens just that setting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerDock(app: HkApp, S: Strs, onPlay: () -> Unit, onRecite: () -> Unit, onPick: (Picker) -> Unit) {
    val st by app.player.state.collectAsStateWithLifecycle()
    val s by app.settings.flow.collectAsStateWithLifecycle()
    val player = app.player
    val cs = MaterialTheme.colorScheme
    val t = tones
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), shadowElevation = 12.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 12.dp, bottom = 8.dp)) {
            // ---- status: what is playing, which repeat, whose turn
            val total = s.repeatCount
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(st.step?.let { short(S.stepLabel(it)) }, "🔁 ${st.repeatNo.coerceAtMost(if (total == 0) Int.MAX_VALUE else total)}/${if (total == 0) "∞" else total}").joinToString("  ·  "),
                    Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                when {
                    st.phase == Phase.YOUR_TURN -> Pill(S.yourTurn, t.leaf.container, t.leaf.on)
                    st.playing -> Pill(S.listenNow, t.lotus.container, t.lotus.on)
                }
                if (st.sleepAtMs > 0) { Spacer(Modifier.width(6.dp)); Pill("😴 ${((st.sleepAtMs - System.currentTimeMillis()) / 60000 + 1).coerceAtLeast(1)}", t.berry.container, t.berry.on) }
            }
            // ---- transport
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { player.prevStep() }, Modifier.size(56.dp)) { Icon(Icons.Filled.FastRewind, S.back, Modifier.size(30.dp)) }
                FilledIconButton(onClick = onPlay, Modifier.tourTarget(TT.LEARN_PLAY).size(80.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.primary)) {
                    Icon(if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (st.playing) S.pause else S.play, Modifier.size(48.dp))
                }
                FilledTonalIconButton(onClick = { player.nextStep() }, Modifier.size(56.dp)) { Icon(Icons.Filled.FastForward, S.next, Modifier.size(30.dp)) }
                // reciting is a different activity: its own clearly labelled button
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.tourTarget(TT.LEARN_RECITE).width(84.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onRecite).padding(vertical = 4.dp)) {
                    Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(t.marigold.container), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, null, tint = t.marigold.on, modifier = Modifier.size(26.dp))
                    }
                    Text(short(S.reciteCheck), fontSize = 12.sp, lineHeight = 15.sp, color = cs.onSurface, maxLines = 2, textAlign = TextAlign.Center)
                }
            }
            // ---- the current choices, one tap away
            FlowRow(
                Modifier.fillMaxWidth().tourTarget(TT.LEARN_CHIPS).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingChip("📖", short(S.unitName(s.unit)), t.lotus) { onPick(Picker.UNIT) }
                SettingChip("🔁", if (s.repeatCount == 0) "∞" else "${s.repeatCount}×", t.marigold, Modifier.tourTarget(TT.LEARN_REPEAT)) { onPick(Picker.REPEAT) }
                SettingChip("🗣️", short(S.gapName(s.gapMode, s.gapSeconds)), t.leaf) { onPick(Picker.AFTER) }
                SettingChip("⏩", "%.2f×".format(s.speed).replace(".00×", "×"), t.sky) { onPick(Picker.SPEED) }
            }
        }
    }
}

/** A chip that shows a current value and opens its picker. 48 dp tall: an easy target with wet or busy hands. */
@Composable
private fun SettingChip(emoji: String, value: String, tone: Tone, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = tone.container, contentColor = tone.on, modifier = modifier.heightIn(min = 48.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(" ▾", fontSize = 12.sp, color = tone.on.copy(alpha = 0.7f))
        }
    }
}

/** A big, clearly selectable option with an explanation underneath (instead of a bare chip). */
@Composable
private fun ChoiceCard(title: String, sub: String?, selected: Boolean, tone: Tone, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.medium,
        color = if (selected) tone.container else cs.surfaceContainerLow, contentColor = if (selected) tone.on else cs.onSurface,
        border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) tone.accent else cs.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (sub != null) Text(sub, fontSize = 13.sp, lineHeight = 17.sp, color = (if (selected) tone.on else cs.onSurfaceVariant).copy(alpha = 0.85f))
            }
            RadioButton(selected = selected, onClick = onClick, colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = tone.accent))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PickerSheet(
    pk: Picker, app: HkApp, S: Strs, chores: Boolean, setChores: (Boolean) -> Unit,
    onClose: () -> Unit, onReport: () -> Unit,
) {
    val s by app.settings.flow.collectAsStateWithLifecycle()
    val player = app.player
    val cs = MaterialTheme.colorScheme
    val t = tones
    fun upd(f: (com.example.harikathamruthasara.data.Settings) -> com.example.harikathamruthasara.data.Settings) = app.settings.update(f)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).readable(640.dp).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val (emoji, title) = when (pk) {
                Picker.UNIT -> "📖" to S.sheetUnit
                Picker.REPEAT -> "🔁" to S.sheetRepeat
                Picker.AFTER -> "🗣️" to S.sheetAfter
                Picker.SPEED -> "⏩" to S.sheetSpeed
                Picker.MORE -> "⚙️" to S.options
            }
            Text("$emoji  $title", style = MaterialTheme.typography.titleLarge)
            when (pk) {
                Picker.UNIT -> {
                    Text("✅ " + S.unitRecommended, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HkColors.good)
                    listOf(StudyUnit.SENTENCE, StudyUnit.VERSE).forEach { u ->
                        ChoiceCard(S.unitName(u), S.unitSub(u), s.unit == u, t.lotus) { upd { it.copy(unit = u) } }
                    }
                    Text(S.unitTraining, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    listOf(StudyUnit.LINE, StudyUnit.BUILDUP).forEach { u ->
                        ChoiceCard(S.unitName(u), S.unitSub(u), s.unit == u, t.sky) { upd { it.copy(unit = u) } }
                    }
                }
                Picker.REPEAT -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { upd { it.copy(repeatCount = if (it.repeatCount <= 1) 1 else it.repeatCount - 1) } }, Modifier.size(56.dp)) { Icon(Icons.Filled.Remove, "−") }
                        Text(if (s.repeatCount == 0) "∞" else "${s.repeatCount}×", Modifier.width(110.dp), textAlign = TextAlign.Center, fontSize = 40.sp, fontWeight = FontWeight.Black, color = t.marigold.accent)
                        FilledTonalIconButton(onClick = { upd { it.copy(repeatCount = if (it.repeatCount == 0) 1 else (it.repeatCount + 1).coerceAtMost(108)) } }, Modifier.size(56.dp)) { Icon(Icons.Filled.Add, "+") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 5, 11, 21, 108).forEach { n -> Opt("$n×", s.repeatCount == n) { upd { it.copy(repeatCount = n) } } }
                        Opt(S.untilIStop, s.repeatCount == 0) { upd { it.copy(repeatCount = 0) } }
                    }
                }
                Picker.AFTER -> {
                    ChoiceCard(S.myTurn, S.myTurnSub, s.gapMode == GapMode.ECHO, t.leaf) { upd { it.copy(gapMode = GapMode.ECHO) } }
                    ChoiceCard(S.pauseSec(s.gapSeconds), S.fixedPauseSub, s.gapMode == GapMode.FIXED, t.leaf) { upd { it.copy(gapMode = GapMode.FIXED) } }
                    if (s.gapMode == GapMode.FIXED) {
                        Slider(value = s.gapSeconds.toFloat(), onValueChange = { v -> upd { it.copy(gapSeconds = v.toInt()) } }, valueRange = 1f..15f, steps = 13)
                    }
                    ChoiceCard(S.noPause, S.noPauseSub, s.gapMode == GapMode.NONE, t.leaf) { upd { it.copy(gapMode = GapMode.NONE) } }
                }
                Picker.SPEED -> {
                    Text("%.2f×".format(s.speed), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 40.sp, fontWeight = FontWeight.Black, color = t.sky.accent)
                    Slider(value = s.speed, valueRange = 0.5f..1.5f, onValueChange = { x -> upd { it.copy(speed = x) } })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.75f, 0.9f, 1.0f, 1.1f, 1.25f).forEach { v -> Opt("%.2f×".format(v), kotlin.math.abs(s.speed - v) < 0.01f) { upd { it.copy(speed = v) } } }
                    }
                }
                Picker.MORE -> {
                    if (S.lang.script != Script.KANNADA) {
                        OptLabel("🔤 " + S.showVerses)
                        val me = Scripts.render("ಹರಿಕಥಾ", S.lang.script, s.tamilSuperscripts)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(ScriptMode.BOTH to "ಹರಿಕಥಾ + $me", ScriptMode.MINE to me, ScriptMode.KANNADA to "ಹರಿಕಥಾ").forEach { (m, label) ->
                                Opt(label, s.scriptMode == m) { upd { it.copy(scriptMode = m) } }
                            }
                        }
                    }
                    OptLabel("🔤 " + S.alsoShowIn)
                    AlsoScriptsPicker(s.alsoScripts) { v -> upd { it.copy(alsoScripts = v) } }
                    OptLabel(S.sleepTimer)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 10, 15, 30, 45, 60).forEach { m -> Opt(if (m == 0) S.off else S.minutesN(m), false) { player.setSleepTimer(m); onClose() } }
                    }
                    HorizontalDivider(Modifier.padding(top = 6.dp))
                    SwitchLine(S.bigText, S.bigTextSub, chores, setChores)
                    SwitchLine(S.autoNext, null, s.autoAdvance) { v -> upd { it.copy(autoAdvance = v) } }
                    HorizontalDivider()
                    TextButton(onClick = onReport) { Text(S.reportProblem, fontSize = 16.sp) }
                }
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp)) { Text("✓ " + S.ok, fontSize = 17.sp) }
        }
    }
}

@Composable
fun VagBanner(app: HkApp, sandhiId: Int) {
    val downloads by app.vag.downloads.collectAsStateWithLifecycle()
    app.vag.version.collectAsStateWithLifecycle().value
    val S = strings()
    if (app.vag.isDownloaded(sandhiId)) return
    val d = downloads[sandhiId]
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(S.vagBannerTitle, fontWeight = FontWeight.Bold)
            Text(S.vagBannerBody, fontSize = 13.sp)
            Spacer(Modifier.size(8.dp))
            when (d) {
                is DownloadState.Running -> {
                    LinearProgressIndicator(progress = { d.fraction }, Modifier.fillMaxWidth())
                    Text(S.downloading((d.fraction * 100).toInt()), fontSize = 12.sp)
                }
                is DownloadState.Failed -> {
                    Text(when (d.message) { com.example.harikathamruthasara.audio.VagLibrary.OUTDATED -> S.vagPackOutdated; com.example.harikathamruthasara.audio.VagLibrary.NOT_ON_SERVER -> S.vagNotOnServer; else -> S.vagNotReady + "\n" + d.message }, color = HkColors.bad, fontSize = 13.sp)
                    Button(onClick = { app.vag.download(sandhiId) }) { Text(S.tryAgain) }
                }
                null -> Button(onClick = { app.vag.download(sandhiId) }) { Text("⬇ " + S.download) }
            }
        }
    }
}

@Composable
private fun ChoresView(app: HkApp) {
    val st by app.player.state.collectAsStateWithLifecycle()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val S = strings()
    val verse = st.verse ?: return
    val step = st.step
    val lines = remember(step, verse) {
        if (step == null) verse.lines.indices.toSet() else verse.words.filter { it.global in step }.map { it.line }.toSet()
    }
    val yourTurn = st.phase == Phase.YOUR_TURN
    val bg by animateColorAsState(if (yourTurn) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer, label = "bg")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(24.dp)).padding(20.dp), contentAlignment = Alignment.Center) {
            Text(
                when {
                    !st.playing -> S.paused
                    yourTurn -> S.yourTurnSay
                    else -> S.listenDots
                },
                fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
        }
        VerseView(
            verse, settings.scriptMode, settings.tamilSuperscripts, settings.fontScale * 1.45f,
            look = { g -> if (g == st.currentWord) WordLook.CURRENT else if (step != null && g in step) WordLook.IN_STEP else WordLook.NORMAL },
            onlyLines = lines,
        )
    }
}
