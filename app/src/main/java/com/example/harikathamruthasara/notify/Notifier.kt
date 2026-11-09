package com.example.harikathamruthasara.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.MainActivity
import com.example.harikathamruthasara.R
import com.example.harikathamruthasara.data.LEVELS
import com.example.harikathamruthasara.data.Reward
import com.example.harikathamruthasara.data.today
import com.example.harikathamruthasara.i18n.AccountText
import com.example.harikathamruthasara.i18n.Lang
import com.example.harikathamruthasara.i18n.Strs
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Every notification the app posts, by channel. Channels let the reader silence one kind in Android settings; the
 * app's own Settings → Notifications switches do the same from inside the app.
 */
object Notifier {
    const val CH_REMIND = "reminder"        // daily reminder (existing id kept) + streak nudges + come-back
    const val CH_ACHIEVE = "achievements"
    const val CH_DOWNLOAD = "downloads"
    const val CH_UPDATES = "updates"        // improvements ready, news from the server (push)

    private const val ID_STREAK = 101; private const val ID_COMEBACK = 102; private const val ID_UPDATES = 103
    private const val ID_ACHIEVE = 200; private const val ID_DOWNLOAD = 300

    private fun lang(ctx: Context) = (ctx.applicationContext as HkApp).settings.value.lang ?: Lang.EN

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val A = AccountText(lang(ctx))
        val nm = ctx.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: String, imp: Int) = NotificationChannel(id, name.trimStart { !it.isLetter() }, imp)
        nm.createNotificationChannels(listOf(
            ch(CH_REMIND, A.chReminders, NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_ACHIEVE, A.nAchieve, NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_DOWNLOAD, A.nDownloads, NotificationManager.IMPORTANCE_LOW),
            ch(CH_UPDATES, A.nUpdates, NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    /** Android lets the app post (permission on 13+, and not switched off for the app). */
    fun allowed(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** The app is on screen: say things in the app instead of in the notification shade. */
    fun inForeground(): Boolean = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun openApp(ctx: Context, req: Int) = PendingIntent.getActivity(
        ctx, req, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun post(ctx: Context, channel: String, id: Int, title: String, body: String) {
        if (!allowed(ctx)) return
        createChannels(ctx)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFC43E00.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(ctx, id))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    fun reward(ctx: Context, r: Reward) {
        val app = ctx.applicationContext as HkApp
        if (!app.settings.value.notifyAchievements) return
        val S = Strs(lang(ctx))
        val (title, body) = when (r) {
            is Reward.BadgeEarned -> "${r.badge.emoji} ${S.newBadge}" to "${S.badgeTitle(r.badge.id)} — ${S.badgeHint(r.badge.id)}"
            is Reward.LevelUp -> "${r.level.emoji} ${S.levelUp}" to S.nowLevel(S.levelNames[LEVELS.indexOf(r.level)])
            is Reward.GoalMet -> "🎯 ${S.goalDone}" to S.goalDoneBody(r.minutes) + " " + S.krishnarpanam + " 🙏"
        }
        post(ctx, CH_ACHIEVE, ID_ACHIEVE + (title.hashCode() and 0xff), title, body)
    }

    fun downloadDone(ctx: Context, sandhiTitle: String) {
        if (!(ctx.applicationContext as HkApp).settings.value.notifyDownloads) return
        val A = AccountText(lang(ctx))
        post(ctx, CH_DOWNLOAD, ID_DOWNLOAD, A.downloadDone, A.downloadDoneBody(sandhiTitle))
    }

    fun downloadFailed(ctx: Context) {
        if (!(ctx.applicationContext as HkApp).settings.value.notifyDownloads) return
        val A = AccountText(lang(ctx))
        post(ctx, CH_DOWNLOAD, ID_DOWNLOAD + 1, A.downloadFail, A.downloadFailBody)
    }

    fun updatesReady(ctx: Context, text: Boolean, packs: List<Int>) {
        if (!(ctx.applicationContext as HkApp).settings.value.notifyUpdates) return
        val S = Strs(lang(ctx))
        post(ctx, CH_UPDATES, ID_UPDATES, S.updatesTitle, S.updatesBody(text, packs))
    }

    /** A message pushed from the server (Firebase Cloud Messaging). */
    fun pushed(ctx: Context, title: String, body: String, kind: String?) {
        if (kind == "updates" && !(ctx.applicationContext as HkApp).settings.value.notifyUpdates) return
        post(ctx, CH_UPDATES, ID_UPDATES + 1 + (title.hashCode() and 0xff), title, body)
    }

    // ---------------------------------------------------------------- nudges (checked in the background)
    private const val PREFS = "notify"

    /** Evening streak reminder and a gentle "come back" after 3 quiet days; at most once a day each. */
    fun nudges(ctx: Context) {
        val app = ctx.applicationContext as HkApp
        val s = app.settings.value
        val p = app.progress.state.value
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = today()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val A = AccountText(s.lang ?: Lang.EN)
        val todaySec = p.history[d] ?: 0
        if (s.notifyStreak && p.streak > 0 && todaySec == 0 && hour in 18..21 && prefs.getLong("streakDay", -1) != d) {
            prefs.edit().putLong("streakDay", d).apply()
            post(ctx, CH_REMIND, ID_STREAK, A.streakTitle(p.streak), A.streakBody)
        }
        val lastActive = p.history.filterValues { it > 0 }.keys.maxOrNull() ?: return
        if (s.notifyStreak && d - lastActive >= 3 && hour in 10..19 && d - prefs.getLong("comebackDay", -10) >= 3) {
            prefs.edit().putLong("comebackDay", d).apply()
            post(ctx, CH_REMIND, ID_COMEBACK, A.comeBackTitle, A.comeBackBody)
        }
    }

    /** Runs [NudgeWorker] every few hours (survives reboots; Android picks battery-friendly moments). */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<NudgeWorker>(3, TimeUnit.HOURS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("nudges", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class NudgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Notifier.nudges(applicationContext)
        // look for corrected text / better chant on the server; HkApp notifies when some is found
        (applicationContext as HkApp).source.check()
        return Result.success()
    }
}
