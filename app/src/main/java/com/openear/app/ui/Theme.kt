package com.openear.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.openear.app.settings.ThemeMode

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)

private val LightScheme = lightColorScheme(
    primary = Black, onPrimary = White,
    secondary = Color(0xFF3A3A3A), onSecondary = White,
    background = White, onBackground = Black,
    surface = White, onSurface = Black,
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF3A3A3A),
    secondaryContainer = Color(0xFFD9D9D9), onSecondaryContainer = Black,
    outline = Color(0xFF6B6B6B),
    error = Color(0xFFB3261E), onError = White,
)

private val DarkScheme = darkColorScheme(
    primary = White, onPrimary = Black,
    secondary = Color(0xFFC8C8C8), onSecondary = Black,
    background = Black, onBackground = White,
    surface = Black, onSurface = White,
    surfaceVariant = Color(0xFF242424), onSurfaceVariant = Color(0xFFD0D0D0),
    secondaryContainer = Color(0xFF3A3A3A), onSecondaryContainer = White,
    outline = Color(0xFF8A8A8A),
    error = Color(0xFFFF8A80), onError = Black,
)

@Composable
fun OpenEarTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
