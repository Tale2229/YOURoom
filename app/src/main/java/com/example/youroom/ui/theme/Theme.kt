package com.example.youroom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.example.youroom.domain.Campus
import com.example.youroom.domain.CampusTone
import com.example.youroom.domain.AppearancePreferences

val LocalCampusAppearance = staticCompositionLocalOf { AppearancePreferences() }

fun campusBadgeColor(campus: Campus, tone: CampusTone = CampusTone.default(campus)) = when(tone) {
    CampusTone.SAND -> Color(0xFF493D27)
    CampusTone.PINE -> Color(0xFF203F34)
    CampusTone.SLATE -> Color(0xFF34474D)
    CampusTone.CLAY -> Color(0xFF5B3E34)
}

fun campusColorScheme(campus: Campus, darkTheme: Boolean,
    tone: CampusTone = CampusTone.default(campus)): ColorScheme {
    if(tone == CampusTone.SLATE || tone == CampusTone.CLAY) {
        val clay = tone == CampusTone.CLAY
        val base = campusColorScheme(campus,darkTheme,CampusTone.SAND)
        val primary = if(darkTheme) { if(clay) Color(0xFFE6BBA9) else Color(0xFFB7CED5) }
            else if(clay) Color(0xFF785548) else Color(0xFF4C6068)
        val container = if(darkTheme) { if(clay) Color(0xFF49332B) else Color(0xFF2B3C43) }
            else if(clay) Color(0xFFF0E5DF) else Color(0xFFE7ECEC)
        val control = if(darkTheme) { if(clay) Color(0xFF30251F) else Color(0xFF242D31) }
            else if(clay) Color(0xFFF4EDE7) else Color(0xFFEDF0EE)
        val onPrimary = if(darkTheme) { if(clay) Color(0xFF281B16) else Color(0xFF162227) } else Color.White
        return base.copy(primary=primary,onPrimary=onPrimary,secondary=primary,onSecondary=onPrimary,
            tertiary=primary,onTertiary=onPrimary,primaryContainer=container,onPrimaryContainer=primary,
            secondaryContainer=container,onSecondaryContainer=primary,tertiaryContainer=container,onTertiaryContainer=primary,
            surfaceContainer=control,surfaceContainerLow=control,
            surfaceContainerHigh=container,surfaceContainerHighest=container,
            outline=primary.copy(alpha=.65f))
    }
    val west = tone == CampusTone.SAND
    val accent = if (west) XituchengKhaki else ShaheGreen
    if (darkTheme) {
        val primary = if (west) Color(0xFFD7C4A1) else Color(0xFFA8D2BD)
        val container = if (west) Color(0xFF453B28) else Color(0xFF254639)
        val onPrimary = if (west) Color(0xFF241F16) else Color(0xFF14211B)
        val onSurface = if (west) Color(0xFFEEE9DF) else Color(0xFFEAECE5)
        return darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = container, onPrimaryContainer = primary,
            secondary = primary, onSecondary = onPrimary,
            secondaryContainer = container, onSecondaryContainer = primary,
            tertiary = primary, tertiaryContainer = container,
            background = if (west) Color(0xFF181714) else Color(0xFF141715),
            surface = if (west) Color(0xFF1D1B17) else Color(0xFF191D1A),
            surfaceContainerLowest = if (west) Color(0xFF13120F) else Color(0xFF111412),
            surfaceContainerLow = if (west) Color(0xFF232019) else Color(0xFF1C211D),
            surfaceContainer = if (west) Color(0xFF28241E) else Color(0xFF232A24),
            surfaceContainerHigh = if (west) Color(0xFF322C22) else Color(0xFF2C332D),
            surfaceContainerHighest = if (west) Color(0xFF3D3528) else Color(0xFF353C36),
            onBackground = onSurface, onSurface = onSurface,
            onSurfaceVariant = if (west) Color(0xFFBCB3A3) else Color(0xFFB5BCB3),
            outline = if (west) Color(0xFF978B75) else Color(0xFF818A80),
            outlineVariant = if (west) Color(0xFF494132) else Color(0xFF39423A),
            surfaceTint = Color.Transparent,
        )
    }
    // West favorites reuse the previous control tint. The lighter surfaceContainer
    // keeps unselected periods and the revealed action rail exactly the same color.
    val container = if (west) Color(0xFFEFE8D9) else Color(0xFFE6EEE7)
    return lightColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = container, onPrimaryContainer = accent,
        secondary = accent, onSecondary = Color.White,
        secondaryContainer = container, onSecondaryContainer = accent,
        tertiary = accent, tertiaryContainer = container,
        background = YouRoomPaper, surface = YouRoomPaper,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = if (west) Color(0xFFF3EFE6) else Color(0xFFF2F2EC),
        surfaceContainer = if (west) Color(0xFFF4F0E7) else Color(0xFFEDEFE8),
        surfaceContainerHigh = if (west) Color(0xFFE7DDCA) else Color(0xFFE7EAE3),
        surfaceContainerHighest = if (west) Color(0xFFDED1B8) else Color(0xFFE1E5DD),
        onBackground = YouRoomInk, onSurface = YouRoomInk,
        onSurfaceVariant = if (west) Color(0xFF5D584F) else YouRoomMuted,
        outline = if (west) Color(0xFF91856D) else Color(0xFF858D84),
        outlineVariant = if (west) Color(0xFFDED8CC) else YouRoomRule,
        surfaceTint = Color.Transparent,
    )
}

@Composable
fun YouRoomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    campus: Campus = Campus.XITUCHENG,
    appearance: AppearancePreferences = LocalCampusAppearance.current,
    content: @Composable () -> Unit
) {
    // Each campus surface has a stable palette. Animate its layer, not MaterialTheme:
    // interpolating colors would invalidate every consumer on every transition frame.
    val tone = appearance.tone(campus)
    val colors = remember(campus, darkTheme, tone) { campusColorScheme(campus, darkTheme, tone) }
    CompositionLocalProvider(LocalCampusAppearance provides appearance) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = YouRoomShapes,
        content = content
    )
    }
}
