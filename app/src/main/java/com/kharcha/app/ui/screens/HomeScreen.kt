package com.kharcha.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.data.GoalEntity
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.UserSettings
import com.kharcha.app.insights.LiveAccount
import com.kharcha.app.insights.MonthReport
import com.kharcha.app.ui.AiTextState
import com.kharcha.app.ui.components.AnimatedMoney
import com.kharcha.app.ui.components.BudgetBar
import com.kharcha.app.ui.components.DailyBars
import com.kharcha.app.ui.components.GoalBar
import com.kharcha.app.ui.components.InsightRow
import com.kharcha.app.ui.components.LiveDot
import com.kharcha.app.ui.components.MonthSwitcher
import com.kharcha.app.ui.components.ScoreRing
import com.kharcha.app.ui.components.SectionCard
import com.kharcha.app.ui.components.SectionTitle
import com.kharcha.app.ui.components.SpendDonut
import com.kharcha.app.ui.components.TodayRing
import com.kharcha.app.ui.components.colour
import com.kharcha.app.ui.components.inr
import com.kharcha.app.ui.theme.Alert
import com.kharcha.app.ui.theme.Forest
import com.kharcha.app.ui.theme.ForestLight
import com.kharcha.app.ui.theme.Gold
import com.kharcha.app.ui.theme.Good
import com.kharcha.app.ui.theme.Warn
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: UserSettings,
    month: YearMonth,
    report: MonthReport?,
    accounts: List<LiveAccount>,
    goals: List<GoalEntity>,
    recent: List<TransactionEntity>,
    brief: AiTextState,
    syncing: Boolean,
    onRefresh: () -> Unit,
    onRefreshBrief: () -> Unit,
    onShiftMonth: (Long) -> Unit,
    onOpenTransaction: (TransactionEntity) -> Unit,
    onSeeInsights: () -> Unit,
    onSeeTransactions: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    onAsk: (String) -> Unit,
) {
    PullToRefreshBox(isRefreshing = syncing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(settings.name), style = MaterialTheme.typography.headlineSmall)
                        SyncLine(settings.lastSync, syncing)
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                }
            }
            item { HeroCard(report, accounts, onOpenPlan) }
            item { BriefCard(settings, brief, report, onRefreshBrief, onAsk, onOpenSettings) }
            item { MonthSwitcher(month, onShiftMonth) }
            if (report != null) {
                if (report.isCurrentMonth) item { TodayCard(report, onOpenPlan) }
                item { DisciplineCard(report) }
                item { WhereItWentCard(report, onSeeInsights) }
                if (report.daysElapsed > 0) item { DailyCard(report) }
                item {
                    SectionCard {
                        SectionTitle("What to fix") { TextButton(onClick = onSeeInsights) { Text("All insights") } }
                        report.insights.take(3).forEach { InsightRow(it) }
                    }
                }
            }
            if (goals.isNotEmpty()) item { GoalsSummary(goals, onOpenPlan) }
            item {
                SectionCard(Modifier.animateContentSize()) {
                    SectionTitle("Latest") { TextButton(onClick = onSeeTransactions) { Text("Expense list") } }
                    if (recent.isEmpty()) {
                        Text("Nothing yet this month. Pull down to check for new bank SMS.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    recent.take(6).forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TransactionRow(t, onClick = { onOpenTransaction(t) })
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun greeting(name: String): String {
    val h = LocalTime.now().hour
    val part = when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) part else "$part, ${name.split(' ').first()}"
}

@Composable
private fun SyncLine(lastSync: Long, syncing: Boolean) {
    val text = when {
        syncing -> "Checking bank SMS…"
        lastSync == 0L -> "Pull down to sync"
        else -> {
            val mins = ((System.currentTimeMillis() - lastSync) / 60000).toInt()
            when {
                mins < 1 -> "Synced just now"
                mins < 60 -> "Synced $mins min ago"
                mins < 60 * 24 -> "Synced ${mins / 60} h ago"
                else -> "Synced ${mins / 1440} d ago — pull to refresh"
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LiveDot(if (syncing) Warn else Good)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeroCard(report: MonthReport?, accounts: List<LiveAccount>, onOpenPlan: () -> Unit) {
    val live = accounts.sumOf { it.live }
    val pending = accounts.sumOf { it.pendingCount }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Forest, ForestLight)))
            .clickable(onClick = onOpenPlan).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LIVE BALANCE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.width(6.dp))
            if (accounts.isNotEmpty()) LiveDot(Gold)
        }
        if (accounts.isEmpty()) {
            Text("Add account", style = MaterialTheme.typography.displaySmall, color = Color.White)
        } else {
            AnimatedMoney(live, MaterialTheme.typography.displaySmall, Color.White)
        }
        Text(
            when {
                accounts.isEmpty() -> "Fills in from bank SMS, or tap to add one."
                pending > 0 -> "Bank-confirmed ${inr(accounts.sumOf { it.confirmed })} · $pending txn${if (pending == 1) "" else "s"} since, applied live"
                accounts.size == 1 -> "${accounts.first().account.bank} ••${accounts.first().account.last4 ?: ""} · bank-confirmed"
                else -> "Across ${accounts.size} accounts · bank-confirmed"
            },
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f),
        )
        if (accounts.size > 1) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accounts) { a ->
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(a.account.nickname.ifBlank { a.account.bank } + " ••" + (a.account.last4 ?: ""), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(inr(a.live), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            HeroStat("Spent", report?.spent ?: 0.0, Modifier.weight(1f))
            HeroStat("Income", report?.income ?: 0.0, Modifier.weight(1f))
            val saved = report?.saved ?: 0.0
            HeroStat(if (saved >= 0) "Saved" else "Overspent", kotlin.math.abs(saved), Modifier.weight(1f), if (saved >= 0) Gold else Color(0xFFFF8A80))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: Double, modifier: Modifier, color: Color = Color.White) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        AnimatedMoney(value, MaterialTheme.typography.titleMedium, color)
    }
}

@Composable
private fun BriefCard(
    settings: UserSettings,
    brief: AiTextState,
    report: MonthReport?,
    onRefresh: () -> Unit,
    onAsk: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val purple = Color(0xFF6B4EFF)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(purple.copy(alpha = 0.14f), Gold.copy(alpha = 0.18f))))
            .padding(16.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = purple, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("KHARCHA AI", style = MaterialTheme.typography.labelSmall, color = purple, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (settings.aiReady) {
                if (brief.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = purple)
                else IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Refresh, "Refresh brief", Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            !settings.aiReady -> {
                Text("Give Kharcha a brain", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add a Claude API key to get a daily money brief, ask anything about your spending, and have Claude fix messy transactions.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenSettings) { Text("Switch on AI") }
                return@Column
            }
            brief.text.isNotBlank() -> Text(brief.text, style = MaterialTheme.typography.bodyLarge)
            brief.loading -> Text("Reading your money…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            brief.error != null -> Text(brief.error, style = MaterialTheme.typography.bodySmall, color = Alert)
            else -> TextButton(onClick = onRefresh) { Text("Get today's brief") }
        }
        Spacer(Modifier.height(8.dp))
        val chips = buildList {
            add("Where am I overspending?")
            if (report != null && report.isCurrentMonth) add("Can I afford ₹2,000 this weekend?")
            add("How do I save ₹5,000 more?")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chips) { q -> AssistChip(onClick = { onAsk(q) }, label = { Text(q) }) }
        }
    }
}

