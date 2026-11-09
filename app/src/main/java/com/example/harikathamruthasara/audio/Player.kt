package com.example.harikathamruthasara.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.harikathamruthasara.data.GapMode
import com.example.harikathamruthasara.data.ProgressStore
import com.example.harikathamruthasara.data.Sandhi
import com.example.harikathamruthasara.data.SettingsStore
import com.example.harikathamruthasara.data.StudyUnit
import com.example.harikathamruthasara.data.Verse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StepKind { WORD, LINE, PART, WHOLE, LINES }

/** A piece of a verse that is spoken and repeated as one unit. Word indices are verse-global, [from, to). */
class Step(val words: List<String>, val from: Int, val kind: StepKind, val n: Int) {
    val to: Int get() = from + words.size
    operator fun contains(global: Int) = global in from until to
}

object Planner {
    fun plan(v: Verse, unit: StudyUnit): List<Step> {
        val lineStart = IntArray(v.lines.size)
        var g = 0
        v.lines.forEachIndexed { i, l -> lineStart[i] = g; g += l.words.size }
        fun linesStep(a: Int, b: Int, kind: StepKind, n: Int) =
            Step(v.lines.subList(a, b + 1).flatMap { it.words }, lineStart[a], kind, n)
        return when (unit) {
            StudyUnit.LINE -> v.lines.indices.map { linesStep(it, it, StepKind.LINE, it + 1) }
            StudyUnit.SENTENCE -> buildList {
                var a = 0
                v.lines.forEachIndexed { i, l ->
                    if (l.sentenceEnd || i == v.lines.lastIndex) { add(linesStep(a, i, StepKind.PART, size + 1)); a = i + 1 }
                }
            }
            StudyUnit.VERSE -> listOf(linesStep(0, v.lines.lastIndex, StepKind.WHOLE, 1))
            StudyUnit.BUILDUP -> v.lines.indices.map { linesStep(0, it, if (it == 0) StepKind.LINE else StepKind.LINES, it + 1) }
        }
    }
}

enum class Phase { IDLE, SPEAKING, YOUR_TURN }

data class PlayerState(
    val sandhi: Sandhi? = null,
    val verseIdx: Int = 0,
    val steps: List<Step> = emptyList(),
    val stepIdx: Int = 0,
    val repeatNo: Int = 1,
    val playing: Boolean = false,
    val phase: Phase = Phase.IDLE,
    val currentWord: Int = -1,
    val sleepAtMs: Long = 0L,
    /** set when playback reached the end of the last padya of the sandhi */
    val finishedSandhi: Boolean = false,
    /** set when playback stopped because this sandhi's chant isn't downloaded yet */
    val needsDownload: Boolean = false,
) {
    val verse: Verse? get() = sandhi?.verses?.getOrNull(verseIdx)
    val step: Step? get() = steps.getOrNull(stepIdx)
}

