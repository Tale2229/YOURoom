package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ThemeBeltUiTest {
    @get:Rule val compose=createComposeRule()
    private var appearance by mutableStateOf(AppearancePreferences())
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var enabled by mutableStateOf(true)
    private val changes=mutableListOf<Pair<Campus,CampusTone>>()
    private fun render()=compose.setContent {
        ThemeTransitionHost(campus,appearance,2,true,{ c,t ->
            changes+=c to t; appearance=appearance.withTone(c,t)
        }) { request -> Surface {
            SettingsScreen(YOURoomState(initializing=false,campus=campus,
                snapshot=DemoData.snapshot(campus),appearance=appearance),{},{},{},{ campus=it },
                interactive=enabled,onTone=request)
        } }
    }
    private fun belt()=compose.onNodeWithTag("settings_theme_belt")
    private fun position()=belt().fetchSemanticsNode().config[ThemeBeltPosition]
    private fun browsing()=belt().fetchSemanticsNode().config[ThemeBeltBrowsing]
    private fun wave()=compose.onNodeWithTag("theme_transition_page").fetchSemanticsNode().config[ThemePageWaveProgress]
    private fun shot(name: String) {
        compose.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync()
        val night=i.targetContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        File(i.targetContext.getExternalFilesDir(null),"$name${if(night) "-dark" else ""}.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun aSingleLongDragBrowsesSeveralPalettesWithoutWritingUntilTheFinalSnap() {
        render();compose.mainClock.autoAdvance=false
        val frame=compose.onNodeWithTag("settings_theme_frame").fetchSemanticsNode().boundsInRoot
        val below=compose.onNodeWithTag("settings_strip_layout").fetchSemanticsNode().boundsInRoot
        belt().performTouchInput {
            down(Offset(width*.86f,centerY))
            moveTo(Offset(width*.14f,centerY),400)
            advanceEventTime(200);moveBy(Offset.Zero)
        }
        compose.mainClock.advanceTimeBy(1000)
        assertTrue(browsing());assertTrue(position()>2f)
        assertEquals(1f,belt().fetchSemanticsNode().config[ThemeBeltReveal],.001f)
        compose.onNodeWithTag("settings_theme_next").assertDoesNotExist()
        compose.onNodeWithTag("settings_theme_previous").assertDoesNotExist()
        assertEquals(frame,compose.onNodeWithTag("settings_theme_frame").fetchSemanticsNode().boundsInRoot)
        assertEquals(below,compose.onNodeWithTag("settings_strip_layout").fetchSemanticsNode().boundsInRoot)
        assertEquals(Campus.XITUCHENG,campus);assertEquals(CampusTone.SAND,appearance.westTone)
        assertTrue(changes.isEmpty());assertEquals(1f,wave())
        val expected=CampusTone.entries[ThemeBeltGeometry.value(position(),CampusTone.entries.size)]
        compose.onNodeWithTag("settings_theme_name").assertTextEquals(expected.title)
        shot("theme-belt-depth-1.24.1")
        belt().performTouchInput { advanceEventTime(200);up() }
        compose.mainClock.autoAdvance=true
        compose.waitUntil(5000) { changes.size==1 }
        compose.waitForIdle()
        assertFalse(browsing());assertEquals(listOf(campus to expected),changes)
        assertEquals(expected,appearance.westTone)
        compose.onNodeWithTag("settings_theme_next").assertIsDisplayed().assertIsEnabled()
        assertEquals(frame,compose.onNodeWithTag("settings_theme_frame").fetchSemanticsNode().boundsInRoot)
        assertEquals(below,compose.onNodeWithTag("settings_strip_layout").fetchSemanticsNode().boundsInRoot)
        shot("theme-belt-settled-1.24.1")
    }

    @Test fun releaseHasInertiaAndATouchCanInterruptItWithoutPublishingAnIntermediateTheme() {
        render();compose.mainClock.autoAdvance=false
        belt().performTouchInput { swipe(Offset(width*.82f,centerY),Offset(width*.4f,centerY),80) }
        assertTrue(browsing());assertTrue(changes.isEmpty())
        val released=position()
        compose.mainClock.advanceTimeBy(128)
        assertTrue("Inertia continues after release",position()>released+.05f)
        assertTrue(changes.isEmpty())
        belt().performTouchInput { down(center);moveBy(Offset(40f,0f),60) }
        compose.mainClock.advanceTimeBy(500)
        assertTrue(browsing());assertTrue(changes.isEmpty());assertEquals(1f,wave())
        belt().performTouchInput { advanceEventTime(200);moveBy(Offset.Zero);up() }
        compose.mainClock.autoAdvance=true
        compose.waitUntil(5000) { !browsing() }
        compose.waitUntil(5000) {
            compose.onNodeWithTag("theme_transition_page").fetchSemanticsNode().config[ThemePendingTone].isEmpty() && wave()==1f
        }
        compose.waitForIdle()
        assertTrue(changes.size<=1)
        compose.onNodeWithTag("settings_theme_name").assertTextEquals(appearance.westTone.title)
        compose.onNodeWithTag("settings_theme_previous").assertIsDisplayed()
    }

    @Test fun startingADragPausesAnEarlierArrowRequestAndRestartsTheQuietDelayAfterSettling() {
        render();compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("settings_theme_next").performClick()
        compose.mainClock.advanceTimeBy(100)
        belt().performTouchInput { down(center);moveBy(Offset(-40f,0f),80) }
        compose.mainClock.advanceTimeBy(1000)
        assertTrue(browsing());assertEquals(CampusTone.SAND,appearance.westTone)
        assertTrue(changes.isEmpty());assertEquals(1f,wave())
        belt().performTouchInput { advanceEventTime(200);moveBy(Offset.Zero);up() }
        compose.mainClock.advanceTimeBy(256)
        assertTrue(changes.isEmpty())
        compose.mainClock.autoAdvance=true
        compose.waitUntil(5000) { changes.isNotEmpty() };compose.waitForIdle()
        assertEquals(listOf(Campus.XITUCHENG to CampusTone.PINE),changes)
    }

    @Test fun disablingWhileDraggingCancelsTheUnconfirmedChoiceAndKeepsBothCampusSettings() {
        render();compose.mainClock.autoAdvance=false
        belt().performTouchInput { down(Offset(width*.85f,centerY));moveTo(Offset(width*.2f,centerY),300) }
        compose.mainClock.advanceTimeBy(160);assertTrue(browsing())
        compose.runOnIdle { enabled=false }
        compose.mainClock.advanceTimeBy(1200)
        assertFalse(browsing());assertTrue(changes.isEmpty())
        assertEquals(AppearancePreferences(),appearance)
        belt().performTouchInput { up() }
        compose.runOnIdle { enabled=true }
        compose.mainClock.autoAdvance=true;compose.waitForIdle()
        compose.onNodeWithTag("settings_theme_next").assertIsEnabled()
        compose.onNodeWithTag("settings_theme_name").assertTextEquals("砂岩")
    }

    @Test fun verticalScrollingOverTheStripDoesNotStartThemeBrowsing() {
        render()
        belt().performTouchInput { swipe(center,center+Offset(0f,-180f),300) }
        compose.waitForIdle();assertTrue(changes.isEmpty())
        assertEquals(AppearancePreferences(),appearance);assertEquals(Campus.XITUCHENG,campus)
    }

    @Test fun switchingCampusDuringInertiaDoesNotCommitTheUnconfirmedToneToEitherCampus() {
        render();compose.mainClock.autoAdvance=false
        belt().performTouchInput { swipe(Offset(width*.82f,centerY),Offset(width*.4f,centerY),80) }
        compose.mainClock.advanceTimeBy(96);assertTrue(browsing())
        compose.runOnIdle { campus=Campus.SHAHE }
        compose.mainClock.advanceTimeBy(1600)
        assertTrue(changes.isEmpty());assertEquals(AppearancePreferences(),appearance)
        compose.onNodeWithTag("settings_theme_name").assertTextEquals("松绿")
        compose.onNodeWithTag("settings_theme_next").assertIsEnabled().performClick()
        compose.mainClock.autoAdvance=true
        compose.waitUntil(5000) { changes.size==1 };compose.waitForIdle()
        assertEquals(listOf(Campus.SHAHE to CampusTone.MATISSE),changes)
        assertEquals(CampusTone.SAND,appearance.westTone)
    }

    @Test fun settledSwipeStartsItsWaveAsSoonAsTheArrowsFinishReappearing() {
        render(); compose.mainClock.autoAdvance = false
        val stride = with(compose.density) { ThemeBeltGeometry.strideDp.dp.toPx() }
        belt().performTouchInput {
            down(center); moveBy(Offset(-stride, 0f), 400)
            advanceEventTime(200); moveBy(Offset.Zero); up()
        }
        compose.mainClock.advanceTimeBy(240)
        assertTrue(browsing()); assertTrue(changes.isEmpty())
        var elapsed = 0L
        while (browsing() && elapsed < 2000) {
            compose.mainClock.advanceTimeByFrame(); elapsed += 16L
        }
        assertFalse(browsing())
        assertTrue(changes.isEmpty())
        compose.mainClock.advanceTimeBy(ThemeBeltGeometry.revealMillis + 48L)
        // No extra 320ms theme debounce after the existing belt hold/reveal.
        compose.waitUntil(5000) { changes.size == 1 }
        assertEquals(listOf(Campus.XITUCHENG to CampusTone.PINE), changes)
        compose.mainClock.autoAdvance = true
    }

    @Test fun aSettledStripStaysOpenAndAnotherDragRestartsItsExitDelay() {
        render();compose.mainClock.autoAdvance=false
        val stride=with(compose.density) { ThemeBeltGeometry.strideDp.dp.toPx() }
        fun dragOneStep() {
            belt().performTouchInput {
                down(center);moveBy(Offset(-stride,0f),400)
                advanceEventTime(200);moveBy(Offset.Zero);up()
            }
        }
        dragOneStep()
        compose.mainClock.advanceTimeBy(240)
        assertTrue(browsing());assertTrue(changes.isEmpty())
        assertEquals(1f,position(),.01f)
        compose.onNodeWithTag("settings_theme_previous").assertDoesNotExist()
        compose.onNodeWithTag("settings_theme_next").assertDoesNotExist()
        shot("theme-belt-held-open-1.24.1")
        compose.mainClock.advanceTimeBy(ThemeBeltGeometry.holdMillis/2)
        assertTrue(browsing());assertTrue(changes.isEmpty())
        dragOneStep()
        compose.mainClock.advanceTimeBy(240+ThemeBeltGeometry.holdMillis/2)
        // The first deadline has passed, but the second drag gets its own delay.
        assertTrue(browsing());assertTrue(changes.isEmpty());assertEquals(2f,position(),.01f)
        compose.onNodeWithTag("settings_theme_next").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(ThemeBeltGeometry.holdMillis/2)
        assertFalse(browsing());assertTrue(changes.isEmpty())
        compose.mainClock.autoAdvance=true
        compose.waitUntil(5000) { changes.size==1 };compose.waitForIdle()
        assertEquals(listOf(Campus.XITUCHENG to CampusTone.MATISSE),changes)
        compose.onNodeWithTag("settings_theme_next").assertIsDisplayed().assertIsEnabled()
    }
}
