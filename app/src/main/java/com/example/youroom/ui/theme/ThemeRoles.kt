package com.example.youroom.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.youroom.domain.CampusTone

/** Functional color pairs keep a multi-hue theme independent of layout and behavior. */
data class ThemeRoles(
    val header: Color, val onHeader: Color, val headerMuted: Color,
    val navigation: Color, val navigationSelected: Color, val onNavigationSelected: Color, val navigationIdle: Color,
    val planningHeading: Color, val onPlanningHeading: Color, val planningHeadingMuted: Color, val planningHeadingKey: Color,
    val wheelCenter: Color, val favorite: Color, val note: Color, val settingsIcon: Color,
    val roomActions: Color, val onRoomActions: Color, val selectedRoomAction: Color,
    val filter: Color, val onFilter: Color,
    val launch: Color, val onLaunch: Color, val planningControl: Color, val enabledOption: Color, val onEnabledOption: Color,
    val impressionFrame: Color,
)

internal val LocalThemeRoles = staticCompositionLocalOf<ThemeRoles?> { null }

@Composable
fun themeRoles(): ThemeRoles = LocalThemeRoles.current ?: campusThemeRoles(MaterialTheme.colorScheme)

/** The fallback reproduces the original single-hue themes, including translucent wheel bands. */
fun campusThemeRoles(c: ColorScheme, tone: CampusTone? = null, dark: Boolean = false): ThemeRoles {
    val legacy = ThemeRoles(
        c.surface, c.onSurface, c.onSurfaceVariant,
        c.surface, c.primary, c.surface, c.primary,
        c.surfaceContainerLow, c.primary, c.onSurfaceVariant, c.primary,
        c.primary.copy(alpha=.045f), c.primary, c.onSurfaceVariant, c.primary,
        c.surfaceContainer, c.primary, c.primary,
        c.surfaceContainer, c.onSurface,
        c.primary, c.onPrimary, c.surface, c.primaryContainer, c.primary,
        c.surfaceContainerHighest.copy(alpha=.3f),
    )
    if (tone != CampusTone.MATISSE) return legacy
    val blue = Color(0xFF343E47)
    val pale = Color(0xFFE9EFE4)
    val yellow = c.tertiary
    return legacy.copy(
        header=blue, onHeader=pale, headerMuted=Color(0xFFC5D1CD),
        navigation=blue, navigationSelected=yellow, onNavigationSelected=c.onTertiary, navigationIdle=Color(0xFFC5D1CD),
        planningHeading=blue, onPlanningHeading=pale, planningHeadingMuted=Color(0xFFC5D1CD),
        planningHeadingKey=if(dark) Color(0xFFE9A095) else Color(0xFFAA3D36),
        // Intentionally grey-green, NOT the yellow-green accent used by enabled controls.
        wheelCenter=if(dark) Color(0xFF3A4B40) else Color(0xFFD8E0D6),
        favorite=c.secondary, note=c.secondary, settingsIcon=c.secondary,
        roomActions=blue, onRoomActions=pale, selectedRoomAction=yellow,
        filter=blue, onFilter=pale,
        launch=if(dark) Color(0xFFE9A095) else Color(0xFFAA3D36),
        onLaunch=if(dark) Color(0xFF321B18) else Color(0xFFFFF6EF),
        planningControl=c.surfaceContainer, enabledOption=yellow, onEnabledOption=c.onTertiary, impressionFrame=blue,
    )
}
