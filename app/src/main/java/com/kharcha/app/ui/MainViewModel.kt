package com.kharcha.app.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.KharchaApp
import com.kharcha.app.data.AccountEntity
import com.kharcha.app.data.Category
import com.kharcha.app.data.GoalEntity
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.TxnType
import com.kharcha.app.data.UserSettings
import com.kharcha.app.ingest.ImportResult
import com.kharcha.app.ingest.SmsHistoryImporter
import com.kharcha.app.insights.InsightsEngine
import com.kharcha.app.insights.MonthReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class ImportState(val running: Boolean = false, val progress: Float = 0f, val result: ImportResult? = null, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as KharchaApp).repository

    val month = MutableStateFlow(YearMonth.now())
    val settings: StateFlow<UserSettings> = repo.settings.state
    val accounts = repo.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val goals = repo.goals.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets = repo.budgets.map { list -> list.associate { it.category to it.monthlyLimit } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val transactionCount = repo.transactionCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Selected month plus five before it: enough for trends, subscriptions and comparisons. */
    private val window = month.flatMapLatest { m -> repo.observeMonths(m.minusMonths(5), m) }

    val monthTransactions: StateFlow<List<TransactionEntity>> = combine(window, month) { all, m ->
        val (s, e) = repo.monthRange(m)
        all.filter { it.timestamp in s until e }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val report: StateFlow<MonthReport?> = combine(month, window, budgets, settings) { m, txns, b, s ->
        InsightsEngine.analyze(m, txns, b, s.monthlyBudget, s.monthlyIncome, LocalDate.now(), ZoneId.systemDefault())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val importState = MutableStateFlow(ImportState())

    fun shiftMonth(by: Long) {
        val next = month.value.plusMonths(by)
        if (!next.isAfter(YearMonth.now())) month.value = next
    }

    fun importSms(months: Long = 6) {
        if (importState.value.running) return
        importState.value = ImportState(running = true)
        viewModelScope.launch {
            try {
                val result = SmsHistoryImporter.import(getApplication(), repo, months) { done, total ->
                    importState.value = ImportState(running = true, progress = if (total == 0) 1f else done.toFloat() / total)
                }
                importState.value = ImportState(result = result)
            } catch (e: SecurityException) {
                importState.value = ImportState(error = "SMS permission is needed to import history.")
            } catch (e: Exception) {
                importState.value = ImportState(error = e.message ?: "Import failed")
            }
        }
    }

    fun updateSettings(block: (UserSettings) -> UserSettings) = repo.settings.update(block)

    fun saveTransaction(t: TransactionEntity, applyToMerchant: Boolean) = viewModelScope.launch { repo.update(t, applyToMerchant) }
    fun deleteTransaction(t: TransactionEntity) = viewModelScope.launch { repo.delete(t) }
    fun addManual(amount: Double, type: TxnType, merchant: String, category: Category, timestamp: Long, note: String) =
        viewModelScope.launch { repo.addManual(amount, type, merchant, category, timestamp, note) }

    fun setBudget(c: Category, limit: Double) = viewModelScope.launch { repo.setBudget(c, limit) }
    fun saveGoal(g: GoalEntity) = viewModelScope.launch { repo.saveGoal(g) }
    fun deleteGoal(g: GoalEntity) = viewModelScope.launch { repo.deleteGoal(g) }
    fun saveAccount(a: AccountEntity) = viewModelScope.launch { repo.saveAccount(a) }
    fun deleteAccount(a: AccountEntity) = viewModelScope.launch { repo.deleteAccount(a) }
    fun clearAll() = viewModelScope.launch { repo.clearTransactions() }

    /** Writes every transaction to a CSV and returns a share intent (open in Sheets / Excel). */
    suspend fun exportCsvIntent(): Intent = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "kharcha-expenses-${LocalDate.now()}.csv")
        file.writeText(repo.exportCsv())
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kharcha expense list")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
