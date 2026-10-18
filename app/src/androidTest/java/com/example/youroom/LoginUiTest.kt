package com.example.youroom

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.ui.LoginScreen
import com.example.youroom.ui.YOURoomState
import com.example.youroom.ui.theme.YOURoomTheme
import com.example.youroom.data.local.RememberedCredentials
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LoginUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rememberIsOffByDefaultAndDisclosureFollowsConsent() {
        compose.setContent { YOURoomTheme { LoginScreen(YOURoomState(initializing = false), { _, _ -> }, {}, {}) } }
        compose.onNodeWithTag("remember-password").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("账号密码加密保存在本机，用于自动填入和登录过期后重登；退出登录或结束演示后仍保留。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("remember-password").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("密码仅用于本次 HTTPS 登录，不会保存。登录状态加密保存在本机。").performScrollTo().assertIsDisplayed()
    }

    @Test fun loginPassesExplicitConsentAndClearsPassword() {
        var receivedAccount: String? = null
        var receivedPassword: String? = null
        var receivedRemember = false
        compose.setContent { YOURoomTheme {
            LoginScreen(YOURoomState(initializing = false), { _, _ -> error("Use consent callback") }, {}, {},
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
                rememberedLogin=RememberedCredentials("dummy-account","dummy-password")), { _, p -> received=p }, {}, {})
        } }
        compose.onNodeWithTag("account").assertTextContains("dummy-account")
        compose.onNodeWithTag("remember-password").performScrollTo().assertIsOn()
        compose.onNodeWithTag("login").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("dummy-password",received) }
    }

    @Test fun passwordIsNotRestoredAfterActivityStateRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { YOURoomTheme { LoginScreen(YOURoomState(initializing = false), { _, _ -> }, {}, {}) } }
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
                rememberedLogin=remembered),{_,p->received=p},{},{})
        } }
        compose.onNodeWithTag("password").performTextReplacement("unsaved-edit")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("login").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("dummy-password",received) }
    }

    private val remembered=RememberedCredentials("dummy-account","dummy-password")

    @Test fun pendingLoginDisablesCredentialEditsAndRememberToggle() {
        val state = mutableStateOf(YOURoomState(initializing = false))
        compose.setContent { YOURoomTheme { LoginScreen(state.value, { _, _ -> }, {}, {}) } }
        compose.runOnIdle { state.value = state.value.copy(loggingIn = true) }
        compose.onNodeWithTag("account").assertIsNotEnabled()
        compose.onNodeWithTag("password").assertIsNotEnabled()
        compose.onNodeWithTag("remember-password").performScrollTo().assertIsNotEnabled()
    }

    @Test fun loginScreenKeepsCompactThemeAndAccessibleConsent() {
        compose.setContent { YOURoomTheme { LoginScreen(YOURoomState(initializing = false), { _, _ -> }, {}, {}) } }
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
