package com.kharcha.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Forest = Color(0xFF0E3B2E)
val ForestLight = Color(0xFF1F5C47)
val Gold = Color(0xFFF4C95D)
val Paper = Color(0xFFF7F6F2)
val Ink = Color(0xFF1A1C1A)
val Good = Color(0xFF2E7D32)
val Warn = Color(0xFFEF8F00)
val Alert = Color(0xFFD32F2F)
val Tip = Color(0xFF1565C0)

private val Light = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EBDF),
    onPrimaryContainer = Forest,
    secondary = Color(0xFF8A6A12),
    secondaryContainer = Color(0xFFFCEFC8),
    onSecondaryContainer = Color(0xFF3D2F00),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDECE6),
    onSurfaceVariant = Color(0xFF5C5F5A),
    outlineVariant = Color(0xFFDCDBD4),
    error = Alert,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3B4),
    onPrimary = Color(0xFF003827),
    primaryContainer = ForestLight,
    onPrimaryContainer = Color(0xFFD5EBDF),
    secondary = Gold,
    secondaryContainer = Color(0xFF4A3A07),
    onSecondaryContainer = Color(0xFFFCEFC8),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE3E3DE),
    surface = Color(0xFF181D1A),
    onSurface = Color(0xFFE3E3DE),
    surfaceVariant = Color(0xFF242A26),
    onSurfaceVariant = Color(0xFFB9BDB7),
    outlineVariant = Color(0xFF353B37),
    error = Color(0xFFFF8A80),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
    )
}

@Composable
fun KharchaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
