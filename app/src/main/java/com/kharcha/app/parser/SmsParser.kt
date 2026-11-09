package com.kharcha.app.parser

import com.kharcha.app.data.TxnType

/**
 * Parses Indian bank transaction alerts. Every PhonePe (or any UPI) payment produces one of
 * these from your bank, and they also carry the account balance, so SMS is the most complete
 * source. Formats differ per bank, so this works on keywords rather than one template per bank.
 */
object SmsParser {

    private val IC = RegexOption.IGNORE_CASE

    private val bankBySender = linkedMapOf(
        "HDFC" to "HDFC Bank", "SBI" to "SBI", "ICICI" to "ICICI Bank", "AXIS" to "Axis Bank", "KOTAK" to "Kotak Bank",
        "PNB" to "PNB", "BOB" to "Bank of Baroda", "BARODA" to "Bank of Baroda", "CANBNK" to "Canara Bank", "CANARA" to "Canara Bank",
        "UNION" to "Union Bank", "UBOI" to "Union Bank", "IDFC" to "IDFC First", "INDUS" to "IndusInd Bank", "YESB" to "Yes Bank",
        "FEDBNK" to "Federal Bank", "FEDERAL" to "Federal Bank", "PAYTM" to "Paytm Payments Bank", "AIRBNK" to "Airtel Payments Bank",
        "AUBANK" to "AU Bank", "IOB" to "Indian Overseas Bank", "INDBNK" to "Indian Bank", "CENTBK" to "Central Bank", "BOI" to "Bank of India",
        "RBL" to "RBL Bank", "SCBANK" to "Standard Chartered", "HSBC" to "HSBC", "CITI" to "Citibank", "KVB" to "Karur Vysya",
        "SIB" to "South Indian Bank", "IPPB" to "India Post Payments", "JUPITER" to "Jupiter", "FIBANK" to "Fi", "DBS" to "DBS",
        "EQUITAS" to "Equitas", "UJJIVAN" to "Ujjivan", "BANDHAN" to "Bandhan Bank", "IDBI" to "IDBI Bank",
    )

    private val hardReject = listOf(
        "one time password", "verification code", "is due", "due on", "due date", "will be debited", "to be debited",
        "requested money", "collect request", "has requested", "payment request", "mandate created", "autopay set",
        "e-mandate", "minimum amount due", "statement for", "bill generated", "unsuccessful", "could not be", "not processed",
    )
    private val failurePhrases = listOf("failed", "declined")
    private val promoPhrases = listOf("pre-approved", "preapproved", "apply now", "eligible for", "offer", "congratulations", "win ")
    private val otpRegex = Regex("\\botp\\b.{0,25}\\b\\d{4,8}\\b|\\b\\d{4,8}\\b.{0,30}\\botp\\b", IC)
    private val coreVerb = Regex("\\b(debited|credited|spent|withdrawn)\\b", IC)

    private fun shouldReject(text: String, lower: String): Boolean {
        if (hardReject.any { lower.contains(it) }) return true
        if (otpRegex.containsMatchIn(text)) return true
        val isReversal = lower.contains("revers") || lower.contains("refund")
        if (!isReversal && failurePhrases.any { lower.contains(it) }) return true
        // Banks append promos to real alerts, so only reject promos with no transaction verb.
        if (!coreVerb.containsMatchIn(text) && promoPhrases.any { lower.contains(it) }) return true
        return false
    }

    private val debitStrong = Regex("\\b(debited|debit(?!\\s*card)|spent|withdrawn|paid|sent|purchase|txn of|transferred to|dr\\.?)\\b", IC)
    private val creditStrong = Regex("\\b(credited|received|deposited|refund(?:ed)?|reversed|cr\\.?)\\b", IC)

