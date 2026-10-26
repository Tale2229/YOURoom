package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class TimeTemplateLayoutUiTest {
    @get:Rule val compose=createComposeRule()
    private val data=DemoData.snapshot()
    private var preferences by mutableStateOf(CampusPlanningPreferences())
    private var goal by mutableStateOf(PlanningDraft(savedWindows=listOf(StudyWindow(480,540),StudyWindow(840,900),StudyWindow(1200,1320))))
    private var scale by mutableFloatStateOf(1f)
    private var saved: TimeTemplate?=null
    private var generations=0
    private fun editor(current: List<StudyWindow> = emptyList())=compose.setContent {
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
            YOURoomTheme { TimeTemplateEditor(Campus.XITUCHENG,null,current,"组合1",{ saved=it },{}) }
        }
    }
    private fun planner()=compose.setContent { YOURoomTheme {
        StudyPlanningScreen(YOURoomState(initializing=false,demo=true,snapshot=data,today=data.queryDate,
            planningPreferences=PlanningPreferences().withCampus(Campus.XITUCHENG,preferences)),goal,emptyMap(),
            { goal=it },{ generations++ },{},{},{},showHeader=false,onPlanningPreferences={ _,value -> preferences=value })
    } }
    private fun select(tag: String,index: Int) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(index.toFloat()) }
        compose.waitForIdle()
    }
    private fun add() { compose.onNodeWithTag("template_add_period").performScrollTo().performClick();compose.waitForIdle() }
    private fun shot(name: String) {
        compose.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync();android.os.SystemClock.sleep(250)
        val night=i.targetContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        File(i.targetContext.getExternalFilesDir(null),"$name${if(night) "-dark" else ""}.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    private fun titleSize(): Float {
        val results=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("settings_sheet_title",true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.style.fontSize.value
    }
    private fun visibleDigitTop(tag: String): Float {
        val node=compose.onNodeWithTag("${tag}_face",true)
        val pixels=node.captureToImage().toPixelMap()
        val background=pixels[0,0]
        for(y in 0 until pixels.height) {
            // The full face can share its empty cap with the title; inspect the
            // central numeric column, not the adjacent title's edge.
            val ink=(pixels.width/6 until pixels.width*2/3).count { x ->
                val p=pixels[x,y]
                p.alpha>.08f && abs(p.red-background.red)+abs(p.green-background.green)+abs(p.blue-background.blue)>.12f
            }
            if(ink>=2) return node.fetchSemanticsNode().boundsInRoot.top+y
        }
        error("No visible digits in $tag")
    }

    @Test fun managerNewStartsEmptyWithTheCountBasedNameAndAFullWheel() {
        preferences=preferences.copy(templates=listOf(TimeTemplate("one","上午",listOf(StudyWindow(480,540))),
            TimeTemplate("two","下午",listOf(StudyWindow(840,900)))))
        planner()
        compose.onNodeWithTag("planner_add_period").performTouchInput { longClick() }
        assertEquals(16f,titleSize(),.01f)
        compose.onNodeWithTag("template_import_manage").performClick();assertEquals(16f,titleSize(),.01f)
        val create=compose.onNodeWithTag("template_create").assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        val close=compose.onNodeWithTag("settings_sheet_close").assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        assertEquals("Create and close share one footer row",create.center.y,close.center.y,1f)
        assertTrue(create.right<close.left)
        shot("template-manager-footer-1.22.4")
        compose.onNodeWithTag("template_create").performClick();assertEquals(16f,titleSize(),.01f)
        compose.onNodeWithTag("template_name").assertTextContains("组合3")
        compose.onNodeWithTag("template_period_0").assertDoesNotExist()
        compose.onNodeWithTag("template_start_hour_wheel").assertHeightIsEqualTo(216.dp)
        val name=compose.onNodeWithTag("template_name").fetchSemanticsNode().boundsInRoot
        val viewport=compose.onNodeWithTag("template_editor_content").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.width,name.width,1f)
        shot("template-name-refinement")
        assertTrue("Name height ${name.height}, density ${compose.density.density}",name.height<with(compose.density) { 44.dp.toPx() })
        val label=compose.onNodeWithTag("template_name_label",true).fetchSemanticsNode().boundsInRoot
        assertTrue("The compact field must not clip its floating label",label.top>=viewport.top)
        val title=compose.onNodeWithTag("settings_sheet_title",true).fetchSemanticsNode().boundsInRoot
        assertTrue(name.top-title.bottom>=with(compose.density) { 28.dp.toPx() })
        for(part in listOf("start","end")) {
            val templateGap=visibleDigitTop("template_${part}_hour_wheel")-
                compose.onNodeWithTag("template_${part}_title",true).fetchSemanticsNode().boundsInRoot.bottom
            val plannerGap=visibleDigitTop("planner_${part}_hour_wheel")-
                compose.onNodeWithTag("planner_${part}_title",true).fetchSemanticsNode().boundsInRoot.bottom
            assertEquals("Match painted digits, not invisible viewport padding: $part",plannerGap,templateGap,
                with(compose.density) { 2.dp.toPx() })
        }
        val titleTop=compose.onNodeWithTag("template_start_title",true).fetchSemanticsNode().boundsInRoot.top
        assertEquals(with(compose.density) { 16.dp.toPx() },titleTop-name.bottom,1f)
        val controls=compose.onNodeWithTag("template_period_controls").fetchSemanticsNode().boundsInRoot
        val wheel=compose.onNodeWithTag("template_start_hour_wheel").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { (-8).dp.toPx() },controls.top-wheel.bottom,1f)
        shot("template-empty-full-wheels-1.22.3")
        assertEquals(3,goal.savedWindows.size);assertEquals(2,preferences.templates.size);assertEquals(0,generations)
    }

    @Test fun allThreeTintedPeriodsShareOneLineWithTopRightCrossesCountAndPlus() {
        editor(goal.savedWindows)
        val rows=(0..2).map { index ->
            val face=compose.onNodeWithTag("template_period_face_$index",true).performScrollTo().fetchSemanticsNode().boundsInRoot
            val text=compose.onNodeWithTag("template_period_$index").fetchSemanticsNode().boundsInRoot
            val cross=compose.onNodeWithTag("template_remove_period_$index").fetchSemanticsNode().boundsInRoot
            assertTrue(cross.top<face.top);assertTrue(cross.center.x>face.right-with(compose.density) { 10.dp.toPx() })
            assertTrue(cross.right>face.right);assertTrue(text.right<=face.right)
            face
        }
        rows.forEach { assertEquals(rows.first().top,it.top,1f);assertEquals(rows.first().width,it.width,1f) }
        rows.zipWithNext().forEach { (a,b) -> assertTrue(a.right<b.left) }
        val count=compose.onNodeWithTag("template_count").assertTextEquals("3/3").fetchSemanticsNode().boundsInRoot
        val add=compose.onNodeWithTag("template_add_period").fetchSemanticsNode().boundsInRoot
        assertTrue(rows.last().right<count.left);assertTrue(count.right<=add.left)
        assertEquals(rows.first().center.y,count.center.y,1f);assertEquals(count.center.y,add.center.y,1f)
        compose.onNodeWithTag("template_save").assertIsDisplayed()
        shot("template-three-periods-1.22.1")
        compose.onNodeWithTag("template_remove_period_1").performClick()
        compose.onNodeWithTag("template_period_1").assertTextEquals("20:00–22:00")
        compose.onNodeWithTag("template_save").performClick()
        compose.waitForIdle();assertEquals(listOf(StudyWindow(480,540),StudyWindow(1200,1320)),saved!!.windows)
    }

    @Test fun saveMenuPrefillsAllPeriodsAndSavingDoesNotEditThePlanOrGenerate() {
        planner();val before=goal
        compose.onNodeWithTag("planner_target_range_1",true).performClick()
        compose.onNodeWithTag("planner_period_actions_face_1",true).assertHeightIsEqualTo(37.dp).assertWidthIsEqualTo(132.dp)
        compose.onNodeWithTag("planner_save_periods_1").assertContentDescriptionEquals("保存时段组合").performClick()
        compose.onNodeWithTag("template_name").assertTextContains("组合1")
        compose.onNodeWithTag("template_period_0").performScrollTo().assertTextEquals("08:00–09:00")
        compose.onNodeWithTag("template_period_1").assertTextEquals("14:00–15:00")
        compose.onNodeWithTag("template_period_2").assertTextEquals("20:00–22:00")
        compose.onNodeWithTag("template_save").performClick()
        compose.onNodeWithTag("template_name").assertDoesNotExist()
        assertEquals(before,goal);assertEquals(before.savedWindows,preferences.templates.single().windows)
        assertEquals("组合1",preferences.templates.single().name);assertEquals(0,generations)
    }

    @Test fun invalidAndTooShortTimesUseTheSameSlidingFeedbackAndDurationShortcuts() {
        editor()
        select("template_end_hour_wheel",7);add()
        compose.onNodeWithTag("template_message").assertIsDisplayed().assertTextEquals("结束时间必须晚于开始时间\n暂不支持跨日规划")
        for(hours in listOf(1,2,4)) compose.onNodeWithTag("template_duration_${hours}h").assertIsDisplayed()
        compose.onNodeWithTag("template_duration_1h").performClick()
        compose.onNodeWithTag("template_message").assertDoesNotExist()
        val end=compose.onNodeWithTag("template_end_hour_wheel").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals(9f,end,.01f)
        select("template_end_hour_wheel",8);select("template_end_minute_wheel",15);add()
        compose.onNodeWithTag("template_message").performScrollTo().assertTextEquals("自习时段不能少于30分钟")
        shot("template-duration-feedback-1.22")
        compose.onNodeWithTag("template_duration_2h").performClick();add()
        compose.onNodeWithTag("template_period_0").performScrollTo().assertTextEquals("08:00–10:00")
        select("template_start_hour_wheel",8);select("template_end_hour_wheel",9);add()
        compose.onNodeWithTag("template_message").performScrollTo().assertTextEquals("时段不能重叠，请调整当前时间")
        assertNull(saved)
    }

    @Test fun aFourthPeriodShowsTheSharedLimitNoticeWithoutAddingOrOverwriting() {
        editor(goal.savedWindows)
        add()
        compose.onNodeWithTag("template_message").performScrollTo().assertTextEquals("最多添加三个时段")
        compose.onNodeWithTag("template_period_2").assertExists()
        compose.onNodeWithTag("template_period_3").assertDoesNotExist()
        add()
        compose.onAllNodesWithTag("template_message").assertCountEquals(1)
        compose.onNodeWithTag("template_save").performClick()
        compose.waitForIdle();assertEquals(goal.savedWindows,saved!!.windows)
    }

    @Test fun largeTextKeepsThreePeriodsTogetherAndTheFixedSaveReachable() {
        scale=1.5f;editor(goal.savedWindows)
        val bounds=(0..2).map { compose.onNodeWithTag("template_period_face_$it",true).performScrollTo().fetchSemanticsNode().boundsInRoot }
        bounds.forEach { assertEquals(bounds[0].top,it.top,1f) }
        compose.onNodeWithTag("template_save").assertIsDisplayed()
        shot("template-large-three-periods-1.22.1")
        compose.onNodeWithTag("template_save").performClick();compose.waitForIdle();assertEquals(3,saved!!.windows.size)
    }

    @Test fun managementAndImportUseIdenticalSpacingAndThreeSingleLineTimes() {
        val template=TimeTemplate("three","全天",goal.savedWindows)
        preferences=preferences.copy(templates=listOf(template));planner()
        compose.onNodeWithTag("planner_add_period").performTouchInput { longClick() }
        fun gaps(): Pair<Float,Float> {
            val name=compose.onNodeWithTag("template_summary_name_three",true).fetchSemanticsNode().boundsInRoot
            val times=compose.onNodeWithTag("template_summary_times_three",true).fetchSemanticsNode().boundsInRoot
            val rule=compose.onNodeWithTag("template_summary_divider_three",true).fetchSemanticsNode().boundsInRoot
            val periods=(0..2).map { compose.onNodeWithTag("template_summary_period_three_$it",true).fetchSemanticsNode().boundsInRoot }
            periods.forEach { assertEquals(periods[0].top,it.top,1f);assertTrue(it.bottom<=times.bottom+1f) }
            periods.zipWithNext().forEach { (a,b) -> assertTrue(a.right<b.left) }
            return (times.top-name.bottom) to (rule.top-times.bottom)
        }
        val imported=gaps()
        shot("template-import-spacing-1.22.1")
        compose.onNodeWithTag("template_import_manage").performClick()
        val managed=gaps()
        assertEquals(imported.first,managed.first,1f);assertEquals(imported.second,managed.second,1f)
        assertEquals(with(compose.density) { 9.dp.toPx() },managed.first,1f)
        assertEquals(with(compose.density) { 4.dp.toPx() },managed.second,1f)
        shot("template-manager-spacing-1.22.1")
    }
}
