package com.kharcha.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, AccountEntity::class, BudgetEntity::class, GoalEntity::class, MerchantRuleEntity::class],
    version = 2,
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
            Room.databaseBuilder(context, AppDatabase::class.java, "kharcha.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN aiChecked INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
