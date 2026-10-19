package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
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
    @get:Rule val compose = createComposeRule()
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var fontScale by mutableFloatStateOf(1f)
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
        CompositionLocalProvider(LocalUriHandler provides browser, LocalDensity provides Density(density.density, fontScale)) {
            YOURoomTheme(campus = campus) { Surface {
                val snapshot = DemoData.snapshot(campus)
                val room = snapshot.classrooms.first().emptyProfile().copy(favorite = true, note = "靠窗")
                SettingsScreen(YOURoomState(initializing = false, campus = campus, snapshot = snapshot,
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
        compose.onNodeWithText("开发者  23级 软工 小嚎猪").performScrollTo().assertIsDisplayed()
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
            "空闲信息来自学校课表，并非实时空座",
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
        compose.onNodeWithText("开发者  23级 软工 小嚎猪").performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithTag("settings_about_content").assertDoesNotExist()
        assertEquals(before, compose.onNodeWithTag("settings_about").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, mutations); assertTrue(opened.isEmpty())
    }

    @Test fun authorAndContentAreConsistentInBothCampusPalettes() {
        render()
        for (target in Campus.entries) {
            compose.runOnIdle { campus = target }
            compose.waitForIdle(); open()
            compose.onNodeWithText("开发者  23级 软工 小嚎猪").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("非学校官方应用").performScrollTo().assertIsDisplayed()
            shot("about-${target.dataKey}-1.21")
            compose.onNodeWithTag("settings_sheet_close").performClick()
        }
        assertEquals(0, mutations); assertTrue(opened.isEmpty())
    }

    @Test fun allFourHeadingsUseTheSameTypographyAndTheAuthorIsLastAndRightAligned() {
        render(); open()
        compose.onNodeWithTag("settings_sheet_campus").assertDoesNotExist()
        val brand = compose.onNodeWithTag("settings_about_name").fetchSemanticsNode().boundsInRoot
        val description = compose.onNodeWithTag("settings_about_description").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 12.dp.toPx() }, description.top-brand.bottom, 1f)
        val sizes = mutableListOf<androidx.compose.ui.unit.TextUnit>()
        for (title in listOf("GitHub","问题反馈","数据与使用","本地与登录")) {
            val result = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(title, useUnmergedTree=true).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
            sizes += result.single().layoutInput.style.fontSize
        }
        assertEquals(1,sizes.distinct().size)
        val items=listOf("settings_about_project","settings_about_feedback","settings_about_data","settings_about_local")
        val bounds=items.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (a,b) -> assertEquals("Uniform compact section spacing",a.bottom,b.top,1f) }
        compose.onNodeWithTag("settings_about_data_heading").assertHeightIsEqualTo(36.dp)
        compose.onNodeWithTag("settings_about_local_heading").assertHeightIsEqualTo(36.dp)
        for (section in listOf("data", "local")) {
            val heading = compose.onNodeWithTag("settings_about_${section}_heading").fetchSemanticsNode().boundsInRoot
            val paragraphs = compose.onNodeWithTag("settings_about_${section}_paragraphs").fetchSemanticsNode().boundsInRoot
            assertEquals(heading.bottom, paragraphs.top, 1f)
        }
        compose.onNodeWithText("开发者  23级 软工 小嚎猪").performScrollTo()
        val authorLayout=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("开发者  23级 软工 小嚎猪")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(authorLayout) }
        val nickname=authorLayout.single().layoutInput.text.spanStyles.single()
        assertEquals("23级 软工 小嚎猪",authorLayout.single().layoutInput.text.text.substring(nickname.start,nickname.end))
        assertEquals(14.sp,nickname.item.fontSize)
        assertEquals(FontWeight.Bold,nickname.item.fontWeight)
        val author=compose.onNodeWithTag("settings_about_author").fetchSemanticsNode().boundsInRoot
        val content=compose.onNodeWithTag("settings_about_content").fetchSemanticsNode().boundsInRoot
        assertEquals(content.right,author.right,1f)
        assertTrue(author.top>=compose.onNodeWithText("退出登录清除登录状态，保留已记住的账号密码、教室标记与设置",substring=true).fetchSemanticsNode().boundsInRoot.bottom)
    }
}
