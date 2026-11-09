package com.kharcha.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.data.Category
import com.kharcha.app.insights.Insight
import com.kharcha.app.insights.Money
import com.kharcha.app.insights.Severity
import com.kharcha.app.ui.theme.Alert
import com.kharcha.app.ui.theme.Good
import com.kharcha.app.ui.theme.Tip
import com.kharcha.app.ui.theme.Warn
import java.time.YearMonth
import java.time.format.DateTimeFormatter

fun inr(v: Double) = Money.inr(v)
fun inr2(v: Double) = Money.inr(v, decimals = true)

val Category.colour get() = Color(color)

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onShift: (Long) -> Unit, modifier: Modifier = Modifier) {
    val isNow = month == YearMonth.now()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { onShift(1) }, enabled = !isNow) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
        }
    }
}

@Composable
fun CategoryDot(category: Category, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(category.colour.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Text(category.emoji, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun BudgetBar(spent: Double, limit: Double, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val pct = if (limit > 0) (spent / limit).toFloat() else 0f
    val barColor = when {
        pct >= 1f -> Alert
        pct >= 0.8f -> Warn
        else -> color
    }
    LinearProgressIndicator(
        progress = { pct.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        color = barColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun GoalBar(saved: Double, target: Double, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { if (target > 0) (saved / target).toFloat().coerceIn(0f, 1f) else 0f },
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        color = if (saved >= target && target > 0) Good else MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

fun Severity.colour(): Color = when (this) {
    Severity.GOOD -> Good
    Severity.TIP -> Tip
    Severity.WARN -> Warn
    Severity.ALERT -> Alert
}

fun Severity.label(): String = when (this) {
    Severity.GOOD -> "WIN"
    Severity.TIP -> "SAVE"
    Severity.WARN -> "WATCH"
    Severity.ALERT -> "ACT NOW"
}

@Composable
fun InsightRow(insight: Insight, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(
            Modifier.padding(top = 4.dp).size(width = 4.dp, height = 36.dp).clip(RoundedCornerShape(2.dp))
                .background(insight.severity.colour()),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    insight.severity.label(), style = MaterialTheme.typography.labelSmall,
                    color = insight.severity.colour(), fontWeight = FontWeight.Bold,
                )
                if (insight.potentialSaving >= 100) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "save ~${inr(insight.potentialSaving)}/mo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(insight.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (insight.detail.isNotBlank()) {
                Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
