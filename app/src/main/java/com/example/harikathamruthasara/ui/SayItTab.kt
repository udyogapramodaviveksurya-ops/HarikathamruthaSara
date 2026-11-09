package com.example.harikathamruthasara.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.data.Scripts
import com.example.harikathamruthasara.audio.letterAsset
import com.example.harikathamruthasara.audio.wordAsset
import com.example.harikathamruthasara.i18n.strings
import com.example.harikathamruthasara.speech.EarState
import com.example.harikathamruthasara.speech.Listener
import com.example.harikathamruthasara.speech.Pronunciation
import com.example.harikathamruthasara.speech.SayCheck
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The practice letters for Say it (and the letters a letter is compared with). */
private val SAY_LETTERS = VOWELS + CONSONANTS + GAME_SYLLABLES

/** Hands-free: tries per letter or word before moving on, and unanswered tries in a row before it stops listening. */
private const val HANDS_FREE_ATTEMPTS = 2
private const val HANDS_FREE_MAX_SILENT = 3

/** assets/saycheck/refs.bin, loaded once (~2 MB). */
private object SayRefs {
    @Volatile private var refs: SayCheck.Refs? = null
    fun get(app: HkApp): SayCheck.Refs = refs ?: synchronized(this) {
        refs ?: SayCheck.load(app.assets.open("saycheck/refs.bin").use { it.readBytes() }).also { refs = it }
    }
}

/**
 * Read Kannada → 🎤 Say it: she hears a letter or a simple word, says it, and the ear tells her whether it was right
 * ([SayCheck]) — offline. A near miss offers both sounds (the right one and what it sounded like) to hear side by side.
 */
