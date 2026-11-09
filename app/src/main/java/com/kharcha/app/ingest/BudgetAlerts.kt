package com.kharcha.app.ingest

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
import com.kharcha.app.MainActivity
import com.kharcha.app.R
import com.kharcha.app.data.Repository
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.isExpense
import com.kharcha.app.insights.Money
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Nudges you the moment a payment pushes a budget past 80% or 100%. */
class BudgetAlerts(private val context: Context, private val repo: Repository) {
    private val prefs = context.getSharedPreferences("kharcha_alerts", Context.MODE_PRIVATE)

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Warnings when a payment pushes you close to or over budget"
                },
            )
        }
    }

    suspend fun onNewTransaction(t: TransactionEntity) {
        if (!t.isExpense || !repo.settings.state.value.budgetAlerts) return
        val month = YearMonth.from(Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault()))
        if (month != YearMonth.now()) return // don't alert while importing history

        val overallBudget = repo.settings.state.value.monthlyBudget
        if (overallBudget > 0) {
            check("all", "Monthly budget", repo.monthSpend(null, month), overallBudget, month)
        }
        repo.budgets.first().firstOrNull { it.category == t.category }?.let { b ->
            check(b.category.name, "${b.category.emoji} ${b.category.label}", repo.monthSpend(b.category, month), b.monthlyLimit, month)
        }
    }

    private fun check(key: String, label: String, spent: Double, limit: Double, month: YearMonth) {
        val pct = spent / limit * 100
        val level = when {
            pct >= 100 -> 100
            pct >= 80 -> 80
            else -> return
        }
        val prefKey = "$month:$key:$level"
        if (prefs.getBoolean(prefKey, false)) return
        prefs.edit().putBoolean(prefKey, true).apply()
        val title = if (level == 100) "$label budget crossed" else "$label at ${pct.toInt()}%"
        val body = if (level == 100) "${Money.inr(spent)} spent of ${Money.inr(limit)}. Time to pause the wants."
        else "${Money.inr(limit - spent)} left for the rest of the month."
        notify(prefKey.hashCode(), title, body)
    }

    private fun notify(id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    companion object {
        const val CHANNEL = "budget_alerts"
    }
}
