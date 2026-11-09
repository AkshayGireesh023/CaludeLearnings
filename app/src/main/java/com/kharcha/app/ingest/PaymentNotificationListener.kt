package com.kharcha.app.ingest

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kharcha.app.KharchaApp
import com.kharcha.app.data.TxnSource
import com.kharcha.app.parser.NotificationParser
import kotlinx.coroutines.launch

/**
 * Listens for PhonePe (and other UPI app) payment notifications. Gives clean merchant
 * names and catches payments even when the bank SMS is delayed.
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val recent = ArrayDeque<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in NotificationParser.trackedPackages) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        // Apps re-post the same notification when it updates; skip repeats.
        val fingerprint = "${sbn.packageName}|$title|$text"
        if (fingerprint in recent) return
        recent.addLast(fingerprint)
        if (recent.size > 30) recent.removeFirst()

        val parsed = NotificationParser.parse(sbn.packageName, title, text, sbn.postTime) ?: return
        val app = applicationContext as KharchaApp
        app.appScope.launch {
            app.repository.ingest(parsed, TxnSource.NOTIFICATION)?.let { app.alerts.onNewTransaction(it) }
        }
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, PaymentNotificationListener::class.java).flattenToString()
            return flat.split(':').any { it == me }
        }
    }
}
