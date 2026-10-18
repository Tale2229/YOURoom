package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
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
import kotlin.math.abs

class MatisseThemeUiTest {
    @get:Rule val compose=createComposeRule()
    // Synthetic fixture in a normal workspace, so the demo-only snapshot guard accepts it.
    private val data=DemoData.snapshot().copy(isDemo=false)
    private val appearance=AppearancePreferences(westTone=CampusTone.MATISSE)
    private val room=data.classrooms.first()
    private val profile=room.emptyProfile().copy(favorite=true,note="窗边位置安静",outlets=true,excluded=true)
    private val state=YOURoomState(initializing=false,snapshot=data,today=data.queryDate,
        appearance=appearance,profiles=mapOf(room.id to profile))
    private val night get()=InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    private val colors get()=campusColorScheme(Campus.XITUCHENG,night,CampusTone.MATISSE)
    private val roles get()=campusThemeRoles(colors,CampusTone.MATISSE,night)
    private fun textColor(tag: String): Color = textColor(compose.onNodeWithTag(tag,true))
    private fun textColor(node: SemanticsNodeInteraction): Color {
        val results=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.style.color
    }
    private fun assertPaint(tag: String,color: Color) {
        val p=compose.onNodeWithTag(tag,true).captureToImage().toPixelMap()
        val count=(0 until p.width).sumOf { x -> (0 until p.height).count { y ->
            val c=p[x,y];abs(c.red-color.red)+abs(c.green-color.green)+abs(c.blue-color.blue)<.03f
        } }
        assertTrue("$tag must actually paint $color",count>10)
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync()
        File(i.targetContext.getExternalFilesDir(null),"matisse-$name-${if(night) "dark" else "light"}.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun sharedHeaderAndNavigationUseBlueGreyWithOneSmallYellowGreenSelection() {
        var tab by mutableIntStateOf(0)
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface { Column {
            CampusHeader(state,{})
            Spacer(Modifier.weight(1f))
            WorkspaceNavigation(tab) { tab=it }
        } } } }
        assertPaint("campus_header",roles.header)
        assertEquals(roles.onHeader,textColor("campus_brand"))
        assertEquals(roles.headerMuted,textColor("campus_date"))
        assertPaint("tab_rooms_face",roles.navigationSelected)
        assertPaint("tab_settings_face",roles.navigation)
        compose.onNodeWithTag("tab_planner").performClick()
        assertPaint("tab_planner_face",roles.navigationSelected)
        assertPaint("tab_rooms_face",roles.navigation)
    }
    @Test fun settingsKeepBrownIconsAndBlueGreyTextAndExposeMatisseInTheBelt() {
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface { Column {
            Box(Modifier.weight(1f)) { SettingsScreen(state,{},{},{}) }
            WorkspaceNavigation(2,{})
        } } } }
        compose.onNodeWithTag("settings_theme_name").assertTextEquals("马蒂斯")
        assertPaint("settings_theme_frame",roles.impressionFrame)
        assertPaint("settings_strip_layout_icon",roles.settingsIcon)
        assertPaint("settings_strip_numbers_icon",roles.settingsIcon)
        assertEquals(colors.onSurface,textColor(compose.onNodeWithText("时段条样式")))
        assertNotEquals(colors.onSurface,roles.settingsIcon)
        shot("settings")
    }
    @Test fun plannerUsesGreyGreenWheelBandsBlueGreyHeadingAndBrickRedLaunch() {
        val goal=PlanningDraft(start=480,end=720,flexible=true,savedWindows=listOf(StudyWindow(480,600)))
        val plan=StudyPlan(StudyWindow(480,600),listOf(PlanningStep.Study(480,600,room,true)),120,120,0,0,0,0,0,0,0)
        val result=PlanningResult(options=listOf(PlanningOption("综合推荐",plan)),
            groups=listOf(PlanningGroup(PlanningDirection.BALANCED,listOf(0))))
        val execution=PlanningExecution(1,PlanningInput(data,Campus.XITUCHENG,data.queryDate,state.profiles,
            PlanningSchedule.confirmedGoal(goal)!!),result)
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface { Column {
            Box(Modifier.weight(1f)) { StudyPlanningScreen(state,goal,mapOf(Campus.XITUCHENG to execution),{},{},{},{},{}) }
            WorkspaceNavigation(1,{})
        } } } }
        assertPaint("planner_start_hour_wheel_center_band",roles.wheelCenter)
        assertPaint("planner_start_minute_wheel_center_band",roles.wheelCenter)
        assertPaint("planner_generate_face",roles.launch)
        assertPaint("planner_adjust_face",roles.enabledOption)
        assertPaint("planner_alternatives_face",roles.planningHeading)
        assertEquals(roles.onPlanningHeading,textColor("planner_alternatives_title"))
        assertPaint("planner_alternatives_key",roles.planningHeadingKey)
        shot("planner")
    }
    @Test fun classroomFavoriteNotesAndOpenedRailUseSmallAccentsWithoutRecoloringItsBody() {
        var expanded by mutableStateOf(false)
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface { Column {
            CampusHeader(state,{})
            SwipeRoomRow(RoomMatch(room,profile,setOf(1,2,6)),data.slots,PersonalTags.defaultOrder,expanded,true,
                { expanded=it },{},{},{},{})
            Spacer(Modifier.weight(1f))
            WorkspaceNavigation(0,{})
        } } } }
        assertPaint("room_favorite_${room.id}",roles.favorite)
        assertEquals(roles.note,textColor("room_note_text_${room.id}"))
        shot("room")
        compose.runOnIdle { expanded=true }
        for (index in 0..3) assertPaint("room_action_icon_${room.id}_$index",roles.selectedRoomAction)
        compose.onNodeWithTag("room_action_${room.id}_0").assertHasClickAction()
        shot("room-actions")
    }

    @Test fun expectedSelectionHasNoGreyGreenOutlineAndStillRemovesOnClick() {
        var selected by mutableStateOf(setOf(room.id))
        compose.setContent { YOURoomTheme(appearance=appearance) { ExpectedRoomDialog(state,selected,{selected=it},{}) } }
        val card=compose.onNodeWithTag("expected_selected_${room.id}")
        val p=card.captureToImage().toPixelMap()
        val border=colors.outlineVariant
        val borderPixels=(0 until p.width).sumOf { x -> (0 until p.height).count { y ->
            val c=p[x,y]
            abs(c.red-border.red)+abs(c.green-border.green)+abs(c.blue-border.blue)<.008f
        } }
        assertEquals("No selected-room outline should remain",0,borderPixels)
        shot("expected-rooms")
        card.performClick()
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
    }

    @Test fun inactiveRailButtonsKeepTheirNeutralColor() {
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface(color=roles.roomActions) {
            RoomActionButtons(room.emptyProfile(),{},{},{},{},onRail=true)
        } } }
        for (index in 0..3) assertPaint("room_action_icon_${room.id}_$index",roles.onRoomActions)
    }
}
