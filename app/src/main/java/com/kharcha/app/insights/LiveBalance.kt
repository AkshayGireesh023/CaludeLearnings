package com.kharcha.app.insights

import com.kharcha.app.data.AccountEntity
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.TxnSource
import com.kharcha.app.data.TxnType
import com.kharcha.app.data.sameAccount

data class LiveAccount(
    val account: AccountEntity,
    /** Last balance your bank actually reported. */
    val confirmed: Double,
    /** Confirmed balance moved forward by every debit/credit on this account since then. */
    val live: Double,
    val pendingCount: Int,
    val lastActivity: Long,
)

/**
 * Many UPI alerts don't include the balance, so the last "Avl Bal" quickly goes stale.
 * This rolls the last confirmed balance forward with every later transaction on the same account.
 */
object LiveBalance {
    fun compute(accounts: List<AccountEntity>, transactions: List<TransactionEntity>): List<LiveAccount> =
        accounts.map { a ->
            val later = transactions.filter { t ->
                t.source != TxnSource.MANUAL && t.timestamp > a.updatedAt && t.account != null && a.last4 != null &&
                    sameAccount(t.account, a.last4) && (t.bank == null || t.bank == a.bank)
            }
            val delta = later.sumOf { if (it.type == TxnType.CREDIT) it.amount else -it.amount }
            LiveAccount(a, a.balance, a.balance + delta, later.size, maxOf(a.updatedAt, later.maxOfOrNull { it.timestamp } ?: 0))
        }
}
