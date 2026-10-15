package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ExpectedRoomUiTest {
    @get:Rule val compose = createComposeRule()
    private var goal by mutableStateOf(PlanningDraft())
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var requested by mutableStateOf(false)
    private var profiles by mutableStateOf<Map<String,RoomProfile>>(emptyMap())
    private fun render() {
        compose.setContent { YouRoomTheme(campus = campus) {
            val data=DemoData.snapshot(campus)
            StudyPlanningScreen(YouRoomState(initializing=false,demo=true,campus=campus,snapshot=data,today=data.queryDate,profiles=profiles),
                goal,requested,{goal=it;requested=false},{requested=true},{},{},{},showHeader=false)
        } }
    }
    private fun select(tag: String,index: Int) {
        val action=compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.SetProgress].action!!
        compose.runOnIdle { action(index.toFloat()) }
        compose.waitForIdle()
    }
    private fun value(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo].current.toInt()
    private fun open() = compose.onNodeWithTag("planner_expected").performScrollTo().performClick()

    @Test fun theThreeActionsShareOneRowInTheRequestedOrder() {
        render()
        val bounds=listOf("planner_expected","planner_adjust","planner_generate").map {
            compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertEquals(bounds[0].top,bounds[1].top,1f)
        assertEquals(bounds[1].top,bounds[2].top,1f)
        assertTrue(bounds[0].right < bounds[1].left && bounds[1].right < bounds[2].left)
        compose.onNodeWithTag("planner_adjust").assertTextContains("宽松模式").assertIsOn().performClick().assertIsOff()
        assertFalse(goal.flexible)
        compose.onNodeWithTag("planner_origin").assertDoesNotExist()
        compose.onNodeWithText("允许调整时间").assertDoesNotExist()
    }

    @Test fun onlyPlusCreatesPeriodBlocksBelowTheWheels() {
        render()
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_saved_period_0").assertDoesNotExist()
        val add=compose.onNodeWithTag("planner_add_period").fetchSemanticsNode().boundsInRoot
        val wheel=compose.onNodeWithTag("planner_end_minute_wheel").fetchSemanticsNode().boundsInRoot
        assertTrue(add.top >= wheel.bottom)
        compose.onNodeWithTag("planner_add_period").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("planner_saved_period_0").assertTextContains("08:00–12:00")
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_saved_period_1").assertDoesNotExist()
    }

    @Test fun rollingTheWheelDoesNotCreateAPeriodBeforePlus() {
        render()
        val wheel=compose.onNodeWithTag("planner_start_minute_wheel")
        val row=wheel.fetchSemanticsNode().boundsInRoot.height/3.5f
        wheel.performTouchInput { down(center); moveBy(Offset(0f,-row),delayMillis=500) }
        compose.onNodeWithTag("planner_saved_period_0").assertDoesNotExist()
        wheel.performTouchInput { advanceEventTime(200); up() }
        compose.waitForIdle()
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_saved_period_0").assertDoesNotExist()
        compose.onNodeWithTag("planner_add_period").performClick()
        compose.onNodeWithTag("planner_saved_period_0").assertTextContains("08:01–12:00")
    }

    @Test fun pinsAddMultipleRoomsAndClosingUpdatesTheBadge() {
        render(); open()
        compose.onNodeWithTag("expected_pin").performClick()
        assertEquals(1,goal.expectedRooms.size)
        select("expected_classroom_wheel",1)
        compose.onNodeWithTag("expected_pin").performClick()
        assertEquals(2,goal.expectedRooms.size)
        compose.onNodeWithTag("expected_room_dialog").assertIsDisplayed()
        compose.onNodeWithTag("expected_close").performClick()
        compose.onNodeWithTag("planner_expected_badge").assertTextEquals("2")
        open()
        compose.onNodeWithTag("expected_clear").performClick()
        assertTrue(goal.expectedRooms.isEmpty())
        compose.onNodeWithTag("expected_close").performClick()
        compose.onNodeWithTag("planner_expected_badge").assertDoesNotExist()
    }

    @Test fun classroomWheelTracksTheBuildingWithoutLosingPreviousSelections() {
        render(); open()
        compose.onNodeWithTag("expected_pin").performClick()
        val first=goal.expectedRooms.single()
        select("expected_building_wheel",2)
        assertEquals(0,value("expected_classroom_wheel"))
        compose.onNodeWithTag("expected_pin").performClick()
        assertEquals(2,goal.expectedRooms.size)
        assertTrue(first in goal.expectedRooms)
        assertTrue(goal.expectedRooms.any { it.startsWith("01:3-") })
    }

    @Test fun finiteWheelsDoNotWrapAndTheActionsAlignWithTheThreeColumns() {
        render(); open()
        val tags=listOf("expected_building_wheel","expected_classroom_wheel")
        for(tag in tags) {
            select(tag,0)
            val node=compose.onNodeWithTag(tag).fetchSemanticsNode()
            val previous=node.config[SemanticsActions.CustomActions].single { it.label.startsWith("上一") }
            compose.runOnIdle { previous.action() }
            compose.waitForIdle()
            assertEquals(0,value(tag))
            val maximum=node.config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive.toInt()
            select(tag,maximum)
            val next=compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label.startsWith("下一") }
            compose.runOnIdle { next.action() }
            compose.waitForIdle()
            assertEquals(maximum,value(tag))
        }
        val left=compose.onNodeWithTag(tags[0]).fetchSemanticsNode().boundsInRoot
        val right=compose.onNodeWithTag(tags[1]).fetchSemanticsNode().boundsInRoot
        val selected=compose.onNodeWithTag("expected_selected").fetchSemanticsNode().boundsInRoot
        val clear=compose.onNodeWithTag("expected_clear").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val pin=compose.onNodeWithTag("expected_pin").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val close=compose.onNodeWithTag("expected_close").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(left.right <= right.left && right.right <= selected.left)
        assertEquals(left.width,right.width,1f)
        assertEquals(right.width,selected.width,1f)
        assertEquals(left.center.x,clear.center.x,1f)
        assertEquals(right.center.x,pin.center.x,1f)
        assertEquals(selected.center.x,close.center.x,1f)
        assertEquals(clear.center.y,pin.center.y,1f)
        assertEquals(pin.center.y,close.center.y,1f)
        assertTrue(pin.top >= right.bottom)
        assertEquals(35f,ExpectedPinTilt,0f)
        compose.onNodeWithText("清空").assertDoesNotExist()
        compose.onNodeWithText("关闭").assertDoesNotExist()
    }

    @Test fun aMovingWheelCannotPinAnIntermediateClassroom() {
        render(); open()
        val wheel=compose.onNodeWithTag("expected_classroom_wheel")
        wheel.performTouchInput { down(center); moveBy(Offset(0f,-height/3.5f),delayMillis=500) }
        compose.onNodeWithTag("expected_pin").assertIsNotEnabled()
        assertTrue(goal.expectedRooms.isEmpty())
        wheel.performTouchInput { advanceEventTime(200); up() }
        compose.waitForIdle()
        compose.onNodeWithTag("expected_pin").assertIsEnabled().performClick()
        assertEquals(setOf("01:2-240"),goal.expectedRooms)
    }

    @Test fun pinningAnAlreadySelectedRoomRemovesIt() {
        render(); open()
        compose.onNodeWithTag("expected_pin").assertContentDescriptionEquals("添加期望教室").performClick()
        compose.onNodeWithTag("expected_pin").assertContentDescriptionEquals("取消期望教室").performClick()
        assertTrue(goal.expectedRooms.isEmpty())
    }

    @Test fun badgesAndClearingAreCampusSpecific() {
        goal=PlanningDraft(expectedRooms=setOf("01:2-201","04:演示楼-A101","04:演示楼-A102"))
        render()
        compose.onNodeWithTag("planner_expected_badge").assertTextEquals("1")
        compose.runOnIdle { campus=Campus.SHAHE }
        compose.waitForIdle()
        compose.onNodeWithTag("planner_expected_badge").assertTextEquals("2")
        open()
        compose.onNodeWithTag("expected_clear").performClick()
        assertEquals(setOf("01:2-201"),goal.expectedRooms)
    }

    @Test fun expectedRoomsSurviveRestorationWithTheCurrentAndSavedPeriods() {
        val data=DemoData.snapshot()
        val restoration=StateRestorationTester(compose)
        restoration.setContent {
            var saved by rememberSaveable(stateSaver=PlanningGoalSaver) { mutableStateOf(PlanningDraft()) }
            YouRoomTheme { StudyPlanningScreen(YouRoomState(initializing=false,demo=true,snapshot=data,today=data.queryDate),
                saved,false,{saved=it},{},{},{},{},showHeader=false) }
        }
        open()
        compose.onNodeWithTag("expected_pin").performClick()
        compose.onNodeWithTag("expected_close").performClick()
        compose.onNodeWithTag("planner_add_period").performClick()
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("planner_expected_badge").assertTextEquals("1")
        compose.onNodeWithTag("planner_saved_period_0").assertExists()
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
    }

    @Test fun pinningThenGeneratingActuallyUsesTheDesiredRoom() {
        render(); open()
        select("expected_classroom_wheel",1)
        compose.onNodeWithTag("expected_pin").performClick()
        compose.onNodeWithTag("expected_close").performClick()
        compose.onNodeWithTag("planner_add_period").performClick()
        compose.onNodeWithTag("planner_generate").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("planner_generate").fetchSemanticsNodes().any {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "生成规划" } == true
            }
        }
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        compose.onNodeWithTag("planner_step_0").assertTextContains("2-240")
    }

    @Test fun favoritesAreTheFirstCategoryAndKeepBuildingIdentity() {
        val rooms=DemoData.snapshot().classrooms
        val a=rooms.first { it.name == "2-201" }
        val b=rooms.first { it.building == "3" }
        profiles=mapOf(a.id to a.emptyProfile().copy(favorite=true),b.id to b.emptyProfile().copy(favorite=true))
        render(); open()
        assertEquals(0,value("expected_building_wheel"))
        assertEquals("收藏",compose.onNodeWithTag("expected_building_wheel").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals("教2-201",compose.onNodeWithTag("expected_classroom_wheel").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        compose.onNodeWithTag("expected_pin").performClick()
        select("expected_classroom_wheel",1)
        compose.onNodeWithTag("expected_pin").performClick()
        assertEquals(setOf(a.id,b.id),goal.expectedRooms)
        val first=compose.onNodeWithTag("expected_selected_${a.id}").fetchSemanticsNode().boundsInRoot
        val second=compose.onNodeWithTag("expected_selected_${b.id}").fetchSemanticsNode().boundsInRoot
        val wheel=compose.onNodeWithTag("expected_classroom_wheel").fetchSemanticsNode().boundsInRoot
        val list=compose.onNodeWithTag("expected_selected").fetchSemanticsNode().boundsInRoot
        assertEquals(list.top,first.top,1f)
        assertEquals(first.left,second.left,1f)
        assertEquals(first.right,second.right,1f)
        assertTrue(first.bottom <= second.top && first.left >= wheel.right)
        compose.onNodeWithTag("expected_selected_${a.id}").performClick()
        assertEquals(setOf(b.id),goal.expectedRooms)
        assertEquals(2,compose.onNodeWithTag("expected_classroom_wheel").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive.toInt()+1)
    }

    @Test fun emptyFavoritesDisablePinningWithoutPreventingOtherBuildings() {
        render(); open()
        select("expected_building_wheel",0)
        compose.onNodeWithTag("expected_empty_classrooms").assertTextEquals("暂无收藏")
        compose.onNodeWithTag("expected_classroom_wheel").assertDoesNotExist()
        compose.onNodeWithTag("expected_pin").assertIsNotEnabled()
        select("expected_building_wheel",1)
        compose.onNodeWithTag("expected_classroom_wheel").assertIsDisplayed()
        compose.onNodeWithTag("expected_pin").assertIsEnabled().performClick()
        assertEquals(setOf("01:2-201"),goal.expectedRooms)
    }

    @Test fun returnClosesWithoutPinningThePreviewAndFavoritesStayCampusSpecific() {
        profiles=mapOf("01:2-201" to Classroom("01:2-201","2-201","2",60).emptyProfile().copy(favorite=true))
        render(); open()
        assertEquals(0,value("expected_building_wheel"))
        compose.onNodeWithTag("expected_close").assertContentDescriptionEquals("完成选择并关闭").performClick()
        assertTrue(goal.expectedRooms.isEmpty())
        compose.runOnIdle { campus=Campus.SHAHE }
        compose.waitForIdle(); open()
        select("expected_building_wheel",0)
        compose.onNodeWithTag("expected_empty_classrooms").assertTextEquals("暂无收藏")
        compose.onNodeWithTag("expected_pin").assertIsNotEnabled()
    }

    @Test fun manySelectedRoomsScrollInOneColumnAndKeepActionsVisible() {
        val rooms=DemoData.snapshot().classrooms
        goal=PlanningDraft(expectedRooms=rooms.map { it.id }.toSet())
        render(); open()
        compose.onNodeWithTag("expected_clear").assertIsDisplayed()
        compose.onNodeWithTag("expected_pin").assertIsDisplayed()
        compose.onNodeWithTag("expected_close").assertIsDisplayed()
        val last=rooms.maxBy { it.id }
        compose.onNodeWithTag("expected_selected").performScrollToNode(hasTestTag("expected_selected_${last.id}"))
        compose.onNodeWithTag("expected_selected_${last.id}").assertIsDisplayed().performClick()
        assertFalse(last.id in goal.expectedRooms)
        compose.onNodeWithTag("expected_clear").performClick()
        assertTrue(goal.expectedRooms.isEmpty())
        compose.onNodeWithTag("expected_clear").assertIsNotEnabled()
    }

    @Test fun longFavoriteNamesFitAlongsideTheSelectedColumn() {
        val rooms=DemoData.snapshot().classrooms
        val a=rooms.first { it.name == "2-201" }
        val main=rooms.first { it.name == "未来学习大楼-202-203" }
        profiles=listOf(a,main).associate { it.id to it.emptyProfile().copy(favorite=true) }
        goal=PlanningDraft(expectedRooms=setOf(a.id,main.id))
        render(); open()
        select("expected_classroom_wheel",1)
        assertEquals("主楼-202-203",compose.onNodeWithTag("expected_classroom_wheel").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription])
        compose.onNodeWithTag("expected_selected_${main.id}").assertIsDisplayed()
        compose.onNodeWithTag("expected_close").assertIsDisplayed()
        val image=compose.onNodeWithTag("expected_room_dialog").captureToImage().asAndroidBitmap()
        val target=File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir,
            "expected-favorites-1.12.1.png")
        target.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test fun shaheFavoritesKeepTheirOriginalTwoLineWheelPresentation() {
        campus=Campus.SHAHE
        val room=DemoData.snapshot(campus).classrooms.first()
        profiles=mapOf(room.id to room.emptyProfile().copy(favorite=true))
        render();open()
        val value=compose.onNodeWithTag("expected_classroom_wheel").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue(value.contains('\n'))
        assertEquals(room.name.substringAfter('-',room.name),value.substringAfter('\n'))
        val image=compose.onNodeWithTag("expected_room_dialog").captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir,"expected-shahe-1.12.1.png")
            .outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
