package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic state only; never reads credentials or installs over the user's app. */
class MainPagesUiTest {
    @get:Rule val compose = createComposeRule()
    private var height by mutableIntStateOf(840)
    private var dark by mutableStateOf(false)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var fontScale by mutableFloatStateOf(1f)
    private var demo by mutableStateOf(false)
    private var tone by mutableStateOf(CampusTone.MATISSE)
    private var profiles by mutableStateOf<Map<String, RoomProfile>>(emptyMap())
    private var logouts = 0
    private var clears = 0

    private fun render(rooms: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                val appearance = AppearancePreferences(westTone = tone, shaheTone = tone, darkMode = dark)
                YOURoomTheme(campus = campus, darkTheme = dark, appearance = appearance) {
                    Surface(Modifier.fillMaxSize()) {
                        Box {
                        Column(Modifier.width(320.dp).height(height.dp)) {
                            Box(Modifier.weight(1f)) {
                                val snapshot = remember(campus) { DemoData.snapshot(campus) }
                                val state = YOURoomState(initializing = false, demo = demo, campus = campus,
                                    snapshot = snapshot, today = snapshot.queryDate, appearance = appearance, profiles = profiles,
                                    filters = SearchFilters(setOf(1, 2)))
                                if (rooms) ClassroomScreen(state, {}, {}, { _, _ -> }, {})
                                else SettingsScreen(state, {}, {}, { logouts++ }, { campus = it },
                                    onClearCache = { clears++ })
                            }
                            WorkspaceNavigation(if (rooms) 0 else 2, {})
                        }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun settingsKeepTheir52dpRowsAndOnlyRevealTheAccountPanelAfterScrolling() {
        render()
        val tags = listOf("settings_dark_mode", "settings_strip_layout", "settings_default_campus",
            "settings_strip_numbers", "settings_room_marks", "settings_planning", "settings_usage_guide", "settings_about")
        val bounds = tags.map { compose.onNodeWithTag(it).assertHeightIsEqualTo(52.dp).fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (a,b) -> assertEquals(a.bottom, b.top, 1f) }
        compose.onNodeWithTag("settings_clear_cache").assertIsNotDisplayed()
        compose.onNodeWithTag("settings_logout").assertIsNotDisplayed()
        compose.onNodeWithText("上滑查看本机与账户").assertDoesNotExist()
        compose.onNodeWithTag("settings_footer_handle").assertIsDisplayed().assertHeightIsEqualTo(48.dp)
        shot("settings-first")
        compose.onNodeWithTag("settings_list").performTouchInput { swipeUp(durationMillis = 500) }
        compose.onNodeWithTag("settings_clear_cache").assertIsDisplayed()
        compose.onNodeWithTag("settings_logout").assertIsDisplayed()
        val clear = compose.onNodeWithTag("settings_clear_cache").fetchSemanticsNode().boundsInRoot
        val logout = compose.onNodeWithTag("settings_logout").fetchSemanticsNode().boundsInRoot
        assertEquals(clear.center.x, logout.center.x, 1f)
        assertTrue(clear.bottom < logout.top)
        compose.onNodeWithTag("settings_clear_cache").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("settings_logout").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("settings_version").assertIsDisplayed()
        compose.onNodeWithText("本机与账户").assertDoesNotExist()
        compose.onNodeWithText("保留个人使用数据").assertDoesNotExist()
        compose.onNodeWithText("需校验登录账密", substring = true).assertDoesNotExist()
        shot("settings-account")
        compose.onNodeWithTag("settings_logout").performClick()
        assertEquals(0, logouts)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("settings_clear_cache").performClick()
        assertEquals(1, clears)
    }

    @Test fun shortScreenLargeTextAndDemoCanStillReachTheAccountActions() {
        height = 560; fontScale = 1.5f; demo = true
        render()
        compose.onNodeWithTag("settings_clear_cache").assertIsNotDisplayed()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_clear_cache"))
        compose.onNodeWithTag("settings_clear_cache").assertIsDisplayed()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_logout"))
        compose.onNodeWithText("结束演示").assertIsDisplayed()
        shot("settings-large")
        compose.onNodeWithTag("settings_logout").performClick()
        compose.onNodeWithText("确认").performClick()
        assertEquals(1, logouts)
    }

    @Test fun foldedSettingsWorkInBothCampusesAndDarkMode() {
        dark = true; campus = Campus.SHAHE; tone = CampusTone.MORNING_BELL
        render()
        compose.onNodeWithTag("settings_clear_cache").assertIsNotDisplayed()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_logout"))
        compose.onNodeWithTag("settings_logout").assertIsDisplayed()
        shot("settings-dark")
        compose.onNodeWithTag("settings_list").performTouchInput { swipeDown(durationMillis = 500) }
        compose.onNodeWithTag("settings_clear_cache").assertIsNotDisplayed()
    }

    @Test fun classroomQueryIsPaperAndRowsUseWholeFlatBandsEvenWhenSwiped() {
        demo = true
        val favorite = DemoData.snapshot().classrooms.first().emptyProfile().copy(favorite = true)
        profiles = mapOf(favorite.roomId to favorite)
        render(rooms = true)
        val colors = campusColorScheme(Campus.XITUCHENG, false, tone)
        pixel("room_query_surface", colors.surface)
        pixel("room_results_heading", colors.surfaceContainerLow)
        val visibleRows = hasTestTagPrefix("room_row_") and hasAnyAncestor(hasTestTag("campus_content_01"))
        compose.waitUntil(10_000) { compose.onAllNodes(visibleRows, true).fetchSemanticsNodes().size >= 2 }
        val rows = compose.onAllNodes(visibleRows, true).fetchSemanticsNodes()
        val first = rows[0].config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("room_row_")
        val second = rows[1].config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("room_row_")
        pixel("room_face_$first", colors.surface)
        pixel("room_face_$second", mainPageRowColor(colors, true))
        val row = roomNode("room_row_$first").fetchSemanticsNode().boundsInRoot
        val corner = roomNode("room_favorite_$first").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 14.dp.toPx() }, corner.top - row.top, 1f)
        assertEquals(with(compose.density) { 24.dp.toPx() }, row.right - corner.right, 1f)
        shot("rooms")
        roomNode("room_face_$first").performTouchInput { swipeLeft(durationMillis = 400) }
        pixel("room_face_$first", colors.surface)
        roomNode("room_actions_$first").assertIsDisplayed()
        shot("rooms-swiped")
    }

    @Test fun plannerUsesFlatResultBandsWithoutRegeneratingAnExistingPlan() {
        val data = DemoData.snapshot()
        val first = data.classrooms[0]
        val second = data.classrooms[1]
        val windows = listOf(StudyWindow(540, 600), StudyWindow(780, 840))
        val plan = StudyPlan(StudyWindow(540, 840), listOf(
            PlanningStep.Study(540, 600, first, false), PlanningStep.Break(600, 780),
            PlanningStep.Study(780, 840, second, false)), 120, 0, 1, 0, 0, 0, 0, 0, 0, windows)
        val execution = PlanningExecution(1, PlanningInput(data, Campus.XITUCHENG, data.queryDate,
            emptyMap(), PlanningGoal(780, 840, savedWindows = listOf(windows[0]))),
            PlanningResult(options = listOf(PlanningOption("综合", plan))))
        var draft by mutableStateOf(PlanningDraft(savedWindows = windows))
        var generations = 0
        compose.setContent {
            val appearance = AppearancePreferences(westTone = CampusTone.MATISSE, darkMode = false)
            YOURoomTheme(appearance = appearance) {
                Surface {
                    StudyPlanningScreen(YOURoomState(initializing = false, demo = true, snapshot = data,
                        today = data.queryDate, appearance = appearance), draft, mapOf(Campus.XITUCHENG to execution),
                        { draft = it }, { generations++ }, {}, {}, {})
                }
            }
        }
        val colors = campusColorScheme(Campus.XITUCHENG, false, CampusTone.MATISSE)
        for (index in 0..1) {
            val pixels = compose.onNodeWithTag("planner_step_$index").captureToImage().toPixelMap()
            assertPixel("plan $index", mainPageRowColor(colors, index == 1), pixels[4, 4])
        }
        shot("planner")
        compose.runOnIdle { draft = draft.copy(flexible = !draft.flexible) }
        compose.onNodeWithTag("planner_step_0").assertIsDisplayed()
        compose.onNodeWithTag("planner_step_1").assertIsDisplayed()
        assertEquals(0, generations)
    }

    @Test fun balancedRoomBandsKeepTheirHeightAndRequestedCornerInsetsWithOrWithoutNumbers() {
        val data = DemoData.snapshot()
        val baseline = data.classrooms[0]
        val balanced = data.classrooms[1]
        var numbers by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                LocalPeriodStripPreferences provides PeriodStripPreferences(showNumbers = numbers)) {
                YOURoomTheme(darkTheme = false) {
                    Surface {
                        Column {
                            for ((room, inset) in listOf(baseline to false, balanced to true)) {
                                SwipeRoomRow(RoomMatch(room, room.emptyProfile().copy(favorite = true), setOf(1, 2)),
                                    data.slots, emptyList(), false, true, {}, {}, {}, {}, {},
                                    showDivider = false, balancedBandInsets = inset)
                            }
                        }
                    }
                }
            }
        }
        for (font in listOf(1f, 1.5f)) for (show in listOf(false, true)) {
            compose.runOnIdle { scale = font; numbers = show }
            val old = compose.onNodeWithTag("room_row_${baseline.id}", true).fetchSemanticsNode().boundsInRoot
            val updated = compose.onNodeWithTag("room_row_${balanced.id}", true).fetchSemanticsNode().boundsInRoot
            val corner = compose.onNodeWithTag("room_favorite_${balanced.id}", true).fetchSemanticsNode().boundsInRoot
            assertEquals(old.height, updated.height, 1f)
            assertEquals(with(compose.density) { 14.dp.toPx() }, corner.top - updated.top, 1f)
            assertEquals(with(compose.density) { 24.dp.toPx() }, updated.right - corner.right, 1f)
            val strip = compose.onNodeWithTag("room_period_strip_${balanced.id}", true).fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag("room_period_${balanced.id}_1", true).fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("room_period_strip_${balanced.id}", true).performTouchInput {
                down(bar.center - strip.topLeft)
            }
            compose.waitUntil(4_000) { compose.onAllNodesWithTag("room_time_${balanced.id}_tooltip").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("room_time_${balanced.id}_tooltip").assertIsDisplayed()
            compose.onNodeWithTag("room_period_strip_${balanced.id}", true).performTouchInput { up() }
            compose.onNodeWithTag("room_period_strip_${balanced.id}", true).assertIsDisplayed()
            assertTrue(strip.height > with(compose.density) { 20.dp.toPx() })
        }
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("tag prefix $prefix") {
        it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }.startsWith(prefix)
    }
    private fun roomNode(tag: String) = compose.onNode(hasTestTag(tag) and
        hasAnyAncestor(hasTestTag("campus_content_01")), true)
    private fun pixel(tag: String, expected: Color) {
        val pixels = roomNode(tag).captureToImage().toPixelMap()
        assertPixel(tag, expected, pixels[4, 4])
    }
    private fun assertPixel(tag: String, expected: Color, actual: Color) {
        assertTrue("$tag $actual != $expected", kotlin.math.abs(actual.red - expected.red) < .015f &&
            kotlin.math.abs(actual.green - expected.green) < .015f && kotlin.math.abs(actual.blue - expected.blue) < .015f)
    }
    private fun shot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        File(instrumentation.targetContext.getExternalFilesDir(null), "main-pages-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
