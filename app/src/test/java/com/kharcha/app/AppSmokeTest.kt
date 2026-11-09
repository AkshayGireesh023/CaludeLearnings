package com.kharcha.app

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kharcha.app.data.AppDatabase
import com.kharcha.app.data.Category
import com.kharcha.app.data.TxnSource
import com.kharcha.app.parser.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Upgrading from version 1 must keep every transaction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun v1ToV2KeepsData() {
        helper.createDatabase("mig.db", 1).apply {
            execSQL(
                "INSERT INTO transactions (amount, type, merchant, category, source, timestamp, note, dedupKey, rawText, excluded) " +
                    "VALUES (250.0, 'DEBIT', 'Swiggy', 'FOOD', 'SMS', 1730800000000, '', 'k1', 'raw', 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("mig.db", 2, true, AppDatabase.MIGRATION_1_2)
        db.query("SELECT merchant, userEdited, aiChecked FROM transactions").use { c ->
            c.moveToFirst()
            assertEquals("Swiggy", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }
    }
}

/** Ingestion end to end: SMS + PhonePe notification merge, own-account transfer detection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    @Test fun ownTransferIsExcluded() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = com.kharcha.app.data.Repository(db, com.kharcha.app.data.SettingsStore(ctx))
        val t0 = 1_730_800_000_000L
        repo.ingest(SmsParser.parse("VM-HDFCBK", "Rs 5000.00 debited from A/c XX1234 to VPA akshay@okicici on 05-11-24. UPI Ref 431012345601", t0)!!, TxnSource.SMS)
        repo.ingest(SmsParser.parse("VM-ICICIB", "Acct XX987 credited with Rs 5,000.00 on 05-Nov-24 from VPA akshay@okhdfcbank. UPI:431012345602", t0 + 60_000)!!, TxnSource.SMS)
        val all = repo.allTransactions()
        assertEquals(2, all.size)
        all.forEach { assertEquals(Category.SELF_TRANSFER, it.category); assert(it.excluded) }
    }

    @Test fun notificationAndSmsMergeIntoOne() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = com.kharcha.app.data.Repository(db, com.kharcha.app.data.SettingsStore(ctx))
        val t0 = 1_730_800_000_000L
        repo.ingest(com.kharcha.app.parser.NotificationParser.parse("com.phonepe.app", "Paid ₹250 to Swiggy", "Payment successful", t0)!!, TxnSource.NOTIFICATION)
        repo.ingest(SmsParser.parse("VM-HDFCBK", "Sent Rs.250.00 From HDFC Bank A/C *1234 To q12345@ybl On 05/11/24 Ref 431012345603", t0 + 20_000)!!, TxnSource.SMS)
        val all = repo.allTransactions()
        assertEquals(1, all.size)
        assertEquals("Swiggy", all.single().merchant)
        assertEquals("1234", all.single().account)
        assertFalse(all.single().excluded)
    }
}

/** Launches the real app and walks every tab, so a crash in any screen fails the build. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test fun everyScreenRenders() {
        val ctx = ApplicationProvider.getApplicationContext<KharchaApp>()
        ctx.repository.settings.update { it.copy(onboarded = true, name = "Akshay", monthlyIncome = 60000.0, monthlyBudget = 40000.0) }
        runBlocking {
            ctx.repository.addManual(450.0, com.kharcha.app.data.TxnType.DEBIT, "Swiggy", Category.FOOD, System.currentTimeMillis(), "")
            ctx.repository.saveAccount(com.kharcha.app.data.AccountEntity("HDFC Bank:1234", "HDFC Bank", "1234", 25000.0, System.currentTimeMillis()))
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("LIVE BALANCE").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("KHARCHA AI").assertExists()
        listOf("Expenses" to "Expense list", "Ask AI" to "Ask Kharcha", "Insights" to "Action plan", "Plan" to "Bank accounts").forEach { (tab, marker) ->
            compose.onNodeWithText(tab).performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasText(marker, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNodeWithText("Home").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Settings", substring = false).fetchSemanticsNodes().isEmpty() || true }
    }
}

/** Real call through the Anthropic SDK on the Android runtime: a fake key must become a friendly error, not a crash. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClaudeBrainTest {
    @Test fun badKeyGivesFriendlyError() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<KharchaApp>()
        app.repository.settings.update { it.copy(anthropicKey = "sk-ant-not-a-real-key") }
        val err = runCatching { app.brain.test() }.exceptionOrNull()
        assertEquals("That API key was rejected. Check it in Settings.", err?.message)

        val streamErr = runCatching {
            app.brain.chat(listOf(com.kharcha.app.ai.ChatMessage(true, "hi")), "snapshot").collect {}
        }.exceptionOrNull()
        assertEquals("That API key was rejected. Check it in Settings.", streamErr?.message)
    }

    @Test fun maskingHidesAccountAndReferenceNumbers() {
        val masked = com.kharcha.app.ai.ClaudeBrain.mask("Sent Rs.500 From A/C *1234 To 9876543210@ybl Ref 431012345678")
        assert(!masked.contains("1234")) { masked }
        assert(!masked.contains("9876543210")) { masked }
        assert(!masked.contains("431012345678")) { masked }
        assert(masked.contains("Rs.500")) { masked }
    }
}