@Composable
internal fun SayItTab(app: HkApp) {
    val S = strings()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val ear by app.ear.state.collectAsStateWithLifecycle()
    val listener = remember {
        Listener(app.ear).also {
            if (com.example.harikathamruthasara.BuildConfig.DEBUG) it.debugDump = java.io.File(app.cacheDir, "sayit_last.wav")
        }
    }
    listener.strs = S
    val ls by listener.state.collectAsStateWithLifecycle()
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { listener.destroy(); tone?.release() } }

    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it }
    val refs by produceState<SayCheck.Refs?>(null) { value = withContext(Dispatchers.IO) { runCatching { SayRefs.get(app) }.getOrNull() } }

    var words by rememberSaveable { mutableStateOf(false) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val items = if (words) PRACTICE_WORDS else SAY_LETTERS
    val item = items[index.mod(items.size)]
    val current by rememberUpdatedState(item)
    var verdict by remember { mutableStateOf<SayCheck.Verdict?>(null) }
    var judging by remember { mutableStateOf(false) }
    var right by rememberSaveable { mutableIntStateOf(0) }
    var tried by rememberSaveable { mutableIntStateOf(0) }

    /** Kannada with the reader's own script beside it, e.g. "ಹಾಲು (ஹாலு)" — just Kannada when that is her script */
    fun spelled(t: String): String = if (t in PRACTICE_WORDS) Scripts.renderWord(t, S.lang.script, settings.tamilSuperscripts) else Scripts.render(t, S.lang.script, settings.tamilSuperscripts)
    fun both(t: String): String = spelled(t).let { if (it == t || it.isBlank()) t else "$t ($it)" }
    fun hear(t: String) { listener.cancel(); if (words) app.player.sayPracticeWord(t) else app.player.sayLetter(t) }
    fun go(step: Int) { listener.cancel(); verdict = null; index = (index + step).mod(items.size) }
    /** Judges the attempt the listener just finished. */
    suspend fun judge(target: String, isWord: Boolean, r: SayCheck.Refs): SayCheck.Verdict {
        val lp = listener.lastLogProbs
        judging = true
        return withContext(Dispatchers.Default) {
            if (listener.state.value.error != null || lp == null) SayCheck.Verdict.NotHeard
            else if (isWord) SayCheck.judgeWord(lp, target, PRACTICE_WORDS, r)
            else SayCheck.judgeLetter(lp, target, SAY_LETTERS, r, settings.strictness)
        }.also { judging = false }
    }
    fun score(v: SayCheck.Verdict, isWord: Boolean) {
        if (v == SayCheck.Verdict.NotHeard) return
        tried++
        val ok = v == SayCheck.Verdict.Ok
        if (ok) right++
        if (!isWord) app.progress.recordLetter(ok)
        tone?.startTone(if (ok) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 250)
    }
    fun say() {
        if (!hasMic) { micLauncher.launch(Manifest.permission.RECORD_AUDIO); return }
        app.player.pause(); app.player.clip.pause()
        verdict = null
        val target = item; val isWord = words
        listener.onSegmentDone = {
            val r = refs
            if (r != null) scope.launch {
                val v = judge(target, isWord, r)
                if (target != current) return@launch         // she moved on meanwhile
                verdict = v
                score(v, isWord)
            }
        }
        listener.start(continuous = false, maxSeconds = 8, quickEnd = true)
    }

    // Hands-free: the app says each letter or word, listens, answers with a tone, and moves on by itself.
    var handsFree by rememberSaveable { mutableStateOf(false) }
    var playingClip by remember { mutableStateOf(false) }
    var stoppedForSilence by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { handsFree = false }   // never keep the microphone open out of sight
    val view = LocalView.current
    DisposableEffect(handsFree) {
        view.keepScreenOn = handsFree
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(handsFree) {
        val r = refs
        if (!handsFree || r == null) return@LaunchedEffect
        val isWord = words
        var silent = 0
        try {
            while (true) {
                val target = items[index.mod(items.size)]
                repeat(HANDS_FREE_ATTEMPTS) attempt@{ n ->
                    if (verdict == SayCheck.Verdict.Ok && n > 0) return@attempt
                    verdict = null
                    app.player.pause()
                    playingClip = true
                    app.player.clip.playAsset(if (isWord) wordAsset(target) else letterAsset(target))
                    playingClip = false
                    delay(350)                                // let the clip's echo die before the microphone opens
                    val done = CompletableDeferred<Unit>()
                    listener.onSegmentDone = { done.complete(Unit) }
                    listener.start(continuous = false, maxSeconds = 5, quickEnd = true)
                    done.await()
                    val v = judge(target, isWord, r)
                    verdict = v
                    score(v, isWord)
                    silent = if (v == SayCheck.Verdict.NotHeard) silent + 1 else 0
                    if (silent >= HANDS_FREE_MAX_SILENT) { stoppedForSilence = true; handsFree = false; return@LaunchedEffect }
                    delay(900)
                }
                verdict = null
                index = (items.indexOf(target) + 1).mod(items.size)
            }
        } finally {
            playingClip = false
            judging = false
            listener.cancel()
            app.player.clip.pause()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).readable(560.dp).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ear != EarState.Ready) EarBanner(app)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !words, enabled = !handsFree, onClick = { if (words) { words = false; index = 0; verdict = null; listener.cancel() } }, label = { Text(S.sayLetters) })
            FilterChip(selected = words, enabled = !handsFree, onClick = { if (!words) { words = true; index = 0; verdict = null; listener.cancel() } }, label = { Text(S.sayWords) })
        }
        if (tried > 0) Pill(S.sayStreak(right, tried))
        Text(if (handsFree) S.sayHandsFreeHow else S.sayHow, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        // the letter / word: tap to hear it
        BouncyCard(onClick = { if (!handsFree) hear(item) }, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Column(Modifier.padding(horizontal = 40.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item, fontSize = if (words) 52.sp else 72.sp, fontWeight = FontWeight.Bold)
                Text(spelled(item), fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                Text("🔊", fontSize = 22.sp)
            }
        }

        // big microphone (in hands-free, one big Stop instead)
        val busy = ls.listening || ls.analyzing || judging
        val canStart = ear == EarState.Ready && refs != null && !busy
        if (handsFree) Button(
            onClick = { handsFree = false },
            colors = ButtonDefaults.buttonColors(containerColor = HkColors.bad, contentColor = MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        ) { Text(S.sayHandsFreeStop, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        else Surface(
            onClick = { if (ls.listening) listener.stop() else if (!busy) say() },
            enabled = ear == EarState.Ready && refs != null && !ls.analyzing && !judging,
            shape = CircleShape,
            color = if (ls.listening) HkColors.bad else MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(96.dp),
        ) { Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(if (ls.listening) "⏹" else "🎤", fontSize = 40.sp) } }
        when {
            ls.listening -> {
                Text(S.sayListening, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { ls.level }, Modifier.fillMaxWidth(0.6f))
            }
            ls.analyzing || judging -> Text(S.checking)
            handsFree && playingClip -> Text(S.sayHandsFreeHear, fontWeight = FontWeight.SemiBold)
        }

        // the verdict
        verdict?.let { v ->
            Surface(shape = RoundedCornerShape(18.dp), color = when (v) {
                SayCheck.Verdict.Ok -> HkColors.good.copy(alpha = 0.15f)
                is SayCheck.Verdict.Mistake -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when (v) {
                        SayCheck.Verdict.Ok -> S.sayOk
                        is SayCheck.Verdict.Mistake -> S.sayMistake(both(v.said), v.kind == Pronunciation.Kind.LENGTH)
                        is SayCheck.Verdict.Wrong -> if (words && v.heard != null) S.sayOtherWord(both(v.heard)) else S.sayNotSure
                        SayCheck.Verdict.NotHeard -> ls.error ?: S.didntHear
                    }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (v is SayCheck.Verdict.Mistake && !handsFree) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { hear(item) }) { Text("🔊 " + both(item)) }
                        OutlinedButton(onClick = { listener.cancel(); app.player.sayLetter(v.said) }) { Text("🔊 " + both(v.said)) }
                    }
                    if (!words && !handsFree && v is SayCheck.Verdict.Wrong) Text(S.sayLetterNote, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!handsFree) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { go(-1) }) { Text("◀") }
                Button(onClick = { go(+1) }) { Text(S.next + " ▶") }
            }
            Button(
                onClick = {
                    if (!hasMic) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else { listener.cancel(); stoppedForSilence = false; verdict = null; handsFree = true }
                },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text(S.sayHandsFreeStart, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            if (stoppedForSilence) Text(S.sayHandsFreeStopped, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
}
