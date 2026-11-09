package com.kharcha.app.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.KharchaApp
import com.kharcha.app.ai.AiException
import com.kharcha.app.ai.ChatMessage
import com.kharcha.app.ai.FinanceSnapshot
import com.kharcha.app.data.AccountEntity
import com.kharcha.app.data.Category
import com.kharcha.app.data.GoalEntity
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.TxnType
import com.kharcha.app.data.UserSettings
import com.kharcha.app.ingest.ImportResult
import com.kharcha.app.ingest.SmsHistoryImporter
import com.kharcha.app.insights.InsightsEngine
import com.kharcha.app.insights.LiveAccount
import com.kharcha.app.insights.LiveBalance
import com.kharcha.app.insights.MonthReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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
import java.time.format.DateTimeFormatter

data class ImportState(val running: Boolean = false, val progress: Float = 0f, val result: ImportResult? = null, val error: String? = null)

data class AiTextState(val loading: Boolean = false, val text: String = "", val error: String? = null)

data class ChatState(val messages: List<ChatMessage> = emptyList(), val streaming: Boolean = false, val error: String? = null)

data class CleanupState(val running: Boolean = false, val reviewed: Int = 0, val fixed: Int = 0, val done: Boolean = false, val error: String? = null)

/** Raise when the SMS parser changes in a way that should re-read old messages. */
private const val PARSER_VERSION = 2

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val kApp = app as KharchaApp
    private val repo = kApp.repository
    private val brain = kApp.brain

    val month = MutableStateFlow(YearMonth.now())
    val settings: StateFlow<UserSettings> = repo.settings.state
    val accounts = repo.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val goals = repo.goals.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets = repo.budgets.map { list -> list.associate { it.category to it.monthlyLimit } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val transactionCount = repo.transactionCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Selected month plus five before it: enough for trends, subscriptions and comparisons. */
    private val window = month.flatMapLatest { m -> repo.observeMonths(m.minusMonths(5), m) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val monthTransactions: StateFlow<List<TransactionEntity>> = combine(window, month) { all, m ->
        val (s, e) = repo.monthRange(m)
        all.filter { it.timestamp in s until e }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val report: StateFlow<MonthReport?> = combine(month, window, budgets, settings) { m, txns, b, s ->
        InsightsEngine.analyze(m, txns, b, s.monthlyBudget, s.monthlyIncome, LocalDate.now(), ZoneId.systemDefault())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Each account's last confirmed balance rolled forward with every transaction since. */
    val liveAccounts: StateFlow<List<LiveAccount>> = repo.accounts.flatMapLatest { accs ->
        val since = accs.minOfOrNull { it.updatedAt } ?: System.currentTimeMillis()
        repo.observeSince(since).map { LiveBalance.compute(accs, it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val importState = MutableStateFlow(ImportState())
    val syncing = MutableStateFlow(false)
    val rebuilding = MutableStateFlow(false)

    private val _newTransaction = MutableSharedFlow<TransactionEntity>(extraBufferCapacity = 4)
    /** Fires when a payment lands while the app is open, so the UI can celebrate or warn. */
    val newTransaction: SharedFlow<TransactionEntity> = _newTransaction

    init {
        // Announce fresh payments (not history imports) as they arrive.
        viewModelScope.launch {
            var lastSeen = -1L
            repo.latestTransaction.filterNotNull().collect { t ->
                if (lastSeen >= 0 && t.id > lastSeen && System.currentTimeMillis() - t.timestamp < 30 * 60 * 1000 && !importState.value.running && !rebuilding.value) {
                    _newTransaction.tryEmit(t)
                }
                lastSeen = maxOf(lastSeen, t.id)
            }
        }
    }

    fun shiftMonth(by: Long) {
        val next = month.value.plusMonths(by)
        if (!next.isAfter(YearMonth.now())) month.value = next
    }

    /** Called when the app opens or comes back to the foreground, and on pull-to-refresh. */
    fun sync() {
        if (syncing.value || importState.value.running || rebuilding.value) return
        val ctx = getApplication<Application>()
        if (!SmsHistoryImporter.hasSmsPermission(ctx)) return
        if (settings.value.onboarded && settings.value.parserVersion < PARSER_VERSION && settings.value.lastSmsImport > 0) {
            rebuild(12, auto = true)
            return
        }
        syncing.value = true
        viewModelScope.launch {
            try {
                SmsHistoryImporter.catchUp(ctx, repo)
            } catch (_: Exception) {
            } finally {
                syncing.value = false
            }
            if (settings.value.aiReady && settings.value.briefDay != LocalDate.now().toString()) refreshBrief()
        }
    }

    fun importSms(months: Long = 6) {
        if (importState.value.running) return
        importState.value = ImportState(running = true)
        viewModelScope.launch {
            try {
                val result = SmsHistoryImporter.import(getApplication(), repo, months) { done, total ->
                    importState.value = ImportState(running = true, progress = if (total == 0) 1f else done.toFloat() / total)
                }
                repo.settings.update { it.copy(parserVersion = PARSER_VERSION) }
                importState.value = ImportState(result = result)
            } catch (e: SecurityException) {
                importState.value = ImportState(error = "SMS permission is needed to import history.")
            } catch (e: Exception) {
                importState.value = ImportState(error = e.message ?: "Import failed")
            }
        }
    }

    /**
     * Throws away SMS-derived transactions you haven't edited and reads the inbox again with the
     * current parser. Your edits, notes, manual entries, budgets and goals are kept.
     */
    fun rebuild(months: Long = 12, auto: Boolean = false) {
        if (rebuilding.value) return
        rebuilding.value = true
        importState.value = ImportState(running = true)
        viewModelScope.launch {
            try {
                repo.clearForRescan()
                val result = SmsHistoryImporter.import(getApplication(), repo, months) { done, total ->
                    importState.value = ImportState(running = true, progress = if (total == 0) 1f else done.toFloat() / total)
                }
                repo.settings.update { it.copy(parserVersion = PARSER_VERSION) }
                importState.value = ImportState(result = result)
            } catch (e: Exception) {
                importState.value = ImportState(error = if (auto) null else e.message ?: "Re-scan failed")
            } finally {
                rebuilding.value = false
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

    // ---------------------------------------------------------------- Claude

    private fun snapshot(): String = FinanceSnapshot.build(
        settings.value, report.value, liveAccounts.value, goals.value, window.value,
    )

    val brief = MutableStateFlow(AiTextState(text = settings.value.briefText.takeIf { settings.value.briefDay == LocalDate.now().toString() } ?: ""))

    fun refreshBrief() {
        if (brief.value.loading || !settings.value.aiReady) return
        brief.value = brief.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val text = brain.dailyBrief(snapshot())
                repo.settings.update { it.copy(briefDay = LocalDate.now().toString(), briefText = text) }
                brief.value = AiTextState(text = text)
            } catch (e: AiException) {
                brief.value = brief.value.copy(loading = false, error = e.message)
            } catch (e: Exception) {
                brief.value = brief.value.copy(loading = false, error = "Couldn't reach Claude.")
            }
        }
    }

    val review = MutableStateFlow(AiTextState())

    fun runReview() {
        if (review.value.loading) return
        review.value = AiTextState(loading = true)
        viewModelScope.launch {
            try {
                val label = month.value.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                review.value = AiTextState(text = brain.monthlyReview(snapshot(), label))
            } catch (e: AiException) {
                review.value = AiTextState(error = e.message)
            } catch (e: Exception) {
                review.value = AiTextState(error = "Couldn't reach Claude.")
            }
        }
    }

    val chat = MutableStateFlow(ChatState())
    private var chatJob: Job? = null

    fun ask(question: String) {
        val q = question.trim()
        if (q.isBlank() || chat.value.streaming) return
        val history = chat.value.messages + ChatMessage(true, q)
        chat.value = ChatState(history + ChatMessage(false, ""), streaming = true)
        chatJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                brain.chat(history, snapshot()).collect { piece ->
                    sb.append(piece)
                    chat.value = chat.value.copy(messages = history + ChatMessage(false, sb.toString()))
                }
                chat.value = chat.value.copy(streaming = false)
            } catch (e: AiException) {
                chat.value = ChatState(history, streaming = false, error = e.message)
            } catch (e: Exception) {
                chat.value = ChatState(history, streaming = false, error = "Couldn't reach Claude.")
            }
        }
    }

    fun stopChat() {
        chatJob?.cancel()
        chat.value = chat.value.copy(streaming = false)
    }

    fun clearChat() {
        stopChat()
        chat.value = ChatState()
    }

    val cleanup = MutableStateFlow(CleanupState())

    /** Has Claude re-read recent transactions (and their masked SMS) to fix names, categories and false positives. */
    fun runCleanup() {
        if (cleanup.value.running) return
        cleanup.value = CleanupState(running = true)
        viewModelScope.launch {
            var reviewed = 0
            var fixed = 0
            try {
                val since = month.value.minusMonths(3).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                repeat(6) {
                    val batch = repo.needingAiReview(since, 50)
                    if (batch.isEmpty()) return@repeat
                    val results = brain.review(batch).associateBy { it.id }
                    batch.forEach { t ->
                        val r = results[t.id]
                        val updated = when {
                            r == null -> t.copy(aiChecked = true)
                            r.notTransaction -> t.copy(excluded = true, aiChecked = true, note = t.note.ifBlank { "Not a real payment (AI)" })
                            r.selfTransfer -> t.copy(category = Category.SELF_TRANSFER, excluded = true, aiChecked = true)
                            else -> {
                                val cat = r.category?.takeIf { c -> (t.type == TxnType.CREDIT) == (c.bucket == com.kharcha.app.data.Bucket.INCOME) } ?: t.category
                                if (r.category != null && cat != Category.OTHERS && cat != Category.TRANSFER && r.merchant != null) {
                                    repo.rememberMerchant(r.merchant, cat)
                                }
                                t.copy(merchant = r.merchant ?: t.merchant, category = cat, aiChecked = true)
                            }
                        }
                        if (updated.copy(aiChecked = t.aiChecked) != t) fixed++
                        repo.saveRaw(updated)
                    }
                    reviewed += batch.size
                    cleanup.value = CleanupState(running = true, reviewed = reviewed, fixed = fixed)
                }
                cleanup.value = CleanupState(reviewed = reviewed, fixed = fixed, done = true)
            } catch (e: AiException) {
                cleanup.value = CleanupState(reviewed = reviewed, fixed = fixed, error = e.message)
            } catch (e: Exception) {
                cleanup.value = CleanupState(reviewed = reviewed, fixed = fixed, error = "Clean-up stopped: ${e.message}")
            }
        }
    }

    val keyTest = MutableStateFlow(AiTextState())

    fun testKey() {
        keyTest.value = AiTextState(loading = true)
        viewModelScope.launch {
            keyTest.value = try {
                AiTextState(text = brain.test())
            } catch (e: AiException) {
                AiTextState(error = e.message)
            } catch (e: Exception) {
                AiTextState(error = "Couldn't reach Claude.")
            }
        }
    }

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
