package com.kharcha.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val name: String = "",
    val monthlyIncome: Double = 0.0,
    val monthlyBudget: Double = 0.0,
    val savingsTargetPct: Int = 20,
    val onboarded: Boolean = false,
    val budgetAlerts: Boolean = true,
    val lastSmsImport: Long = 0,
    /** Last time the SMS inbox was caught up (app open, pull-to-refresh, background worker). */
    val lastSync: Long = 0,
    /** Bumped when the SMS parser improves, triggering a one-time rebuild of old data. */
    val parserVersion: Int = 0,
    val anthropicKey: String = "",
    val aiModel: String = AI_MODELS.first().first,
    val briefDay: String = "",
    val briefText: String = "",
) {
    val aiReady: Boolean get() = anthropicKey.isNotBlank()
}

/** Model id to label. Claude Opus 5.5 is the default; the others are cheaper and faster. */
val AI_MODELS = listOf(
    "claude-opus-5-5" to "Claude Opus 5.5 (smartest)",
    "claude-sonnet-5-5" to "Claude Sonnet 5.5 (balanced)",
    "claude-haiku-5-5" to "Claude Haiku 5.5 (fastest, cheapest)",
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("kharcha_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<UserSettings> = _state.asStateFlow()

    private fun read() = UserSettings(
        name = prefs.getString("name", "") ?: "",
        monthlyIncome = prefs.getFloat("income", 0f).toDouble(),
        monthlyBudget = prefs.getFloat("budget", 0f).toDouble(),
        savingsTargetPct = prefs.getInt("savingsTarget", 20),
        onboarded = prefs.getBoolean("onboarded", false),
        budgetAlerts = prefs.getBoolean("budgetAlerts", true),
        lastSmsImport = prefs.getLong("lastSmsImport", 0),
        lastSync = prefs.getLong("lastSync", 0),
        parserVersion = prefs.getInt("parserVersion", 0),
        anthropicKey = prefs.getString("anthropicKey", "") ?: "",
        aiModel = prefs.getString("aiModel", AI_MODELS.first().first) ?: AI_MODELS.first().first,
        briefDay = prefs.getString("briefDay", "") ?: "",
        briefText = prefs.getString("briefText", "") ?: "",
    )

    @Synchronized
    fun update(block: (UserSettings) -> UserSettings) {
        val s = block(_state.value)
        prefs.edit()
            .putString("name", s.name)
            .putFloat("income", s.monthlyIncome.toFloat())
            .putFloat("budget", s.monthlyBudget.toFloat())
            .putInt("savingsTarget", s.savingsTargetPct)
            .putBoolean("onboarded", s.onboarded)
            .putBoolean("budgetAlerts", s.budgetAlerts)
            .putLong("lastSmsImport", s.lastSmsImport)
            .putLong("lastSync", s.lastSync)
            .putInt("parserVersion", s.parserVersion)
            .putString("anthropicKey", s.anthropicKey)
            .putString("aiModel", s.aiModel)
            .putString("briefDay", s.briefDay)
            .putString("briefText", s.briefText)
            .apply()
        _state.value = s
    }
}
