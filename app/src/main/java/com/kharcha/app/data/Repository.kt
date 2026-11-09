package com.kharcha.app.data

import com.kharcha.app.parser.Categorizer
import com.kharcha.app.parser.ParsedTxn
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

class Repository(private val db: AppDatabase, val settings: SettingsStore) {

    private val txDao = db.transactions()
    private val zone get() = ZoneId.systemDefault()

    fun monthRange(m: YearMonth): Pair<Long, Long> =
        m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun observeMonths(from: YearMonth, to: YearMonth): Flow<List<TransactionEntity>> =
        txDao.observeBetween(monthRange(from).first, monthRange(to).second)

    val accounts = db.accounts().observeAll()
    val budgets = db.budgets().observeAll()
    val goals = db.goals().observeAll()
    val transactionCount = txDao.observeCount()

    /**
     * Stores a parsed transaction, merging it with the same payment if it already arrived
     * from another source (PhonePe notification + bank SMS for one payment).
     * Returns the stored transaction if it is new, null if it was a duplicate.
     */
    suspend fun ingest(p: ParsedTxn, source: TxnSource): TransactionEntity? {
        p.balance?.let { updateBalance(p, it) }

        p.refNo?.let { ref ->
            txDao.byRef(ref)?.let { existing ->
                mergeInto(existing, p, source)
                return null
            }
        }
        // Same amount & direction within 15 minutes from a different source = same payment.
        val window = 15 * 60 * 1000L
        txDao.near(p.type, p.amount, p.timestamp - window, p.timestamp + window)
            .firstOrNull { it.source != source && (it.refNo == null || p.refNo == null || it.refNo == p.refNo) }
            ?.let { existing ->
                mergeInto(existing, p, source)
                return null
            }

        val merchant = p.merchant ?: if (p.type == TxnType.DEBIT) "Payment" else "Money received"
        val category = resolveCategory(merchant, p)
        val entity = TransactionEntity(
            amount = p.amount,
            type = p.type,
            merchant = merchant,
            category = category,
            source = source,
            account = p.account,
            bank = p.bank,
            timestamp = p.timestamp,
            refNo = p.refNo,
            dedupKey = dedupKey(p, source),
            rawText = p.raw,
        )
        val id = txDao.insert(entity)
        return if (id > 0) entity.copy(id = id) else null
    }

    /** Live SMS and the history import see the same message with slightly different timestamps, so key on content. */
    private fun dedupKey(p: ParsedTxn, source: TxnSource): String = when {
        p.refNo != null -> "ref:${p.refNo}"
        source == TxnSource.SMS -> "sms:${p.type}:${p.amount}:${p.raw.trim().hashCode()}"
        else -> "${source.name}:${p.type}:${p.amount}:${p.timestamp / 60000}:${p.raw.hashCode()}"
    }

    private suspend fun mergeInto(existing: TransactionEntity, p: ParsedTxn, source: TxnSource) {
        // Notifications have cleaner merchant names; SMS have account & reference details.
        val betterMerchant = when {
            p.merchant == null -> existing.merchant
            source == TxnSource.NOTIFICATION -> p.merchant
            existing.merchant in genericNames -> p.merchant
            else -> existing.merchant
        }
        val merged = existing.copy(
            merchant = betterMerchant,
            account = existing.account ?: p.account,
            bank = if (source == TxnSource.SMS) p.bank ?: existing.bank else existing.bank,
            refNo = existing.refNo ?: p.refNo,
            category = if (existing.category == Category.OTHERS || existing.category == Category.TRANSFER) {
                resolveCategory(betterMerchant, p).takeIf { it != Category.OTHERS } ?: existing.category
            } else existing.category,
            rawText = if (existing.rawText.contains(p.raw)) existing.rawText else existing.rawText + "\n---\n" + p.raw,
        )
        if (merged != existing) txDao.update(merged)
    }

    private val genericNames = setOf("Payment", "Money received")

    private suspend fun resolveCategory(merchant: String, p: ParsedTxn): Category {
        db.merchantRules().get(merchant.lowercase())?.let { rule ->
            if (p.type == TxnType.DEBIT || rule.category.bucket == Bucket.INCOME) return rule.category
        }
        return Categorizer.categorize(merchant, p.raw, p.type, p.isPersonVpa)
    }

    private suspend fun updateBalance(p: ParsedTxn, balance: Double) {
        val bank = p.bank ?: "Bank"
        val key = "${bank}:${p.account ?: "main"}"
        val existing = db.accounts().get(key)
        if (existing == null || existing.updatedAt <= p.timestamp) {
            db.accounts().upsert(
                AccountEntity(key, bank, p.account, balance, p.timestamp, existing?.nickname ?: ""),
            )
        }
    }

    suspend fun addManual(
        amount: Double, type: TxnType, merchant: String, category: Category, timestamp: Long, note: String,
    ) {
        txDao.insert(
            TransactionEntity(
                amount = amount, type = type, merchant = merchant.ifBlank { category.label }, category = category,
                source = TxnSource.MANUAL, timestamp = timestamp, note = note,
                dedupKey = "manual:${System.nanoTime()}",
            ),
        )
    }

    /** Updates a transaction; a category change is remembered for that merchant and applied to its other payments. */
    suspend fun update(t: TransactionEntity, applyToMerchant: Boolean) {
        txDao.update(t)
        if (applyToMerchant && t.type == TxnType.DEBIT) {
            val key = t.merchant.lowercase()
            db.merchantRules().upsert(MerchantRuleEntity(key, t.category))
            txDao.recategorizeMerchant(key, t.category)
        }
    }

    suspend fun delete(t: TransactionEntity) = txDao.delete(t)

    suspend fun saveAccount(a: AccountEntity) = db.accounts().upsert(a)
    suspend fun deleteAccount(a: AccountEntity) = db.accounts().delete(a)

    suspend fun setBudget(category: Category, limit: Double) =
        if (limit > 0) db.budgets().upsert(BudgetEntity(category, limit)) else db.budgets().delete(category)

    suspend fun saveGoal(g: GoalEntity) = db.goals().upsert(g)
    suspend fun deleteGoal(g: GoalEntity) = db.goals().delete(g)

    suspend fun monthSpend(category: Category?, month: YearMonth): Double {
        val (s, e) = monthRange(month)
        return txDao.between(s, e).filter { it.isExpense && (category == null || it.category == category) }.sumOf { it.amount }
    }

    suspend fun clearTransactions() {
        txDao.clear()
        db.accounts().clear()
    }

    suspend fun exportCsv(): String {
        val sb = StringBuilder("Date,Time,Type,Amount,Merchant,Category,Bank,Account,Source,Note,Excluded\n")
        val fmtDate = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val fmtTime = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        fun esc(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
        txDao.all().forEach { t ->
            val dt = java.time.Instant.ofEpochMilli(t.timestamp).atZone(zone)
            sb.append(fmtDate.format(dt)).append(',').append(fmtTime.format(dt)).append(',')
                .append(t.type.name).append(',').append(String.format(java.util.Locale.US, "%.2f", abs(t.amount))).append(',')
                .append(esc(t.merchant)).append(',').append(esc(t.category.label)).append(',')
                .append(esc(t.bank)).append(',').append(esc(t.account)).append(',')
                .append(t.source.name).append(',').append(esc(t.note)).append(',').append(t.excluded).append('\n')
        }
        return sb.toString()
    }
}
