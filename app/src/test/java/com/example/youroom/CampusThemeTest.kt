package com.example.youroom

import com.example.youroom.domain.Campus
import com.example.youroom.domain.CampusTone
import com.example.youroom.ui.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class CampusThemeTest {
    @Test fun sandInkIsWarmAndDarkerThanItsAccentInsteadOfNeutralBlack() {
        val colors=campusColorScheme(Campus.XITUCHENG,false)
        assertTrue(colors.onSurface.red>colors.onSurface.green && colors.onSurface.green>colors.onSurface.blue)
        assertTrue(colors.onSurface.luminance()<colors.primary.luminance())
        assertEquals(0xFF352D20.toInt(),colors.onSurface.toArgb())
    }
    @Test fun everySelectableToneKeepsReadableButtonsTextAndBadges() {
        for (tone in CampusTone.entries) for (dark in listOf(false, true)) for (campus in Campus.entries) {
            val c = campusColorScheme(campus, dark, tone)
            assertTrue("$tone $dark button", contrast(c.primary, c.onPrimary) >= 4.5)
            assertTrue("$tone $dark text", contrast(c.surface, c.onSurface) >= 4.5)
            assertTrue("$tone $dark secondary", contrast(c.surface, c.onSurfaceVariant) >= 4.5)
            assertTrue("$tone $dark selected", contrast(c.primaryContainer, c.onPrimaryContainer) >= 4.5)
            assertTrue("$tone $dark controls", contrast(c.surfaceContainer, c.primary) >= 4.5)
            assertTrue("$tone badge", contrast(campusBadgeColor(campus, tone), Color.White) >= 4.5)
        }
    }
    @Test fun campusSubtitleRetainsTheOriginalBodySmallTypography() {
        assertEquals(12.sp, Typography.bodySmall.fontSize)
        assertEquals(FontFamily.Default, Typography.bodySmall.fontFamily)
    }
    @Test fun lightThemesUseTheAgreedCampusColors() {
        assertEquals(0xFF665637.toInt(), campusColorScheme(Campus.XITUCHENG, false).primary.toArgb())
        assertEquals(0xFF315C4E.toInt(), campusColorScheme(Campus.SHAHE, false).primary.toArgb())
    }

    @Test fun allTonesHaveTheirOwnPaperInkMutedRulesAndSurfaces() {
        val west = campusColorScheme(Campus.XITUCHENG, false)
        val shahe = campusColorScheme(Campus.SHAHE, false)
        assertNotEquals(west.surface, shahe.surface)
        assertNotEquals(west.onSurface, shahe.onSurface)
        for (dark in listOf(false, true)) {
            val all = CampusTone.entries.map { campusColorScheme(Campus.XITUCHENG, dark, it) }
            listOf<(androidx.compose.material3.ColorScheme) -> Color>({ it.background }, { it.surface },
                { it.onSurface }, { it.onSurfaceVariant }, { it.outlineVariant }, { it.surfaceContainer }).forEach { role ->
                assertEquals(CampusTone.entries.size, all.map(role).distinct().size)
            }
            all.forEach { c ->
                assertTrue(contrast(c.inverseSurface, c.inverseOnSurface) >= 4.5)
                assertTrue(contrast(c.errorContainer, c.onErrorContainer) >= 4.5)
                assertTrue(contrast(c.primaryFixed, c.onPrimaryFixed) >= 4.5)
                assertTrue(contrast(c.tertiaryContainer, c.onTertiaryContainer) >= 4.5)
            }
        }
    }

    @Test fun primaryTextAndButtonsRetainReadableContrastInBothModes() {
        for (campus in Campus.entries) for (dark in listOf(false, true)) {
            val colors = campusColorScheme(campus, dark)
            assertTrue("$campus $dark primary", contrast(colors.primary, colors.onPrimary) >= 4.5)
            assertTrue("$campus $dark text", contrast(colors.surface, colors.onSurface) >= 4.5)
            assertTrue("$campus $dark secondary text", contrast(colors.surface, colors.onSurfaceVariant) >= 4.5)
            assertTrue("$campus $dark selected periods", contrast(colors.primaryContainer, colors.onPrimaryContainer) >= 4.5)
            assertTrue("$campus $dark period/action surface", contrast(colors.surfaceContainer, colors.onSurface) >= 4.5)
            assertTrue("$campus $dark action icons", contrast(colors.surfaceContainer, colors.primary) >= 4.5)
        }
    }

    @Test fun periodAndActionSurfacesAreWarmKhakiForWestAndGreenForShahe() {
        for (dark in listOf(false, true)) {
            val west = campusColorScheme(Campus.XITUCHENG, dark)
            val shahe = campusColorScheme(Campus.SHAHE, dark)
            for (color in listOf(west.surfaceContainerLow, west.surfaceContainer, west.surfaceContainerHigh, west.surfaceContainerHighest)) {
                assertTrue("West must be warm khaki, not blue", color.red > color.green && color.green > color.blue)
            }
            for (color in listOf(shahe.surfaceContainerLow, shahe.surfaceContainer, shahe.surfaceContainerHigh, shahe.surfaceContainerHighest)) {
                assertTrue("Shahe keeps its green/olive surfaces", color.green >= color.red && color.green > color.blue)
            }
            assertNotEquals(west.surfaceContainer, shahe.surfaceContainer)
        }
    }

    @Test fun westFavoriteAndActionRailRemainVisiblyDistinctWithReadableText() {
        for (dark in listOf(false, true)) {
            val colors = campusColorScheme(Campus.XITUCHENG, dark)
            // Both daytime backgrounds are intentionally very pale. Exact color
            // checks below protect the requested hierarchy without deepening it.
            assertTrue("Favorite/rail separation", contrast(colors.primaryContainer, colors.surfaceContainer) >= if (dark) 1.2 else 1.05)
            assertTrue("Favorite title", contrast(colors.primaryContainer, colors.onSurface) >= 4.5)
            assertTrue("Favorite metadata", contrast(colors.primaryContainer, colors.onSurfaceVariant) >= 4.5)
        }
    }

    @Test fun westFavoritesReusePreviousControlTintAndControlsBecomeLighter() {
        val light = campusColorScheme(Campus.XITUCHENG, false)
        assertEquals(0xFFEFE8D9.toInt(), light.primaryContainer.toArgb())
        assertTrue(light.primaryContainer.luminance() > Color(0xFFE6DDCB).luminance())
        assertTrue(light.primaryContainer.luminance() < light.surfaceContainer.luminance())
        assertEquals(0xFF665637.toInt(), light.primary.toArgb())
        assertEquals(0xFFF4F0E7.toInt(), light.surfaceContainer.toArgb())
        assertEquals(0xFFF7F6F2.toInt(), light.background.toArgb())
        assertEquals(0xFF453B28.toInt(), campusColorScheme(Campus.XITUCHENG, true).primaryContainer.toArgb())
        assertEquals(0xFF28241E.toInt(), campusColorScheme(Campus.XITUCHENG, true).surfaceContainer.toArgb())
    }

    @Test fun pineUsesGreenPaperInBothModesAndRetainsItsPrimaryHue() {
        val light = campusColorScheme(Campus.SHAHE, false)
        val dark = campusColorScheme(Campus.SHAHE, true)
        val expected = listOf(
            light.primary to 0xFF315C4E, light.primaryContainer to 0xFFDFEADF,
            light.background to 0xFFF0F5EF, light.surface to 0xFFF0F5EF,
            light.surfaceContainer to 0xFFE7EFE4, light.onSurfaceVariant to 0xFF526555,
            light.outlineVariant to 0xFFD2DFD0, campusBadgeColor(Campus.SHAHE) to 0xFF203F34,
            dark.primary to 0xFFA8D2BD, dark.primaryContainer to 0xFF254639,
            dark.background to 0xFF121A16, dark.surface to 0xFF17211B,
            dark.surfaceContainer to 0xFF203026, dark.onSurfaceVariant to 0xFFADBDAD,
            dark.outlineVariant to 0xFF35473B)
        expected.forEach { (color, argb) -> assertEquals(argb.toInt(), color.toArgb()) }
    }

    private fun contrast(a: Color, b: Color): Double {
        fun luminance(color: Color): Double {
            fun channel(value: Float): Double = value.toDouble().let {
                if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4)
            }
            return channel(color.red) * .2126 + channel(color.green) * .7152 + channel(color.blue) * .0722
        }
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }

    @Test fun darkCampusBadgeHasReadableWhiteNumbers() {
        Campus.entries.forEach { assertTrue(contrast(campusBadgeColor(it), Color.White) >= 4.5) }
    }
}
