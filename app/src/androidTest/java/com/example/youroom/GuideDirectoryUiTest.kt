package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class GuideDirectoryUiTest {
    @get:Rule val compose=createComposeRule()
    private var tone by mutableStateOf(CampusTone.MATISSE)
    private var dark by mutableStateOf(false)
    private var large by mutableStateOf(false)
    private var entered=-1
    private var closed=0
    private var skipped=0

    @Composable private fun Content(currentStep:Int=16) {
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides if(large) Density(density.density*1.25f,1.6f) else density) {
            YOURoomTheme(campus=Campus.XITUCHENG,appearance=AppearancePreferences(westTone=tone,darkMode=dark)) {
                GuideDirectory(currentStep,Campus.XITUCHENG,{closed++},{skipped++},{entered=it})
            }
        }
    }

    @Test fun approvedLayoutAndChapterEntriesWorkInEveryThemeAndMode() {
        compose.setContent {Content()}
        for(value in CampusTone.entries) for(night in listOf(false,true)) {
            compose.runOnIdle {tone=value;dark=night}
            compose.onNodeWithTag("guide_directory_eyebrow").performScrollTo().assertTextEquals("YOURoom / CONTENTS")
            compose.onAllNodesWithText("4 个章节，43 个小步骤").assertCountEquals(0)
            compose.onAllNodesWithText("按章节查看，也可以从头开始").assertCountEquals(0)
            compose.onNodeWithTag("guide_menu_skip").assertDoesNotExist()
            compose.onNodeWithTag("guide_menu_close").assertContentDescriptionEquals("跳过指引")
            compose.onNodeWithTag("guide_continue").assertIsDisplayed().assertTextContains("继续第17步")
            for(chapter in GuideChapters.indices) {
                compose.onNodeWithTag("guide_chapter_$chapter").performScrollTo().performClick()
                assertEquals(-1,entered)
                compose.onNodeWithTag("guide_chapter_back").assertContentDescriptionEquals("返回目录").assertIsDisplayed()
                compose.onNodeWithTag("guide_menu_close").assertDoesNotExist()
                compose.onAllNodesWithText("可以从任意一步开始查看").assertCountEquals(0)
                compose.onNodeWithTag("guide_step_${guideChapterSteps(chapter).first().ordinal}").assertExists()
                compose.onNodeWithTag("guide_chapter_back").performClick()
            }
            if(value in listOf(CampusTone.MATISSE,CampusTone.MORNING_BELL,CampusTone.MONDRIAN)) shot("${value.id}-$night")
        }
    }

    @Test fun chapterSelectionBackContinueAndSkipDoNotEnterAnotherLesson() {
        compose.setContent {Content()}
        compose.onNodeWithTag("guide_chapter_2").performScrollTo().performClick()
        compose.onNodeWithTag("guide_step_16").assertIsSelected()
        compose.onNodeWithTag("guide_step_18").performScrollTo().performClick().assertIsSelected()
        assertEquals(-1,entered)
        compose.onNodeWithTag("guide_chapter_back").performClick()
        compose.onNodeWithTag("guide_continue").performClick()
        assertEquals(1,closed)
        assertEquals(-1,entered)
        compose.onNodeWithTag("guide_menu_close").performClick()
        assertEquals(1,skipped)
        assertEquals(1,closed)
        compose.onNodeWithTag("guide_chapter_3").performScrollTo().performClick()
        compose.onNodeWithTag("guide_step_list").performScrollToNode(hasTestTag("guide_step_48"))
        compose.onNodeWithTag("guide_step_48").performScrollTo().performClick()
        compose.onNodeWithTag("guide_enter_step").assertTextContains("进入第49步").performClick()
        assertEquals(48,entered)
    }

    @Test fun largeTextKeepsFooterBackAndLastChapterStepReachable() {
        large=true
        compose.setContent {Content(0)}
        compose.onNodeWithTag("guide_directory_eyebrow").assertIsDisplayed()
        compose.onNodeWithTag("guide_continue").assertIsDisplayed().assertTextContains("开始使用指引")
        compose.onNodeWithTag("guide_menu_close").assertIsDisplayed().assertContentDescriptionEquals("跳过指引")
        shot("large-cover")
        compose.onNodeWithTag("guide_chapter_3").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("guide_step_list").performScrollToNode(hasTestTag("guide_step_48"))
        compose.onNodeWithTag("guide_step_48").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("guide_enter_step").assertIsDisplayed()
        compose.onNodeWithTag("guide_chapter_back").assertIsDisplayed()
        shot("large-chapter")
    }

    @Test fun inspectedChapterAndSelectedStepSurviveSavedStateRestoration() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {Content()}
        compose.onNodeWithTag("guide_chapter_3").performScrollTo().performClick()
        compose.onNodeWithTag("guide_step_list").performScrollToNode(hasTestTag("guide_step_47"))
        compose.onNodeWithTag("guide_step_47").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("guide_enter_step").assertTextContains("进入第48步")
        compose.onNodeWithTag("guide_chapter_back").assertIsDisplayed()
        assertEquals(-1,entered)
        compose.onNodeWithTag("guide_enter_step").performClick()
        assertEquals(47,entered)
    }

    private fun shot(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null),"directory-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
