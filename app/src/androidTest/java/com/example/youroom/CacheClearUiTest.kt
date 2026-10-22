package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.*
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CacheClearUiTest {
    @get:Rule val compose=createComposeRule()
    private class Source:ClassroomDataSource {
        override val profiles=MutableStateFlow<Map<String,RoomProfile>>(emptyMap())
        var attempts=0
        var gate:CompletableDeferred<Unit>?=null
        var failure:Exception?=null
        override suspend fun hasSession()=true
        override suspend fun currentAccount()="sample"
        override suspend fun login(account:String,password:String)=error("No real login in tests")
        override suspend fun verifyCacheClearCredentials(account:String,password:String) { attempts++;gate?.await();failure?.let{throw it} }
        override suspend fun logout()=Unit
        override suspend fun cached(campus:Campus)=DemoData.snapshot(campus,GuideData.clock()).copy(isDemo=false)
        override suspend fun refresh(campus:Campus)=cached(campus)
        override suspend fun saveProfile(profile:RoomProfile)=Unit
        override suspend fun deleteProfile(roomId:String)=Unit
    }
    private val source=Source()
    private lateinit var model:YOURoomViewModel
    private var resetCalls=0
    private var resetAccepted=true
    private fun render(large:Boolean=false,demo:Boolean=false) {
        val store=ViewModelStore()
        compose.setContent {
            val instance=remember { YOURoomViewModel(source,GuideSettings(AppearancePreferences(),PeriodStripPreferences()),
                GuideData.clock(),resetApplicationData={resetCalls++;resetAccepted}).also{model=it;store.put("test",it)} }
            DisposableEffect(Unit) { onDispose { store.clear() } }
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if(large) Density(density.density*1.25f,1.6f) else density) {
                YOURoomTheme { YOURoomApp(instance) }
            }
        }
        compose.waitUntil(10_000) { ::model.isInitialized && !model.state.value.initializing }
        if(demo) compose.runOnIdle { model.showDemo() }
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_clear_cache"))
        compose.onNodeWithTag("settings_clear_cache").performClick()
    }
    private fun credentials() {
        compose.onNodeWithTag("cache_clear_confirm").performClick()
        compose.onNodeWithTag("cache_clear_submit").assertIsNotEnabled()
        compose.onNodeWithTag("cache_clear_account").performTextInput("sample")
        compose.onNodeWithTag("cache_clear_password").performTextInput("dummy-password")
    }
    private fun shot(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync();android.os.SystemClock.sleep(300)
        File(instrumentation.targetContext.getExternalFilesDir(null),"cache-clear-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun buttonRequiresConfirmationAndPasswordBeforeAResetCanBeRequested() {
        render()
        compose.onNodeWithTag("cache_clear_dialog").assertIsDisplayed()
        compose.onNodeWithTag("cache_clear_password").assertDoesNotExist()
        assertEquals(0,source.attempts);assertEquals(0,resetCalls)
        shot("confirmation")
        credentials();shot("credentials")
        compose.onNodeWithTag("cache_clear_submit").performClick()
        compose.waitUntil(10_000) { model.state.value.cacheClear?.phase==CacheClearPhase.REQUESTED }
        assertEquals(1,source.attempts);assertEquals(1,resetCalls)
        compose.onNodeWithTag("cache_clear_cancel").assertDoesNotExist()
        compose.onNodeWithTag("cache_clear_password").assertDoesNotExist()
    }

    @Test fun rejectedCredentialsPreserveTheAccountAndAllowReentryWithoutRetainingPassword() {
        source.failure=SchoolLoginRejected("密码错误，请重新输入")
        render();credentials()
        val original=model.state.value
        compose.onNodeWithTag("cache_clear_submit").performClick()
        compose.onNodeWithTag("cache_clear_error").performScrollTo().assertTextEquals("密码错误，请重新输入")
        compose.onNodeWithTag("cache_clear_password").assert(hasText(""))
        compose.onNodeWithTag("cache_clear_submit").assertIsNotEnabled()
        assertEquals(0,resetCalls)
        assertEquals(original.appearance,model.state.value.appearance)
        assertEquals(original.snapshot,model.state.value.snapshot)
        compose.onNodeWithTag("cache_clear_cancel").performClick()
        assertTrue(model.state.value.loggedIn)
    }

    @Test fun cancellingWhileVerifyingNeverResetsAfterALateReply() {
        source.gate=CompletableDeferred()
        render();credentials()
        compose.onNodeWithTag("cache_clear_submit").performClick()
        compose.onNodeWithTag("cache_clear_verifying").assertIsDisplayed()
        compose.onNodeWithTag("cache_clear_cancel").performClick()
        source.gate!!.complete(Unit);compose.waitForIdle()
        assertEquals(0,resetCalls)
        assertNull(model.state.value.cacheClear)
    }

    @Test fun demoButtonOnlyExplainsThatFormalDataCannotBeDeleted() {
        render(demo=true)
        compose.onNodeWithTag("cache_clear_acknowledge").performClick()
        assertEquals(0,source.attempts);assertEquals(0,resetCalls)
        assertTrue(model.state.value.demo)
    }

    @Test fun largeTextCanReachConfirmationCredentialsAndCancellation() {
        render(large=true)
        compose.onNodeWithTag("cache_clear_confirm").assertIsDisplayed()
        credentials()
        compose.onNodeWithTag("cache_clear_cancel").assertIsDisplayed()
        shot("large-text")
        compose.onNodeWithTag("cache_clear_cancel").performClick()
        assertEquals(0,resetCalls)
    }

    @Test fun activityStateRestorationDoesNotSaveEitherCredentialField() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent { YOURoomTheme { CacheClearDialog(CacheClearState(CacheClearPhase.CREDENTIALS),{},{},{_,_->}) } }
        compose.onNodeWithTag("cache_clear_account").performTextInput("sample")
        compose.onNodeWithTag("cache_clear_password").performTextInput("dummy-password")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("cache_clear_account").assert(hasText(""))
        compose.onNodeWithTag("cache_clear_password").assert(hasText(""))
        compose.onNodeWithTag("cache_clear_submit").assertIsNotEnabled()
    }
}
