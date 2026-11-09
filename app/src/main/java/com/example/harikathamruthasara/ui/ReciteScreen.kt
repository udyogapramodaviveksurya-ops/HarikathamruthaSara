package com.example.harikathamruthasara.ui

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.foundation.selection.toggleable
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.audio.Planner
import com.example.harikathamruthasara.audio.Step
import com.example.harikathamruthasara.data.Sandhi
import com.example.harikathamruthasara.data.StudyUnit
import com.example.harikathamruthasara.i18n.strings
import com.example.harikathamruthasara.i18n.Strs
import com.example.harikathamruthasara.speech.Listener
import com.example.harikathamruthasara.speech.Matcher
import com.example.harikathamruthasara.speech.Pronunciation
import com.example.harikathamruthasara.speech.EarState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RECITE_UNITS = listOf(StudyUnit.SENTENCE, StudyUnit.VERSE, StudyUnit.LINE)

private class ReciteSession(val app: HkApp, val sandhi: Sandhi, startVerse: Int, val scope: CoroutineScope, var S: Strs) {
    val listener = Listener(app.ear).also {
        it.strs = S
        if (com.example.harikathamruthasara.BuildConfig.DEBUG) it.debugDump = java.io.File(app.cacheDir, "recite_last.wav")
    }
    var verseIdx by mutableStateOf(startVerse)
    var unit by mutableStateOf(StudyUnit.SENTENCE)
    var partIdx by mutableStateOf(0)
    var hideText by mutableStateOf(false)
    var handsFree by mutableStateOf(false)
    var hintFirst by mutableStateOf(true)
    var result by mutableStateOf<Matcher.Result?>(null)
    var finalResult by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var handsFreeJob: Job? = null
    private var hearJob: Job? = null
    var speaking by mutableStateOf(false)

    val verse get() = sandhi.verses[verseIdx]
    val parts: List<Step> get() = Planner.plan(verse, unit)
    val part: Step get() = parts[partIdx.coerceIn(0, parts.lastIndex)]
    private val strictness get() = app.settings.value.strictness
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()

    fun live(text: String) {
        if (finalResult) return
        result = if (text.isBlank()) null else Matcher.match(part.words, text, strictness)
    }

    /** Which words were said (from the ear's letters) and how well (its verdict on each judged sound). */
    private suspend fun score(text: String, lp: Array<FloatArray>?): Matcher.Result = withContext(Dispatchers.Default) {
        val s = strictness
        val r = Matcher.match(part.words, text, s)
        val joins = (part.from until part.to).map { g -> verse.words[g].let { w -> verse.lines[w.line].wordEnds.getOrElse(w.index) { '1' } == '1' } }
        val sounds = if (lp == null) emptyMap() else Pronunciation.judge(lp, part.words, joins, r.reached, { r.isSaid(it, s) }, s)
        Matcher.withSounds(r, part.words, sounds, s)
    }

    /** The strictness changed: check the last attempt again (the score shown, not the one already saved). */
    fun rescore() {
        val text = listener.state.value.text
        if (text.isBlank() || result == null || !finalResult) return
        scope.launch {
            result = score(text, listener.lastLogProbs)
            message = S.feedback(result!!.overall, strictness.passMark)
        }
    }

    /** Whatever is being heard (a part or a word) stops at once. */
    fun stopHearing() { hearJob?.cancel(); hearJob = null; app.player.clip.pause() }
    fun reset() { stopHearing(); listener.cancel(); result = null; finalResult = false; message = null }

    fun setPart(i: Int) { reset(); partIdx = i.coerceIn(0, parts.lastIndex) }
    fun nextPart() {
        stopHearing()   // even at the first or last part, where there is nowhere to move
        if (partIdx < parts.lastIndex) setPart(partIdx + 1)
        else if (verseIdx < sandhi.verses.lastIndex) { reset(); verseIdx++; partIdx = 0 }
    }
    fun prevPart() {
        stopHearing()   // even at the first or last part, where there is nowhere to move
        if (partIdx > 0) setPart(partIdx - 1)
        else if (verseIdx > 0) { reset(); verseIdx--; partIdx = parts.lastIndex }
    }

    /** Longest an attempt at this step may take: ~3× its chanted length (~0.16 s per letter, Vāgdhenu's pace) + 4 s. */
    private val maxSeconds: Int get() = (part.words.sumOf { Matcher.normalize(it, true).length } * 0.16 * 3 + 4).toInt().coerceAtLeast(10)

