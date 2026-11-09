package com.kharcha.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kharcha.app.insights.Money

/** Money that counts up/down to its new value instead of jumping. */
@Composable
fun AnimatedMoney(value: Double, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "money",
    )
    Text(Money.inr(animated.toDouble()), style = style, color = color, modifier = modifier, maxLines = 1)
}

/** A softly pulsing dot that says "this is live". */
@Composable
fun LiveDot(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Canvas(modifier.size(8.dp)) { drawCircle(color.copy(alpha = a)) }
}

/** Animates 0 → 1 when first shown (and again when [key] changes); use it to grow charts in. */
@Composable
fun rememberEntrance(key: Any? = Unit): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    return a.value
}

/**
 * Renders the small slice of markdown Claude uses on a phone: **bold**, _italic_ and "- " bullets.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        text.trim().lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.size(6.dp))
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("• ") || line.trimStart().startsWith("* ") -> Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(inline(line.trimStart().drop(2)), style = MaterialTheme.typography.bodyMedium, color = color)
                }
                line.startsWith("#") -> Text(
                    inline(line.trimStart('#', ' ')), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 6.dp),
                )
                else -> Text(inline(line), style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

private fun inline(s: String) = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) && s.indexOf("**", i + 2) > 0 -> {
                val end = s.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                i = end + 2
            }
            s[i] == '_' && s.indexOf('_', i + 1) > i + 1 && (i == 0 || s[i - 1] == ' ') -> {
                val end = s.indexOf('_', i + 1)
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                i = end + 1
            }
            else -> { append(s[i]); i++ }
        }
    }
}

