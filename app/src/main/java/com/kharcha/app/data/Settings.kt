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
    )

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
            .apply()
        _state.value = s
    }
}
