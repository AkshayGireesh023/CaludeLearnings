package com.kharcha.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kharcha.app.ui.ChatState
import com.kharcha.app.ui.components.MarkdownText
import com.kharcha.app.ui.theme.Alert

private val starters = listOf(
    "Where am I overspending this month?",
    "How much did I spend on food delivery?",
    "Can I afford a ₹15,000 phone this month?",
    "Make me a plan to save ₹5,000 more",
    "What are my recurring payments?",
    "Compare this month with last month",
    "Which day of the week do I spend most?",
    "How long until I hit my savings goal?",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(
    chat: ChatState,
    aiReady: Boolean,
    pendingQuestion: String?,
    onConsumePending: () -> Unit,
    onAsk: (String) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val purple = Color(0xFF6B4EFF)

    LaunchedEffect(pendingQuestion) {
        if (pendingQuestion != null) {
            if (aiReady) onAsk(pendingQuestion)
            onConsumePending()
        }
    }
    LaunchedEffect(chat.messages.size, chat.messages.lastOrNull()?.text?.length) {
        if (chat.messages.isNotEmpty()) listState.animateScrollToItem(chat.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = purple)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Ask Kharcha", style = MaterialTheme.typography.headlineSmall)
                Text("Claude, reading your real transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (chat.messages.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Filled.DeleteSweep, "New chat") }
        }

        if (!aiReady) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🧠", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(12.dp))
                Text("Your money, explained", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Add a Claude API key and ask anything: \"Why is this month so expensive?\", \"Can I afford a trip?\", \"Where can I cut ₹3,000?\"",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Button(onClick = onOpenSettings) { Text("Add API key") }
            }
            return@Column
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (chat.messages.isEmpty()) {
                item {
                    Column {
                        Text("Try asking", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            starters.forEach { q -> SuggestionChip(onClick = { onAsk(q) }, label = { Text(q) }) }
                        }
                    }
                }
            }
            itemsIndexed(chat.messages) { i, m ->
                val isLast = i == chat.messages.lastIndex
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier.widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (m.fromUser) 20.dp else 6.dp, bottomEnd = if (m.fromUser) 6.dp else 20.dp))
                            .background(if (m.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        if (m.fromUser) {
                            Text(m.text, color = MaterialTheme.colorScheme.onPrimary)
                        } else if (m.text.isBlank() && isLast && chat.streaming) {
                            Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        } else {
                            MarkdownText(m.text + if (isLast && chat.streaming) " ▍" else "")
                        }
                    }
                }
            }
            chat.error?.let { item { Text(it, color = Alert, style = MaterialTheme.typography.bodySmall) } }
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about your money…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) { onAsk(input); input = "" } }),
            )
            Spacer(Modifier.width(8.dp))
            if (chat.streaming) {
                FilledIconButton(onClick = onStop, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Stop, "Stop") }
            } else {
                FilledIconButton(
                    onClick = { onAsk(input); input = "" }, enabled = input.isNotBlank(), modifier = Modifier.size(52.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    }
}
