package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.youroom.data.GuideData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** This fixture owns only synthetic data. It never opens the installed app's
 * database, account, credentials or preferences. */
class UsageGuideUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var runtime:GuideRuntime
    private var visible by mutableStateOf(true)
    private val records=mutableListOf<Pair<Int,GuideOutcome>>()

    @Test fun everyPracticeHelperStillCompletesOnlyItsOwnLesson() {
        render()
        for(stage in GuideStage.entries.filter { it.practice }) {
            visit(stage,openEntry=false)
            compose.waitUntil(10_000) { !runtime.preparing }
            compose.onNodeWithTag("guide_demonstrate").assertTextEquals("替我演示").performClick()
            compose.waitUntil(15_000) { runtime.done }
            compose.onNodeWithTag("guide_demonstrate").assertContentDescriptionEquals("已完成操作")
            compose.mainClock.advanceTimeBy(10_000)
            assertEquals("A helper must never start auto-play",stage,runtime.stage)
        }
    }

    @Test fun filterLessonsKeepTheSameDialogAcrossRepeatedNextAndPrevious() {
        render(GuideStage.BUILDINGS);visit(GuideStage.BUILDINGS)
        val modal=runtime.visibleDialogs.single().value
        val node=compose.onNodeWithTag("guide_modal_filter").fetchSemanticsNode().id
        repeat(3) {
            for(stage in listOf(GuideStage.FILTER_TAGS,GuideStage.BUILDINGS)) {
                compose.mainClock.autoAdvance=false
                compose.runOnIdle { runtime.select(stage.ordinal) }
                repeat(18) {
                    compose.mainClock.advanceTimeByFrame()
                    assertSame(modal,runtime.visibleDialogs.single().value)
                    assertEquals(node,compose.onNodeWithTag("guide_modal_filter").fetchSemanticsNode().id)
                }
                compose.mainClock.autoAdvance=true
                compose.waitUntil(10_000) { !runtime.preparing && runtime.ready }
                if(stage==GuideStage.FILTER_TAGS) {
                    compose.onNodeWithTag("edit_tags").performTouchInput { click() }
                    compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
                }
            }
        }
    }

    @Test fun footerLessonsNeverDropTheirPanelToTheScreenBottomDuringPreparation() {
        render(GuideStage.LOGOUT);visit(GuideStage.LOGOUT)
        repeat(3) {
            for(stage in listOf(GuideStage.CLEAR_CACHE,GuideStage.LOGOUT)) {
                val target=runtime.anchors.getValue(stage.target).bounds
                compose.mainClock.autoAdvance=false
                compose.runOnIdle { runtime.select(stage.ordinal) }
                repeat(18) {
                    compose.mainClock.advanceTimeByFrame()
                    val panel=compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot
                    assertTrue("$stage panel fell below its button in frame $it",panel.bottom<target.top)
                    assertTrue("Panel should remain close above its button",target.top-panel.bottom<with(compose.density) { 25.dp.toPx() })
                }
                compose.mainClock.autoAdvance=true
                compose.waitUntil(10_000) { !runtime.preparing && runtime.ready }
            }
        }
        visit(GuideStage.FINISH)
        val panel=compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot
        val scrim=compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().boundsInRoot
        assertTrue(scrim.bottom-panel.bottom in 0f..100f)
        compose.onNodeWithTag("guide_finish_icon",useUnmergedTree=true).assertWidthIsEqualTo(18.dp).assertHeightIsEqualTo(18.dp)
        compose.onNodeWithTag("guide_next").assertContentDescriptionEquals("完成指引").performClick()
        assertEquals(GuideOutcome.COMPLETED,records.last().second)
    }

    @Test fun skippingFavoriteNeverCreatesACheckButARealActionStillDoes() {
        render(GuideStage.FAVORITE);visit(GuideStage.FAVORITE)
        repeat(3) {
            compose.onNodeWithTag("guide_demonstrate").assertTextEquals("替我演示")
            compose.onNodeWithTag("guide_next").performClick()
            compose.waitUntil(10_000) { runtime.stage==GuideStage.TAGS && !runtime.preparing }
            compose.onNodeWithTag("guide_previous").performClick()
            compose.waitUntil(10_000) { runtime.stage==GuideStage.FAVORITE && !runtime.preparing }
            assertFalse(runtime.done)
        }
        waitForRoomAction(0)
        compose.onNodeWithTag("room_action_01:2-201_0").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.done }
        visit(GuideStage.TAGS);visit(GuideStage.FAVORITE)
        compose.onNodeWithTag("guide_demonstrate").assertContentDescriptionEquals("已完成操作")
    }

    @Test fun leavingOpenPopupsNeverRemountsThemUnderTheDestinationLesson() {
        render()
        fun leave(source:GuideStage,destination:GuideStage,openEntry:Boolean=true) {
            visit(source,openEntry)
            if(source==GuideStage.EXPECT) {
                compose.onNodeWithTag("planner_expected").performTouchInput { click() }
                compose.waitUntil(10_000) { runtime.entry==1 && runtime.presentedWindow=="expected" }
                compose.waitForIdle()
            }
            assertTrue("Expected an open popup in $source",runtime.visibleDialogs.isNotEmpty())
            val scrim=compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().id
            compose.mainClock.autoAdvance=false
            compose.runOnIdle { runtime.select(destination.ordinal) }
            repeat(18) {
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    assertEquals("Popup reappeared in $source → $destination, frame $it","root",runtime.presentedWindow)
                    assertTrue(runtime.visibleDialogs.isEmpty())
                }
                assertEquals(scrim,compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().id)
                for(window in listOf("filter","sheet","alert","expected","period"))
                    compose.onNodeWithTag("guide_modal_$window").assertDoesNotExist()
            }
            compose.mainClock.autoAdvance=true
            compose.waitUntil(10_000) { !runtime.preparing && runtime.ready }
        }
        leave(GuideStage.FILTER_TAGS,GuideStage.SWIPE)
        leave(GuideStage.SAVE_TEMPLATE,GuideStage.PAST)
        leave(GuideStage.REPLACE,GuideStage.EXPECT)
        leave(GuideStage.EXPECT,GuideStage.FLEXIBLE)
        leave(GuideStage.TEMPLATES,GuideStage.ABOUT,openEntry=false)
        leave(GuideStage.TEMPLATES,GuideStage.ABOUT)
        shot("popup-exits-no-stale-remount")
    }

    @Test fun returningFromReplacementKeepsTheImporterButDropsItsConfirmation() {
        render(GuideStage.REPLACE);visit(GuideStage.REPLACE)
        val importer=runtime.visibleDialogs.first { it.value.window=="sheet" }.value
        val node=compose.onNodeWithTag("guide_modal_sheet").fetchSemanticsNode().id
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { runtime.select(GuideStage.IMPORT_PICK.ordinal) }
        repeat(18) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("guide_modal_alert").assertDoesNotExist()
            assertSame(importer,runtime.visibleDialogs.single().value)
            assertEquals(node,compose.onNodeWithTag("guide_modal_sheet").fetchSemanticsNode().id)
        }
        compose.mainClock.autoAdvance=true
        compose.waitUntil(10_000) { !runtime.preparing && runtime.ready }
        compose.onNodeWithTag("template_import_guide").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.stage==GuideStage.REPLACE && runtime.ready }
        compose.onNodeWithTag("template_replace_confirm").assertIsDisplayed()
        assertSame(importer,runtime.visibleDialogs.first { it.value.window=="sheet" }.value)
    }

    @Test fun flexibleLessonStaysIdleAndTemplateManagementIsEntirelyReadOnly() {
        render(GuideStage.EXPECT);visit(GuideStage.EXPECT)
        compose.onNodeWithTag("planner_expected").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        visit(GuideStage.FLEXIBLE)
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled().performClick()
        repeat(3) {
            compose.onNodeWithTag("planner_adjust").performTouchInput { click() }
            compose.runOnIdle { runtime.requestPlanning() }
            compose.mainClock.advanceTimeBy(1000)
            assertTrue(runtime.model.planning.executions.value.isEmpty())
        }
        visit(GuideStage.TEMPLATES)
        val before=runtime.model.state.value.planningPreferences
        for(id in listOf("guide","guide-weekday","guide-weekend")) {
            val row=compose.onNodeWithTag("template_$id").assertIsNotEnabled()
            row.performTouchInput { click();click(androidx.compose.ui.geometry.Offset(width/2f,height-1f)) }
            compose.onNodeWithTag("template_delete_$id").assertIsNotEnabled().performTouchInput { click() }
        }
        compose.onNodeWithTag("template_create").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("settings_sheet_close").assertIsNotEnabled()
        compose.onNodeWithTag("template_editor_content").assertDoesNotExist()
        compose.onNodeWithTag("template_delete_confirm").assertDoesNotExist()
        assertEquals(before,runtime.model.state.value.planningPreferences)
    }

    @Test fun settingsCutoutsAreRevealedOnlyAfterThePreviousOptionsHaveCollapsed() {
        render(GuideStage.STRIP);visit(GuideStage.STRIP)
        for(stage in listOf(GuideStage.DEFAULT_CAMPUS,GuideStage.NUMBERS)) {
            compose.mainClock.autoAdvance=false
            compose.runOnIdle {
                runtime.select(stage.ordinal)
                assertTrue(runtime.preparing);assertFalse(runtime.layoutReady)
                assertNull(runtime.retainedOpening)
            }
            var revealed:androidx.compose.ui.geometry.Rect?=null
            repeat(24) {
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    if(!runtime.preparing) {
                        assertTrue(runtime.layoutReady)
                        val current=runtime.anchors.getValue(runtime.target).bounds
                        if(revealed==null) revealed=current else assertEquals(revealed,current)
                    }
                }
                if(!runtime.preparing) {
                    compose.onNodeWithTag("settings_strip_EQUAL").assertDoesNotExist()
                    if(stage==GuideStage.NUMBERS) compose.onNodeWithTag("settings_default_04").assertDoesNotExist()
                }
            }
            compose.mainClock.autoAdvance=true
            compose.waitUntil(10_000) { !runtime.preparing && runtime.ready }
            assertNotNull(revealed)
        }
        shot("settings-cutouts-after-collapse")
    }

    @Test fun settingsChoiceLessonsUseOneOpeningAndKeepTheirScrollOriginAfterReturning() {
        render(GuideStage.STRIP);visit(GuideStage.STRIP)
        assertEquals(listOf("settings_strip_layout_group"),runtime.spotlightTargets)
        val origin=compose.onNodeWithTag("settings_strip_layout").fetchSemanticsNode().boundsInRoot.top
        fun containsOptions(group:String,options:List<String>) {
            val bounds=runtime.anchors.getValue(group).bounds
            assertEquals(compose.onNodeWithTag(group).fetchSemanticsNode().boundsInRoot,bounds)
            for(tag in options) {
                val option=compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
                assertTrue("$tag must fit inside $group",bounds.top<=option.top && bounds.bottom>=option.bottom)
            }
        }
        containsOptions("settings_strip_layout_group",listOf("settings_strip_EQUAL","settings_strip_TIME_PROPORTIONAL"))
        compose.onNodeWithTag("guide_instruction").assertTextEquals("可切换两种时段条样式，“等间距”更清爽，“等比例”更符合直觉")
        visit(GuideStage.DEFAULT_CAMPUS)
        assertEquals(listOf("settings_default_campus_group"),runtime.spotlightTargets)
        containsOptions("settings_default_campus_group",listOf("settings_default_01","settings_default_04"))
        compose.onNodeWithTag("settings_default_04").performTouchInput { click() }
        compose.waitUntil(5_000) { runtime.model.state.value.appearance.defaultCampus==Campus.SHAHE }
        visit(GuideStage.STRIP)
        assertEquals(origin,compose.onNodeWithTag("settings_strip_layout").fetchSemanticsNode().boundsInRoot.top,1f)
        repeat(3) {
            compose.onNodeWithTag("settings_strip_layout").performTouchInput { click() }
            compose.waitForIdle()
            assertEquals(origin,compose.onNodeWithTag("settings_strip_layout").fetchSemanticsNode().boundsInRoot.top,1f)
        }
        shot("settings-groups-fixed-origin")
    }

    @Test fun planningChoicesAreAllClickableAndTheNextLessonReusesTheSamePreferencesSheet() {
        render(GuideStage.PLANNING);visit(GuideStage.PLANNING)
        assertEquals(listOf("planning_default_direction_group"),runtime.spotlightTargets)
        fun chooseAll() {
            val bounds=runtime.anchors.getValue("planning_default_direction_group").bounds
            for(direction in PlanningDirection.entries) {
                val tag="planning_default_${direction.name}"
                val item=compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
                assertTrue("$direction must be inside the opening",bounds.top<=item.top && bounds.bottom>=item.bottom)
                compose.onNodeWithTag(tag).performTouchInput { click() }
                compose.waitUntil(5_000) { runtime.model.state.value.planningPreferences.forCampus(Campus.XITUCHENG).direction==direction }
            }
        }
        chooseAll()
        compose.onNodeWithTag("planning_default_direction").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithTag("planning_default_EXPECTED").assertDoesNotExist()
        compose.onNodeWithTag("planning_default_direction").performTouchInput { click() }
        compose.waitForIdle();chooseAll()
        val modal=runtime.visibleDialogs.single().value
        val node=compose.onNodeWithTag("guide_modal_sheet").fetchSemanticsNode().id
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { runtime.select(GuideStage.TEMPLATES.ordinal) }
        compose.mainClock.advanceTimeByFrame()
        assertSame(modal,runtime.visibleDialogs.single().value)
        assertEquals(node,compose.onNodeWithTag("guide_modal_sheet").fetchSemanticsNode().id)
        compose.mainClock.autoAdvance=true
        compose.waitUntil(10_000) { !runtime.preparing && runtime.ready }
        compose.waitForIdle()
        assertSame(modal,runtime.visibleDialogs.single().value)
        compose.onNodeWithTag("planning_default_direction").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已收起"))
        compose.onNodeWithTag("planning_default_EXPECTED").assertDoesNotExist()
        compose.onNodeWithTag("planning_templates_entry").assertIsDisplayed().performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("template_list").assertIsDisplayed()
        shot("preferences-sheet-reused")
    }

    @Test fun aboutRequiresItsEntryAndThePracticeSheetFitsAboveTheInstructionPanel() {
        render(GuideStage.ABOUT);visit(GuideStage.ABOUT,openEntry=false)
        assertEquals("settings_about",runtime.target)
        compose.onNodeWithTag("settings_about_dialog").assertDoesNotExist()
        compose.onNodeWithTag("settings_about").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.waitForIdle()
        val dialog=compose.onNodeWithTag("settings_about_dialog").fetchSemanticsNode().boundsInRoot
        val panel=compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot
        val mask=compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().boundsInRoot
        assertTrue("About must not overlap or shrink the instruction panel",dialog.bottom<panel.top)
        assertTrue("About must sit close above the instruction panel",panel.top-dialog.bottom in
            with(compose.density) { 8.dp.toPx() }..with(compose.density) { 20.dp.toPx() })
        compose.onNodeWithTag("guide_instruction_area").assertIsDisplayed()
        compose.onNodeWithTag("guide_next").assertIsDisplayed()
        compose.onNodeWithTag("settings_about_content").performTouchInput { swipeUp() }
        compose.onNodeWithTag("settings_sheet_close").assertIsDisplayed()
        shot("about-above-guide-panel")
    }

    @Test fun plannerAndSettingsLessonsRequireTheirBottomNavigationEntrances() {
        render(GuideStage.WHEELS);visit(GuideStage.WHEELS,openEntry=false)
        assertEquals("tab_planner",runtime.target)
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.onNodeWithTag("planner_wheels").assertDoesNotExist()
        val panel=compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot
        val target=runtime.anchors.getValue("tab_planner").bounds
        assertTrue(panel.bottom<target.top)
        compose.onNodeWithTag("tab_planner").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.waitForIdle()
        compose.onNodeWithTag("tab_planner").assertIsSelected()
        compose.onNodeWithTag("planner_wheels").assertIsDisplayed()
        compose.onNodeWithTag("guide_progress").assertTextEquals("规划自习 · 16/43")
        compose.onNodeWithTag("guide_instruction").assertTextEquals(GuideStage.WHEELS.instruction)
        visit(GuideStage.THEME,openEntry=false)
        assertEquals("tab_settings",runtime.target)
        compose.onNodeWithTag("tab_planner").assertIsSelected()
        compose.onNodeWithTag("tab_settings").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.waitForIdle()
        compose.onNodeWithTag("settings_theme_belt").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").assertIsSelected()
        compose.onNodeWithTag("guide_progress").assertTextEquals("设置与帮助 · 32/43")
        compose.onNodeWithTag("guide_instruction").assertTextEquals(GuideStage.THEME.instruction)
        shot("navigation-entrances-same-step")
    }

    @Test fun importPreviewToChoiceKeepsTheOpenSheetMountedAndDirectEntryStillOpensIt() {
        render(GuideStage.IMPORT);visit(GuideStage.IMPORT)
        compose.onNodeWithTag("planner_add_period").performTouchInput { longClick() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.waitForIdle()
        val modal=runtime.visibleDialogs.single().value
        val owner=runtime.visibleDialogs.single().key
        val node=compose.onNodeWithTag("guide_modal_sheet").fetchSemanticsNode().id
        val row=compose.onNodeWithTag("template_import_guide").fetchSemanticsNode().id
        val opening=runtime.anchors.getValue("template_import_dialog").bounds
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { runtime.select(GuideStage.IMPORT_PICK.ordinal) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals("sheet",runtime.presentedWindow)
            assertSame(modal,runtime.visibleDialogs.single().value)
            assertSame(owner,runtime.visibleDialogs.single().key)
            assertEquals(opening,runtime.retainedOpening?.bounds)
        }
        assertEquals(node,compose.onNodeWithTag("guide_modal_sheet").fetchSemanticsNode().id)
        assertEquals(row,compose.onNodeWithTag("template_import_guide").fetchSemanticsNode().id)
        compose.mainClock.autoAdvance=true
        compose.waitUntil(10_000) { !runtime.preparing && runtime.ready }
        assertSame(modal,runtime.visibleDialogs.single().value)
        assertEquals(node,compose.onNodeWithTag("guide_modal_sheet").fetchSemanticsNode().id)
        compose.onNodeWithTag("template_import_guide").assertIsEnabled()
        visit(GuideStage.ABOUT)
        visit(GuideStage.IMPORT_PICK)
        compose.onNodeWithTag("guide_modal_sheet").assertIsDisplayed()
        compose.onNodeWithTag("template_import_guide").assertIsEnabled()
        shot("import-sheet-reused-or-opened")
    }

    @Test fun practiceHasNoBannerAndOtherFavoritesCannotPushTheTargetRowOffscreen() {
        render(GuideStage.GENERATE);visit(GuideStage.GENERATE)
        compose.onNodeWithTag("guide_workspace").assertIsDisplayed()
        compose.onNodeWithTag("guide_practice_banner").assertDoesNotExist()
        compose.onNodeWithText("指引练习 · 示例时间 10:00 · 不影响你的数据").assertDoesNotExist()
        assertTrue(runtime.model.state.value.profiles.values.none { it.favorite })
        compose.onNodeWithTag("planner_generate").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.model.planning.executions.value[Campus.XITUCHENG]?.result!=null }
        assertTrue(runtime.model.planning.executions.value.getValue(Campus.XITUCHENG).result!!.options.size>1)
        // Simulate stale favorites left by an older planning chapter.
        compose.runOnIdle {
            runtime.data.snapshot(Campus.XITUCHENG).classrooms.drop(1).forEach {
                runtime.model.saveProfile(it.emptyProfile().copy(favorite=true))
            }
        }
        for(stage in listOf(GuideStage.SWIPE,GuideStage.FAVORITE,GuideStage.NOTE,GuideStage.GENERATE,GuideStage.FAVORITE)) {
            visit(stage,openEntry=false)
            assertTrue(runtime.model.state.value.profiles.values.none { it.favorite && it.roomId!="01:2-201" })
            if(stage.page==0) {
                val row=compose.onNodeWithTag("room_face_01:2-201").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val panel=compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot
                assertTrue("The demonstration room must remain above the guide at $stage",row.bottom<panel.top)
            }
        }
        shot("no-banner-no-other-favorites")
    }

    @Test fun freshAndChapterEntriesNeverAdvanceOnTheirOwnAndDirectoryIsCentered() {
        render();visit(GuideStage.INTRO)
        compose.onNodeWithTag("guide_pause").assertDoesNotExist()
        val navigation=compose.onNodeWithTag("guide_navigation").fetchSemanticsNode().boundsInRoot
        val directory=compose.onNodeWithTag("guide_directory").fetchSemanticsNode().boundsInRoot
        assertEquals(navigation.center.x,directory.center.x,1f)
        compose.mainClock.advanceTimeBy(12_000)
        assertEquals(GuideStage.INTRO,runtime.stage)
        compose.onNodeWithTag("guide_directory").performClick()
        compose.onNodeWithTag("guide_chapter_2").performClick()
        compose.waitUntil(10_000) { runtime.stage==GuideStage.WHEELS && runtime.ready }
        compose.onNodeWithTag("guide_pause").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(15_000)
        assertEquals(GuideStage.WHEELS,runtime.stage)
    }

    @Test fun slotOpeningPersistsAcrossBothDirectionsWhilePreparationStillBlocksInput() {
        render(GuideStage.SLOTS);visit(GuideStage.SLOTS)
        compose.waitUntil(10_000) { !runtime.preparing }
        val original=runtime.anchors.getValue("slot_grid").bounds
        val scrim=compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().id
        val maskBounds=compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().boundsInRoot
        val sample=compose.onNodeWithTag("slot_summary_space").fetchSemanticsNode().boundsInRoot.center-maskBounds.topLeft
        val reference=compose.onNodeWithTag("guide_scrim").captureToImage().toPixelMap()[sample.x.toInt(),sample.y.toInt()]
        for(stage in listOf(GuideStage.INSPECT,GuideStage.SLOTS)) {
            compose.mainClock.autoAdvance=false
            compose.runOnIdle { runtime.select(stage.ordinal) }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertTrue(runtime.preparing)
                assertEquals(original,runtime.retainedOpening?.bounds)
            }
            assertEquals(scrim,compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().id)
            assertEquals(compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().boundsInRoot,
                compose.onNodeWithTag("guide_blocker_0").fetchSemanticsNode().boundsInRoot)
            val during=compose.onNodeWithTag("guide_scrim").captureToImage().toPixelMap()[sample.x.toInt(),sample.y.toInt()]
            assertEquals(reference.red,during.red,.02f)
            assertEquals(reference.green,during.green,.02f)
            assertEquals(reference.blue,during.blue,.02f)
            assertEquals(reference.alpha,during.alpha,.02f)
            compose.mainClock.autoAdvance=true
            compose.waitUntil(10_000) { !runtime.preparing }
            assertEquals(original,runtime.anchors.getValue("slot_grid").bounds)
            assertNull(runtime.retainedOpening)
        }
        shot("slots-continuous-opening")
    }

    @Test fun titleAndInstructionSlideTogetherInTheRequestedDirectionOnly() {
        render(GuideStage.SLOTS);visit(GuideStage.SLOTS)
        val resting=compose.onNodeWithTag("guide_title").fetchSemanticsNode().positionInRoot.x
        for((stage,direction) in listOf(GuideStage.INSPECT to 1,GuideStage.SLOTS to -1)) {
            compose.mainClock.autoAdvance=false
            compose.runOnIdle { runtime.select(stage.ordinal) }
            compose.mainClock.advanceTimeBy(64)
            val title=compose.onNodeWithTag("guide_title").fetchSemanticsNode().positionInRoot.x
            val instruction=compose.onNodeWithTag("guide_instruction_area").fetchSemanticsNode().positionInRoot.x
            assertTrue("Wrong slide direction for $stage: $title / $resting",(title-resting)*direction>1f)
            assertEquals("Title and instruction must move as one group",title,instruction,1f)
            compose.onAllNodesWithTag("guide_title").assertCountEquals(1)
            compose.mainClock.advanceTimeBy(300)
            compose.mainClock.autoAdvance=true
            compose.waitForIdle()
            assertEquals(resting,compose.onNodeWithTag("guide_title").fetchSemanticsNode().positionInRoot.x,1f)
            compose.onNodeWithTag("guide_instruction").assertTextEquals(stage.instruction)
        }
    }

    private fun render(step:GuideStage=GuideStage.INTRO,manual:Boolean=false,large:Boolean=false) {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if(large) Density(density.density*1.25f,1.6f) else density) {
            YOURoomTheme {
                if(visible) UsageGuideExperience(GuideRequest("test",step.ordinal,manual=manual,serial=1),
                    YOURoomState(),onRuntime={
                        if(!::runtime.isInitialized) { runtime=it }
                    }) { position,outcome ->
                    records+=position to outcome
                    if(outcome!=GuideOutcome.IN_PROGRESS) visible=false
                }
            }
            }
        }
        compose.waitUntil(15_000) { ::runtime.isInitialized && !runtime.model.state.value.initializing }
    }
    private fun visit(stage:GuideStage,openEntry:Boolean=true) {
        android.util.Log.i("UsageGuideTest","Visiting ${stage.name}")
        compose.runOnIdle { runtime.select(stage.ordinal) }
        compose.waitUntil(15_000) { runtime.preparedStage==stage }
        compose.waitForIdle()
        try { compose.waitUntil(15_000) { runtime.ready } }
        catch(error:Throwable) {
            shot("failed-${stage.name}")
            throw AssertionError("Missing ${stage.name}: ${runtime.window}/${runtime.target}; anchors=${runtime.anchors.keys.toList()}",error)
        }
        compose.onNodeWithTag("guide_title").assertTextEquals(stage.title)
        if(openEntry && stage==GuideStage.FILTER_TAGS && runtime.entry==0) {
            compose.onNodeWithTag("edit_tags").performTouchInput { click() }
            compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
            compose.waitForIdle()
        }
        if(openEntry && stage in listOf(GuideStage.PLANNING,GuideStage.TEMPLATES,GuideStage.WHEELS,GuideStage.THEME,GuideStage.ABOUT) && runtime.entry==0) {
            compose.onNodeWithTag(runtime.target).performTouchInput { click() }
            compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
            compose.waitForIdle()
        }
        if(openEntry && stage in listOf(GuideStage.TAGS,GuideStage.NOTE) && runtime.entry==0) {
            compose.onNodeWithTag(runtime.target).performTouchInput { click() }
            compose.waitUntil(15_000) { runtime.entry==1 && runtime.ready }
            compose.waitForIdle()
        }
        if(openEntry && stage==GuideStage.MARKS && runtime.entry<2) {
            if(runtime.entry==0) {
                compose.onNodeWithTag("tab_settings").performTouchInput { click() }
                compose.waitUntil(15_000) { runtime.entry==1 && runtime.ready }
                compose.waitForIdle()
            }
            compose.onNodeWithTag("settings_room_marks").performTouchInput { click() }
            compose.waitUntil(15_000) { runtime.entry==2 && runtime.ready }
            compose.waitForIdle()
        }
        if(openEntry && stage in listOf(GuideStage.PERIOD,GuideStage.SAVE_TEMPLATE,GuideStage.DURATION) && runtime.entry==0) {
            compose.onNodeWithTag(runtime.target).performTouchInput { click() }
            compose.waitUntil(15_000) { runtime.entry==1 && runtime.ready }
            compose.waitForIdle()
        }
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Android window fade-in is not driven by Compose's virtual animation clock.
        android.os.SystemClock.sleep(300)
        File(instrumentation.targetContext.getExternalFilesDir(null),"guide-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun everyStepCanFindItsProductionControlIncludingNativeDialogs() {
        render()
        for(stage in GuideStage.entries) {
            visit(stage)
            val panel=compose.onNodeWithTag("guide_panel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val scrim=compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().boundsInRoot
            if(stage==GuideStage.NAV) {
                val navigation=compose.onNodeWithTag("workspace_navigation").fetchSemanticsNode().boundsInRoot
                assertTrue("NAV must stay just above bottom navigation",panel.bottom<navigation.top && panel.bottom>navigation.top-100f)
            } else if(stage in listOf(GuideStage.LOGOUT,GuideStage.CLEAR_CACHE)) {
                assertTrue("${stage.name} must remain above its button",panel.bottom<runtime.anchors.getValue(runtime.target).bounds.top)
            } else assertTrue("${stage.name} guide is not bottom-anchored: $panel / $scrim",
                panel.height>0 && scrim.bottom-panel.bottom in 0f..100f)
            compose.onNodeWithTag("guide_next").performScrollTo().assertIsDisplayed()
            if(stage in listOf(GuideStage.SLOTS,GuideStage.FAVORITE,GuideStage.FROM_NOW,
                    GuideStage.EXPECT,GuideStage.THEME,GuideStage.ABOUT)) shot(stage.name.lowercase())
        }
        compose.onNodeWithTag("guide_next").performClick()
        compose.onNodeWithTag("guide_panel").assertDoesNotExist()
        assertEquals(GuideOutcome.COMPLETED,records.last().second)
    }

    @Test fun theHolePassesRealGesturesButTheRestOfThePageIsBlocked() {
        render(GuideStage.CAMPUS)
        visit(GuideStage.CAMPUS)
        // This point is outside both the spotlight and the guide control panel.
        compose.onNodeWithTag("slot_1").performTouchInput { click() }
        compose.onNodeWithTag("tab_rooms").assertIsSelected()
        assertTrue(runtime.model.state.value.filters.selectedSlots.isEmpty())
        shot("campus-before-swipe")
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        try { compose.waitUntil(10_000) { runtime.model.state.value.campus==Campus.SHAHE } }
        catch(error:Throwable) {
            shot("campus-after-swipe")
            throw AssertionError("Campus gesture failed; anchor=${runtime.anchors[GuideStage.CAMPUS.target]}",error)
        }
        compose.waitUntil(10_000) { runtime.done }
        visit(GuideStage.SLOTS)
        compose.onNodeWithTag("slot_1").performTouchInput { click() }
        compose.onNodeWithTag("slot_2").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.model.state.value.filters.selectedSlots.size==2 }
        compose.waitUntil(10_000) { runtime.done }
        visit(GuideStage.SWIPE)
        compose.onNodeWithTag("room_swipe_01:2-201").performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { runtime.done }
        visit(GuideStage.FAVORITE)
        compose.onNodeWithTag("room_action_01:2-201_0").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.model.state.value.profiles["01:2-201"]?.favorite==true }
    }

    @Test fun planningAdditionAndMenuKeepTheSameExampleWithoutAnotherWheelJump() {
        render(GuideStage.ADD);visit(GuideStage.ADD,openEntry=false)
        compose.onNodeWithTag("planner_add_period").assertHasClickAction()
        compose.onNodeWithTag("planner_add_period").performTouchInput { longClick() }
        compose.onNodeWithTag("template_import_list").assertDoesNotExist()
        // A long press without a long-click action may be handled as a click;
        // restart this practice example before testing the explicit confirmation.
        compose.runOnIdle { runtime.prepare() };compose.waitForIdle()
        compose.onNodeWithTag("planner_add_period").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.waitForIdle()
        val added=runtime.goal
        assertTrue("The opening must cover the whole strip",runtime.spotlightTargets.contains("planner_target_strip"))
        val strip=compose.onNodeWithTag("planner_target_strip").fetchSemanticsNode().boundsInRoot
        assertEquals(strip,runtime.anchors.getValue("planner_target_strip").bounds)
        assertEquals(listOf(StudyWindow(660,720)),added.savedWindows)
        assertEquals(720,added.start);assertEquals(780,added.end)
        compose.onNodeWithTag("planner_target_range_0",useUnmergedTree=true).assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("planner_period_actions_0").assertDoesNotExist()
        shot("17-added-window-disabled")
        compose.onNodeWithTag("guide_next").performClick()
        compose.waitUntil(10_000) { runtime.preparedStage==GuideStage.PERIOD && runtime.ready }
        compose.waitForIdle()
        assertEquals(added,runtime.goal)
        compose.onNodeWithTag("planner_start").assertTextEquals("12:00")
        compose.onNodeWithTag("planner_end").assertTextEquals("13:00")
        compose.onNodeWithTag("planner_target_range_0",useUnmergedTree=true).assertIsEnabled().performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        for(tag in listOf("planner_edit_period_0","planner_remove_period_0","planner_save_periods_0"))
            compose.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput { click() }
        assertEquals(added,runtime.goal)
        compose.onNodeWithTag("template_editor_content").assertDoesNotExist()
        shot("18-disabled-period-menu")
    }

    @Test fun saveCombinationRequiresTheSaveButtonAndStartsWithOnlyElevenToTwelve() {
        render(GuideStage.SAVE_TEMPLATE);visit(GuideStage.SAVE_TEMPLATE,openEntry=false)
        assertEquals("planner_save_periods_0",runtime.target)
        compose.onNodeWithTag("template_editor_content").assertDoesNotExist()
        compose.onNodeWithTag("planner_edit_period_0").assertIsNotEnabled()
        compose.onNodeWithTag("planner_remove_period_0").assertIsNotEnabled()
        compose.onNodeWithTag("planner_save_periods_0").assertIsEnabled().performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("guide_title").assertTextEquals("保存时段组合")
        compose.onNodeWithTag("template_count").assertTextEquals("1/3")
        compose.onNodeWithTag("template_period_0",useUnmergedTree=true).assert(hasAnyDescendant(hasText("11:00–12:00")))
        compose.onNodeWithTag("template_period_1",useUnmergedTree=true).assertDoesNotExist()
        shot("19-single-period-template")
    }

    @Test fun pastTimeAndFromNowShareTheShortcutThenShowReadOnlyWheelCenters() {
        render(GuideStage.PAST);visit(GuideStage.PAST,openEntry=false)
        assertEquals(listOf("planner_add_period","planner_start_center"),runtime.spotlightTargets)
        compose.onNodeWithTag("planner_start_hour_wheel").assertIsNotEnabled()
        compose.onNodeWithTag("planner_start_minute_wheel").assertIsNotEnabled()
        compose.onNodeWithTag("planner_from_now").assertDoesNotExist()
        compose.onNodeWithTag("planner_add_period").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.stage==GuideStage.FROM_NOW && runtime.preparedStage==GuideStage.FROM_NOW && runtime.ready }
        assertEquals(540,runtime.goal.start);assertEquals(720,runtime.goal.end)
        compose.onNodeWithTag("guide_progress").assertTextEquals("规划自习 · 21/43")
        compose.waitForIdle()
        assertEquals("planner_from_now",runtime.target)
        compose.onNodeWithTag("planner_from_now").assertIsEnabled().performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.waitForIdle()
        assertEquals(listOf("planner_start_center"),runtime.spotlightTargets)
        assertEquals(601,runtime.goal.start);assertEquals(720,runtime.goal.end)
        compose.onNodeWithTag("planner_start").assertTextEquals("10:01")
        compose.onNodeWithTag("planner_end").assertTextEquals("12:00")
        for(tag in listOf("planner_start_hour_wheel","planner_start_minute_wheel","planner_end_hour_wheel","planner_end_minute_wheel")) {
            compose.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput {
                down(center);moveBy(androidx.compose.ui.geometry.Offset(0f,-height/3f),delayMillis=300);up()
            }
            compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {
                assertFalse(it(15f))
            }
        }
        compose.waitForIdle()
        assertEquals(601,runtime.goal.start);assertEquals(720,runtime.goal.end)
        compose.onNodeWithTag("guide_retry").assertDoesNotExist()
        compose.onNodeWithText("正在定位控件；也可重试或直接下一步").assertDoesNotExist()
        shot("21-readonly-wheel-centers")
    }

    @Test fun invalidEndRequiresAddingBeforeOfferingDurationShortcuts() {
        render(GuideStage.DURATION);visit(GuideStage.DURATION,openEntry=false)
        assertTrue(runtime.spotlightTargets.containsAll(listOf("planner_start_center","planner_end_center")))
        for(tag in listOf("planner_start_hour_wheel","planner_start_minute_wheel","planner_end_hour_wheel","planner_end_minute_wheel"))
            compose.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput { swipeUp() }
        compose.onNodeWithTag("planner_start").assertTextEquals("10:01")
        compose.onNodeWithTag("planner_end").assertTextEquals("10:00")
        compose.onNodeWithTag("planner_duration_buttons").assertDoesNotExist()
        compose.onNodeWithTag("planner_add_period").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("planner_duration_1h").assertIsEnabled().performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==2 && runtime.ready }
        compose.waitForIdle()
        assertEquals(601,runtime.goal.start);assertEquals(661,runtime.goal.end)
        assertTrue(runtime.goal.savedWindows.isEmpty())
        shot("22-corrected-end")
    }

    private fun assertDemoLeftOfSkip() {
        val demo=compose.onNodeWithTag("guide_demonstrate").fetchSemanticsNode().boundsInRoot
        val skip=compose.onNodeWithTag("guide_skip").fetchSemanticsNode().boundsInRoot
        assertTrue("demonstration must precede skip",demo.right<=skip.left+1f)
        assertEquals(skip.center.y,demo.center.y,1f)
    }

    @Test fun everyPracticeDemonstrationSitsImmediatelyBeforeSkipIncludingCompletion() {
        render()
        for(stage in GuideStage.entries.filter { it.practice }) {
            visit(stage)
            assertDemoLeftOfSkip()
        }
        visit(GuideStage.ADD)
        compose.onNodeWithTag("guide_demonstrate").performClick()
        compose.waitUntil(10_000) { runtime.done }
        compose.onNodeWithTag("guide_demonstrate").assertContentDescriptionEquals("已完成操作").assertTextEquals()
        assertDemoLeftOfSkip()
        shot("demonstration-header")
    }

    @Test fun importPreviewIsReadOnlyThenTheSameListAndSkipOptionBecomeInteractive() {
        render(GuideStage.IMPORT);visit(GuideStage.IMPORT)
        val before=runtime.goal
        compose.onNodeWithTag("planner_add_period").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(before,runtime.goal)
        compose.onNodeWithTag("template_import_dialog").assertDoesNotExist()
        compose.onNodeWithTag("planner_add_period").performTouchInput { longClick() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.waitForIdle()
        assertEquals("sheet",runtime.window);assertEquals("template_import_dialog",runtime.target)
        for(tag in listOf("template_import_guide-weekday","template_import_guide-weekend",
                "template_skip_invalid","template_import_manage","settings_sheet_close"))
            compose.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput { click() }
        val first=compose.onNodeWithTag("template_import_guide").fetchSemanticsNode().boundsInRoot
        val weekday=compose.onNodeWithTag("template_import_guide-weekday").fetchSemanticsNode().boundsInRoot
        val weekend=compose.onNodeWithTag("template_import_guide-weekend").fetchSemanticsNode().boundsInRoot
        assertTrue(first.bottom<=weekday.top && weekday.bottom<=weekend.top)
        compose.onNodeWithTag("template_import_guide").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("template_replace_confirm").assertDoesNotExist()
        assertEquals(1,runtime.goal.savedWindows.size)
        assertDemoLeftOfSkip()
        shot("23-readonly-import")
        compose.onNodeWithTag("guide_next").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { runtime.preparedStage==GuideStage.IMPORT_PICK && runtime.ready }
        compose.waitForIdle()
        compose.onNodeWithTag("template_skip_invalid").assertIsEnabled().assertIsOff()
        compose.onNodeWithText("某一时段不合法时仍导入剩余时段").performTouchInput { click() }
        compose.onNodeWithTag("template_skip_invalid").assertIsOn()
        compose.onNodeWithTag("template_skip_invalid").performTouchInput { click() }
        compose.onNodeWithTag("template_skip_invalid").assertIsOff()
        shot("24-import-option")
        compose.onNodeWithTag("template_import_guide").assertIsEnabled().performTouchInput { click() }
        compose.onNodeWithTag("template_replace_confirm").assertExists()
        compose.waitUntil(10_000) { runtime.preparedStage==GuideStage.REPLACE && runtime.ready }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.top>
            compose.onNodeWithTag("template_replace_confirm").fetchSemanticsNode().boundsInRoot.bottom)
        shot("25-replace-bottom")
        compose.onNodeWithTag("template_replace_confirm").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.preparedStage==GuideStage.EXPECT && runtime.ready }
        compose.onNodeWithTag("guide_progress").assertTextEquals("规划自习 · 26/43")
    }

    @Test fun expectedLessonCombinesBookmarkWheelsPinAndClearWithoutAnExtraStep() {
        render(GuideStage.EXPECT);visit(GuideStage.EXPECT)
        compose.onNodeWithTag("planner_expected").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.waitForIdle()
        assertEquals("expected_room_dialog",runtime.target)
        compose.onNodeWithTag("expected_clear").assertIsNotEnabled()
        compose.onNodeWithTag("expected_pin").assertIsEnabled()
        compose.onNodeWithTag("expected_close").assertIsNotEnabled().performTouchInput { click() }
        val wheel=compose.onNodeWithTag("expected_building_wheel")
        val before=wheel.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current
        wheel.performTouchInput {
            down(center);moveBy(androidx.compose.ui.geometry.Offset(0f,-height/3.5f),delayMillis=500)
            advanceEventTime(200);up()
        }
        compose.waitForIdle()
        assertNotEquals(before,wheel.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current)
        assertTrue(runtime.goal.expectedRooms.isEmpty())
        shot("26-expected-wheels")
        assertEquals(GuideStage.EXPECT,runtime.stage)
        compose.onNodeWithTag("guide_progress").assertTextEquals("规划自习 · 26/43")
        compose.onNodeWithTag("expected_close").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("expected_pin").assertIsEnabled().performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.goal.expectedRooms.isNotEmpty() && runtime.done }
        compose.onNodeWithTag("expected_pin").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.goal.expectedRooms.isEmpty() }
        compose.onNodeWithTag("expected_pin").performTouchInput { click() }
        compose.onNodeWithTag("expected_clear").assertIsEnabled().performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.goal.expectedRooms.isEmpty() }
        assertTrue(runtime.done)
        assertTrue(compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.top>
            compose.onNodeWithTag("expected_actions").fetchSemanticsNode().boundsInRoot.bottom)
        shot("26-merged-expected-actions-bottom")
    }

    @Test fun importChoiceOpeningExcludesWeekdayAndWeekendButKeepsTheSkipOption() {
        render(GuideStage.IMPORT_PICK);visit(GuideStage.IMPORT_PICK)
        assertEquals(listOf("template_import_guide","template_skip_invalid_option"),runtime.spotlightTargets)
        val opening=runtime.anchors.getValue("template_import_guide").bounds
        val first=compose.onNodeWithTag("template_import_guide").fetchSemanticsNode().boundsInRoot
        assertEquals(first,opening)
        for(tag in listOf("template_import_guide-weekday","template_import_guide-weekend")) {
            val row=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(opening.bottom<=row.top)
            compose.onNodeWithTag(tag).performTouchInput { click() }
            assertEquals(GuideStage.IMPORT_PICK,runtime.stage)
            compose.onNodeWithTag("template_import_error").assertDoesNotExist()
        }
        compose.onNodeWithTag("template_skip_invalid").performTouchInput { click() }
        compose.onNodeWithTag("template_skip_invalid").assertIsOn()
        compose.onNodeWithTag("template_import_guide").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.preparedStage==GuideStage.REPLACE && runtime.ready }
        assertEquals("guide",runtime.importTemplateId)
        compose.onNodeWithTag("template_replace_confirm").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.preparedStage==GuideStage.EXPECT && runtime.ready }
        assertEquals(GuideData.windows,runtime.goal.savedWindows)
    }

    @Test fun multipleResultsAreReusedWhenOpeningAlternativesAndStopHasNoRetryHint() {
        render(GuideStage.GENERATE);visit(GuideStage.GENERATE)
        val started=android.os.SystemClock.elapsedRealtime()
        compose.onNodeWithTag("planner_generate").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.model.planning.executions.value[Campus.XITUCHENG]?.result!=null }
        android.util.Log.i("UsageGuideTest","Guide portfolio completed in ${android.os.SystemClock.elapsedRealtime()-started}ms")
        val calculated=runtime.model.planning.executions.value.getValue(Campus.XITUCHENG)
        assertTrue(calculated.result!!.options.size>1)
        assertTrue(calculated.result.groups.first { it.direction==PlanningDirection.BALANCED }.optionIndices.size>1)
        visit(GuideStage.RESULTS)
        assertEquals(calculated.id,runtime.model.planning.executions.value.getValue(Campus.XITUCHENG).id)
        compose.onNodeWithTag("guide_title").assertTextEquals("查看规划结果")
        compose.onNodeWithTag("planner_alternatives").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.preparedStage==GuideStage.ALTERNATIVES && runtime.ready }
        compose.waitForIdle()
        val reused=runtime.model.planning.executions.value.getValue(Campus.XITUCHENG)
        assertEquals(calculated.id,reused.id);assertSame(calculated.result,reused.result)
        compose.onNodeWithTag("planner_direction_CHANGES").performTouchInput { click() }
        compose.onNodeWithTag("planner_direction_CHANGES").assertIsSelected()
        compose.onNodeWithTag("planner_alternative_list").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("planner_direction_BALANCED").performTouchInput { click() }
        for(index in listOf(1,0,1)) {
            compose.onNodeWithTag("planner_alternative_BALANCED_$index").performScrollTo().performTouchInput { click() }
            val option=calculated.result.groups.first { it.direction==PlanningDirection.BALANCED }.optionIndices[index]
            compose.waitUntil(10_000) { runtime.model.planning.executions.value.getValue(Campus.XITUCHENG).selectedOption==option }
            compose.waitForIdle()
            compose.onNodeWithTag("planner_drawer").assertIsDisplayed()
            assertTrue(runtime.ready)
            assertEquals(GuideStage.ALTERNATIVES,runtime.stage)
            assertEquals(calculated.id,runtime.model.planning.executions.value.getValue(Campus.XITUCHENG).id)
        }
        assertTrue(compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.top>
            compose.onNodeWithTag("planner_drawer").fetchSemanticsNode().boundsInRoot.bottom)
        compose.onNodeWithTag("guide_next").performScrollTo().assertIsDisplayed()
        shot("32-multiple-results-bottom")
        visit(GuideStage.STOP)
        compose.onNodeWithTag("guide_retry").assertDoesNotExist()
        compose.onNodeWithText("正在定位控件；也可重试或直接下一步").assertDoesNotExist()
        compose.onNodeWithTag("planner_generate").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.model.planning.executions.value[Campus.XITUCHENG]?.cancelled==true }
        compose.onNodeWithTag("guide_retry").assertDoesNotExist()
    }

    @Test fun pastTimeImportExpectedRoomAndStopUseRealHandlers() {
        render(GuideStage.PAST)
        visit(GuideStage.PAST)
        compose.onNodeWithTag("planner_add_period").performTouchInput { click() }
        compose.onNodeWithTag("planner_from_now").assertExists()
        visit(GuideStage.FROM_NOW)
        compose.onNodeWithTag("planner_from_now").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.goal.start==601 }
        assertEquals(720,runtime.goal.end)
        visit(GuideStage.ADD)
        compose.onNodeWithTag("planner_add_period").performTouchInput { click() }
        assertEquals(listOf(StudyWindow(660,720)),runtime.goal.savedWindows)
        visit(GuideStage.IMPORT)
        compose.onNodeWithTag("planner_add_period").performTouchInput { longClick() }
        compose.onNodeWithTag("template_import_guide").assertIsDisplayed()
        visit(GuideStage.IMPORT_PICK)
        compose.onNodeWithTag("template_import_guide").performTouchInput { click() }
        compose.onNodeWithTag("template_replace_confirm").assertIsDisplayed()
        visit(GuideStage.REPLACE)
        compose.onNodeWithTag("template_replace_confirm").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.goal.savedWindows.size==3 }
        visit(GuideStage.EXPECT)
        compose.onNodeWithTag("planner_expected").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("expected_pin").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.goal.expectedRooms.isNotEmpty() }
        visit(GuideStage.GENERATE)
        compose.onNodeWithTag("planner_generate").performTouchInput { click() }
        compose.waitUntil(20_000) { runtime.model.planning.executions.value[Campus.XITUCHENG]?.result!=null }
        assertTrue(runtime.model.planning.executions.value.getValue(Campus.XITUCHENG).result!!.options.isNotEmpty())
        visit(GuideStage.STOP)
        assertTrue(runtime.model.planning.executions.value.getValue(Campus.XITUCHENG).pending)
        compose.onNodeWithTag("planner_generate").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.model.planning.executions.value[Campus.XITUCHENG]?.cancelled==true }
    }

    @Test fun manualDirectorySkipAndReplayDoNotStartAnotherGuideInsidePractice() {
        render(manual=true)
        compose.onNodeWithTag("guide_chapter_3").performClick()
        visit(GuideStage.REPLAY)
        compose.onNodeWithTag("settings_usage_guide").performTouchInput { click() }
        assertNull(runtime.model.state.value.guideRequest)
        compose.onNodeWithTag("guide_directory").performClick()
        compose.onNodeWithTag("guide_chapter_2").performClick()
        visit(GuideStage.FROM_NOW)
        compose.onNodeWithTag("guide_skip").performClick()
        compose.onNodeWithTag("guide_panel").assertDoesNotExist()
        assertEquals(GuideOutcome.SKIPPED,records.last().second)
    }

    @Test fun largeTextKeepsDialogInstructionsAndExitControlsReachable() {
        render(GuideStage.FROM_NOW,large=true)
        visit(GuideStage.FROM_NOW)
        compose.onNodeWithTag("guide_next").performScrollTo().assertIsDisplayed()
        shot("large-from-now")
        visit(GuideStage.SAVE_TEMPLATE)
        compose.onNodeWithTag("guide_skip").performScrollTo().assertIsDisplayed()
        shot("large-template")
        visit(GuideStage.ABOUT)
        compose.onNodeWithTag("guide_next").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("guide_skip").performScrollTo().performClick()
        assertEquals(GuideOutcome.SKIPPED,records.last().second)
    }

    @Test fun manualNextTraversesEveryStepAndNeverCompletesSkippedPractice() {
        render()
        for(stage in GuideStage.entries.dropLast(1)) {
            android.util.Log.i("UsageGuideTest","Manual next ${stage.name}")
            compose.waitUntil(20_000) { runtime.stage==stage && runtime.preparedStage==stage && runtime.ready }
            compose.waitUntil(10_000) { !runtime.preparing }
            if(stage.practice) assertFalse("Navigation is not an action: $stage",runtime.done)
            compose.onNodeWithTag("guide_next").performScrollTo().performClick()
        }
        compose.waitUntil(10_000) { runtime.stage==GuideStage.FINISH && !runtime.preparing }
        assertEquals(GuideStage.FINISH,runtime.stage)
        compose.onNodeWithTag("guide_finish_icon",useUnmergedTree=true).assertWidthIsEqualTo(18.dp).assertHeightIsEqualTo(18.dp)
        compose.onNodeWithTag("guide_next").performClick()
        assertEquals(GuideOutcome.COMPLETED,records.last().second)
    }

    @Test fun savedStateRestoresTheStepWithoutReopeningTheManualDirectoryOrAPlayer() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {
            YOURoomTheme {
                UsageGuideExperience(GuideRequest("test",manual=true,serial=1),YOURoomState(),
                    onRuntime={ runtime=it }) { _,_ -> }
            }
        }
        compose.waitUntil(10_000) { ::runtime.isInitialized && !runtime.model.state.value.initializing }
        compose.runOnIdle { runtime.menu=false }
        visit(GuideStage.FROM_NOW)
        val old=runtime
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(10_000) { runtime!==old && runtime.preparedStage==GuideStage.FROM_NOW }
        assertFalse(runtime.menu)
        compose.onNodeWithTag("guide_pause").assertDoesNotExist()
        compose.onNodeWithTag("guide_chapter_0").assertDoesNotExist()
    }

    @Test fun cacheClearLessonIsInformationalAndNeverOpensAuthenticationOrDeletesData() {
        render(GuideStage.CLEAR_CACHE)
        visit(GuideStage.CLEAR_CACHE)
        compose.onNodeWithTag("settings_clear_cache").performTouchInput { click() }
        compose.waitForIdle()
        assertNull(runtime.model.state.value.cacheClear)
        compose.onNodeWithTag("cache_clear_password").assertDoesNotExist()
        assertTrue(runtime.model.state.value.loggedIn)
        assertEquals(GuideStage.CLEAR_CACHE,runtime.stage)
    }

    @Test fun settingsLessonsResetDarkModeExposeChoicesAndRequireBothManagementEntrances() {
        render(GuideStage.DARK);visit(GuideStage.DARK)
        compose.runOnIdle { runtime.model.setDarkMode(true) }
        compose.waitUntil(5_000) { runtime.model.state.value.appearance.darkMode==true }
        visit(GuideStage.STRIP)
        compose.waitUntil(5_000) { runtime.model.state.value.appearance.darkMode==false }
        compose.onNodeWithTag("settings_strip_TIME_PROPORTIONAL").performTouchInput { click() }
        compose.waitUntil(5_000) { runtime.model.state.value.periodStrip.layout==PeriodStripLayout.TIME_PROPORTIONAL }
        compose.onNodeWithTag("settings_strip_EQUAL").performTouchInput { click() }
        compose.waitUntil(5_000) { runtime.model.state.value.periodStrip.layout==PeriodStripLayout.EQUAL }
        visit(GuideStage.DEFAULT_CAMPUS)
        compose.onNodeWithTag("guide_progress").assertTextEquals("设置与帮助 · 35/43")
        compose.onNodeWithTag("settings_default_04").performTouchInput { click() }
        compose.waitUntil(5_000) { runtime.model.state.value.appearance.defaultCampus==Campus.SHAHE }
        assertEquals(Campus.XITUCHENG,runtime.model.state.value.campus)
        visit(GuideStage.NUMBERS)
        compose.onNodeWithTag("guide_progress").assertTextEquals("设置与帮助 · 36/43")
        val before=runtime.model.state.value.periodStrip.showNumbers
        compose.onNodeWithTag("settings_strip_numbers").performTouchInput { click() }
        compose.waitUntil(5_000) { runtime.model.state.value.periodStrip.showNumbers!=before }
        visit(GuideStage.PLANNING,openEntry=false)
        assertEquals("settings_planning",runtime.target)
        compose.onNodeWithTag("planning_default_direction").assertDoesNotExist()
        compose.onNodeWithTag("settings_planning").performTouchInput { click() }
        compose.waitUntil(5_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("planning_default_direction").assertIsDisplayed()
        visit(GuideStage.TEMPLATES,openEntry=false)
        assertEquals("planning_templates_entry",runtime.target)
        compose.onNodeWithTag("template_list").assertDoesNotExist()
        compose.onNodeWithTag("planning_templates_entry").performTouchInput { click() }
        compose.waitUntil(5_000) { runtime.entry==1 && runtime.ready }
        val templates=runtime.model.state.value.planningPreferences
        compose.onNodeWithTag("template_guide").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("template_delete_guide").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("template_editor_content").assertDoesNotExist()
        compose.onNodeWithTag("template_delete_confirm").assertDoesNotExist()
        compose.onNodeWithTag("template_guide-weekday").assertIsNotEnabled()
        assertEquals(templates,runtime.model.state.value.planningPreferences)
        compose.onNodeWithTag("guide_progress").assertTextEquals("设置与帮助 · 38/43")
        shot("39-protected-practice-template")
    }

    @Test fun aboutIsFullyHighlightedAndDangerousSettingsStayInformationalAboveTheirButtons() {
        render(GuideStage.ABOUT);visit(GuideStage.ABOUT)
        assertEquals(compose.onNodeWithTag("settings_about_dialog").fetchSemanticsNode().boundsInRoot,
            runtime.anchors.getValue("settings_about_dialog").bounds)
        for(tag in listOf("settings_about_project","settings_about_feedback"))
            compose.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput { click() }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        visit(GuideStage.LOGOUT)
        compose.onNodeWithTag("guide_progress").assertTextEquals("设置与帮助 · 41/43")
        compose.onNodeWithTag("settings_logout").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("settings_logout_confirm").assertDoesNotExist()
        assertTrue(runtime.model.state.value.loggedIn)
        assertTrue(compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.bottom<runtime.anchors.getValue("settings_logout").bounds.top)
        shot("42-logout-above-disabled-button")
        visit(GuideStage.CLEAR_CACHE)
        compose.onNodeWithTag("guide_progress").assertTextEquals("设置与帮助 · 42/43")
        assertTrue(compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.bottom<runtime.anchors.getValue("settings_clear_cache").bounds.top)
        compose.onNodeWithTag("settings_clear_cache").performTouchInput { click() }
        assertNull(runtime.model.state.value.cacheClear)
        shot("43-cache-above-button")
        visit(GuideStage.FINISH)
        compose.onNodeWithTag("guide_progress").assertTextEquals("设置与帮助 · 43/43")
        compose.onNodeWithTag("guide_instruction").assertTextEquals("感谢使用本应用，希望能够帮助到您，祝您学习生活愉快顺利！")
    }

    private fun waitForRoomAction(index:Int) {
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasTestTag("room_action_01:2-201_$index") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    @Test fun swipeLessonDisablesAllActionsAndKeepsThePanelAtTheBottom() {
        render(GuideStage.SWIPE);visit(GuideStage.SWIPE)
        compose.onNodeWithTag("room_swipe_01:2-201").performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { runtime.done }
        compose.waitForIdle()
        for(index in 0..3) compose.onNodeWithTag("room_action_01:2-201_$index").assertIsNotEnabled().performTouchInput { click() }
        assertFalse(runtime.model.state.value.profiles["01:2-201"]?.favorite ?: false)
        assertNull(runtime.model.state.value.selectedRoomId)
        assertTrue(compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.top>
            compose.onNodeWithTag("room_swipe_01:2-201").fetchSemanticsNode().boundsInRoot.bottom)
        shot("swipe-disabled-bottom")
    }

    @Test fun favoriteAndExclusionReopenAfterMotionForRepeatedToggling() {
        render(GuideStage.FAVORITE);visit(GuideStage.FAVORITE)
        for(selected in listOf(true,false,true)) {
            waitForRoomAction(0)
            compose.onNodeWithTag("room_action_01:2-201_0").performTouchInput { click() }
            assertEquals(GuideStage.FAVORITE,runtime.stage)
            compose.waitUntil(10_000) { runtime.model.state.value.profiles["01:2-201"]?.favorite==selected }
            waitForRoomAction(0)
            assertTrue(compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.top>
                compose.onNodeWithTag("room_swipe_01:2-201").fetchSemanticsNode().boundsInRoot.bottom)
        }
        visit(GuideStage.EXCLUDE)
        for(excluded in listOf(true,false,true)) {
            waitForRoomAction(3)
            compose.onNodeWithTag("room_action_01:2-201_3").performTouchInput { click() }
            compose.waitUntil(10_000) { runtime.model.state.value.profiles["01:2-201"]?.excluded==excluded }
            waitForRoomAction(3)
        }
        assertEquals(GuideStage.EXCLUDE,runtime.stage)
        shot("exclusion-reopened-bottom")
    }

    @Test fun tagLessonRequiresEntryPreservesQuietDefinitionAndAllowsReopening() {
        render(GuideStage.TAGS);visit(GuideStage.TAGS,openEntry=false)
        assertEquals(0,runtime.entry)
        compose.onNodeWithTag("room_tag_dialog").assertDoesNotExist()
        waitForRoomAction(1)
        compose.onNodeWithTag("room_action_01:2-201_1").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("filter_tag_安静").assertIsSelected().performTouchInput { click() }
        compose.onNodeWithTag("filter_tag_安静").assertIsNotSelected().performTouchInput { click() }
        compose.onNodeWithTag("filter_tag_安静").assertIsSelected()
        compose.onNodeWithTag("reset_room_tags").performTouchInput { click() }
        compose.onNodeWithTag("filter_tag_安静").assertIsNotSelected()
        assertTrue("安静" in runtime.model.state.value.tagOrder)
        compose.onNodeWithTag("close_room_tags").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==0 }
        waitForRoomAction(1)
        compose.onNodeWithTag("room_subtitle_01:2-201",useUnmergedTree=true).assert(hasText("安静",substring=true).not())
        compose.onNodeWithTag("room_action_01:2-201_1").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("filter_tag_安静").assertIsNotSelected().performTouchInput { click() }
        compose.onNodeWithTag("close_room_tags").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==0 }
        waitForRoomAction(1)
        compose.onNodeWithTag("room_action_01:2-201_1").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("filter_tag_安静").assertIsSelected()
        compose.onNodeWithTag("edit_tags").performTouchInput { click() }
        compose.onNodeWithTag("custom_tag_name").assertIsDisplayed()
        shot("tags-reentered-with-quiet")
    }

    @Test fun noteLessonRequiresEntryLimitsTwentyCharactersAndReopensAfterRendering() {
        render(GuideStage.NOTE);visit(GuideStage.NOTE,openEntry=false)
        compose.onNodeWithTag("room_note_dialog").assertDoesNotExist()
        waitForRoomAction(2)
        compose.onNodeWithTag("room_action_01:2-201_2").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("clear_room_note").performTouchInput { click() }
        compose.onNodeWithTag("room_note").performTextInput("12345678901234567890EXTRA")
        compose.onNodeWithTag("room_note_count").assertTextEquals("20/20")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("close_room_note").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==0 }
        waitForRoomAction(2)
        compose.onNodeWithTag("room_note_text_01:2-201",useUnmergedTree=true).assertTextEquals("12345678901234567890")
        compose.onNodeWithTag("room_action_01:2-201_2").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("room_note").assert(hasText("12345678901234567890"))
        compose.onNodeWithTag("clear_room_note").performTouchInput { click() }
        compose.onNodeWithTag("close_room_note").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==0 }
        waitForRoomAction(2)
        assertEquals("",runtime.model.state.value.profiles.getValue("01:2-201").note)
    }

    @Test fun marksLessonRequiresSettingsThenMarksWithoutAddingAnotherStep() {
        render(GuideStage.MARKS);visit(GuideStage.MARKS,openEntry=false)
        assertEquals("tab_settings",runtime.target)
        compose.onNodeWithTag("tab_rooms").assertIsSelected()
        compose.onNodeWithTag("tab_settings").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("settings_marks_list").assertDoesNotExist()
        compose.onNodeWithTag("settings_room_marks").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==2 && runtime.ready }
        compose.onNodeWithTag("settings_marks_list").assertIsDisplayed()
        compose.onNodeWithTag("guide_progress").assertTextEquals("管理教室 · 15/43")
        val before=runtime.model.state.value.profiles
        compose.onNodeWithText("2-201").performTouchInput { click() }
        compose.onNodeWithTag("room_actions_dialog").assertIsDisplayed()
        for(index in 0..3) compose.onNodeWithTag("room_action_01:2-201_$index")
            .assertIsNotEnabled().performTouchInput { click() }
        assertEquals(before,runtime.model.state.value.profiles)
        compose.onNodeWithTag("close_room_actions").performTouchInput { click() }
        compose.onNodeWithTag("settings_marks_list").assertIsDisplayed()
    }

    @Test fun tagEditingRequiresThePlusEntranceWithinTheNinthStep() {
        render(GuideStage.FILTER_TAGS);visit(GuideStage.FILTER_TAGS,openEntry=false)
        assertEquals("edit_tags",runtime.target)
        compose.onNodeWithTag("custom_tag_name").assertDoesNotExist()
        compose.onNodeWithTag("edit_tags").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("custom_tag_name").assertIsDisplayed()
        compose.onNodeWithTag("guide_progress").assertTextEquals("查询教室 · 9/43")
        compose.onNodeWithTag("cancel_add_tag").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==0 && runtime.ready }
        compose.onNodeWithTag("edit_tags").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.entry==1 && runtime.ready }
        compose.onNodeWithTag("custom_tag_name").assertIsDisplayed()
    }

    @Test fun iconControlsKeepAccessibleNamesAndAllInstructionsOmitTrailingFullStops() {
        render()
        compose.onNodeWithTag("guide_previous").assertContentDescriptionEquals("上一步").assertIsNotEnabled()
        compose.onNodeWithTag("guide_next").assertContentDescriptionEquals("下一步").performClick()
        compose.onNodeWithTag("guide_previous").assertIsEnabled().performClick()
        compose.onNodeWithTag("guide_pause").assertDoesNotExist()
        compose.onNodeWithTag("guide_instruction").assertTextEquals(GuideStage.INTRO.instruction)
        shot("updated-controls")
    }

    @Test fun originalTitleSizeAndThreeLineInstructionViewportKeepLongTextReadable() {
        render()
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("guide_title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(14.sp,layouts.single().layoutInput.style.fontSize)
        var expectedHeight=0f
        compose.onNodeWithTag("guide_instruction").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val results=mutableListOf<TextLayoutResult>()
            action(results)
            val input=results.single().layoutInput
            expectedHeight=with(input.density) { input.style.lineHeight.toPx()*3 }
        }
        val height=compose.onNodeWithTag("guide_instruction_area").fetchSemanticsNode().size.height
        assertEquals(expectedHeight,height.toFloat(),1f)
        visit(GuideStage.FLEXIBLE)
        assertEquals(height,compose.onNodeWithTag("guide_instruction_area").fetchSemanticsNode().size.height)
        compose.onNodeWithTag("guide_instruction").assertTextEquals(GuideStage.FLEXIBLE.instruction)
        visit(GuideStage.CLEAR_CACHE)
        assertEquals(height,compose.onNodeWithTag("guide_instruction_area").fetchSemanticsNode().size.height)
        compose.onNodeWithTag("guide_instruction").assertTextEquals(GuideStage.CLEAR_CACHE.instruction)
        // The updated cache copy fits three lines at ordinary size. Do not force
        // fake overflow; at larger sizes, extra text still scrolls without loss.
        val copyLayout=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("guide_instruction").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(copyLayout) }
        assertEquals(Int.MAX_VALUE,copyLayout.single().layoutInput.maxLines)
        val scroll=compose.onNodeWithTag("guide_instruction_area").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        if(scroll.maxValue()>0) {
            compose.onNodeWithTag("guide_instruction_area").performScrollTo().performTouchInput { swipeUp() }
            assertTrue("Overflowing instructions must remain scrollable",scroll.value()>0)
        } else assertTrue("Fitting copy must retain every line",copyLayout.single().lineCount<=3)
        compose.onNodeWithTag("guide_next").performScrollTo().assertIsDisplayed()
        shot("three-line-instruction")
    }

    @Test fun missingAnchorRelocatesSilentlyWithoutResettingPractice() {
        render(GuideStage.WHEELS)
        visit(GuideStage.WHEELS)
        val draft=runtime.goal.copy(start=670,end=750)
        compose.runOnIdle {
            runtime.updateGoal(draft)
            runtime.anchors.remove(runtime.target)
        }
        compose.onNodeWithTag("guide_retry").assertDoesNotExist()
        compose.onNodeWithText("重新定位").assertDoesNotExist()
        compose.onNodeWithText("正在定位控件；也可重试或直接下一步").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(3000)
        compose.waitUntil(5_000) { runtime.ready }
        assertEquals(GuideStage.WHEELS,runtime.preparedStage)
        assertEquals(draft,runtime.goal)
        compose.onNodeWithTag("guide_next").performScrollTo().assertIsDisplayed()
    }

    @Test fun stepPreparationKeepsOneScrimAndOneWindowUntilTheFinalLayoutIsReady() {
        render(GuideStage.WHEELS)
        visit(GuideStage.WHEELS)
        compose.waitUntil(5_000) { !runtime.preparing }
        val scrim=compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().id
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { runtime.select(GuideStage.PAST.ordinal) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue(runtime.preparing) }
        // No old or intermediate target can be touched while prerequisites run.
        val guard=compose.onNodeWithTag("guide_blocker_0").fetchSemanticsNode().boundsInRoot
        val mask=compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().boundsInRoot
        assertEquals(mask,guard)
        assertEquals(scrim,compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().id)
        compose.mainClock.autoAdvance=true
        compose.waitUntil(10_000) { !runtime.preparing }
        assertEquals(540,runtime.goal.start)
        assertEquals(720,runtime.goal.end)
        for(stage in listOf(GuideStage.BUILDINGS,GuideStage.FILTER_TAGS,GuideStage.EXPECT,
                GuideStage.REPLACE,GuideStage.ABOUT,GuideStage.WHEELS,GuideStage.FROM_NOW)) {
            visit(stage)
            compose.waitUntil(10_000) { !runtime.preparing }
            assertEquals("Scrim must not remount at $stage",scrim,compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().id)
            compose.onAllNodes(isDialog()).assertCountEquals(1)
            assertEquals(runtime.window,runtime.presentedWindow)
        }
        compose.onNodeWithTag("guide_directory").performScrollTo().performClick()
        assertEquals(scrim,compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().id)
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithTag("guide_chapter_3").performClick()
        compose.waitUntil(10_000) { !runtime.preparing }
        assertEquals(scrim,compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().id)
        shot("single-window-stable-scrim")
    }

    @Test fun firstNineStepsExposeOnlyTheRequestedControlsAndClearSearchFocus() {
        render(GuideStage.NAV)
        visit(GuideStage.NAV)
        val navigation=compose.onNodeWithTag("workspace_navigation").fetchSemanticsNode().boundsInRoot
        val panel=compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Navigation guide should sit just above the bottom bar",panel.bottom<navigation.top && panel.bottom>navigation.top-100f)
        shot("nav-above-navigation")
        visit(GuideStage.SLOTS)
        compose.onNodeWithTag("room_search").assertIsNotEnabled()
        compose.onNodeWithTag("extra_filters").assertIsNotEnabled()
        assertEquals(listOf("slot_grid"),runtime.spotlightTargets)
        assertFalse(runtime.anchors.containsKey("room_result_count"))
        assertTrue(runtime.anchors.getValue("slot_grid").bounds.bottom>=compose.onNodeWithTag("slot_summary_space").fetchSemanticsNode().boundsInRoot.bottom)
        compose.onNodeWithTag("slot_1").performTouchInput { click() }
        compose.onNodeWithTag("slot_2").performTouchInput { click() }
        compose.onNodeWithTag("slot_summary").assertIsDisplayed()
        compose.onNodeWithTag("room_result_count").assertIsDisplayed()
        compose.onNodeWithTag("clear_slots").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.model.state.value.filters.selectedSlots.isEmpty() }
        shot("slots-and-count")
        visit(GuideStage.INSPECT)
        compose.onNodeWithTag("room_search").assertIsNotEnabled()
        compose.onNodeWithTag("extra_filters").assertIsNotEnabled()
        compose.onNodeWithTag("slot_time_tooltip").assertDoesNotExist()
        compose.onNodeWithTag("slot_2").performTouchInput { longClick() }
        assertTrue(runtime.model.state.value.filters.selectedSlots.isEmpty())
        visit(GuideStage.SEARCH)
        compose.onNodeWithTag("room_search").assertIsEnabled().performClick().performTextInput("201")
        compose.onNodeWithTag("room_search").assertIsFocused()
        compose.onNodeWithTag("guide_next").performScrollTo().performClick()
        compose.waitUntil(10_000) { runtime.preparedStage==GuideStage.FILTER && runtime.ready }
        compose.onNodeWithTag("room_search").assertIsNotFocused()
        compose.onNodeWithTag("extra_filters").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.stage==GuideStage.BUILDINGS && runtime.ready }
        compose.onNodeWithTag("filter_outlets").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.model.state.value.filters.outletsOnly }
        compose.onNodeWithTag("building_2").performTouchInput { click() }
        compose.waitUntil(10_000) { "2" in runtime.model.state.value.filters.buildings }
        compose.onNodeWithTag("reset_filters").performTouchInput { click() }
        compose.waitUntil(10_000) { runtime.model.state.value.filters.badgeCount==0 }
        shot("filter-choices-and-reset")
        visit(GuideStage.FILTER_TAGS)
        val editor=compose.onNodeWithTag("tag_editor").fetchSemanticsNode().boundsInRoot
        val tagPanel=compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Tag guide should remain beneath the editor",tagPanel.top>editor.bottom)
        shot("tag-editor-bottom")
        compose.onNodeWithTag("custom_tag_name").performTouchInput { click() }
        compose.onNodeWithTag("custom_tag_name").performTextInput("练习新增")
        compose.onNodeWithTag("add_tag").performTouchInput { click() }
        compose.waitUntil(10_000) { "练习新增" in runtime.model.state.value.tagOrder }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithTag("filter_tag_练习新增").assertIsDisplayed()
    }

    @Test fun tagEditorOpeningPassesActualLongPressReorderingAndDeleteConfirmation() {
        render(GuideStage.FILTER_TAGS)
        visit(GuideStage.FILTER_TAGS)
        val before=runtime.model.state.value.tagOrder
        val from=compose.onNodeWithTag("filter_outlets").fetchSemanticsNode().boundsInRoot
        val to=compose.onNodeWithTag("filter_tag_人少").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("filter_outlets").performTouchInput {
            down(center);advanceEventTime(650);moveTo(to.center-from.topLeft,delayMillis=200);up()
        }
        compose.waitUntil(10_000) { runtime.model.state.value.tagOrder!=before }
        compose.onNodeWithContentDescription("删除词条 人少").performTouchInput { click() }
        compose.onNodeWithTag("confirm_delete_tag").performTouchInput { click() }
        compose.waitUntil(10_000) { "人少" !in runtime.model.state.value.tagOrder }
        assertEquals(GuideStage.FILTER_TAGS,runtime.stage)
    }

    @Test fun navigationAndTagEditorStayBelowTheirTargetsWithLargeText() {
        render(GuideStage.NAV,large=true)
        visit(GuideStage.NAV)
        val nav=compose.onNodeWithTag("workspace_navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.bottom<nav.top)
        visit(GuideStage.FILTER_TAGS)
        val editor=compose.onNodeWithTag("tag_editor").fetchSemanticsNode().boundsInRoot
        assertTrue("Large-text panel overlaps tag editor",compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.top>editor.bottom)
        compose.onNodeWithTag("custom_tag_name").assertIsDisplayed()
        shot("large-tag-editor-bottom")
    }

    @Test fun directoryKeepsScrimMountedIncludingWhenChangingChaptersFromANativeDialog() {
        render(GuideStage.CAMPUS)
        visit(GuideStage.CAMPUS)
        compose.onNodeWithTag("guide_directory").performClick()
        compose.onNodeWithTag("guide_scrim").assertExists()
        compose.onNodeWithTag("guide_panel").assertDoesNotExist()
        shot("directory-with-scrim")
        compose.onNodeWithTag("guide_chapter_3").performClick()
        compose.onNodeWithTag("guide_scrim").assertExists()
        visit(GuideStage.THEME)
        visit(GuideStage.TAGS)
        compose.onNodeWithTag("guide_directory").performClick()
        compose.onNodeWithTag("guide_scrim").assertExists()
        compose.onNodeWithTag("guide_chapter_0").performClick()
        compose.onNodeWithTag("guide_scrim").assertExists()
        visit(GuideStage.INTRO)
    }

    @Test fun completedButtonSurvivesUndoRevisitAndSavedStateRestoration() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {
            YOURoomTheme {
                UsageGuideExperience(GuideRequest("test",GuideStage.CAMPUS.ordinal,serial=1),YOURoomState(),
                    onRuntime={ runtime=it }) { _,_ -> }
            }
        }
        compose.waitUntil(10_000) { ::runtime.isInitialized && runtime.preparedStage==GuideStage.CAMPUS }
        visit(GuideStage.CAMPUS)
        compose.onNodeWithTag("guide_demonstrate").performClick()
        compose.waitUntil(10_000) { runtime.done }
        compose.runOnIdle { runtime.model.setCampus(Campus.XITUCHENG) }
        compose.onNodeWithTag("guide_demonstrate").assertContentDescriptionEquals("已完成操作").assertTextEquals()
        visit(GuideStage.SLOTS)
        compose.onNodeWithTag("guide_demonstrate").assertTextEquals("替我演示")
        visit(GuideStage.CAMPUS)
        compose.onNodeWithTag("guide_demonstrate").assertContentDescriptionEquals("已完成操作").assertTextEquals()
        val old=runtime
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(10_000) { runtime!==old && runtime.preparedStage==GuideStage.CAMPUS }
        compose.onNodeWithTag("guide_demonstrate").assertContentDescriptionEquals("已完成操作").assertTextEquals()
        assertEquals(Campus.XITUCHENG,runtime.model.state.value.campus)
    }

    @Test fun backgroundingAndReturningNeverAdvanceTheManualGuide() {
        val owner=object:LifecycleOwner { override val lifecycle=LifecycleRegistry(this) }
        compose.runOnIdle { owner.lifecycle.currentState=Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                YOURoomTheme {
                    UsageGuideExperience(GuideRequest("test",serial=1),YOURoomState(),
                        onRuntime={ runtime=it }) { _,_ -> }
                }
            }
        }
        compose.waitUntil(10_000) { ::runtime.isInitialized && runtime.preparedStage==GuideStage.INTRO }
        compose.runOnIdle { owner.lifecycle.currentState=Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeBy(15_000)
        compose.runOnIdle { owner.lifecycle.currentState=Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(15_000)
        compose.waitForIdle()
        assertEquals(GuideStage.INTRO,runtime.stage)
        compose.onNodeWithTag("guide_pause").assertDoesNotExist()
    }
}
