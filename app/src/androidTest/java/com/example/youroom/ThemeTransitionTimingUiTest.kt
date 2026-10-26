package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class ThemeTransitionTimingUiTest {
    @get:Rule val compose = createComposeRule()
    private var appearance by mutableStateOf(AppearancePreferences())
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var headerMoving by mutableStateOf(false)
    private val changes = mutableListOf<CampusTone>()
    private val night get() = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun renderWave() = compose.setContent {
        ThemeTransitionHost(campus, appearance, 2, true, { c, tone ->
            changes += tone
            appearance = appearance.withTone(c, tone)
        }) { request -> Surface {
            Column {
                Box(Modifier.weight(1f)) {
                    SettingsScreen(YOURoomState(initializing = false, campus = campus,
                        snapshot = DemoData.snapshot(campus), appearance = appearance,
                        periodStrip = PeriodStripPreferences(showNumbers = true)), {}, {}, {},
                        showHeader = false, sharedHeaderMoving = headerMoving, onTone = request)
                }
                WorkspaceNavigation(2, {})
            }
        } }
    }
    private fun progress() = compose.onNodeWithTag("theme_transition_page").fetchSemanticsNode().config[ThemePageWaveProgress]
    private fun assertPaint(tag: String, expected: Color) {
        val pixels = compose.onNodeWithTag(tag, true).captureToImage().toPixelMap()
        val count = (0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y ->
            val p = pixels[x,y]
            abs(p.red-expected.red)+abs(p.green-expected.green)+abs(p.blue-expected.blue) < .025f
        } }
        assertTrue("$tag should already use $expected", count > 10)
    }

    @Test fun paletteChangesSnapLocalColorTweensInTheFirstFrameInEveryThemeAndMode() {
        var dark by mutableStateOf(false)
        compose.setContent {
            YOURoomTheme(darkTheme = dark, appearance = appearance) { Surface {
                Column {
                    CompactSettingsSwitch(true, true, {}, Modifier.testTag("switch"))
                    WorkspaceNavigation(2, {})
                }
            } }
        }
        compose.mainClock.autoAdvance = false
        for (mode in listOf(false, true)) for (tone in CampusTone.entries) {
            compose.runOnIdle { dark = mode; appearance = appearance.withTone(Campus.XITUCHENG, tone) }
            compose.mainClock.advanceTimeByFrame()
            val c = campusColorScheme(Campus.XITUCHENG, mode, tone)
            val r = campusThemeRoles(c, tone, mode)
            assertPaint("tab_settings_face", r.navigationSelected)
            assertPaint("tab_settings_icon", r.onNavigationSelected)
            assertPaint("tab_rooms_face", r.navigation)
            assertPaint("settings_strip_numbers_switch_track", r.switchTrackOn ?: c.primary)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun ordinarySwitchAndNavigationStateChangesStillAnimate() {
        var checked by mutableStateOf(false)
        var tab by mutableIntStateOf(0)
        val c = campusColorScheme(Campus.XITUCHENG, false, CampusTone.SAND)
        compose.setContent { YOURoomTheme(darkTheme = false) { Surface {
            Column {
                CompactSettingsSwitch(checked, true, { checked = it }, Modifier.testTag("switch"))
                WorkspaceNavigation(tab, { tab = it })
            }
        } } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("switch").performClick()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.mainClock.advanceTimeBy(64)
        val track = compose.onNodeWithTag("settings_strip_numbers_switch_track", true).captureToImage().toPixelMap()
        assertNotEquals(c.primary, track[track.width/2, track.height/2])
        compose.mainClock.advanceTimeBy(224)
        assertPaint("settings_strip_numbers_switch_track", c.primary)
        assertPaint("tab_settings_face", c.primary)
        compose.mainClock.autoAdvance = true
    }

    @Test fun lastWaveFrameAlreadyContainsFinalNavigationAndSwitchColors() {
        renderWave()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("settings_theme_next").performClick()
        compose.mainClock.advanceTimeBy(352)
        compose.waitUntil(5000) { changes.size == 1 }
        compose.waitUntil(5000) {
            compose.mainClock.advanceTimeByFrame()
            progress() in .0001f.. .99f
        }
        compose.mainClock.advanceTimeBy(704)
        assertTrue(progress() in .90f.. .999f)
        val c = campusColorScheme(Campus.XITUCHENG, night, CampusTone.PINE)
        val r = campusThemeRoles(c, CampusTone.PINE, night)
        val root = compose.onNodeWithTag("theme_transition_page")
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val frame = root.captureToImage().toPixelMap()
        for ((tag, color) in listOf("tab_settings_face" to r.navigationSelected,
            "tab_settings_icon" to r.onNavigationSelected,
            "settings_strip_numbers_switch_track" to (r.switchTrackOn ?: c.primary))) {
            val bounds = compose.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
            var count = 0
            for (x in (bounds.left-origin.x).roundToInt().coerceAtLeast(0) until (bounds.right-origin.x).roundToInt().coerceAtMost(frame.width))
                for (y in (bounds.top-origin.y).roundToInt().coerceAtLeast(0) until (bounds.bottom-origin.y).roundToInt().coerceAtMost(frame.height)) {
                    val p = frame[x,y]
                    if (abs(p.red-color.red)+abs(p.green-color.green)+abs(p.blue-color.blue) < .025f) count++
                }
            assertTrue("Wave screenshot must contain final $tag color, not the old tween", count > 10)
        }
        compose.mainClock.advanceTimeBy(128)
        assertEquals(1f, progress())
        assertPaint("tab_settings_face", r.navigationSelected)
        assertPaint("settings_strip_numbers_switch_track", r.switchTrackOn ?: c.primary)
        compose.mainClock.autoAdvance = true
    }

    @Test fun settingsUsesCurrentCampusAndEnabledActionsWithoutWaitingForTheHeader() {
        renderWave()
        compose.mainClock.autoAdvance = false
        for (target in listOf(Campus.SHAHE, Campus.XITUCHENG)) {
            compose.runOnIdle { campus = target; headerMoving = true }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("settings_theme_previous").assertIsEnabled()
            compose.onNodeWithTag("settings_theme_next").assertIsEnabled()
            compose.onNodeWithTag("settings_theme_name").assertTextEquals(appearance.tone(target).title)
            compose.onNodeWithTag("settings_logout").assertIsEnabled().performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithText("返回登录页？").assertExists()
            compose.onNodeWithText("取消").performClick()
            compose.mainClock.advanceTimeBy(64)
        }
        compose.mainClock.autoAdvance = true
    }
}
