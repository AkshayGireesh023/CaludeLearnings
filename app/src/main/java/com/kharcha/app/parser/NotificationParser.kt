package com.kharcha.app.parser

import com.kharcha.app.data.TxnType

/**
 * Parses payment notifications posted by PhonePe (and other UPI apps). These carry the
 * clean merchant name ("Paid ₹250 to Swiggy") that bank SMS often truncate, so they are
 * merged with the matching SMS when both arrive.
 */
object NotificationParser {

    val trackedPackages = mapOf(
        "com.phonepe.app" to "PhonePe",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.dreamplug.androidapp" to "CRED",
    )

    private val IC = RegexOption.IGNORE_CASE
    private val ignore = listOf(
        "request", "reminder", "offer", "cashback won", "you won", "scratch", "reward", "pending", "failed", "declined",
        "due", "bill is", "expir", "kyc", "verify", "otp", "unsuccessful", "try again", "coupon", "voucher",
    )
    private val creditWords = Regex("\\b(received|credited|got|added to)\\b", IC)
    private val debitWords = Regex("\\b(paid|sent|debited|transferred|spent)\\b", IC)
    private val toRegex = Regex("\\b(?:to|at)\\s+(.{2,40}?)(?:\\s+(?:on|via|using|from|successfully|is|was)\\b|[.,!|\\n]|$)", IC)
    private val fromRegex = Regex("\\bfrom\\s+(.{2,40}?)(?:\\s+(?:on|via|using|to|successfully|is|was)\\b|[.,!|\\n]|$)", IC)

    fun parse(packageName: String, title: String?, text: String?, timestamp: Long): ParsedTxn? {
        if (packageName !in trackedPackages) return null
        val full = listOfNotNull(title, text).joinToString(" | ").replace('\n', ' ')
        val lower = full.lowercase()
        if (ignore.any { lower.contains(it) }) return null

        val amount = TextUtil.currencyAmount.find(full)?.groupValues?.get(1)?.let(TextUtil::toAmount) ?: return null
        val c = creditWords.find(full)?.range?.first
        val d = debitWords.find(full)?.range?.first
        val type = when {
            c == null && d == null -> if (lower.contains("payment")) TxnType.DEBIT else return null
            c == null -> TxnType.DEBIT
            d == null -> TxnType.CREDIT
            c < d -> TxnType.CREDIT
            else -> TxnType.DEBIT
        }
        val rawMerchant = if (type == TxnType.DEBIT) toRegex.find(full)?.groupValues?.get(1)
        else fromRegex.find(full)?.groupValues?.get(1)
        val cleaned = TextUtil.cleanMerchant(rawMerchant?.replace(TextUtil.currencyAmount, "")?.trim())

        return ParsedTxn(
            amount = amount,
            type = type,
            merchant = cleaned?.first,
            bank = trackedPackages[packageName],
            isPersonVpa = cleaned?.second ?: false,
            timestamp = timestamp,
            raw = full,
        )
    }
}
