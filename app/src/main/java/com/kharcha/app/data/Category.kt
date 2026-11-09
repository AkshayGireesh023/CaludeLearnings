package com.kharcha.app.data

/** How a category counts toward the 50 / 30 / 20 rule. */
enum class Bucket { NEED, WANT, SAVING, TRANSFER, INCOME, OTHER }

enum class TxnType { DEBIT, CREDIT }

enum class TxnSource { SMS, NOTIFICATION, MANUAL }

enum class Category(val label: String, val emoji: String, val bucket: Bucket, val color: Long) {
    FOOD("Food & Dining", "🍔", Bucket.WANT, 0xFFFF7043),
    GROCERIES("Groceries", "🛒", Bucket.NEED, 0xFF66BB6A),
    SHOPPING("Shopping", "🛍️", Bucket.WANT, 0xFFAB47BC),
    TRANSPORT("Transport", "🚕", Bucket.NEED, 0xFF29B6F6),
    FUEL("Fuel", "⛽", Bucket.NEED, 0xFF8D6E63),
    BILLS("Bills & Recharges", "💡", Bucket.NEED, 0xFFFFCA28),
    RENT("Rent & Housing", "🏠", Bucket.NEED, 0xFF5C6BC0),
    HEALTH("Health", "💊", Bucket.NEED, 0xFFEF5350),
    EDUCATION("Education", "📚", Bucket.NEED, 0xFF26A69A),
    EMI("EMI & Loans", "🏦", Bucket.NEED, 0xFF78909C),
    ENTERTAINMENT("Entertainment", "🎬", Bucket.WANT, 0xFFEC407A),
    TRAVEL("Travel", "✈️", Bucket.WANT, 0xFF42A5F5),
    PERSONAL_CARE("Personal Care", "💇", Bucket.WANT, 0xFFF06292),
    TRANSFER("Sent to People", "👥", Bucket.TRANSFER, 0xFF9E9E9E),
    CASH("Cash Withdrawal", "💵", Bucket.OTHER, 0xFF8BC34A),
    SELF_TRANSFER("Own accounts / Card bill", "🔁", Bucket.OTHER, 0xFF90A4AE),
    INVESTMENT("Investments & Savings", "📈", Bucket.SAVING, 0xFF00897B),
    OTHERS("Others", "🧾", Bucket.OTHER, 0xFFBDBDBD),
    INCOME("Salary & Income", "💰", Bucket.INCOME, 0xFF43A047),
    REFUND("Refunds & Cashback", "↩️", Bucket.INCOME, 0xFF7CB342);

    val isSpending: Boolean get() = bucket != Bucket.INCOME && bucket != Bucket.SAVING

    companion object {
        val expenseCategories = entries.filter { it.bucket != Bucket.INCOME }
        val incomeCategories = entries.filter { it.bucket == Bucket.INCOME }
    }
}
