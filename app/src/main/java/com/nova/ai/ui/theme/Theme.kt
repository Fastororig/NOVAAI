package com.nova.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NovaPrimary,
    onPrimary = TextPrimary,
    primaryContainer = NovaPrimaryDark,
    onPrimaryContainer = TextPrimary,
    secondary = NovaAccent,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = ErrorColor
)

@Composable
fun NovaAITheme(content: @Composable () -> Unit) {
    // Pure dark theme enforced for consistent high-contrast UX and OLED power saving
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
