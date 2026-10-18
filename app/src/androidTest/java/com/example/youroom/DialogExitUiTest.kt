package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DialogExitUiTest {
    @get:Rule val compose = createComposeRule()
    private val data = DemoData.snapshot()
    private val room = data.classrooms.first()
    private var profile by mutableStateOf(room.emptyProfile())
    private var state by mutableStateOf(YOURoomState(initializing=false, demo=true, snapshot=data, today=data.queryDate))
    private var preferences by mutableStateOf(CampusPlanningPreferences())
    private var closed = 0
    private var writes = 0
    private fun closeIcon() {
        compose.onNodeWithTag("dialog_exit_close", true).assertExists()
        compose.onNodeWithTag("dialog_exit_save", true).assertDoesNotExist()
    }
    private fun saveIcon() {
        compose.onNodeWithTag("dialog_exit_save", true).assertExists()
        compose.onNodeWithTag("dialog_exit_close", true).assertDoesNotExist()
    }
    private fun manager() = compose.setContent { YOURoomTheme {
        TimeTemplateManager(Campus.XITUCHENG,preferences,{ preferences=it; writes++ },{ closed++ })
    } }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(200)
        val dark=instrumentation.targetContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        File(instrumentation.targetContext.getExternalFilesDir(null),"$name${if(dark) "-dark" else ""}.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun settingsPreferenceChangesAreAlreadySavedAndKeepTheCross() {
        compose.setContent { YOURoomTheme {
            PlanningPreferencesSheet(Campus.XITUCHENG,preferences,{ preferences=it; writes++ },{},{ closed++ })
        } }
        closeIcon()
        compose.onNodeWithTag("planning_default_flexible").performClick(); closeIcon()
        assertFalse(preferences.flexible)
        compose.onNodeWithTag("planning_default_direction").performClick()
        compose.onNodeWithTag("planning_default_FAVORITES").performScrollTo().performClick(); closeIcon()
        assertEquals(PlanningDirection.FAVORITES,preferences.direction)
        val count=writes
        compose.onNodeWithTag("settings_sheet_close").assertContentDescriptionEquals("关闭").performClick()
        assertEquals(count,writes); assertEquals(1,closed)
    }

    @Test fun wheelPreviewAloneDoesNotChangeTheSavedTemplateOrRequireSaving() {
        val original=TimeTemplate("morning","上午",listOf(StudyWindow(480,720)))
        preferences=preferences.copy(templates=listOf(original)); manager()
        compose.onNodeWithText("上午").performClick(); closeIcon()
        compose.onNodeWithTag("template_start_hour_wheel").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(9f) }
        closeIcon()
        compose.onNodeWithTag("settings_sheet_close").performClick()
        assertEquals(0,writes); assertEquals(original,preferences.templates.single())
    }

    @Test fun liveFilterTagsNotesAndExpectedRoomsAlwaysCloseWithACrossWithoutRepeatingWrites() {
        var panel by mutableIntStateOf(0)
        var selected by mutableStateOf(emptySet<String>())
        compose.setContent { YOURoomTheme {
            when(panel) {
                0 -> FilterDialog(state,{closed++},{state=state.copy(filters=it);writes++},{},{},{})
                1 -> RoomTagsDialog(RoomMatch(room,profile,emptySet()),state,{closed++},{profile=it;writes++},{},{},{})
                2 -> RoomNoteDialog(RoomMatch(room,profile,emptySet()),{closed++},{profile=it;writes++})
                3 -> ExpectedRoomDialog(state,selected,{selected=it;writes++},{closed++})
            }
        } }
        closeIcon()
        compose.onNodeWithTag("filter_outlets").performClick(); closeIcon()
        var count=writes
        compose.onNodeWithTag("close_filters").assertContentDescriptionEquals("关闭").performClick()
        assertEquals(count,writes)
        compose.runOnIdle { panel=1 }; closeIcon()
        compose.onNodeWithTag("filter_outlets").performClick(); closeIcon(); assertTrue(profile.outlets)
        count=writes; compose.onNodeWithTag("close_room_tags").performClick(); assertEquals(count,writes)
        compose.runOnIdle { panel=2 }; closeIcon()
        compose.onNodeWithTag("room_note").performTextInput("靠窗"); closeIcon(); assertEquals("靠窗",profile.note)
        count=writes; compose.onNodeWithTag("close_room_note").assertContentDescriptionEquals("关闭").performClick(); assertEquals(count,writes)
        compose.runOnIdle { panel=3 }; closeIcon()
        compose.onNodeWithTag("expected_pin").performClick(); closeIcon(); assertEquals(1,selected.size)
        count=writes; compose.onNodeWithTag("expected_close").assertContentDescriptionEquals("关闭").performClick(); assertEquals(count,writes)
        assertEquals(4,closed)
    }

    @Test fun newTemplateChangesFromCrossToCheckAndBackAndOnlyTheCheckSaves() {
        manager(); closeIcon()
        compose.onNodeWithTag("template_create").performClick(); closeIcon()
        compose.onNodeWithTag("template_name").assertTextContains("组合1").performTextReplacement("上午"); saveIcon()
        compose.onNodeWithTag("template_name").performTextReplacement("组合1"); closeIcon()
        compose.onNodeWithTag("template_add_period").performScrollTo().performClick(); saveIcon()
        compose.onNodeWithTag("template_clear").performClick(); closeIcon()
        compose.onNodeWithTag("settings_sheet_close").assertContentDescriptionEquals("关闭").performClick()
        compose.onNodeWithTag("template_create").assertExists(); assertEquals(0,writes)
        compose.onNodeWithTag("template_create").performClick()
        compose.onNodeWithTag("template_name").performTextReplacement("上午")
        compose.onNodeWithTag("template_save").assertContentDescriptionEquals("保存组合").performClick()
        compose.onNodeWithTag("template_message").performScrollTo().assertTextEquals("请添加1至3个合法时段"); saveIcon()
        assertEquals(0,writes)
        compose.onNodeWithTag("template_add_period").performScrollTo().performClick()
        shot("dialog-exit-save-1.21.1")
        compose.onNodeWithTag("template_save").performClick()
        assertEquals(1,writes); assertEquals("上午",preferences.templates.single().name)
        closeIcon()
    }

    @Test fun existingTemplateUsesPersistedValuesAsBaselineIncludingRemovedAndReaddedPeriods() {
        val original=TimeTemplate("morning","上午",listOf(StudyWindow(480,720)))
        preferences=preferences.copy(templates=listOf(original)); manager()
        compose.onNodeWithText("上午").performClick(); closeIcon()
        compose.onNodeWithTag("template_name").performTextReplacement("早间"); saveIcon()
        compose.onNodeWithTag("template_name").performTextReplacement("上午"); closeIcon()
        compose.onNodeWithTag("template_remove_period_0").performScrollTo().performClick(); saveIcon()
        compose.onNodeWithTag("template_add_period").performScrollTo().performClick(); closeIcon()
        shot("dialog-exit-close-1.21.1")
        compose.onNodeWithTag("settings_sheet_close").performClick()
        assertEquals(0,writes); assertEquals(original,preferences.templates.single())
        compose.onNodeWithText("上午").performClick()
        compose.onNodeWithTag("template_name").performTextReplacement("早间"); saveIcon()
        // The IME can still be resizing the native window after SetText; dispatch
        // the accessible click directly rather than tapping its previous bounds.
        compose.onNodeWithTag("template_save").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1,writes); assertEquals(original.copy(name="早间"),preferences.templates.single()) }
        closeIcon()
    }
}
