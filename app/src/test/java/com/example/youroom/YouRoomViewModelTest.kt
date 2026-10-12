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
        testClock: Clock = clock,
        block: suspend TestScope.(YouRoomViewModel, FakeSource, FakeSettings) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource(authenticated).apply(setupSource)
        val settings = FakeSettings()
        val model = YouRoomViewModel(source, settings, testClock)
        val store = ViewModelStore().apply { put("test", model) }
        try { runCurrent(); block(model, source, settings) }
        finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun switchKeepsTimeAndTagsButClearsBuildings() = checkModel { model, source, _ ->
        model.setFilters(model.state.value.filters.copy(buildings = setOf("2"), selectedSlots = (4..6).toSet()))
        runCurrent()
        model.setCampus(Campus.SHAHE)
        runCurrent()
        assertEquals(listOf(Campus.XITUCHENG, Campus.SHAHE), source.requests)
        assertEquals("04", model.state.value.snapshot!!.campusId)
        assertTrue(model.state.value.filters.buildings.isEmpty())
        assertEquals(setOf(4, 5, 6), model.state.value.filters.selectedSlots)
        model.setFilters(model.state.value.filters.copy(tags = setOf(PersonalTags.comfortable)))
        runCurrent()
        model.setCampus(Campus.XITUCHENG)
        assertTrue(model.state.value.filters.buildings.isEmpty())
        assertEquals(setOf(4, 5, 6), model.state.value.filters.selectedSlots)
        assertTrue(PersonalTags.comfortable in model.state.value.filters.tags)
        model.setCampus(Campus.SHAHE)
        assertTrue(PersonalTags.comfortable in model.state.value.filters.tags)
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
        model.setFilters(model.state.value.filters.copy(buildings = setOf("2"), selectedSlots = (4..5).toSet()))
        val room = model.state.value.snapshot!!.classrooms.first()
        model.toggleFavorite(room)
        model.setCampus(Campus.SHAHE)
        model.setCampus(Campus.XITUCHENG)
        assertTrue(model.state.value.filters.buildings.isEmpty())
        assertEquals(setOf(4, 5), model.state.value.filters.selectedSlots)
        assertTrue(model.state.value.profiles[room.id]!!.favorite)
        runCurrent()
        assertTrue(settings.saved.isEmpty())
        assertTrue(source.profiles.value.isEmpty())
        model.openLogin()
        assertFalse(model.state.value.demo)
        assertTrue(model.state.value.profiles.isEmpty())
        assertTrue(model.state.value.filters.buildings.isEmpty())
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

    @Test fun refreshingSameDayDoesNotFillAnIntentionallyEmptySelection() = checkModel { model, _, _ ->
        model.setFilters(model.state.value.filters.copy(selectedSlots = emptySet()))
        model.refresh(); runCurrent()
        assertTrue(model.state.value.filters.selectedSlots.isEmpty())
        assertTrue(model.state.value.result.rooms.isEmpty())
    }

    @Test fun outgoingPickerCannotRestoreItsSelectionIntoAnotherCampus() = checkModel { model, _, _ ->
        val west = model.state.value.filters.copy(selectedSlots = setOf(3, 5))
        model.setCampus(Campus.SHAHE); runCurrent()
        val shahe = model.state.value.filters
        model.setFiltersFor(Campus.XITUCHENG, west)
        assertEquals(shahe, model.state.value.filters)
    }

    @Test fun newDayRefreshReinitializesSlotsAndPreservesPersonalFilters() {
        val time = TestClock(instant)
        checkModel(testClock = time) { model, source, _ ->
            model.setFilters(model.state.value.filters.copy(selectedSlots = setOf(4, 6), buildings = setOf("2")))
            runCurrent()
            time.value = instant.plusSeconds(86_400)
            source.loader = { snapshot(it).copy(queryDate = LocalDate.of(2026, 10, 13), fetchedAt = time.value) }
            model.onForeground(); runCurrent()
            assertEquals(setOf(1), model.state.value.filters.selectedSlots)
            assertEquals(setOf("2"), model.state.value.filters.buildings)
            assertFalse(model.state.value.stale)
        }
    }

    @Test fun overnightDemoReinitializesBothCampusesWithoutNetworkOrPersistence() {
        val time = TestClock(instant)
        checkModel(false, testClock = time) { model, source, settings ->
            model.showDemo()
            model.setFilters(model.state.value.filters.copy(selectedSlots = setOf(4, 6)))
            model.setCampus(Campus.SHAHE)
            model.setFilters(model.state.value.filters.copy(selectedSlots = setOf(7, 9)))
            time.value = instant.plusSeconds(86_400)
            model.onForeground()
            assertFalse(model.state.value.stale)
            assertEquals(setOf(1), model.state.value.filters.selectedSlots)
            model.setCampus(Campus.XITUCHENG)
            assertEquals(setOf(1), model.state.value.filters.selectedSlots)
            runCurrent()
            assertTrue(source.requests.isEmpty())
            assertTrue(settings.saved.isEmpty())
        }
    }

    private class TestClock(var value: Instant) : Clock() {
        override fun getZone() = SchoolZone
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant() = value
    }

    @Test fun emptySelectionSurvivesFirstQueryOnOtherCampusAndReturn() = checkModel { model, _, _ ->
        model.setFilters(model.state.value.filters.copy(selectedSlots = emptySet()))
        model.setCampus(Campus.SHAHE); runCurrent()
        assertTrue(model.state.value.filters.selectedSlots.isEmpty())
        assertTrue(model.state.value.result.rooms.isEmpty())
        model.setCampus(Campus.XITUCHENG); runCurrent()
        assertTrue(model.state.value.filters.selectedSlots.isEmpty())
    }

    @Test fun sharedAttributesPersistButSwitchingBuildingsDoesNotWriteSettings() = checkModel { model, _, settings ->
        model.setFilters(model.state.value.filters.copy(buildings = setOf("2", "3")))
        runCurrent()
        assertTrue(settings.saved.isEmpty())
        model.setFilters(model.state.value.filters.copy(favoritesOnly = true, outletsOnly = true,
            tags = setOf(PersonalTags.comfortable, PersonalTags.uncrowded)))
        runCurrent()
        model.setCampus(Campus.SHAHE); runCurrent()
        assertTrue(model.state.value.filters.buildings.isEmpty())
        assertTrue(model.state.value.filters.favoritesOnly && model.state.value.filters.outletsOnly)
        assertEquals(setOf(PersonalTags.comfortable, PersonalTags.uncrowded), model.state.value.filters.tags)
        assertEquals(1, settings.saved.size)
    }

    @Test fun customTagDefinitionsPersistInRealModeButNotDemoMode() = checkModel { model, _, settings ->
        model.addCustomTag("靠窗"); runCurrent()
        assertEquals(setOf("靠窗"), settings.customTags.value)
        model.showDemo()
        model.addCustomTag("前排"); runCurrent()
        assertEquals(setOf("前排"), model.state.value.customTags)
        assertEquals(setOf("靠窗"), settings.customTags.value)
        model.openLogin()
        assertEquals(setOf("靠窗"), model.state.value.customTags)
    }

    @Test fun legacyRoomTagsBecomeAvailableCustomTerms() = checkModel { model, source, _ ->
        val room = snapshot(Campus.XITUCHENG).classrooms.first()
        source.profiles.value = mapOf(room.id to room.emptyProfile().copy(quiet = true))
        runCurrent()
        assertTrue(PersonalTags.legacyQuiet in model.state.value.customTags)
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
        override val filters = MutableStateFlow(SearchFilters())
        override val customTags = MutableStateFlow<Set<String>>(emptySet())
        val saved = mutableListOf<SearchFilters>()
        override suspend fun save(filters: SearchFilters) {
            saved += filters
            this.filters.value = filters
        }
        override suspend fun addTag(name: String) { customTags.value += name }
    }
}
