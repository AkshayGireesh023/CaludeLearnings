package com.kharcha.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharcha.app.insights.CategorySpend
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Category donut with total in the middle. */
@Composable
fun SpendDonut(categories: List<CategorySpend>, total: Double, modifier: Modifier = Modifier, size: Dp = 160.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val slices = categories.filter { it.amount > 0 }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.14f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            if (slices.isEmpty() || total <= 0) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            val gap = if (slices.size > 1) 2f else 0f
            slices.forEach { s ->
                val sweep = (s.amount / total * 360f).toFloat()
                drawArc(
                    s.category.colour, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(inr(total), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Discipline score ring, 0-100. */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: Dp = 84.dp, color: Color, track: Color) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.11f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * score / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$score", style = MaterialTheme.typography.headlineSmall, color = color)
    }
}

/** Daily spend bars with a dashed "safe daily pace" line. */
@Composable
fun DailyBars(daily: DoubleArray, dailyPace: Double, daysElapsed: Int, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val future = MaterialTheme.colorScheme.surfaceVariant
    val line = MaterialTheme.colorScheme.secondary
    val max = maxOf(daily.maxOrNull() ?: 0.0, dailyPace * 1.2, 1.0)
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val n = daily.size
        val slot = size.width / n
        val w = slot * 0.62f
        daily.forEachIndexed { i, v ->
            val h = if (i >= daysElapsed) size.height * 0.04f else (v / max * size.height).toFloat().coerceAtLeast(2f)
            val c = when {
                i >= daysElapsed -> future
                dailyPace > 0 && v > dailyPace -> over
                else -> bar
            }
            drawRoundRect(c, Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(w / 2, w / 2))
        }
        if (dailyPace > 0) {
            val y = (size.height - dailyPace / max * size.height).toFloat()
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
    }
}

/** Last six months of spending, current month highlighted. */
@Composable
fun TrendBars(trend: List<Pair<YearMonth, Double>>, budget: Double, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val current = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val line = MaterialTheme.colorScheme.secondary
    val max = maxOf(trend.maxOfOrNull { it.second } ?: 0.0, budget, 1.0) * 1.1
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / trend.size
            val w = slot * 0.5f
            trend.forEachIndexed { i, (_, v) ->
                val h = (v / max * size.height).toFloat().coerceAtLeast(3f)
                val c = when {
                    budget > 0 && v > budget -> over
                    i == trend.lastIndex -> current
                    else -> bar
                }
                drawRoundRect(c, Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(10f, 10f))
            }
            if (budget > 0) {
                val y = (size.height - budget / max * size.height).toFloat()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            trend.forEach { (m, v) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        m.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(compact(v), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

fun compact(v: Double): String = when {
    v >= 1_00_000 -> "₹%.1fL".format(v / 1_00_000)
    v >= 1_000 -> "₹%.1fk".format(v / 1_000)
    else -> "₹${v.toInt()}"
}
