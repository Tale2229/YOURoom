package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.example.youroom.data.*
import com.example.youroom.data.local.FilterSettings
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UsageGuideIntegrationUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var model:YOURoomViewModel
    private class Settings(private val memory:GuideSettings=GuideSettings(AppearancePreferences(),PeriodStripPreferences())):
        FilterSettings by memory {
        override val guideHistory=MutableStateFlow(GuideHistory(available=true))
        var ordinaryWrites=0
        override suspend fun saveAppearance(value:AppearancePreferences) { ordinaryWrites++;memory.saveAppearance(value) }
        override suspend fun savePeriodStrip(value:PeriodStripPreferences) { ordinaryWrites++;memory.savePeriodStrip(value) }
        override suspend fun savePlanningPreferences(value:PlanningPreferences) { ordinaryWrites++;memory.savePlanningPreferences(value) }
        override suspend fun saveGuideRecord(identity:String,record:GuideRecord) {
            guideHistory.value=guideHistory.value.copy(records=guideHistory.value.records+(identity to record))
        }
        override suspend fun guideAccountIdentity(account:String):String {
            guideHistory.value=guideHistory.value.copy(activeAccount="test-$account")
            return "test-$account"
        }
    }
    private class Source:ClassroomDataSource {
        override val profiles=MutableStateFlow<Map<String,RoomProfile>>(emptyMap())
        var session=false
        var logouts=0
        override suspend fun hasSession()=session
        override suspend fun login(account:String,password:String) { session=true }
        override suspend fun logout() { logouts++;session=false }
        override suspend fun cached(campus:Campus):ClassroomSnapshot?=null
        override suspend fun refresh(campus:Campus)=DemoData.snapshot(campus,GuideData.clock()).copy(isDemo=false)
        override suspend fun saveProfile(profile:RoomProfile) { profiles.value+=profile.roomId to profile }
        override suspend fun deleteProfile(roomId:String) { profiles.value-=roomId }
    }

    @Test fun firstUseReplayAndGuestHistoryAreIntegratedWithoutChangingTheOriginalWorkspace() {
        val source=Source();val settings=Settings()
        val store=ViewModelStore()
        compose.setContent {
            val instance=remember { YOURoomViewModel(source,settings,GuideData.clock()).also { model=it;store.put("test",it) } }
            DisposableEffect(Unit) { onDispose { store.clear() } }
            YOURoomTheme { YOURoomApp(instance) }
        }
        compose.waitUntil(10_000) { ::model.isInitialized && !model.state.value.initializing }
        compose.runOnIdle { model.showDemo() }
        compose.onNodeWithTag("guide_workspace").assertIsDisplayed()
        compose.onNodeWithTag("guide_practice_banner").assertDoesNotExist()
        compose.onNodeWithTag("guide_skip").performClick()
        compose.waitUntil(10_000) { model.state.value.guideRequest==null }
        compose.runOnIdle { model.generatePlanning(PlanningDraft(savedWindows=GuideData.windows)) }
        compose.waitUntil(20_000) { model.planning.executions.value[Campus.XITUCHENG]?.result!=null }
        val original=model.state.value
        val plan=model.planning.executions.value
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_usage_guide"))
        compose.onNodeWithTag("settings_usage_guide").performClick()
        compose.onNodeWithTag("guide_chapter_3").performClick()
        compose.onNodeWithTag("guide_pause").assertDoesNotExist()
        compose.onAllNodesWithTag("tab_settings").onLast().performTouchInput { click() }
        compose.onAllNodesWithTag("settings_theme_next").onLast().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("guide_next").performClick()
        compose.onAllNodesWithTag("settings_dark_mode_switch").onLast().performClick()
        compose.onNodeWithTag("guide_skip").performClick()
        compose.waitForIdle()
        assertEquals(original.appearance,model.state.value.appearance)
        assertEquals(original.periodStrip,model.state.value.periodStrip)
        assertEquals(original.planningPreferences,model.state.value.planningPreferences)
        assertEquals(original.profiles,model.state.value.profiles)
        assertEquals(plan,model.planning.executions.value)
        assertEquals(0,settings.ordinaryWrites)
        assertEquals(0,source.logouts)
        compose.onNodeWithTag("settings_usage_guide").assertIsDisplayed()
        compose.runOnIdle { model.openLogin();model.showDemo() }
        compose.waitForIdle()
        assertNull(model.state.value.guideRequest)
        compose.runOnIdle { model.openLogin();model.login("sample-account","dummy") }
        compose.waitUntil(10_000) { model.state.value.guideRequest?.identity=="test-sample-account" }
        compose.onNodeWithTag("guide_workspace").assertIsDisplayed()
        compose.onNodeWithTag("guide_practice_banner").assertDoesNotExist()
        compose.onNodeWithTag("guide_skip").performClick()
        compose.waitUntil(10_000) { model.state.value.guideRequest==null }
        assertTrue(source.session)
        assertEquals(0,source.logouts)
        assertEquals(GuideOutcome.SKIPPED,settings.guideHistory.value.records.getValue(GuideHistory.Guest).outcome)
    }
}
