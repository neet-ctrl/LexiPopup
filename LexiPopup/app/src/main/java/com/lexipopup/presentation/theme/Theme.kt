package com.lexipopup.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightPrimary = Color(0xFF1565C0)
private val LightOnPrimary = Color.White
private val LightPrimaryContainer = Color(0xFFD9E3FF)
private val LightSecondary = Color(0xFF5E35B1)
private val LightBackground = Color(0xFFF8F9FF)
private val LightSurface = Color(0xFFFFFFFF)
private val LightError = Color(0xFFD32F2F)

private val DarkPrimary = Color(0xFF82B1FF)
private val DarkOnPrimary = Color(0xFF00214F)
private val DarkPrimaryContainer = Color(0xFF003782)
private val DarkSecondary = Color(0xFFCBB6FF)
private val DarkBackground = Color(0xFF0D0E1A)
private val DarkSurface = Color(0xFF1A1B2E)
private val DarkError = Color(0xFFFF6B6B)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface,
    error = LightError,
    surfaceVariant = Color(0xFFEEF1F8)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    error = DarkError,
    surfaceVariant = Color(0xFF252638)
)

@Composable
fun LexiPopupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
