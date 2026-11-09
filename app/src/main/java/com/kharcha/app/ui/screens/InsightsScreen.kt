package com.kharcha.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.insights.CategorySpend
import com.kharcha.app.insights.MonthReport
import com.kharcha.app.ui.components.BudgetBar
import com.kharcha.app.ui.components.CategoryDot
import com.kharcha.app.ui.components.EmptyState
import com.kharcha.app.ui.components.InsightRow
import com.kharcha.app.ui.components.MonthSwitcher
import com.kharcha.app.ui.components.SectionCard
import com.kharcha.app.ui.components.SectionTitle
import com.kharcha.app.ui.components.TrendBars
import com.kharcha.app.ui.components.inr
import com.kharcha.app.ui.theme.Alert
import com.kharcha.app.ui.theme.Good
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(month: YearMonth, report: MonthReport?, onShiftMonth: (Long) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Insights", style = MaterialTheme.typography.headlineMedium)
            MonthSwitcher(month, onShiftMonth)
        }
        if (report == null) {
            item { EmptyState("Crunching numbers…", "") }
            return@LazyColumn
        }

        val savable = report.insights.sumOf { it.potentialSaving }
        if (savable >= 500) {
            item {
                SectionCard {
                    Text("YOU COULD SAVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${inr(savable)} a month", style = MaterialTheme.typography.headlineMedium, color = Good)
                    Text(
                        "That's ${inr(savable * 12)} a year, just by acting on the cards below.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Action plan")
                report.insights.forEach { InsightRow(it) }
            }
        }

        item {
            SectionCard {
                SectionTitle("6-month trend")
                TrendBars(report.monthlyTrend, report.budget)
                Spacer(Modifier.height(8.dp))
                val prev = report.lastMonthSpent
                if (prev > 0) {
                    val diff = report.spent - prev
                    Text(
                        if (diff > 0) "${inr(diff)} more than last month so far." else "${inr(-diff)} less than last month.",
                        style = MaterialTheme.typography.bodySmall, color = if (diff > 0) Alert else Good,
                    )
                }
            }
        }

        item { RuleCard(report) }

        item {
            SectionCard {
                SectionTitle("Category vs last month")
                report.categories.forEach { CategoryLine(it) }
            }
        }

        if (report.topMerchants.isNotEmpty()) {
            item {
                SectionCard {
                    SectionTitle("Top places your money goes")
                    report.topMerchants.forEachIndexed { i, m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(24.dp))
                            CategoryDot(m.category, 32)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${m.count} payment${if (m.count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(inr(m.amount), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (report.subscriptions.isNotEmpty()) {
            item {
                SectionCard {
                    SectionTitle("Recurring payments")
                    report.subscriptions.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(s.merchant, modifier = Modifier.weight(1f))
                            Text("${inr(s.monthlyAmount)}/mo", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        "${inr(report.subscriptions.sumOf { it.monthlyAmount } * 12)} a year. Cancel what you don't use.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun RuleCard(report: MonthReport) {
    val income = report.income
    SectionCard {
        SectionTitle("50 / 30 / 20 check")
        if (income <= 0) {
            Text(
                "Add your monthly income in Settings to see how your needs, wants and savings split.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val other = report.spent - report.needs - report.wants
        val saved = (income - report.spent).coerceAtLeast(0.0)
        RuleLine("Needs", report.needs + other, income, 0.5)
        RuleLine("Wants", report.wants, income, 0.3)
        RuleLine("Savings", saved, income, 0.2, higherIsBetter = true)
    }
}

@Composable
private fun RuleLine(label: String, amount: Double, income: Double, target: Double, higherIsBetter: Boolean = false) {
    val pct = amount / income
    val ok = if (higherIsBetter) pct >= target else pct <= target
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text(
                "${(pct * 100).roundToInt()}% · target ${if (higherIsBetter) "≥" else "≤"}${(target * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall, color = if (ok) Good else Alert, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        if (higherIsBetter) {
            com.kharcha.app.ui.components.GoalBar(amount, income * target, Modifier.height(6.dp))
        } else {
            BudgetBar(amount, income * target, Modifier.height(6.dp))
        }
    }
}

@Composable
private fun CategoryLine(c: CategorySpend) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(c.category, 32)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(c.category.label)
                val change = c.changePct
                val sub = buildString {
                    append("${c.count} txn")
                    if (change != null) append(" · ${if (change >= 0) "▲" else "▼"} ${kotlin.math.abs(change).roundToInt()}% vs last month")
                    else if (c.amount > 0) append(" · new this month")
                }
                Text(
                    sub, style = MaterialTheme.typography.bodySmall,
                    color = when {
                        change == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        change > 15 -> Alert
                        change < -10 -> Good
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(inr(c.amount), fontWeight = FontWeight.SemiBold)
                c.budget?.let { Text("of ${inr(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        c.budget?.let {
            Spacer(Modifier.height(6.dp))
            BudgetBar(c.amount, it, Modifier.height(6.dp))
        }
    }
}
