package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
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

class ThemeSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var appearance by mutableStateOf(AppearancePreferences())
    private var fontScale by mutableFloatStateOf(1f)
    private val changes = mutableListOf<Pair<Campus, CampusTone>>()
    private fun render() = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            YouRoomTheme(campus = campus, appearance = appearance) { Surface {
                Column {
                    Box(Modifier.weight(1f)) {
                        SettingsScreen(YouRoomState(initializing = false, campus = campus,
                            snapshot = DemoData.snapshot(campus), appearance = appearance), {}, {}, {}, { campus = it },
                            onTone = { c, tone -> changes += c to tone; appearance = appearance.withTone(c, tone) },
                            onDefaultCampus = { appearance = appearance.copy(defaultCampus = it) })
                    }
                    WorkspaceNavigation(2, {})
                }
            } }
        }
    }
    private fun progress() = compose.onNodeWithTag("settings_theme_impression").fetchSemanticsNode().config[ThemeMosaicProgress]
    private fun shot(name: String) {
        val i = InstrumentationRegistry.getInstrumentation()
        i.waitForIdleSync()
        val night = i.targetContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        File(i.targetContext.getExternalFilesDir(null), "$name${if(night) "-dark" else ""}.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun campusChoicesStayIndependentAndDefaultSelectorDoesNotChangeCurrentCampus() {
        render()
        compose.onNodeWithTag("settings_theme_next").assertWidthIsEqualTo(48.dp).performClick()
        assertEquals(CampusTone.PINE, appearance.westTone)
        assertEquals(CampusTone.PINE, appearance.shaheTone)
        compose.onNodeWithTag("settings_theme_next").performClick()
        assertEquals(CampusTone.SLATE, appearance.westTone)
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        assertEquals(Campus.SHAHE, campus)
        compose.onNodeWithTag("settings_theme_previous").performClick()
        assertEquals(CampusTone.SAND, appearance.shaheTone)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_default_04"))
        compose.onNodeWithTag("settings_default_04").performClick().assertIsSelected()
        assertEquals(Campus.SHAHE, campus)
        compose.onNodeWithTag("settings_default_01").performClick().assertIsSelected()
        assertEquals(Campus.SHAHE, campus)
        assertEquals(CampusTone.SLATE, appearance.westTone)
        assertEquals(3, changes.size)
    }

    @Test fun waveStartsOnlyOnColorChangeAndRapidChangesRetargetWithoutAQueue() {
        appearance = AppearancePreferences(westTone = CampusTone.CLAY)
        render()
        assertEquals(1f, progress())
        val initial = compose.onNodeWithTag("settings_theme_impression").captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("settings_theme_next").performClick()
        compose.mainClock.advanceTimeBy(160)
        assertTrue(progress() in .01f.. .99f)
        val middle = compose.onNodeWithTag("settings_theme_impression").captureToImage().toPixelMap()
        assertTrue((0 until initial.width).any { x -> (0 until initial.height).any { y -> initial[x,y] != middle[x,y] } })
        shot("settings-wave-1.19")
        compose.onNodeWithTag("settings_theme_next").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("settings_theme_previous").performClick()
        compose.mainClock.advanceTimeBy(640)
        assertEquals(1f, progress())
        assertEquals(CampusTone.SAND, appearance.westTone)
        compose.onNodeWithTag("settings_theme_impression").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "砂岩"))
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { campus = Campus.SHAHE }
        assertEquals(1f, progress())
        compose.runOnIdle { campus = Campus.XITUCHENG }
        assertEquals(1f, progress())
    }

    @Test fun artworkCanSwipeBothWaysAndAllPalettesFitWithoutClippingAtLargeFonts() {
        render()
        compose.onNodeWithTag("settings_theme_impression").performTouchInput { swipeLeft() }
        assertEquals(CampusTone.PINE, appearance.westTone)
        compose.onNodeWithTag("settings_theme_impression").performTouchInput { swipeRight() }
        assertEquals(CampusTone.SAND, appearance.westTone)
        for (c in Campus.entries) for (tone in CampusTone.entries) {
            compose.runOnIdle { campus = c; appearance = appearance.withTone(c, tone) }
            compose.waitForIdle()
            compose.onNodeWithTag("settings_theme_impression").assertIsDisplayed()
            compose.onNodeWithTag("settings_theme_previous").assertIsDisplayed()
            compose.onNodeWithTag("settings_theme_next").assertIsDisplayed()
            shot("settings-${c.dataKey}-${tone.id}-1.19")
        }
        compose.runOnIdle { fontScale = 1.5f }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_default_04"))
        compose.onNodeWithTag("settings_default_04").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_logout"))
        compose.onNodeWithTag("settings_logout").assertIsDisplayed()
        shot("settings-large-text-1.19")
    }

    @Test fun realDataStoreRestoresBothPalettesAndDefaultCampusWithoutTouchingMarksOrFilters() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = File(context.cacheDir, "appearance-test-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val raw = PreferenceDataStoreFactory.create(scope = scope, produceFile = { path })
            val store = SettingsStore(raw)
            assertEquals(AppearancePreferences(), store.appearance.first())
            raw.edit { it[SharedFilterPreferences.favorites] = true; it[PeriodStripPreferenceKeys.numbers] = true }
            val choice = AppearancePreferences(CampusTone.CLAY, CampusTone.SLATE, Campus.SHAHE)
            store.saveAppearance(choice)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            val reopened = SettingsStore(PreferenceDataStoreFactory.create(scope = reopenedScope, produceFile = { path }))
            assertEquals(choice, reopened.appearance.first())
            assertTrue(reopened.filters.first().favoritesOnly)
            assertTrue(reopened.periodStrip.first().showNumbers)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            reopenedScope.coroutineContext[Job]!!.cancelAndJoin()
            path.delete()
        }
    }
}
