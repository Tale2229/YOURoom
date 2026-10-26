package com.example.youroom

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class DialogDesignUiTest {
    @get:Rule val compose = createComposeRule()
    private var tone by mutableStateOf(CampusTone.MATISSE)
    private var dark by mutableStateOf(false)
    private var scene by mutableIntStateOf(0)
    private var large by mutableStateOf(false)
    private var cache by mutableStateOf(CacheClearState(CacheClearPhase.CREDENTIALS))
    private var titleColor = Color.Transparent
    private var footerColor = Color.Transparent
    private var paper = Color.Transparent
    private var selections by mutableStateOf(SearchFilters())
    private var profile by mutableStateOf(DemoData.snapshot().classrooms.first().emptyProfile())

    private fun render() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (large) Density(density.density * 1.15f, 1.6f) else density) {
                YOURoomTheme(darkTheme = dark, appearance = AppearancePreferences(westTone = tone)) {
                    val colors = MaterialTheme.colorScheme
                    SideEffect { titleColor = colors.surfaceContainer; footerColor = colors.surfaceContainerLow; paper = colors.surface }
                    val data = remember { DemoData.snapshot() }
                    val state = YOURoomState(initializing = false, demo = true, snapshot = data,
                        today = data.queryDate, filters = selections)
                    when (scene) {
                        0 -> FilterDialog(state, {}, { selections = it }, {}, {}, {})
                        1 -> RoomTagsDialog(RoomMatch(data.classrooms.first(), profile, emptySet()), state, {}, { profile = it }, {}, {}, {})
                        2 -> CacheClearDialog(cache, {}, {}, { _, _ -> })
                    }
                }
            }
        }
    }

    private fun band(tag: String, expected: Color) {
        val pixels = compose.onNodeWithTag(tag).assertIsDisplayed().captureToImage().toPixelMap()
        val actual = pixels[8.coerceAtMost(pixels.width - 1), pixels.height / 2]
        assertTrue("$tone/$dark $tag expected $expected, got $actual",
            abs(expected.red - actual.red) < .025f && abs(expected.green - actual.green) < .025f && abs(expected.blue - actual.blue) < .025f)
    }

    private fun shot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        File(instrumentation.targetContext.getExternalFilesDir(null), "dialog-design-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun bareExit(tag: String, background: Color) {
        val exit = compose.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction()
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        val pixels = exit.captureToImage().toPixelMap()
        // Sample outside the glyph, where both the old filled A face and
        // outlined B face used to paint over the underlying footer.
        for (x in listOf(.15f, .85f)) for (y in listOf(.25f, .5f, .75f)) {
            val actual = pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]
            assertTrue("$tone/$dark $tag must not paint a button frame: $actual / $background",
                abs(background.red - actual.red) < .025f && abs(background.green - actual.green) < .025f &&
                    abs(background.blue - actual.blue) < .025f)
        }
    }

    @Test fun approvedABSurfacesFollowAll12ThemesInLightAndDark() {
        render()
        for (night in listOf(false, true)) for (palette in CampusTone.entries) {
            compose.runOnIdle { tone = palette; dark = night; scene = 0 }
            compose.waitForIdle()
            band("dialog_design_heading", titleColor)
            band("dialog_design_footer", footerColor)
            compose.onNodeWithTag("close_filters").assertIsDisplayed().assertHasClickAction()
            bareExit("close_filters", footerColor)
            if (palette in listOf(CampusTone.MATISSE, CampusTone.MORNING_BELL)) shot("B-${palette.id}-$night")
            compose.runOnIdle { scene = 1 }
            compose.waitForIdle()
            band("dialog_design_heading", paper)
            band("dialog_design_footer", paper)
            compose.onNodeWithTag("close_room_tags").assertIsDisplayed().assertHasClickAction()
            bareExit("close_room_tags", paper)
            if (palette == CampusTone.MATISSE) shot("A-${palette.id}-$night")
        }
    }

    @Test fun changedContentKeepsTheBareCheckAndItsConfirmationAnimation() {
        scene = 1; render()
        compose.runOnIdle { profile = profile.copy(outlets = true) }
        compose.waitForIdle()
        compose.onNodeWithTag("dialog_exit_save", true).assertExists()
        bareExit("close_room_tags", paper)
        compose.onNodeWithTag("close_room_tags").performClick()
        compose.waitForIdle()
        bareExit("close_room_tags", paper)
        compose.onNodeWithTag("close_room_tags").assert(SemanticsMatcher.expectValue(ConfirmationRunning, false))
    }

    @Test fun cacheStyleChangesKeepInputsAndHideActionsAfterIrreversiblePhase() {
        scene = 2; render()
        compose.onNodeWithTag("cache_clear_account").performTextInput("sample")
        compose.onNodeWithTag("cache_clear_password").performTextInput("dummy")
        compose.runOnIdle { cache = CacheClearState(CacheClearPhase.VERIFYING) }
        compose.onNodeWithTag("cache_clear_account").assertTextContains("sample").assertIsNotEnabled()
        band("dialog_design_heading", titleColor)
        compose.runOnIdle { cache = CacheClearState(CacheClearPhase.CREDENTIALS, "校验未通过") }
        compose.onNodeWithTag("cache_clear_account").assertTextContains("sample").assertIsEnabled()
        compose.onNodeWithTag("cache_clear_error").assertIsDisplayed()
        compose.runOnIdle { cache = CacheClearState(CacheClearPhase.CLEARING) }
        compose.onNodeWithTag("cache_clear_submit").assertDoesNotExist()
        compose.onNodeWithTag("cache_clear_cancel").assertDoesNotExist()
        compose.onNodeWithTag("dialog_design_footer").assertDoesNotExist()
    }

    @Test fun largeTextKeepsCacheActionsAndScrollableCredentialFieldsReachable() {
        scene = 2; large = true; render()
        compose.onNodeWithTag("cache_clear_submit").assertIsDisplayed()
        compose.onNodeWithTag("cache_clear_cancel").assertIsDisplayed()
        compose.onNodeWithTag("cache_clear_password").performScrollTo().assertIsDisplayed()
        shot("B-cache-large")
        compose.runOnIdle { cache = CacheClearState(CacheClearPhase.CONFIRM) }
        compose.onNodeWithTag("cache_clear_confirm").assertIsDisplayed()
        shot("A-cache-large")
    }
}
