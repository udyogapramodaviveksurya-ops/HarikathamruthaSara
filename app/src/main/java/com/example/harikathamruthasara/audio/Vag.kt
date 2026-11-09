package com.example.harikathamruthasara.audio

import android.content.Context
import org.json.JSONArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import com.example.harikathamruthasara.data.openHttp
import com.example.harikathamruthasara.data.isTemporaryHttpError
import com.example.harikathamruthasara.data.retryingNetwork

/**
 * One line of a padya: the half-padya take it is sung in ([file]), its range [start, end) in seconds inside
 * that take, and each word's start/end (same clock). Lines of one half share a file, so they play seamlessly
 * and a word running across a line break is never cut.
 */
class VagLine(val file: File, val start: Double, val end: Double, val words: List<Pair<Double, Double>>)

/**
 * Chanted audio made with Vāgdhenu (Prof. Prathosh A P, IISc) from the Tadipatri script, one take per
 * half-padya, with exact word timings. Each sandhi is a zip (takes + manifest.json) from the source-of-truth
 * server; where to get it and its fingerprint come from the server's catalog ([source], see data/Source.kt).
 *
 * Every downloaded pack remembers its fingerprint (rev.txt), so the app can tell when the server has an improved
 * one. An update for a sandhi that is playing right then is swapped in only when playback stops ([applyPending]).
 */
class VagLibrary(context: Context) {
    companion object {
        /** Shown when the server still has a pack made from the old text; the app shows its own translated message. */
        const val OUTDATED = "outdated-pack"
        /** The server has no pack for this sandhi yet (or can't be reached); the app shows its own message. */
        const val NOT_ON_SERVER = "not-on-server"
        /**
         * Pack format the app plays (tools/vagdhenu/pack.py writes it): 3 = half-padya takes with Vāgdhenu's stray
         * pauses tightened and lines cut inside the silence before the next line.
         */
        const val PACK_VERSION = 3
    }

    /**
     * Set by the app: where to download a sandhi's pack and its fingerprint, from the server's catalog
     * (called on a background thread; may fetch the catalog). null = the server has no pack for it.
     */
    var source: (Int) -> Pair<String, String>? = { null }

    /** Set by the app: true while this sandhi is being played, so an update waits instead of cutting in. */
    var isBusy: (Int) -> Boolean = { false }
    /** Set by the app: called after a pack is replaced, so nothing keeps playing the old files. */
    var onReplaced: (Int) -> Unit = {}
    private val pending = HashMap<Int, Pair<File, String>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root = File(context.filesDir, "vag").apply { mkdirs() }
    private val manifests = HashMap<Int, JSONObject?>()
    /** "Download all" queues 33 packs; a few at a time keeps a mobile connection usable. */
    private val slots = Semaphore(3)

    private val _downloads = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<Int, DownloadState>> = _downloads
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun dir(sandhiId: Int) = File(root, "%02d".format(sandhiId))
    /** Downloaded, and in the current pack format ([PACK_VERSION]); older packs must be fetched again. */
    fun isDownloaded(sandhiId: Int) = manifest(sandhiId)?.optInt("version") == PACK_VERSION
    fun downloadedCount() = (1..33).count { isDownloaded(it) }

    private fun manifest(sandhiId: Int): JSONObject? = synchronized(manifests) {
        manifests.getOrPut(sandhiId) {
            runCatching { JSONObject(File(dir(sandhiId), "manifest.json").readText()) }.getOrNull()
        }
    }

    /** The clip for line [line] of padya [padya] (0 = refrain), or null if not downloaded. */
    fun line(sandhiId: Int, padya: Int, line: Int): VagLine? {
        val key = "%03d_%d".format(padya, line)
        val m = manifest(sandhiId)?.optJSONObject("lines")?.optJSONObject(key) ?: return null
        val f = File(dir(sandhiId), m.getString("f") + ".m4a"); if (!f.exists()) return null
        val ws = m.getJSONArray("words")
        return VagLine(f, m.getDouble("start"), m.getDouble("end"), (0 until ws.length()).map { val p = ws.getJSONArray(it); p.getDouble(0) to p.getDouble(1) })
    }