    fun startManual() {
        app.player.pause()
        reset()
        message = S.listeningRecite
        listener.onSegmentDone = { scope.launch { finish(speakBack = true) } }
        listener.start(continuous = unit != StudyUnit.LINE, maxSeconds = maxSeconds)
    }

    fun stopManual() { listener.stop() }

    /** Scores the attempt and gives feedback. Returns the score. */
    suspend fun finish(speakBack: Boolean): Int {
        val text = listener.state.value.text
        val r = score(text, listener.lastLogProbs)
        result = r; finalResult = true
        if (text.isBlank()) { message = listener.state.value.error ?: S.didntHear; return 0 }
        val full = unit == StudyUnit.VERSE
        app.progress.recordRecite(verse.key, r.overall, full)
        if (full) app.progress.checkSandhiBadge(sandhi)
        message = S.feedback(r.overall, strictness.passMark)
        if (speakBack && app.settings.value.speakCorrections) scope.launch { speakFeedback(r) }
        return r.overall
    }


    suspend fun speakFeedback(r: Matcher.Result) {
        speaking = true
        try {
            // a cheerful or a "try again" chime (the on-screen message says it in words)
            tone?.startTone(if (r.overall >= strictness.passMark) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 300)
            delay(450)
            val missed = part.words.indices.filter { !r.isCorrect(it, strictness) }
            if (missed.isNotEmpty() && r.overall < 95) {
                delay(250)
                for (i in missed.take(5)) {
                    val g = part.from + i
                    app.player.sayAnyVoice(sandhi.id, verse, com.example.harikathamruthasara.audio.Step(listOf(verse.words[g].text), g, com.example.harikathamruthasara.audio.StepKind.WORD, g + 1))
                    delay(250)
                }
            }
        } finally { speaking = false }
    }

    fun hear() {
        app.player.pause(); listener.cancel(); stopHearing()
        hearJob = scope.launch { speaking = true; try { app.player.sayAnyVoice(sandhi.id, verse, part) } finally { speaking = false } }
    }

    fun hearWord(global: Int) { listener.cancel(); app.player.sayWord(sandhi.id, verse, global) }

    // ---------- hands-free echo loop ----------

    fun startHandsFree() {
        app.player.pause()
        handsFree = true
        handsFreeJob?.cancel()
        handsFreeJob = scope.launch {
            val s = app.settings.value
            while (true) {
                var tries = 0
                while (true) {
                    reset()
                    if (hintFirst || tries > 0) {
                        message = S.listenDots
                        speaking = true
                        app.player.sayAnyVoice(sandhi.id, verse, part)
                        speaking = false
                        delay(300)
                    }
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(250)
                    message = S.reciteNow
                    val done = CompletableDeferred<Unit>()
                    listener.onSegmentDone = { done.complete(Unit) }
                    listener.start(continuous = false, maxSeconds = maxSeconds)
                    done.await()
                    val score = finish(speakBack = false)
                    if (score == 0 && listener.state.value.text.isBlank()) { tries++; if (tries >= 2) break else continue }
                    val r = result!!
                    speakFeedback(r)
                    if (score >= strictness.passMark || ++tries >= 3) break
                    delay(400)
                }
                delay(600)
                if (partIdx == parts.lastIndex && verseIdx == sandhi.verses.lastIndex) break
                nextPart()
            }
            handsFree = false
            message = S.sandhiComplete
        }
    }

    fun stopHandsFree() {
        handsFreeJob?.cancel(); handsFreeJob = null
        handsFree = false; speaking = false
        app.player.clip.pause(); listener.cancel()
        message = null
    }

