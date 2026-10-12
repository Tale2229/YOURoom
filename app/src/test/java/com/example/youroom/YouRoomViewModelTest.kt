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

    @Test fun switchSharesTimeAndTagsAndRestoresCampusBuildings() = checkModel { model, source, _ ->
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
        assertEquals(setOf("2"), model.state.value.filters.buildings)
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
        assertEquals(setOf("2"), model.state.value.filters.buildings)
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

    @Test fun pagePrewarmingSnapshotsStayScopedAndAreIsolatedFromDemo() = checkModel { model, _, _ ->
        assertEquals(setOf(Campus.XITUCHENG), model.state.value.campusSnapshots.keys)
        model.setCampus(Campus.SHAHE); runCurrent()
        assertEquals(setOf(Campus.XITUCHENG, Campus.SHAHE), model.state.value.campusSnapshots.keys)
        assertEquals("01", model.state.value.campusSnapshots.getValue(Campus.XITUCHENG).campusId)
        assertEquals("04", model.state.value.campusSnapshots.getValue(Campus.SHAHE).campusId)
        model.showDemo()
        assertTrue(model.state.value.campusSnapshots.values.all { it.isDemo })
        model.openLogin()
        assertTrue(model.state.value.campusSnapshots.values.none { it.isDemo })
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
        assertEquals(snapshot(Campus.XITUCHENG).classrooms.map { it.id }.toSet(),
            model.state.value.result.rooms.map { it.classroom.id }.toSet())
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

    @Test fun bothCampusBuildingsPersistAndRestoreWhileTimesRemainShared() = checkModel { model, _, settings ->
        model.setFilters(model.state.value.filters.copy(buildings = setOf("2", "3"), selectedSlots = setOf(1, 6)))
        runCurrent()
        model.setCampus(Campus.SHAHE); runCurrent()
        assertTrue(model.state.value.filters.buildings.isEmpty())
        model.setFilters(model.state.value.filters.copy(buildings = setOf("教一"), selectedSlots = setOf(6)))
        runCurrent()
        model.setCampus(Campus.XITUCHENG)
        assertEquals(setOf("2", "3"), model.state.value.filters.buildings)
        assertEquals(setOf(6), model.state.value.filters.selectedSlots)
        model.setCampus(Campus.SHAHE)
        assertEquals(setOf("教一"), model.state.value.filters.buildings)
        assertEquals(listOf(Campus.XITUCHENG to setOf("2", "3"), Campus.SHAHE to setOf("教一")), settings.savedBuildings)
    }

    @Test fun deletingTermCancelsItsFilterButNeverDeletesRoomAnnotations() = checkModel { model, source, settings ->
        val room = snapshot(Campus.XITUCHENG).classrooms.first()
        val profile = room.emptyProfile().copy(outlets = true, tags = setOf(PersonalTags.comfortable))
        source.profiles.value = mapOf(room.id to profile); runCurrent()
        model.setFilters(model.state.value.filters.copy(outletsOnly = true, tags = setOf(PersonalTags.comfortable)))
        runCurrent()
        model.removePersonalTag(PersonalTags.outlets); runCurrent()
        assertFalse(model.state.value.filters.outletsOnly)
        assertFalse(PersonalTags.outlets in model.state.value.tagOrder)
        assertTrue(source.profiles.value.getValue(room.id).outlets)
        assertFalse(PersonalTags.outlets in settings.catalog.value.available())
        model.removePersonalTag(PersonalTags.favorite); runCurrent()
        assertEquals(PersonalTags.favorite, model.state.value.tagOrder.first())
        model.addCustomTag(PersonalTags.outlets); runCurrent()
        assertTrue(PersonalTags.outlets in model.state.value.tagOrder)
        model.removePersonalTag(PersonalTags.comfortable); runCurrent()
        assertTrue(model.state.value.filters.tags.isEmpty())
        assertTrue(PersonalTags.comfortable in source.profiles.value.getValue(room.id).tags)
    }

    @Test fun orderedTermsPersistAndDemoEditingIsIsolated() = checkModel { model, _, settings ->
        model.addCustomTag("靠窗"); runCurrent()
        val order = listOf("已收藏", "靠窗", "人少", "有插座", "桌椅舒适")
        model.reorderPersonalTags(order); runCurrent()
        assertEquals(order, model.state.value.tagOrder)
        assertEquals(order, settings.catalog.value.available())
        model.showDemo()
        model.removePersonalTag(PersonalTags.outlets)
        model.addCustomTag("前排")
        model.reorderPersonalTags(listOf("人少", "前排", "已收藏", "桌椅舒适")); runCurrent()
        assertEquals(PersonalTags.favorite, model.state.value.tagOrder.first())
        assertEquals(order, settings.catalog.value.available())
        model.openLogin()
        assertEquals(order, model.state.value.tagOrder)
    }

    @Test fun emptySelectionSurvivesFirstQueryOnOtherCampusAndReturn() = checkModel { model, _, _ ->
        model.setFilters(model.state.value.filters.copy(selectedSlots = emptySet()))
        model.setCampus(Campus.SHAHE); runCurrent()
        assertTrue(model.state.value.filters.selectedSlots.isEmpty())
        assertEquals(snapshot(Campus.SHAHE).classrooms.map { it.id }.toSet(),
            model.state.value.result.rooms.map { it.classroom.id }.toSet())
        model.setCampus(Campus.XITUCHENG); runCurrent()
        assertTrue(model.state.value.filters.selectedSlots.isEmpty())
        assertEquals(snapshot(Campus.XITUCHENG).classrooms.map { it.id }.toSet(),
            model.state.value.result.rooms.map { it.classroom.id }.toSet())
    }

    @Test fun sharedAttributesAndSeparateBuildingsPersistWithoutExtraWritesOnSwitch() = checkModel { model, _, settings ->
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
        assertEquals(setOf("靠窗"), settings.catalog.value.available().filterNot { it in PersonalTags.builtIn }.toSet())
        model.showDemo()
        model.addCustomTag("前排"); runCurrent()
        assertEquals(setOf("前排"), model.state.value.customTags)
        assertEquals(setOf("靠窗"), settings.catalog.value.available().filterNot { it in PersonalTags.builtIn }.toSet())
        model.openLogin()
        assertEquals(setOf("靠窗"), model.state.value.customTags)
    }

    @Test fun legacyRoomTagsBecomeAvailableCustomTerms() = checkModel { model, source, _ ->
        val room = snapshot(Campus.XITUCHENG).classrooms.first()
        source.profiles.value = mapOf(room.id to room.emptyProfile().copy(quiet = true))
        runCurrent()
        assertTrue(PersonalTags.legacyQuiet in model.state.value.customTags)
    }

    @Test fun rapidProfileEditsAreImmediateAndOlderDatabaseValuesCannotUndoThem() = checkModel { model, source, _ ->
        val room = snapshot(Campus.XITUCHENG).classrooms.first()
        val gate = CompletableDeferred<Unit>()
        source.profileSaver = { profile ->
            if (source.profileWrites.size == 1) gate.await()
            source.profiles.value += profile.roomId to profile
        }
        model.selectRoom(room.id)
        val first = room.emptyProfile().copy(favorite = true, note = "初稿")
        model.saveProfile(first)
        assertEquals(first, model.state.value.profiles[room.id])
        runCurrent()
        model.saveProfile(first.copy(outlets = true, note = "第二稿"))
        val latest = first.copy(outlets = true, note = "最终备注 ", tags = setOf(PersonalTags.comfortable))
        model.saveProfile(latest)
        source.profiles.value = mapOf(room.id to first)
        runCurrent()
        assertEquals(latest, model.state.value.profiles[room.id])
        assertEquals(room.id, model.state.value.selectedRoomId)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(first, latest), source.profileWrites)
        assertEquals(latest, source.profiles.value[room.id])
        assertEquals(latest, model.state.value.profiles[room.id])
        assertEquals(room.id, model.state.value.selectedRoomId)
    }

    @Test fun rapidFavoriteTapsUseTheLatestLocalStateAndPreserveOtherMarks() = checkModel { model, source, _ ->
        val room = snapshot(Campus.XITUCHENG).classrooms.first()
        val profile = room.emptyProfile().copy(note = "靠窗", outlets = true, tags = setOf("人少"))
        source.profiles.value = mapOf(room.id to profile); runCurrent()
        model.toggleFavorite(room)
        assertTrue(model.state.value.profiles.getValue(room.id).favorite)
        model.toggleFavorite(room)
        assertEquals(profile, model.state.value.profiles[room.id])
        runCurrent()
        assertEquals(profile, source.profiles.value[room.id])
    }

    @Test fun clearingAProfileIsSerializedAfterAnInFlightSave() = checkModel { model, source, _ ->
        val room = snapshot(Campus.XITUCHENG).classrooms.first()
        val gate = CompletableDeferred<Unit>()
        source.profileSaver = { profile -> gate.await(); source.profiles.value += profile.roomId to profile }
        model.saveProfile(room.emptyProfile().copy(note = "编辑中")); runCurrent()
        model.clearProfile(room.id)
        assertFalse(model.state.value.profiles.containsKey(room.id))
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(room.id), source.profileDeletes)
        assertFalse(source.profiles.value.containsKey(room.id))
        assertFalse(model.state.value.profiles.containsKey(room.id))
    }

    @Test fun failedRealtimeSaveKeepsTheEditAndCanBeRetriedWithoutClosingDetails() = checkModel { model, source, _ ->
        val room = snapshot(Campus.XITUCHENG).classrooms.first()
        model.selectRoom(room.id)
        val profile = room.emptyProfile().copy(favorite = true, note = "保留输入")
        source.profileSaver = { throw java.io.IOException("Simulated database failure") }
        model.saveProfile(profile); runCurrent()
        assertEquals(profile, model.state.value.profiles[room.id])
        assertTrue(model.state.value.error!!.contains("保存失败"))
        assertEquals(room.id, model.state.value.selectedRoomId)
        assertTrue(source.profiles.value.isEmpty())
        source.profileSaver = { source.profiles.value += it.roomId to it }
        model.saveProfile(profile.copy(note = "重试后的输入")); runCurrent()
        assertEquals("重试后的输入", source.profiles.value.getValue(room.id).note)
    }

    @Test fun aPendingRealSaveCannotModifyDemoProfiles() = checkModel { model, source, _ ->
        val room = snapshot(Campus.XITUCHENG).classrooms.first()
        val gate = CompletableDeferred<Unit>()
        source.profileSaver = { profile -> gate.await(); source.profiles.value += profile.roomId to profile }
        val profile = room.emptyProfile().copy(outlets = true, note = "真实偏好")
        model.saveProfile(profile); runCurrent()
        model.showDemo()
        gate.complete(Unit); runCurrent()
        assertTrue(model.state.value.demo)
        assertTrue(model.state.value.profiles.isEmpty())
        model.openLogin()
        assertEquals(profile, model.state.value.profiles[room.id])
    }

    @Test fun closingDetailsDoesNotCancelTheLatestNoteWrite() = checkModel { model, source, _ ->
        val room = snapshot(Campus.XITUCHENG).classrooms.first()
        val gate = CompletableDeferred<Unit>()
        source.profileSaver = { profile -> gate.await(); source.profiles.value += profile.roomId to profile }
        model.selectRoom(room.id)
        val profile = room.emptyProfile().copy(note = "关闭前的最后一笔")
        model.saveProfile(profile); runCurrent()
        model.selectRoom(null)
        gate.complete(Unit); runCurrent()
        assertNull(model.state.value.selectedRoomId)
        assertEquals(profile, source.profiles.value[room.id])
    }

    private inner class FakeSource(var authenticated: Boolean) : ClassroomDataSource {
        override val profiles = MutableStateFlow<Map<String, RoomProfile>>(emptyMap())
        val requests = mutableListOf<Campus>()
        val caches = mutableMapOf<Campus, ClassroomSnapshot>()
        val profileWrites = mutableListOf<RoomProfile>()
        val profileDeletes = mutableListOf<String>()
        var profileSaver: suspend (RoomProfile) -> Unit = { profiles.value += it.roomId to it }
        var loader: suspend (Campus) -> ClassroomSnapshot = { snapshot(it) }
        override suspend fun hasSession() = authenticated
        override suspend fun login(account: String, password: String) { authenticated = true }
        override suspend fun logout() { authenticated = false }
        override suspend fun cached(campus: Campus): ClassroomSnapshot? = caches[campus]
        override suspend fun refresh(campus: Campus): ClassroomSnapshot { requests += campus; return loader(campus) }
        override suspend fun saveProfile(profile: RoomProfile) { profileWrites += profile; profileSaver(profile) }
        override suspend fun deleteProfile(roomId: String) { profileDeletes += roomId; profiles.value -= roomId }
    }

    private class FakeSettings : FilterSettings {
        override val filters = MutableStateFlow(SearchFilters())
        override val catalog = MutableStateFlow(TagCatalog())
        override val buildings = MutableStateFlow(Campus.entries.associateWith { emptySet<String>() })
        val saved = mutableListOf<SearchFilters>()
        var saveGate: CompletableDeferred<Unit>? = null
        override suspend fun save(filters: SearchFilters) {
            saveGate?.await()
            saved += filters
            this.filters.value = filters
        }
        val savedBuildings = mutableListOf<Pair<Campus, Set<String>>>()
        override suspend fun saveBuildings(campus: Campus, names: Set<String>) {
            savedBuildings += campus to names
            buildings.value += campus to names
        }
        override suspend fun addTag(name: String) { catalog.value = catalog.value.added(name) }
        override suspend fun removeTag(name: String) {
            catalog.value = catalog.value.deleted(name)
            filters.value = filters.value.withoutTerm(name)
        }
        override suspend fun reorderTags(names: List<String>) { catalog.value = catalog.value.reordered(names) }
    }

    @Test fun immediateFilteringIsNotOverwrittenByOlderPreferenceEmissions() = checkModel { model, _, settings ->
        settings.saveGate = CompletableDeferred()
        model.setFilters(model.state.value.filters.copy(favoritesOnly = true)); runCurrent()
        settings.filters.value = SearchFilters(outletsOnly = true); runCurrent()
        assertTrue(model.state.value.filters.favoritesOnly)
        assertFalse(model.state.value.filters.outletsOnly)
        settings.saveGate!!.complete(Unit); runCurrent()
        assertTrue(model.state.value.filters.favoritesOnly)
        assertFalse(model.state.value.filters.outletsOnly)
    }
}
