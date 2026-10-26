package com.example.youroom

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.ui.LoginScreen
import com.example.youroom.ui.YOURoomState
import com.example.youroom.ui.theme.YOURoomTheme
import com.example.youroom.data.local.RememberedCredentials
import com.example.youroom.domain.Campus
import com.example.youroom.domain.AppearancePreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LoginUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rememberIsOffByDefaultAndStillRequiresExplicitConsent() {
        compose.setContent { YOURoomTheme { LoginScreen(YOURoomState(initializing = false), { _, _ -> }, {}) } }
        compose.onNodeWithTag("remember-password").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("remember-password").performScrollTo().performClick().assertIsOff()
    }

    @Test fun transparentG66LogoSitsRightOfTheTitleWithoutRedundantCopyOrCacheEntry() {
        var demoOpened = false
        compose.setContent { YOURoomTheme {
            LoginScreen(YOURoomState(initializing = false, cachedCampuses = Campus.entries.toSet()),
                { _, _ -> }, { demoOpened = true })
        } }
        val title = compose.onNodeWithTag("login_title").assertTextEquals("YOURoom").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val logo = compose.onNodeWithTag("login_logo").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Logo must be to the right of YOURoom",logo.left >= title.right)
        assertTrue("Title and logo share a row",kotlin.math.abs(logo.center.y-title.center.y)<1f)
        for (removed in listOf("北邮 · 自习", "北邮空闲教室", "登录教务账号", "账号密码加密保存在本机",
            "密码仅用于本次 HTTPS 登录", "连接提示", "可通过能访问教务服务的网络使用",
            "查看本地缓存（非实时）", "个人工具 · 非学校官方应用")) {
            compose.onNodeWithText(removed,substring=true).assertDoesNotExist()
        }
        compose.onNodeWithTag("demo").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(demoOpened) }
    }

    @Test fun narrowLoginKeepsTheBrandOnOneLineAtLargeFontsInBothModes() {
        val dark = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                YOURoomTheme(appearance = AppearancePreferences(darkMode = dark.value)) {
                    Surface(Modifier.width(320.dp)) {
                        LoginScreen(YOURoomState(initializing = false), { _, _ -> }, {})
                    }
                }
            }
        }
        for (night in listOf(false,true)) {
            compose.runOnIdle { dark.value = night }
            compose.waitForIdle()
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("YOURoom").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertEquals(1,layout.single().lineCount)
            assertFalse(layout.single().hasVisualOverflow)
            compose.onNodeWithTag("login_logo").assertIsDisplayed()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null),"login-g66-${if(night) "dark" else "light"}.png")
                .outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }

    @Test fun loginPassesExplicitConsentAndClearsPassword() {
        var receivedAccount: String? = null
        var receivedPassword: String? = null
        var receivedRemember = false
        compose.setContent { YOURoomTheme {
            LoginScreen(YOURoomState(initializing = false), { _, _ -> error("Use consent callback") }, {},
                onRememberedLogin = { a, p, r -> receivedAccount = a; receivedPassword = p; receivedRemember = r })
        } }
        compose.onNodeWithTag("account").performTextInput("dummy-account")
        compose.onNodeWithTag("password").performTextInput("dummy-password")
        compose.onNodeWithTag("remember-password").performScrollTo().performClick()
        compose.onNodeWithTag("login").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("dummy-account", receivedAccount)
            assertEquals("dummy-password", receivedPassword)
            assertTrue(receivedRemember)
        }
        compose.onNodeWithTag("password").assertTextContains("")
        compose.onNodeWithTag("login").assertIsNotEnabled()
    }

    @Test fun rememberedAccountAndPasswordArePrefilledAndCanBeSubmitted() {
        var received: String? = null
        compose.setContent { YOURoomTheme {
            LoginScreen(YOURoomState(initializing = false, rememberedAccount = "dummy-account",
                rememberedLogin=RememberedCredentials("dummy-account","dummy-password")), { _, p -> received=p }, {})
        } }
        compose.onNodeWithTag("account").assertTextContains("dummy-account")
        compose.onNodeWithTag("remember-password").performScrollTo().assertIsOn()
        compose.onNodeWithTag("login").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("dummy-password",received) }
    }

    @Test fun passwordIsNotRestoredAfterActivityStateRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { YOURoomTheme { LoginScreen(YOURoomState(initializing = false), { _, _ -> }, {}) } }
        compose.onNodeWithTag("account").performTextInput("dummy-account")
        compose.onNodeWithTag("password").performTextInput("dummy-password")
        compose.onNodeWithTag("remember-password").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("account").assertTextContains("dummy-account")
        compose.onNodeWithTag("remember-password").assertIsOn()
        compose.onNodeWithTag("login").assertIsNotEnabled()
    }

    @Test fun rememberedPasswordIsReloadedButAnUnsavedEditIsNotRestored() {
        val restoration=StateRestorationTester(compose)
        var received: String?=null
        restoration.setContent { YOURoomTheme {
            LoginScreen(YOURoomState(initializing=false,rememberedAccount="dummy-account",
                rememberedLogin=remembered),{_,p->received=p},{})
        } }
        compose.onNodeWithTag("password").performTextReplacement("unsaved-edit")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("login").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("dummy-password",received) }
    }

    private val remembered=RememberedCredentials("dummy-account","dummy-password")

    @Test fun pendingLoginDisablesCredentialEditsAndRememberToggle() {
        val state = mutableStateOf(YOURoomState(initializing = false))
        compose.setContent { YOURoomTheme { LoginScreen(state.value, { _, _ -> }, {}) } }
        compose.runOnIdle { state.value = state.value.copy(loggingIn = true) }
        compose.onNodeWithTag("account").assertIsNotEnabled()
        compose.onNodeWithTag("password").assertIsNotEnabled()
        compose.onNodeWithTag("remember-password").performScrollTo().assertIsNotEnabled()
    }

    @Test fun loginScreenKeepsCompactThemeAndAccessibleConsent() {
        compose.setContent { YOURoomTheme { LoginScreen(YOURoomState(initializing = false), { _, _ -> }, {}) } }
        compose.onNodeWithTag("remember-password").performScrollTo().assertHasClickAction()
        val bounds = compose.onNodeWithTag("remember-password").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height >= 48 * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density)
        compose.onNodeWithTag("account").performScrollTo()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "remember-login.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