    /** Fingerprint of the downloaded pack ("" if none or unknown). */
    fun rev(sandhiId: Int) = runCatching { File(dir(sandhiId), "rev.txt").readText().trim() }.getOrDefault("")

    private fun open(url0: String): HttpURLConnection {
        var url = URL(url0); var hops = 0
        while (true) {
            val conn = openHttp(url)
            conn.instanceFollowRedirects = false; conn.connectTimeout = 20_000; conn.readTimeout = 30_000
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 6) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
            if (isTemporaryHttpError(code)) throw java.io.IOException("Server said $code")
            if (code != 200) throw IllegalStateException("Server said $code")
            return conn
        }
    }

    /** Swaps in updates that were waiting for playback to stop. */
    fun applyPending() {
        val ready = synchronized(pending) { pending.keys.filter { !isBusy(it) }.associateWith { pending.remove(it)!! } }
        for ((n, p) in ready) install(n, p.first, p.second)
    }

    private fun install(sandhiId: Int, tmp: File, rev: String) {
        val target = dir(sandhiId)
        File(tmp, "rev.txt").writeText(rev)
        target.deleteRecursively(); tmp.renameTo(target)
        synchronized(manifests) { manifests.remove(sandhiId) }
        onReplaced(sandhiId)
        _version.update { it + 1 }
    }

    /** Downloads (or with [update], re-downloads) a sandhi's pack from the server. */
    fun download(sandhiId: Int, update: Boolean = false) {
        if (_downloads.value[sandhiId] is DownloadState.Running || (!update && isDownloaded(sandhiId))) return
        if (update && synchronized(pending) { sandhiId in pending }) return
        _downloads.update { it + (sandhiId to DownloadState.Running(0f)) }
        scope.launch {
            val tmp = File(root, "%02d.tmp".format(sandhiId))
            try {
                val rev = slots.withPermit { retryingNetwork {
                        tmp.deleteRecursively(); tmp.mkdirs()
                        val (url, rev) = source(sandhiId) ?: throw IllegalStateException(NOT_ON_SERVER)
                        val conn = open(url)
                        val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                        var done = 0L; var lastEmit = 0L
                        val counting = object : java.io.FilterInputStream(conn.inputStream) {
                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                val n = super.read(b, off, len)
                                if (n > 0) {
                                    done += n
                                    if (total > 0 && done - lastEmit > 128 * 1024) {
                                        lastEmit = done
                                        _downloads.update { it + (sandhiId to DownloadState.Running((done.toFloat() / total).coerceIn(0f, 1f))) }
                                    }
                                }
                                return n
                            }
                        }
                        ZipInputStream(counting.buffered()).use { zin ->
                            while (true) {
                                val e = zin.nextEntry ?: break
                                val name = File(e.name).name   // flat files only; never trust paths inside the zip
                                if (!e.isDirectory && name.isNotBlank()) File(tmp, name).outputStream().use { zin.copyTo(it) }
                            }
                        }
                        rev
                } }
                if (!File(tmp, "manifest.json").exists()) throw IllegalStateException("Download was incomplete")
                // older packs: v1 = Wikisource text, one clip per line; v2 = stray pauses + line cuts that leaked
                val version = runCatching { JSONObject(File(tmp, "manifest.json").readText()).optInt("version") }.getOrDefault(0)
                if (version != PACK_VERSION) throw IllegalStateException(OUTDATED)
                _downloads.update { it - sandhiId }
                if (update && isBusy(sandhiId)) synchronized(pending) { pending[sandhiId] = tmp to rev }   // swap after playback
                else install(sandhiId, tmp, rev)
            } catch (e: Exception) {
                tmp.deleteRecursively()
                // a failed quiet update keeps the current pack and stays silent; a first download shows the error
                _downloads.update { if (update) it - sandhiId else it + (sandhiId to DownloadState.Failed(e.message ?: "Download failed. Check internet.")) }
            }
        }
    }

    fun downloadAll() = (1..33).forEach { download(it) }
    fun delete(sandhiId: Int) { dir(sandhiId).deleteRecursively(); synchronized(manifests) { manifests.remove(sandhiId) }; _version.update { it + 1 } }
    fun deleteAll() = (1..33).forEach { delete(it) }
}
