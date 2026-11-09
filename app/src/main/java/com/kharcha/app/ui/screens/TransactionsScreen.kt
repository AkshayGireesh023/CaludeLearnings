package com.kharcha.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.data.Category
import com.kharcha.app.data.TransactionEntity
import com.kharcha.app.data.TxnSource
import com.kharcha.app.data.TxnType
import com.kharcha.app.data.isExpense
import com.kharcha.app.data.isIncome
import com.kharcha.app.ui.components.CategoryDot
import com.kharcha.app.ui.components.EmptyState
import com.kharcha.app.ui.components.MonthSwitcher
import com.kharcha.app.ui.components.SectionCard
import com.kharcha.app.ui.components.StatPill
import com.kharcha.app.ui.components.inr
import com.kharcha.app.ui.components.inr2
import com.kharcha.app.ui.theme.Good
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Filter(val label: String) { ALL("All"), SPENT("Spent"), RECEIVED("Received") }

@Composable
fun TransactionsScreen(
    month: YearMonth,
    transactions: List<TransactionEntity>,
    onShiftMonth: (Long) -> Unit,
    onOpen: (TransactionEntity) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var category by remember { mutableStateOf<Category?>(null) }

    val zone = ZoneId.systemDefault()
    val filtered = transactions.filter { t ->
        (query.isBlank() || t.merchant.contains(query, true) || t.note.contains(query, true) || t.category.label.contains(query, true)) &&
            when (filter) {
                Filter.ALL -> true
                Filter.SPENT -> t.type == TxnType.DEBIT
                Filter.RECEIVED -> t.type == TxnType.CREDIT
            } &&
            (category == null || t.category == category)
    }
    val grouped = filtered.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    val usedCategories = transactions.map { it.category }.distinct().sortedBy { it.ordinal }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Expense list", style = MaterialTheme.typography.headlineMedium)
            MonthSwitcher(month, onShiftMonth)
        }
        item {
            SectionCard {
                Row {
                    StatPill("SPENT", inr(filtered.filter { it.isExpense }.sumOf { it.amount }), Modifier.weight(1f))
                    StatPill("RECEIVED", inr(filtered.filter { it.isIncome }.sumOf { it.amount }), Modifier.weight(1f), Good)
                    StatPill("ENTRIES", "${filtered.size}", Modifier.weight(0.6f))
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search merchant, note, category") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
                items(usedCategories) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = if (category == c) null else c },
                        label = { Text("${c.emoji} ${c.label}") },
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    "No transactions",
                    if (transactions.isEmpty()) "Import SMS history from Settings, or tap + to add one." else "Nothing matches these filters.",
                )
            }
        }
        grouped.forEach { (date, list) ->
            item(key = "h$date") { DayHeader(date, list) }
            item(key = "d$date") {
                SectionCard {
                    list.forEach { t -> TransactionRow(t, onClick = { onOpen(t) }) }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun DayHeader(date: LocalDate, list: List<TransactionEntity>) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        val spent = list.filter { it.isExpense }.sumOf { it.amount }
        if (spent > 0) Text("−${inr(spent)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TransactionRow(t: TransactionEntity, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryDot(t.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.merchant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                add(t.category.label)
                add(time)
                if (t.source == TxnSource.MANUAL) add("manual")
                if (t.excluded) add("excluded")
            }.joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.note.isNotBlank()) {
                Text(t.note, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val credit = t.type == TxnType.CREDIT
        Text(
            (if (credit) "+" else "−") + inr2(t.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (credit) Good else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (t.excluded) TextDecoration.LineThrough else null,
        )
    }
}
