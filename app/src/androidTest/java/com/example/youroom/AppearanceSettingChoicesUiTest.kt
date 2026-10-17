package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppearanceSettingChoicesUiTest {
    @get:Rule val compose = createComposeRule()
    private var strip by mutableStateOf(PeriodStripPreferences())
    private var appearance by mutableStateOf(AppearancePreferences())
    private var enabled by mutableStateOf(true)
    private var fontScale by mutableFloatStateOf(1f)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var writes = 0
    private fun render() = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            YouRoomTheme(campus = campus, appearance = appearance) { Surface {
                Box(Modifier.widthIn(max = 320.dp).fillMaxHeight()) {
                    SettingsScreen(YouRoomState(initializing = false, campus = campus, snapshot = DemoData.snapshot(campus),
                        periodStrip = strip, appearance = appearance), {}, {}, {}, showHeader = false, interactive = enabled,
                        onPeriodStrip = { strip = it; writes++ }, onDefaultCampus = { appearance = appearance.copy(defaultCampus = it); writes++ })
                }
            } }
        }
    }
    private fun rotation(tag: String) = compose.onNodeWithTag("${tag}_arrow", true).fetchSemanticsNode().config[SettingsDisclosureRotation]
    private fun shot(name: String) {
        val i = InstrumentationRegistry.getInstrumentation(); i.waitForIdleSync()
        val night = i.targetContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        File(i.targetContext.getExternalFilesDir(null), "$name${if(night) "-dark" else ""}.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun previewUses75dpArtworkAndAUniform10dpRoundedFrame() {
        render()
        val image = compose.onNodeWithTag("settings_theme_impression").assertWidthIsEqualTo(75.dp).assertHeightIsEqualTo(75.dp)
            .fetchSemanticsNode().boundsInRoot
        val frame = compose.onNodeWithTag("settings_theme_frame").assertWidthIsEqualTo(95.dp).assertHeightIsEqualTo(95.dp)
            .fetchSemanticsNode().boundsInRoot
        val thickness = with(compose.density) { 10.dp.toPx() }
        assertEquals(thickness, image.left-frame.left, 1f); assertEquals(thickness, frame.right-image.right, 1f)
        assertEquals(thickness, image.top-frame.top, 1f); assertEquals(thickness, frame.bottom-image.bottom, 1f)
        val pixels = compose.onNodeWithTag("settings_theme_frame").captureToImage().toPixelMap()
        assertNotEquals(pixels[0,0], pixels[5,pixels.height/2])
        shot("settings-compact-collapsed-1.20.2")
    }

    @Test fun groupsStartCollapsedAreOrderedBeforeNumbersAndAllSettingTitlesUseTheSameFont() {
        render()
        compose.onNodeWithTag("settings_strip_EQUAL").assertDoesNotExist()
        compose.onNodeWithTag("settings_default_01").assertDoesNotExist()
        val style = compose.onNodeWithTag("settings_strip_layout").fetchSemanticsNode().boundsInRoot
        val default = compose.onNodeWithTag("settings_default_campus").fetchSemanticsNode().boundsInRoot
        val numbers = compose.onNodeWithTag("settings_strip_numbers").fetchSemanticsNode().boundsInRoot
        assertTrue(style.bottom <= default.top); assertTrue(default.bottom <= numbers.top)
        for (title in listOf("时段条样式", "默认校区", "节次编号", "教室标记", "规划偏好", "关于")) {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(title, useUnmergedTree = true).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(14.sp, results.single().layoutInput.style.fontSize)
            assertEquals(Typography.bodyMedium.fontWeight, results.single().layoutInput.style.fontWeight)
        }
        assertEquals(0, writes)
    }

    @Test fun dropdownsShowTwoVerticalRowsWithRightAlignedCircleSelectionAndLiveUpdates() {
        render()
        compose.onNodeWithTag("settings_strip_layout").performClick()
        val equal = compose.onNodeWithTag("settings_strip_EQUAL").assertIsSelected().fetchSemanticsNode().boundsInRoot
        val proportional = compose.onNodeWithTag("settings_strip_TIME_PROPORTIONAL").assertIsNotSelected().fetchSemanticsNode().boundsInRoot
        assertTrue(equal.bottom <= proportional.top)
        for (layout in PeriodStripLayout.entries) {
            val row = "settings_strip_${layout.name}"
            val title = if (layout == PeriodStripLayout.EQUAL) "等间距" else "等比例"
            val label = compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag(row)), true).fetchSemanticsNode().boundsInRoot
            val preview = compose.onNodeWithTag("settings_strip_preview_${layout.name}", true).fetchSemanticsNode().boundsInRoot
            val circle = compose.onNodeWithTag("${row}_radio", true).assertWidthIsEqualTo(20.dp).fetchSemanticsNode().boundsInRoot
            assertTrue(label.right < preview.left); assertTrue(preview.right < circle.left)
        }
        compose.onNodeWithTag("settings_strip_TIME_PROPORTIONAL_radio", true).performTouchInput { click() }
        assertEquals(PeriodStripLayout.TIME_PROPORTIONAL, strip.layout)
        compose.onNodeWithTag("settings_strip_TIME_PROPORTIONAL").assertIsSelected()
        compose.onNodeWithTag("settings_strip_layout_summary", true).assertTextEquals("等比例")
        compose.onNodeWithTag("settings_default_campus").performScrollTo().performClick()
        compose.onNodeWithTag("settings_default_04_radio", true).performScrollTo().performTouchInput { click() }
        compose.onNodeWithTag("settings_default_04").assertIsSelected()
        assertEquals(Campus.SHAHE, appearance.defaultCampus)
        assertEquals(Campus.XITUCHENG, campus)
        compose.onNodeWithTag("settings_default_campus_summary", true).assertTextEquals("沙河")
        assertEquals(2, writes)
        shot("settings-compact-expanded-1.20.2")
    }

    @Test fun bothChevronButtonsRotateCounterclockwise90DegreesSmoothlyAndReverseInPlace() {
        render()
        for (tag in listOf("settings_strip_layout", "settings_default_campus")) {
            compose.onNodeWithTag(tag).performScrollTo()
            assertEquals(0f, rotation(tag))
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag(tag).performClick()
            compose.mainClock.advanceTimeBy(80)
            assertTrue(rotation(tag) in -89f.. -1f)
            compose.mainClock.advanceTimeBy(160)
            assertEquals(-90f, rotation(tag))
            compose.onNodeWithTag(tag).performClick()
            compose.mainClock.advanceTimeBy(64)
            val reversing = rotation(tag)
            assertTrue(reversing in -89f.. -1f)
            compose.onNodeWithTag(tag).performClick()
            compose.mainClock.advanceTimeBy(240)
            assertEquals(-90f, rotation(tag))
            compose.onNodeWithTag(tag).performClick()
            compose.mainClock.advanceTimeBy(240)
            assertEquals(0f, rotation(tag))
            compose.mainClock.autoAdvance = true
        }
        assertEquals(0, writes)
    }

    @Test fun disabledExpandedOptionsCannotWriteAndLargeTextCanStillReachBothGroupsAndExit() {
        fontScale = 1.5f
        render()
        compose.onNodeWithTag("settings_strip_layout").performClick()
        compose.onNodeWithTag("settings_default_campus").performScrollTo().performClick()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("settings_strip_TIME_PROPORTIONAL").assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("settings_default_04").assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertEquals(0, writes)
        compose.runOnIdle { enabled = true }
        compose.onNodeWithTag("settings_default_04").performScrollTo().performClick()
        assertEquals(Campus.SHAHE, appearance.defaultCampus)
        compose.onNodeWithTag("settings_strip_numbers").performScrollTo().performClick()
        assertTrue(strip.showNumbers)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_logout"))
        compose.onNodeWithTag("settings_logout").assertIsDisplayed()
        shot("settings-compact-large-text-1.20.2")
    }
}
