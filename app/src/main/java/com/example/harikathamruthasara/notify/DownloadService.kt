package com.example.harikathamruthasara.notify

import android.app.Service
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.R
import com.example.harikathamruthasara.audio.DownloadState
import com.example.harikathamruthasara.i18n.Lang
import com.example.harikathamruthasara.i18n.Strs
import com.example.harikathamruthasara.speech.EarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps downloads (chant packs and the listening model) going when the reader leaves the app or locks the phone:
 * Android cuts the network of apps in the background and lets the CPU sleep, so while any download runs the app holds
 * this foreground service, with a progress notification and wake locks. It stops itself when the last download ends.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching: Job? = null
    private var cpu: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.createChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        runCatching { ServiceCompat.startForeground(this, ID, build(0f, 1), type) }.onFailure { stopSelf(); return START_NOT_STICKY }
        holdLocks()
        if (watching?.isActive == true) return START_NOT_STICKY   // every new download starts the service again
        val app = applicationContext as HkApp
        var shown = -1; var shownAt = 0L
        watching = scope.launch {
            combine(app.vag.downloads, app.ear.state) { packs, ear ->
                packs.values.filterIsInstance<DownloadState.Running>().map { it.fraction } +
                    listOfNotNull((ear as? EarState.Downloading)?.fraction)
            }.collect { running ->
                if (running.isEmpty()) { ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf(); return@collect }
                // Android drops notifications updated more than ~5 times a second: only on a visible change, ≤ 1/s
                val f = running.average().toFloat()
                val pct = (f * 100).toInt()
                val now = System.currentTimeMillis()
                if (pct != shown && now - shownAt >= 1000) {
                    shown = pct; shownAt = now
                    val nm = getSystemService(android.app.NotificationManager::class.java)
                    runCatching { nm.notify(ID, build(f, running.size)) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(fraction: Float, n: Int) = NotificationCompat.Builder(this, Notifier.CH_DOWNLOAD)
        .setSmallIcon(R.drawable.ic_notify)
        .setColor(0xFFC43E00.toInt())
        .setContentTitle("🎶 " + Strs((applicationContext as HkApp).settings.value.lang ?: Lang.EN).downloading((fraction * 100).toInt()))
        .setProgress(100, (fraction * 100).toInt(), fraction <= 0f)
        .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)   // otherwise Android holds it back ~10 s
        .apply { if (n > 1) setSubText("×$n") }
        .build()

    /** The screen going off must not put the CPU or Wi-Fi to sleep mid-download. */
    private fun holdLocks() {
        if (cpu == null) cpu = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hks:download").apply { setReferenceCounted(false); acquire(LOCK_MS) }
        if (wifi == null) wifi = runCatching {
            @Suppress("DEPRECATION")
            applicationContext.getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "hks:download").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
    }

    /** Android's daily limit for this kind of service was reached. */
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    override fun onDestroy() {
        scope.cancel()
        runCatching { cpu?.takeIf { it.isHeld }?.release() }
        runCatching { wifi?.takeIf { it.isHeld }?.release() }
        super.onDestroy()
    }

    companion object {
        private const val ID = 310
        private const val LOCK_MS = 6L * 60 * 60 * 1000
        fun start(ctx: Context) = runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java)) }
    }
}
