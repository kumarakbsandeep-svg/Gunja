package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ZoyaPrimaryMagenta,
    onPrimary = Color.White,
    primaryContainer = ZoyaCardElevated,
    onPrimaryContainer = Color.White,
    secondary = ZoyaSecondaryViolet,
    onSecondary = Color.White,
    secondaryContainer = ZoyaSurfaceDark,
    onSecondaryContainer = ZoyaTextSecondary,
    tertiary = ZoyaAccentCyan,
    background = ZoyaBgDark,
    onBackground = ZoyaTextPrimary,
    surface = ZoyaSurfaceDark,
    onSurface = ZoyaTextPrimary,
    surfaceVariant = ZoyaCardDark,
    onSurfaceVariant = ZoyaTextSecondary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
