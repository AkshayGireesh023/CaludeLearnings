package com.kharcha.app.insights

import com.kharcha.app.data.Bucket
import com.kharcha.app.data.Category
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.TxnType
import com.kharcha.app.data.isExpense
import com.kharcha.app.data.isIncome
import com.kharcha.app.data.isInvestment
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Severity { GOOD, TIP, WARN, ALERT }

data class Insight(
    val severity: Severity,
    val title: String,
    val detail: String,
    /** Rough monthly amount you could save by acting on this. */
    val potentialSaving: Double = 0.0,
    val category: Category? = null,
)

data class CategorySpend(
    val category: Category,
    val amount: Double,
    val count: Int,
    val lastMonth: Double,
    val budget: Double?,
) {
    val changePct: Double? get() = if (lastMonth > 0) (amount - lastMonth) / lastMonth * 100 else null
    val budgetUsedPct: Double? get() = budget?.takeIf { it > 0 }?.let { amount / it * 100 }
}

data class MerchantSpend(val merchant: String, val amount: Double, val count: Int, val category: Category)

data class Subscription(val merchant: String, val monthlyAmount: Double, val months: Int)

data class DisciplineScore(
    val score: Int,
    val label: String,
    val budgetPoints: Int,
    val savingsPoints: Int,
    val wantsPoints: Int,
    val noSpendPoints: Int,
)

data class MonthReport(
    val month: YearMonth,
    val income: Double,
    val spent: Double,
    val invested: Double,
    val lastMonthSpent: Double,
    val budget: Double,
    val projectedSpend: Double,
    val safeToSpendToday: Double,
    val daysElapsed: Int,
    val daysInMonth: Int,
    val noSpendDays: Int,
    val savingsRate: Double?,
    val needs: Double,
    val wants: Double,
    val categories: List<CategorySpend>,
    val topMerchants: List<MerchantSpend>,
    val dailySpend: DoubleArray,
    val monthlyTrend: List<Pair<YearMonth, Double>>,
    val subscriptions: List<Subscription>,
    val insights: List<Insight>,
    val discipline: DisciplineScore,
    val todaySpent: Double = 0.0,
    /** Consecutive days (ending today) spent within the daily budget pace. */
    val streak: Int = 0,
) {
    val saved: Double get() = income - spent
    val isCurrentMonth: Boolean get() = daysElapsed < daysInMonth
}

object InsightsEngine {

