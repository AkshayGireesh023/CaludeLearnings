package com.kharcha.app.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.data.AccountEntity
import com.kharcha.app.data.GoalEntity
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.UserSettings
import com.kharcha.app.insights.MonthReport
import com.kharcha.app.ui.components.BudgetBar
import com.kharcha.app.ui.components.DailyBars
import com.kharcha.app.ui.components.GoalBar
import com.kharcha.app.ui.components.InsightRow
import com.kharcha.app.ui.components.MonthSwitcher
import com.kharcha.app.ui.components.ScoreRing
import com.kharcha.app.ui.components.SectionCard
import com.kharcha.app.ui.components.SectionTitle
import com.kharcha.app.ui.components.SpendDonut
import com.kharcha.app.ui.components.colour
import com.kharcha.app.ui.components.inr
import com.kharcha.app.ui.theme.Alert
import com.kharcha.app.ui.theme.Forest
import com.kharcha.app.ui.theme.Gold
import com.kharcha.app.ui.theme.Good
import com.kharcha.app.ui.theme.Warn
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    settings: UserSettings,
    month: YearMonth,
    report: MonthReport?,
    accounts: List<AccountEntity>,
    goals: List<GoalEntity>,
    recent: List<TransactionEntity>,
    onShiftMonth: (Long) -> Unit,
    onOpenTransaction: (TransactionEntity) -> Unit,
    onSeeInsights: () -> Unit,
    onSeeTransactions: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(greeting(settings.name), style = MaterialTheme.typography.headlineMedium)
                MonthSwitcher(month, onShiftMonth, Modifier.padding(start = 0.dp))
            }
        }
        item { HeroCard(report, accounts, onOpenPlan) }
        if (report != null) {
            if (report.isCurrentMonth) item { SafeToSpendCard(report, onOpenPlan) }
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
            SectionCard {
                SectionTitle("Recent") { TextButton(onClick = onSeeTransactions) { Text("Expense list") } }
                if (recent.isEmpty()) {
                    Text("Nothing yet this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                recent.take(5).forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TransactionRow(t, onClick = { onOpenTransaction(t) })
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
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
private fun HeroCard(report: MonthReport?, accounts: List<AccountEntity>, onOpenPlan: () -> Unit) {
    val balance = accounts.sumOf { it.balance }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Forest).clickable(onClick = onOpenPlan).padding(20.dp),
    ) {
        Text("BANK BALANCE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        Text(
            if (accounts.isEmpty()) "Add account" else inr(balance),
            style = MaterialTheme.typography.displaySmall, color = Color.White,
        )
        Text(
            when (accounts.size) {
                0 -> "Balances fill in automatically from bank SMS, or add one in Plan."
                1 -> "${accounts.first().bank} ••${accounts.first().last4 ?: ""}"
                else -> "Across ${accounts.size} accounts"
            },
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            HeroStat("Spent", inr(report?.spent ?: 0.0), Modifier.weight(1f))
            HeroStat("Income", inr(report?.income ?: 0.0), Modifier.weight(1f))
            val saved = report?.saved ?: 0.0
            HeroStat(if (saved >= 0) "Saved" else "Overspent", inr(kotlin.math.abs(saved)), Modifier.weight(1f), if (saved >= 0) Gold else Color(0xFFFF8A80))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier, color: Color = Color.White) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SafeToSpendCard(report: MonthReport, onOpenPlan: () -> Unit) {
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
            Column(Modifier.weight(1f)) {
                Text("SAFE TO SPEND TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    inr(report.safeToSpendToday), style = MaterialTheme.typography.headlineMedium,
                    color = if (report.safeToSpendToday <= 0) Alert else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${report.daysInMonth - report.daysElapsed + 1} days left · ${inr((report.budget - report.spent).coerceAtLeast(0.0))} of ${inr(report.budget)} remaining",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        BudgetBar(report.spent, report.budget, Modifier.height(8.dp))
        val overshoot = report.projectedSpend - report.budget
        Spacer(Modifier.height(6.dp))
        Text(
            if (overshoot > 0) "Projected ${inr(report.projectedSpend)} by month end — ${inr(overshoot)} over."
            else "Projected ${inr(report.projectedSpend)} by month end. You're on track.",
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
