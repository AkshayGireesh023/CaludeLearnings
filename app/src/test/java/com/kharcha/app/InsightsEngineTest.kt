package com.kharcha.app

import com.kharcha.app.data.Category
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.TxnSource
import com.kharcha.app.data.TxnType
import com.kharcha.app.insights.InsightsEngine
import com.kharcha.app.insights.Money
import com.kharcha.app.insights.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class InsightsEngineTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private var n = 0

    private fun tx(date: LocalDate, amount: Double, cat: Category, merchant: String, type: TxnType = TxnType.DEBIT, hour: Int = 13) =
        TransactionEntity(
            amount = amount, type = type, merchant = merchant, category = cat, source = TxnSource.SMS,
            timestamp = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), dedupKey = "k${n++}",
        )

    @Test fun reportFlagsOverspendAndFindsSubscriptions() {
        val month = YearMonth.of(2026, 11)
        val today = month.atDay(15)
        val txns = buildList {
            // Previous months: Netflix every month, modest food.
            for (m in 1L..2L) {
                val pm = month.minusMonths(m)
                add(tx(pm.atDay(5), 649.0, Category.ENTERTAINMENT, "Netflix"))
                add(tx(pm.atDay(10), 2000.0, Category.FOOD, "Swiggy"))
                add(tx(pm.atDay(1), 60000.0, Category.INCOME, "Acme Corp", TxnType.CREDIT))
            }
            add(tx(month.atDay(1), 60000.0, Category.INCOME, "Acme Corp", TxnType.CREDIT))
            add(tx(month.atDay(2), 20000.0, Category.RENT, "Landlord"))
            add(tx(month.atDay(5), 649.0, Category.ENTERTAINMENT, "Netflix"))
            // Lots of food orders, some late at night.
            for (d in 1..14) add(tx(month.atDay(d), 450.0, Category.FOOD, "Swiggy", hour = if (d % 3 == 0) 23 else 20))
            add(tx(month.atDay(8), 9000.0, Category.SHOPPING, "Myntra"))
        }
        val r = InsightsEngine.analyze(month, txns, mapOf(Category.FOOD to 5000.0), 35000.0, 0.0, today, zone)

        assertEquals(60000.0, r.income, 0.01)
        assertEquals(20000.0 + 649 + 450 * 14 + 9000, r.spent, 0.01)
        assertEquals(Category.RENT, r.categories.first().category)
        assertTrue(r.subscriptions.any { it.merchant == "Netflix" })
        assertTrue(r.insights.any { it.severity == Severity.ALERT && it.category == Category.FOOD }) // food budget blown
        assertTrue(r.insights.any { it.title.contains("food orders") })
        assertTrue(r.projectedSpend > r.spent)
        assertTrue(r.discipline.score in 0..100)
        assertEquals(6, r.monthlyTrend.size)
        println("Score ${r.discipline.score} ${r.discipline.label}")
        r.insights.forEach { println("[${it.severity}] ${it.title} — ${it.detail}") }
    }

    @Test fun indianMoneyFormat() {
        assertEquals("₹1,23,456", Money.inr(123456.4))
        assertEquals("₹999", Money.inr(999.0))
        assertEquals("-₹10,00,000", Money.inr(-1_000_000.0))
        assertEquals("₹1,234.50", Money.inr(1234.5, decimals = true))
    }
}
