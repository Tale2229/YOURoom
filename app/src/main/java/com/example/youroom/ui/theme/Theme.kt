package com.example.youroom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFAEC3FF),
    secondary = Color(0xFFAEC3FF),
    primaryContainer = Color(0xFF24395F),
    onPrimaryContainer = Color(0xFFDDE7FF),
    secondaryContainer = Color(0xFF24395F),
    onSecondaryContainer = Color(0xFFDDE7FF),
    background = Color(0xFF111825),
    surface = Color(0xFF151F30),
    surfaceContainer = Color(0xFF1C283C),
    onBackground = Color(0xFFE7EDF9),
    onSurface = Color(0xFFE7EDF9),
    onSurfaceVariant = Color(0xFFACB9CF)
)

private val LightColorScheme = lightColorScheme(
    primary = YouRoomBlue,
    secondary = YouRoomBlue,
    primaryContainer = Color(0xFFE8EEFE),
    onPrimaryContainer = YouRoomBlue,
    secondaryContainer = Color(0xFFE8EEFE),
    onSecondaryContainer = YouRoomBlue,
    background = YouRoomPaper,
    surface = Color.White,
    surfaceContainer = Color(0xFFEEF2FA),
    surfaceContainerLow = Color(0xFFF3F5FA),
    onPrimary = Color.White,
    onBackground = YouRoomInk,
    onSurface = YouRoomInk,
    onSurfaceVariant = YouRoomMuted,
    outlineVariant = Color(0xFFE0E5EF)
)

@Composable
fun YouRoomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