    fun analyze(
        month: YearMonth,
        transactions: List<TransactionEntity>,
        categoryBudgets: Map<Category, Double>,
        monthlyBudget: Double,
        expectedIncome: Double,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): MonthReport {
        fun ym(t: TransactionEntity) = YearMonth.from(Instant.ofEpochMilli(t.timestamp).atZone(zone))
        fun date(t: TransactionEntity) = Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate()

        val byMonth = transactions.groupBy(::ym)
        val current = byMonth[month].orEmpty()
        val previous = byMonth[month.minusMonths(1)].orEmpty()

        val expenses = current.filter { it.isExpense }
        val prevExpenses = previous.filter { it.isExpense }
        val spent = expenses.sumOf { it.amount }
        val lastMonthSpent = prevExpenses.sumOf { it.amount }
        val actualIncome = current.filter { it.isIncome }.sumOf { it.amount }
        val income = max(actualIncome, if (month == YearMonth.from(today)) expectedIncome else 0.0)
            .takeIf { it > 0 } ?: actualIncome
        val invested = current.filter { it.isInvestment }.sumOf { it.amount }

        val daysInMonth = month.lengthOfMonth()
        val daysElapsed = when {
            month == YearMonth.from(today) -> today.dayOfMonth
            month.isAfter(YearMonth.from(today)) -> 0
            else -> daysInMonth
        }
        val budget = if (monthlyBudget > 0) monthlyBudget else categoryBudgets.values.sum()

        // Projection ignores one-off big-ticket items (rent, EMI) that will not repeat this month.
        val fixed = expenses.filter { it.category == Category.RENT || it.category == Category.EMI }.sumOf { it.amount }
        val variable = spent - fixed
        val projected = if (daysElapsed in 1 until daysInMonth) fixed + variable / daysElapsed * daysInMonth else spent
        val daysLeft = max(1, daysInMonth - daysElapsed + 1)
        val spentBeforeToday = expenses.filter { date(it) < today }.sumOf { it.amount }
        val safeToSpend = if (budget > 0) max(0.0, (budget - spentBeforeToday) / daysLeft) else 0.0

        val daily = DoubleArray(daysInMonth)
        expenses.forEach { daily[date(it).dayOfMonth - 1] += it.amount }
        val noSpendDays = (0 until daysElapsed).count { daily[it] == 0.0 }

        val prevByCat = prevExpenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        val categories = expenses.groupBy { it.category }.map { (cat, list) ->
            CategorySpend(cat, list.sumOf { it.amount }, list.size, prevByCat[cat] ?: 0.0, categoryBudgets[cat])
        }.plus(
            // Budgeted categories with no spend yet still show up.
            categoryBudgets.keys.filter { c -> expenses.none { it.category == c } }
                .map { CategorySpend(it, 0.0, 0, prevByCat[it] ?: 0.0, categoryBudgets[it]) },
        ).sortedByDescending { it.amount }

        val topMerchants = expenses.groupBy { it.merchant.lowercase() }.map { (_, list) ->
            MerchantSpend(list.first().merchant, list.sumOf { it.amount }, list.size, list.first().category)
        }.sortedByDescending { it.amount }.take(8)

        val needs = expenses.filter { it.category.bucket == Bucket.NEED }.sumOf { it.amount }
        val wants = expenses.filter { it.category.bucket == Bucket.WANT }.sumOf { it.amount }
        val savingsRate = if (income > 0) (income - spent) / income else null

        val trend = (5 downTo 0).map { month.minusMonths(it.toLong()) }
            .map { m -> m to byMonth[m].orEmpty().filter { it.isExpense }.sumOf { it.amount } }

        val subscriptions = detectSubscriptions(transactions, month, ::ym)

        val discipline = score(budget, projected, spent, savingsRate, wants, noSpendDays, daysElapsed)

        val insights = buildInsights(
            month, today, expenses, current, categories, budget, spent, projected, income, wants, needs,
            savingsRate, noSpendDays, daysElapsed, daysInMonth, subscriptions, lastMonthSpent, invested, zone,
        )

        val pace = if (budget > 0) budget / daysInMonth else 0.0
        val todaySpent = if (month == YearMonth.from(today) && daysElapsed > 0) daily[daysElapsed - 1] else 0.0
        var streak = 0
        if (pace > 0) {
            var i = daysElapsed - 1
            while (i >= 0 && daily[i] <= pace) { streak++; i-- }
        }

        return MonthReport(
            month = month, income = income, spent = spent, invested = invested, lastMonthSpent = lastMonthSpent,
            budget = budget, projectedSpend = projected, safeToSpendToday = safeToSpend, daysElapsed = daysElapsed,
            daysInMonth = daysInMonth, noSpendDays = noSpendDays, savingsRate = savingsRate, needs = needs, wants = wants,
            categories = categories, topMerchants = topMerchants, dailySpend = daily, monthlyTrend = trend,
            subscriptions = subscriptions, insights = insights, discipline = discipline,
            todaySpent = todaySpent, streak = streak,
        )
    }

    /** Same merchant, similar amount, in at least 2 of the last 3 months = probably a subscription. */
    private fun detectSubscriptions(
        all: List<TransactionEntity>,
        month: YearMonth,
        ym: (TransactionEntity) -> YearMonth,
    ): List<Subscription> {
        val window = setOf(month, month.minusMonths(1), month.minusMonths(2))
        return all.filter { it.isExpense && ym(it) in window && it.category != Category.TRANSFER && it.amount >= 49 }
            .groupBy { it.merchant.lowercase() }
            .mapNotNull { (_, list) ->
                val perMonth = list.groupBy(ym)
                if (perMonth.size < 2) return@mapNotNull null
                // One payment per month, amounts within 15% of each other.
                if (perMonth.values.any { it.size > 2 }) return@mapNotNull null
                val amounts = perMonth.values.map { m -> m.maxOf { it.amount } }
                val avg = amounts.average()
                if (amounts.any { kotlin.math.abs(it - avg) / avg > 0.15 }) return@mapNotNull null
                Subscription(list.first().merchant, avg, perMonth.size)
            }
            .sortedByDescending { it.monthlyAmount }
    }

