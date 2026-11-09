package com.kharcha.app.ingest

import android.content.Context
import android.provider.Telephony
import com.kharcha.app.data.Repository
import com.kharcha.app.data.TxnSource
import com.kharcha.app.parser.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class ImportResult(val scanned: Int, val matched: Int, val added: Int)

/** Reads past bank SMS from the inbox so you start with months of history, not an empty screen. */
object SmsHistoryImporter {
    suspend fun import(
        context: Context,
        repo: Repository,
        months: Long = 6,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): ImportResult = importSince(
        context, repo,
        LocalDate.now().minusMonths(months).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        onProgress,
    ).also { repo.settings.update { it.copy(lastSmsImport = System.currentTimeMillis()) } }

    /**
     * Catches up on any SMS since the last sync. Covers phones that kill the live SMS receiver
     * in the background (common on OnePlus / Xiaomi / Oppo). Duplicates are skipped by the repository.
     */
    suspend fun catchUp(context: Context, repo: Repository): ImportResult {
        if (!hasSmsPermission(context)) return ImportResult(0, 0, 0)
        val last = repo.settings.state.value.lastSync
        val since = if (last == 0L) System.currentTimeMillis() - 3L * 24 * 3600 * 1000 else last - 2L * 3600 * 1000
        return importSince(context, repo, since)
    }

    fun hasSmsPermission(context: Context) =
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun importSince(
        context: Context,
        repo: Repository,
        since: Long,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): ImportResult = mutex.withLock { withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        var scanned = 0
        var matched = 0
        var added = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val total = c.count
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                scanned++
                val parsed = SmsParser.parse(c.getString(iAddr) ?: "", c.getString(iBody) ?: "", c.getLong(iDate))
                if (parsed != null) {
                    matched++
                    if (repo.ingest(parsed, TxnSource.SMS) != null) added++
                }
                if (scanned % 50 == 0) onProgress(scanned, total)
            }
            onProgress(total, total)
        }
        repo.settings.update { it.copy(lastSync = startedAt) }
        ImportResult(scanned, matched, added)
    } }
}
