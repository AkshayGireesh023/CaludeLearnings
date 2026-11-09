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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kharcha.app.data.AccountEntity
import com.kharcha.app.data.Category
import com.kharcha.app.data.GoalEntity
import com.kharcha.app.data.UserSettings
import com.kharcha.app.insights.MonthReport
import com.kharcha.app.ui.components.BudgetBar
import com.kharcha.app.ui.components.CategoryDot
import com.kharcha.app.ui.components.GoalBar
import com.kharcha.app.ui.components.SectionCard
import com.kharcha.app.ui.components.SectionTitle
import com.kharcha.app.ui.components.inr
import com.kharcha.app.ui.theme.Good
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private sealed interface PlanDialog {
    data object MonthlyBudget : PlanDialog
    data class CategoryBudget(val category: Category) : PlanDialog
    data class Account(val account: AccountEntity?) : PlanDialog
    data class Goal(val goal: GoalEntity?) : PlanDialog
    data class AddToGoal(val goal: GoalEntity) : PlanDialog
}

@Composable
fun PlanScreen(
    settings: UserSettings,
    report: MonthReport?,
    accounts: List<AccountEntity>,
    budgets: Map<Category, Double>,
    goals: List<GoalEntity>,
    onSetMonthlyBudget: (Double) -> Unit,
    onSetCategoryBudget: (Category, Double) -> Unit,
    onSaveAccount: (AccountEntity) -> Unit,
    onDeleteAccount: (AccountEntity) -> Unit,
    onSaveGoal: (GoalEntity) -> Unit,
    onDeleteGoal: (GoalEntity) -> Unit,
) {
    var dialog by remember { mutableStateOf<PlanDialog?>(null) }
    val spentByCat = report?.categories?.associate { it.category to it.amount }.orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Plan", style = MaterialTheme.typography.headlineMedium) }

        // Accounts & balance
        item {
            SectionCard {
                SectionTitle("Bank accounts") {
                    TextButton(onClick = { dialog = PlanDialog.Account(null) }) { Icon(Icons.Default.Add, null); Text("Add") }
                }
                if (accounts.isEmpty()) {
                    Text(
                        "Balances are read from bank SMS (\"Avl Bal\"). If your bank doesn't send it, add the account here.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                accounts.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { dialog = PlanDialog.Account(a) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(a.nickname.ifBlank { a.bank } + (a.last4?.let { " ••$it" } ?: ""))
                            Text(
                                "Updated " + Instant.ofEpochMilli(a.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a")),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(inr(a.balance), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (accounts.size > 1) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("Total", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(inr(accounts.sumOf { it.balance }), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Monthly budget
        item {
            SectionCard {
                SectionTitle("Monthly budget") { TextButton(onClick = { dialog = PlanDialog.MonthlyBudget }) { Text(if (settings.monthlyBudget > 0) "Edit" else "Set") } }
                if (settings.monthlyBudget > 0) {
                    Text("${inr(report?.spent ?: 0.0)} of ${inr(settings.monthlyBudget)}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    BudgetBar(report?.spent ?: 0.0, settings.monthlyBudget, Modifier.height(8.dp))
                } else {
                    val suggestion = if (settings.monthlyIncome > 0) settings.monthlyIncome * (1 - settings.savingsTargetPct / 100.0) else 0.0
                    Text(
                        if (suggestion > 0) "Suggested: ${inr(suggestion)} (income minus your ${settings.savingsTargetPct}% savings target)."
                        else "Pick a number you'll commit to. Everything else on the dashboard keys off it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Category budgets
        item {
            SectionCard {
                SectionTitle("Category limits")
                Text(
                    "Cap the categories that leak. Tap one to set a limit; you'll get an alert at 80% and 100%.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val ordered = Category.expenseCategories.sortedByDescending { (budgets[it] ?: 0.0) * 1e9 + (spentByCat[it] ?: 0.0) }
                ordered.forEach { c ->
                    val limit = budgets[c]
                    val spent = spentByCat[c] ?: 0.0
                    Column(Modifier.fillMaxWidth().clickable { dialog = PlanDialog.CategoryBudget(c) }.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryDot(c, 32)
                            Spacer(Modifier.width(10.dp))
                            Text(c.label, modifier = Modifier.weight(1f))
                            Text(
                                if (limit != null) "${inr(spent)} / ${inr(limit)}" else if (spent > 0) "${inr(spent)} · no limit" else "no limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (limit != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (limit != null) {
                            Spacer(Modifier.height(6.dp))
                            BudgetBar(spent, limit, Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        // Goals
        item {
            SectionCard {
                SectionTitle("Savings goals") {
                    TextButton(onClick = { dialog = PlanDialog.Goal(null) }) { Icon(Icons.Default.Add, null); Text("New") }
                }
                if (goals.isEmpty()) {
                    Text(
                        "Give your savings a name — emergency fund, a trip, a bike. Named money is harder to spend.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                goals.forEach { g -> GoalItem(g, onAdd = { dialog = PlanDialog.AddToGoal(g) }, onEdit = { dialog = PlanDialog.Goal(g) }) }
                if (goals.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Total saved toward goals: ${inr(goals.sumOf { it.saved })}",
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }

    when (val d = dialog) {
        null -> {}
        PlanDialog.MonthlyBudget -> AmountDialog("Monthly budget", settings.monthlyBudget, onDismiss = { dialog = null }) {
            onSetMonthlyBudget(it); dialog = null
        }
        is PlanDialog.CategoryBudget -> AmountDialog(
            "${d.category.emoji} ${d.category.label} limit", budgets[d.category] ?: 0.0,
            hint = "Set 0 to remove the limit", onDismiss = { dialog = null },
        ) { onSetCategoryBudget(d.category, it); dialog = null }
        is PlanDialog.Account -> AccountDialog(d.account, onDismiss = { dialog = null }, onSave = { onSaveAccount(it); dialog = null },
            onDelete = { onDeleteAccount(it); dialog = null })
        is PlanDialog.Goal -> GoalDialog(d.goal, onDismiss = { dialog = null }, onSave = { onSaveGoal(it); dialog = null },
            onDelete = { onDeleteGoal(it); dialog = null })
        is PlanDialog.AddToGoal -> AmountDialog("Add to ${d.goal.name}", 0.0, hint = "Use a negative number to withdraw", allowNegative = true,
            onDismiss = { dialog = null }) { onSaveGoal(d.goal.copy(saved = (d.goal.saved + it).coerceAtLeast(0.0))); dialog = null }
    }
}

@Composable
private fun GoalItem(g: GoalEntity, onAdd: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${g.emoji} ${g.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("+ Add money") }
        }
        GoalBar(g.saved, g.target, Modifier.height(8.dp))
        Spacer(Modifier.height(4.dp))
        val remaining = (g.target - g.saved).coerceAtLeast(0.0)
        val sub = when {
            remaining <= 0 -> "Goal reached! 🎉"
            g.deadline != null -> {
                val end = Instant.ofEpochMilli(g.deadline).atZone(ZoneId.systemDefault()).toLocalDate()
                val months = ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1), end.withDayOfMonth(1)).coerceAtLeast(1)
                "${inr(remaining)} to go · save ${inr(remaining / months)}/month to hit it by ${end.format(DateTimeFormatter.ofPattern("MMM yyyy"))}"
            }
            else -> "${inr(remaining)} to go"
        }
        Text(
            "${inr(g.saved)} of ${inr(g.target)} · $sub", style = MaterialTheme.typography.bodySmall,
            color = if (remaining <= 0) Good else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AmountDialog(
    title: String,
    initial: Double,
    hint: String? = null,
    allowNegative: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(if (initial != 0.0) initial.toLong().toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || (allowNegative && it == '-') } },
                    label = { Text("Amount (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.toDoubleOrNull() ?: 0.0) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AccountDialog(existing: AccountEntity?, onDismiss: () -> Unit, onSave: (AccountEntity) -> Unit, onDelete: (AccountEntity) -> Unit) {
    var bank by remember { mutableStateOf(existing?.bank ?: "") }
    var last4 by remember { mutableStateOf(existing?.last4 ?: "") }
    var nickname by remember { mutableStateOf(existing?.nickname ?: "") }
    var balance by remember { mutableStateOf(existing?.balance?.toLong()?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add account" else "Edit account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(bank, { bank = it }, label = { Text("Bank") }, singleLine = true, enabled = existing == null)
                OutlinedTextField(last4, { v -> last4 = v.filter(Char::isDigit).take(4) }, label = { Text("Last 4 digits") }, singleLine = true,
                    enabled = existing == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(nickname, { nickname = it }, label = { Text("Nickname (optional)") }, singleLine = true)
                OutlinedTextField(balance, { v -> balance = v.filter { it.isDigit() || it == '.' || it == '-' } }, label = { Text("Current balance (₹)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text(
                    "Updates automatically whenever a bank SMS includes the balance.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = bank.isNotBlank(), onClick = {
                val b = balance.toDoubleOrNull() ?: 0.0
                val acc = existing?.copy(nickname = nickname.trim(), balance = b, updatedAt = System.currentTimeMillis())
                    ?: AccountEntity("${bank.trim()}:${last4.ifBlank { "main" }}", bank.trim(), last4.ifBlank { null }, b, System.currentTimeMillis(), nickname.trim())
                onSave(acc)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun GoalDialog(existing: GoalEntity?, onDismiss: () -> Unit, onSave: (GoalEntity) -> Unit, onDelete: (GoalEntity) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "🎯") }
    var target by remember { mutableStateOf(existing?.target?.toLong()?.toString() ?: "") }
    var months by remember {
        mutableStateOf(
            existing?.deadline?.let {
                val end = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                ChronoUnit.MONTHS.between(LocalDate.now(), end).coerceAtLeast(1).toString()
            } ?: "",
        )
    }
    val emojis = listOf("🎯", "🛟", "✈️", "🏍️", "🚗", "🏠", "💍", "🎓", "📱", "💻", "🏖️", "🎁")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New savings goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    emojis.take(6).forEach { e ->
                        TextButton(onClick = { emoji = e }, modifier = Modifier.width(40.dp)) {
                            Text(e, style = if (emoji == e) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    emojis.drop(6).forEach { e ->
                        TextButton(onClick = { emoji = e }, modifier = Modifier.width(40.dp)) {
                            Text(e, style = if (emoji == e) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Goal name (e.g. Emergency fund)") }, singleLine = true)
                OutlinedTextField(target, { v -> target = v.filter(Char::isDigit) }, label = { Text("Target (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(months, { v -> months = v.filter(Char::isDigit).take(3) }, label = { Text("In how many months? (optional)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (target.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                val deadline = months.toLongOrNull()?.takeIf { it > 0 }?.let {
                    LocalDate.now().plusMonths(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                val t = target.toDouble()
                onSave(existing?.copy(name = name.trim(), emoji = emoji, target = t, deadline = deadline)
                    ?: GoalEntity(name = name.trim(), emoji = emoji, target = t, deadline = deadline))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
