package com.example.youroom

import androidx.lifecycle.ViewModelStore
import com.example.youroom.data.*
import com.example.youroom.data.local.FilterSettings
import com.example.youroom.domain.*
import com.example.youroom.ui.YouRoomViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class YouRoomViewModelTest {
    private val instant = Instant.parse("2026-10-12T00:10:00Z")
    private val clock = Clock.fixed(instant, SchoolZone)
    private fun snapshot(campus: Campus) = DemoData.snapshot(campus).copy(isDemo = false,
        queryDate = LocalDate.of(2026, 10, 12), fetchedAt = instant)

    private fun checkModel(authenticated: Boolean = true,
        setupSource: FakeSource.() -> Unit = {},
        block: suspend TestScope.(YouRoomViewModel, FakeSource, FakeSettings) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource(authenticated).apply(setupSource)
        val settings = FakeSettings()
        val model = YouRoomViewModel(source, settings, clock)
        val store = ViewModelStore().apply { put("test", model) }
        try { runCurrent(); block(model, source, settings) }
        finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun switchQueriesTargetCampusAndKeepsFiltersSeparate() = checkModel { model, source, _ ->
        model.setFilters(model.state.value.filters.copy(building = "2", firstSlot = 4, lastSlot = 6))
        runCurrent()
        model.setCampus(Campus.SHAHE)
        runCurrent()
        assertEquals(listOf(Campus.XITUCHENG, Campus.SHAHE), source.requests)
        assertEquals("04", model.state.value.snapshot!!.campusId)
        assertNull(model.state.value.filters.building)
        model.setFilters(model.state.value.filters.copy(quietOnly = true))
        runCurrent()
        model.setCampus(Campus.XITUCHENG)
        assertEquals("2", model.state.value.filters.building)
        assertEquals(4, model.state.value.filters.firstSlot)
        assertEquals(6, model.state.value.filters.lastSlot)
        assertFalse(model.state.value.filters.quietOnly)
        model.setCampus(Campus.SHAHE)
        assertTrue(model.state.value.filters.quietOnly)
    }

    @Test fun lateResponseFromCanceledRequestCannotReplaceActiveCampus() = checkModel { model, source, _ ->
        var late: kotlin.coroutines.Continuation<ClassroomSnapshot>? = null
        source.loader = { campus -> if (campus == Campus.XITUCHENG) suspendCoroutine { late = it } else snapshot(campus) }
        model.refresh()
        runCurrent()
        model.setCampus(Campus.SHAHE)
        runCurrent()
        assertEquals("04", model.state.value.snapshot!!.campusId)
        late!!.resume(snapshot(Campus.XITUCHENG))
        runCurrent()
        assertEquals(Campus.SHAHE, model.state.value.campus)
        assertEquals("04", model.state.value.snapshot!!.campusId)
        assertFalse(model.state.value.refreshing)
    }

    @Test fun errorsAndCachedRoomsRemainScopedToTheirCampus() = checkModel { model, source, _ ->
        source.loader = { campus -> if (campus == Campus.SHAHE) throw SchoolRequestFailed("沙河查询失败") else snapshot(campus) }
        model.setCampus(Campus.SHAHE)
        runCurrent()
        assertEquals("沙河查询失败", model.state.value.error)
        assertNull(model.state.value.snapshot)
        model.setCampus(Campus.XITUCHENG)
        assertNull(model.state.value.error)
        assertEquals("01", model.state.value.snapshot!!.campusId)
    }

    @Test fun expiredSessionStopsAuthenticatedWorkspaceWithoutDeletingProfiles() = checkModel { model, source, _ ->
        val profile = snapshot(Campus.XITUCHENG).classrooms.first().emptyProfile().copy(favorite = true)
        source.profiles.value = mapOf(profile.roomId to profile)
        source.loader = { throw AuthenticationRequired() }
        model.setCampus(Campus.SHAHE)
        runCurrent()
        assertFalse(model.state.value.loggedIn)
        assertFalse(model.state.value.showWorkspace)
        assertEquals(profile, model.state.value.profiles[profile.roomId])
    }

    @Test fun demoSwitchRetainsDemoFiltersAndNeverSavesRealPreferences() = checkModel(false) { model, source, settings ->
        model.showDemo()
        model.setFilters(model.state.value.filters.copy(building = "2", firstSlot = 4, lastSlot = 5))
        val room = model.state.value.snapshot!!.classrooms.first()
        model.toggleFavorite(room)
        model.setCampus(Campus.SHAHE)
        model.setCampus(Campus.XITUCHENG)
        assertEquals("2", model.state.value.filters.building)
        assertEquals(4, model.state.value.filters.firstSlot)
        assertTrue(model.state.value.profiles[room.id]!!.favorite)
        runCurrent()
        assertTrue(settings.saved.isEmpty())
        assertTrue(source.profiles.value.isEmpty())
        model.openLogin()
        assertFalse(model.state.value.demo)
        assertTrue(model.state.value.profiles.isEmpty())
        assertNull(model.state.value.filters.building)
    }

    @Test fun returningToFreshCampusDoesNotQueryAgain() = checkModel { model, source, _ ->
        model.setCampus(Campus.SHAHE); runCurrent()
        model.setCampus(Campus.XITUCHENG); runCurrent()
        model.setCampus(Campus.SHAHE); runCurrent()
        assertEquals(2, source.requests.size)
    }

    @Test fun offlineUserCanOpenShaheCacheEvenWhenThereIsNoWestCache() = checkModel(false,
        setupSource = { caches[Campus.SHAHE] = snapshot(Campus.SHAHE) }) { model, source, _ ->
        assertEquals(setOf(Campus.SHAHE), model.state.value.cachedCampuses)
        assertNull(model.state.value.snapshot)
        model.viewCache()
        assertEquals(Campus.SHAHE, model.state.value.campus)
        assertTrue(model.state.value.viewingCache)
        assertTrue(model.state.value.fromCache)
        assertEquals("04", model.state.value.snapshot!!.campusId)
        assertTrue(source.requests.isEmpty())
    }

    @Test fun refreshFailureKeepsExistingTargetCampusSnapshot() = checkModel { model, source, _ ->
        model.setCampus(Campus.SHAHE); runCurrent()
        val previous = model.state.value.snapshot
        source.loader = { throw SchoolRequestFailed("查询失败") }
        model.refresh(); runCurrent()
        assertEquals(previous, model.state.value.snapshot)
        assertEquals("查询失败", model.state.value.error)
        assertFalse(model.state.value.refreshing)
    }

    private inner class FakeSource(var authenticated: Boolean) : ClassroomDataSource {
        override val profiles = MutableStateFlow<Map<String, RoomProfile>>(emptyMap())
        val requests = mutableListOf<Campus>()
        val caches = mutableMapOf<Campus, ClassroomSnapshot>()
        var loader: suspend (Campus) -> ClassroomSnapshot = { snapshot(it) }
        override suspend fun hasSession() = authenticated
        override suspend fun login(account: String, password: String) { authenticated = true }
        override suspend fun logout() { authenticated = false }
        override suspend fun cached(campus: Campus): ClassroomSnapshot? = caches[campus]
        override suspend fun refresh(campus: Campus): ClassroomSnapshot { requests += campus; return loader(campus) }
        override suspend fun saveProfile(profile: RoomProfile) { profiles.value += profile.roomId to profile }
        override suspend fun deleteProfile(roomId: String) { profiles.value -= roomId }
    }

    private class FakeSettings : FilterSettings {
        override val filters = MutableStateFlow(Campus.entries.associateWith { SearchFilters() })
        val saved = mutableListOf<Pair<Campus, SearchFilters>>()
        override suspend fun save(campus: Campus, filters: SearchFilters) {
            saved += campus to filters
            this.filters.value += campus to filters
        }
    }
}
