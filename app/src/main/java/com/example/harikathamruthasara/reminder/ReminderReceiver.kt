package com.example.harikathamruthasara.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.MainActivity
import com.example.harikathamruthasara.R
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HkApp
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Reminders.schedule(context); return }
        if (intent.action != ACTION) return
        val p = app.progress.state.value
        val s = app.settings.value
        if (p.todaySeconds >= s.dailyGoalMinutes * 60) return // already done today 🙏
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val S = com.example.harikathamruthasara.i18n.Strs(s.lang ?: com.example.harikathamruthasara.i18n.Lang.EN)
        val text = S.reminderBody(p.streak, s.dailyGoalMinutes)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(S.reminderTitle)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(77, n) }
    }

    companion object {
        const val ACTION = "hks.reminder"
        const val CHANNEL = "reminder"
    }
}

object Reminders {
    fun schedule(context: Context) {
        val app = context.applicationContext as HkApp
        val s = app.settings.value
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (!s.reminderOn) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, s.reminderHour); set(Calendar.MINUTE, s.reminderMinute); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }
}
