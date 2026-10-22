package com.example.youroom

import androidx.lifecycle.ViewModelStore
import com.example.youroom.data.*
import com.example.youroom.data.local.FilterSettings
import com.example.youroom.domain.*
import com.example.youroom.ui.YOURoomViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CacheClearTest {
    private class Source:ClassroomDataSource {
        override val profiles=MutableStateFlow<Map<String,RoomProfile>>(emptyMap())
        var owner:String?="sample"
        var verificationFailure:Exception?=null
        var gate:CompletableDeferred<Unit>?=null
        var nonCancellable=false
        var verifications=0
        var logins=0
        override suspend fun hasSession()=true
        override suspend fun currentAccount()=owner
        override suspend fun login(account:String,password:String) { logins++ }
        override suspend fun verifyCacheClearCredentials(account:String,password:String) {
            verifications++
            if(nonCancellable) withContext(NonCancellable) { gate?.await() } else gate?.await()
            verificationFailure?.let { throw it }
        }
        override suspend fun logout()=Unit
        override suspend fun cached(campus:Campus)=DemoData.snapshot(campus,GuideData.clock()).copy(isDemo=false)
        override suspend fun refresh(campus:Campus)=cached(campus)
        override suspend fun saveProfile(profile:RoomProfile) { profiles.value+=profile.roomId to profile }
        override suspend fun deleteProfile(roomId:String) { profiles.value-=roomId }
    }
    private class Settings(private val memory:GuideSettings=GuideSettings(AppearancePreferences(),PeriodStripPreferences())):
        FilterSettings by memory {
        var match:Boolean?=null
        override suspend fun matchesActiveAccount(account:String)=match
    }
    private class Reset { var calls=0;var accepted=true;fun request():Boolean { calls++;return accepted } }
    private fun check(training:Boolean=false,block:suspend TestScope.(YOURoomViewModel,Source,Settings,Reset)->Unit)=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source=Source();val settings=Settings();val reset=Reset()
        val model=YOURoomViewModel(source,settings,GuideData.clock(),StandardTestDispatcher(testScheduler),
            training=training,resetApplicationData=reset::request)
        val store=ViewModelStore().apply { put("test",model) }
        try { runCurrent();block(model,source,settings,reset) }
        finally { store.clear();Dispatchers.resetMain() }
    }
    private fun authenticate(model:YOURoomViewModel) { model.requestCacheClear();model.confirmCacheClear() }

    @Test fun verificationCannotBypassConfirmationOrRunWithEmptyCredentials()=check { model,source,_,reset ->
        model.verifyAndClearCache("sample","secret");runCurrent()
        assertNull(model.state.value.cacheClear);assertEquals(0,reset.calls)
        model.requestCacheClear()
        assertEquals(CacheClearPhase.CONFIRM,model.state.value.cacheClear!!.phase)
        model.verifyAndClearCache("sample","secret");runCurrent()
        assertEquals(0,source.verifications)
        model.confirmCacheClear();model.verifyAndClearCache("sample","");runCurrent()
        assertEquals(CacheClearPhase.CREDENTIALS,model.state.value.cacheClear!!.phase)
        assertNotNull(model.state.value.cacheClear!!.error)
        assertEquals(0,reset.calls)
    }

    @Test fun successfulVerificationRequestsExactlyOneSystemResetWithoutLoggingInAgain()=check { model,source,_,reset ->
        authenticate(model)
        source.gate=CompletableDeferred()
        model.verifyAndClearCache(" sample ","secret");runCurrent()
        assertEquals(CacheClearPhase.VERIFYING,model.state.value.cacheClear!!.phase)
        model.verifyAndClearCache("sample","secret");runCurrent()
        assertEquals(1,source.verifications);assertEquals(0,reset.calls)
        source.gate!!.complete(Unit);runCurrent()
        assertEquals(1,reset.calls)
        assertEquals(0,source.logins)
        assertEquals(CacheClearPhase.REQUESTED,model.state.value.cacheClear!!.phase)
        model.cancelCacheClear();model.verifyAndClearCache("sample","secret");runCurrent()
        assertEquals(1,reset.calls)
    }

    @Test fun wrongOwnerCannotMakeARemoteRequestOrDeleteAnything()=check { model,source,_,reset ->
        authenticate(model);model.verifyAndClearCache("other","secret");runCurrent()
        assertEquals(0,source.verifications);assertEquals(0,reset.calls)
        assertTrue(model.state.value.cacheClear!!.error!!.contains("当前登录账号"))
    }

    @Test fun passwordOrNetworkFailurePreservesAllWorkspaceData()=check { model,source,settings,reset ->
        model.setDarkMode(true);model.setFilters(SearchFilters(query="2-"));runCurrent()
        val before=model.state.value
        val prefs=settings.appearance.first()
        for(error in listOf(SchoolLoginRejected("密码错误"),SchoolRequestFailed("网络不可用"))) {
            source.verificationFailure=error
            authenticate(model);model.verifyAndClearCache("sample","wrong");runCurrent()
            assertEquals(0,reset.calls)
            assertEquals(before.appearance,model.state.value.appearance)
            assertEquals(before.snapshot,model.state.value.snapshot)
            assertEquals(before.filters,model.state.value.filters)
            assertEquals(before.profiles,model.state.value.profiles)
            assertEquals(prefs,settings.appearance.first())
            assertEquals(error.userMessage,model.state.value.cacheClear!!.error)
            model.cancelCacheClear()
        }
    }

    @Test fun cancelledVerificationDiscardsEvenAnUncancellableLateSuccess()=check { model,source,_,reset ->
        authenticate(model);source.gate=CompletableDeferred();source.nonCancellable=true
        model.verifyAndClearCache("sample","secret");runCurrent()
        model.cancelCacheClear();source.gate!!.complete(Unit);runCurrent()
        assertNull(model.state.value.cacheClear);assertEquals(0,reset.calls)
    }

    @Test fun changingAccountsWhileVerifyingCannotClearTheNewSession()=check { model,source,_,reset ->
        authenticate(model);source.gate=CompletableDeferred();source.nonCancellable=true
        model.verifyAndClearCache("sample","secret");runCurrent()
        model.openLogin();source.gate!!.complete(Unit);model.login("another","dummy");runCurrent()
        assertEquals(0,reset.calls)
        assertNull(model.state.value.cacheClear)
    }

    @Test fun legacyHashIsReadOnlyAndUnknownIdentityRequiresRelogin()=check { model,source,settings,reset ->
        source.owner=null
        for(match in listOf(null,false)) {
            settings.match=match
            authenticate(model);model.verifyAndClearCache("sample","secret");runCurrent()
            assertEquals(0,source.verifications);assertEquals(0,reset.calls)
            assertNotNull(model.state.value.cacheClear!!.error)
            model.cancelCacheClear()
        }
        settings.match=true
        authenticate(model);model.verifyAndClearCache("sample","secret");runCurrent()
        assertEquals(1,reset.calls)
    }

    @Test fun systemRefusalRequiresFreshVerificationBeforeRetry()=check { model,source,_,reset ->
        reset.accepted=false
        authenticate(model);model.verifyAndClearCache("sample","secret");runCurrent()
        assertEquals(CacheClearPhase.CREDENTIALS,model.state.value.cacheClear!!.phase)
        assertTrue(model.state.value.loggedIn)
        reset.accepted=true
        model.verifyAndClearCache("sample","secret");runCurrent()
        assertEquals(2,source.verifications);assertEquals(2,reset.calls)
    }

    @Test fun demoCanOnlyDisplayInformationAndNeverVerifiesOrResets()=check { model,source,_,reset ->
        model.showDemo();runCurrent();authenticate(model)
        model.verifyAndClearCache("sample","secret");runCurrent()
        assertEquals(CacheClearPhase.INFORMATION,model.state.value.cacheClear!!.phase)
        assertEquals(0,source.verifications);assertEquals(0,reset.calls)
    }

    @Test fun trainingCannotAuthorizeSystemResetEvenWithValidCredentials()=check(training=true) { model,source,_,reset ->
        authenticate(model);model.verifyAndClearCache("sample","secret");runCurrent()
        assertEquals(CacheClearPhase.INFORMATION,model.state.value.cacheClear!!.phase)
        assertEquals(0,source.verifications);assertEquals(0,reset.calls)
    }
}
