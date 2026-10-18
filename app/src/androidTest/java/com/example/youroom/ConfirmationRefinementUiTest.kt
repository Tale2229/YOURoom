package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class ConfirmationRefinementUiTest {
    @get:Rule val compose=createComposeRule()
    private val data=DemoData.snapshot()
    private val room=data.classrooms.first()
    private fun near(a: Color,b: Color)=abs(a.red-b.red)+abs(a.green-b.green)+abs(a.blue-b.blue)<.035f

    @Test fun checkDrawsAboveACompleteUnderlineAndPausesBeforeClosingOnce() {
        var closes=0
        compose.setContent { YOURoomTheme(darkTheme=false) { Surface {
            DialogExitAction(true,{closes++},Modifier.testTag("confirm"))
        } } }
        compose.waitForIdle();compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("confirm").performClick()
        compose.mainClock.advanceTimeByFrame()
        val initial=compose.onNodeWithTag("confirm").fetchSemanticsNode().config
        assertTrue(initial[ConfirmationRunning]);assertEquals(0,closes)
        assertTrue(initial[ConfirmationDrawProgress]<.08f)
        val blank=compose.onNodeWithTag("dialog_exit_save",true).captureToImage().toPixelMap()
        val ink=campusColorScheme(Campus.XITUCHENG,false).primary
        assertTrue((0 until blank.width).sumOf { x -> (0 until blank.height*5/6).count { y -> near(blank[x,y],ink) } }<5)
        fun underlinePixels(p: androidx.compose.ui.graphics.PixelMap) = (0 until p.width).sumOf { x ->
            (p.height*5/6 until p.height).count { y -> near(p[x,y],ink) } }
        val fullUnderline=underlinePixels(blank)
        assertTrue("Underline appears immediately",fullUnderline>5)
        compose.mainClock.advanceTimeBy(144)
        val middle=compose.onNodeWithTag("confirm").fetchSemanticsNode().config
        assertTrue(middle[ConfirmationDrawProgress] in .1f.. .9f)
        val image=compose.onNodeWithTag("dialog_exit_save",true).captureToImage().toPixelMap()
        val underline=(0 until image.width).sumOf { x -> (image.height*5/6 until image.height).count { y -> near(image[x,y],ink) } }
        assertEquals("Underline stays fully drawn",fullUnderline,underline)
        compose.onNodeWithTag("confirm").assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertEquals(0,closes)
        compose.mainClock.advanceTimeBy(200)
        val complete=compose.onNodeWithTag("confirm").fetchSemanticsNode().config
        assertEquals(1f,complete[ConfirmationDrawProgress],0f)
        assertTrue(complete[ConfirmationRunning]);assertEquals(0,closes)
        compose.mainClock.advanceTimeBy(64)
        assertEquals(0,closes)
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1,closes) }
        compose.onNodeWithTag("confirm").assertIsEnabled()
        compose.mainClock.autoAdvance=true
    }

    @Test fun noteEditsShowCheckRevertingShowsCrossAndTheTrashIsFivePhysicalPixelsHigher() {
        var profile by mutableStateOf(room.emptyProfile())
        var closes=0;var writes=0
        compose.setContent { YOURoomTheme { RoomNoteDialog(RoomMatch(room,profile,emptySet()),{closes++},{profile=it;writes++}) } }
        val button=compose.onNodeWithTag("clear_room_note").fetchSemanticsNode().boundsInRoot
        val icon=compose.onNodeWithTag("room_note_trash_icon",true).fetchSemanticsNode().boundsInRoot
        assertEquals(button.center.y-5,icon.center.y,.6f)
        compose.onNodeWithTag("dialog_exit_close",true).assertExists()
        compose.onNodeWithTag("room_note").performTextInput("靠窗")
        compose.onNodeWithTag("dialog_exit_save",true).assertExists()
        compose.onNodeWithTag("clear_room_note").performClick()
        compose.onNodeWithTag("dialog_exit_close",true).assertExists()
        compose.onNodeWithTag("room_note").performTextInput("安静")
        val before=writes
        compose.onNodeWithTag("close_room_note").performClick()
        compose.runOnIdle { assertEquals(1,closes);assertEquals(before,writes) }
    }

    @Test fun tagAndCombinationNameFieldsShareHeightAndCancelExitsWithoutCreatingATag() {
        var editing by mutableStateOf(true)
        var name by mutableStateOf("")
        var adds=0
        val state=YOURoomState(initializing=false,demo=true,snapshot=data,today=data.queryDate)
        compose.setContent { YOURoomTheme { Surface { Column(Modifier.padding(20.dp)) {
            CompactNameField(name,{name=it},"组合名称",Modifier.fillMaxWidth().testTag("comparison_name"))
            Spacer(Modifier.height(20.dp))
            TagEditor(state,{},{adds++},{},{},editing=editing,onCancelEdit={editing=false})
        } } } }
        val a=compose.onNodeWithTag("comparison_name").fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithTag("custom_tag_name").fetchSemanticsNode().boundsInRoot
        assertEquals(a.height,b.height,.5f)
        compose.onNodeWithTag("custom_tag_name").performTextInput("未添加")
        compose.onNodeWithTag("cancel_add_tag").performClick()
        compose.onNodeWithTag("custom_tag_name").assertDoesNotExist()
        assertEquals(0,adds)
    }

    @Test fun twoAndThreePeriodSummariesKeepTheSameFixedColumnsInBothDialogs() {
        val windows=listOf(StudyWindow(480,540),StudyWindow(840,900),StudyWindow(1200,1320))
        val templates=listOf(TimeTemplate("two","两段",windows.take(2)),TimeTemplate("three","三段",windows))
        var importer by mutableStateOf(false)
        compose.setContent { YOURoomTheme {
            if(importer) TimeTemplateImportDialog(Campus.XITUCHENG,templates,emptyList(),true,{null},{},{},{})
            else TimeTemplateManager(Campus.XITUCHENG,CampusPlanningPreferences(templates=templates),{},{})
        } }
        var firstLeft: Float?=null
        for(import in listOf(false,true)) {
            compose.runOnIdle { importer=import }
            val a=compose.onNodeWithTag("template_summary_period_two_1").fetchSemanticsNode().boundsInRoot
            val b=compose.onNodeWithTag("template_summary_period_three_1").fetchSemanticsNode().boundsInRoot
            assertEquals(a.left,b.left,.5f);assertEquals(a.width,b.width,.5f)
            val first=compose.onNodeWithTag("template_summary_period_three_0").fetchSemanticsNode().boundsInRoot
            if(firstLeft==null) firstLeft=first.left else assertEquals(firstLeft!!,first.left,.5f)
            val last=compose.onNodeWithTag("template_summary_period_three_2").fetchSemanticsNode().boundsInRoot
            assertEquals(first.center.y,last.center.y,.5f)
            assertTrue(first.right<a.left);assertTrue(b.right<last.left)
        }
    }

    @Test fun marksChangedThroughSettingsShowTheConfirmationCheckWithoutRepeatingTheClear() {
        var state by mutableStateOf(YOURoomState(initializing=false,demo=true,snapshot=data,today=data.queryDate,
            profiles=mapOf(room.id to room.emptyProfile().copy(favorite=true))))
        var clears=0
        compose.setContent { YOURoomTheme { Surface { SettingsScreen(state,{},
            {state=state.copy(profiles=emptyMap());clears++},{}) } } }
        compose.onNodeWithTag("settings_room_marks").performClick()
        compose.onNodeWithTag("dialog_exit_close",true).assertExists()
        compose.onNodeWithContentDescription("清除 ${room.name} 的个人标记").performClick()
        compose.onNodeWithText("清除",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("dialog_exit_save",true).assertExists()
        compose.onNodeWithTag("settings_sheet_close").performClick()
        compose.onNodeWithTag("settings_sheet_close").assertDoesNotExist()
        assertEquals(1,clears)
    }

    @Test fun originalDividersOnlyHideTheMatisseHeaderRuleWithoutChangingItsHeight() {
        var tone by mutableStateOf(CampusTone.MATISSE)
        compose.setContent { YOURoomTheme(darkTheme=false,appearance=AppearancePreferences(westTone=tone)) { Surface {
            Column(Modifier.fillMaxWidth().padding(top=20.dp)) {
                CampusHeaderDivider()
                Spacer(Modifier.height(10.dp))
                ThemeDivider(Modifier.testTag("ordinary_rule"))
            }
        } } }
        for(next in listOf(CampusTone.MATISSE,CampusTone.SAND,CampusTone.PINE)) {
            compose.runOnIdle { tone=next }
            val c=campusColorScheme(Campus.XITUCHENG,false,next)
            val r=campusThemeRoles(c,next,false)
            val header=compose.onNodeWithTag("campus_divider",true).assertHeightIsEqualTo(1.dp).captureToImage().toPixelMap()
            assertTrue(near(header[header.width/2,0],if(r.hideHeaderDivider)c.surface else c.outlineVariant))
            val line=compose.onNodeWithTag("ordinary_rule",true).captureToImage().toPixelMap()
            assertTrue(near(line[line.width/2,0],r.divider.compositeOver(c.surface)))
        }
    }
}
