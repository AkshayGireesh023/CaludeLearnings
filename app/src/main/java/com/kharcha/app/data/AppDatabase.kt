package com.kharcha.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class, AccountEntity::class, BudgetEntity::class, GoalEntity::class, MerchantRuleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun accounts(): AccountDao
    abstract fun budgets(): BudgetDao
    abstract fun goals(): GoalDao
    abstract fun merchantRules(): MerchantRuleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "kharcha.db").build()
    }
}