    fun dispose() { stopHandsFree(); listener.destroy(); tone?.release() }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ReciteScreen(app: HkApp, sandhi: Sandhi, verseIdx: Int, nav: Nav) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val S = strings()
    val session = remember(sandhi.id) { ReciteSession(app, sandhi, verseIdx.coerceIn(0, sandhi.verses.lastIndex), scope, S) }
    session.S = S; session.listener.strs = S
    DisposableEffect(session) { onDispose { session.dispose() } }
    val ls by session.listener.state.collectAsStateWithLifecycle()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it }
    LaunchedEffect(Unit) { app.player.pause(); if (!hasMic) micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    LaunchedEffect(ls.text) { session.live(ls.text) }
    val view = LocalView.current
    DisposableEffect(session.handsFree) { view.keepScreenOn = session.handsFree; onDispose { view.keepScreenOn = false } }

    val verse = session.verse
    val part = session.part
    val r = session.result
    val strictness = settings.strictness
    LaunchedEffect(strictness) { session.rescore() }
    val look: (Int) -> WordLook = { g ->
        if (g !in part) WordLook.NORMAL.let { if (session.hideText) WordLook.HIDDEN else it }
        else {
            val i = g - part.from
            when {
                r == null -> if (session.hideText) WordLook.HIDDEN else WordLook.IN_STEP
                session.finalResult && r.isSaid(i, strictness) && i in r.sounds -> WordLook.SOUND
                r.isCorrect(i, strictness) && (session.finalResult || i <= r.reached) -> WordLook.GOOD
                session.finalResult || i < r.reached -> if (session.hideText && !session.finalResult) WordLook.HIDDEN else WordLook.BAD
                i == r.reached + 1 && !session.hideText -> WordLook.NEXT
                else -> if (session.hideText) WordLook.HIDDEN else WordLook.IN_STEP
            }
        }
    }

    val tour = reciteTour()
    AutoTour(tour)
    var showOptions by remember { mutableStateOf(false) }
    if (showOptions) ReciteOptions(app, session, onClose = { showOptions = false })
    Scaffold(topBar = { BackBar(S.reciteTitle, nav, "${mine(sandhi.title, settings.tamilSuperscripts)} · ${S.verseLabel(verse)}") {
        TourHelpButton(tour)
        IconButton(onClick = { showOptions = true }, enabled = !session.handsFree) { Icon(Icons.Rounded.Tune, S.options) }
    } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).readable(820.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // the listening ear (on-device model): offer the download until it is there
                EarBanner(app)
                if (!hasMic) Banner(S.micNeeded, S.allow) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

                // "hear it" uses the Vāgdhenu chant: offer the download until this sandhi has it
                val vagReady = app.vag.version.collectAsStateWithLifecycle().value.let { app.player.usesVag(sandhi.id, verse) }
                if (!vagReady) VagBanner(app, sandhi.id)
                Card(Modifier.tourTarget(TT.RECITE_VERSE), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(short(S.stepLabel(part)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Stars(app.progress.state.collectAsStateWithLifecycle().value.stat(verse.key).stars)
                        }
                        Spacer(Modifier.height(12.dp))
                        VerseView(
                            verse, settings.scriptMode, settings.tamilSuperscripts, settings.fontScale, look,
                            onWordTap = { g -> if (g in part) session.hearWord(g) },
                        )
                    }
                }

                // result
                if (r != null && session.finalResult) {
                    val sc = r.overall
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = if (sc >= strictness.passMark) HkColors.goodBg else MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$sc%", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = if (sc >= strictness.passMark) HkColors.good else MaterialTheme.colorScheme.secondary)
                            Stars(when { sc >= maxOf(90, strictness.passMark) -> 3; sc >= strictness.passMark -> 2; sc >= 40 -> 1; else -> 0 }, size = 26)
                            // sounds to fix: the word as written, and how it sounded
                            val fix = part.words.indices.filter { r.isSaid(it, strictness) && it in r.sounds }
                            if (fix.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(S.soundsToFix, fontSize = 13.sp)
                                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    fix.take(6).forEach { i ->
                                        val w = part.words[i]
                                        val said = r.sounds.getValue(i).minBy { it.margin }.said
                                        OutlinedButton(onClick = { session.hearWord(part.from + i) }) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(w, fontSize = 16.sp, color = HkColors.sound, fontWeight = FontWeight.Bold)
                                                if (S.lang.script != com.example.harikathamruthasara.data.Script.KANNADA) Text(mine(w, settings.tamilSuperscripts), fontSize = 12.sp)
                                                Text(S.notLike(said), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                            val missed = part.words.indices.filter { !r.isSaid(it, strictness) }
                            if (missed.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(S.practiseWords, fontSize = 13.sp)
                                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    missed.take(4).forEach { i ->
                                        val w = part.words[i]
                                        OutlinedButton(onClick = { session.hearWord(part.from + i) }) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(w, fontSize = 15.sp); Text(mine(w, settings.tamilSuperscripts), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                (if (ls.analyzing) S.checking else session.message)?.let { Text(it, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                if (ls.text.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(12.dp).fillMaxWidth()) {
                            Text(S.whatIHeard, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(ls.text, fontSize = 16.sp)
                            Text(mine(ls.text, settings.tamilSuperscripts), fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                ls.error?.let { if (session.message != it) Text(it, color = HkColors.bad, fontSize = 14.sp) }
            }

            // controls
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (session.handsFree) {
                        Text(S.handsFreeOn, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        MicLevel(ls.level, ls.listening)
                        Button(onClick = { session.stopHandsFree() }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(Icons.Filled.Stop, null); Text(S.stopHandsFree, fontSize = 17.sp) }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            LabeledControl(Icons.AutoMirrored.Filled.NavigateBefore, short(S.back), Modifier.weight(1f)) { session.prevPart() }
                            LabeledControl(Icons.AutoMirrored.Filled.VolumeUp, short(S.hear), Modifier.weight(1f).tourTarget(TT.RECITE_HEAR)) { session.hear() }
                            val scale by animateFloatAsState(if (ls.listening) 1f + ls.level * 0.25f else 1f, label = "mic")
                            Box(Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                                FilledIconButton(
                                    onClick = { if (ls.listening) session.stopManual() else if (hasMic) session.startManual() else micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                    modifier = Modifier.tourTarget(TT.RECITE_MIC).size(76.dp).scale(scale),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (ls.listening) HkColors.bad else MaterialTheme.colorScheme.primary),
                                ) { Icon(if (ls.listening) Icons.Filled.Stop else Icons.Filled.Mic, if (ls.listening) S.stop else S.reciteCheck, Modifier.size(40.dp)) }
                            }
                            LabeledControl(Icons.AutoMirrored.Filled.NavigateNext, short(S.next), Modifier.weight(1f)) { session.nextPart() }
                        }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { if (hasMic) session.startHandsFree() else micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).tourTarget(TT.RECITE_HANDS),
                        ) { Text(short(S.handsFree), fontSize = 17.sp, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
}

/** Everything that isn't the verse: how much to recite and how it is checked. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReciteOptions(app: HkApp, session: ReciteSession, onClose: () -> Unit) {
    val S = strings()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(S.reciteHowMuch, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RECITE_UNITS.forEach { u ->
                    FilterChip(selected = session.unit == u, onClick = { session.reset(); session.unit = u; session.partIdx = 0 }, label = {
                        Text(short(when (u) { StudyUnit.LINE -> S.unitLine; StudyUnit.SENTENCE -> S.unitHalf; else -> S.unitVerse }))
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(short(S.strictness), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.harikathamruthasara.speech.Strictness.entries.forEach { lv ->
                    FilterChip(selected = settings.strictness == lv, onClick = { app.settings.update { it.copy(strictness = lv) } }, label = { Text(short(S.strictName(lv))) })
                }
            }
            Text(S.strictSub(settings.strictness), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OptionSwitch(short(S.memoryTest), S.memoryTestSub, session.hideText) { session.hideText = it }
            OptionSwitch(short(S.hearFirst), null, session.hintFirst) { session.hintFirst = it }
            Text(S.reciteTip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OptionSwitch(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun MicLevel(level: Float, listening: Boolean) {
    val bars = 12
    Row(Modifier.fillMaxWidth().height(28.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        repeat(bars) { i ->
            val on = listening && i < (level * bars).toInt() + 1
            Surface(
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier.padding(horizontal = 2.dp).width(8.dp).height((8 + i * 1.5).dp),
            ) {}
        }
    }
}

/** Download card for the listening ear (Recite & check). Nothing when it is ready. */
@Composable
fun EarBanner(app: HkApp, showReady: Boolean = false) {
    val st by app.ear.state.collectAsStateWithLifecycle()
    val S = strings()
    if (st == EarState.Ready) {
        if (showReady) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.earReady(app.ear.sizeMb), Modifier.weight(1f), fontSize = 14.sp)
            OutlinedButton(onClick = { app.ear.delete() }) { Text(S.delete) }
        }
        return
    }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(S.earTitle, fontWeight = FontWeight.Bold)
            Text(S.earBody(122), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            when (val s = st) {
                is EarState.Downloading -> {
                    androidx.compose.material3.LinearProgressIndicator(progress = { s.fraction }, Modifier.fillMaxWidth())
                    Text(S.downloading((s.fraction * 100).toInt()), fontSize = 12.sp)
                }
                is EarState.Failed -> {
                    Text(s.message, color = HkColors.bad, fontSize = 13.sp)
                    Button(onClick = { app.ear.download() }) { Text(S.tryAgain) }
                }
                else -> Button(onClick = { app.ear.download() }) { Text("⬇ " + S.download) }
            }
        }
    }
}
