package com.example.youroom

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.ui.LoginScreen
import com.example.youroom.ui.YouRoomState
import com.example.youroom.ui.theme.YouRoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LoginUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rememberIsOffByDefaultAndDisclosureFollowsConsent() {
        compose.setContent { YouRoomTheme { LoginScreen(YouRoomState(initializing = false), { _, _ -> }, {}, {}) } }
        compose.onNodeWithTag("remember-password").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("密码加密保存在本机，仅用于登录过期后自动重登；退出登录时清除。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("remember-password").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("密码仅用于本次 HTTPS 登录，不会保存。登录状态加密保存在本机。").performScrollTo().assertIsDisplayed()
    }

    @Test fun loginPassesExplicitConsentAndClearsPassword() {
        var receivedAccount: String? = null
        var receivedPassword: String? = null
        var receivedRemember = false
        compose.setContent { YouRoomTheme {
            LoginScreen(YouRoomState(initializing = false), { _, _ -> error("Use consent callback") }, {}, {},
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

    @Test fun rememberedAccountIsPrefilledButPasswordNeverIs() {
        compose.setContent { YouRoomTheme {
            LoginScreen(YouRoomState(initializing = false, rememberedAccount = "dummy-account"), { _, _ -> }, {}, {})
        } }
        compose.onNodeWithTag("account").assertTextContains("dummy-account")
        compose.onNodeWithTag("remember-password").performScrollTo().assertIsOn()
        compose.onNodeWithTag("login").assertIsNotEnabled()
    }

    @Test fun passwordIsNotRestoredAfterActivityStateRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { YouRoomTheme { LoginScreen(YouRoomState(initializing = false), { _, _ -> }, {}, {}) } }
        compose.onNodeWithTag("account").performTextInput("dummy-account")
        compose.onNodeWithTag("password").performTextInput("dummy-password")
        compose.onNodeWithTag("remember-password").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("account").assertTextContains("dummy-account")
        compose.onNodeWithTag("remember-password").assertIsOn()
        compose.onNodeWithTag("login").assertIsNotEnabled()
    }

    @Test fun pendingLoginDisablesCredentialEditsAndRememberToggle() {
        val state = mutableStateOf(YouRoomState(initializing = false))
        compose.setContent { YouRoomTheme { LoginScreen(state.value, { _, _ -> }, {}, {}) } }
        compose.runOnIdle { state.value = state.value.copy(loggingIn = true) }
        compose.onNodeWithTag("account").assertIsNotEnabled()
        compose.onNodeWithTag("password").assertIsNotEnabled()
        compose.onNodeWithTag("remember-password").performScrollTo().assertIsNotEnabled()
    }

    @Test fun loginScreenKeepsCompactThemeAndAccessibleConsent() {
        compose.setContent { YouRoomTheme { LoginScreen(YouRoomState(initializing = false), { _, _ -> }, {}, {}) } }
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
