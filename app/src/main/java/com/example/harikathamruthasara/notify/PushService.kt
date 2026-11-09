package com.example.harikathamruthasara.notify

import com.example.harikathamruthasara.HkApp
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Push messages from the server (Firebase Cloud Messaging). The server sends to topics:
 *   "all"      — news for everyone
 *   "updates"  — corrected text / better chant published (tools/push_notify.py on the server)
 * Each message carries title/body (in the data, so we always build the notification ourselves) and an optional
 * "kind"; for "updates" the app also checks the server for the new content straight away.
 */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage) {
        val app = applicationContext as HkApp
        val title = msg.data["title"] ?: msg.notification?.title ?: return
        val body = msg.data["body"] ?: msg.notification?.body ?: ""
        val kind = msg.data["kind"]
        if (kind == "updates") app.source.check()
        // the reader's language, when the server sent translations: title_ta, body_ta …
        val lang = app.settings.value.lang?.name?.lowercase()
        val t = lang?.let { msg.data["title_$it"] } ?: title
        val b = lang?.let { msg.data["body_$it"] } ?: body
        Notifier.pushed(this, t, b, kind)
    }

    override fun onNewToken(token: String) {
        (applicationContext as HkApp).sync.saveToken(token)
    }

    companion object {
        /** Subscribes to the topics the reader wants (Settings → Notifications). */
        fun syncTopics(app: HkApp) {
            if (FirebaseApp.getApps(app).isEmpty()) return
            val fm = FirebaseMessaging.getInstance()
            fm.subscribeToTopic("all")
            if (app.settings.value.notifyUpdates) fm.subscribeToTopic("updates") else fm.unsubscribeFromTopic("updates")
        }
    }
}