    private val balPrefix = "(?:avl\\.?|avail(?:able)?\\.?|clear|closing|total|a/c|acct|net)?\\s*\\bbal(?:ance)?\\.?(?:\\s+(?:in your a/c|in a/c|in account))?"
    // "Avl Bal: Rs 1,234.50" / "Avl Bal as on 05-11 is INR 1,234"
    private val balanceRegex = Regex(
        "$balPrefix(?:\\s*(?:as on|on|as of)\\s+[0-9a-z/.:, -]{5,22}?)?[^0-9₹]{0,20}?(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        IC,
    )
    // "Avl Bal:12,345.00" — no currency, so demand a decimal amount to avoid reading a date.
    private val balanceRegexBare = Regex(
        "$balPrefix\\s*(?:is|:|-)?\\s*:?\\s*([0-9][0-9,]*\\.[0-9]{2})\\b",
        IC,
    )
    private val bareAmountAfterVerb = Regex("(?:debited|credited)\\s*(?:by|for|with|of)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\b", IC)

    private val accountRegex = Regex(
        "(?:a/c|a/c no\\.?|ac|acct|account|card)\\s*(?:no\\.?|number)?\\s*(?:ending\\s*(?:with|in)?)?\\s*[:\\s]*[xX*•.]+\\s*(\\d{3,6})",
        IC,
    )
    private val upiPathRef = Regex("UPI/(?:P2[AM]|CR|DR)/(\\d{9,16})", IC)
    private val accountRegexLoose = Regex("\\b[xX*]{1,}(\\d{4})\\b")

    private val refRegex = Regex(
        "(?:upi\\s*(?:ref(?:erence)?\\.?\\s*(?:no|number)?|txn\\s*id)?|ref(?:erence)?\\s*(?:no|number|id)?|refno|rrn|txn\\s*(?:id|no))[\\s.:#/-]*(\\d{9,16})",
        IC,
    )

    // Merchant patterns, tried in order.
    private val stop = "(?=\\s+(?:on|refno|ref|upi|avl|via|from|for|not|if|info|bal|using|with|dated|date)\\b|\\s*[,;(]|\\.\\s|\\.?$|\\s+-|\\s+\\d{2}[-/])"
    private val upiPathRegex = Regex("UPI/(?:P2[AM]|CR|DR)/\\d*/?([A-Za-z0-9 .&'_@-]{2,40}?)(?:/|\\s|$)", IC)
    private val infoRegex = Regex("info[:\\s-]+(?:upi[/-]|neft[/-]|imps[/-]|ach[/-]|pos[/-]?)?([A-Za-z0-9 .&'@_-]{2,40}?)(?:[/-]\\d|\\.|$)", IC)
    private val iciciStyle = Regex(";\\s*([A-Za-z0-9 .&'@_-]{2,40}?)\\s+credited", IC)
    private val toRegex = Regex("\\b(?:to|at|towards|trf to|paid to)\\s+(?:vpa\\s+|merchant\\s+)?([A-Za-z0-9 .&'@_-]{2,40}?)$stop", IC)
    private val fromRegex = Regex("\\b(?:from|by)\\s+(?:vpa\\s+)?([A-Za-z0-9 .&'@_-]{2,40}?)$stop", IC)

    private val cardSpend = Regex("spent (?:on|using|via)? ?(?:your )?(?:[a-z ]+ )?(?:credit )?card|card (?:no\\.? )?[xX*]+\\d{3,4}.{0,40}\\b(?:at|on)\\b", IC)
    private val cardBillPayment = Regex(
        "payment (?:of [^.]{0,30})?(?:received|credited)?.{0,30}(?:towards|to|on) (?:your )?(?:[a-z ]{0,20})?credit card|credit card (?:bill|payment|dues)|cc bill|card bill|payment received on your card|towards your card",
        IC,
    )
    private val selfTransferWords = Regex("\\b(self transfer|own account|to self|sweep|fd booked|fixed deposit booked|transfer to rd)\\b", IC)
    private val bankWords = listOf("a/c", "acct", "account", "bank", "upi", "card", "neft", "imps", "rtgs", "atm")

    private fun looksBankLike(sender: String, lower: String): Boolean =
        bankFromSender(sender) != null || bankWords.count { lower.contains(it) } >= 2

    private val ownAccountWords = Regex("\\b(a/c|ac|acct|account|card|bank|your|you|xx\\d+)\\b", IC)

    fun isLikelyBankSender(sender: String): Boolean {
        val s = sender.uppercase()
        if (s.filter { it.isDigit() }.length >= 10) return false // personal number
        return s.any { it.isLetter() }
    }

