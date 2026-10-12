package com.example.youroom

import com.example.youroom.domain.Campus
import com.example.youroom.ui.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class CampusThemeTest {
    @Test fun campusSubtitleRetainsTheOriginalBodySmallTypography() {
        assertEquals(12.sp, Typography.bodySmall.fontSize)
        assertEquals(FontFamily.Default, Typography.bodySmall.fontFamily)
    }
    @Test fun lightThemesUseTheAgreedCampusColors() {
        assertEquals(0xFF263D59.toInt(), campusColorScheme(Campus.XITUCHENG, false).primary.toArgb())
        assertEquals(0xFF315C4E.toInt(), campusColorScheme(Campus.SHAHE, false).primary.toArgb())
    }

    @Test fun bothCampusesShareNeutralPaperAndTextColors() {
        val west = campusColorScheme(Campus.XITUCHENG, false)
        val shahe = campusColorScheme(Campus.SHAHE, false)
        assertEquals(west.surface, shahe.surface)
        assertEquals(west.onSurface, shahe.onSurface)
        assertEquals(west.outlineVariant, shahe.outlineVariant)
    }

    @Test fun primaryTextAndButtonsRetainReadableContrastInBothModes() {
        for (campus in Campus.entries) for (dark in listOf(false, true)) {
            val colors = campusColorScheme(campus, dark)
            assertTrue("$campus $dark primary", contrast(colors.primary, colors.onPrimary) >= 4.5)
            assertTrue("$campus $dark text", contrast(colors.surface, colors.onSurface) >= 4.5)
            assertTrue("$campus $dark secondary text", contrast(colors.surface, colors.onSurfaceVariant) >= 4.5)
            assertTrue("$campus $dark selected periods", contrast(colors.primaryContainer, colors.onPrimaryContainer) >= 4.5)
        }
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
}
