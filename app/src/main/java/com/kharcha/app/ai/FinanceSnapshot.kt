package com.kharcha.app.ai

import com.kharcha.app.data.GoalEntity
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.TxnType
import com.kharcha.app.data.UserSettings
import com.kharcha.app.insights.LiveAccount
import com.kharcha.app.insights.Money
import com.kharcha.app.insights.MonthReport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Renders the user's finances as compact text for Claude: profile, this month's report, balances,
 * goals, six-month trend and recent transactions. No raw SMS and no account numbers.
 */
object FinanceSnapshot {
    private val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm")

    fun build(
        settings: UserSettings,
        report: MonthReport?,
        accounts: List<LiveAccount>,
        goals: List<GoalEntity>,
        transactions: List<TransactionEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(),
    ): String = buildString {
        fun inr(v: Double) = Money.inr(v)
        appendLine("Today: ${today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))}")
        appendLine("Name: ${settings.name.ifBlank { "not set" }}")
        appendLine("Monthly take-home income (user-entered): ${if (settings.monthlyIncome > 0) inr(settings.monthlyIncome) else "not set"}")
        appendLine("Monthly spending budget: ${if (settings.monthlyBudget > 0) inr(settings.monthlyBudget) else "not set"}")
        appendLine("Savings target: ${settings.savingsTargetPct}% of income")
        appendLine()

        appendLine("## Bank accounts")
        if (accounts.isEmpty()) appendLine("None detected yet.")
        accounts.forEach { a ->
            val name = a.account.nickname.ifBlank { a.account.bank }
            appendLine(
                "- $name (••${a.account.last4 ?: "?"}): live balance ${inr(a.live)}; last bank-confirmed ${inr(a.confirmed)} on " +
                    Instant.ofEpochMilli(a.account.updatedAt).atZone(zone).format(dayFmt) +
                    if (a.pendingCount > 0) " (${a.pendingCount} transactions since)" else "",
            )
        }
        if (accounts.size > 1) appendLine("Total live balance: ${inr(accounts.sumOf { it.live })}")
        appendLine()

        report?.let { r ->
            appendLine("## ${r.month} so far (day ${r.daysElapsed} of ${r.daysInMonth})")
            appendLine("Spent: ${inr(r.spent)} | Income received: ${inr(r.income)} | Invested: ${inr(r.invested)} | Saved: ${inr(r.saved)}")
            r.savingsRate?.let { appendLine("Savings rate: ${(it * 100).roundToInt()}%") }
            if (r.budget > 0) appendLine("Budget ${inr(r.budget)} | projected month-end ${inr(r.projectedSpend)} | safe to spend today ${inr(r.safeToSpendToday)}")
            appendLine("Spent today: ${inr(r.todaySpent)} | within-pace streak: ${r.streak} days | no-spend days: ${r.noSpendDays}")
            appendLine("Needs ${inr(r.needs)} | Wants ${inr(r.wants)}")
            appendLine("Discipline score ${r.discipline.score}/100 (${r.discipline.label})")
            appendLine("Last month total spend: ${inr(r.lastMonthSpent)}")
            appendLine("Categories (this month | last month | budget):")
            r.categories.filter { it.amount > 0 || it.budget != null }.forEach { c ->
                appendLine("- ${c.category.label}: ${inr(c.amount)} (${c.count} txns) | ${inr(c.lastMonth)} | ${c.budget?.let { inr(it) } ?: "-"}")
            }
            appendLine("Top merchants: " + r.topMerchants.joinToString { "${it.merchant} ${inr(it.amount)} x${it.count}" })
            if (r.subscriptions.isNotEmpty()) {
                appendLine("Recurring payments: " + r.subscriptions.joinToString { "${it.merchant} ~${inr(it.monthlyAmount)}/mo" })
            }
            appendLine("6-month spend trend: " + r.monthlyTrend.joinToString { "${it.first}: ${inr(it.second)}" })
            appendLine("App's rule-based flags: " + r.insights.joinToString(" / ") { it.title })
            appendLine()
        }

        if (goals.isNotEmpty()) {
            appendLine("## Savings goals")
            goals.forEach { g ->
                val by = g.deadline?.let { " by " + Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: ""
                appendLine("- ${g.name}: ${inr(g.saved)} of ${inr(g.target)}$by")
            }
            appendLine()
        }

        appendLine("## Transactions, newest first (date | type | amount | merchant | category | flags | note)")
        transactions.sortedByDescending { it.timestamp }.take(500).forEach { t ->
            val flags = buildList {
                if (t.excluded) add("excluded")
                if (t.source.name == "MANUAL") add("manual")
            }.joinToString(",")
            append(Instant.ofEpochMilli(t.timestamp).atZone(zone).format(dayFmt)).append(" | ")
            append(if (t.type == TxnType.DEBIT) "OUT" else "IN").append(" | ")
            append(inr(t.amount)).append(" | ").append(t.merchant).append(" | ").append(t.category.label)
            append(" | ").append(flags).append(" | ").append(t.note)
            appendLine()
        }
    }
}
