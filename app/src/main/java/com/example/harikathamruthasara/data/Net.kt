package com.example.harikathamruthasara.data

import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

/**
 * Opens an http(s) connection. A server on this computer or the local network (the source-of-truth server while
 * it runs on a Mac/PC) is reached directly, never through a proxy — a proxy can't see it (the emulator's own
 * proxy refuses 10.0.2.2, for example). Anything else (a hosted server) honours the device's proxy settings.
 */
fun openHttp(url: URL): HttpURLConnection =
    (if (isLocalHost(url.host)) url.openConnection(Proxy.NO_PROXY) else url.openConnection()) as HttpURLConnection

/**
 * Runs a download attempt again whenever the network drops, for up to about 10 minutes, so a dead spot or a change
 * between Wi-Fi and mobile data doesn't end it. Anything that isn't a network error fails at once.
 */
suspend fun <T> retryingNetwork(attempt: suspend () -> T): T {
    repeat(NETWORK_RETRIES) {
        try { return attempt() } catch (_: java.io.IOException) { kotlinx.coroutines.delay(NETWORK_RETRY_MS) }
    }
    return attempt()
}
private const val NETWORK_RETRIES = 60
private const val NETWORK_RETRY_MS = 10_000L

/** The server is busy (temporary), as opposed to refusing the file. */
fun isTemporaryHttpError(code: Int) = code == 429 || code >= 500

fun isLocalHost(h: String): Boolean {
    if (h == "localhost" || h.endsWith(".local")) return true
    val p = h.split('.').mapNotNull { it.toIntOrNull() }
    if (p.size != 4) return false
    return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
}
