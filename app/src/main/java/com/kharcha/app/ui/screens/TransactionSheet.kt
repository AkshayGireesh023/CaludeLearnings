package com.kharcha.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kharcha.app.data.Category
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.TxnSource
import com.kharcha.app.data.TxnType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Edit an existing transaction (existing != null) or add a manual one (cash spends etc).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionSheet(
    existing: TransactionEntity?,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity, applyToMerchant: Boolean) -> Unit,
    onAdd: (amount: Double, type: TxnType, merchant: String, category: Category, timestamp: Long, note: String) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = ZoneId.systemDefault()
    var amount by remember { mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: TxnType.DEBIT) }
    var merchant by remember { mutableStateOf(existing?.merchant ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: Category.FOOD) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var excluded by remember { mutableStateOf(existing?.excluded ?: false) }
    var applyAll by remember { mutableStateOf(true) }
    var timestamp by remember { mutableStateOf(existing?.timestamp ?: System.currentTimeMillis()) }
    var showDate by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (existing == null) "Add transaction" else "Edit transaction", style = MaterialTheme.typography.titleLarge)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TxnType.DEBIT to "Spent", TxnType.CREDIT to "Received").forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            type = t
                            if (t == TxnType.CREDIT && category.bucket != com.kharcha.app.data.Bucket.INCOME) category = Category.INCOME
                            if (t == TxnType.DEBIT && category.bucket == com.kharcha.app.data.Bucket.INCOME) category = Category.OTHERS
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }

            OutlinedTextField(
                value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
                label = { Text("Amount (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = merchant, onValueChange = { merchant = it },
                label = { Text(if (type == TxnType.DEBIT) "Paid to" else "Received from") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Instant.ofEpochMilli(timestamp).atZone(zone).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a")),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showDate = true }) { Text("Change date") }
            }

            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val options = if (type == TxnType.DEBIT) Category.expenseCategories else Category.incomeCategories
                options.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text("${c.emoji} ${c.label}") })
                }
            }

            if (existing != null && type == TxnType.DEBIT && category != existing.category) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyAll, onCheckedChange = { applyAll = it })
                    Text("Always file ${merchant.ifBlank { "this merchant" }} under ${category.label}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("Note (what was it for?)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exclude from totals", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "For moving money between your own accounts or a loan repaid to you.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = excluded, onCheckedChange = { excluded = it })
            }

            if (existing != null && existing.rawText.isNotBlank() && existing.source != TxnSource.MANUAL) {
                TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "Hide original message" else "Show original message") }
                if (showRaw) {
                    Text(existing.rawText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing != null) {
                    OutlinedButton(onClick = { onDelete(existing); onDismiss() }) { Text("Delete") }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = (amount.toDoubleOrNull() ?: 0.0) > 0,
                    onClick = {
                        val a = amount.toDoubleOrNull() ?: return@Button
                        if (existing == null) {
                            onAdd(a, type, merchant.trim(), category, timestamp, note.trim())
                        } else {
                            onSave(
                                existing.copy(
                                    amount = a, type = type, merchant = merchant.trim().ifBlank { existing.merchant },
                                    category = category, note = note.trim(), excluded = excluded, timestamp = timestamp,
                                ),
                                applyAll && category != existing.category,
                            )
                        }
                        onDismiss()
                    },
                ) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDate) {
        val current = Instant.ofEpochMilli(timestamp).atZone(zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = if (existing == null && date == java.time.LocalDate.now()) LocalTime.now() else current.toLocalTime()
                        timestamp = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