/** Plays a padya step by step, with repeats and "your turn" gaps, in the Vāgdhenu chant (the only voice). */
class Player(
    private val app: Context,
    private val settings: SettingsStore,
    private val progress: ProgressStore,
    val vag: VagLibrary,
) {
    val clip = ClipPlayer(app)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private var job: Job? = null
    private var sleepJob: Job? = null

    init {
        scope.launch {
            settings.flow.map { it.unit }.distinctUntilChanged().drop(1).collect { u ->
                restart { s -> s.verse?.let { s.copy(steps = Planner.plan(it, u), stepIdx = 0) } ?: s }
            }
        }
    }

    fun open(sandhi: Sandhi, verseIdx: Int) {
        val cur = _state.value
        if (cur.sandhi?.id == sandhi.id && cur.verseIdx == verseIdx) return
        restart(keepPlaying = false) { it.copy(sandhi = sandhi, verseIdx = verseIdx, steps = plan(sandhi, verseIdx), stepIdx = 0) }
        progress.setPosition(sandhi.id, verseIdx)
    }

    fun play() {
        if (_state.value.verse == null || job?.isActive == true) return
        _state.update { it.copy(playing = true, finishedSandhi = false, needsDownload = false) }
        startService()
        job = scope.launch { loop() }
    }

    fun pause() {
        job?.cancel(); job = null
        clip.pause()
        _state.update { it.copy(playing = false, phase = Phase.IDLE, currentWord = -1) }
        vag.applyPending()   // an updated pack waiting for playback to stop
    }

    /** Stops the audio and closes its notification, so it can't be restarted from outside the app. */
    fun stopAndClose() {
        pause()
        app.stopService(Intent(app, PlaybackService::class.java))
    }

    fun toggle() = if (_state.value.playing) pause() else play()

    fun nextStep() = moveStep(+1)
    fun prevStep() = moveStep(-1)
    fun goToStep(i: Int) = restart { it.copy(stepIdx = i.coerceIn(0, (it.steps.size - 1).coerceAtLeast(0))) }

    fun nextVerse() = moveVerse(+1)
    fun prevVerse() = moveVerse(-1)
    fun goToVerse(i: Int) {
        val s = _state.value.sandhi ?: return
        val idx = i.coerceIn(0, s.verses.lastIndex)
        restart { it.copy(verseIdx = idx, steps = plan(s, idx), stepIdx = 0) }
        progress.setPosition(s.id, idx)
    }

    /** Tapping a word jumps to the step that contains it. */
    fun tapWord(global: Int) {
        val i = _state.value.steps.indexOfFirst { global in it }
        if (i >= 0) goToStep(i)
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _state.update { it.copy(sleepAtMs = 0) }; return }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepAtMs = at) }
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            pause()
            _state.update { it.copy(sleepAtMs = 0) }
        }
    }

    private fun moveStep(d: Int) {
        val s = _state.value
        val ni = s.stepIdx + d
        when {
            ni in s.steps.indices -> goToStep(ni)
            d > 0 -> moveVerse(+1)
            else -> moveVerse(-1, toLastStep = true)
        }
    }

    private fun moveVerse(d: Int, toLastStep: Boolean = false) {
        val s = _state.value
        val sandhi = s.sandhi ?: return
        val ni = s.verseIdx + d
        if (ni !in sandhi.verses.indices) return
        val steps = plan(sandhi, ni)
        restart { it.copy(verseIdx = ni, steps = steps, stepIdx = if (toLastStep) steps.lastIndex else 0) }
        progress.setPosition(sandhi.id, ni)
    }

    private fun plan(sandhi: Sandhi, idx: Int) = Planner.plan(sandhi.verses[idx], settings.value.unit)

    private fun restart(keepPlaying: Boolean = true, f: (PlayerState) -> PlayerState) {
        val wasPlaying = _state.value.playing && keepPlaying
        job?.cancel(); job = null
        clip.pause()
        _state.update { f(it).copy(repeatNo = 1, currentWord = -1, phase = Phase.IDLE, playing = wasPlaying, finishedSandhi = false, needsDownload = false) }
        if (wasPlaying) job = scope.launch { loop() }
    }

    private suspend fun loop() {
        while (true) {
            val st = _state.value
            val verse = st.verse ?: break
            val step = st.step ?: break
            val s = settings.value
            if (s.repeatCount != 0 && st.repeatNo > s.repeatCount) {
                if (!advance()) break
                continue
            }
            _state.update { it.copy(phase = Phase.SPEAKING, currentWord = step.from) }
            val dur = say(st.sandhi!!.id, verse, step) { w ->
                _state.update { if (it.step === step) it.copy(currentWord = w) else it }
            }
            if (dur == null) {   // no Vāgdhenu chant for this padya yet: stop and ask for the download
                _state.update { it.copy(playing = false, phase = Phase.IDLE, currentWord = -1, needsDownload = true) }
                job = null
                return
            }
            if (dur > 0) progress.addListen(verse.key, ((dur + 500) / 1000).toInt())
            val gap = when (s.gapMode) {
                GapMode.NONE -> 300L
                GapMode.FIXED -> s.gapSeconds * 1000L
                GapMode.ECHO -> (dur * 1.15).toLong() + 700L
            }
            _state.update { it.copy(phase = if (s.gapMode == GapMode.NONE) Phase.SPEAKING else Phase.YOUR_TURN, currentWord = -1) }
            delay(gap.coerceAtLeast(300L))
            if (s.gapMode != GapMode.NONE && gap >= 1000) progress.addSeconds((gap / 1000).toInt())
            _state.update { it.copy(repeatNo = it.repeatNo + 1) }
        }
        _state.update { it.copy(playing = false, phase = Phase.IDLE, currentWord = -1, stepIdx = 0, repeatNo = 1, finishedSandhi = it.sandhi != null && it.verseIdx == it.sandhi.verses.lastIndex) }
        job = null
        vag.applyPending()   // an updated pack waiting for playback to stop
    }

    /** Moves to the next step, or next verse when auto-advance is on. Returns false at the end. */
    private fun advance(): Boolean {
        val st = _state.value
        if (st.stepIdx + 1 < st.steps.size) {
            _state.update { it.copy(stepIdx = it.stepIdx + 1, repeatNo = 1) }
            return true
        }
        val sandhi = st.sandhi ?: return false
        progress.checkSandhiBadge(sandhi)
        if (settings.value.autoAdvance && st.verseIdx + 1 < sandhi.verses.size) {
            val ni = st.verseIdx + 1
            _state.update { it.copy(verseIdx = ni, steps = plan(sandhi, ni), stepIdx = 0, repeatNo = 1) }
            progress.setPosition(sandhi.id, ni)
            return true
        }
        return false
    }

    /** True when this padya can be chanted (its sandhi's Vāgdhenu pack is downloaded). */
    fun usesVag(sandhiId: Int, verse: Verse): Boolean =
        vag.isDownloaded(sandhiId) && vag.line(sandhiId, verse.number, 0) != null

    /**
     * Chants one step. Each half-padya is one sung take, so the lines of a step that lie in the same half play
     * as ONE continuous range — no gap at line breaks, and a word running across a line (ಸ-|ಮೀರ) stays whole.
     * The script's '-' and '--' are silent pauses: never heard as gaps. The only stop is at the || between halves
     * (~1.2 s, measured on Tadipatri Gurukula's recitation). [onWord] receives the verse-global word index.
     * Returns ms played, or null when this padya isn't downloaded.
     */
    suspend fun say(sandhiId: Int, verse: Verse, step: Step, onWord: (Int) -> Unit = {}): Long? {
        if (!vag.isDownloaded(sandhiId)) return null
        val speed = settings.value.speed
        val l0 = verse.words[step.from].line
        val l1 = verse.words[step.to - 1].line
        val clips = (l0..l1).associateWith { li -> vag.line(sandhiId, verse.number, li)?.takeIf { it.words.size == verse.lines[li].words.size } ?: return null }
        val t0 = System.currentTimeMillis()
        if (step.words.size == 1) {
            val w = verse.words[step.from]
            val (a, b) = clips.getValue(w.line).words[w.index]
            clip.play(clips.getValue(w.line).file, (a - 0.03).coerceAtLeast(0.0), b + 0.06, speed) { onWord(step.from) }
            return System.currentTimeMillis() - t0
        }
        // runs of consecutive lines sung in the same take
        val runs = mutableListOf<MutableList<Int>>()
        for (li in l0..l1) {
            if (runs.isNotEmpty() && clips.getValue(runs.last().last()).file == clips.getValue(li).file) runs.last() += li else runs += mutableListOf(li)
        }
        runs.forEachIndexed { k, run ->
            // (start, global word index) for every word in this run, in time order
            val marks = run.flatMap { li ->
                val base = verse.words.first { it.line == li }.global
                clips.getValue(li).words.mapIndexed { wi, (a, _) -> a to base + wi }
            }
            val first = clips.getValue(run.first()); val last = clips.getValue(run.last())
            clip.play(first.file, first.start, last.end, speed) { pos ->
                var w = 0
                while (w + 1 < marks.size && pos >= marks[w + 1].first) w++
                onWord(marks[w].second)
            }
            if (k < runs.lastIndex) delay((1200 / speed).toLong())
        }
        return System.currentTimeMillis() - t0
    }

    /**
     * Says a step for Recite, a tapped word and the quiz. Returns ms played; 0 (silence) when this padya's chant
     * isn't downloaded yet — those screens show the download banner instead.
     */
    suspend fun sayAnyVoice(sandhiId: Int, verse: Verse, step: Step, onWord: (Int) -> Unit = {}): Long =
        say(sandhiId, verse, step, onWord) ?: 0L

    /** True when this padya can be said now. */
    fun canSay(sandhiId: Int, verse: Verse): Boolean = usesVag(sandhiId, verse)

    /** One word of a padya, cut from its line's chant. */
    fun sayWord(sandhiId: Int, verse: Verse, global: Int) {
        pause()
        val w = verse.words.getOrNull(global) ?: return
        scope.launch { sayAnyVoice(sandhiId, verse, Step(listOf(w.text), global, StepKind.WORD, global + 1)) }
    }

    /** One whole line of a padya (quiz "listen and pick"). */
    fun sayLine(sandhiId: Int, verse: Verse, line: Int) {
        pause()
        val ws = verse.words.filter { it.line == line }
        if (ws.isEmpty()) return
        scope.launch { sayAnyVoice(sandhiId, verse, Step(ws.map { it.text }, ws.first().global, StepKind.LINE, line + 1)) }
    }

    /** A Kannada letter or syllable from the bundled Vāgdhenu letter clips. False when there's no clip for it. */
    fun hasLetter(text: String): Boolean = runCatching { app.assets.openFd(letterAsset(text)).close() }.isSuccess

    fun sayLetter(text: String) {
        pause()
        // bundled clips of Google's Kannada voice (user's choice 2026-11-07: Vāgdhenu letters were wrong or inaudible);
        // every vowel, consonant, game syllable and kagunita cell — made by tools/letters/ (no phone voice at runtime)
        scope.launch { clip.playAsset(letterAsset(text)) }
    }

    /** A "Say it" practice word (bundled clip). */
    fun sayPracticeWord(text: String) {
        pause()
        scope.launch { clip.playAsset(wordAsset(text)) }
    }

    private fun startService() {
        runCatching { ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java)) }
    }
}

/** Asset path of a letter clip; file names use code points so they are plain ASCII ("ಕಾ" -> letters/0c95_0cbe.m4a). */
fun letterAsset(text: String): String = "letters/" + text.map { "%04x".format(it.code) }.joinToString("_") + ".m4a"

/** Asset path of a "Say it" practice word clip (Google's Kannada voice, like the letters): words/<code points>.m4a */
fun wordAsset(text: String): String = "words/" + text.map { "%04x".format(it.code) }.joinToString("_") + ".m4a"
