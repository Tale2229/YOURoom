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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class PeachPlumThemeUiTest {
    @get:Rule val compose=createComposeRule()
    private val data=DemoData.snapshot().copy(isDemo=false)
    private val appearance=AppearancePreferences(westTone=CampusTone.PEACH_PLUM)
    private val room=data.classrooms.first()
    private val profile=room.emptyProfile().copy(favorite=true,outlets=true,note="窗边位置安静",excluded=true)
    private val state=YOURoomState(initializing=false,snapshot=data,today=data.queryDate,
        appearance=appearance,profiles=mapOf(room.id to profile))
    private val night get()=InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    private val colors get()=campusColorScheme(Campus.XITUCHENG,night,CampusTone.PEACH_PLUM)
    private val roles get()=campusThemeRoles(colors,CampusTone.PEACH_PLUM,night)
    private fun assertPaint(tag: String,color: Color) {
        val p=compose.onNodeWithTag(tag,true).captureToImage().toPixelMap()
        val count=(0 until p.width).sumOf { x->(0 until p.height).count { y->
            val c=p[x,y];abs(c.red-color.red)+abs(c.green-color.green)+abs(c.blue-color.blue)<.03f
        } }
        assertTrue("$tag paints $color",count>10)
    }
    private fun textColor(tag: String): Color {
        val result=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag,true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        return result.single().layoutInput.style.color
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync()
        File(i.targetContext.getExternalFilesDir(null),"peach-plum-$name-${if(night) "dark" else "light"}.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun headerAndNavigationUseSmokePlanesAndPeachSelections() {
        var tab by mutableIntStateOf(0)
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface { Column {
            CampusHeader(state,{});CampusHeaderDivider();Spacer(Modifier.weight(1f));WorkspaceNavigation(tab) { tab=it }
        } } } }
        assertPaint("campus_header",roles.header)
        assertEquals(roles.onHeader,textColor("campus_brand"));assertEquals(roles.headerMuted,textColor("campus_date"))
        assertPaint("tab_rooms_face",roles.enabledOption)
        assertPaint("tab_settings_face",roles.navigation)
        compose.onNodeWithTag("tab_planner").performClick()
        assertPaint("tab_planner_face",roles.enabledOption)
        compose.onNodeWithTag("campus_divider",true).assertHeightIsEqualTo(1.dp)
    }

    @Test fun settingsShowThePeachTeaArtworkWithoutChangingNativeTypography() {
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface { Column {
            Box(Modifier.weight(1f)) { SettingsScreen(state,{},{},{}) };WorkspaceNavigation(2,{})
        } } } }
        compose.onNodeWithTag("settings_theme_name").assertTextEquals("蜜桃乌梅")
        assertThemeFrame(compose.onNodeWithTag("settings_theme_frame"), CampusTone.PEACH_PLUM, colors.surface, night)
        assertPaint("settings_theme_previous_triangle",roles.themeArrow)
        assertPaint("settings_strip_layout_icon",roles.settingsIcon)
        assertThemeAvatar(compose.onNodeWithTag("settings_theme_impression", true), CampusTone.PEACH_PLUM)
        shot("settings")
    }

    @Test fun launchIsDeepSmokeWithWarmIconWhileLooseModeStaysPeach() {
        val goal=PlanningDraft(start=480,end=720,flexible=true,savedWindows=listOf(StudyWindow(480,600)))
        val plan=StudyPlan(StudyWindow(480,600),listOf(PlanningStep.Study(480,600,room,true)),120,120,0,0,0,0,0,0,0)
        val result=PlanningResult(options=listOf(PlanningOption("综合推荐",plan)),groups=listOf(PlanningGroup(PlanningDirection.BALANCED,listOf(0))))
        val execution=PlanningExecution(1,PlanningInput(data,Campus.XITUCHENG,data.queryDate,state.profiles,PlanningSchedule.confirmedGoal(goal)!!),result)
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface { Column {
            Box(Modifier.weight(1f)) { StudyPlanningScreen(state,goal,mapOf(Campus.XITUCHENG to execution),{},{},{},{},{}) }
            WorkspaceNavigation(1,{})
        } } } }
        assertPaint("planner_generate_face",roles.launch);assertPaint("planner_play_icon",roles.onLaunch)
        assertPaint("planner_adjust_face",roles.enabledOption)
        assertNotEquals(roles.launch,roles.enabledOption)
        assertPaint("planner_start_hour_wheel_center_band",roles.wheelCenter)
        assertPaint("planner_alternatives_face",roles.planningHeading)
        assertEquals(roles.onPlanningHeading,textColor("planner_alternatives_title"))
        shot("planner")
    }

    @Test fun activeRailIconsUsePeachWhileTheBodyKeepsItsOwnAccents() {
        var expanded by mutableStateOf(false)
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface { Column {
            CampusHeader(state,{})
            SwipeRoomRow(RoomMatch(room,profile,setOf(1,2,6)),data.slots,PersonalTags.defaultOrder,expanded,true,{expanded=it},{},{},{},{})
            Spacer(Modifier.weight(1f));WorkspaceNavigation(0,{})
        } } } }
        assertPaint("room_favorite_${room.id}",roles.favorite)
        assertEquals(roles.note,textColor("room_note_text_${room.id}"))
        shot("room")
        compose.runOnIdle { expanded=true }
        for(index in 0..3) assertPaint("room_action_icon_${room.id}_$index",roles.selectedRoomAction)
        assertPaint("room_action_line_${room.id}_top_thin",roles.roomActionDivider!!)
        assertPaint("room_action_line_${room.id}_bottom_thin",roles.roomActionDivider!!)
        shot("actions")
    }

    @Test fun inactiveRailIconsStayWarmPaperRatherThanPeach() {
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface(color=roles.roomActions) {
            RoomActionButtons(room.emptyProfile(),{},{},{},{},onRail=true)
        } } }
        for(index in 0..3) assertPaint("room_action_icon_${room.id}_$index",roles.onRoomActions)
    }

    @Test fun selectedChipsUsePeachAndDarkInkEvenAtNight() {
        compose.setContent { YOURoomTheme(appearance=appearance) { Surface { CompactFilterChip("靠窗",true,{}) } } }
        assertPaint("filter_chip_face_靠窗",roles.enabledOption)
        val text=compose.onNodeWithText("靠窗",true)
        val result=mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        assertEquals(roles.onEnabledOption,result.single().layoutInput.style.color)
    }

    @Test fun expectedRoomsUsePeachAndCanStillBeRemoved() {
        var selected by mutableStateOf(setOf(room.id))
        compose.setContent { YOURoomTheme(appearance=appearance) { ExpectedRoomDialog(state,selected,{selected=it},{}) } }
        assertPaint("expected_selected_${room.id}",roles.enabledOption)
        shot("expected")
        compose.onNodeWithTag("expected_selected_${room.id}").performClick()
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
    }

    @Test fun realStoreReopensTheNewThemeWithoutChangingOtherCampusOrSettings() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val path=File(context.cacheDir,"peach-plum-${System.nanoTime()}.preferences_pb")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);val reopenedScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val raw=PreferenceDataStoreFactory.create(scope=scope,produceFile={path})
            raw.edit { it[PeriodStripPreferenceKeys.numbers]=true;it[SharedFilterPreferences.favorites]=true }
            val value=AppearancePreferences(CampusTone.PEACH_PLUM,CampusTone.DUNHUANG,Campus.SHAHE)
            SettingsStore(raw).saveAppearance(value);scope.coroutineContext[Job]!!.cancelAndJoin()
            val reopened=SettingsStore(PreferenceDataStoreFactory.create(scope=reopenedScope,produceFile={path}))
            assertEquals(value,reopened.appearance.first());assertTrue(reopened.periodStrip.first().showNumbers)
            assertTrue(reopened.filters.first().favoritesOnly)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin();reopenedScope.coroutineContext[Job]!!.cancelAndJoin();path.delete() }
    }

    @Test fun selectingTheNewThemeUsesTheExistingWholePageWaveAndWraps() {
        var choice by mutableStateOf(AppearancePreferences(westTone=CampusTone.OLD_PORT));var writes=0
        compose.setContent { ThemeTransitionHost(Campus.XITUCHENG,choice,2,true,{c,t->choice=choice.withTone(c,t);writes++}) { request->
            Surface { SettingsScreen(state.copy(appearance=choice),{},{},{},onTone=request) }
        } }
        compose.onNodeWithTag("settings_theme_next").performClick()
        compose.waitUntil(5000) { writes==1 };compose.waitForIdle()
        assertEquals(CampusTone.PEACH_PLUM,choice.westTone);assertEquals(CampusTone.PINE,choice.shaheTone)
        compose.onNodeWithTag("settings_theme_name").assertTextEquals("蜜桃乌梅")
        assertThemeFrame(compose.onNodeWithTag("settings_theme_frame"), CampusTone.PEACH_PLUM, colors.surface, night)
        compose.onNodeWithTag("settings_theme_next").performClick()
        compose.waitUntil(5000) { writes==2 };compose.waitForIdle()
        compose.onNodeWithTag("settings_theme_name").assertTextEquals("勃艮第")
    }
}
