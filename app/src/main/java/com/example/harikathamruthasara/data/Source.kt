package com.example.harikathamruthasara.data

import android.content.Context
import com.example.harikathamruthasara.audio.VagLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import kotlinx.coroutines.tasks.await
import java.net.URL
import java.security.MessageDigest

private const val REPORT_TIMEOUT_MS = 15_000L
/** firebase/firestore.rules refuses longer notes */
private const val REPORT_NOTE_MAX = 2000

/** One published item in the server's catalog: its fingerprint, where to download it, and why it changed. */
data class Published(val rev: String, val url: String, val bytes: Long, val note: String)

/** [reports]: where problem reports go (relative to the catalog, or absolute — an ntfy inbox for the HF copy). */
/** [ear]: the listening model for Recite & check (speech/Ear.kt). */
data class Catalog(val revision: Int, val text: Published?, val packs: Map<Int, Published>, val reports: String?, val ear: Published? = null)

/** What the app knows about the server right now. */
data class SourceState(
    val checking: Boolean = false,
    val lastCheck: Long = 0L,
    val error: String? = null,
    val revision: Int = 0,
    /** the server has improved text */
    val textUpdate: Boolean = false,
    /** downloaded sandhis whose chant the server has improved */
    val packUpdates: List<Int> = emptyList(),
    /** sandhis whose chant the server has */
    val onServer: Set<Int> = emptySet(),
    val updating: Boolean = false,
) {
    val hasUpdates: Boolean get() = textUpdate || packUpdates.isNotEmpty()
}

/**
 * The app's link to the source of truth: the text and every sandhi's chant are corrected in the server project
 * (HarikathamruthaSaraServer, Vāgdhenu on the maintainer's Mac) and published — to a Hugging Face dataset, free and
 * worldwide — as a catalog (catalog.json) with a fingerprint per item; file urls are relative to the catalog. The app keeps local copies, checks the
 * catalog (at start, when it comes back to the front, or on demand), tells the user when improvements are waiting,
 * and downloads them when she chooses — or by itself when "update automatically" is on.
 */
