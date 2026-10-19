package com.example.youroom

import android.accessibilityservice.AccessibilityService
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.youroom.data.ClassroomDataSource
import com.example.youroom.data.local.FilterSettings
import com.example.youroom.domain.*
import com.example.youroom.ui.YOURoomApp
import com.example.youroom.ui.YOURoomViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class YOURoomUiTest {

    @Test fun savedPlanningDefaultsInitializeANewDraftAndOnlyANewGenerationChangesTheDisplayedDirection() {
        compose.waitUntil(10_000) { !capturedModel.state.value.initializing }
        compose.runOnIdle { capturedModel.setPlanningPreferences(Campus.XITUCHENG,
            CampusPlanningPreferences(PlanningDirection.FAVORITES,false)) }
        enterDemo()
        compose.onNodeWithTag("tab_planner").performClick()
        compose.onNodeWithTag("planner_adjust").assertIsOff()
        compose.onNodeWithTag("planner_add_period").performClick(); compose.waitForIdle()
        compose.onNodeWithTag("planner_generate").performClick()
        compose.waitUntil(20_000) { capturedModel.planning.executions.value[Campus.XITUCHENG]?.pending == false }
        compose.onNodeWithTag("planner_alternatives_title",true).assertTextEquals("收藏教室覆盖最多")
        val previous = capturedModel.planning.executions.value.getValue(Campus.XITUCHENG)
        compose.runOnIdle { capturedModel.setPlanningPreferences(Campus.XITUCHENG,
            CampusPlanningPreferences(PlanningDirection.CHANGES,true)) }
        compose.waitForIdle()
        assertEquals(previous,capturedModel.planning.executions.value.getValue(Campus.XITUCHENG))
        compose.onNodeWithTag("planner_adjust").assertIsOff()
        compose.onNodeWithTag("planner_alternatives_title",true).assertTextEquals("收藏教室覆盖最多")
        compose.onNodeWithTag("planner_generate").performClick()
        compose.waitUntil(20_000) {
            capturedModel.planning.executions.value[Campus.XITUCHENG]?.let { it.id>previous.id && !it.pending } == true
        }
        compose.onNodeWithTag("planner_alternatives_title",true).assertTextEquals("换教室最少")
    }
    @get:Rule val compose = createComposeRule()
    private lateinit var capturedModel: YOURoomViewModel

    @Before fun renderIsolatedApp() {
        // Never read a real user's Token, database or preferences during instrumentation.
        val source = object : ClassroomDataSource {
            override val profiles = MutableStateFlow<Map<String, RoomProfile>>(emptyMap())
            override suspend fun hasSession() = false
            override suspend fun cached(campus: Campus): ClassroomSnapshot? = null
            override suspend fun login(account: String, password: String) = error("Live login disabled in UI tests")
            override suspend fun refresh(campus: Campus): ClassroomSnapshot = error("Live queries disabled in UI tests")
            override suspend fun logout() = Unit
            override suspend fun saveProfile(profile: RoomProfile) = error("Real persistence disabled in UI tests")
            override suspend fun deleteProfile(roomId: String) = error("Real persistence disabled in UI tests")
        }
        val settings = object : FilterSettings {
            override val planningPreferences=MutableStateFlow(PlanningPreferences())
            override suspend fun savePlanningPreferences(value: PlanningPreferences) { planningPreferences.value=value }
            override val appearance=MutableStateFlow(AppearancePreferences())
            override suspend fun saveAppearance(value: AppearancePreferences) { appearance.value=value }
            override val periodStrip=MutableStateFlow(PeriodStripPreferences())
            override suspend fun savePeriodStrip(value: PeriodStripPreferences) { periodStrip.value=value }
            override val filters = MutableStateFlow(SearchFilters())
            override val catalog = MutableStateFlow(TagCatalog())
        override val buildings = MutableStateFlow(Campus.entries.associateWith { emptySet<String>() })
            override suspend fun save(filters: SearchFilters) = error("Real settings disabled in UI tests")
            override suspend fun addTag(name: String) = error("Real settings disabled in UI tests")
            override suspend fun saveBuildings(campus: Campus, names: Set<String>) = error("Real settings disabled in UI tests")
            override suspend fun removeTag(name: String) = error("Real settings disabled in UI tests")
            override suspend fun reorderTags(names: List<String>) = error("Real settings disabled in UI tests")
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = YOURoomViewModel(source, settings) as T
        }
        compose.setContent {
            val model: YOURoomViewModel = viewModel(factory = factory)
            androidx.compose.runtime.SideEffect { capturedModel = model }
            YOURoomApp(model)
        }
    }

    @Test fun campusHeadingChangesWithoutChangingItsFontSize() {
        enterDemo()
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        val west = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("campus_label").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(west) }
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        val shahe = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("campus_label").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(shahe) }
        assertEquals(12.sp, west.single().layoutInput.style.fontSize)
        assertEquals(west.single().layoutInput.style.fontSize, shahe.single().layoutInput.style.fontSize)
        assertEquals(west.single().layoutInput.style.fontFamily, shahe.single().layoutInput.style.fontFamily)
    }

    @Test fun demoWorkspaceAndPreferencesAreReachableWithoutCredentials() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("demo").performScrollTo().performClick()
        compose.onNodeWithText("可选教室").performScrollTo().assertIsDisplayed()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("收藏 2-201").performScrollTo().performClick()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_room_marks").performClick()
        compose.onNodeWithText("2-201").assertIsDisplayed()
        compose.onNodeWithText("已收藏").assertIsDisplayed()
    }

    @Test fun plannerIsBetweenTheExistingTabsAndGeneratesAnOpenableFavoriteRoute() {
        enterDemo()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("收藏 2-201").performScrollTo().performClick()
        val rooms = compose.onNodeWithTag("tab_rooms").fetchSemanticsNode().boundsInRoot
        val planner = compose.onNodeWithTag("tab_planner").fetchSemanticsNode().boundsInRoot
        val preferences = compose.onNodeWithTag("tab_settings").fetchSemanticsNode().boundsInRoot
        assertTrue(rooms.right <= planner.left && planner.right <= preferences.left)
        compose.onNodeWithTag("tab_planner").performClick()
        compose.onNodeWithTag("planner_screen").assertExists()
        generatePlanner()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        assertPlanWindow("08:00—12:00")
        assertEquals(0, capturedModel.planning.executions.value.getValue(Campus.XITUCHENG).result!!.options.first().plan.changes)
        assertEquals(240, capturedModel.planning.executions.value.getValue(Campus.XITUCHENG).result!!.options.first().plan.favoriteMinutes)
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        compose.onNodeWithTag("planner_step_0").performClick()
        compose.onNodeWithTag("room_actions_dialog").assertDoesNotExist()
        compose.onNodeWithTag("room_detail").assertDoesNotExist()
        compose.onNodeWithTag("tab_rooms").performClick()
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_room_marks").performClick()
        compose.onNodeWithText("2-201").assertExists()
    }

    @Test fun plannerTimeInputPersistsAcrossTabsAndDoesNotChangeStudySlots() {
        enterDemo()
        compose.onNodeWithTag("tab_planner").performClick()
        compose.onNodeWithTag("planner_end_hour_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(9f) }
        compose.onNodeWithTag("planner_end_minute_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(35f) }
        compose.onNodeWithTag("planner_end").assertTextContains("09:35")
        compose.onNodeWithTag("planner_adjust").performClick()
        generatePlanner()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        assertPlanWindow("08:00—09:35")
        compose.onNodeWithTag("tab_rooms").performClick()
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("tab_planner").performClick()
        compose.onNodeWithTag("planner_target_range_0").assertTextContains("08:00–09:35")
        compose.onNodeWithTag("planner_start").assertTextContains("09:35")
        compose.onNodeWithTag("planner_end").assertTextContains("10:35")
    }

    @Test fun stripSettingsApplyAcrossTabsAndDoNotRegenerateOrClearTheExistingPlan() {
        enterDemo()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_strip_layout").performScrollTo().performClick()
        compose.onNodeWithTag("settings_strip_TIME_PROPORTIONAL").performClick()
        compose.onNodeWithTag("settings_strip_numbers").performScrollTo().performClick()
        compose.onNodeWithTag("tab_rooms").performClick()
        compose.onNodeWithTag("room_period_number_01:2-201_1",true).performScrollTo().assertIsDisplayed()
        val fifth=compose.onNodeWithTag("room_period_01:2-201_5",true).fetchSemanticsNode().boundsInRoot
        val sixth=compose.onNodeWithTag("room_period_01:2-201_6",true).fetchSemanticsNode().boundsInRoot
        assertEquals(fifth.width,sixth.left-fifth.right,1f)
        compose.onNodeWithTag("tab_planner").performClick()
        generatePlanner()
        val execution=capturedModel.planning.executions.value.getValue(Campus.XITUCHENG)
        compose.onNodeWithTag("planner_target_number_1",true).assertExists()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_strip_layout").performScrollTo()
        compose.onNodeWithTag("settings_strip_EQUAL").performClick()
        compose.onNodeWithTag("tab_planner").performClick()
        compose.onNodeWithTag("planner_target_range_0",true).assertExists()
        assertEquals(execution,capturedModel.planning.executions.value.getValue(Campus.XITUCHENG))
        val a=compose.onNodeWithTag("planner_target_period_5",true).fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithTag("planner_target_period_6",true).fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 3.dp.toPx() },b.left-a.right,1f)
    }

    @Test fun manualDarkModeAppliesThroughTheSettingsSwitchAndSurvivesLeavingDemo() {
        enterDemo()
        compose.runOnIdle { capturedModel.setDarkMode(false) }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_dark_mode_switch").assertIsOff().performClick()
        compose.waitUntil(5000) { capturedModel.state.value.appearance.darkMode == true }
        compose.onNodeWithTag("settings_dark_mode_switch").assertIsOn()
        compose.onNodeWithTag("tab_rooms").performClick()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_dark_mode_switch").assertIsOn()
        compose.runOnIdle { capturedModel.logout() }
        compose.waitUntil(5000) { !capturedModel.state.value.showWorkspace }
        assertEquals(true, capturedModel.state.value.appearance.darkMode)
        enterDemo()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_dark_mode_switch").assertIsOn()
    }

    @Test fun numberSwitchAlreadyShowsTheSavedCheckWhileTheSettingsPageIsEntering() {
        enterDemo()
        compose.runOnIdle { capturedModel.setPeriodStrip(PeriodStripPreferences(showNumbers=true)) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("tab_settings").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("settings_strip_numbers_switch",true).assertIsOn().assertIsEnabled()
        compose.onNodeWithTag("settings_logout",true).assertIsEnabled()
        compose.onNodeWithTag("settings_theme_previous",true).assertIsEnabled()
        compose.onNodeWithTag("settings_theme_next",true).assertIsEnabled()
        assertTrue(capturedModel.state.value.periodStrip.showNumbers)
        compose.mainClock.advanceTimeBy(1400)
        compose.onNodeWithTag("settings_strip_numbers_switch",true).assertIsOn().assertIsEnabled()
        compose.mainClock.autoAdvance=true
    }

    @Test fun appearanceChangesApplyAcrossTabsAndKeepTheExistingPlan() {
        enterDemo()
        compose.onNodeWithTag("tab_planner").performClick()
        generatePlanner()
        val execution=capturedModel.planning.executions.value.getValue(Campus.XITUCHENG)
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_theme_previous").performClick()
        compose.waitUntil(5_000) { capturedModel.state.value.appearance.westTone == CampusTone.MONDRIAN }
        compose.waitForIdle()
        compose.onNodeWithTag("tab_rooms").performClick()
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("tab_planner").performClick()
        compose.onNodeWithTag("planner_target_range_0",true).assertExists()
        assertEquals(execution,capturedModel.planning.executions.value.getValue(Campus.XITUCHENG))
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_theme_name").assertTextEquals("蒙德里安")
        compose.onNodeWithTag("settings_theme_impression").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(
            com.example.youroom.ui.ThemePreviewSlideProgress,1f))
    }

    @Test fun plannerSwitchesCampusesWithoutKeepingWestRoomsOrTheRemovedRulesHeading() {
        enterDemo()
        compose.onNodeWithTag("tab_planner").performClick()
        generatePlanner()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        compose.onNodeWithTag("planner_generate").performScrollTo()
        compose.onNodeWithTag("planner_step_0").assertDoesNotExist()
        compose.onNodeWithTag("planner_generate").performClick()
        awaitPlanner()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        compose.onNodeWithTag("planner_step_0").assert(hasAnyDescendant(hasText("演示楼-A101")))
        compose.onNodeWithText("2-201").assertDoesNotExist()
        compose.onNodeWithText("规则").assertDoesNotExist()
        compose.onNodeWithText("规划规则").assertDoesNotExist()
    }

    @Test fun classroomFavoriteTagAndExclusionEditsKeepTheGeneratedPlanUntilAnotherClick() {
        enterDemo()
        compose.onNodeWithTag("tab_planner").performClick()
        generatePlanner()
        val first = capturedModel.planning.executions.value.getValue(Campus.XITUCHENG)
        compose.onNodeWithTag("tab_rooms").performClick()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("收藏 2-201").performClick()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithTag("filter_outlets").performClick()
        compose.onNodeWithTag("close_room_tags").performClick()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("排除 2-201").performClick()
        compose.onNodeWithTag("tab_planner").performClick()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        assertPlanWindow("08:00—12:00")
        assertEquals(0, capturedModel.planning.executions.value.getValue(Campus.XITUCHENG).result!!.options.first().plan.favoriteMinutes)
        org.junit.Assert.assertSame(first, capturedModel.planning.executions.value[Campus.XITUCHENG])
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("tab_planner").performClick()
        org.junit.Assert.assertSame(first, capturedModel.planning.executions.value[Campus.XITUCHENG])
        generatePlanner()
        val next = capturedModel.planning.executions.value.getValue(Campus.XITUCHENG)
        assertTrue(next.id > first.id)
        assertTrue(next.input.profiles.getValue("01:2-201").excluded)
        assertTrue(next.result!!.options.flatMap { it.plan.steps }.filterIsInstance<PlanningStep.Study>()
            .none { it.classroom.id == "01:2-201" })
    }

    @Test fun expectedRoomsModesAndRemovingAllPeriodsDoNotEraseAnExistingPlan() {
        enterDemo()
        compose.onNodeWithTag("tab_planner").performClick()
        generatePlanner()
        val first = capturedModel.planning.executions.value.getValue(Campus.XITUCHENG)
        compose.onNodeWithTag("planner_expected").performScrollTo()
        compose.onNodeWithTag("planner_expected").performClick()
        compose.onNodeWithTag("expected_classroom_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag("expected_pin").performClick()
        compose.onNodeWithTag("expected_close").performClick()
        compose.onNodeWithTag("planner_expected_badge").assertTextEquals("1")
        compose.onNodeWithTag("planner_adjust").performClick()

        removePeriod(0)
        compose.onNodeWithTag("planner_generate").performScrollTo()
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        assertPlanWindow("08:00—12:00")
        org.junit.Assert.assertSame(first, capturedModel.planning.executions.value[Campus.XITUCHENG])
        compose.onNodeWithTag("planner_start_hour_wheel").performScrollTo()
        compose.onNodeWithTag("planner_start_hour_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        compose.onNodeWithTag("planner_end_hour_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(11f) }
        compose.onNodeWithTag("planner_add_period").performScrollTo().performClick()
        org.junit.Assert.assertSame(first, capturedModel.planning.executions.value[Campus.XITUCHENG])
        generatePlanner()
        val next = capturedModel.planning.executions.value.getValue(Campus.XITUCHENG)
        assertTrue(next.id > first.id)
        assertEquals(listOf(StudyWindow(600,660)), next.input.goal.windows)
        assertEquals(setOf("01:2-240"), next.input.goal.expectedRooms)
    }

    @Test fun plannerInlineWheelsKeepStartAndEndIndependentWithoutConfirmation() {
        enterDemo()
        compose.onNodeWithTag("tab_planner").performClick()
        compose.onNodeWithTag("planner_start_minute_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        compose.onNodeWithTag("planner_start").assertTextContains("08:12")
        compose.onNodeWithTag("planner_end").assertTextContains("12:00")
        assertEquals(12f, compose.onNodeWithTag("planner_end_hour_wheel").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current)
        assertEquals(0f, compose.onNodeWithTag("planner_end_minute_wheel").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current)
        compose.onNodeWithTag("planner_time_confirm").assertDoesNotExist()
        compose.onNodeWithTag("planner_time_cancel").assertDoesNotExist()
    }

    @Test fun anInvalidUnconfirmedDraftDoesNotInvalidateTheConfirmedPlan() {
        enterDemo()
        compose.onNodeWithTag("tab_planner").performClick()
        generatePlanner()
        compose.onNodeWithTag("planner_start_hour_wheel").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(18f) }
        generatePlanner()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        assertPlanWindow("08:00—12:00")
        compose.onNodeWithTag("planner_add_period").performScrollTo()
        compose.onNodeWithTag("planner_add_period").performClick()
        compose.onNodeWithTag("planner_message").assertIsDisplayed()
        compose.onNodeWithTag("planner_message").assertTextContains("结束时间必须晚于开始时间", substring = true)
    }

    @Test fun slotGridSupportsDisjointSelectionAndClear() {
        enterDemo()
        compose.onNodeWithTag("clear_slots").performScrollTo().performClick()
        compose.onNodeWithTag("slot_1").performScrollTo().performClick()
        compose.onNodeWithTag("slot_3").performClick()
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsNotSelected()
        compose.onNodeWithTag("slot_3").assertIsSelected()
        compose.onNodeWithTag("clear_slots").performClick()
        compose.onNodeWithText("请选择自习时段").assertDoesNotExist()
        compose.onNodeWithTag("slot_summary").assertDoesNotExist()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").assertIsDisplayed()
    }

    @Test fun changingSelectionNeverShiftsTheClassroomsHeading() {
        enterDemo()
        val top = compose.onNodeWithText("可选教室").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("clear_slots").performClick()
        assertEquals(top, compose.onNodeWithText("可选教室").fetchSemanticsNode().boundsInRoot.top, 1f)
        for (number in 1..14 step 2) compose.onNodeWithTag("slot_$number").performClick()
        assertEquals(top, compose.onNodeWithText("可选教室").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("clear_slots").performClick()
        assertEquals(top, compose.onNodeWithText("可选教室").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun gridDragCanAddAndRemoveWithoutTogglingTheWholePage() {
        enterDemo()
        compose.onNodeWithTag("clear_slots").performScrollTo().performClick()
        val grid = compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("slot_1").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        val third = compose.onNodeWithTag("slot_3").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        compose.onNodeWithTag("slot_grid").performTouchInput {
            down(first)
            moveTo(third)
            up()
        }
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_3").assertIsSelected()
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("slot_grid").performTouchInput {
            down(first)
            moveTo(third)
            up()
        }
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("slot_2").assertIsNotSelected()
    }

    @Test fun cancelingAPaintGestureRestoresTheSelection() {
        enterDemo()
        compose.onNodeWithTag("clear_slots").performScrollTo().performClick()
        val grid = compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("slot_1").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        val third = compose.onNodeWithTag("slot_3").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third); cancel() }
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("slot_2").assertIsNotSelected()
        compose.onNodeWithTag("slot_3").assertIsNotSelected()
    }

    @Test fun campusDemoSwitchDoesNotCarryWestClassroomsIntoShahe() {
        enterDemo()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("演示楼-A101"))
        compose.onNodeWithText("演示楼-A101").assertIsDisplayed()
        compose.onNodeWithText("2-201").assertDoesNotExist()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").assertIsDisplayed()
    }

    private fun generatePlanner() {
        compose.onNodeWithTag("planner_add_period").performScrollTo()
        if (compose.onAllNodesWithTag("planner_target_range_0").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("planner_add_period").performClick()
        }
        compose.onNodeWithTag("planner_generate").performScrollTo()
        compose.onNodeWithTag("planner_generate").performClick()
        awaitPlanner()
    }

    private fun awaitPlanner() {
        compose.waitUntil(20_000) {
            capturedModel.planning.executions.value[capturedModel.state.value.campus]?.pending == false
        }
    }

    private fun removePeriod(index: Int) {
        compose.onNodeWithTag("planner_target_range_$index",true).performClick()
        compose.onNodeWithTag("planner_remove_period_$index").performClick()
    }

    private fun assertPlanWindow(expected: String) = compose.runOnIdle {
        val execution=capturedModel.planning.executions.value.getValue(capturedModel.state.value.campus)
        val plan=execution.result!!.options[execution.selectedOption].plan
        assertEquals(expected,"${planningTime(plan.window.start)}—${planningTime(plan.window.end)}")
    }

    private fun enterDemo() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("demo").performScrollTo().performClick()
    }

    private fun revealRoom(id: String) {
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("room_row_$id"))
        compose.onNodeWithTag("room_row_$id").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("room_actions_$id").assertIsDisplayed()
    }

    @Test fun fixedHeaderAndIndependentScrollPositionsSurviveCampusSwitch() {
        enterDemo()
        val header = compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("未来学习大楼-202-203"))
        val scroll = compose.onNodeWithTag("room_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(scroll > 0f)
        assertEquals(header, compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        assertEquals(scroll, compose.onNodeWithTag("room_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value())
        compose.onNodeWithTag("campus_SHAHE").assertDoesNotExist()
        compose.onNodeWithTag("campus_XITUCHENG").assertDoesNotExist()
    }

    @Test fun verticalOrOutwardHeaderSwipesDoNotSwitchCampuses() {
        enterDemo()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeDown() }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("refresh_rooms").assertDoesNotExist()
    }

    @Test fun campusCanBeSwitchedUsingTheAccessibilityAction() {
        enterDemo()
        val action = compose.onNodeWithTag("campus_swipe_region").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("切换到沙河校区", action.label)
        compose.runOnIdle { assertTrue(action.action()) }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
    }

    @Test fun listScrollingDoesNotPaintOrSwitchCampus() {
        enterDemo()
        compose.onNodeWithTag("room_list").performTouchInput {
            swipeUp(startY = height * .9f, endY = height * .1f)
        }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("room_list").performScrollToIndex(1)
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_3").assertIsNotSelected()
    }

    @Test fun filterPopupSupportsMultipleBuildingsAndCountsSelectedChips() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("filter_dialog").assertIsDisplayed()
        compose.onNodeWithTag("apply_filters").assertDoesNotExist()
        compose.onNodeWithText("个人词条").assertDoesNotExist()
        compose.onNodeWithTag("building_2").performClick()
        compose.onNodeWithTag("building_3").performClick()
        compose.onNodeWithTag("building_2").assertIsSelected()
        compose.onNodeWithTag("building_3").assertIsSelected()
        compose.onNodeWithTag("building_all").assertIsNotSelected()
        compose.onNodeWithTag("filter_favorite").performClick()
        compose.onNodeWithTag("filter_outlets").performClick()
        compose.onNodeWithTag("filter_badge").assertTextEquals("4")
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("filter_dialog").assertDoesNotExist()
        compose.onNodeWithTag("filter_badge").assertTextEquals("4")
        compose.onNodeWithText("已有额外筛选 · 点击修改").assertDoesNotExist()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("filter_badge").assertTextEquals("2")
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("building_all").assertIsSelected()
        compose.onNodeWithTag("filter_favorite").assertIsSelected()
        compose.onNodeWithTag("filter_outlets").assertIsSelected()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("building_2").assertIsSelected()
        compose.onNodeWithTag("building_3").assertIsSelected()
    }

    @Test fun filtersHaveNoCapacityFieldAndResetAndCloseShareTheFooter() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithText("最低标注容量").assertDoesNotExist()
        compose.onNodeWithText("重置筛选").assertDoesNotExist()
        compose.onNodeWithTag("reset_filters").assertContentDescriptionEquals("重置")
        compose.onNodeWithTag("close_filters").assertContentDescriptionEquals("关闭")
        val reset = compose.onNodeWithTag("reset_filters").fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithTag("close_filters").fetchSemanticsNode().boundsInRoot
        val campus = compose.onNodeWithTag("filter_campus").fetchSemanticsNode().boundsInRoot
        assertTrue(reset.right <= close.left)
        assertEquals(reset.center.y, close.center.y, 1f)
        assertTrue(close.top > campus.bottom)
        compose.onNodeWithTag("building_2").performClick()
        compose.onNodeWithTag("filter_favorite").performClick()
        val image=compose.onNodeWithTag("filter_dialog").captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"filter-footer-1.14.1.png")
            .outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithTag("reset_filters").performClick()
        compose.onNodeWithTag("building_all").assertIsSelected()
        compose.onNodeWithTag("filter_favorite").assertIsNotSelected()
        compose.onNodeWithTag("filter_badge").assertDoesNotExist()
        compose.onNodeWithTag("filter_dialog").assertIsDisplayed()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("filter_dialog").assertDoesNotExist()
    }

    @Test fun addTermButtonIsToTheRightOfTheNameField() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("edit_tags").performClick()
        val input = compose.onNodeWithTag("custom_tag_name").fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithTag("add_tag").fetchSemanticsNode().boundsInRoot
        assertTrue(add.left >= input.right)
        assertTrue(add.center.y in input.top..input.bottom)
        compose.onNodeWithTag("custom_tag_name").performTextInput("靠窗")
        compose.onNodeWithTag("close_filters").assertIsDisplayed()
        compose.onNodeWithTag("add_tag").performClick()
        compose.onNodeWithTag("filter_tag_靠窗").assertExists()
    }

    @Test fun emptyTimeSelectionStillAllowsExtraFiltersAndReset() {
        enterDemo()
        compose.onNodeWithTag("clear_slots").performScrollTo().performClick()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("filter_favorite").performClick()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithText("没有符合条件的教室").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("清除额外筛选").performScrollTo().performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").assertIsDisplayed()
        compose.onNodeWithText("请选择自习时段").assertDoesNotExist()
    }

    @Test fun selectedTimeIsSharedEvenAfterEditingTheOtherCampus() {
        enterDemo()
        compose.onNodeWithTag("clear_slots").performScrollTo().performClick()
        compose.onNodeWithTag("slot_1").performClick()
        compose.onNodeWithTag("slot_6").performClick()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_6").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsNotSelected()
        compose.onNodeWithTag("slot_3").performClick()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("slot_3").assertIsSelected()
    }

    @Test fun compactGridAlignsSearchAndFilterWithoutTimeMarkers() {
        enterDemo()
        val first = compose.onNodeWithTag("slot_1").fetchSemanticsNode().boundsInRoot
        val sixth = compose.onNodeWithTag("slot_6").fetchSemanticsNode().boundsInRoot
        val seventh = compose.onNodeWithTag("slot_7").fetchSemanticsNode().boundsInRoot
        val tenth = compose.onNodeWithTag("slot_10").fetchSemanticsNode().boundsInRoot
        val eleventh = compose.onNodeWithTag("slot_11").fetchSemanticsNode().boundsInRoot
        val twelfth = compose.onNodeWithTag("slot_12").fetchSemanticsNode().boundsInRoot
        val thirteenth = compose.onNodeWithTag("slot_13").fetchSemanticsNode().boundsInRoot
        val fourteenth = compose.onNodeWithTag("slot_14").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithTag("room_search_frame").fetchSemanticsNode().boundsInRoot
        val filter = compose.onNodeWithTag("extra_filters").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, sixth.top, 1f)
        assertEquals(first.left, seventh.left, 1f)
        assertEquals(first.left, thirteenth.left, 1f)
        assertEquals(tenth.left, search.left, 1f)
        assertEquals(eleventh.right, search.right, 1f)
        assertEquals(twelfth.left, filter.left, 1f)
        assertEquals(twelfth.right, filter.right, 1f)
        assertEquals(fourteenth.center.y, search.center.y, 1f)
        assertEquals(fourteenth.center.y, filter.center.y, 1f)
        assertTrue(first.width + 1f >= first.height)
        // Both stationary bodies remain precomposed. The unmerged tree deliberately
        // includes the hidden one; inspect the current campus, not just its coordinates.
        val face = compose.onNode(hasTestTag("slot_face_1") and
            hasAnyAncestor(hasTestTag("campus_content_01")), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(first.height * .75f, face.height, 1f)
        for (number in listOf(1, 6, 7, 12, 13, 14)) {
            compose.onNodeWithTag("time_marker_$number").assertDoesNotExist()
        }
        compose.onNodeWithTag("filtering_indicator").assertDoesNotExist()
        compose.onNodeWithText("正在筛选教室", substring = true).assertDoesNotExist()
        compose.onNodeWithText("搜索教室或备注").assertDoesNotExist()
        compose.onNodeWithText("只确认所选教学节次空闲，课间与午休不作保证。").assertDoesNotExist()
    }

    @Test fun editingSearchAndOpeningFiltersDoesNotPaintPeriods() {
        enterDemo()
        compose.onNodeWithTag("room_search").performClick().performTextInput("201")
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_13").assertIsNotSelected()
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("filter_dialog").assertIsDisplayed()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_14").assertIsNotSelected()
    }

    @Test fun touchingTheFixedHeaderClearsSearchFocusAndCampusSwipeKeepsItBlurred() {
        enterDemo()
        compose.onNodeWithTag("room_search").performTouchInput { click() }
        compose.onNodeWithTag("room_search").performTextInput("201")
        compose.onNodeWithTag("room_search").assertIsFocused()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { click() }
        compose.onNodeWithTag("room_search").assertIsNotFocused().assertTextEquals("201")
        compose.onNodeWithTag("room_search").performTouchInput { click() }
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        compose.onNodeWithTag("room_search").assertIsNotFocused()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("room_search").assertIsNotFocused().assertTextEquals("201")
        compose.onNodeWithTag("room_search").performTouchInput { click() }
        compose.onNodeWithTag("tab_settings").performTouchInput { click() }
        compose.onNodeWithTag("tab_rooms").performTouchInput { click() }
        compose.onNodeWithTag("room_search").assertIsNotFocused().assertTextEquals("201")
    }

    @Test fun searchClearRestoresRoomsWithoutChangingPeriodsOrSelectedBuildings() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("building_2").performClick()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("room_search").performTextInput("不存在的教室")
        compose.onNodeWithTag("room_search").performImeAction()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("没有符合条件的教室").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("clear_room_search").performTouchInput { click() }
        compose.onNodeWithTag("clear_room_search").assertDoesNotExist()
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("filter_badge").assertTextEquals("1")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("2-201").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("2-201").assertExists()
        compose.onNodeWithTag("filtering_indicator").assertDoesNotExist()
    }

    @Test fun roomActionsAreOnlyRevealedByLeftSwipeInBothCampuses() {
        enterDemo()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithContentDescription("收藏 2-201").assertDoesNotExist()
        compose.onNodeWithText("2-201").performTouchInput { click() }
        compose.onNodeWithTag("room_detail").assertDoesNotExist()
        compose.onNodeWithTag("room_actions_dialog").assertDoesNotExist()
        revealRoom("01:2-201")
        for (index in 0..3) compose.onNodeWithTag("room_action_01:2-201_$index").assertIsDisplayed()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("收藏 演示楼-A101").assertDoesNotExist()
        revealRoom("04:演示楼-A101")
        for (index in 0..3) compose.onNodeWithTag("room_action_04:演示楼-A101_$index").assertIsDisplayed()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("room_actions_01:2-201").assertDoesNotExist()
    }

    @Test fun favoriteCornersAndAutoCloseWorkInBothCampusWorkspaces() {
        enterDemo()
        for ((id, name) in listOf("01:2-201" to "2-201", "04:演示楼-A101" to "演示楼-A101")) {
            if (id.startsWith("04:")) compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
            revealRoom(id)
            compose.onNodeWithContentDescription("收藏 $name").performClick()
            compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
            val marker = compose.onNodeWithTag("room_favorite_$id", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val face = compose.onNodeWithTag("room_face_$id").fetchSemanticsNode().boundsInRoot
            val period = compose.onNodeWithTag("room_period_${id}_14", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(face.top, marker.top, 1f); assertEquals(period.right, marker.right, 1f)
            compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("slot_1"))
            compose.onNodeWithTag("slot_1").assertIsSelected()
            compose.onNodeWithTag("slot_2").assertIsSelected()
        }
    }

    @Test fun roomPopupsUpdateMarksAndNotesWithoutSavingAndSearchIgnoresNotes() {
        enterDemo()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("收藏 2-201").performClick()
        compose.onNodeWithTag("room_actions_01:2-201").assertDoesNotExist()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithTag("room_tag_dialog").assertIsDisplayed()
        compose.onNodeWithTag("building_all").assertDoesNotExist()
        compose.onNodeWithTag("filter_favorite").assertDoesNotExist()
        compose.onNodeWithTag("filter_outlets").performClick().assertIsSelected()
        compose.onNodeWithTag("save_profile").assertDoesNotExist()
        compose.onNodeWithText("保存个人标记").assertDoesNotExist()
        compose.onNodeWithTag("close_room_tags").performClick()
        compose.onNodeWithTag("filter_badge").assertDoesNotExist()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("取消收藏 2-201").assertExists()
        compose.onNodeWithContentDescription("修改备注 2-201").performClick()
        compose.onNodeWithTag("room_note_dialog").assertIsDisplayed()
        compose.onNodeWithTag("room_note").performTextInput("仅备注关键词")
        compose.onNodeWithTag("close_room_note").performClick()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改备注 2-201").performClick()
        compose.onNodeWithTag("room_note").assertTextContains("仅备注关键词")
        compose.onNodeWithTag("close_room_note").performClick()
        compose.onNodeWithTag("room_search").performScrollTo().performTextInput("仅备注关键词")
        compose.onNodeWithTag("room_search").performImeAction()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("没有符合条件的教室").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("没有符合条件的教室").performScrollTo().assertIsDisplayed()
    }

    @Test fun customTermCanBeCreatedMarkedAndUsedToFilter() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("edit_tags").performClick()
        compose.onNodeWithTag("custom_tag_name").performScrollTo().performTextInput("靠窗")
        compose.onNodeWithTag("add_tag").performScrollTo().performClick()
        compose.onNodeWithTag("filter_tag_靠窗").performScrollTo().assertExists()
        compose.onNodeWithTag("close_filters").performClick()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithTag("filter_tag_靠窗").performClick()
        compose.onNodeWithTag("save_profile").assertDoesNotExist()
        compose.onNodeWithTag("close_room_tags").performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("extra_filters"))
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("filter_tag_靠窗").performScrollTo().performClick()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("filter_badge").assertTextEquals("1")
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").assertIsDisplayed()
        compose.onNodeWithText("3-108").assertDoesNotExist()
    }

    @Test fun addingRoomTagsAutomaticallyMarksTheRoomWithoutChangingSearchConditions() {
        enterDemo()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithTag("edit_tags").performClick()
        compose.onNodeWithContentDescription("删除词条 有插座").assertExists()
        compose.onNodeWithTag("custom_tag_name").performTextInput("靠窗")
        compose.onNodeWithTag("add_tag").performClick()
        compose.onNodeWithTag("filter_tag_靠窗").assertIsSelected()
        compose.onNodeWithTag("filter_outlets").performClick()
        val image=compose.onNodeWithTag("room_tag_dialog").captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"room-tags-footer-1.14.1.png")
            .outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithTag("close_room_tags").performClick()
        compose.onNodeWithTag("filter_badge").assertDoesNotExist()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithTag("filter_tag_靠窗").assertIsSelected()
        compose.onNodeWithTag("filter_outlets").assertIsSelected()
        compose.onNodeWithTag("reset_room_tags").performClick()
        compose.onNodeWithTag("filter_tag_靠窗").assertIsNotSelected()
        compose.onNodeWithTag("filter_outlets").assertIsNotSelected()
        compose.onNodeWithTag("close_room_tags").performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("extra_filters"))
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("filter_tag_靠窗").assertIsNotSelected()
        compose.onNodeWithTag("filter_outlets").assertIsNotSelected()
    }

    @Test fun roomTagEditingRestoresDeleteConfirmationAndTheSharedCatalog() {
        enterDemo();revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithTag("filter_outlets").performClick().assertIsSelected()
        compose.onNodeWithTag("edit_tags").performClick()
        compose.onNodeWithContentDescription("删除词条 有插座").assertIsDisplayed().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("filter_outlets").assertIsSelected()
        compose.onNodeWithContentDescription("删除词条 有插座").performClick()
        compose.onNodeWithTag("confirm_delete_tag").performClick()
        compose.onNodeWithTag("filter_outlets").assertDoesNotExist()
        compose.onNodeWithTag("custom_tag_name").performTextInput("有插座")
        compose.onNodeWithTag("add_tag").performClick()
        compose.onNodeWithTag("filter_outlets").assertIsSelected()
        compose.onNodeWithTag("close_room_tags").performClick()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("filter_outlets").assertIsNotSelected()
        compose.onNodeWithTag("filter_favorite").assertExists()
    }

    @Test fun roomTagsReorderOnlyAfterLongPressAndTheOrderIsSharedWithFilters() {
        enterDemo();revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithTag("edit_tags").performClick()
        val source=compose.onNodeWithTag("filter_outlets").fetchSemanticsNode().boundsInRoot
        val target=compose.onNodeWithTag("filter_tag_人少").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("filter_outlets").performTouchInput {
            down(center);moveTo(target.center-source.topLeft,delayMillis=100);up()
        }
        val unchanged=compose.onNodeWithTag("filter_outlets").fetchSemanticsNode().boundsInRoot
        assertEquals(source.left,unchanged.left,1f)
        assertEquals(source.top,unchanged.top,1f)
        compose.onNodeWithTag("filter_outlets").performTouchInput {
            down(center);advanceEventTime(650);moveTo(target.center-source.topLeft,delayMillis=200);up()
        }
        val after=compose.onNodeWithTag("filter_outlets").fetchSemanticsNode().boundsInRoot
        val people=compose.onNodeWithTag("filter_tag_人少").fetchSemanticsNode().boundsInRoot
        assertTrue(after.top>people.top||(after.top==people.top&&after.left>people.left))
        val image=compose.onNodeWithTag("room_tag_dialog").captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir,"room-tags-1.12.1.png")
            .outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithTag("close_room_tags").performClick()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        val restored=compose.onNodeWithTag("filter_outlets").fetchSemanticsNode().boundsInRoot
        val restoredPeople=compose.onNodeWithTag("filter_tag_人少").fetchSemanticsNode().boundsInRoot
        assertTrue(restored.top>restoredPeople.top||(restored.top==restoredPeople.top&&restored.left>restoredPeople.left))
    }

    @Test fun deletingOneRoomTagKeepsTheOtherChipsAndSelectionsVisibleInTheSameDialog() {
        enterDemo();revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithTag("filter_tag_人少").performClick().assertIsSelected()
        compose.onNodeWithTag("filter_tag_桌椅舒适").performClick().assertIsSelected()
        compose.onNodeWithTag("edit_tags").performClick()
        compose.onNodeWithContentDescription("删除词条 有插座").performTouchInput { click() }
        compose.onNodeWithTag("confirm_delete_tag").performTouchInput { click() }
        compose.onNodeWithTag("filter_outlets").assertDoesNotExist()
        compose.onNodeWithTag("filter_tag_人少").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("filter_tag_桌椅舒适").assertIsDisplayed().assertIsSelected()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"room-tags-after-delete-1.15.3.png")
            .outputStream().use {
                compose.onNodeWithTag("room_tag_dialog").captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        compose.onNodeWithContentDescription("删除词条 桌椅舒适").performTouchInput { click() }
        compose.onNodeWithTag("confirm_delete_tag").performTouchInput { click() }
        compose.onNodeWithTag("filter_tag_人少").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("filter_tag_桌椅舒适").assertDoesNotExist()
        compose.onNodeWithTag("close_room_tags").performClick()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithTag("filter_tag_人少").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("filter_outlets").assertDoesNotExist()
    }

    @Test fun cancelingAnActiveRoomTagDragRestoresOrderWithoutHidingOtherTags() {
        enterDemo();revealRoom("01:2-201")
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithTag("filter_outlets").performClick()
        compose.onNodeWithTag("edit_tags").performClick()
        val source=compose.onNodeWithTag("filter_outlets").fetchSemanticsNode().boundsInRoot
        val target=compose.onNodeWithTag("filter_tag_人少").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("filter_outlets").performTouchInput {
            down(center);advanceEventTime(650);moveTo(target.center-source.topLeft,delayMillis=200);cancel()
        }
        val restored=compose.onNodeWithTag("filter_outlets").assertIsDisplayed().assertIsSelected()
            .fetchSemanticsNode().boundsInRoot
        assertEquals(source.left,restored.left,1f);assertEquals(source.top,restored.top,1f)
        compose.onNodeWithTag("filter_tag_桌椅舒适").assertIsDisplayed()
        compose.onNodeWithTag("filter_tag_人少").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除词条 桌椅舒适").performClick()
        compose.onNodeWithTag("confirm_delete_tag").performClick()
        compose.onNodeWithTag("filter_outlets").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("filter_tag_人少").assertIsDisplayed()
    }

    @Test fun roomStripHoldInspectsTimeWithoutChangingFiltersOrCampus() {
        enterDemo()
        val id = "01:2-201"
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("room_row_$id"))
        val strip = compose.onNodeWithTag("room_period_strip_$id").fetchSemanticsNode().boundsInRoot
        val face = compose.onNodeWithTag("room_period_${id}_6", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("room_period_strip_$id").performTouchInput { down(face.center - strip.topLeft) }
        compose.waitUntil(4_000) { compose.onAllNodesWithTag("room_time_${id}_tooltip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("room_time_${id}_label_6").assertTextEquals("6 · 13:00–13:45")
        compose.onNodeWithTag("room_period_strip_$id").performTouchInput {
            moveTo(face.center - strip.topLeft + androidx.compose.ui.geometry.Offset(0f, -150f)); up()
        }
        compose.onNodeWithTag("room_time_${id}_tooltip").assertDoesNotExist()
        compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("slot_1"))
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("filter_badge").assertDoesNotExist()
    }

    @Test fun excludedFavoritesStayVisibleWithoutPeriodsAndReturnWhenPeriodsAreCleared() {
        enterDemo()
        compose.onNodeWithTag("clear_slots").performScrollTo().performClick()
        revealRoom("01:2-240")
        compose.onNodeWithContentDescription("收藏 2-240").performClick()
        revealRoom("01:2-240")
        compose.onNodeWithContentDescription("排除 2-240").performClick()
        compose.onNodeWithTag("room_actions_01:2-240").assertDoesNotExist()
        revealRoom("01:2-240")
        compose.onNodeWithContentDescription("取消排除 2-240").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消收藏 2-240").assertIsDisplayed()
        compose.onNodeWithTag("room_row_01:2-240").performTouchInput { swipeRight() }
        compose.onNodeWithTag("room_excluded_01:2-240", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("可选教室"))
        val favorite = compose.onNodeWithTag("room_row_01:2-240").fetchSemanticsNode().boundsInRoot
        val ordinary = compose.onNodeWithTag("room_row_01:2-201").fetchSemanticsNode().boundsInRoot
        assertTrue(favorite.top < ordinary.top)
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("slot_1"))
        compose.onNodeWithTag("slot_1").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("2-240").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("clear_slots").performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("room_row_01:2-240"))
        compose.onNodeWithTag("room_excluded_01:2-240", useUnmergedTree = true).assertIsDisplayed()
        revealRoom("01:2-240")
        compose.onNodeWithContentDescription("取消收藏 2-240").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消排除 2-240").performClick()
        compose.onNodeWithTag("room_actions_01:2-240").assertDoesNotExist()
        revealRoom("01:2-240")
        compose.onNodeWithContentDescription("排除 2-240").assertIsDisplayed()
        compose.onNodeWithTag("room_excluded_01:2-240", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("slot_1"))
        compose.onNodeWithTag("slot_1").performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("room_row_01:2-240"))
        compose.onNodeWithText("2-240").assertIsDisplayed()
    }

    @Test fun excludingAndRestoringABrowsedRoomBothCloseTheActionsInBothCampuses() {
        enterDemo()
        compose.onNodeWithTag("clear_slots").performScrollTo().performClick()
        for ((id, name) in listOf("01:2-201" to "2-201", "04:演示楼-A101" to "演示楼-A101")) {
            if (id.startsWith("04:")) compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
            revealRoom(id)
            compose.onNodeWithContentDescription("排除 $name").performClick()
            compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
            compose.onNodeWithTag("room_row_$id").assertIsDisplayed()
            compose.onNodeWithTag("room_excluded_$id", useUnmergedTree = true).assertExists()
            revealRoom(id)
            compose.onNodeWithContentDescription("取消排除 $name").performClick()
            compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
            compose.onNodeWithTag("room_excluded_$id", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test fun tagAndNoteEditsKeepTheRailOpenUntilTheirCloseButtonsInBothCampuses() {
        enterDemo()
        for ((id, name) in listOf("01:2-201" to "2-201", "04:演示楼-A101" to "演示楼-A101")) {
            if (id.startsWith("04:")) compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
            revealRoom(id)
            val openX = compose.onNodeWithTag("room_face_$id").fetchSemanticsNode().positionInRoot.x
            compose.onNodeWithContentDescription("修改标签 $name").performClick()
            compose.onNodeWithTag("room_tag_dialog").assertIsDisplayed()
            compose.onNodeWithTag("room_actions_$id").assertExists()
            compose.onNodeWithTag("filter_outlets").performClick()
            assertEquals(openX, compose.onNodeWithTag("room_face_$id").fetchSemanticsNode().positionInRoot.x, 1f)
            compose.onNodeWithTag("close_room_tags").performClick()
            compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
            revealRoom(id)
            compose.onNodeWithContentDescription("修改备注 $name").performClick()
            compose.onNodeWithTag("room_note_dialog").assertIsDisplayed()
            compose.onNodeWithTag("room_note").performTextInput("立即更新的备注")
            compose.onNodeWithTag("room_actions_$id").assertExists()
            assertEquals(openX, compose.onNodeWithTag("room_face_$id").fetchSemanticsNode().positionInRoot.x, 1f)
            compose.onNodeWithTag("close_room_note").performClick()
            compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
            compose.onNode(hasText("立即更新的备注") and hasAnyAncestor(hasTestTag("room_row_$id"))).assertIsDisplayed()
        }
    }

    @Test fun backingOutOfEitherEditorAlsoClosesOnlyItsMatchingRail() {
        enterDemo()
        for ((id, name, kind, dialog) in listOf(
            listOf("01:2-201", "2-201", "标签", "room_tag_dialog"),
            listOf("01:2-240", "2-240", "备注", "room_note_dialog"))) {
            revealRoom(id)
            compose.onNodeWithContentDescription("修改$kind $name").performClick()
            compose.onNodeWithTag(dialog).assertIsDisplayed()
            compose.onNodeWithTag("room_actions_$id").assertExists()
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation
                .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            // System navigation is asynchronous and outside Compose's frame clock.
            compose.waitUntil(10_000) { compose.onAllNodesWithTag(dialog).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag(dialog).assertDoesNotExist()
            compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
            assertEquals(compose.onNodeWithTag("room_row_$id").fetchSemanticsNode().positionInRoot.x,
                compose.onNodeWithTag("room_face_$id").fetchSemanticsNode().positionInRoot.x, 1f)
        }
    }

    @Test fun exclusionRemovesTheResultAndCanBeReversedInPreferencesWithoutLosingFavorite() {
        enterDemo()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("收藏 2-201").performClick()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("排除 2-201").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("2-201").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_room_marks").performClick()
        compose.onNodeWithTag("room_excluded_01:2-201", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2-201").performClick()
        compose.onNodeWithTag("room_actions_dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消收藏 2-201").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消排除 2-201").performClick()
        compose.onNodeWithContentDescription("排除 2-201").assertIsDisplayed()
        compose.onNodeWithTag("close_room_actions").performClick()
        compose.onNodeWithTag("room_excluded_01:2-201", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("settings_sheet_close").performClick()
        compose.onNodeWithTag("tab_rooms").performClick()
        revealRoom("01:2-201")
        compose.onNodeWithContentDescription("取消收藏 2-201").assertIsDisplayed()
    }

    @Test fun revealingAnotherRowClosesTheFirstAndDoesNotChangeCampusesOrPeriods() {
        enterDemo()
        revealRoom("01:2-201")
        revealRoom("01:2-240")
        compose.onNodeWithTag("room_actions_01:2-201").assertDoesNotExist()
        compose.onNodeWithTag("room_actions_01:2-240").assertIsDisplayed()
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("room_row_01:2-240").performTouchInput { swipeRight() }
        compose.onNodeWithTag("room_actions_01:2-240").assertDoesNotExist()
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("slot_1"))
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
    }

    @Test fun campusTransitionMovesOnlyTheHeaderAndKeepsDividerAndContentAnchored() {
        enterDemo()
        val initialHeader = compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        val initialDivider = compose.onNodeWithTag("campus_divider").fetchSemanticsNode().boundsInRoot
        val initialBody = compose.onNodeWithTag("campus_body").fetchSemanticsNode().boundsInRoot
        val initialGrid = compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("campus_swipe_region").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { assertTrue(action.action()) }
        compose.mainClock.advanceTimeBy(160)
        val moving = compose.onAllNodesWithTag("campus_header").fetchSemanticsNodes()
        // The offscreen, precomposed page is hidden from accessibility/test queries.
        assertTrue(moving.isNotEmpty())
        assertTrue(moving.any {
            val bounds = it.boundsInRoot
            kotlin.math.abs(bounds.left - initialHeader.left) > 1f || bounds.width < initialHeader.width * .9f
        })
        assertEquals(initialDivider, compose.onNodeWithTag("campus_divider").fetchSemanticsNode().boundsInRoot)
        assertEquals(initialBody, compose.onNodeWithTag("campus_body").fetchSemanticsNode().boundsInRoot)
        assertEquals(initialGrid, compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("room_row_04:演示楼-A101").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("演示楼-A101"))
        compose.onNodeWithText("2-201").assertDoesNotExist()
        assertEquals(initialDivider, compose.onNodeWithTag("campus_divider").fetchSemanticsNode().boundsInRoot)
        assertEquals(initialBody, compose.onNodeWithTag("campus_body").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun allThreeTabsShareOneStationaryHeaderWhileOnlyBodiesSlide() {
        enterDemo()
        val header = compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        val divider = compose.onNodeWithTag("campus_divider").fetchSemanticsNode().boundsInRoot
        for ((tag, page) in listOf("tab_planner" to 1, "tab_settings" to 2, "tab_rooms" to 0)) {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag(tag).performClick()
            compose.mainClock.advanceTimeBy(144)
            compose.onAllNodesWithTag("campus_header").assertCountEquals(1)
            assertEquals(header, compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot)
            assertEquals(divider, compose.onNodeWithTag("campus_divider").fetchSemanticsNode().boundsInRoot)
            val incoming = compose.onNodeWithTag("workspace_page_$page").fetchSemanticsNode().boundsInRoot
            val body = compose.onNodeWithTag("workspace_pages").fetchSemanticsNode().boundsInRoot
            assertTrue("incoming page must translate", kotlin.math.abs(incoming.left - body.left) > 1f || incoming.width < body.width - 1f)
            compose.mainClock.advanceTimeBy(1400)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertEquals(header, compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot)
            compose.onAllNodesWithTag("campus_header").assertCountEquals(1)
        }
    }

    @Test fun settingsHeaderIsFixedWhileScrollingAndCanSwitchCampuses() {
        enterDemo()
        compose.onNodeWithTag("tab_settings").performClick()
        val header = compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_about"))
        assertEquals(header, compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        compose.onNodeWithTag("settings_campus_body_content_04").assertExists()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
    }

    @Test fun plannerCampusBodyFadesWithoutMovingAndKeepsTheSameTimeWheelNodes() {
        enterDemo()
        compose.onNodeWithTag("tab_planner").performClick()
        val body = compose.onNodeWithTag("planner_campus_body").fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag("planner_start_hour_wheel").fetchSemanticsNode()
        val initialTime = compose.onNodeWithTag("planner_start").fetchSemanticsNode().config[SemanticsProperties.Text]
        val action = compose.onNodeWithTag("campus_swipe_region").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { action.action() }
        compose.mainClock.advanceTimeBy(240)
        assertEquals(body, compose.onNodeWithTag("planner_campus_body").fetchSemanticsNode().boundsInRoot)
        assertEquals(start.boundsInRoot, compose.onNodeWithTag("planner_start_hour_wheel").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        assertEquals(start.id, compose.onNodeWithTag("planner_start_hour_wheel").fetchSemanticsNode().id)
        assertEquals(initialTime, compose.onNodeWithTag("planner_start").fetchSemanticsNode().config[SemanticsProperties.Text])
        compose.onNodeWithTag("planner_campus_body_content_04").assertExists()
    }

    @Test fun rapidTabRetargetingEndsWithOneHeaderAndTheLatestInteractivePage() {
        enterDemo()
        compose.onNodeWithTag("tab_planner").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tab_planner").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("tab_settings").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("tab_rooms").performClick()
        compose.mainClock.advanceTimeBy(1600)
        compose.mainClock.autoAdvance = true
        compose.onAllNodesWithTag("campus_header").assertCountEquals(1)
        compose.onNodeWithTag("planner_screen").assertDoesNotExist()
        compose.onNodeWithTag("settings_list").assertDoesNotExist()
        compose.onNodeWithTag("slot_1").assertIsEnabled()
    }

    @Test fun reversingBeforeTheBodySwapKeepsWestContentAndSharedSlots() {
        enterDemo()
        compose.onNodeWithTag("clear_slots").performClick()
        compose.onNodeWithTag("slot_6").performClick()
        val toShahe = compose.onNodeWithTag("campus_swipe_region").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { assertTrue(toShahe.action()) }
        compose.mainClock.advanceTimeBy(208)
        val toWest = compose.onNodeWithTag("campus_swipe_region").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single()
        assertEquals("切换到西土城校区", toWest.label)
        compose.runOnIdle { assertTrue(toWest.action()) }
        compose.mainClock.advanceTimeBy(800)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("slot_6").assertIsSelected()
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("演示楼-A101").assertDoesNotExist()
    }

    @Test fun reversingDuringIncomingFadeFinishesWithTheLatestCampusAndSelection() {
        enterDemo()
        compose.onNodeWithTag("clear_slots").performClick()
        compose.onNodeWithTag("slot_6").performClick()
        val toShahe = compose.onNodeWithTag("campus_swipe_region").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { assertTrue(toShahe.action()) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        // The incoming grid is visible but cannot edit either campus mid-handoff.
        compose.onNodeWithTag("slot_6").assertIsNotEnabled()
        val toWest = compose.onNodeWithTag("campus_swipe_region").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single()
        compose.runOnIdle { assertTrue(toWest.action()) }
        compose.mainClock.advanceTimeBy(800)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("slot_6").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("演示楼-A101").assertDoesNotExist()
    }

    @Test fun termDeletionRequiresConfirmationAndFavoriteCannotBeDeleted() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("filter_outlets").performClick()
        compose.onNodeWithTag("edit_tags").performClick()
        compose.onNodeWithContentDescription("删除词条 已收藏").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除词条 有插座").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("filter_outlets").assertExists()
        compose.onNodeWithContentDescription("删除词条 有插座").performClick()
        compose.onNodeWithTag("confirm_delete_tag").performClick()
        compose.onNodeWithTag("filter_outlets").assertDoesNotExist()
        compose.onNodeWithTag("filter_favorite").assertIsDisplayed()
        compose.onNodeWithTag("filter_tag_桌椅舒适").assertIsDisplayed()
        compose.onNodeWithTag("filter_tag_人少").assertIsDisplayed()
        compose.onNodeWithTag("filter_badge").assertDoesNotExist()
    }

    @Test fun editableTermsCanBeDraggedAndAccessibleOrderIsRestoredOnReopen() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("edit_tags").performClick()
        val source = compose.onNodeWithTag("filter_outlets").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("filter_tag_人少").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("filter_outlets").performTouchInput {
            down(center)
            advanceEventTime(650)
            moveTo(target.center - source.topLeft, delayMillis = 200)
            up()
        }
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("extra_filters").performClick()
        val outlets = compose.onNodeWithTag("filter_outlets").fetchSemanticsNode().boundsInRoot
        val people = compose.onNodeWithTag("filter_tag_人少").fetchSemanticsNode().boundsInRoot
        assertTrue(outlets.top > people.top || (outlets.top == people.top && outlets.left > people.left))
    }

    @Test fun buildingRowsAndCampusHeadingHaveTheRequestedLayout() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("filter_campus").assertTextEquals("西土城")
        compose.onNodeWithTag("building_all").assertTextEquals("全部教学楼")
        compose.onNodeWithTag("building_row_1").assertExists()
        compose.onNodeWithTag("building_row_2").assertDoesNotExist()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("filter_campus").assertTextEquals("沙河")
        compose.onNodeWithTag("building_row_0").assertExists()
        compose.onNodeWithTag("building_row_1").assertDoesNotExist()
    }
}
