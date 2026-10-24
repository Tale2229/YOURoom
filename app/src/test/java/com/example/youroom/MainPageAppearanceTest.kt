package com.example.youroom

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.youroom.domain.Campus
import com.example.youroom.domain.CampusTone
import com.example.youroom.ui.mainPageRowColor
import com.example.youroom.ui.settingsFooterGapPx
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Assert.*
import org.junit.Test

class MainPageAppearanceTest {
    @Test fun paperBandsStayOpaqueAndPreserveEveryThemeInBothModes() {
        for (tone in CampusTone.entries) for (dark in listOf(false, true)) {
            val colors = campusColorScheme(Campus.XITUCHENG, dark, tone)
            assertNotEquals("$tone/$dark query vs heading", colors.surface, colors.surfaceContainerLow)
            assertEquals(colors.surface, mainPageRowColor(colors, false))
            assertEquals(lerp(colors.surface, colors.primary, .037f), mainPageRowColor(colors, true))
            for (paper in listOf(mainPageRowColor(colors, true), mainPageRowColor(colors, false))) {
                assertEquals(1f, paper.alpha, .001f)
                val ink = colors.onSurface.luminance()
                val background = paper.luminance()
                assertTrue("$tone/$dark readable", (maxOf(ink, background)+.05f)/(minOf(ink, background)+.05f)>=4.5f)
            }
        }
    }

    @Test fun footerOnlyFillsTheUnusedSpaceWithoutShrinkingRows() {
        assertEquals(172, settingsFooterGapPx(800, 20, 560, 48))
        assertEquals(0, settingsFooterGapPx(500, 20, 560, 48))
        assertEquals(0, settingsFooterGapPx(628, 20, 560, 48))
        for (viewport in listOf(500, 628, 800, 1200)) for (prefix in listOf(400, 560, 900)) {
            val gap = settingsFooterGapPx(viewport, 20, prefix, 48)
            assertTrue(20 + prefix + gap + 48 >= viewport)
            assertTrue(gap >= 0)
        }
    }
}
