package com.example.harikathamruthasara.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.MainActivity
import com.example.harikathamruthasara.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** Keeps recitation going with the screen off and shows play/pause controls in the notification. */
class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false
    private val player get() = (application as HkApp).player

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Recitation", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows the verse being recited"
                    setShowBadge(false)
                }
            )
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hks:recite").apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            ServiceCompat.startForeground(
                this, NOTIF_ID, build(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            )
            scope.launch {
                player.state.distinctUntilChangedBy { listOf(it.sandhi?.id, it.verseIdx, it.stepIdx, it.repeatNo, it.playing) }
                    .collect { st ->
                        if (st.playing) wakeLock?.acquire(3 * 60 * 60 * 1000L) else if (wakeLock?.isHeld == true) wakeLock?.release()
                        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build())
                    }
            }
        }
        when (intent?.action) {
            ACT_TOGGLE -> player.toggle()
            ACT_NEXT -> player.nextStep()
            ACT_PREV -> player.prevStep()
            ACT_STOP -> {
                player.pause()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    private fun action(act: String) = PendingIntent.getService(
        this, act.hashCode(), Intent(this, PlaybackService::class.java).setAction(act),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun build(): android.app.Notification {
        val st = player.state.value
        val app = application as HkApp
        val total = app.settings.value.repeatCount
        val S = com.example.harikathamruthasara.i18n.Strs(app.settings.value.lang ?: com.example.harikathamruthasara.i18n.Lang.EN)
        val sc = S.lang.script
        val sup = app.settings.value.tamilSuperscripts
        val title = buildString {
            append(com.example.harikathamruthasara.data.Scripts.render(st.sandhi?.title ?: "ಹರಿಕಥಾಮೃತಸಾರ", sc, sup))
            st.verse?.let { append(" · ").append(if (it.isRefrain) S.refrain else S.padya(it.number)) }
        }
        val stepText = st.step?.let { step ->
            val v = st.verse
            if (v == null || step.words.size == 1) step.words.joinToString(" ")
            else (v.words[step.from].line..v.words[step.to - 1].line).joinToString("  ") { v.lines[it].text }
        }
        val text = stepText?.let { com.example.harikathamruthasara.data.Scripts.render(it, sc, sup) } ?: ""
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(st.step?.let { "${st.repeatNo}/${if (total == 0) "∞" else total}" })
            .setContentIntent(open)
            .setOngoing(st.playing)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, S.back, action(ACT_PREV))
            .addAction(
                if (st.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (st.playing) S.pause else S.play, action(ACT_TOGGLE)
            )
            .addAction(android.R.drawable.ic_media_next, S.next, action(ACT_NEXT))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, S.stop, action(ACT_STOP))
            .build()
    }

    companion object {
        const val CHANNEL = "recitation"
        const val NOTIF_ID = 108
        const val ACT_TOGGLE = "hks.toggle"
        const val ACT_NEXT = "hks.next"
        const val ACT_PREV = "hks.prev"
        const val ACT_STOP = "hks.stop"
    }
}
