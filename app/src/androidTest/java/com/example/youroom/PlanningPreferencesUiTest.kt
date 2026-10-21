package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.data.local.SettingsStore
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.*

class PlanningPreferencesUiTest {
    @get:Rule val compose = createComposeRule()
    private val clock = Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), SchoolZone)
    private val data = DemoData.snapshot().copy(isDemo = false, queryDate = LocalDate.of(2026,10,17))
    private var preferences by mutableStateOf(PlanningPreferences())
    private var goal by mutableStateOf(PlanningDraft(1260,1320,expectedRooms=setOf("01:3-305")))
    private var pending by mutableStateOf(false)
    private var scale by mutableFloatStateOf(1f)
    private var generations = 0
    private fun render(settings: Boolean = false) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
            YOURoomTheme {
                val state = YOURoomState(initializing=false, loggedIn=true, snapshot=data, today=data.queryDate,
                    planningPreferences=preferences)
                val update: (Campus, CampusPlanningPreferences) -> Unit = { campus, value -> preferences=preferences.withCampus(campus,value) }
                if (settings) SettingsScreen(state,{},{},{},showHeader=false,onPlanningPreferences=update)
                else StudyPlanningScreen(state,goal,if (pending) mapOf(Campus.XITUCHENG to PlanningExecution(1,
                    PlanningInput(data,Campus.XITUCHENG,data.queryDate,emptyMap(),PlanningGoal()))) else emptyMap(),
                    { goal=it },{ generations++ },{},{},{}, clock=clock,showHeader=false,onPlanningPreferences=update)
            }
        }
    }
    private fun use(vararg templates: TimeTemplate) {
        preferences=preferences.withCampus(Campus.XITUCHENG,CampusPlanningPreferences(templates=templates.toList()))
    }
    private fun longAdd() { compose.onNodeWithTag("planner_add_period").performTouchInput { longClick() } }
    private fun shot(name: String) {
        val i=InstrumentationRegistry.getInstrumentation(); i.waitForIdleSync(); android.os.SystemClock.sleep(250)
        val night=i.targetContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        File(i.targetContext.getExternalFilesDir(null),"$name${if(night) "-dark" else ""}.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun preferencesExposeFiveDirectionsAndAnIndependentDefaultModeWithoutGenerating() {
        render(true)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_planning"))
        compose.onNodeWithTag("settings_planning").performClick()
        compose.onNodeWithTag("planning_default_direction").performClick()
        compose.onNodeWithTag("planning_default_FAVORITES").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("planning_default_flexible").performScrollTo().performClick().assertIsOff()
        assertEquals(PlanningDirection.FAVORITES,preferences.forCampus(Campus.XITUCHENG).direction)
        assertFalse(preferences.forCampus(Campus.XITUCHENG).flexible)
        assertEquals(CampusPlanningPreferences(),preferences.forCampus(Campus.SHAHE))
        shot("planning-preferences-1.21")
        assertEquals(0,generations)
    }
    @Test fun managerCreatesEditsAndConfirmsDeletionOfACombinationUsingTheTimeWheels() {
        render(true)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_planning"))
        compose.onNodeWithTag("settings_planning").performClick()
        compose.onNodeWithTag("planning_templates_entry").performClick()
        compose.onNodeWithTag("template_create").performClick()
        compose.onNodeWithTag("template_name").performTextReplacement("上午")
        compose.onNodeWithTag("template_add_period").performScrollTo().performClick()
        compose.onNodeWithTag("template_save").performClick()
        // Saving runs after the check-mark acknowledgement animation completes.
        compose.waitForIdle()
        val created=preferences.forCampus(Campus.XITUCHENG).templates.single()
        assertEquals(listOf(StudyWindow(480,720)),created.windows)
        compose.onNodeWithText("上午").performClick()
        compose.onNodeWithTag("template_name").performTextReplacement("上午自习")
        compose.onNodeWithTag("template_save").performClick()
        compose.waitForIdle()
        val edited=preferences.forCampus(Campus.XITUCHENG).templates.single()
        assertEquals(created.id,edited.id); assertEquals("上午自习",edited.name)
        shot("time-template-manager-1.21")
        compose.onNodeWithTag("template_delete_${created.id}").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(1,preferences.forCampus(Campus.XITUCHENG).templates.size)
        compose.onNodeWithTag("template_delete_${created.id}").performClick()
        compose.onNodeWithTag("template_delete_confirm").performClick()
        assertTrue(preferences.forCampus(Campus.XITUCHENG).templates.isEmpty()); assertEquals(0,generations)
    }
    @Test fun longPressDoesNotAddAndStrictOrCancelledImportPreservesTheDraftUntilConfirmed() {
        use(TimeTemplate("day","全天",listOf(StudyWindow(480,720),StudyWindow(900,1020),StudyWindow(1200,1320))))
        render()
        compose.onNodeWithTag("planner_add_period").performClick(); compose.waitForIdle()
        val before=goal
        longAdd()
        assertEquals(before,goal)
        compose.onNodeWithTag("template_import_day").performClick()
        compose.onNodeWithTag("template_import_error").assertTextContains("组合未导入", substring=true)
        assertEquals(before,goal)
        compose.onNodeWithTag("template_skip_invalid").performClick()
        compose.onNodeWithTag("template_import_day").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(before,goal)
        shot("time-template-import-1.21")
        compose.onNodeWithTag("template_import_day").performClick()
        compose.onNodeWithTag("template_replace_confirm").performClick()
        assertEquals(listOf(StudyWindow(900,1020),StudyWindow(1200,1320)),goal.savedWindows)
        assertEquals(before.start,goal.start); assertEquals(before.end,goal.end)
        assertEquals(before.expectedRooms,goal.expectedRooms); assertEquals(0,generations)
        compose.onNodeWithTag("template_import_list").assertDoesNotExist()
    }
    @Test fun allInvalidEvenWhenSkippingNeverClearsCurrentWindows() {
        use(TimeTemplate("past","上午",listOf(StudyWindow(480,720))))
        goal=goal.copy(savedWindows=listOf(StudyWindow(900,960)))
        render(); longAdd()
        compose.onNodeWithTag("template_skip_invalid").performClick()
        compose.onNodeWithTag("template_import_past").performClick()
        compose.onNodeWithTag("template_import_error").assertTextEquals("没有可导入的合法时段")
        assertEquals(listOf(StudyWindow(900,960)),goal.savedWindows)
        assertEquals(0,generations)
    }
    @Test fun longPressStillImportsWhenThreePeriodsExistButNotWhileAPlanIsRunning() {
        use(TimeTemplate("future","晚上",listOf(StudyWindow(1200,1320))))
        goal=goal.copy(savedWindows=listOf(StudyWindow(900,960),StudyWindow(960,1020),StudyWindow(1200,1260)))
        render(); longAdd()
        compose.onNodeWithTag("template_import_future").performClick()
        compose.onNodeWithTag("template_replace_confirm").performClick()
        assertEquals(listOf(StudyWindow(1200,1320)),goal.savedWindows)
        compose.runOnIdle { pending=true }
        compose.onNodeWithTag("planner_add_period").assertIsNotEnabled().performTouchInput { longClick() }
        compose.onNodeWithTag("template_import_list").assertDoesNotExist()
        assertEquals(0,generations)
    }
    @Test fun realDataStoreRetainsTheTemplatesModesAndDirectionsAfterReopening() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val path=File(context.cacheDir,"planning-test-${System.nanoTime()}.preferences_pb")
        val first=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val second=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val value=PlanningPreferences().withCampus(Campus.XITUCHENG,CampusPlanningPreferences(PlanningDirection.FAVORITES,false,
            listOf(TimeTemplate("morning","上午",listOf(StudyWindow(480,720)))))).withCampus(Campus.SHAHE,CampusPlanningPreferences())
        try {
            val store=SettingsStore(PreferenceDataStoreFactory.create(scope=first,produceFile={path}))
            store.savePlanningPreferences(value)
            first.coroutineContext[Job]!!.cancelAndJoin()
            val reopened=SettingsStore(PreferenceDataStoreFactory.create(scope=second,produceFile={path}))
            assertEquals(value,reopened.planningPreferences.first())
        } finally {
            first.coroutineContext[Job]!!.cancelAndJoin(); second.coroutineContext[Job]!!.cancelAndJoin(); path.delete()
        }
    }

    @Test fun largeTextKeepsTheImportOptionAndTemplateEditorActionsReachable() {
        scale=1.5f
        use(TimeTemplate("future","晚上",listOf(StudyWindow(1200,1320))))
        render(); longAdd()
        compose.onNodeWithTag("template_skip_invalid").assertIsDisplayed()
        compose.onNodeWithTag("settings_sheet_close").assertIsDisplayed()
        shot("time-template-import-large-1.21")
        compose.onNodeWithTag("template_import_manage").performClick()
        compose.onNodeWithTag("template_create").assertIsDisplayed().performClick()
        compose.onNodeWithTag("template_name").performTextReplacement("自习")
        compose.onNodeWithTag("template_add_period").performScrollTo().performClick()
        compose.onNodeWithTag("template_save").assertIsDisplayed()
        shot("time-template-editor-large-1.21")
        compose.onNodeWithTag("template_save").performClick()
        compose.waitForIdle()
        assertEquals(2,preferences.forCampus(Campus.XITUCHENG).templates.size)
        assertEquals(0,generations)
    }
}
