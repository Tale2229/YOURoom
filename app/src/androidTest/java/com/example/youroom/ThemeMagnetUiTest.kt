package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ThemeMagnetUiTest {
    @get:Rule val compose=createComposeRule()
    private var appearance by mutableStateOf(AppearancePreferences(darkMode=false))
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var enabled by mutableStateOf(true)
    private val writes=mutableListOf<CampusTone>()
    private fun render()=compose.setContent {
        ThemeTransitionHost(campus,appearance,2,true,{c,t->writes+=t;appearance=appearance.withTone(c,t)}) { request ->
            Surface { SettingsScreen(YOURoomState(initializing=false,campus=campus,
                snapshot=DemoData.snapshot(campus),appearance=appearance),{},{},{},
                interactive=enabled,onTone=request) }
        }
    }
    private fun frame()=compose.onNodeWithTag("settings_theme_frame")
    private fun belt()=compose.onNodeWithTag("settings_theme_belt")
    private fun position()=belt().fetchSemanticsNode().config[ThemeBeltPosition]
    private fun visualPosition()=belt().fetchSemanticsNode().config[ThemeBeltVisualPosition]
    private fun assertTone(tone: CampusTone, attached: Boolean = true) {
        frame().assert(SemanticsMatcher.expectValue(ThemeFrameTone,tone.id))
        frame().assert(SemanticsMatcher.expectValue(ThemeFrameAttached,attached))
    }
    private fun moveTo(target: Float) {
        val stride=with(compose.density){ThemeBeltGeometry.strideDp.dp.toPx()}
        val dx=(position()-target)*stride
        belt().performTouchInput { moveBy(Offset(dx,0f),80) }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(target,position(),.015f)
    }

    @Test fun detentPinsTheWholeStripUntilEscapeAndSnapsApproachingAvatarsIntoTheFrame() {
        render();compose.mainClock.autoAdvance=false
        val warmup=with(compose.density){25.dp.toPx()}
        belt().performTouchInput { down(center);moveBy(Offset(-warmup,0f),100) }
        compose.mainClock.advanceTimeBy(64)
        val stride=ThemeBeltGeometry.strideDp
        moveTo(22f/stride);assertTone(CampusTone.SAND);assertEquals(0f,visualPosition(),.002f)
        moveTo(36f/stride);assertTone(CampusTone.SAND);assertEquals(0f,visualPosition(),.002f)
        shot("locked-before-escape")
        moveTo(40f/stride);assertTone(CampusTone.SAND,attached=false);assertEquals(40f/stride,visualPosition(),.01f)
        moveTo(1f-24f/stride);assertTone(CampusTone.SAND,attached=false)
        moveTo(1f-22f/stride);assertTone(CampusTone.PINE);assertEquals(1f,visualPosition(),.002f)
        shot("direct-capture")
        moveTo(1f-36f/stride);assertTone(CampusTone.PINE);assertEquals(1f,visualPosition(),.002f)
        moveTo(1f-40f/stride);assertTone(CampusTone.PINE,attached=false);assertEquals(1f-40f/stride,visualPosition(),.01f)
        moveTo(24f/stride);assertTone(CampusTone.PINE,attached=false)
        moveTo(22f/stride);assertTone(CampusTone.SAND);assertEquals(0f,visualPosition(),.002f)
        assertTrue(writes.isEmpty())
        belt().performTouchInput { up() }
        compose.mainClock.autoAdvance=true;compose.waitForIdle()
        assertTrue(writes.isEmpty());assertEquals(CampusTone.SAND,appearance.westTone)
    }

    @Test fun aFastShortFlickInsideTheDetentCannotSwitchTheThemeAfterRelease() {
        render();compose.mainClock.autoAdvance=false
        val stride=with(compose.density){ThemeBeltGeometry.strideDp.dp.toPx()}
        belt().performTouchInput { down(center);moveBy(Offset(-stride*.20f,0f),32) }
        compose.mainClock.advanceTimeBy(32)
        assertTrue(position()<ThemeBeltMagnet.escapeRadius)
        assertTone(CampusTone.SAND)
        belt().performTouchInput { up() }
        compose.mainClock.autoAdvance=true;compose.waitForIdle()
        assertEquals(0f,position(),.01f);assertEquals(0f,visualPosition(),.002f)
        assertEquals(CampusTone.SAND,appearance.westTone);assertTrue(writes.isEmpty())
    }
    private fun shot(name: String) {
        val i=InstrumentationRegistry.getInstrumentation()
        File(i.targetContext.getExternalFilesDir(null),"magnet-$name.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun rapidArrowsChangeFrameBeforeTheAppliedPaletteAndCancelInterruptedFades() {
        render();compose.mainClock.autoAdvance=false
        repeat(3) { compose.onNodeWithTag("settings_theme_next").performClick();compose.mainClock.advanceTimeBy(64) }
        compose.mainClock.advanceTimeBy(176)
        assertTone(CampusTone.DUNHUANG)
        assertEquals(CampusTone.SAND,appearance.westTone);assertTrue(writes.isEmpty())
        assertEquals(1f,compose.onNodeWithTag("theme_transition_page").fetchSemanticsNode().config[ThemePageWaveProgress])
        assertThemeFrame(frame(),CampusTone.DUNHUANG,campusColorScheme(campus,false,CampusTone.SAND).surface,false)
        shot("arrow-preview-before-page")
        compose.mainClock.autoAdvance=true
        compose.waitUntil(5000){writes.size==1};compose.waitForIdle()
        assertEquals(listOf(CampusTone.DUNHUANG),writes);assertTone(CampusTone.DUNHUANG)
    }

    @Test fun gapsKeepLastColorUntilANewCaptureAndDoNotSaveBeforeRelease() {
        render();compose.mainClock.autoAdvance=false
        val warmup=with(compose.density){50.dp.toPx()}
        belt().performTouchInput { down(center);moveBy(Offset(-warmup,0f),100) }
        compose.mainClock.advanceTimeBy(32)
        moveTo(.45f);assertTone(CampusTone.SAND,attached=false)
        val colors=campusColorScheme(campus,false,CampusTone.SAND)
        assertThemeFrame(frame(),CampusTone.SAND,colors.surface,false,horizontalEdgesOnly=true)
        val image=frame().captureToImage().toPixelMap()
        val center=image[image.width/2,image.height/2]
        assertEquals(colors.surface.red,center.red,.02f)
        assertEquals(colors.surface.green,center.green,.02f)
        assertEquals(colors.surface.blue,center.blue,.02f)
        shot("retained-gap")
        moveTo(.86f);assertTone(CampusTone.PINE)
        assertThemeFrame(frame(),CampusTone.PINE,colors.surface,false,horizontalEdgesOnly=true)
        shot("captured-before-page")
        moveTo(.75f);assertTone(CampusTone.PINE)
        moveTo(.59f);assertTone(CampusTone.PINE,attached=false)
        moveTo(.70f);assertTone(CampusTone.PINE,attached=false)
        moveTo(.85f);assertTone(CampusTone.PINE)
        moveTo(1.45f);assertTone(CampusTone.PINE,attached=false)
        assertThemeFrame(frame(),CampusTone.PINE,colors.surface,false,horizontalEdgesOnly=true)
        moveTo(1.86f);assertTone(CampusTone.MATISSE)
        assertTrue(writes.isEmpty());assertEquals(CampusTone.SAND,appearance.westTone)
        moveTo(2f)
        belt().performTouchInput { advanceEventTime(200);moveBy(Offset.Zero);up() }
        compose.mainClock.autoAdvance=true
        compose.waitUntil(5000){writes.size==1};compose.waitForIdle()
        assertEquals(listOf(CampusTone.MATISSE),writes);assertTone(CampusTone.MATISSE)
    }

    @Test fun darkGapsRetainThePreviousThemeColorWithoutFillingTheSlot() {
        appearance=appearance.copy(darkMode=true)
        render();compose.mainClock.autoAdvance=false
        val warmup=with(compose.density){50.dp.toPx()}
        belt().performTouchInput { down(center);moveBy(Offset(-warmup,0f),100) }
        compose.mainClock.advanceTimeBy(32)
        moveTo(.45f);assertTone(CampusTone.SAND,attached=false)
        val colors=campusColorScheme(campus,true,CampusTone.SAND)
        assertThemeFrame(frame(),CampusTone.SAND,colors.surface,true,horizontalEdgesOnly=true)
        val image=frame().captureToImage().toPixelMap()
        val center=image[image.width/2,image.height/2]
        assertEquals(colors.surface.red,center.red,.02f);assertEquals(colors.surface.green,center.green,.02f)
        shot("retained-gap-dark")
        compose.runOnIdle{enabled=false}
        belt().performTouchInput{up()}
        compose.mainClock.autoAdvance=true;compose.waitForIdle()
        assertTone(CampusTone.SAND);assertTrue(writes.isEmpty())
    }

    @Test fun disablingOrChangingCampusClearsDetachedStateWithoutLeakingPreview() {
        render();compose.mainClock.autoAdvance=false
        val warmup=with(compose.density){50.dp.toPx()}
        belt().performTouchInput { down(center);moveBy(Offset(-warmup,0f),100) }
        compose.mainClock.advanceTimeBy(32)
        moveTo(.45f);assertTone(CampusTone.SAND,attached=false)
        compose.runOnIdle { enabled=false }
        compose.mainClock.advanceTimeBy(400);assertTone(CampusTone.SAND)
        belt().performTouchInput { up() }
        compose.runOnIdle { campus=Campus.SHAHE;enabled=true }
        compose.mainClock.advanceTimeBy(400);assertTone(CampusTone.PINE)
        compose.onNodeWithTag("settings_theme_next").assertIsEnabled()
        assertTrue(writes.isEmpty());assertEquals(CampusTone.SAND,appearance.westTone)
        compose.mainClock.autoAdvance=true
    }

    @Test fun foregroundFrameCoversThePassingAvatarAndWholeBeltUsesOnePosition() {
        appearance=appearance.copy(westTone=CampusTone.MATISSE)
        render();compose.mainClock.autoAdvance=false
        val warmup=with(compose.density){50.dp.toPx()}
        belt().performTouchInput { down(center);moveBy(Offset(-warmup,0f),100) }
        compose.mainClock.advanceTimeBy(32)
        moveTo(.45f);assertTone(CampusTone.MATISSE,attached=false)
        assertEquals(ThemeBeltMagnet.position(position(),null),visualPosition(),.002f)
        val pixels=frame().captureToImage().toPixelMap()
        val covered=pixels[5*pixels.width/95,pixels.height/2]
        val expected=themeAvatarFrameColor(campus,CampusTone.MATISSE,false)
        assertEquals(1f,expected.alpha,0f)
        assertEquals(expected.red,covered.red,.02f);assertEquals(expected.green,covered.green,.02f)
        assertEquals(expected.blue,covered.blue,.02f)
        shot("foreground-frame")
        compose.runOnIdle { enabled=false }
        belt().performTouchInput { up() }
        compose.mainClock.autoAdvance=true;compose.waitForIdle()
        assertTrue(writes.isEmpty())
    }
}
