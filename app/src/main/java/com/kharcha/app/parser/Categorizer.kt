package com.kharcha.app.parser

import com.kharcha.app.data.Category
import com.kharcha.app.data.TxnType

/**
 * Keyword-based categoriser for Indian merchants. User corrections are stored as
 * merchant rules in the database and take priority over this (see Repository).
 */
object Categorizer {

    private val keywords: List<Pair<Category, List<String>>> = listOf(
        Category.INVESTMENT to listOf(
            "zerodha", "groww", "upstox", "angel one", "angelone", "kuvera", "coin by", "mutual fund", "sip ", "nps",
            "ppf", "recurring deposit", "fixed deposit", " rd ", " fd ", "smallcase", "indmoney", "paytm money", "etmoney",
            "icici pru", "hdfc mf", "sbi mf", "nippon", "axis mf", "dhan", "5paisa", "jar app", "gold", "digigold",
        ),
        Category.RENT to listOf("rent", "nobroker", "nestaway", "housing", "maintenance", "society", "pg ", "hostel"),
        Category.EMI to listOf(
            "emi", "loan", "bajaj fin", "bajajfinserv", "home credit", "tata capital", "hdb fin", "lendingkart", "kreditbee",
            "moneyview", "navi", "slice", "simpl", "lazypay", "credit card bill", "cc bill", "card payment", "cred",
        ),
        Category.FOOD to listOf(
            "swiggy", "zomato", "dominos", "domino", "pizza", "mcdonald", "kfc", "burger", "subway", "starbucks", "cafe",
            "coffee", "chai", "tea ", "restaurant", "hotel", "dhaba", "biryani", "bakery", "eatclub", "box8", "faasos",
            "behrouz", "haldiram", "food", "kitchen", "canteen", "eats", "chaayos", "third wave", "barbeque", "juice", "ice cream",
        ),
        Category.GROCERIES to listOf(
            "blinkit", "zepto", "instamart", "bigbasket", "big basket", "dmart", "d mart", "avenue supermarts", "jiomart",
            "reliance fresh", "reliance smart", "more retail", "spencer", "grofers", "dunzo", "milkbasket", "country delight",
            "supermarket", "kirana", "grocery", "provision", "vegetable", "fruits", "dairy", "milk", "nature basket", "swiggy instamart",
        ),
        Category.SHOPPING to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "snapdeal", "lenskart", "decathlon",
            "ikea", "croma", "reliance digital", "vijay sales", "shoppers stop", "lifestyle", "westside", "pantaloons",
            "zara", "h&m", "uniqlo", "max fashion", "trends", "firstcry", "boat", "mall", "store", "shop", "fashion", "footwear",
        ),
        Category.TRANSPORT to listOf(
            "uber", "ola", "rapido", "metro", "irctc", "railway", "redbus", "bus", "auto", "cab", "taxi", "fastag",
            "parking", "toll", "namma yatri", "blusmart", "yulu", "bounce",
        ),
        Category.FUEL to listOf(
            "petrol", "diesel", "fuel", "hpcl", "bpcl", "iocl", "indian oil", "bharat petroleum", "hindustan petroleum",
            "shell", "nayara", "reliance bp", "jio-bp", "cng",
        ),
        Category.BILLS to listOf(
            "electricity", "bescom", "msedcl", "tneb", "kseb", "bses", "tata power", "adani elec", "torrent power", "water bill",
            "airtel", "jio", "vodafone", "vi ", "bsnl", "act fibernet", "hathway", "broadband", "recharge", "dth", "tata play",
            "dish tv", "gas", "indane", "hp gas", "bharat gas", "mahanagar gas", "igl", "bill", "postpaid", "lic", "insurance",
            "policybazaar", "acko", "digit insurance",
        ),
        Category.ENTERTAINMENT to listOf(
            "netflix", "hotstar", "disney", "prime video", "amazon prime", "spotify", "youtube", "apple.com", "apple services",
            "google play", "bookmyshow", "pvr", "inox", "cinepolis", "zee5", "sonyliv", "jiocinema", "gaana", "steam",
            "playstation", "xbox", "dream11", "mpl", "games", "club", "pub", "bar ", "brewery", "district",
        ),
        Category.HEALTH to listOf(
            "pharmacy", "pharma", "medical", "medicos", "chemist", "apollo", "medplus", "1mg", "tata 1mg", "pharmeasy",
            "netmeds", "hospital", "clinic", "diagnostic", "lab", "practo", "cult", "cultfit", "gym", "fitness", "dental",
        ),
        Category.EDUCATION to listOf(
            "school", "college", "university", "tuition", "coaching", "byju", "unacademy", "udemy", "coursera", "upgrad",
            "vedantu", "physicswallah", "books", "stationery", "exam", "fees",
        ),
        Category.TRAVEL to listOf(
            "makemytrip", "goibibo", "yatra", "cleartrip", "ixigo", "easemytrip", "airbnb", "oyo", "booking.com", "agoda",
            "indigo", "air india", "vistara", "spicejet", "akasa", "airasia", "treebo", "fabhotels", "zostel", "resort",
        ),
        Category.PERSONAL_CARE to listOf("salon", "saloon", "spa", "urban company", "urbanclap", "parlour", "barber", "grooming", "beauty"),
    )

    private val matchers: List<Triple<Category, String, Regex?>> = keywords.flatMap { (cat, words) ->
        words.map { raw ->
            val w = raw.trim()
            // Short words must match whole words: "ola" must not hit "motorola", "rent" not "parent".
            Triple(cat, w, if (w.length <= 4) Regex("\\b${Regex.escape(w)}\\b") else null)
        }
    }

    /** Longest matching keyword wins, so "amazon prime" beats "amazon" and "swiggy instamart" beats "swiggy". */
    fun keywordCategory(text: String): Category? {
        val t = text.lowercase()
        var best: Category? = null
        var bestLen = 0
        for ((cat, word, regex) in matchers) {
            if (word.length <= bestLen) continue
            val hit = regex?.containsMatchIn(t) ?: t.contains(word)
            if (hit) {
                best = cat
                bestLen = word.length
            }
        }
        return best
    }

    fun categorize(merchant: String?, body: String, type: TxnType, isPerson: Boolean): Category {
        val b = body.lowercase()
        if (type == TxnType.CREDIT) {
            return when {
                listOf("refund", "reversal", "reversed", "cashback", "cash back").any { b.contains(it) } -> Category.REFUND
                listOf("salary", "sal ", "payroll", "neft", "imps", "interest", "dividend").any { b.contains(it) } -> Category.INCOME
                else -> Category.INCOME
            }
        }
        if (listOf("atm", "cash withdrawal", "withdrawn at", "cash wdl").any { b.contains(it) }) return Category.CASH
        merchant?.let { m -> keywordCategory(m)?.let { return it } }
        if (isPerson) return Category.TRANSFER
        if (listOf("emi", "loan", "nach", "ecs", "mandate").any { b.contains(it) }) return Category.EMI
        if (listOf("sip", "mutual fund").any { b.contains(it) }) return Category.INVESTMENT
        return Category.OTHERS
    }
}
