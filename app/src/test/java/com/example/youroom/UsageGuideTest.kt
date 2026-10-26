package com.example.youroom

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Rect
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelStore
import com.example.youroom.data.*
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class UsageGuideTest {
    private fun expandedStep(index: Int)=when { index<=28 -> index;index<=30 -> index+4;else -> index+6 }
    @Test fun sixNewLessonsAndLegacyProgressUseTheApprovedFortyNineStepOrder() {
        assertEquals(listOf(30,31,32,33,34,35,36,37,38),listOf(GuideStage.WAIT_CONTENT,GuideStage.READER,
            GuideStage.DINO,GuideStage.WAIT_RESULT,GuideStage.RESULTS,GuideStage.ALTERNATIVES,
            GuideStage.HISTORY,GuideStage.HISTORY_REPLAY,GuideStage.THEME).map { it.ordinal+1 })
        assertEquals(49,GuideStage.entries.size)
        for(old in 0..42) for(outcome in GuideOutcome.entries) {
            val migrated=GuideRecord(old,outcome,4).current()
            assertEquals(when { old<=28 -> old;old<=30 -> old+4;else -> old+6 },migrated.step)
            assertEquals(outcome,migrated.outcome)
            assertEquals(5,migrated.version)
            assertEquals(outcome==GuideOutcome.IN_PROGRESS,GuideHistory(records=mapOf("test" to migrated)).shouldOffer("test"))
        }
    }
    @Test fun replayAndAboutSwapPreservesProgressAndOutcomes() {
        assertEquals(44,GuideStage.REPLAY.ordinal)
        assertEquals(45,GuideStage.ABOUT.ordinal)
        assertEquals("settings_about",GuideStage.ABOUT.target)
        assertEquals("root",GuideStage.ABOUT.window)
        assertFalse(GuideStage.ABOUT.practice)
        for(old in 0..42) for(outcome in GuideOutcome.entries) {
            val record=GuideRecord(old,outcome,3).current()
            val swapped=when(old) {38->39;39->38;else->old}
            assertEquals(when { swapped<=28 -> swapped;swapped<=30 -> swapped+4;else -> swapped+6 },record.step)
            assertEquals(outcome,record.outcome)
            assertEquals(GuideHistory.Version,record.version)
            assertEquals(record,record.current())
            assertEquals(outcome==GuideOutcome.IN_PROGRESS,
                GuideHistory(records=mapOf("test" to record)).shouldOffer("test"))
        }
    }
    private fun reveal(runtime:GuideRuntime) {
        runtime.spotlightTargets.forEach { runtime.anchors[it]=GuideAnchor("root",Rect(10f,10f,100f,100f),Any()) }
        runtime.finishPreparation(runtime.stage)
        assertFalse(runtime.preparing)
    }
    @Test fun automaticStateChangesAndSkippingNeverCountAsPracticeActions()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.FAVORITE.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare();runCurrent()
            val room=runtime.data.snapshot(Campus.XITUCHENG).classrooms.first()
            // Both pending setup and late model updates after reveal lack an action.
            runtime.model.toggleFavorite(room);runCurrent();runtime.observe()
            assertFalse(runtime.done)
            reveal(runtime);runtime.observe();assertFalse(runtime.done)
            runtime.next();runtime.prepare();runCurrent()
            runtime.select(GuideStage.FAVORITE.ordinal);runtime.prepare();runCurrent();reveal(runtime)
            runtime.observe();assertFalse(runtime.done)
            runtime.recordAction(GuideStage.SLOTS) // A stale callback from another lesson.
            runtime.model.toggleFavorite(room);runCurrent();runtime.observe();assertFalse(runtime.done)
            runtime.model.toggleFavorite(room);runCurrent()
            runtime.recordAction(GuideStage.FAVORITE);runtime.model.toggleFavorite(room);runCurrent();runtime.observe()
            assertTrue(runtime.done)
            runtime.next();runtime.select(GuideStage.FAVORITE.ordinal);runtime.prepare();runCurrent()
            assertTrue(runtime.done)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun filterLessonsShareOneDialogFamilyInBothDirections()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.BUILDINGS.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent()
            repeat(4) {
                runtime.select(GuideStage.BUILDINGS.ordinal)
                assertEquals(GuideStage.BUILDINGS,runtime.dialogStage)
                runtime.next()
                assertEquals(GuideStage.FILTER_TAGS,runtime.stage)
                assertEquals(GuideStage.BUILDINGS,runtime.dialogStage)
            }
        } finally { runtime.close();Dispatchers.resetMain() }
    }
    @Test fun destinationWindowRejectsStaleModalsBeforeCleanupEffects()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(0),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent()
            val modalWindows=listOf("filter","sheet","alert","expected","period","room-editor")
            for(stage in listOf(GuideStage.SWIPE,GuideStage.PAST,GuideStage.EXPECT,GuideStage.FLEXIBLE,GuideStage.ABOUT)) {
                runtime.select(stage.ordinal)
                assertEquals("root",runtime.window)
                assertTrue("Stale popup accepted by $stage",modalWindows.none(runtime::acceptsDialog))
            }
            runtime.select(GuideStage.REPLACE.ordinal)
            assertTrue(runtime.acceptsDialog("alert"));assertTrue(runtime.acceptsDialog("sheet"))
            assertEquals(GuideStage.IMPORT,runtime.dialogStage)
            runtime.select(GuideStage.IMPORT_PICK.ordinal)
            assertFalse(runtime.acceptsDialog("alert"));assertTrue(runtime.acceptsDialog("sheet"))
            runtime.select(GuideStage.EXPECT.ordinal);runtime.openedExpected()
            assertTrue(runtime.acceptsDialog("expected"))
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun flexibleLessonAndStaleGenerateCallbacksNeverStartPlanning()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.FLEXIBLE.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare();runCurrent()
            repeat(3) { runtime.requestPlanning();runtime.demonstrate();runCurrent() }
            assertTrue(runtime.model.planning.executions.value.isEmpty())
            runtime.select(GuideStage.GENERATE.ordinal);runtime.prepare();runCurrent()
            runtime.requestPlanning() // No requests during preparation, either.
            assertTrue(runtime.model.planning.executions.value.isEmpty())
            runtime.anchors[runtime.target]=GuideAnchor("root",Rect(10f,10f,40f,40f),Any())
            runtime.finishPreparation(GuideStage.GENERATE)
            runtime.requestPlanning()
            assertTrue(runtime.model.planning.executions.value.isNotEmpty())
            runtime.select(GuideStage.FLEXIBLE.ordinal)
            assertTrue(runtime.model.planning.executions.value.isEmpty())
            runtime.requestPlanning();runCurrent()
            assertTrue(runtime.model.planning.executions.value.isEmpty())
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun settingsGroupsAboutEntranceAndSharedPreferencesKeepTheSameLessons()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.STRIP.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare()
            assertTrue(runtime.fixedSettingsPosition)
            assertEquals(listOf("settings_strip_layout_group"),runtime.spotlightTargets)
            assertEquals("可切换两种时段条样式，“等间距”更清爽，“等比例”更符合直觉",runtime.stage.instruction)
            runtime.select(GuideStage.DEFAULT_CAMPUS.ordinal);runtime.prepare()
            assertTrue(runtime.fixedSettingsPosition)
            assertEquals(listOf("settings_default_campus_group"),runtime.spotlightTargets)
            runtime.select(GuideStage.PLANNING.ordinal);runtime.prepare();runtime.enteredPlanning()
            assertFalse(runtime.fixedSettingsPosition)
            assertEquals(listOf("planning_default_direction_group"),runtime.spotlightTargets)
            val family=runtime.dialogStage
            runtime.select(GuideStage.TEMPLATES.ordinal);runtime.prepare()
            assertEquals(family,runtime.dialogStage)
            assertEquals("planning_templates_entry",runtime.target)
            runtime.select(GuideStage.ABOUT.ordinal);runtime.prepare()
            assertEquals("settings_about",runtime.target);assertEquals("root",runtime.window)
            runtime.demonstrate()
            assertEquals("settings_about",runtime.target);assertEquals("root",runtime.window)
            assertEquals(0,runtime.entry)
            assertEquals(45,runtime.position.intValue)
            assertEquals(49,GuideStage.entries.size)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun plannerAndThemeEntrancesUseTheBottomTabsWithoutAddingLessons()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.WHEELS.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare()
            assertEquals("tab_planner",runtime.target);assertEquals(2,runtime.page)
            assertEquals(15,runtime.position.intValue)
            runtime.enteredPlannerPage()
            assertEquals("planner_wheels",runtime.target);assertEquals(1,runtime.page)
            assertEquals(15,runtime.position.intValue)
            runtime.select(GuideStage.THEME.ordinal);runtime.prepare()
            assertEquals("tab_settings",runtime.target);assertEquals(1,runtime.page)
            runtime.enteredSettings()
            assertEquals("settings_theme_belt",runtime.target);assertEquals(2,runtime.page)
            assertEquals(37,runtime.position.intValue)
            assertEquals(49,GuideStage.entries.size)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun importPreviewAndChoiceUseOneDialogStage()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.IMPORT.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare();runtime.openedImport()
            assertEquals(GuideStage.IMPORT,runtime.dialogStage)
            runtime.select(GuideStage.IMPORT_PICK.ordinal)
            assertEquals(GuideStage.IMPORT,runtime.dialogStage)
            runtime.prepare()
            assertEquals(GuideStage.IMPORT,runtime.dialogStage)
            assertEquals("template_import_guide",runtime.target)
            runtime.select(GuideStage.ABOUT.ordinal)
            assertEquals(GuideStage.ABOUT,runtime.dialogStage)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun expandingResultsAdvancesOnlyTheResultsLesson()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.RESULTS.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent()
            runtime.openedAlternatives()
            assertEquals(GuideStage.ALTERNATIVES,runtime.stage)
            runtime.openedAlternatives()
            assertEquals(GuideStage.ALTERNATIVES,runtime.stage)
            runtime.select(GuideStage.GENERATE.ordinal);runtime.openedAlternatives()
            assertEquals(GuideStage.GENERATE,runtime.stage)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun practiceNeverSeedsOtherFavoritesAcrossChaptersOrReturnTrips()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.INTRO.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent()
            val target=runtime.data.snapshot(Campus.XITUCHENG).classrooms.first()
            for(stage in GuideStage.entries) {
                runtime.select(stage.ordinal);runtime.prepare();runCurrent()
                assertTrue("Unexpected favorite at $stage",runtime.model.state.value.profiles.values.none {
                    it.favorite && it.roomId!=target.id
                })
                if(stage.page==1) assertTrue("Planning must not seed favorites at $stage",
                    runtime.model.state.value.profiles.values.none { it.favorite })
            }
            // Old practice state, including another campus, must also be cleaned
            // when returning from settings/planning to a classroom lesson.
            for(campus in Campus.entries) runtime.data.snapshot(campus).classrooms.drop(1).take(3).forEach {
                runtime.model.saveProfile(it.emptyProfile().copy(favorite=true,note="保留备注"))
            }
            runCurrent()
            runtime.select(GuideStage.FAVORITE.ordinal);runtime.prepare();runCurrent()
            assertTrue(runtime.model.state.value.profiles.values.none { it.favorite })
            assertTrue(runtime.model.state.value.profiles.values.any { it.note=="保留备注" })
            reveal(runtime);runtime.recordAction(GuideStage.FAVORITE)
            runtime.model.toggleFavorite(target);runCurrent();runtime.observe()
            assertTrue(runtime.done)
            runtime.select(GuideStage.NOTE.ordinal);runtime.prepare();runCurrent()
            assertEquals(listOf(target.id),runtime.model.state.value.profiles.values.filter { it.favorite }.map { it.roomId })
            runtime.select(GuideStage.GENERATE.ordinal);runtime.prepare();runCurrent()
            assertTrue(runtime.model.state.value.profiles.values.none { it.favorite })
            runtime.select(GuideStage.SWIPE.ordinal);runtime.prepare();runCurrent()
            assertTrue(runtime.model.state.value.profiles.values.none { it.favorite })
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun bothLegacyLayoutsMergeExpectedRoomsAndKeepAllLaterLessonIdentities() {
        for(old in 0..43) {
            val updated=GuideRecord(old,version=2).current()
            val merged=if(old>25) old-1 else old
            assertEquals(expandedStep(when(merged) {38->39;39->38;else->merged}),updated.step)
            assertEquals(GuideHistory.Version,updated.version)
            assertEquals(updated,updated.current())
        }
        assertEquals(GuideStage.EXPECT.ordinal,GuideRecord(26,version=2).current().step)
        assertFalse(GuideStage.entries.any { it.name=="EXPECT_PIN" })
        assertEquals("期望教室",GuideStage.EXPECT.title)
        assertEquals("书签按钮可选择临时想去的教室，但优先级低于收藏的教室。图钉按钮用于添加或取消期望教室，垃圾桶可清除全部选择",GuideStage.EXPECT.instruction)
        for(outcome in listOf(GuideOutcome.COMPLETED,GuideOutcome.SKIPPED))
            assertFalse(GuideHistory(records=mapOf("old" to GuideRecord(43,outcome,2))).shouldOffer("old"))
    }

    @Test fun onlySlotLessonTransitionsRetainTheVisualOpening()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.SLOTS.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare()
            val anchor=GuideAnchor("root",Rect(5f,20f,100f,80f),Any())
            runtime.anchors["slot_grid"]=anchor
            runtime.finishPreparation(GuideStage.SLOTS)
            for(stage in listOf(GuideStage.INSPECT,GuideStage.SLOTS)) {
                runtime.select(stage.ordinal)
                assertTrue(runtime.preparing);assertEquals(anchor,runtime.retainedOpening)
                runtime.prepare();runtime.finishPreparation(stage)
                assertFalse(runtime.preparing);assertNull(runtime.retainedOpening)
            }
            runtime.select(GuideStage.INSPECT.ordinal)
            assertEquals(anchor,runtime.retainedOpening)
            runtime.select(GuideStage.SEARCH.ordinal)
            assertNull(runtime.retainedOpening)
            assertTrue(runtime.preparing)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun settingsStepOrderCopyAndDeletedConfirmationAreFinalized() {
        assertEquals(49,GuideStage.entries.size)
        assertEquals(GuideStage.DEFAULT_CAMPUS,GuideStage.entries[40])
        assertEquals(GuideStage.NUMBERS,GuideStage.entries[41])
        assertEquals(GuideStage.CLEAR_CACHE,GuideStage.entries[47])
        assertEquals(GuideStage.FINISH,GuideStage.entries[48])
        assertFalse(GuideStage.entries.any { it.name=="LOGOUT_CONFIRM" })
        assertEquals("切换配色",GuideStage.THEME.title)
        assertEquals("进入设置页面，点击左右箭头或者滑动来切换配色。合适的色彩能带来愉悦、共鸣，也是一种自我表达哦",GuideStage.THEME.instruction)
        assertEquals("深色模式应用于整个应用，包括两个校区",GuideStage.DARK.instruction)
        assertEquals("开启后时段条显示节次数字，关闭则保持简洁",GuideStage.NUMBERS.instruction)
        assertEquals("选择进入时默认打开的校区，不会立即切换当前页面",GuideStage.DEFAULT_CAMPUS.instruction)
        assertEquals("规划偏好",GuideStage.PLANNING.title)
        assertEquals("点击管理规划偏好，默认偏好影响优先展示的方案导向",GuideStage.PLANNING.instruction)
        assertEquals("点击管理常用时段组合",GuideStage.TEMPLATES.instruction)
        assertEquals("这里有代码库、问题反馈链接和相关说明",GuideStage.ABOUT.instruction)
        assertEquals("退出登录",GuideStage.LOGOUT.title)
        assertEquals("退出登录仍保留个人使用数据和游戏最高分，规划历史仍会在次日清理",GuideStage.LOGOUT.instruction)
        assertEquals("清除账号密码及全部个人数据，包括规划历史和游戏最高分，需要登录账密校验，不可恢复，请谨慎选择",GuideStage.CLEAR_CACHE.instruction)
        assertEquals("感谢使用本应用，希望能够帮助到您，祝您学习生活愉快顺利！",GuideStage.FINISH.instruction)
    }

    @Test fun legacyProgressFollowsTheSameLessonWithoutReplayingCompletedGuides() {
        for(old in 0..44) {
            val previous=when(old) {35->36;36->35;42->41;43->42;44->43;else->old}
            val merged=if(previous>25) previous-1 else previous
            val expected=when(merged) {38->39;39->38;else->merged}
            val updated=GuideRecord(old,version=1).current()
            assertEquals(expandedStep(expected),updated.step)
            assertEquals(GuideHistory.Version,updated.version)
            assertEquals(updated,updated.current())
        }
        for(outcome in listOf(GuideOutcome.COMPLETED,GuideOutcome.SKIPPED)) {
            assertFalse(GuideHistory(records=mapOf("old" to GuideRecord(44,outcome,1))).shouldOffer("old"))
        }
        val values=mutablePreferencesOf(stringPreferencesKey("guide.records.v1") to
            """{"guest":{"step":35,"outcome":"IN_PROGRESS","version":1}}""")
        assertEquals(41,GuidePreferenceKeys.read(values).records.getValue("guest").step)
        GuidePreferenceKeys.write(values,"another",GuideRecord(1))
        assertEquals(41,GuidePreferenceKeys.read(values).records.getValue("guest").step)
    }

    @Test fun darkLessonResetsOnlyThePracticeThemeAndSettingsEntrancesStayInTheirSteps()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val original=AppearancePreferences(darkMode=true)
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.DARK.ordinal),original,PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare();runtime.select(GuideStage.STRIP.ordinal);runCurrent()
            assertEquals(false,runtime.model.state.value.appearance.darkMode)
            assertEquals(true,original.darkMode)
            runtime.select(GuideStage.PLANNING.ordinal);runtime.prepare()
            assertEquals("settings_planning",runtime.target);assertEquals("root",runtime.window)
            runtime.enteredPlanning()
            assertEquals("planning_default_direction_group",runtime.target);assertEquals("sheet",runtime.window)
            assertEquals(42,runtime.position.intValue)
            runtime.select(GuideStage.TEMPLATES.ordinal);runtime.prepare()
            assertEquals("planning_templates_entry",runtime.target)
            runtime.enteredTemplates();assertEquals("template_list",runtime.target)
            assertEquals(43,runtime.position.intValue)
            runtime.exitedTemplates();assertEquals("planning_templates_entry",runtime.target)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun practiceTemplatesAreOrderedAndUseTheRequestedTimesForBothCampuses() {
        for(campus in Campus.entries) {
            val templates=GuideData.preferences().forCampus(campus).templates
            assertEquals(listOf("指引练习","工作日","周末"),templates.map { it.name })
            assertEquals(GuideData.windows,templates[0].windows)
            assertEquals(listOf(StudyWindow(540,720),StudyWindow(840,1020)),templates[1].windows)
            assertEquals(listOf(StudyWindow(705,840)),templates[2].windows)
            assertTrue(templates.all { TimeTemplates.valid(it.windows) })
        }
    }

    @Test fun tagEntranceAndImportTransitionsDoNotAddStepsOrLoseTheChosenTemplate()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.FILTER_TAGS.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare()
            assertEquals("edit_tags",runtime.target)
            runtime.editingFilterTags(true)
            assertEquals("tag_editor",runtime.target);assertEquals(8,runtime.position.intValue)
            runtime.editingFilterTags(false);assertEquals("edit_tags",runtime.target)
            runtime.select(GuideStage.IMPORT_PICK.ordinal);runtime.prepare()
            runtime.pickedImportTemplate("guide-weekend")
            assertEquals(GuideStage.REPLACE,runtime.stage)
            assertEquals("guide-weekend",runtime.importTemplateId)
            runtime.prepare();assertEquals("guide-weekend",runtime.importTemplateId)
            val imported=runtime.goal.copy(savedWindows=listOf(StudyWindow(705,840)))
            runtime.updateGoal(imported)
            runtime.importedTemplate();assertEquals(GuideStage.EXPECT,runtime.stage)
            runtime.prepare();assertEquals(imported,runtime.goal)
            assertEquals(49,GuideStage.entries.size)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun preparationOnlyOpensForTheCurrentPreparedStepAndItsValidTarget()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.WHEELS.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare();runCurrent()
            assertTrue(runtime.preparing)
            runtime.finishPreparation(GuideStage.WHEELS)
            assertTrue("A missing target must keep preparation guarded",runtime.preparing)
            runtime.anchors[runtime.target]=GuideAnchor("root",Rect(0f,0f,100f,100f),Any())
            runtime.finishPreparation(GuideStage.WHEELS)
            assertFalse(runtime.preparing)
            runtime.select(GuideStage.PAST.ordinal)
            assertTrue(runtime.preparing)
            runtime.finishPreparation(GuideStage.WHEELS)
            assertTrue("The previous step cannot release a new jump",runtime.preparing)
            runtime.prepare();runCurrent()
            runtime.anchors[runtime.target]=GuideAnchor("other-window",Rect(0f,0f,100f,100f),Any())
            runtime.finishPreparation(GuideStage.PAST)
            assertTrue(runtime.preparing)
            runtime.anchors[runtime.target]=GuideAnchor("root",Rect(0f,0f,100f,100f),Any())
            runtime.anchors["planner_start_center"]=GuideAnchor("root",Rect(0f,0f,50f,50f),Any())
            runtime.finishPreparation(GuideStage.PAST)
            assertFalse(runtime.preparing)
            val goal=runtime.goal.copy(start=550)
            runtime.updateGoal(goal)
            runtime.select(GuideStage.PAST.ordinal)
            assertFalse("Selecting the same step must not re-prepare practice",runtime.preparing)
            assertEquals(goal,runtime.goal)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun editorAndMarksEntrancesAreSubstatesOfTheSameStep()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.TAGS.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare();runCurrent()
            assertNull(runtime.model.state.value.selectedRoomId)
            assertEquals("room_action_01:2-201_1",runtime.target);assertEquals("root",runtime.window)
            runtime.enteredEditor()
            assertEquals("room_tag_dialog",runtime.target);assertEquals("room-editor",runtime.window)
            runtime.exitedEditor();assertEquals(GuideStage.TAGS,runtime.stage)
            assertEquals("room_action_01:2-201_1",runtime.target)
            val state=runtime.model.state.value
            runtime.model.reorderPersonalTags(state.tagOrder)
            runtime.model.saveProfile(state.profiles.getValue("01:2-201").withoutPersonalTags());runCurrent()
            assertTrue("安静" in runtime.model.state.value.tagOrder)
            assertFalse(runtime.model.state.value.profiles.getValue("01:2-201").hasTerm("安静"))
            runtime.select(GuideStage.MARKS.ordinal);runtime.prepare();runCurrent()
            assertEquals("tab_settings",runtime.target);assertEquals(0,runtime.page)
            runtime.enteredSettings();assertEquals("settings_room_marks",runtime.target);assertEquals(2,runtime.page)
            runtime.enteredMarks();assertEquals("settings_marks_list",runtime.target);assertEquals("sheet",runtime.window)
            assertEquals(14,runtime.position.intValue);assertEquals(49,GuideStage.entries.size)
        } finally { runtime.close();Dispatchers.resetMain() }
    }
    @Test fun everyStepInstructionOmitsOnlyItsTrailingFullStop() {
        assertEquals(49,GuideStage.entries.size)
        assertTrue(GuideStage.entries.all { it.instruction.isNotBlank() && !it.instruction.endsWith("。") && !it.instruction.endsWith(".") })
        assertEquals("欢迎使用YOURoom！来看看有哪些功能以及如何操作吧",GuideStage.INTRO.instruction)
        assertEquals("点击“从现在起”把开始时间设为当前时间，此处假设是10:01，此操作不会修改结束时间",GuideStage.FROM_NOW.instruction)
    }

    @Test fun importAndExpectedPreviewStayInTheirOriginalSteps()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.IMPORT.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare()
            assertEquals("planner_add_period",runtime.target)
            runtime.openedImport()
            assertEquals("template_import_dialog",runtime.target);assertEquals("sheet",runtime.window)
            runtime.select(GuideStage.IMPORT_PICK.ordinal);runtime.prepare()
            assertEquals(listOf("template_import_guide","template_skip_invalid_option"),runtime.spotlightTargets)
            runtime.select(GuideStage.EXPECT.ordinal);runtime.prepare();runCurrent()
            assertEquals("planner_expected",runtime.target);assertEquals("root",runtime.window)
            runtime.openedExpected()
            assertEquals("expected_room_dialog",runtime.target);assertEquals("expected",runtime.window)
            assertEquals(25,runtime.position.intValue)
            assertTrue(runtime.goal.expectedRooms.isEmpty())
            assertEquals(49,GuideStage.entries.size)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun guidePortfolioHasMultipleRealAlternativesWithoutExpandingTheFullGraph() {
        val data=GuideData(GuideData.clock())
        for(campus in Campus.entries) for(flexible in listOf(false,true)) {
            val snapshot=data.snapshot(campus)
            val goal=PlanningSchedule.confirmedGoal(PlanningDraft(flexible=flexible,savedWindows=GuideData.windows,
                expectedRooms=setOf(snapshot.classrooms.first().id)))!!
            var work=0
            val result=StudyPlanner.portfolio(snapshot,campus,snapshot.queryDate,emptyMap(),goal) { work++ }
            assertTrue("Not enough alternatives in $campus/$flexible",result.options.size>1)
            assertTrue(result.groups.first { it.direction==PlanningDirection.BALANCED }.optionIndices.size>1)
            assertEquals(result.options.size,result.options.map { it.plan.signature }.distinct().size)
            assertTrue("Guide expanded too much work: $work",work<5_000)
        }
    }

    @Test fun flexibleInstructionUsesTheActualShiftAndLossLimits() {
        assertTrue(GuideStage.FLEXIBLE.instruction.contains("平移不超过${PlanningPolicy.shiftLimit}分钟"))
        assertTrue(GuideStage.FLEXIBLE.instruction.contains("单段最多缩短${PlanningPolicy.lossLimit}分钟"))
        assertTrue(GuideStage.FLEXIBLE.instruction.contains("多段合计${PlanningPolicy.multiLossLimit}分钟"))
    }

    @Test fun planningPeriodAndSaveUseTheConfirmedExampleWithoutASecondWheelJump()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.ADD.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare();runtime.demonstrate()
            val added=runtime.goal
            assertEquals(listOf(StudyWindow(660,720)),added.savedWindows)
            assertEquals(listOf("planner_add_period","planner_target_strip"),runtime.spotlightTargets)
            runtime.select(GuideStage.PERIOD.ordinal);runtime.prepare()
            assertEquals(added,runtime.goal)
            assertEquals("planner_target_range_0",runtime.target);assertEquals("root",runtime.window)
            runtime.openedPeriod()
            assertEquals("planner_period_actions_0",runtime.target);assertEquals("period",runtime.window)
            runtime.select(GuideStage.SAVE_TEMPLATE.ordinal);runtime.prepare()
            assertEquals(added,runtime.goal)
            assertEquals("planner_save_periods_0",runtime.target);assertEquals("period",runtime.window)
            runtime.openedTemplate()
            assertEquals("template_editor_content",runtime.target);assertEquals("sheet",runtime.window)
            assertEquals(49,GuideStage.entries.size)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun timeRejectionAndCorrectionsProgressInsideTheOriginalSteps()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(GuideStage.PAST.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();runtime.prepare()
            assertEquals(listOf("planner_add_period","planner_start_center"),runtime.spotlightTargets)
            runtime.rejectedPeriod(PlanningSchedule.add(runtime.goal,600).error?.problem)
            assertEquals(GuideStage.FROM_NOW,runtime.stage)
            assertEquals(listOf("planner_from_now"),runtime.spotlightTargets)
            val past=runtime.goal
            runtime.select(GuideStage.FROM_NOW.ordinal);runtime.prepare()
            assertEquals(past,runtime.goal);assertEquals("planner_from_now",runtime.target)
            runtime.demonstrate()
            assertEquals(601,runtime.goal.start);assertEquals(720,runtime.goal.end)
            assertEquals(listOf("planner_start_center"),runtime.spotlightTargets)
            runtime.select(GuideStage.DURATION.ordinal);runtime.prepare()
            assertEquals(PlanningDraft(601,600),runtime.goal)
            assertEquals("planner_add_period",runtime.target)
            runtime.demonstrate();assertEquals(1,runtime.entry)
            assertEquals("planner_duration_buttons",runtime.target)
            runtime.demonstrate()
            assertEquals(601,runtime.goal.start);assertEquals(721,runtime.goal.end)
            assertEquals(listOf("planner_start_center","planner_end_center"),runtime.spotlightTargets)
        } finally { runtime.close();Dispatchers.resetMain() }
    }

    @Test fun multipleSpotlightsSubtractTheirUnionWithoutOpeningTheGapBetweenThem() {
        val holes=listOf(Rect(10f,10f,40f,40f),Rect(30f,30f,60f,60f),Rect(75f,75f,95f,95f))
        val blockers=guideMultipleBlockers(100f,100f,holes)
        assertEquals(7900f,blockers.sumOf { (it.width*it.height).toDouble() }.toFloat(),0f)
        for(blocker in blockers) for(hole in holes) assertTrue(blocker.intersect(hole).let { it.width<=0 || it.height<=0 })
        assertTrue(blockers.any { it.contains(androidx.compose.ui.geometry.Offset(65f,65f)) })
        for((i,left) in blockers.withIndex()) for(right in blockers.drop(i+1))
            assertTrue(left.intersect(right).let { it.width<=0 || it.height<=0 })
        assertEquals(listOf(Rect(0f,0f,100f,100f)),guideMultipleBlockers(100f,100f,listOf(Rect(110f,110f,120f,120f))))
        assertEquals(listOf("slot_grid"),GuideStage.SLOTS.spotlightTargets)
        assertEquals(listOf("filter_choices","reset_filters"),GuideStage.BUILDINGS.spotlightTargets)
    }

    @Test fun completedPracticeCannotBeUndoneByReturningToInitialValuesOrRevisiting()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val position=mutableIntStateOf(GuideStage.CAMPUS.ordinal)
        val practice=GuideRuntime(position,AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();practice.prepare();runCurrent();practice.observe()
            assertFalse(practice.done)
            reveal(practice);practice.recordAction(GuideStage.CAMPUS)
            practice.model.setCampus(Campus.SHAHE);runCurrent();practice.observe()
            assertTrue(practice.done)
            practice.model.setCampus(Campus.XITUCHENG);runCurrent();practice.observe()
            assertTrue(practice.done)
            practice.select(GuideStage.SLOTS.ordinal);practice.prepare();runCurrent();practice.observe()
            assertFalse(practice.done)
            reveal(practice);practice.recordAction(GuideStage.SLOTS)
            practice.model.setFilters(SearchFilters(selectedSlots=setOf(1,2)));runCurrent();practice.observe()
            assertTrue(practice.done)
            practice.model.setFilters(SearchFilters());runCurrent();practice.observe()
            assertTrue(practice.done)
            practice.select(GuideStage.THEME.ordinal);practice.prepare();runCurrent();practice.observe()
            assertFalse(practice.done)
            reveal(practice);practice.demonstrate();runCurrent();practice.observe()
            assertTrue(practice.done)
            practice.model.setCampusTone(Campus.XITUCHENG,practice.initialTone);runCurrent();practice.observe()
            assertTrue(practice.done)
            for(stage in listOf(GuideStage.CAMPUS,GuideStage.SLOTS,GuideStage.THEME)) {
                practice.select(stage.ordinal);practice.prepare();runCurrent();practice.observe()
                assertTrue("Lost completion for $stage",practice.done)
            }
        } finally { practice.close();Dispatchers.resetMain() }
    }

    @Test fun accountKeysAreStableLocallySaltedAndNeverContainTheAccount() {
        val values=mutablePreferencesOf()
        val first=GuidePreferenceKeys.account(values,"  student-a  ")
        assertEquals(first,GuidePreferenceKeys.account(values,"student-a"))
        assertNotEquals(first,GuidePreferenceKeys.account(values,"student-b"))
        assertNotEquals(first,GuidePreferenceKeys.account(mutablePreferencesOf(),"student-a"))
        assertFalse(values.asMap().values.joinToString().contains("student-"))
        assertTrue(first.startsWith("account:"))
    }

    @Test fun recordsAreIndependentAndOnlyProgressHistoryChanges() {
        val values=mutablePreferencesOf(stringPreferencesKey("ordinary.setting") to "keep")
        val account=GuidePreferenceKeys.account(values,"account")
        GuidePreferenceKeys.write(values,GuideHistory.Guest,GuideRecord(4,GuideOutcome.SKIPPED))
        GuidePreferenceKeys.write(values,account,GuideRecord(19))
        val history=GuidePreferenceKeys.read(values)
        assertEquals("keep",values[stringPreferencesKey("ordinary.setting")])
        assertFalse(history.shouldOffer(GuideHistory.Guest))
        assertTrue(history.shouldOffer(account))
        assertEquals(19,history.records.getValue(account).step)
        assertEquals(account,history.activeAccount)
        GuidePreferenceKeys.write(values,account,GuideRecord(41,GuideOutcome.COMPLETED))
        assertFalse(GuidePreferenceKeys.read(values).shouldOffer(account))
        assertTrue(history.copy(records=mapOf(account to GuideRecord(version=0,outcome=GuideOutcome.COMPLETED))).shouldOffer(account))
    }

    @Test fun legacyIdentityMatchingNeverChangesTheActiveAccountOrGuideHistory() {
        val values=mutablePreferencesOf()
        assertNull(GuidePreferenceKeys.matchesActiveAccount(values,"sample"))
        GuidePreferenceKeys.account(values,"sample")
        GuidePreferenceKeys.write(values,"guest",GuideRecord(3,GuideOutcome.SKIPPED))
        val before=values.asMap().toMap()
        assertEquals(true,GuidePreferenceKeys.matchesActiveAccount(values," sample "))
        assertEquals(false,GuidePreferenceKeys.matchesActiveAccount(values,"different"))
        assertEquals(before,values.asMap())
    }

    @Test fun malformedRecordsDoNotBreakSettingsOrOtherEntries() {
        val key=stringPreferencesKey("guide.records.v1")
        val values=mutablePreferencesOf(key to "bad-json")
        assertTrue(GuidePreferenceKeys.read(values).records.isEmpty())
        values[key]="""{"bad":{"step":"oops"},"guest":{"step":-5,"outcome":"SKIPPED","version":1}}"""
        assertEquals(mapOf("guest" to GuideRecord(0,GuideOutcome.SKIPPED)),GuidePreferenceKeys.read(values).records)
    }

    @Test fun maskBlockersCoverOnlyTheAreaOutsideTheInteractiveHole() {
        val hole=Rect(20f,30f,70f,80f)
        val blockers=guideBlockers(100f,100f,hole)
        assertEquals(7500f,blockers.sumOf { (it.width*it.height).toDouble() }.toFloat(),0f)
        assertTrue(blockers.all { it.intersect(hole).let { part -> part.width<=0 || part.height<=0 } })
        assertEquals(listOf(Rect(0f,0f,100f,100f)),guideBlockers(100f,100f,null))
        assertEquals(emptyList<Rect>(),guideBlockers(100f,100f,Rect(-10f,-10f,110f,110f)))
    }

    @Test fun syntheticPracticeDateAndPlansAreStableInBothCampuses() {
        val clock=GuideData.clock(LocalDate.of(2026,10,21))
        assertEquals(10,java.time.LocalTime.now(clock).hour)
        assertEquals(PlanningProblem.PAST_TIME,PlanningSchedule.add(PlanningDraft(540,720),600).error?.problem)
        assertEquals(601,PlanningTimeActions.startFromNow(PlanningDraft(540,720),clock)?.start)
        for(campus in Campus.entries) {
            val snapshot=GuideData(clock).snapshot(campus)
            assertTrue(snapshot.isDemo)
            assertTrue(TimeTemplates.valid(GuideData.windows))
            val goal=PlanningSchedule.confirmedGoal(PlanningDraft(savedWindows=GuideData.windows))!!
            val result=StudyPlanner.portfolio(snapshot,campus,snapshot.queryDate,emptyMap(),goal)
            assertTrue("No practice routes for $campus: ${result.message}",result.options.isNotEmpty())
        }
    }

    private class Source(var authenticated:Boolean=false):ClassroomDataSource {
        override val profiles=MutableStateFlow<Map<String,RoomProfile>>(emptyMap())
        private val clock=GuideData.clock()
        override suspend fun hasSession()=authenticated
        override suspend fun login(account:String,password:String) { authenticated=true }
        override suspend fun logout() { authenticated=false }
        override suspend fun cached(campus:Campus):ClassroomSnapshot?=if(authenticated) DemoData.snapshot(campus,clock).copy(isDemo=false) else null
        override suspend fun refresh(campus:Campus)=DemoData.snapshot(campus,clock).copy(isDemo=false)
        override suspend fun saveProfile(profile:RoomProfile) { profiles.value+=profile.roomId to profile }
        override suspend fun deleteProfile(roomId:String) { profiles.value-=roomId }
    }
    private class Settings(private val practice:GuideSettings=GuideSettings(AppearancePreferences(),PeriodStripPreferences())):FilterSettings by practice {
        override val filters=practice.filters
        override val catalog=practice.catalog
        override val guideHistory=MutableStateFlow(GuideHistory(available=true))
        var failure=false
        override suspend fun guideAccountIdentity(account:String):String {
            val id="test-$account"
            guideHistory.value=guideHistory.value.copy(activeAccount=id)
            return id
        }
        override suspend fun saveGuideRecord(identity:String,record:GuideRecord) {
            if(failure) error("simulated storage error")
            guideHistory.value=guideHistory.value.copy(records=guideHistory.value.records+(identity to record))
        }
        override suspend fun save(filters:SearchFilters) { this.filters.value=filters }
        override suspend fun addTag(name:String)=Unit
        override suspend fun removeTag(name:String)=Unit
        override suspend fun reorderTags(names:List<String>)=Unit
    }
    private fun check(block:suspend TestScope.(YOURoomViewModel,Settings,Source)->Unit)=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings=Settings();val source=Source()
        val model=YOURoomViewModel(source,settings,GuideData.clock(),StandardTestDispatcher(testScheduler))
        val store=ViewModelStore().apply { put("guide",model) }
        try { runCurrent();block(model,settings,source) }
        finally { store.clear();Dispatchers.resetMain() }
    }

    @Test fun demoAndAccountsHaveSeparateFirstUseAndSkipIsNotOfferedAgain()=check { model,settings,_ ->
        assertNull(model.state.value.guideRequest)
        model.showDemo();runCurrent()
        val guest=model.state.value.guideRequest!!
        assertEquals(GuideHistory.Guest,guest.identity)
        model.saveGuideProgress(guest,3,GuideOutcome.SKIPPED);runCurrent()
        model.openLogin();model.showDemo();runCurrent()
        assertNull(model.state.value.guideRequest)
        model.openLogin();model.login("a","dummy");runCurrent()
        val first=model.state.value.guideRequest!!
        assertEquals("test-a",first.identity)
        model.saveGuideProgress(first,41,GuideOutcome.COMPLETED);runCurrent()
        model.logout();runCurrent();model.login("b","dummy");runCurrent()
        val second=model.state.value.guideRequest!!
        assertEquals("test-b",second.identity)
        model.saveGuideProgress(second,0,GuideOutcome.SKIPPED);runCurrent()
        model.logout();runCurrent();model.login("a","dummy");runCurrent()
        assertNull(model.state.value.guideRequest)
        assertEquals(3,settings.guideHistory.value.records.size)
    }

    @Test fun resumeManualReplayAndStaleCallbacksCannotCloseANewerGuide()=check { model,settings,_ ->
        settings.guideHistory.value=GuideHistory(true,records=mapOf(GuideHistory.Guest to GuideRecord(20)))
        runCurrent();model.showDemo();runCurrent()
        val resumed=model.state.value.guideRequest!!
        assertTrue(resumed.resume);assertEquals(20,resumed.step)
        model.startGuide()
        val manual=model.state.value.guideRequest!!
        assertTrue(manual.manual)
        model.saveGuideProgress(resumed,20,GuideOutcome.SKIPPED)
        assertEquals(manual,model.state.value.guideRequest)
        model.saveGuideProgress(manual,7,GuideOutcome.COMPLETED);runCurrent()
        assertNull(model.state.value.guideRequest)
        model.startGuide();assertTrue(model.state.value.guideRequest!!.manual)
    }

    @Test fun viewModelResumesLegacyProgressAtTheMappedLesson()=check { model,settings,_ ->
        settings.guideHistory.value=GuideHistory(true,records=mapOf(GuideHistory.Guest to GuideRecord(35,version=1)))
        runCurrent();model.showDemo();runCurrent()
        val request=model.state.value.guideRequest!!
        assertTrue(request.resume)
        assertEquals(GuideStage.NUMBERS.ordinal,request.step)
        model.saveGuideProgress(request,request.step,GuideOutcome.SKIPPED);runCurrent()
        assertEquals(GuideHistory.Version,settings.guideHistory.value.records.getValue(GuideHistory.Guest).version)
    }

    @Test fun aStorageFailureDoesNotTrapTheUserInAReplayLoop()=check { model,settings,_ ->
        settings.failure=true
        model.showDemo();runCurrent()
        model.saveGuideProgress(model.state.value.guideRequest!!,0,GuideOutcome.SKIPPED);runCurrent()
        model.openLogin();model.showDemo();runCurrent()
        assertNull(model.state.value.guideRequest)
    }

    @Test fun practiceViewModelUsesActualPastTimeValidationAndCannotStartANestedGuide()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val position=mutableIntStateOf(GuideStage.PAST.ordinal)
        val practice=GuideRuntime(position,AppearancePreferences(),PeriodStripPreferences())
        try {
            runCurrent();practice.prepare();runCurrent()
            assertTrue(practice.model.state.value.training)
            assertFalse(practice.model.state.value.demo)
            assertTrue(practice.model.state.value.campusSnapshot!!.isDemo)
            assertNull(practice.model.state.value.guideRequest)
            assertEquals(PlanningProblem.PAST_TIME,PlanningSchedule.add(practice.goal,600).error?.problem)
            practice.model.startGuide();assertNull(practice.model.state.value.guideRequest)
            practice.select(GuideStage.FROM_NOW.ordinal);practice.prepare();practice.demonstrate()
            assertEquals(601,practice.goal.start);assertEquals(720,practice.goal.end)
            practice.select(GuideStage.ADD.ordinal);practice.prepare();practice.demonstrate()
            assertEquals(listOf(StudyWindow(660,720)),practice.goal.savedWindows)
        } finally { practice.close();Dispatchers.resetMain() }
    }
}
