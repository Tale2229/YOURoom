package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AboutUiTest {
    @Test fun pigShakesBothWaysSquintsAndResetsWithoutChangingData() {
        render();open()
        val pig=compose.onNodeWithTag("settings_about_pig").performScrollTo().assertHasClickAction()
        fun eyes(value:String)=pig.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,value))
        eyes("豆豆眼")
        val original=pig.captureToImage().toPixelMap()
        val origin=pig.fetchSemanticsNode().positionInRoot.x
        compose.mainClock.autoAdvance=false
        pig.performClick()
        compose.mainClock.advanceTimeBy(64)
        eyes("眯眼")
        val squint=pig.captureToImage().toPixelMap()
        var changed=0
        for(y in 0 until original.height) for(x in 0 until original.width)
            if(original[x,y]!=squint[x,y]) changed++
        assertTrue("Eyes must visibly change",changed>0)
        shot("about-pig-squint")
        val xs=mutableListOf<Float>()
        repeat(12) {
            compose.mainClock.advanceTimeBy(16)
            xs+=pig.fetchSemanticsNode().positionInRoot.x
        }
        assertTrue(xs.min()<origin-1f)
        assertTrue(xs.max()>origin+1f)
        // A tap during the nudge must play once after the first one, not restart it.
        pig.performClick()
        compose.mainClock.advanceTimeBy(150)
        eyes("眯眼")
        compose.mainClock.advanceTimeBy(240)
        eyes("豆豆眼")
        assertEquals(origin,pig.fetchSemanticsNode().positionInRoot.x,.5f)
        pig.performClick()
        compose.mainClock.advanceTimeBy(64)
        eyes("眯眼")
        compose.mainClock.advanceTimeBy(400)
        eyes("豆豆眼")
        compose.mainClock.autoAdvance=true
        compose.onNodeWithTag("settings_sheet_close").performClick()
        open()
        compose.onNodeWithTag("settings_about_pig").performScrollTo()
        eyes("豆豆眼")
        assertEquals(0,mutations)
        assertTrue(opened.isEmpty())
    }
    @Test fun rapidClicksPlayEveryNudgeAndClosingDropsOnlyTheOldSheetQueue() {
        render();open()
        val pig=compose.onNodeWithTag("settings_about_pig").performScrollTo()
        compose.mainClock.autoAdvance=false
        val eyes=SemanticsProperties.StateDescription
        repeat(8) { pig.performClick() }
        val positions=mutableListOf<Float>()
        var previous="豆豆眼"
        var starts=0
        repeat(170) {
            compose.mainClock.advanceTimeByFrame()
            val node=pig.fetchSemanticsNode()
            val current=node.config[eyes]
            if(current=="眯眼" && previous=="豆豆眼") starts++
            previous=current
            positions+=node.positionInRoot.x
        }
        assertEquals("No tap may be dropped or duplicated",8,starts)
        pig.assert(SemanticsMatcher.expectValue(eyes,"豆豆眼"))
        assertTrue(positions.max()-positions.min()>4f)
        repeat(12) { pig.performClick() }
        compose.mainClock.advanceTimeBy(64)
        pig.assert(SemanticsMatcher.expectValue(eyes,"眯眼"))
        compose.onNodeWithTag("settings_sheet_close").performClick()
        compose.mainClock.autoAdvance=true
        open()
        compose.onNodeWithTag("settings_about_pig").performScrollTo()
            .assert(SemanticsMatcher.expectValue(eyes,"豆豆眼"))
        compose.mainClock.advanceTimeBy(1500)
        pig.assert(SemanticsMatcher.expectValue(eyes,"豆豆眼"))
        assertEquals(0,mutations)
        assertTrue(opened.isEmpty())
    }

    @Test fun compactPageFitsOneScreenAndUsesOneBodyColorWithInsetRules() {
        render()
        for(scale in listOf(1f,1.14f)) {
            compose.runOnIdle {viewportScale=scale}
            open()
            val scroll=compose.onNodeWithTag("settings_about_content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
            assertEquals("Normal text must fit without scrolling at viewport scale $scale",0f,scroll.maxValue(),.5f)
            compose.onNodeWithText("YOURoom / ABOUT").assertIsDisplayed()
            compose.onNodeWithTag("settings_about_pig").assertIsDisplayed()
            compose.onNodeWithText(AboutCopy.local.last()).assertIsDisplayed()
            val content=compose.onNodeWithTag("settings_about_content").fetchSemanticsNode().boundsInRoot
            val line=compose.onNodeWithTag("settings_about_data_local_divider").fetchSemanticsNode().boundsInRoot
            assertTrue(line.left>content.left+10f && line.right<content.right-10f)
            val pixels=compose.onNodeWithTag("settings_about_content").captureToImage().toPixelMap()
            val data=compose.onNodeWithTag("settings_about_data").fetchSemanticsNode().boundsInRoot
            val local=compose.onNodeWithTag("settings_about_local").fetchSemanticsNode().boundsInRoot
            assertEquals(pixels[3,(data.center.y-content.top).toInt()],pixels[3,(local.center.y-content.top).toInt()])
            shot("about-compact-one-screen-$scale")
            compose.onNodeWithTag("settings_sheet_close").performClick()
        }
    }
    @get:Rule val compose = createComposeRule()
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var fontScale by mutableFloatStateOf(1f)
    private var viewportScale by mutableFloatStateOf(1f)
    private var tone by mutableStateOf<CampusTone?>(null)
    private var dark by mutableStateOf(false)
    private var refuseBrowser = false
    private var mutations = 0
    private val opened = mutableListOf<String>()
    private val browser = object : UriHandler {
        override fun openUri(uri: String) {
            opened += uri
            if (refuseBrowser) throw IllegalArgumentException("No test browser")
        }
    }
    private fun render() = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalUriHandler provides browser, LocalDensity provides Density(density.density*viewportScale, fontScale)) {
            val appearance=AppearancePreferences(darkMode=dark).let { base -> tone?.let {base.withTone(campus,it)} ?: base }
            YOURoomTheme(campus = campus, appearance=appearance) { Surface {
                val snapshot = DemoData.snapshot(campus)
                val room = snapshot.classrooms.first().emptyProfile().copy(favorite = true, note = "靠窗")
                SettingsScreen(YOURoomState(initializing = false, campus = campus, appearance=appearance, snapshot = snapshot,
                    profiles = mapOf(room.roomId to room)), { mutations++ }, { mutations++ }, { mutations++ },
                    onPeriodStrip = { mutations++ }, onTone = { _, _ -> mutations++ }, onDefaultCampus = { mutations++ })
            } }
        }
    }
    private fun open() {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_about"))
        compose.onNodeWithTag("settings_about").performClick()
    }
    private fun shot(name: String) {
        val i = InstrumentationRegistry.getInstrumentation()
        i.waitForIdleSync()
        // Compose can be idle while the platform Dialog is still fading in.
        android.os.SystemClock.sleep(250)
        val night = i.targetContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        File(i.targetContext.getExternalFilesDir(null), "$name${if (night) "-dark" else ""}.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun aboutShowsTheAuthorActualVersionAndExplicitBrowserOnlyProjectAndFeedbackLinks() {
        render(); open()
        assertTrue(opened.isEmpty())
        compose.onNodeWithTag("settings_about_author_name").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("非学校官方应用").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings_sheet_campus").assertDoesNotExist()
        compose.onNodeWithTag("settings_sheet_title").assertDoesNotExist()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        compose.onNodeWithTag("settings_about_version").assertTextEquals(version)
        shot("about-compact-1.22.4")
        compose.onNodeWithTag("settings_about_project").assertHeightIsAtLeast(44.dp).performClick()
        compose.onNodeWithTag("settings_about_feedback").assertHeightIsAtLeast(44.dp).performClick()
        assertEquals(listOf("https://github.com/Tale2229/YOURoom", "https://github.com/Tale2229/YOURoom/issues"), opened)
        compose.onNodeWithText("反馈时请勿附带账号、密码或 Token").assertDoesNotExist()
        compose.onNodeWithText("GitHub").assertIsDisplayed()
        assertEquals(0, mutations)
    }

    @Test fun unavailableBrowserDoesNotCrashDismissOrRetryAndDoesNotChangeUserData() {
        refuseBrowser = true
        render(); open()
        compose.onNodeWithTag("settings_about_project").performClick()
        compose.onNodeWithTag("settings_about_version").assertIsDisplayed()
        assertEquals(listOf(AboutLinks.project), opened)
        compose.onNodeWithTag("settings_sheet_close").performClick()
        open()
        assertEquals(1, opened.size)
        compose.onNodeWithTag("settings_about_version").assertIsDisplayed()
        compose.onNodeWithTag("settings_sheet_close").performClick()
        assertEquals(0, mutations)
    }

    @Test fun largeTextCanReachAccurateBackupAndLogoutInformationWithTheCloseButtonFixed() {
        fontScale = 1.5f
        render(); open()
        val close = compose.onNodeWithTag("settings_sheet_close").fetchSemanticsNode().boundsInRoot
        val paragraphs = listOf(
            "空闲信息来自学校教务，并非实时空座",
            "教室开放情况请以现场为准",
            "教室标记与设置保存在设备上，可能参与系统备份",
            "退出登录清除登录状态，保留已记住的账号密码、教室标记与设置",
        )
        for (paragraph in paragraphs) {
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(paragraph, substring=true).performScrollTo().assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            val result = layout.single()
            assertFalse("$paragraph: width=${result.didOverflowWidth}, height=${result.didOverflowHeight}, size=${result.size}, lines=${result.lineCount}",
                result.hasVisualOverflow)
        }
        assertEquals(close, compose.onNodeWithTag("settings_sheet_close").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("关闭", true).assertIsDisplayed()
        val pixels = compose.onNodeWithTag("settings_sheet_close").captureToImage().toPixelMap()
        val background = pixels[0, 0]
        val ink = (0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y ->
            val color = pixels[x, y]
            kotlin.math.abs(color.red - background.red) + kotlin.math.abs(color.green - background.green) +
                kotlin.math.abs(color.blue - background.blue) > .15f
        } }
        assertTrue("The fixed close icon must actually be painted", ink > 24)
        compose.onNodeWithTag("settings_about_author_name").performScrollTo().assertIsDisplayed()
        shot("about-large-text-1.22.4")
        compose.onNodeWithTag("settings_sheet_close").performClick()
        assertEquals(0, mutations); assertTrue(opened.isEmpty())
    }

    @Test fun closeIconAndSystemBackBothDismissWithoutMovingTheSettingsList() {
        render()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_about"))
        val before = compose.onNodeWithTag("settings_about").fetchSemanticsNode().boundsInRoot
        open()
        compose.onNodeWithTag("settings_sheet_close").performClick()
        assertEquals(before, compose.onNodeWithTag("settings_about").fetchSemanticsNode().boundsInRoot)
        open()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val keyTime = android.os.SystemClock.uptimeMillis()
        // Inject into the focused Dialog window, not Espresso's unfocused Activity root.
        for (action in listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
            instrumentation.uiAutomation.injectInputEvent(android.view.KeyEvent(keyTime,
                android.os.SystemClock.uptimeMillis(), action, android.view.KeyEvent.KEYCODE_BACK, 0), true)
        }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("settings_about_content").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("settings_about_content").assertDoesNotExist()
        assertEquals(before, compose.onNodeWithTag("settings_about").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, mutations); assertTrue(opened.isEmpty())
    }

    @Test fun authorAndContentAreConsistentInBothCampusPalettes() {
        render()
        for (target in Campus.entries) {
            compose.runOnIdle { campus = target }
            compose.waitForIdle(); open()
            compose.onNodeWithTag("settings_about_author_name").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("非学校官方应用").performScrollTo().assertIsDisplayed()
            shot("about-${target.dataKey}-1.21")
            compose.onNodeWithTag("settings_sheet_close").performClick()
        }
        assertEquals(0, mutations); assertTrue(opened.isEmpty())
    }

    @Test fun approvedFolioKeepsIntroductionInCoverAndAuthorBesideOriginalPig() {
        render();open()
        compose.onNodeWithTag("settings_sheet_campus").assertDoesNotExist()
        compose.onNodeWithTag("settings_sheet_title").assertDoesNotExist()
        compose.onNodeWithTag("settings_about_description").assertTextEquals(AboutCopy.description)
        compose.onNodeWithText("YOURoom / ABOUT").assertExists()
        val cover=compose.onNodeWithTag("settings_about_cover").fetchSemanticsNode().boundsInRoot
        val description=compose.onNodeWithTag("settings_about_description").fetchSemanticsNode().boundsInRoot
        assertTrue(description.top>=cover.top && description.bottom<=cover.bottom)
        compose.onNodeWithText("01").assertExists()
        compose.onNodeWithText("02").assertExists()
        compose.onNodeWithText("03").assertExists()
        compose.onNodeWithText("04").assertExists()
        compose.onNodeWithTag("settings_about_author_name").performScrollTo().assertTextEquals(AboutCopy.author)
        val authorLayout=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("settings_about_author_name").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(authorLayout)}
        assertEquals(14.sp,authorLayout.single().layoutInput.style.fontSize)
        assertEquals(FontWeight.Bold,authorLayout.single().layoutInput.style.fontWeight)
        val name=compose.onNodeWithTag("settings_about_author_name").fetchSemanticsNode().boundsInRoot
        val pig=compose.onNodeWithTag("settings_about_pig").assertWidthIsEqualTo(63.dp).assertHeightIsEqualTo(63.dp)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(name.right<pig.left)
        assertTrue(pig.center.y>=name.top-50f && pig.center.y<=name.bottom+50f)
        compose.onNodeWithText(AboutCopy.local.last()).assertIsDisplayed()
        assertEquals(0,mutations)
        assertTrue(opened.isEmpty())
        shot("about-approved-a-footer")
    }

    @Test fun allTwelveThemesAndBothModesKeepOriginalPigColorsAndReachableClose() {
        render()
        for(value in CampusTone.entries) for(night in listOf(false,true)) {
            compose.runOnIdle {tone=value;dark=night}
            open()
            compose.onNodeWithTag("settings_about_name").assertIsDisplayed()
            if(value in listOf(CampusTone.MATISSE,CampusTone.MORNING_BELL)) shot("about-"+value.id+"-"+night+"-cover")
            compose.onNodeWithTag("settings_about_pig").performScrollTo().assertIsDisplayed()
            val pixels=compose.onNodeWithTag("settings_about_pig").captureToImage().toPixelMap()
            for(rgb in listOf(0xFFFFD2B1.toInt(),0xFFFF7D86.toInt(),0xFFE6508F.toInt(),0xFF2D2D2B.toInt())) {
                var count=0
                for(y in 0 until pixels.height) for(x in 0 until pixels.width)
                    if(pixels[x,y].toArgb()==rgb) count++
                assertTrue("$value/$night: missing original pig color "+rgb.toUInt().toString(16),count>5)
            }
            compose.onNodeWithTag("settings_sheet_close").assertIsDisplayed().performClick()
        }
        assertEquals(0,mutations)
        assertTrue(opened.isEmpty())
    }
}
