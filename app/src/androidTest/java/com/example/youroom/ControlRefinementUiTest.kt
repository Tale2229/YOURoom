package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

class ControlRefinementUiTest {
    @get:Rule val compose=createComposeRule()
    private val snapshot=DemoData.snapshot()
    private val room=snapshot.classrooms.first()
    private var profile by mutableStateOf(room.emptyProfile())
    private var state by mutableStateOf(YouRoomState(initializing=false,demo=true,snapshot=snapshot,today=snapshot.queryDate))
    private var closed=false
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(150)
        File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun filterButtonUsesTheSameOpenCornerGeometryAsAPeriod() {
        compose.setContent { YouRoomTheme { Surface {
            SlotPicker(Campus.XITUCHENG,snapshot.queryDate,true,SearchFilters(),snapshot.slots,{})
        } } }
        val period=compose.onNodeWithTag("slot_face_1",true).captureToImage().toPixelMap()
        val filter=compose.onNodeWithTag("extra_filters_face",true).captureToImage().toPixelMap()
        assertEquals(period.width,filter.width);assertEquals(period.height,filter.height)
        // Compare the whole perimeter band, avoiding the different center glyphs.
        for(x in 0 until period.width) for(y in 0 until period.height) {
            if(y<period.height/4 || y>=period.height*3/4 || x<period.width/5 || x>=period.width*4/5)
                assertTrue("edge $x,$y",kotlin.math.abs(period[x,y].red-filter[x,y].red)+
                    kotlin.math.abs(period[x,y].green-filter[x,y].green)+kotlin.math.abs(period[x,y].blue-filter[x,y].blue)<.015f)
        }
    }

    @Test fun filterFooterHasOneCenteredPlusAndKeepsAddingEditingAndDeletion() {
        compose.setContent { YouRoomTheme {
            FilterDialog(state, {closed=true},{state=state.copy(filters=it)},
                { name -> state=state.copy(tagCatalog=state.tagCatalog.added(name)) },
                {name -> state=state.copy(tagCatalog=state.tagCatalog.deleted(name))},{})
        } }
        assertFooter("reset_filters","close_filters")
        compose.onNodeWithText("＋").assertDoesNotExist()
        compose.onNodeWithTag("edit_tags").performClick().assertIsSelected()
        compose.onNodeWithTag("custom_tag_name").performTextInput("安静角落")
        compose.onNodeWithTag("add_tag").performClick()
        compose.onNodeWithTag("filter_tag_安静角落").assertExists()
        compose.onNodeWithContentDescription("删除词条 安静角落").performClick()
        compose.onNodeWithTag("confirm_delete_tag").performClick()
        compose.onNodeWithTag("filter_tag_安静角落").assertDoesNotExist()
        compose.onNodeWithTag("filter_outlets").assertExists()
        shot("filter-corners-footer-1.16.1")
    }

    @Test fun roomTagFooterCentersPlusAndKeepsTogglingAndResettingTags() {
        compose.setContent { YouRoomTheme {
            RoomTagsDialog(RoomMatch(room,profile,emptySet()),state,{closed=true},{profile=it},{},{},{})
        } }
        assertFooter("reset_room_tags","close_room_tags")
        compose.onNodeWithTag("edit_tags").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("删除词条 有插座").assertExists()
        compose.onNodeWithTag("filter_outlets").performClick()
        assertTrue(profile.outlets)
        compose.onNodeWithTag("reset_room_tags").performClick()
        assertFalse(profile.outlets)
        compose.onNodeWithTag("close_room_tags").performClick()
        assertTrue(closed)
    }

    @Test fun noteLabelFloatsOnFocusAndTrashAndCountStayInsideTheFrame() {
        compose.setContent { YouRoomTheme { RoomNoteDialog(RoomMatch(room,profile,emptySet()),{closed=true},{profile=it}) } }
        val field=compose.onNodeWithTag("room_note_frame").fetchSemanticsNode().boundsInRoot
        val label=compose.onNodeWithTag("room_note_label",true).assertTextEquals("个人备注").fetchSemanticsNode().boundsInRoot
        val count=compose.onNodeWithTag("room_note_count",true).assertTextEquals("0/20").fetchSemanticsNode().boundsInRoot
        val clear=compose.onNodeWithTag("clear_room_note").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val close=compose.onNodeWithTag("close_room_note").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(label.top>field.top+with(compose.density) { 8.dp.toPx() })
        assertTrue(count.bottom<=field.bottom && count.right<field.right)
        assertTrue(clear.left>=field.left && clear.right<=field.right)
        assertTrue(close.top-field.bottom<=with(compose.density) { 4.dp.toPx() })
        assertTrue(field.height<=with(compose.density) { 80.dp.toPx() })
        compose.onNodeWithText("清空").assertDoesNotExist()
        compose.onNodeWithText("关闭").assertDoesNotExist()
        shot("note-empty-1.16.2")
        compose.onNodeWithTag("room_note").performClick()
        val floated=compose.onNodeWithTag("room_note_label",true).fetchSemanticsNode().boundsInRoot
        assertTrue(floated.top<label.top)
        compose.onNodeWithTag("room_note").performTextInput("靠窗座位")
        compose.onNodeWithTag("room_note_label",true).assertTextEquals("个人备注")
        compose.onNodeWithTag("room_note_count",true).assertTextEquals("4/20")
        assertEquals("靠窗座位",profile.note)
        shot("note-editing-1.16.2")
        compose.onNodeWithTag("clear_room_note").performClick()
        assertEquals("",profile.note)
        compose.onNodeWithTag("close_room_note").performClick()
        assertTrue(closed)
    }

    @Test fun stopGlyphGrowsButAllFourVisibleFacesRemainSquare() {
        compose.setContent { YouRoomTheme { Surface {
            PlanningTimeControls(PlanningDraft(),true,true,true,true,0,true,{},{},{},{},{},{},{},{},true,{})
        } } }
        for(tag in listOf("planner_add_period","planner_expected","planner_adjust","planner_generate"))
            compose.onNodeWithTag("${tag}_face",true).assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
        compose.onNodeWithTag("planner_stop_icon",true).assertWidthIsEqualTo(22.dp).assertHeightIsEqualTo(22.dp)
        compose.onNodeWithTag("planner_generate").assertContentDescriptionEquals("终止规划").assertIsEnabled()
        shot("planning-controls-stop-1.16.1")
    }

    private fun assertFooter(reset: String,close: String) {
        compose.onAllNodesWithTag("edit_tags").assertCountEquals(1)
        val left=compose.onNodeWithTag(reset).fetchSemanticsNode().boundsInRoot
        val middle=compose.onNodeWithTag("edit_tags").assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        val right=compose.onNodeWithTag(close).fetchSemanticsNode().boundsInRoot
        assertEquals((left.center.x+right.center.x)/2,middle.center.x,1f)
        assertEquals(left.center.y,middle.center.y,1f);assertEquals(right.center.y,middle.center.y,1f)
        val trash=compose.onNodeWithTag("${reset}_icon",true).fetchSemanticsNode().boundsInRoot
        val rise=with(compose.density) { 1.dp.toPx() }
        assertEquals("both tag and filter trash move, not their hit areas",left.center.y-rise,trash.center.y,.5f)
        assertEquals(with(compose.density) { 48.dp.toPx() },left.height,.5f)
    }
}
