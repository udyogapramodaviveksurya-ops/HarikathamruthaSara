package com.example.harikathamruthasara.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.example.harikathamruthasara.data.openHttp
import com.example.harikathamruthasara.data.isTemporaryHttpError
import com.example.harikathamruthasara.data.retryingNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.security.MessageDigest

sealed interface EarState {
    data object Missing : EarState
    data class Downloading(val fraction: Float) : EarState
    data class Failed(val message: String) : EarState
    data object Ready : EarState
}

/**
 * The "ear": a Kannada letter model (Vakyansh wav2vec2, CTC, exported by the server's tools/ear/export_ear.py) that
 * Recite & check uses to hear every letter — offline, no Google. Downloaded once (~122 MB) from the server's catalog
 * ("ear" entry, [source]); its sha256 is checked against the catalog's fingerprint before it is used.
 */
class EarStore(context: Context) {
    /** Set by the app: where to download the model and its fingerprint (sha256), from the catalog. Background thread. */
    var source: () -> Pair<String, String>? = { null }

    private val dir = File(context.filesDir, "ear").apply { mkdirs() }
    private val file = File(dir, "ear.onnx")
    private val revFile = File(dir, "rev.txt")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<EarState>(if (file.exists() && revFile.exists()) EarState.Ready else EarState.Missing)
    val state: StateFlow<EarState> = _state
    val isReady: Boolean get() = _state.value == EarState.Ready
    fun rev(): String = runCatching { revFile.readText().trim() }.getOrDefault("")
    val sizeMb: Long get() = if (file.exists()) file.length() / 1_000_000 else 0

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private val lock = Any()

    fun download() {
        if (_state.value is EarState.Downloading) return
        _state.value = EarState.Downloading(0f)
        scope.launch {
            val tmp = File(dir, "ear.onnx.tmp")
            try {
                tmp.delete()
                // the model is 122 MB: after a dropped connection, carry on from the bytes already saved
                val (md, rev) = retryingNetwork {
                    val (url, rev) = source() ?: throw IllegalStateException("The server has no listening model yet.")
                    val have = tmp.length()
                    val conn = open(url, have)
                    val resumed = have > 0 && conn.responseCode == 206
                    var done = if (resumed) have else 0L
                    val total = conn.contentLengthLong.takeIf { it > 0 }?.plus(done) ?: -1L
                    val md = MessageDigest.getInstance("SHA-256")
                    if (resumed) tmp.inputStream().use { old -> digest(old, md) }
                    var lastEmit = done
                    conn.inputStream.use { inp ->
                        java.io.FileOutputStream(tmp, resumed).use { out ->
                            val buf = ByteArray(1 shl 16)
                            while (true) {
                                val n = inp.read(buf); if (n < 0) break
                                out.write(buf, 0, n); md.update(buf, 0, n); done += n
                                if (total > 0 && done - lastEmit > 512 * 1024) { lastEmit = done; _state.value = EarState.Downloading(done.toFloat() / total) }
                            }
                        }
                    }
                    md to rev
                }
                val sha = md.digest().joinToString("") { "%02x".format(it) }
                if (sha != rev) throw IllegalStateException("Download was damaged. Please try again.")
                synchronized(lock) { close(); tmp.renameTo(file); revFile.writeText(rev) }
                _state.value = EarState.Ready
            } catch (e: Exception) {
                tmp.delete()
                _state.value = EarState.Failed(e.message ?: "Download failed. Check internet.")
            }
        }
    }

    fun delete() = synchronized(lock) { close(); file.delete(); revFile.delete(); _state.value = EarState.Missing }

    private fun close() { session?.close(); session = null }

    /**
     * Per-frame log-probabilities ([frames][66], one frame per 20 ms) for 16 kHz mono [samples] in -1..1.
     * Blocking and CPU-heavy: call off the main thread. null when the model isn't downloaded.
     */
    fun listen(samples: FloatArray): Array<FloatArray>? = synchronized(lock) {
        if (!isReady || samples.size < 1600) return null
        val e = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val s = session ?: e.createSession(file.absolutePath, OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        }).also { session = it }
        OnnxTensor.createTensor(e, FloatBuffer.wrap(samples), longArrayOf(1, samples.size.toLong())).use { input ->
            s.run(mapOf("audio" to input)).use { out ->
                @Suppress("UNCHECKED_CAST")
                (out[0].value as Array<Array<FloatArray>>)[0]
            }
        }
    }

    private fun digest(from: java.io.InputStream, md: MessageDigest) {
        val buf = ByteArray(1 shl 16)
        while (true) { val n = from.read(buf); if (n < 0) break; md.update(buf, 0, n) }
    }

    /** [from] > 0 asks the server for the rest of the file, from that byte. */
    private fun open(url0: String, from: Long): HttpURLConnection {
        var url = URL(url0); var hops = 0
        while (true) {
            val conn = openHttp(url)
            conn.instanceFollowRedirects = false; conn.connectTimeout = 20_000; conn.readTimeout = 60_000
            if (from > 0) conn.setRequestProperty("Range", "bytes=$from-")
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 6) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
            if (code == 416) { File(dir, "ear.onnx.tmp").delete(); throw java.io.IOException("Start again") }
            if (isTemporaryHttpError(code)) throw java.io.IOException("Server said $code")
            if (code != 200 && code != 206) throw IllegalStateException("Server said $code")
            return conn
        }
    }
}
