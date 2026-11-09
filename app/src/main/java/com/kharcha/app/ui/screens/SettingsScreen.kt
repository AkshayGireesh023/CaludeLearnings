package com.kharcha.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kharcha.app.data.UserSettings
import com.kharcha.app.ui.ImportState
import com.kharcha.app.ui.components.SectionCard
import com.kharcha.app.ui.components.SectionTitle
import com.kharcha.app.ui.components.inr
import com.kharcha.app.ui.notificationAccessIntent
import com.kharcha.app.ui.rememberPermissionStatus
import com.kharcha.app.ui.smsPermissions
import com.kharcha.app.ui.theme.Alert
import com.kharcha.app.ui.theme.Good
import kotlinx.coroutines.launch
import android.content.Intent

@Composable
fun SettingsScreen(
    settings: UserSettings,
    importState: ImportState,
    transactionCount: Int,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onImport: (Long) -> Unit,
    onExport: suspend () -> Intent,
    onClearAll: () -> Unit,
    onBack: () -> Unit = {},
    rebuilding: Boolean = false,
    onRebuild: () -> Unit = {},
    cleanup: com.kharcha.app.ui.CleanupState = com.kharcha.app.ui.CleanupState(),
    onCleanup: () -> Unit = {},
    keyTest: com.kharcha.app.ui.AiTextState = com.kharcha.app.ui.AiTextState(),
    onTestKey: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val perms = rememberPermissionStatus()
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var editing by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val batteryOk = remember(perms) {
        (context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
        }

        item {
            SectionCard {
                SectionTitle("✨ AI brain (Claude)")
                Text(
                    "Powers the daily brief, Ask, month reviews and transaction clean-up. Uses your own Anthropic API key " +
                        "(console.anthropic.com → API keys), billed to your account. Kharcha sends Claude summaries and transaction " +
                        "lines; clean-up also sends the SMS text with account and reference numbers masked.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingRow("API key", if (settings.aiReady) "sk-ant-…" + settings.anthropicKey.takeLast(4) else "Not set") { editing = "key" }
                SettingRow("Model", com.kharcha.app.data.AI_MODELS.firstOrNull { it.first == settings.aiModel }?.second ?: settings.aiModel) { editing = "model" }
                if (settings.aiReady) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onTestKey, enabled = !keyTest.loading) { Text(if (keyTest.loading) "Testing…" else "Test connection") }
                    }
                    keyTest.text.takeIf { it.isNotBlank() }?.let { Text("✓ $it", color = Good, style = MaterialTheme.typography.bodySmall) }
                    keyTest.error?.let { Text(it, color = Alert, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Fix my data")
                Text(
                    "Wrong amounts, duplicates or missing payments? Re-scan reads your SMS again with the latest parser " +
                        "(your edits, notes and manual entries are kept). AI clean-up has Claude re-check names, categories and fake entries.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = perms.sms && !rebuilding && !importState.running, onClick = onRebuild) {
                        Text(if (rebuilding) "Re-scanning…" else "Re-scan 12 months")
                    }
                    Button(enabled = settings.aiReady && !cleanup.running, onClick = onCleanup) {
                        Text(if (cleanup.running) "Cleaning… ${cleanup.reviewed}" else "AI clean-up")
                    }
                }
                if (cleanup.done) {
                    Text(
                        if (cleanup.reviewed == 0) "Everything's already been checked." else "Claude checked ${cleanup.reviewed} transactions and fixed ${cleanup.fixed}.",
                        color = Good, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                cleanup.error?.let { Text(it, color = Alert, style = MaterialTheme.typography.bodySmall) }
            }
        }

        item {
            SectionCard {
                SectionTitle("You")
                SettingRow("Name", settings.name.ifBlank { "Not set" }) { editing = "name" }
                SettingRow("Monthly income", if (settings.monthlyIncome > 0) inr(settings.monthlyIncome) else "Not set") { editing = "income" }
                SettingRow("Savings target", "${settings.savingsTargetPct}% of income") { editing = "target" }
            }
        }

        item {
            SectionCard {
                SectionTitle("Tracking sources")
                PermissionRow(
                    "Bank SMS", "Reads UPI / bank alerts for every PhonePe payment, plus your balance.", perms.sms,
                ) { smsLauncher.launch(smsPermissions) }
                PermissionRow(
                    "PhonePe notifications", "Captures clean merchant names from PhonePe, GPay & Paytm.", perms.notificationAccess,
                ) { context.startActivity(notificationAccessIntent()) }
                PermissionRow(
                    "Budget alerts", "Lets Kharcha warn you when you cross 80% / 100% of a budget.", perms.postNotifications,
                ) { if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                PermissionRow(
                    "Background tracking", "Stops your phone from killing Kharcha, so payments log within minutes even when it's closed.",
                    batteryOk,
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(android.net.Uri.parse("package:" + context.packageName)),
                        )
                    }.onFailure { context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Send budget alerts", modifier = Modifier.weight(1f))
                    Switch(checked = settings.budgetAlerts, onCheckedChange = { v -> onUpdate { it.copy(budgetAlerts = v) } })
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Import past transactions")
                Text(
                    "Scans your SMS inbox for bank alerts. Safe to run again — duplicates are skipped.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (importState.running) {
                    LinearProgressIndicator(progress = { importState.progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3L, 6L, 12L).forEach { m ->
                            OutlinedButton(enabled = perms.sms, onClick = { onImport(m) }) { Text("$m months") }
                        }
                    }
                }
                importState.result?.let {
                    Text(
                        "Scanned ${it.scanned} SMS · found ${it.matched} transactions · ${it.added} new",
                        style = MaterialTheme.typography.bodySmall, color = Good, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                importState.error?.let { Text(it, color = Alert, style = MaterialTheme.typography.bodySmall) }
                if (!perms.sms) Text("Allow Bank SMS above first.", style = MaterialTheme.typography.bodySmall, color = Alert)
            }
        }

        item {
            SectionCard {
                SectionTitle("Your data")
                Text(
                    "$transactionCount transactions stored on this phone. No account, no cloud sync. The internet is used only to talk to Claude, and only if you add a key.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val intent = onExport()
                            context.startActivity(Intent.createChooser(intent, "Export expenses"))
                        }
                    }) { Text("Export CSV") }
                    OutlinedButton(onClick = { confirmClear = true }) { Text("Clear transactions") }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }

    when (editing) {
        "key" -> TextDialog("Claude API key", settings.anthropicKey, onDismiss = { editing = null }) { v ->
            onUpdate { it.copy(anthropicKey = v.trim(), briefDay = "") }; editing = null
        }
        "model" -> AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Claude model") },
            text = {
                Column {
                    com.kharcha.app.data.AI_MODELS.forEach { (id, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onUpdate { it.copy(aiModel = id) }; editing = null }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = settings.aiModel == id, onClick = { onUpdate { it.copy(aiModel = id) }; editing = null })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
        )
        "name" -> TextDialog("Your name", settings.name, onDismiss = { editing = null }) { v -> onUpdate { it.copy(name = v) }; editing = null }
        "income" -> AmountDialog("Monthly take-home income", settings.monthlyIncome, hint = "Used for savings rate and the 50/30/20 check.",
            onDismiss = { editing = null }) { v -> onUpdate { it.copy(monthlyIncome = v) }; editing = null }
        "target" -> AmountDialog("Savings target (%)", settings.savingsTargetPct.toDouble(), hint = "20% is a good start.",
            onDismiss = { editing = null }) { v -> onUpdate { it.copy(savingsTargetPct = v.toInt().coerceIn(0, 90)) }; editing = null }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all transactions?") },
            text = { Text("Removes every stored transaction and account balance. Budgets, goals and settings stay. You can re-import from SMS.") },
            confirmButton = { TextButton(onClick = { onClearAll(); confirmClear = false }) { Text("Clear", color = Alert) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PermissionRow(title: String, body: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) Text("On ✓", color = Good, fontWeight = FontWeight.SemiBold)
        else TextButton(onClick = onGrant) { Text("Allow") }
    }
}

@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
