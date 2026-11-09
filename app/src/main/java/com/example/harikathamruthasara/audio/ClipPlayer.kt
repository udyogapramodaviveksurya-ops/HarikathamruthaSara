package com.example.harikathamruthasara.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DownloadState {
    data class Running(val fraction: Float) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** Plays Vāgdhenu clips: downloaded line clips (files) or the bundled letter clips (assets). */
class ClipPlayer(private val context: Context) {
    private companion object { const val STOP_EARLY = 0.02; const val FADE_IN = 0.02; const val FADE_OUT = 0.04 }
    private var mp: MediaPlayer? = null
    private var loaded: String? = null

    private suspend fun ensure(key: String, source: (MediaPlayer) -> Unit): MediaPlayer {
        mp?.let { if (loaded == key) return it }
        release()
        val p = MediaPlayer()
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        withContext(Dispatchers.IO) { source(p) }
        val ready = CompletableDeferred<Unit>()
        p.setOnPreparedListener { ready.complete(Unit) }
        p.setOnErrorListener { _, _, _ -> ready.complete(Unit); true }
        p.prepareAsync()
        ready.await()
        mp = p; loaded = key
        return p
    }

    private suspend fun seek(p: MediaPlayer, sec: Double) {
        val done = CompletableDeferred<Unit>()
        p.setOnSeekCompleteListener { done.complete(Unit) }
        val ms = (sec * 1000).toLong().coerceAtLeast(0)
        if (Build.VERSION.SDK_INT >= 26) p.seekTo(ms, MediaPlayer.SEEK_CLOSEST) else p.seekTo(ms.toInt())
        done.await()
    }

    private suspend fun run(p: MediaPlayer, start: Double, end: Double, speed: Float, onPosition: (Double) -> Unit): Long {
        seek(p, start)
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
        // a range that starts or ends inside the take may cut through sound (a word running across a line,
        // ಸ- | ಮೀರ): fade in/out a few ms there so the cut is never heard as a blip of the neighbouring syllable
        val fadeIn = start > 0.0
        val fadeOut = end < Double.MAX_VALUE / 2
        p.setVolume(if (fadeIn) 0f else 1f, if (fadeIn) 0f else 1f)
        val t0 = System.currentTimeMillis()
        p.start()
        try {
            // stop a hair early and check often: MediaPlayer reports position coarsely and pause() lands a few ms
            // late, which used to let the next line's first sound slip into the end of this one
            val stopAt = end - STOP_EARLY * speed
            while (true) {
                val pos = p.currentPosition / 1000.0
                onPosition(pos)
                if (pos >= stopAt || !p.isPlaying) break
                var v = 1.0
                if (fadeIn) v = minOf(v, (pos - start) / FADE_IN)
                if (fadeOut) v = minOf(v, (stopAt - pos) / FADE_OUT)
                val vol = v.coerceIn(0.0, 1.0).toFloat(); p.setVolume(vol, vol)
                delay(5)
            }
        } finally {
            if (p.isPlaying) p.pause()
            p.setVolume(1f, 1f)
        }
        return System.currentTimeMillis() - t0
    }

    /** Plays [start, end) seconds of [file]; returns wall-clock ms. [onPosition] gets the position in seconds. */
    suspend fun play(file: File, start: Double, end: Double, speed: Float, onPosition: (Double) -> Unit = {}): Long =
        run(ensure(file.absolutePath) { it.setDataSource(file.absolutePath) }, start, end, speed, onPosition)

    /** Plays a whole clip bundled in assets (e.g. "letters/ಕ.m4a"); false when it isn't there. */
    suspend fun playAsset(path: String): Boolean {
        val afd = runCatching { context.assets.openFd(path) }.getOrNull() ?: return false
        val p = ensure("asset:$path") { afd.use { a -> it.setDataSource(a.fileDescriptor, a.startOffset, a.length) } }
        run(p, 0.0, Double.MAX_VALUE, 1f) {}
        return true
    }

    fun pause() { runCatching { if (mp?.isPlaying == true) mp?.pause() } }

    fun release() { runCatching { mp?.release() }; mp = null; loaded = null }
}