    private fun score(
        budget: Double, projected: Double, spent: Double, savingsRate: Double?, wants: Double,
        noSpendDays: Int, daysElapsed: Int,
    ): DisciplineScore {
        val budgetPts = if (budget <= 0) 18 else {
            val ratio = projected / budget
            (35 * (1 - ((ratio - 0.9).coerceAtLeast(0.0) * 2.5)).coerceIn(0.0, 1.0)).roundToInt()
        }
        val savingsPts = when (savingsRate) {
            null -> 15
            else -> (30 * (savingsRate / 0.2).coerceIn(0.0, 1.0)).roundToInt()
        }
        val wantsShare = if (spent > 0) wants / spent else 0.0
        val wantsPts = (20 * (1 - ((wantsShare - 0.3) / 0.4).coerceIn(0.0, 1.0))).roundToInt()
        val noSpendPts = if (daysElapsed == 0) 8 else (15 * min(1.0, noSpendDays.toDouble() / daysElapsed / 0.2)).roundToInt()
        val total = budgetPts + savingsPts + wantsPts + noSpendPts
        val label = when {
            total >= 80 -> "Disciplined"
            total >= 60 -> "On track"
            total >= 40 -> "Slipping"
            else -> "Needs attention"
        }
        return DisciplineScore(total, label, budgetPts, savingsPts, wantsPts, noSpendPts)
    }

