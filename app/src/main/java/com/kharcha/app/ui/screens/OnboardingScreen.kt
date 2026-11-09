package com.kharcha.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kharcha.app.data.UserSettings
import com.kharcha.app.ui.ImportState
import com.kharcha.app.ui.components.SectionCard
import com.kharcha.app.ui.notificationAccessIntent
import com.kharcha.app.ui.rememberPermissionStatus
import com.kharcha.app.ui.smsPermissions
import com.kharcha.app.ui.theme.Forest
import com.kharcha.app.ui.theme.Gold

@Composable
fun OnboardingScreen(
    importState: ImportState,
    onFinishProfile: (name: String, income: Double, budget: Double) -> Unit,
    onImport: () -> Unit,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    when (step) {
        0 -> Welcome { step = 1 }
        1 -> Profile { n, i, b -> onFinishProfile(n, i, b); step = 2 }
        else -> Connect(importState, onImport, onDone)
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Forest).systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text("KHARCHA", style = MaterialTheme.typography.labelSmall, color = Gold)
        Spacer(Modifier.height(12.dp))
        Text("Every rupee,\naccounted for.", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(
            "Kharcha reads your PhonePe and bank alerts, files every spend into your monthly expense list, and tells you exactly where to cut.",
            style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No login. No cloud. Your money data never leaves this phone.",
            style = MaterialTheme.typography.bodySmall, color = Gold,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Forest),
        ) { Text("Start building discipline") }
    }
}

@Composable
private fun Profile(onNext: (String, Double, Double) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf("") }
    var budget by rememberSaveable { mutableStateOf("") }
    val incomeVal = income.toDoubleOrNull() ?: 0.0
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Set your baseline", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Three numbers. You can change them anytime.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            income, { v -> income = v.filter(Char::isDigit) }, label = { Text("Monthly take-home income (₹)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            budget, { v -> budget = v.filter(Char::isDigit) }, label = { Text("Monthly spending budget (₹)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                if (incomeVal > 0) Text("Tip: ${com.kharcha.app.ui.components.inr(incomeVal * 0.8)} keeps 20% for savings.")
            },
            colors = OutlinedTextFieldDefaults.colors(),
        )
        if (incomeVal > 0 && budget.isEmpty()) {
            TextButton(onClick = { budget = (incomeVal * 0.8).toLong().toString() }) { Text("Use the 80% budget") }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onNext(name.trim(), incomeVal, budget.toDoubleOrNull() ?: 0.0) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Continue") }
    }
}

@Composable
private fun Connect(importState: ImportState, onImport: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val perms = rememberPermissionStatus()
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) onImport()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !perms.postNotifications) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Connect your payments", style = MaterialTheme.typography.headlineMedium)
        Text(
            "PhonePe doesn't share your history with other apps, but your bank texts you for every payment. Kharcha reads those.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard {
            PermissionRow("1. Bank SMS", "Required. Logs every UPI payment and reads your balance.", perms.sms) {
                smsLauncher.launch(smsPermissions)
            }
            PermissionRow("2. PhonePe notifications", "Recommended. Cleaner merchant names, instant capture.", perms.notificationAccess) {
                context.startActivity(notificationAccessIntent())
            }
        }
        if (importState.running) {
            Text("Importing your past 6 months…")
            LinearProgressIndicator(progress = { importState.progress }, modifier = Modifier.fillMaxWidth())
        }
        importState.result?.let {
            Text("Found ${it.matched} transactions in ${it.scanned} messages. 🎉", color = MaterialTheme.colorScheme.primary)
        }
        if (perms.sms && importState.result == null && !importState.running) {
            TextButton(onClick = onImport) { Text("Import last 6 months") }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone, enabled = !importState.running,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (perms.sms) "Show me my money" else "Skip for now") }
    }
}