    fun parse(sender: String, body: String, timestamp: Long): ParsedTxn? {
        if (!isLikelyBankSender(sender)) return null
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val lower = text.lowercase()
        if (shouldReject(text, lower)) return null

        val type = detectType(text) ?: return null

        // Balance first, so its number is not mistaken for the transaction amount.
        val balanceMatch = balanceRegex.find(text) ?: balanceRegexBare.find(text)
        val balance = balanceMatch?.groupValues?.get(1)?.let(TextUtil::toAmount)

        val amount = TextUtil.currencyAmount.findAll(text)
            .firstOrNull { m -> balanceMatch == null || m.range.first !in balanceMatch.range }
            ?.groupValues?.get(1)?.let(TextUtil::toAmount)
            ?: bareAmountAfterVerb.find(text)?.groupValues?.get(1)?.let(TextUtil::toAmount)
            ?: return null

        // A credit card spend ("spent on ... Card x1234", "Avl Lmt") has no bank balance.
        val isCard = cardSpend.containsMatchIn(text) || lower.contains("avl lmt") || lower.contains("available limit") ||
            (lower.contains("credit card") && type == TxnType.DEBIT && !cardBillPayment.containsMatchIn(lower))

        val account = (accountRegex.find(text) ?: accountRegexLoose.find(text))?.groupValues?.get(1)?.takeLast(4)
        val bank = bankFromSender(sender) ?: bankFromBody(lower)
        val ref = (refRegex.find(text) ?: upiPathRef.find(text))?.groupValues?.get(1)

        // Merchant / telecom / shopping SMS ("Your order of Rs 499 is paid") duplicate the bank's own
        // alert, so only accept known bank senders or messages that name an account, card or UPI reference.
        if (bank == null && account == null && ref == null) return null
        if (!looksBankLike(sender, lower) && account == null) return null

        // Paying your own credit card bill or moving money between your own accounts is not spending.
        val selfTransfer = cardBillPayment.containsMatchIn(lower) || selfTransferWords.containsMatchIn(lower)

        val rawMerchant = extractMerchant(text, type)
        val cleaned = TextUtil.cleanMerchant(rawMerchant)

        return ParsedTxn(
            amount = amount,
            type = type,
            merchant = cleaned?.first,
            account = account,
            bank = bank,
            balance = if (isCard) null else balance,
            refNo = ref,
            isCard = isCard,
            isPersonVpa = cleaned?.second ?: false,
            selfTransfer = selfTransfer,
            timestamp = timestamp,
            raw = body,
        )
    }

    private fun detectType(text: String): TxnType? {
        val d = debitStrong.find(text)?.range?.first
        val c = creditStrong.find(text)?.range?.first
        return when {
            d == null && c == null -> null
            d == null -> TxnType.CREDIT
            c == null -> TxnType.DEBIT
            d < c -> TxnType.DEBIT
            else -> TxnType.CREDIT
        }
    }

    private fun extractMerchant(text: String, type: TxnType): String? {
        fun ok(s: String?) = s?.trim()?.takeIf { it.length >= 2 && !ownAccountWords.containsMatchIn(it) && it.any(Char::isLetter) }

        ok(upiPathRegex.find(text)?.groupValues?.get(1))?.let { return it }
        if (type == TxnType.DEBIT) {
            ok(iciciStyle.find(text)?.groupValues?.get(1))?.let { return it }
            toRegex.findAll(text).forEach { m -> ok(m.groupValues[1])?.let { return it } }
        } else {
            fromRegex.findAll(text).forEach { m -> ok(m.groupValues[1])?.let { return it } }
        }
        ok(infoRegex.find(text)?.groupValues?.get(1))?.let { return it }
        return null
    }

    private fun bankFromSender(sender: String): String? {
        // Senders look like "VM-HDFCBK", "AD-SBIUPI-S", "JD-ICICIT".
        val core = sender.uppercase().split('-').firstOrNull { it.length >= 4 && it.length <= 8 && it != sender.uppercase().take(2) }
            ?: sender.uppercase()
        return bankBySender.entries.firstOrNull { core.contains(it.key) }?.value
    }

    private fun bankFromBody(lower: String): String? =
        bankBySender.values.distinct().firstOrNull { lower.contains(it.lowercase()) }
}
