package com.kharcha.app.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kharcha.app.KharchaApp
import com.kharcha.app.data.TxnSource
import com.kharcha.app.parser.SmsParser
import kotlinx.coroutines.launch

/** Picks up bank alerts the moment they arrive, so PhonePe payments are logged in real time. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Long SMS arrive in parts; stitch them per sender.
        val bySender = messages.groupBy { it.originatingAddress ?: "" }
        val app = context.applicationContext as KharchaApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                bySender.forEach { (sender, parts) ->
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    val ts = parts.first().timestampMillis
                    SmsParser.parse(sender, body, ts)?.let { parsed ->
                        app.repository.ingest(parsed, TxnSource.SMS)?.let { app.alerts.onNewTransaction(it) }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
