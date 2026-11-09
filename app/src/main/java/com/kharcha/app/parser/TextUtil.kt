package com.kharcha.app.parser

internal object TextUtil {
    private val amountNumber = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    val currencyAmount = Regex("(?:rs\\.?|inr|₹)\\s*$amountNumber", RegexOption.IGNORE_CASE)

    fun toAmount(s: String): Double? = s.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

    private val corporateNoise = Regex(
        "\\b(pvt|private|ltd|limited|llp|india|technologies|tech|services|solutions|payments?|retail|online|internet)\\b\\.?",
        RegexOption.IGNORE_CASE,
    )

    /** Turns "swiggy.stores@icici" / "AMAZON PAY INDIA PVT LTD" into something readable. */
    fun cleanMerchant(input: String?): Pair<String, Boolean>? {
        if (input.isNullOrBlank()) return null
        var s = input.trim().trim('.', ',', ';', ':', '-', '(', ')', '*')
        var person = false
        if (s.contains('@')) {
            val handle = s.substringBefore('@')
            val digits = handle.filter { it.isDigit() }
            if (digits.length >= 10) {
                return "UPI ••${digits.takeLast(4)}" to true
            }
            s = handle
            // Personal VPAs usually live on these handles.
            val host = input.substringAfter('@').lowercase()
            if (host in setOf("ybl", "ibl", "axl", "okaxis", "okhdfcbank", "okicici", "oksbi", "paytm", "upi", "apl", "yapl")) {
                person = !looksLikeBusiness(handle)
            }
        }
        s = s.replace(Regex("^(?:neft|imps|rtgs|upi|ach|nach|pos|ecom)[\\s/:-]+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[._]+"), " ")
            .replace(Regex("\\d{6,}"), " ")
            .replace(corporateNoise, " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '/')
        if (s.length < 2) return null
        val titled = s.split(' ').joinToString(" ") { w ->
            if (w.length <= 3 && w.all { it.isUpperCase() }) w
            else w.lowercase().replaceFirstChar { it.titlecase() }
        }
        return titled.take(32) to person
    }

    private fun looksLikeBusiness(handle: String): Boolean {
        val h = handle.lowercase()
        return Categorizer.keywordCategory(h) != null ||
            listOf("pay", "store", "shop", "mart", "merchant", "bharatpe", "q", "paytmqr", "gpay").any { h.startsWith(it) }
    }
}