@Composable
private fun TodayCard(report: MonthReport, onOpenPlan: () -> Unit) {
    SectionCard {
        if (report.budget <= 0) {
            Text("Set a monthly budget", style = MaterialTheme.typography.titleMedium)
            Text(
                "A budget turns this into a daily number you can actually follow.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPlan) { Text("Set budget") }
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TodayRing(report.todaySpent, report.safeToSpendToday)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("SAFE TO SPEND TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val left = report.safeToSpendToday - report.todaySpent
                AnimatedMoney(
                    left.coerceAtLeast(0.0), MaterialTheme.typography.headlineMedium,
                    if (left <= 0) Alert else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (left > 0) "left of ${inr(report.safeToSpendToday)} · spent ${inr(report.todaySpent)} today"
                    else "Over today's limit by ${inr(-left)}. Tomorrow's limit shrinks.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (report.streak >= 2) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Gold.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("🔥 ${report.streak}-day streak inside budget", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(10.dp))
        BudgetBar(report.spent, report.budget, Modifier.height(8.dp))
        val overshoot = report.projectedSpend - report.budget
        Spacer(Modifier.height(6.dp))
        Text(
            "${inr(report.spent)} of ${inr(report.budget)} · " +
                if (overshoot > 0) "heading for ${inr(report.projectedSpend)} (${inr(overshoot)} over)" else "on track for ${inr(report.projectedSpend)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (overshoot > 0) Alert else Good,
        )
    }
}

@Composable
private fun DisciplineCard(report: MonthReport) {
    val d = report.discipline
    val color = when {
        d.score >= 80 -> Good
        d.score >= 60 -> MaterialTheme.colorScheme.primary
        d.score >= 40 -> Warn
        else -> Alert
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(d.score, color = color, track = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("DISCIPLINE SCORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(d.label, style = MaterialTheme.typography.titleLarge, color = color)
                Spacer(Modifier.height(4.dp))
                ScoreFactor("Budget pace", d.budgetPoints, 35)
                ScoreFactor("Savings rate", d.savingsPoints, 30)
                ScoreFactor("Wants control", d.wantsPoints, 20)
                ScoreFactor("No-spend days (${report.noSpendDays})", d.noSpendPoints, 15)
            }
        }
    }
}

@Composable
private fun ScoreFactor(label: String, points: Int, max: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$points/$max", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WhereItWentCard(report: MonthReport, onSeeInsights: () -> Unit) {
    SectionCard {
        SectionTitle("Where it went") { TextButton(onClick = onSeeInsights) { Text("Details") } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpendDonut(report.categories, report.spent, size = 140.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.categories.filter { it.amount > 0 }.take(5).forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c.category.colour))
                        Spacer(Modifier.width(6.dp))
                        Text(c.category.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${(c.amount / report.spent * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (report.spent > 0) {
                    val wantsPct = (report.wants / report.spent * 100).roundToInt()
                    Text(
                        "Needs ${inr(report.needs)} · Wants ${inr(report.wants)} ($wantsPct%)",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyCard(report: MonthReport) {
    val pace = if (report.budget > 0) report.budget / report.daysInMonth else 0.0
    SectionCard {
        SectionTitle("Daily spending")
        DailyBars(report.dailySpend, pace, report.daysElapsed)
        if (pace > 0) {
            Spacer(Modifier.height(6.dp))
            val overDays = report.dailySpend.take(report.daysElapsed).count { it > pace }
            Text(
                "Dashed line = ${inr(pace)}/day budget pace. $overDays day${if (overDays == 1) "" else "s"} over it so far.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalsSummary(goals: List<GoalEntity>, onOpenPlan: () -> Unit) {
    SectionCard {
        SectionTitle("Savings goals") { TextButton(onClick = onOpenPlan) { Text("Manage") } }
        goals.take(3).forEach { g ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row {
                    Text("${g.emoji} ${g.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${inr(g.saved)} / ${inr(g.target)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                GoalBar(g.saved, g.target, Modifier.height(6.dp))
            }
        }
    }
}
