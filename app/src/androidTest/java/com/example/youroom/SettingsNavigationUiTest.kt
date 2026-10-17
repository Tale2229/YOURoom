package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsNavigationUiTest {
    @get:Rule val compose=createComposeRule()
    private var tab by mutableIntStateOf(2)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var dark by mutableStateOf(false)
    private var fontScale by mutableFloatStateOf(1f)
    private var profiles by mutableStateOf<Map<String,RoomProfile>>(emptyMap())
    private val opened=mutableListOf<String?>()
    private val cleared=mutableListOf<String>()
    private var logouts=0

    private fun render(navigationOnly: Boolean=false) {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                YouRoomTheme(campus=campus,darkTheme=dark) {
                    Surface {
                        Column {
                            if(!navigationOnly) {
                                val data=DemoData.snapshot(campus)
                                Box(Modifier.weight(1f)) {
                                    SettingsScreen(YouRoomState(initializing=false,campus=campus,snapshot=data,
                                        today=data.queryDate,profiles=profiles),{ opened+=it },{
                                        cleared+=it;profiles=profiles-it
                                    },{logouts++},{campus=it})
                                }
                            }
                            WorkspaceNavigation(tab,{tab=it})
                        }
                    }
                }
            }
        }
    }

    @Test fun navigationUsesIconsOnlyButKeepsNamedSelectedAccessibleTabs() {
        render(true)
        val tabs=listOf("tab_rooms" to "找教室","tab_planner" to "自习规划","tab_settings" to "设置")
        tabs.forEach { (tag,label) ->
            compose.onNodeWithText(label).assertDoesNotExist()
            compose.onNodeWithTag(tag).assertContentDescriptionEquals(label)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab))
                .assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
            tabs.filter { it.first!=tag }.forEach { compose.onNodeWithTag(it.first).assertIsNotSelected() }
        }
    }

    @Test fun selectionInvertsOnlyTheLocalBlockForBothCampusesAndAppearances() {
        render(true)
        val tags=listOf("tab_rooms","tab_planner","tab_settings")
        for(isDark in listOf(false,true)) for(target in Campus.entries) {
            compose.runOnIdle { campus=target;dark=isDark }
            for(selected in tags.indices) {
                compose.onNodeWithTag(tags[selected]).performClick()
                compose.waitForIdle()
                val colors=campusColorScheme(target,isDark)
                tags.forEachIndexed { index,tag ->
                    val face=compose.onNodeWithTag("${tag}_face",true).assertWidthIsEqualTo(if(index==1) 44.dp else 48.dp)
                        .assertHeightIsEqualTo(if(index==1) 36.dp else 40.dp).captureToImage().toPixelMap()
                    compose.onNodeWithTag("${tag}_icon",true).assertWidthIsEqualTo(if(index==1) 21.dp else 25.dp)
                        .assertHeightIsEqualTo(if(index==1) 21.dp else 25.dp)
                    val block=if(index==selected) colors.primary else colors.surface
                    val icon=if(index==selected) colors.surface else colors.primary
                    assertColor(block,face[8,8])
                    val iconPixels=(0 until face.width).sumOf { x ->
                        (0 until face.height).count { y -> close(icon,face[x,y]) }
                    }
                    assertTrue("icon really inverts",iconPixels>10)
                    val parent=compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                    assertColor(colors.surface,parent[4,parent.height/2])
                    assertColor(colors.surface,parent[parent.width/2,parent.height-2])
                }
            }
        }
    }

    @Test fun settingsKeepTheMainSurfaceBriefAndMoveDetailsIntoSecondarySheets() {
        render()
        compose.onNodeWithText("个人偏好").assertDoesNotExist()
        compose.onNodeWithTag("settings_theme_impression").assertIsDisplayed().assertWidthIsEqualTo(90.dp)
            .assertHeightIsEqualTo(90.dp)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_planning"))
        compose.onNodeWithTag("settings_planning").performClick()
        compose.onNodeWithText("更多默认偏好待开放").assertIsDisplayed()
        compose.onNodeWithTag("settings_sheet_close").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_about"))
        compose.onNodeWithTag("settings_about").performClick()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val version=context.packageManager.getPackageInfo(context.packageName,0).versionName
        compose.onNodeWithText("YouRoom $version · 非学校官方应用").assertIsDisplayed()
        assertEquals(Campus.XITUCHENG,campus)
        assertTrue(cleared.isEmpty());assertEquals(0,logouts)
    }

    @Test fun roomMarksOpenTheirOwnSheetAndLeaveTheSettingsListPositionUnchanged() {
        render()
        val before=compose.onNodeWithTag("settings_room_marks").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("settings_room_marks").performClick()
        compose.onNodeWithText("还没有个人标记").assertIsDisplayed()
        compose.onNodeWithTag("settings_sheet_close").performClick()
        assertEquals(before,compose.onNodeWithTag("settings_room_marks").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("还没有个人标记").assertDoesNotExist()
    }

    @Test fun roomMarksRemainEditableAndClearingStillRequiresConfirmation() {
        val room=DemoData.snapshot().classrooms.first()
        profiles=mapOf(room.id to room.emptyProfile().copy(favorite=true,note="靠窗"))
        render()
        compose.onNodeWithText(room.name).assertDoesNotExist()
        compose.onNodeWithTag("settings_room_marks").performClick()
        compose.onNodeWithText(room.name).assertIsDisplayed().performClick()
        assertEquals(listOf(room.id),opened)
        compose.onNodeWithText("靠窗").assertIsDisplayed()
        compose.onNodeWithContentDescription("清除 ${room.name} 的个人标记").performClick()
        compose.onNodeWithText("取消").performClick()
        assertTrue(cleared.isEmpty())
        compose.onNodeWithText(room.name).assertExists()
        compose.onNodeWithContentDescription("清除 ${room.name} 的个人标记").performClick()
        compose.onNodeWithText("清除",substring=false).performClick()
        assertEquals(listOf(room.id),cleared)
        compose.onNodeWithText("还没有个人标记").assertIsDisplayed()
    }

    @Test fun switchingCampusDoesNotMixOrEraseSavedMarks() {
        val west=DemoData.snapshot().classrooms.first().emptyProfile().copy(favorite=true)
        val shahe=DemoData.snapshot(Campus.SHAHE).classrooms.first().emptyProfile().copy(excluded=true)
        profiles=mapOf(west.roomId to west,shahe.roomId to shahe)
        render()
        compose.onNodeWithTag("settings_room_marks").performClick()
        compose.onNodeWithText(west.name).assertIsDisplayed()
        compose.onNodeWithText(shahe.name).assertDoesNotExist()
        compose.onNodeWithTag("settings_sheet_close").performClick()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("settings_room_marks").performClick()
        compose.onNodeWithText(shahe.name).assertIsDisplayed()
        compose.onNodeWithText(west.name).assertDoesNotExist()
        assertEquals(2,profiles.size);assertTrue(cleared.isEmpty())
    }

    @Test fun largeTextCanScrollToAllSettingsAndLogOutOnlyAfterConfirmation() {
        fontScale=1.5f
        render()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_default_campus"))
        compose.onNodeWithTag("settings_default_campus").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("退出登录"))
        compose.onNodeWithText("退出登录").performClick()
        assertEquals(0,logouts)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("退出登录").performClick()
        compose.onNodeWithText("确认").performClick()
        assertEquals(1,logouts)
    }

    @Test fun captureNativeSettingsForBothCampusPalettes() {
        render()
        for(target in Campus.entries) {
            compose.runOnIdle { campus=target }
            compose.waitForIdle()
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(180)
            File(instrumentation.targetContext.getExternalFilesDir(null),"settings-${target.dataKey}-1.16.png")
                .outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
        }
    }

    private fun close(a: Color,b: Color)=kotlin.math.abs(a.red-b.red)<.012f &&
        kotlin.math.abs(a.green-b.green)<.012f && kotlin.math.abs(a.blue-b.blue)<.012f
    private fun assertColor(expected: Color,actual: Color)=assertTrue("$expected != $actual",close(expected,actual))
}
