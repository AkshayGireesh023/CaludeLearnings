package com.kharcha.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    suspend fun between(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE refNo = :ref LIMIT 1")
    suspend fun byRef(ref: String): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE type = :type AND ABS(amount - :amount) < 0.01 " +
            "AND timestamp BETWEEN :from AND :to ORDER BY timestamp DESC",
    )
    suspend fun near(type: TxnType, amount: Double, from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: TransactionEntity): Long

    @Update
    suspend fun update(t: TransactionEntity)

    @Delete
    suspend fun delete(t: TransactionEntity)

    @Query("UPDATE transactions SET category = :category WHERE LOWER(merchant) = :merchantKey AND type = 'DEBIT'")
    suspend fun recategorizeMerchant(merchantKey: String, category: Category)

    @Query("DELETE FROM transactions")
    suspend fun clear()

    /** Removes SMS-derived rows you never touched, so a re-scan can rebuild them with the improved parser. */
    @Query("DELETE FROM transactions WHERE source != 'MANUAL' AND userEdited = 0")
    suspend fun clearUnedited()

    @Query("SELECT * FROM transactions WHERE timestamp > :after AND excluded = 0 ORDER BY timestamp")
    suspend fun after(after: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE userEdited = 0 AND aiChecked = 0 AND source != 'MANUAL' AND timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun needingAiReview(since: Long, limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<TransactionEntity?>
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY bank")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE `key` = :key")
    suspend fun get(key: String): AccountEntity?

    @Query("SELECT * FROM accounts")
    suspend fun all(): List<AccountEntity>

    @Upsert
    suspend fun upsert(a: AccountEntity)

    @Delete
    suspend fun delete(a: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun clear()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Upsert
    suspend fun upsert(b: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun delete(category: Category)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt")
    fun observeAll(): Flow<List<GoalEntity>>

    @Upsert
    suspend fun upsert(g: GoalEntity)

    @Delete
    suspend fun delete(g: GoalEntity)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key")
    suspend fun get(key: String): MerchantRuleEntity?

    @Upsert
    suspend fun upsert(r: MerchantRuleEntity)
}