    private fun buildInsights(
        month: YearMonth,
        today: LocalDate,
        expenses: List<TransactionEntity>,
        current: List<TransactionEntity>,
        categories: List<CategorySpend>,
        budget: Double,
        spent: Double,
        projected: Double,
        income: Double,
        wants: Double,
        needs: Double,
        savingsRate: Double?,
        noSpendDays: Int,
        daysElapsed: Int,
        daysInMonth: Int,
        subscriptions: List<Subscription>,
        lastMonthSpent: Double,
        invested: Double,
        zone: ZoneId,
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        val isCurrent = month == YearMonth.from(today)
        if (expenses.isEmpty()) {
            out += Insight(Severity.TIP, "No spends tracked yet", "Import your SMS history in Settings, or wait for your next PhonePe payment.")
            return out
        }

        // 1. Overall pace against the budget.
        if (budget > 0) {
            if (spent > budget) {
                out += Insight(Severity.ALERT, "Budget crossed by ${inr(spent - budget)}",
                    "You've spent ${inr(spent)} against a ${inr(budget)} budget. Every rupee from here is borrowed from savings.")
            } else if (isCurrent && projected > budget * 1.05) {
                out += Insight(Severity.ALERT, "On pace to overshoot by ${inr(projected - budget)}",
                    "At this rate you'll end the month near ${inr(projected)}. Keep daily spends under ${inr((budget - spent) / max(1, daysInMonth - daysElapsed + 1))} to stay inside.",
                    potentialSaving = projected - budget)
            } else if (isCurrent && daysElapsed >= 7) {
                out += Insight(Severity.GOOD, "Pacing inside budget",
                    "Projected month-end spend is ${inr(projected)} of ${inr(budget)}. Keep it going.")
            }
        }

        // 2. Category budgets.
        categories.filter { it.budget != null && it.budget > 0 }.forEach { c ->
            val pct = c.budgetUsedPct ?: return@forEach
            when {
                pct >= 100 -> out += Insight(Severity.ALERT, "${c.category.label} budget blown",
                    "${inr(c.amount)} spent on a ${inr(c.budget!!)} limit (${pct.roundToInt()}%).", category = c.category)
                pct >= 80 && isCurrent -> out += Insight(Severity.WARN, "${c.category.label} at ${pct.roundToInt()}% of budget",
                    "Only ${inr(c.budget!! - c.amount)} left for ${daysInMonth - daysElapsed} days.", category = c.category)
            }
        }

        // 3. Categories that jumped versus last month (compare same point in the month when current).
        val scale = if (isCurrent && daysElapsed > 0) daysElapsed.toDouble() / daysInMonth else 1.0
        categories.filter { it.lastMonth > 0 && it.category.bucket != Bucket.TRANSFER }.forEach { c ->
            val comparable = c.lastMonth * scale
            if (comparable > 0 && c.amount > comparable * 1.3 && c.amount - comparable > 1000) {
                val pct = ((c.amount - comparable) / comparable * 100).roundToInt()
                out += Insight(Severity.WARN, "${c.category.emoji} ${c.category.label} up $pct%",
                    "${inr(c.amount)} so far vs ${inr(comparable)} at this point last month.",
                    potentialSaving = c.amount - comparable, category = c.category)
            }
        }

        // 4. Food delivery & frequent small spends.
        val food = expenses.filter { it.category == Category.FOOD }
        if (food.size >= 6) {
            val total = food.sumOf { it.amount }
            val perMonth = if (isCurrent && daysElapsed > 0) total / daysElapsed * daysInMonth else total
            out += Insight(Severity.TIP, "${food.size} food orders = ${inr(total)}",
                "Average ${inr(total / food.size)} each. Cooking just one in three of those saves about ${inr(perMonth / 3)} a month.",
                potentialSaving = perMonth / 3, category = Category.FOOD)
        }
        val small = expenses.filter { it.amount < 200 && it.category != Category.TRANSFER }
        if (small.size >= 15) {
            val total = small.sumOf { it.amount }
            out += Insight(Severity.TIP, "Small spends add up: ${inr(total)}",
                "${small.size} payments under ₹200 this month. They feel like nothing; together they're ${pctOf(total, spent)} of your spending.",
                potentialSaving = total * 0.3)
        }

        // 5. Subscriptions.
        if (subscriptions.isNotEmpty()) {
            val total = subscriptions.sumOf { it.monthlyAmount }
            val n = subscriptions.size
            out += Insight(Severity.TIP, "$n recurring payment${if (n > 1) "s" else ""}: ${inr(total)}/mo",
                subscriptions.take(4).joinToString { it.merchant } +
                    if (n > 1) ". Cancel one you don't use and that's ~${inr(total * 12 / n)} a year back."
                    else ". Still using it? That's ${inr(total * 12)} a year.",
                potentialSaving = subscriptions.minOf { it.monthlyAmount })
        }

        // 6. Late-night impulse spends.
        val lateNight = expenses.filter {
            val h = Instant.ofEpochMilli(it.timestamp).atZone(zone).hour
            (h >= 22 || h < 4) && it.category.bucket == Bucket.WANT
        }
        val lateTotal = lateNight.sumOf { it.amount }
        if (lateNight.size >= 3 && lateTotal > 1000) {
            out += Insight(Severity.TIP, "After-10pm spending: ${inr(lateTotal)}",
                "${lateNight.size} late-night purchases. Try a rule: anything after 10pm waits in the cart till morning.",
                potentialSaving = lateTotal * 0.5)
        }

        // 7. Weekend splurge.
        val (weekend, weekday) = expenses.filter { it.category.bucket == Bucket.WANT }.partition {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfWeek.value >= 6
        }
        val weekendDays = (1..max(daysElapsed, 1)).count { month.atDay(it).dayOfWeek.value >= 6 }.coerceAtLeast(1)
        val weekdayDays = (max(daysElapsed, 1) - weekendDays).coerceAtLeast(1)
        val weAvg = weekend.sumOf { it.amount } / weekendDays
        val wdAvg = weekday.sumOf { it.amount } / weekdayDays
        if (weekend.size >= 3 && weAvg > wdAvg * 2 && weAvg > 500) {
            out += Insight(Severity.TIP, "Weekends cost ${(weAvg / max(wdAvg, 1.0)).roundToInt()}x more",
                "You spend about ${inr(weAvg)} per weekend day on wants vs ${inr(wdAvg)} on weekdays. Plan one free weekend activity.",
                potentialSaving = (weAvg - wdAvg) * 4 * 0.3)
        }

        // 8. Savings rate & 50/30/20.
        if (savingsRate != null && income > 0) {
            when {
                savingsRate >= 0.3 -> out += Insight(Severity.GOOD, "Saving ${(savingsRate * 100).roundToInt()}% of income",
                    "Excellent. Move ${inr(income * savingsRate * 0.5)} into investments so it actually grows.")
                savingsRate >= 0.2 -> out += Insight(Severity.GOOD, "Saving ${(savingsRate * 100).roundToInt()}% of income",
                    "You're hitting the 20% mark. Automate it with a SIP on salary day.")
                savingsRate >= 0 -> out += Insight(Severity.WARN, "Saving only ${(savingsRate * 100).roundToInt()}% of income",
                    "Aim for 20%: that means spending ${inr(spent - income * 0.8)} less this month.",
                    potentialSaving = spent - income * 0.8)
                else -> out += Insight(Severity.ALERT, "Spending more than you earn",
                    "Out ${inr(-income * savingsRate)} beyond income this month. Pause all non-essential spends.",
                    potentialSaving = -income * savingsRate)
            }
            if (wants > income * 0.3) {
                out += Insight(Severity.WARN, "Wants are ${pctOf(wants, income)} of income",
                    "50/30/20 rule: keep wants (food out, shopping, entertainment) under 30% = ${inr(income * 0.3)}.",
                    potentialSaving = wants - income * 0.3)
            }
            if (needs > income * 0.5) {
                out += Insight(Severity.TIP, "Needs take ${pctOf(needs, income)} of income",
                    "Above the 50% guideline. Review rent, bills and EMIs for a cheaper plan or refinancing.")
            }
        }

        // 9. Biggest single spend.
        expenses.filter { it.category != Category.RENT && it.category != Category.EMI }
            .maxByOrNull { it.amount }?.takeIf { it.amount > spent * 0.2 && spent > 0 }?.let {
            out += Insight(Severity.TIP, "One spend = ${pctOf(it.amount, spent)} of the month",
                "${inr(it.amount)} at ${it.merchant}. Big tickets deserve a 48-hour think before you pay.")
        }

        // 10. People transfers.
        val transfers = expenses.filter { it.category == Category.TRANSFER }.sumOf { it.amount }
        if (transfers > spent * 0.25 && transfers > 3000) {
            out += Insight(Severity.TIP, "${inr(transfers)} sent to people",
                "That's ${pctOf(transfers, spent)} of spending with no category. Open them and tag what they were for.")
        }

        // 11. Wins.
        if (daysElapsed > 0 && noSpendDays >= max(3, daysElapsed / 5)) {
            out += Insight(Severity.GOOD, "$noSpendDays no-spend days", "Days without a single spend are the cheapest discipline habit. Keep the streak.")
        }
        if (invested > 0) {
            out += Insight(Severity.GOOD, "${inr(invested)} invested this month", "Paying your future self first. Nice.")
        }
        if (!isCurrent && lastMonthSpent > 0 && spent < lastMonthSpent * 0.9) {
            out += Insight(Severity.GOOD, "Spent ${pctOf(lastMonthSpent - spent, lastMonthSpent)} less than the month before", "")
        }

        return out.sortedWith(compareBy<Insight> { -it.severity.ordinal }.thenByDescending { it.potentialSaving })
    }

    private fun pctOf(part: Double, whole: Double) = if (whole <= 0) "0%" else "${(part / whole * 100).roundToInt()}%"

    fun inr(v: Double): String = Money.inr(v)
}

object Money {
    /** Indian grouping: 1,23,456. */
    fun inr(v: Double, decimals: Boolean = false): String {
        val neg = v < 0
        val abs = kotlin.math.abs(v)
        val whole = if (decimals) abs.toLong() else abs.roundToLong()
        val s = whole.toString()
        val grouped = if (s.length <= 3) s else {
            val last3 = s.takeLast(3)
            val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            "$rest,$last3"
        }
        val frac = if (decimals) "." + ((abs - whole) * 100).roundToInt().coerceIn(0, 99).toString().padStart(2, '0') else ""
        return (if (neg) "-₹" else "₹") + grouped + frac
    }

    private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
}
