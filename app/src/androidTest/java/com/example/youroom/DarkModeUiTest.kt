package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class DarkModeUiTest {
    @get:Rule val compose = createComposeRule()
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var appearance by mutableStateOf(AppearancePreferences(CampusTone.MATISSE, CampusTone.MONDRIAN, darkMode = false))
    private val writes = mutableListOf<Boolean>()
    private val tones = mutableListOf<CampusTone>()
    private fun render() = compose.setContent {
        ThemeTransitionHost(campus, appearance, 2, true, { c, tone ->
            tones += tone; appearance = appearance.withTone(c, tone)
        }) { request -> Surface {
            Column {
                Box(Modifier.weight(1f)) {
                    SettingsScreen(YOURoomState(initializing = false, campus = campus,
                        snapshot = DemoData.snapshot(campus), appearance = appearance), {}, {}, {},
                        onCampus = { campus = it }, onTone = request,
                        onDarkMode = { writes += it; appearance = appearance.copy(darkMode = it) })
                }
                WorkspaceNavigation(2, {})
            }
        } }
    }
    private fun assertPaint(tag: String, expected: Color) {
        // The header pager keeps the other campus precomposed. Its semantics
        // are intentionally cleared in the merged tree, not the unmerged one.
        val image = compose.onNodeWithTag(tag, useUnmergedTree = tag != "campus_header").captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until image.width step 2) for (y in 0 until image.height step 2) {
            val c = image[x,y]
            if (abs(c.red-expected.red)+abs(c.green-expected.green)+abs(c.blue-expected.blue) < .025f) count++
        }
        assertTrue("$tag must use $expected", count > 10)
    }
    private fun assertCampusColors(dark: Boolean) {
        val tone = appearance.tone(campus)
        val c = campusColorScheme(campus, dark, tone)
        val r = campusThemeRoles(c, tone, dark)
        assertPaint("settings_campus_body_content_${campus.dataKey}", c.surface)
        assertPaint("campus_header", r.header)
        assertPaint("tab_settings_face", r.navigationSelected)
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        File(i.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun darkModeIsTheFirstSettingAndReusesTheNumberSwitchsSize() {
        render()
        compose.onNodeWithTag("settings_dark_mode_switch").assertIsOff().assertIsEnabled().assertWidthIsEqualTo(48.dp)
        val dark = compose.onNodeWithTag("settings_dark_mode").fetchSemanticsNode().boundsInRoot
        val strip = compose.onNodeWithTag("settings_strip_layout").fetchSemanticsNode().boundsInRoot
        assertTrue(dark.bottom <= strip.top)
        for (part in listOf("face", "track", "thumb")) {
            val a = compose.onNodeWithTag("settings_dark_mode_switch_$part", true).fetchSemanticsNode().boundsInRoot
            val b = compose.onNodeWithTag("settings_strip_numbers_switch_$part", true).fetchSemanticsNode().boundsInRoot
            assertEquals(a.width, b.width, .1f); assertEquals(a.height, b.height, .1f)
        }
    }

    @Test fun rowAndSwitchBothToggleAndManualModeOverridesTheSystemAcrossCampuses() {
        render(); assertCampusColors(false)
        compose.onNodeWithTag("settings_dark_mode_switch").performClick().assertIsOn()
        assertEquals(listOf(true), writes); assertCampusColors(true)
        shot("dark-mode-settings-west")
        compose.runOnIdle { campus = Campus.SHAHE }
        compose.onNodeWithTag("settings_dark_mode_switch").assertIsOn()
        assertCampusColors(true)
        compose.onNodeWithTag("settings_dark_mode").performClick()
        compose.onNodeWithTag("settings_dark_mode_switch").assertIsOff()
        assertEquals(listOf(true, false), writes); assertCampusColors(false)
        assertEquals(CampusTone.MATISSE, appearance.westTone)
        assertEquals(CampusTone.MONDRIAN, appearance.shaheTone)
    }

    @Test fun everyThemeUsesItsOwnDarkPaletteInTheHeaderBodyAndNavigation() {
        render()
        for (c in Campus.entries) for (tone in CampusTone.entries) {
            compose.runOnIdle { campus = c; appearance = appearance.withTone(c, tone).copy(darkMode = true) }
            compose.onNodeWithTag("settings_dark_mode_switch").assertIsOn()
            assertCampusColors(true)
        }
        assertTrue(writes.isEmpty()); assertTrue(tones.isEmpty())
    }

    @Test fun changingModeFlushesThePendingToneButDoesNotReplayItsWave() {
        render(); compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("settings_theme_next").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("settings_dark_mode_switch").performClick()
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(true, appearance.darkMode)
        assertEquals(CampusTone.DUNHUANG, appearance.westTone)
        assertEquals(listOf(CampusTone.DUNHUANG), tones)
        assertEquals(1f, compose.onNodeWithTag("theme_transition_page").fetchSemanticsNode().config[ThemePageWaveProgress])
        assertEquals("", compose.onNodeWithTag("theme_transition_page").fetchSemanticsNode().config[ThemePendingTone])
        compose.mainClock.autoAdvance = true
        assertCampusColors(true)
    }

    @Test fun dataStoreReopenRestoresBothExplicitModesWithoutChangingOtherSettings() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (dark in listOf(true, false)) {
            val path = File(context.cacheDir, "dark-mode-${System.nanoTime()}.preferences_pb")
            val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val raw = PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { path })
                raw.edit { it[SharedFilterPreferences.favorites] = true; it[PeriodStripPreferenceKeys.numbers] = true }
                val expected = AppearancePreferences(CampusTone.MATISSE, CampusTone.MONDRIAN, Campus.SHAHE, dark)
                SettingsStore(raw).saveAppearance(expected)
                firstScope.coroutineContext[Job]!!.cancelAndJoin()
                val reopened = SettingsStore(PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { path }))
                assertEquals(expected, reopened.appearance.first())
                assertTrue(reopened.filters.first().favoritesOnly)
                assertTrue(reopened.periodStrip.first().showNumbers)
            } finally {
                firstScope.coroutineContext[Job]!!.cancelAndJoin()
                secondScope.coroutineContext[Job]!!.cancelAndJoin()
                path.delete()
            }
        }
    }
}
