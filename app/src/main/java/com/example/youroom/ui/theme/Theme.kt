package com.example.youroom.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.youroom.domain.Campus

fun campusColorScheme(campus: Campus, darkTheme: Boolean): ColorScheme {
    val west = campus == Campus.XITUCHENG
    val accent = if (west) XituchengBlue else ShaheGreen
    if (darkTheme) {
        val primary = if (west) Color(0xFFB8CEE9) else Color(0xFFA8D2BD)
        val container = if (west) Color(0xFF263C54) else Color(0xFF254639)
        return darkColorScheme(
            primary = primary, onPrimary = Color(0xFF14211B),
            primaryContainer = container, onPrimaryContainer = primary,
            secondary = primary, onSecondary = Color(0xFF14211B),
            secondaryContainer = container, onSecondaryContainer = primary,
            tertiary = primary, tertiaryContainer = container,
            background = Color(0xFF141715), surface = Color(0xFF191D1A),
            surfaceContainerLowest = Color(0xFF111412),
            surfaceContainerLow = Color(0xFF1C211D), surfaceContainer = Color(0xFF232A24),
            surfaceContainerHigh = Color(0xFF2C332D), surfaceContainerHighest = Color(0xFF353C36),
            onBackground = Color(0xFFEAECE5), onSurface = Color(0xFFEAECE5),
            onSurfaceVariant = Color(0xFFB5BCB3),
            outline = Color(0xFF818A80), outlineVariant = Color(0xFF39423A),
            surfaceTint = Color.Transparent,
        )
    }
    val container = if (west) Color(0xFFE8EDF2) else Color(0xFFE6EEE7)
    return lightColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = container, onPrimaryContainer = accent,
        secondary = accent, onSecondary = Color.White,
        secondaryContainer = container, onSecondaryContainer = accent,
        tertiary = accent, tertiaryContainer = container,
        background = YouRoomPaper, surface = YouRoomPaper,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F2EC),
        surfaceContainer = Color(0xFFEDEFE8), surfaceContainerHigh = Color(0xFFE7EAE3),
        surfaceContainerHighest = Color(0xFFE1E5DD),
        onBackground = YouRoomInk, onSurface = YouRoomInk, onSurfaceVariant = YouRoomMuted,
        outline = Color(0xFF858D84), outlineVariant = YouRoomRule,
        surfaceTint = Color.Transparent,
    )
}

@Composable
fun YouRoomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    campus: Campus = Campus.XITUCHENG,
    content: @Composable () -> Unit
) {
    val target = campusColorScheme(campus, darkTheme)
    val primary by animateColorAsState(target.primary, tween(180), label = "campus_primary")
    val container by animateColorAsState(target.primaryContainer, tween(180), label = "campus_container")
    val onPrimary by animateColorAsState(target.onPrimary, tween(180), label = "campus_on_primary")

    MaterialTheme(
        colorScheme = target.copy(primary = primary, secondary = primary,
            primaryContainer = container, secondaryContainer = container,
            onPrimaryContainer = primary, onSecondaryContainer = primary, onPrimary = onPrimary),
        typography = Typography,
        shapes = YouRoomShapes,
        content = content
    )
}
