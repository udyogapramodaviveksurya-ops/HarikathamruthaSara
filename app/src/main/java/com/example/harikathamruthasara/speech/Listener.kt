package com.example.harikathamruthasara.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class ListenState(
    val listening: Boolean = false,
    /** the attempt is being checked (after listening stopped) */
    val analyzing: Boolean = false,
    val committed: String = "",
    val partial: String = "",
    val level: Float = 0f,
    val error: String? = null,
) {
    val text: String get() = listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Listens to a recitation with the phone's microphone and the on-device ear ([EarStore]) — offline, no Google.
 * While she recites, what the ear has heard so far is shown ([ListenState.partial], refreshed every ~1.5 s). When the
 * attempt ends — [stop], or a long enough silence after speaking — the whole recording is heard once more, the
 * letters go to [ListenState.committed], the ear's per-frame output to [lastLogProbs] (for [Pronunciation]), and
 * [onSegmentDone] fires on the main thread.
 * [start]'s `continuous` = a long recitation with breaths (half/full padya): wait longer before ending by itself.
 */
class Listener(private val ear: EarStore) {
    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state
    @Volatile private var wanted = false
    @Volatile private var continuous = false
    @Volatile private var maxSeconds = 120
    @Volatile private var quickEnd = false

    /** the ear's output for the last finished attempt (null if nothing was said) */
    @Volatile var lastLogProbs: Array<FloatArray>? = null; private set
    var onSegmentDone: (() -> Unit)? = null
    var strs: com.example.harikathamruthasara.i18n.Strs = com.example.harikathamruthasara.i18n.Strs(com.example.harikathamruthasara.i18n.Lang.EN)

    val available: Boolean get() = ear.isReady

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var job: Job? = null

    /**
     * [maxSeconds]: listen at most this long — a safety net for a weak microphone, where the voice barely rises above
     * the room and the silence that should end the attempt can't be told apart (hands-free would wait forever).
     * [quickEnd]: a single letter or word — end ~1 s after the voice stops.
     */
    fun start(continuous: Boolean, reset: Boolean = true, maxSeconds: Int = 120, quickEnd: Boolean = false) {
        job?.cancel()
        this.continuous = continuous
        this.quickEnd = quickEnd
        this.maxSeconds = maxSeconds.coerceIn(5, 120)
        lastLogProbs = null
        if (reset) _state.value = ListenState(listening = true)
        else _state.update { it.copy(listening = true, analyzing = false, error = null) }
        if (!ear.isReady) { reportError(strs.earMissing); return }
        wanted = true
        job = scope.launch { record() }
    }

    /** Ends listening; the attempt is checked and then [onSegmentDone] fires. */
    fun stop() { wanted = false }

    /** Ends listening and throws the attempt away (no [onSegmentDone]). */
    fun cancel() {
        wanted = false
        job?.cancel(); job = null
        _state.update { it.copy(listening = false, analyzing = false, partial = "", level = 0f) }
    }

    fun destroy() = cancel()

    private suspend fun record() {
        val me = currentCoroutineContext()[Job]
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate / 5 * 2))
        } catch (e: SecurityException) { reportError(strs.micNeeded); return }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); reportError(strs.micBusy); return }

        var data = FloatArray(rate * 20); var n = 0
        val chunk = ShortArray(rate / 10)                // 100 ms
        var noise = -1.0; var spoke = false; var silentMs = 0; var peak = 0.0
        var firstSpeech = -1; var lastSpeech = -1
        var lastLive = 0L; var live: Job? = null
        // quickEnd: one letter or word (Read Kannada → Say it) — don't keep her waiting after she has said it
        val endAfterMs = if (continuous) 5000 else if (quickEnd) 1100 else 2500
        val maxSamples = rate * maxSeconds
        try {
            rec.startRecording()
            while (wanted && currentCoroutineContext().isActive && n < maxSamples) {
                val r = rec.read(chunk, 0, chunk.size)
                if (r <= 0) continue
                if (n + r > data.size) data = data.copyOf(maxOf(data.size * 2, n + r))
                var sum = 0.0
                for (i in 0 until r) { val v = chunk[i] / 32768f; data[n + i] = v; sum += v * v }
                n += r
                val rms = sqrt(sum / r)
                // noise floor: the first 0.3 s, then it follows the quietest moments (a digitally silent chunk —
                // a glitch of some audio drivers and the emulator — tells nothing about the room, so it is skipped)
                if (rms > 1e-4) noise = when {
                    noise < 0 -> rms
                    n <= rate * 3 / 10 -> (noise + rms) / 2
                    rms < noise -> rms
                    else -> noise * 1.01
                }
                // relative to the room: many phones' voice-recognition microphone has no automatic gain, so a
                // recitation can peak at only a few percent of full scale (the ear doesn't mind: it normalises)
                // quickEnd: once she has spoken, much quieter than her voice = silence (room echo and the emulator's
                // speaker→microphone path kept the old threshold busy for the full 8 s, 2026-11-08)
                val speech = rms > maxOf(0.002, noise * 3, if (quickEnd && spoke) peak * 0.12 else 0.0)
                if (speech) peak = maxOf(peak, rms)
                _state.update { it.copy(level = ((20 * kotlin.math.log10(rms.coerceAtLeast(1e-5)) + 55) / 40).toFloat().coerceIn(0f, 1f)) }
                if (speech) { spoke = true; silentMs = 0; if (firstSpeech < 0) firstSpeech = n - r; lastSpeech = n }
                else if (spoke) { silentMs += r * 1000 / rate; if (silentMs >= endAfterMs) break }
                else if (n > rate * 12) break           // nothing said for 12 s
                // what has been heard so far, while she recites
                val now = System.currentTimeMillis()
                if (!quickEnd && n > rate && now - lastLive > 1500 && live?.isActive != true) {
                    lastLive = now
                    val snap = data.copyOfRange(if (firstSpeech < 0) 0 else maxOf(0, firstSpeech - rate * 3 / 10), n)
                    live = scope.launch(Dispatchers.Default) {
                        val lp = runCatching { ear.listen(snap) }.getOrNull() ?: return@launch
                        if (job === me && wanted) _state.update { it.copy(partial = Pronunciation.greedy(lp)) }
                    }
                }
            }
        } finally {
            runCatching { rec.stop() }; rec.release()
        }
        debugDump?.let { f -> runCatching { writeWav(f, data, n, rate) } }
        if (job !== me) return                          // cancelled or restarted meanwhile
        wanted = false
        // the ear always gets the recording (the loudness guess above only decides when to stop listening);
        // when the voice was found, the silence around it is trimmed
        val enough = n > rate / 2
        _state.update { it.copy(listening = false, analyzing = enough, level = 0f) }
        live?.join()
        val lp = if (enough) {
            var a = if (spoke) maxOf(0, firstSpeech - rate * 3 / 10) else 0
            var b = if (spoke) minOf(n, lastSpeech + rate * 3 / 10) else n
            // one letter / word: only the loud part (echo and noise around it made "ಅಮ್ಮ" match "ದೇವರು")
            if (quickEnd) tightSpan(data, a, b, rate)?.let { (x, y) -> a = x; b = y }
            runCatching { ear.listen(data.copyOfRange(a, b)) }.getOrNull()
        } else null
        if (job !== me) return
        lastLogProbs = lp
        val heard = lp?.let(Pronunciation::greedy).orEmpty()
        _state.update { it.copy(committed = heard, partial = "", analyzing = false) }
        main.post { if (job === me) onSegmentDone?.invoke() }
    }

    /** The part of [d] (a..b) that is louder than 8% of its loudest 20 ms, with 0.15 s around it; null if too short. */
    private fun tightSpan(d: FloatArray, a: Int, b: Int, rate: Int): Pair<Int, Int>? {
        val win = rate / 50
        val rms = (a until b - win step win).map { i -> var s = 0.0; for (k in i until i + win) s += d[k] * d[k]; sqrt(s / win) }
        val top = rms.maxOrNull() ?: return null
        val loud = rms.indices.filter { rms[it] > top * 0.08 }
        if (loud.isEmpty()) return null
        val x = maxOf(a, a + loud.first() * win - rate * 15 / 100)
        val y = minOf(b, a + (loud.last() + 1) * win + rate * 15 / 100)
        return if (y - x >= rate / 5) x to y else null
    }

    /** Debug builds: where to keep the last attempt's recording (to check the microphone and tune the ear). */
    var debugDump: java.io.File? = null

    private fun writeWav(f: java.io.File, d: FloatArray, n: Int, rate: Int) {
        val b = java.nio.ByteBuffer.allocate(44 + n * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(rate).putInt(rate * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(n * 2)
        for (i in 0 until n) b.putShort((d[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        f.writeBytes(b.array())
    }

    private fun reportError(msg: String) {
        wanted = false
        _state.update { it.copy(listening = false, analyzing = false, level = 0f, partial = "", error = msg) }
        main.post { onSegmentDone?.invoke() }
    }
}
