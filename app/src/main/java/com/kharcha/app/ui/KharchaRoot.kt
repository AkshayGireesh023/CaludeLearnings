package com.kharcha.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.TxnType
import com.kharcha.app.ui.components.inr
import com.kharcha.app.ui.screens.AskScreen
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
    ASK("Ask AI", Icons.Filled.AutoAwesome),
    INSIGHTS("Insights", Icons.Filled.Insights),
    PLAN("Plan", Icons.Filled.Savings),
}

@Composable
fun KharchaRoot(vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val importState by vm.importState.collectAsState()

    // Catch up on bank SMS every time the app comes to the foreground.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.sync() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

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
    val live by vm.liveAccounts.collectAsState()
    val goals by vm.goals.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val count by vm.transactionCount.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val rebuilding by vm.rebuilding.collectAsState()
    val brief by vm.brief.collectAsState()
    val review by vm.review.collectAsState()
    val chat by vm.chat.collectAsState()
    val cleanup by vm.cleanup.collectAsState()
    val keyTest by vm.keyTest.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pendingQuestion by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // A payment just landed: show it, with a one-tap way to fix its category.
    LaunchedEffect(Unit) {
        vm.newTransaction.collect { t ->
            val msg = if (t.type == TxnType.DEBIT) "${t.category.emoji} ${inr(t.amount)} at ${t.merchant} logged"
            else "💰 ${inr(t.amount)} received from ${t.merchant}"
            if (snackbar.showSnackbar(msg, actionLabel = "Edit", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                editing = t
            }
        }
    }
    LaunchedEffect(rebuilding) {
        if (rebuilding) snackbar.showSnackbar("Re-reading your SMS with the improved parser…", duration = SnackbarDuration.Long)
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && tab != 0) { tab = 0 }

    if (showSettings) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.padding(padding)) {
                SettingsScreen(
                    settings = settings, importState = importState, transactionCount = count,
                    onUpdate = vm::updateSettings, onImport = { vm.importSms(it) },
                    onExport = { vm.exportCsvIntent() }, onClearAll = { vm.clearAll() },
                    onBack = { showSettings = false },
                    rebuilding = rebuilding, onRebuild = { vm.rebuild(12) },
                    cleanup = cleanup, onCleanup = vm::runCleanup,
                    keyTest = keyTest, onTestKey = vm::testKey,
                )
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
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
        AnimatedContent(
            targetState = Tab.entries[tab],
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding),
            label = "tabs",
        ) { current ->
            when (current) {
                Tab.HOME -> HomeScreen(
                    settings = settings, month = month, report = report, accounts = live, goals = goals, recent = txns,
                    brief = brief, syncing = syncing, onRefresh = vm::sync, onRefreshBrief = vm::refreshBrief,
                    onShiftMonth = vm::shiftMonth, onOpenTransaction = { editing = it },
                    onSeeInsights = { tab = Tab.INSIGHTS.ordinal }, onSeeTransactions = { tab = Tab.LIST.ordinal },
                    onOpenPlan = { tab = Tab.PLAN.ordinal }, onOpenSettings = { showSettings = true },
                    onAsk = { q -> pendingQuestion = q; tab = Tab.ASK.ordinal },
                )
                Tab.LIST -> TransactionsScreen(month, txns, vm::shiftMonth) { editing = it }
                Tab.ASK -> AskScreen(
                    chat = chat, aiReady = settings.aiReady, pendingQuestion = pendingQuestion,
                    onConsumePending = { pendingQuestion = null }, onAsk = vm::ask, onStop = vm::stopChat,
                    onClear = vm::clearChat, onOpenSettings = { showSettings = true },
                )
                Tab.INSIGHTS -> InsightsScreen(month, report, vm::shiftMonth, review, settings.aiReady, vm::runReview)
                Tab.PLAN -> PlanScreen(
                    settings = settings, report = report, accounts = accounts, budgets = budgets, goals = goals,
                    onSetMonthlyBudget = { v -> vm.updateSettings { it.copy(monthlyBudget = v) } },
                    onSetCategoryBudget = { c, v -> vm.setBudget(c, v) },
                    onSaveAccount = { vm.saveAccount(it) }, onDeleteAccount = { vm.deleteAccount(it) },
                    onSaveGoal = { vm.saveGoal(it) }, onDeleteGoal = { vm.deleteGoal(it) },
                    live = live.associate { it.account.key to it.live },
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
