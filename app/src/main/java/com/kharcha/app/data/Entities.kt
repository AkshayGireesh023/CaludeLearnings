package com.kharcha.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["dedupKey"], unique = true), Index("timestamp"), Index("refNo")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TxnType,
    val merchant: String,
    val category: Category,
    val source: TxnSource,
    val account: String? = null,
    val bank: String? = null,
    val timestamp: Long,
    val note: String = "",
    val refNo: String? = null,
    val dedupKey: String,
    val rawText: String = "",
    /** Excluded transactions (e.g. moving money between your own accounts) are ignored in totals. */
    val excluded: Boolean = false,
)

val TransactionEntity.isExpense get() = type == TxnType.DEBIT && !excluded && category.isSpending
val TransactionEntity.isIncome get() = type == TxnType.CREDIT && !excluded
val TransactionEntity.isInvestment get() = type == TxnType.DEBIT && !excluded && category == Category.INVESTMENT

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val key: String,
    val bank: String,
    val last4: String?,
    val balance: Double,
    val updatedAt: Long,
    val nickname: String = "",
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: Category,
    val monthlyLimit: Double,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🎯",
    val target: Double,
    val saved: Double = 0.0,
    val deadline: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Remembers how you re-categorised a merchant, so the next payment there is filed the same way. */
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val category: Category,
)
