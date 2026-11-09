package com.kharcha.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.ui.screens.HomeScreen
import com.kharcha.app.ui.screens.InsightsScreen
import com.kharcha.app.ui.screens.OnboardingScreen
import com.kharcha.app.ui.screens.PlanScreen
import com.kharcha.app.ui.screens.SettingsScreen
import com.kharcha.app.ui.screens.TransactionSheet
import com.kharcha.app.ui.screens.TransactionsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    LIST("Expenses", Icons.AutoMirrored.Filled.ReceiptLong),
    INSIGHTS("Insights", Icons.Filled.Insights),
    PLAN("Plan", Icons.Filled.Savings),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun KharchaRoot(vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val importState by vm.importState.collectAsState()

    if (!settings.onboarded) {
        OnboardingScreen(
            importState = importState,
            onFinishProfile = { name, income, budget ->
                vm.updateSettings { it.copy(name = name, monthlyIncome = income, monthlyBudget = budget) }
            },
            onImport = { vm.importSms(6) },
            onDone = { vm.updateSettings { it.copy(onboarded = true) } },
        )
        return
    }

    val month by vm.month.collectAsState()
    val report by vm.report.collectAsState()
    val txns by vm.monthTransactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val goals by vm.goals.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val count by vm.transactionCount.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    // null = closed; editing holds a transaction, adding is a flag.
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (Tab.entries[tab] == Tab.HOME || Tab.entries[tab] == Tab.LIST) {
                FloatingActionButton(onClick = { adding = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Add, "Add a transaction")
                }
            }
        },
    ) { padding ->
        val mod = Modifier.padding(padding)
        androidx.compose.foundation.layout.Box(mod) {
            when (Tab.entries[tab]) {
                Tab.HOME -> HomeScreen(
                    settings = settings, month = month, report = report, accounts = accounts, goals = goals, recent = txns,
                    onShiftMonth = vm::shiftMonth, onOpenTransaction = { editing = it },
                    onSeeInsights = { tab = Tab.INSIGHTS.ordinal }, onSeeTransactions = { tab = Tab.LIST.ordinal },
                    onOpenPlan = { tab = Tab.PLAN.ordinal },
                )
                Tab.LIST -> TransactionsScreen(month, txns, vm::shiftMonth) { editing = it }
                Tab.INSIGHTS -> InsightsScreen(month, report, vm::shiftMonth)
                Tab.PLAN -> PlanScreen(
                    settings = settings, report = report, accounts = accounts, budgets = budgets, goals = goals,
                    onSetMonthlyBudget = { v -> vm.updateSettings { it.copy(monthlyBudget = v) } },
                    onSetCategoryBudget = { c, v -> vm.setBudget(c, v) },
                    onSaveAccount = { vm.saveAccount(it) }, onDeleteAccount = { vm.deleteAccount(it) },
                    onSaveGoal = { vm.saveGoal(it) }, onDeleteGoal = { vm.deleteGoal(it) },
                )
                Tab.SETTINGS -> SettingsScreen(
                    settings = settings, importState = importState, transactionCount = count,
                    onUpdate = vm::updateSettings, onImport = { vm.importSms(it) },
                    onExport = { vm.exportCsvIntent() }, onClearAll = { vm.clearAll() },
                )
            }
        }
    }

    if (editing != null || adding) {
        TransactionSheet(
            existing = editing,
            onDismiss = { editing = null; adding = false },
            onSave = { t, all -> vm.saveTransaction(t, all) },
            onAdd = { a, type, m, c, ts, note -> vm.addManual(a, type, m, c, ts, note) },
            onDelete = { vm.deleteTransaction(it) },
        )
    }
}