class Source(
    private val context: Context,
    private val settings: SettingsStore,
    private val vag: VagLibrary,
    /** called after new text was installed, so the app reloads it */
    private val onTextChanged: () -> Unit,
) {
    companion object {
        /** Look at the server at most this often on its own (a manual check always goes). */
        const val CHECK_EVERY_MS = 15 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SourceState())
    val state: StateFlow<SourceState> = _state
    @Volatile private var catalog: Catalog? = null
    private val cacheFile = File(context.filesDir, "catalog.json")
    private val textDir = File(context.filesDir, "text")

    init {
        // vag asks us where each pack lives; answer from the catalog (fetching it if we have none yet)
        vag.source = { n -> (catalog ?: fetch())?.packs?.get(n)?.let { url(it.url) to it.rev } }
        runCatching { catalog = parseCatalog(cacheFile.readText()) }
        // whenever a pack is installed or deleted (downloads finish on their own), refresh what is still waiting
        scope.launch { vag.version.collect { catalog?.let { c -> _state.update { compare(c, it) } } } }
    }

    /** Where the listening model is and its fingerprint (blocking: may fetch the catalog). */
    fun earLocation(): Pair<String, String>? = (catalog ?: fetch())?.ear?.let { url(it.url) to it.rev }

    private val catalogUrl get() = settings.value.server + "/catalog.json"
    private fun url(rel: String) = URL(URL(catalogUrl), rel).toString()

    private fun get(u: String): HttpURLConnection {
        var url = URL(u); var hops = 0
        while (true) {
            val c = openHttp(url)
            c.instanceFollowRedirects = false; c.connectTimeout = 10_000; c.readTimeout = 30_000
            val code = c.responseCode
            if (code in 300..399 && hops++ < 6) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue }
            if (code != 200) throw IllegalStateException("Server said $code")
            return c
        }
    }

    private fun parseCatalog(json: String): Catalog {
        val o = JSONObject(json)
        fun pub(p: JSONObject) = Published(p.getString("rev"), p.getString("url"), p.optLong("bytes"), p.optString("note"))
        val packs = o.optJSONObject("packs")?.let { ps -> ps.keys().asSequence().associate { it.toInt() to pub(ps.getJSONObject(it)) } }.orEmpty()
        return Catalog(o.optInt("revision"), o.optJSONObject("text")?.let(::pub), packs, o.optString("reports").ifBlank { null }, o.optJSONObject("ear")?.let(::pub))
    }

    /** Fetches the catalog now (blocking; call off the main thread). null when the server can't be reached. */
    private fun fetch(): Catalog? = runCatching {
        val json = get(catalogUrl).inputStream.bufferedReader().use { it.readText() }
        parseCatalog(json).also { catalog = it; cacheFile.writeText(json) }
    }.onFailure { e -> _state.update { it.copy(error = e.message ?: "Server not reachable") } }.getOrNull()

    /** Fingerprint of the text the app is using: the downloaded copy, else the one bundled in the app. */
    private fun localTextRev(): String =
        File(textDir, "rev.txt").takeIf { it.exists() && File(textDir, "hks.json").exists() }?.readText()?.trim() ?: bundledRev

    private val bundledRev: String by lazy {
        val md = MessageDigest.getInstance("SHA-256")
        context.assets.open("hks.json").use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun compare(c: Catalog, s: SourceState): SourceState {
        val textRev = runCatching { localTextRev() }.getOrDefault("")
        return s.copy(
            revision = c.revision,
            textUpdate = c.text != null && c.text.rev != textRev,
            packUpdates = c.packs.filter { (n, p) -> vag.isDownloaded(n) && vag.rev(n) != p.rev }.keys.sorted(),
            onServer = c.packs.keys,
        )
    }

    /** Looks at the server's catalog. Throttled to [CHECK_EVERY_MS] unless [force]. */
    fun check(force: Boolean = false) {
        val s = _state.value
        if (s.checking || (!force && System.currentTimeMillis() - s.lastCheck < CHECK_EVERY_MS)) return
        _state.update { it.copy(checking = true) }
        scope.launch {
            val c = fetch()
            _state.update { (if (c != null) compare(c, it).copy(error = null) else it).copy(checking = false, lastCheck = System.currentTimeMillis()) }
            if (c != null && settings.value.autoUpdate && _state.value.hasUpdates) updateAll()
        }
    }

    /** Downloads every waiting improvement: the text, and the chant of each downloaded sandhi that changed. */
    fun updateAll() {
        val c = catalog ?: return check(force = true)
        if (_state.value.updating) return
        _state.update { it.copy(updating = true) }
        scope.launch {
            try {
                if (_state.value.textUpdate && c.text != null) installText(c.text)
                for (n in _state.value.packUpdates) vag.download(n, update = true)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Update failed") }
            } finally {
                _state.update { compare(c, it).copy(updating = false) }
            }
        }
    }

    private fun installText(p: Published) {
        val json = get(url(p.url)).inputStream.bufferedReader().use { it.readText() }
        Library.parse(json)   // never install text the app can't read
        textDir.mkdirs()
        val tmp = File(textDir, "hks.json.tmp"); tmp.writeText(json)
        tmp.renameTo(File(textDir, "hks.json")); File(textDir, "rev.txt").writeText(p.rev)
        Library.invalidate()
        onTextChanged()
    }

    /**
     * Sends a problem report (wrong text, a badly sung place…). Returns true when it was received.
     * Reports go to a Firestore collection that apps can only add to (firebase/firestore.rules); they are read in the
     * Firebase console. A server on this computer or LAN (development) gets them directly instead.
     */
    fun report(sandhi: Int, padya: Int, line: Int?, kind: String, note: String, onDone: (Boolean) -> Unit) {
        scope.launch {
            val app = com.example.harikathamruthasara.BuildConfig.VERSION_NAME
            val inbox = (catalog ?: fetch())?.reports?.let { runCatching { URL(URL(catalogUrl), it) }.getOrNull() }
            val ok = if (inbox != null && isLocalHost(inbox.host)) postToLocalServer(inbox, sandhi, padya, line, kind, note, app)
            else saveToFirestore(sandhi, padya, line, kind, note, app)
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    private fun postToLocalServer(url: URL, sandhi: Int, padya: Int, line: Int?, kind: String, note: String, app: String): Boolean = runCatching {
        val body = JSONObject().put("sandhi", sandhi).put("padya", padya).put("line", line ?: JSONObject.NULL)
            .put("kind", kind).put("note", note).put("voice", "VAG").put("app", app).toString()
        val conn = openHttp(url)
        conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 10_000; conn.readTimeout = 20_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.responseCode in 200..299
    }.getOrDefault(false)

    private suspend fun saveToFirestore(sandhi: Int, padya: Int, line: Int?, kind: String, note: String, app: String): Boolean = runCatching {
        kotlinx.coroutines.withTimeout(REPORT_TIMEOUT_MS) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("reports").add(
                mapOf(
                    "sandhi" to sandhi, "padya" to padya, "line" to line, "kind" to kind, "note" to note.take(REPORT_NOTE_MAX), "voice" to "VAG", "app" to app,
                    "at" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                )
            ).await()
        }
        true
    }.getOrDefault(false)
}
