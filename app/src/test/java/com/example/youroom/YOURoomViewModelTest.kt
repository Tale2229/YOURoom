package com.example.youroom

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.example.youroom.data.*
import com.example.youroom.data.local.FilterSettings
import com.example.youroom.data.local.RememberedCredentials
import com.example.youroom.domain.*
import com.example.youroom.ui.YOURoomViewModel
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
class YOURoomViewModelTest {
    private val instant = Instant.parse("2026-10-12T00:10:00Z")
    private val clock = Clock.fixed(instant, SchoolZone)
    private fun snapshot(campus: Campus) = DemoData.snapshot(campus).copy(isDemo = false,
        queryDate = LocalDate.of(2026, 10, 12), fetchedAt = instant)

    private fun checkModel(authenticated: Boolean = true,
        setupSource: FakeSource.() -> Unit = {},
        testClock: Clock = clock,
        initialAppearance: AppearancePreferences = AppearancePreferences(),
        initialPlanning: PlanningPreferences = PlanningPreferences(),
        block: suspend TestScope.(YOURoomViewModel, FakeSource, FakeSettings) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource(authenticated).apply(setupSource)
        val settings = FakeSettings().apply { appearance.value = initialAppearance; planningPreferences.value = initialPlanning }
        val model = YOURoomViewModel(source, settings, testClock,StandardTestDispatcher(testScheduler))
        val store = ViewModelStore().apply { put("test", model) }
        try { runCurrent(); block(model, source, settings) }
        finally {
            // Disk work can still be returning from Dispatchers.IO after cancellation.
            // Await the whole ViewModel scope before removing its Main test dispatcher.
            val modelJob=model.viewModelScope.coroutineContext[Job]
            store.clear()
            try { modelJob?.join() } finally { Dispatchers.resetMain() }
        }
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

    @Test fun profileCatalogFilterAndSnapshotChangesCannotRegenerateOrClearAPlan() = checkModel { model, _, _ ->
        val draft=PlanningDraft(flexible=false,savedWindows=listOf(StudyWindow(480,720)))
        model.generatePlanning(draft);runCurrent()
        val old=model.planning.executions.value.getValue(Campus.XITUCHENG)
        assertNotNull(old.result)
        val room=model.state.value.snapshot!!.classrooms.first()
        model.toggleFavorite(room);runCurrent()
        model.toggleExcluded(room);runCurrent()
        model.saveProfile(model.state.value.profiles.getValue(room.id).copy(tags=setOf("靠窗")))
        model.addCustomTag("午后")
        model.setFilters(model.state.value.filters.copy(tags=setOf("靠窗")))
        model.refresh();runCurrent()
        model.setCampus(Campus.SHAHE);runCurrent()
        assertNull(model.planning.executions.value[Campus.SHAHE])
        model.setCampus(Campus.XITUCHENG);runCurrent()
        assertSame(old,model.planning.executions.value.getValue(Campus.XITUCHENG))
        model.generatePlanning(draft.copy(savedWindows=listOf(StudyWindow(600,720))));runCurrent()
        assertTrue(model.planning.executions.value.getValue(Campus.XITUCHENG).id>old.id)
    }

    @Test fun demoAndLoginTransitionsIsolateGeneratedPlans() = checkModel(false) { model, _, _ ->
        val draft=PlanningDraft(savedWindows=listOf(StudyWindow(480,720)))
        model.showDemo();model.generatePlanning(draft);runCurrent()
        assertTrue(model.planning.executions.value.isNotEmpty())
        model.openLogin();runCurrent()
        assertTrue(model.planning.executions.value.isEmpty())
    }

    @Test fun identicalConfirmedInputsReuseHistoryWithoutAppendingAndChangedFavoritesAreNewInputs() = checkModel(false) { model, _, _ ->
        model.showDemo()
        val draft=PlanningDraft(flexible=false,savedWindows=listOf(StudyWindow(480,540)))
        model.generatePlanning(draft);runCurrent()
        val first=model.planning.executions.value.getValue(Campus.XITUCHENG)
        assertEquals(1,model.planningHistory.value.size)
        assertEquals(first.input.key,model.planningHistory.value.single().inputKey)
        model.generatePlanning(draft.copy(start=900,end=1000));runCurrent()
        assertSame(first,model.planning.executions.value.getValue(Campus.XITUCHENG))
        assertEquals(1,model.planningHistory.value.size)
        model.planning.discard(Campus.XITUCHENG)
        model.generatePlanning(draft);runCurrent()
        val reused=model.planning.executions.value.getValue(Campus.XITUCHENG)
        assertTrue(reused.reused);assertEquals(first.result,reused.result)
        assertEquals(1,model.planningHistory.value.size)
        val room=model.state.value.campusSnapshot!!.classrooms.first()
        model.toggleFavorite(room);runCurrent()
        model.generatePlanning(draft);runCurrent()
        assertFalse(model.planning.executions.value.getValue(Campus.XITUCHENG).reused)
        assertEquals(2,model.planningHistory.value.size)
        model.toggleFavorite(room);runCurrent()
        model.generatePlanning(draft);runCurrent()
        assertTrue(model.planning.executions.value.getValue(Campus.XITUCHENG).reused)
        assertEquals(2,model.planningHistory.value.size)
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

    @Test fun rememberedAccountLoadsWithoutExposingPasswordInUiState() = checkModel(false,
        setupSource = { remembered = "dummy-remembered" }) { model, _, _ ->
        assertEquals("dummy-remembered", model.state.value.rememberedAccount)
        assertFalse(model.state.value.loggedIn)
        assertFalse(model.state.value.toString().contains("password"))
    }

    @Test fun manualLoginForwardsConsentAndTrimsAccount() = checkModel(false) { model, source, _ ->
        model.login("  dummy-account  ", "dummy-password", true); runCurrent()
        assertTrue(source.lastRemember)
        assertEquals("dummy-account", source.lastAccount)
        assertEquals("dummy-account", model.state.value.rememberedAccount)
        assertTrue(model.state.value.loggedIn)
        assertFalse(model.state.value.loggingIn)
        assertEquals(listOf(Campus.XITUCHENG), source.requests)
    }

    @Test fun legacyTwoArgumentLoginDoesNotOptIntoRemembering() = checkModel(false) { model, source, _ ->
        model.login("dummy-account", "dummy-password"); runCurrent()
        assertFalse(source.lastRemember)
        assertNull(model.state.value.rememberedAccount)
    }

    @Test fun logoutKeepsRememberedLoginForPrefillAndRetainsRoomMarks() = checkModel(
        setupSource = { remembered = "dummy-account" }) { model, source, _ ->
        val room = snapshot(Campus.XITUCHENG).classrooms.first()
        val profile = room.emptyProfile().copy(favorite = true, note = "保留")
        source.profiles.value = mapOf(room.id to profile); runCurrent()
        model.logout(); runCurrent()
        assertEquals("dummy-account",model.state.value.rememberedAccount)
        assertEquals("dummy-account",source.remembered)
        assertEquals("dummy-password",model.state.value.rememberedLogin!!.password)
        assertFalse(source.authenticated)
        assertFalse(model.state.value.showWorkspace)
        assertEquals(profile, model.state.value.profiles[room.id])
    }

    @Test fun endingDemoReloadsRememberedCredentialsWithoutALoginOrRefresh() = checkModel(false,
        setupSource = { remembered = "dummy-account" }) { model, source, _ ->
        assertEquals("dummy-password",model.state.value.rememberedLogin!!.password)
        model.showDemo(); runCurrent()
        assertNull(model.state.value.rememberedLogin)
        model.logout(); runCurrent()
        assertFalse(model.state.value.demo)
        assertFalse(model.state.value.showWorkspace)
        assertEquals("dummy-account",model.state.value.rememberedAccount)
        assertEquals("dummy-password",model.state.value.rememberedLogin!!.password)
        assertTrue(source.requests.isEmpty())
    }

    @Test fun unrememberedLoginAndEndingDemoNeverRefillThePassword() = checkModel(false) { model, _, _ ->
        model.login("dummy-account","dummy-password",false);runCurrent()
        model.logout();runCurrent()
        assertNull(model.state.value.rememberedAccount)
        assertNull(model.state.value.rememberedLogin)
        model.showDemo();runCurrent();model.logout();runCurrent()
        assertNull(model.state.value.rememberedAccount)
        assertNull(model.state.value.rememberedLogin)
        assertFalse(model.state.value.showWorkspace)
    }

    @Test fun rememberedPasswordsNeverAppearInStateDebugTextOrAuthenticatedState() = checkModel(false,
        setupSource = { remembered = "dummy-account" }) { model, _, _ ->
        assertFalse(model.state.value.toString().contains("dummy-password"))
        model.login("dummy-account","dummy-password",true);runCurrent()
        assertNull(model.state.value.rememberedLogin)
    }

    @Test fun canceledLateManualLoginCannotReopenWorkspace() = checkModel(false) { model, source, _ ->
        var late: kotlin.coroutines.Continuation<Unit>? = null
        source.loginAction = { suspendCoroutine { late = it } }
        model.login("dummy-account", "dummy-password", true); runCurrent()
        assertTrue(model.state.value.loggingIn)
        model.logout(); runCurrent()
        late!!.resume(Unit); runCurrent()
        assertFalse(model.state.value.loggedIn)
        assertFalse(model.state.value.loggingIn)
        assertNull(model.state.value.rememberedAccount)
        assertTrue(source.requests.isEmpty())
    }

    @Test fun backgroundRefreshSuccessDoesNotClearPlansOrChangeRememberChoice() = checkModel(
        setupSource = { remembered = "dummy-account" }) { model, _, _ ->
        model.generatePlanning(PlanningDraft(savedWindows = listOf(StudyWindow(480, 720)))); runCurrent()
        val existing = model.planning.executions.value.getValue(Campus.XITUCHENG)
        model.refresh(); runCurrent()
        assertSame(existing, model.planning.executions.value.getValue(Campus.XITUCHENG))
        assertEquals("dummy-account", model.state.value.rememberedAccount)
        assertTrue(model.state.value.loggedIn)
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

    @Test fun rapidFavoriteAndExclusionTogglesUseTheLatestProfileAndCanBeUndone() = checkModel { model, source, _ ->
        val room = model.state.value.snapshot!!.classrooms.first()
        val original = room.emptyProfile().copy(outlets = true, note = "保留备注", tags = setOf("靠窗"))
        model.saveProfile(original)
        model.toggleFavorite(room)
        model.toggleExcluded(room)
        assertEquals(original.copy(favorite = true, excluded = true), model.state.value.profiles[room.id])
        model.selectRoom(room.id)
        assertTrue(model.state.value.selectedRoom!!.profile.excluded)
        runCurrent()
        assertEquals(original.copy(favorite = true, excluded = true), source.profiles.value[room.id])
        model.toggleExcluded(room)
        runCurrent()
        assertEquals(original.copy(favorite = true), source.profiles.value[room.id])
    }

    @Test fun exclusionToggleAlwaysSavesTheSameMarkIndependentOfSelectedPeriods() = checkModel { model, source, _ ->
        val room = model.state.value.snapshot!!.classrooms.first()
        val original = room.emptyProfile().copy(favorite = true, outlets = true, note = "保留备注", tags = setOf("靠窗"))
        model.saveProfile(original)
        model.setFilters(SearchFilters())
        model.toggleExcluded(room)
        runCurrent()
        val excluded = original.copy(excluded = true)
        assertEquals(excluded, source.profiles.value[room.id])
        assertEquals(room.id, model.state.value.result.rooms.first().classroom.id)
        model.setFilters(SearchFilters(setOf(1)))
        runCurrent()
        assertFalse(model.state.value.result.rooms.any { it.classroom.id == room.id })
        assertEquals(excluded, source.profiles.value[room.id])
        model.toggleExcluded(room)
        runCurrent()
        assertEquals(original, source.profiles.value[room.id])
        assertEquals(room.id, model.state.value.result.rooms.first().classroom.id)
        model.toggleExcluded(room)
        runCurrent()
        assertEquals(excluded, source.profiles.value[room.id])
        model.setFilters(SearchFilters())
        runCurrent()
        assertEquals(room.id, model.state.value.result.rooms.first().classroom.id)
        assertEquals(excluded, source.profiles.value[room.id])
        model.toggleExcluded(room)
        runCurrent()
        assertEquals(original, source.profiles.value[room.id])
    }

    @Test fun demoExclusionIsIsolatedAndRestoredWhenReturningToTheCampus() = checkModel(false) { model, source, _ ->
        model.showDemo()
        val room = model.state.value.snapshot!!.classrooms.first()
        model.toggleExcluded(room)
        model.selectRoom(room.id)
        assertTrue(model.state.value.selectedRoom!!.profile.excluded)
        model.setCampus(Campus.SHAHE)
        assertNull(model.state.value.selectedRoom)
        model.setCampus(Campus.XITUCHENG)
        assertTrue(model.state.value.profiles.getValue(room.id).excluded)
        runCurrent()
        assertTrue(source.profileWrites.isEmpty())
    }

    @Test fun anExcludedSavedRoomOutsideTodaysSnapshotStillHasAManagementEntry() {
        val profile = RoomProfile("01:2-999", "2-999", "2", excluded = true, favorite = true, note = "旧记录")
        val state = com.example.youroom.ui.YOURoomState(snapshot = snapshot(Campus.XITUCHENG),
            today = LocalDate.of(2026, 10, 12), profiles = mapOf(profile.roomId to profile), selectedRoomId = profile.roomId)
        assertEquals(profile, state.selectedRoom!!.profile)
        assertTrue(state.selectedRoom!!.freeSlots.isEmpty())
        assertEquals(profile.name, state.selectedRoom!!.classroom.name)
        assertNull(state.copy(campus = Campus.SHAHE).selectedRoom)
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
        var remembered: String? = null
        var lastRemember = false
        var lastAccount: String? = null
        var loginAction: suspend () -> Unit = {}
        override suspend fun hasSession() = authenticated
        override suspend fun login(account: String, password: String) { authenticated = true }
        override suspend fun login(account: String, password: String, rememberPassword: Boolean) {
            lastAccount = account; lastRemember = rememberPassword
            loginAction()
            authenticated = true
            remembered = account.takeIf { rememberPassword }
        }
        override suspend fun rememberedAccount() = remembered
        override suspend fun rememberedCredentials() = remembered?.let { RememberedCredentials(it,"dummy-password") }
        override suspend fun logout() { authenticated = false }
        override suspend fun cached(campus: Campus): ClassroomSnapshot? = caches[campus]
        override suspend fun refresh(campus: Campus): ClassroomSnapshot { requests += campus; return loader(campus) }
        override suspend fun saveProfile(profile: RoomProfile) { profileWrites += profile; profileSaver(profile) }
        override suspend fun deleteProfile(roomId: String) { profileDeletes += roomId; profiles.value -= roomId }
    }

    private class FakeSettings : FilterSettings {
        override val planningPreferences = MutableStateFlow(PlanningPreferences())
        var planningGate: CompletableDeferred<Unit>? = null
        var planningFailure = false
        val planningSaved = mutableListOf<PlanningPreferences>()
        override suspend fun savePlanningPreferences(value: PlanningPreferences) {
            planningGate?.await()
            if (planningFailure) throw java.io.IOException("test")
            planningSaved += value; planningPreferences.value = value
        }
        override val appearance = MutableStateFlow(AppearancePreferences())
        var appearanceGate: CompletableDeferred<Unit>? = null
        var appearanceFailure = false
        val appearanceSaved = mutableListOf<AppearancePreferences>()
        override suspend fun saveAppearance(value: AppearancePreferences) {
            appearanceGate?.await()
            if (appearanceFailure) throw java.io.IOException("test")
            appearanceSaved += value; appearance.value = value
        }
        override val periodStrip=MutableStateFlow(PeriodStripPreferences())
        var stripGate: CompletableDeferred<Unit>?=null
        var stripFailure=false
        val stripSaved=mutableListOf<PeriodStripPreferences>()
        override suspend fun savePeriodStrip(value: PeriodStripPreferences) {
            stripGate?.await()
            if(stripFailure) throw java.io.IOException("test")
            stripSaved+=value;periodStrip.value=value
        }
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

    @Test fun savedDefaultCampusIsUsedBeforeTheFirstRequest() = checkModel(
        initialAppearance = AppearancePreferences(CampusTone.DUNHUANG, CampusTone.MATISSE, Campus.SHAHE)) { model, source, _ ->
        assertEquals(Campus.SHAHE, model.state.value.campus)
        assertEquals(listOf(Campus.SHAHE), source.requests)
        assertEquals(CampusTone.MATISSE, model.state.value.appearance.tone(Campus.SHAHE))
    }

    @Test fun darkModeIsGlobalPersistsAndNeverRefreshesRoomsOrChangesPlans() = checkModel(
        initialAppearance = AppearancePreferences(CampusTone.MATISSE, CampusTone.MONDRIAN)) { model, source, settings ->
        model.generatePlanning(PlanningDraft(flexible = false, savedWindows = listOf(StudyWindow(480, 720))))
        runCurrent()
        val before = model.state.value
        val requests = source.requests.toList()
        val plans = model.planning.executions.value
        model.setDarkMode(true); runCurrent()
        assertEquals(before.appearance.copy(darkMode = true), settings.appearance.value)
        assertEquals(true, model.state.value.appearance.darkMode)
        assertEquals(before.filters, model.state.value.filters)
        assertEquals(before.profiles, model.state.value.profiles)
        assertEquals(requests, source.requests); assertEquals(plans, model.planning.executions.value)
        model.showDemo(); runCurrent()
        assertEquals(true, model.state.value.appearance.darkMode)
        model.logout(); runCurrent()
        assertEquals(true, model.state.value.appearance.darkMode)
        model.setDarkMode(false); runCurrent()
        assertEquals(false, settings.appearance.value.darkMode)
    }

    @Test fun savedManualModeIsAvailableBeforeTheInitialWorkspaceIsDisplayed() = checkModel(
        initialAppearance = AppearancePreferences(darkMode = true)) { model, _, _ ->
        assertFalse(model.state.value.initializing)
        assertEquals(true, model.state.value.appearance.darkMode)
    }

    @Test fun changingAppearanceDoesNotSwitchCampusOrTouchSearchMarksOrExistingPlan() = checkModel { model, source, settings ->
        model.generatePlanning(PlanningDraft(flexible = false, savedWindows = listOf(StudyWindow(480, 720))))
        runCurrent()
        val plans = model.planning.executions.value
        val before = model.state.value
        val requests = source.requests.toList()
        model.setCampusTone(Campus.XITUCHENG, CampusTone.DUNHUANG)
        model.setDefaultCampus(Campus.SHAHE)
        runCurrent()
        assertEquals(Campus.XITUCHENG, model.state.value.campus)
        assertEquals(CampusTone.PINE, model.state.value.appearance.shaheTone)
        assertEquals(CampusTone.DUNHUANG, settings.appearance.value.westTone)
        assertEquals(Campus.SHAHE, settings.appearance.value.defaultCampus)
        assertEquals(before.filters, model.state.value.filters)
        assertEquals(before.profiles, model.state.value.profiles)
        assertEquals(plans, model.planning.executions.value)
        assertEquals(requests, source.requests)
        model.showDemo(); runCurrent()
        assertEquals(Campus.SHAHE, model.state.value.campus)
        model.openLogin(); runCurrent()
        assertEquals(Campus.SHAHE, model.state.value.campus)
        assertEquals(CampusTone.DUNHUANG, model.state.value.appearance.westTone)
    }

    @Test fun rapidToneAndDefaultChangesPreserveBothCampusesDespiteOlderEmissions() = checkModel { model, _, settings ->
        settings.appearanceGate = CompletableDeferred()
        model.setCampusTone(Campus.XITUCHENG, CampusTone.DUNHUANG); runCurrent()
        val first = model.state.value.appearance
        model.setCampusTone(Campus.SHAHE, CampusTone.MATISSE)
        model.setDefaultCampus(Campus.SHAHE); runCurrent()
        val latest = model.state.value.appearance
        settings.appearance.value = first; runCurrent()
        assertEquals(latest, model.state.value.appearance)
        settings.appearanceGate!!.complete(Unit); runCurrent()
        assertEquals(listOf(first, latest), settings.appearanceSaved)
        assertEquals(latest, settings.appearance.value)
        assertEquals(latest, model.state.value.appearance)
    }

    @Test fun failedAppearanceSaveRollsBackAndCanBeRetried() = checkModel { model, _, settings ->
        settings.appearanceFailure = true
        model.setCampusTone(Campus.XITUCHENG, CampusTone.DUNHUANG); runCurrent()
        assertEquals(AppearancePreferences(), model.state.value.appearance)
        assertTrue(model.state.value.error!!.contains("外观设置保存失败"))
        settings.appearanceFailure = false
        model.setCampusTone(Campus.XITUCHENG, CampusTone.DUNHUANG); runCurrent()
        assertEquals(CampusTone.DUNHUANG, settings.appearance.value.westTone)
    }

    @Test fun stripDefaultsAreOriginalAndChangesDoNotTouchSearchOrPlans() = checkModel { model, source, settings ->
        assertEquals(PeriodStripPreferences(),model.state.value.periodStrip)
        val before=model.state.value.filters
        val requests=source.requests.toList()
        val pref=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL,true)
        model.setPeriodStrip(pref)
        assertEquals(pref,model.state.value.periodStrip)
        runCurrent()
        assertEquals(listOf(pref),settings.stripSaved)
        assertEquals(before,model.state.value.filters);assertEquals(requests,source.requests)
        assertTrue(model.planning.executions.value.isEmpty())
        model.setCampus(Campus.SHAHE);runCurrent()
        assertEquals(pref,model.state.value.periodStrip)
        model.showDemo();runCurrent();assertEquals(pref,model.state.value.periodStrip)
        model.openLogin();runCurrent();assertEquals(pref,model.state.value.periodStrip)
    }

    @Test fun rapidStripChangesAreSerializedAndOlderEmissionsCannotUndoTheLatest() = checkModel { model, _, settings ->
        val first=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL)
        val last=PeriodStripPreferences(PeriodStripLayout.EQUAL,true)
        settings.stripGate=CompletableDeferred()
        model.setPeriodStrip(first);runCurrent()
        model.setPeriodStrip(last);runCurrent()
        settings.periodStrip.value=first;runCurrent()
        assertEquals(last,model.state.value.periodStrip)
        settings.stripGate!!.complete(Unit);runCurrent()
        assertEquals(listOf(first,last),settings.stripSaved)
        assertEquals(last,settings.periodStrip.value);assertEquals(last,model.state.value.periodStrip)
    }

    @Test fun failedStripSaveRollsBackToSavedValueAndCanBeRetried() = checkModel { model, _, settings ->
        val pref=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL,true)
        settings.stripFailure=true
        model.setPeriodStrip(pref);runCurrent()
        assertEquals(PeriodStripPreferences(),model.state.value.periodStrip)
        assertTrue(model.state.value.error!!.contains("时段条设置保存失败"))
        settings.stripFailure=false;model.setPeriodStrip(pref);runCurrent()
        assertEquals(pref,model.state.value.periodStrip)
    }

    @Test fun planningPreferencesDoNotRequestDataOrClearAnExistingPlan() = checkModel { model, source, settings ->
        model.generatePlanning(PlanningDraft(flexible=false,savedWindows=listOf(StudyWindow(480,720)))); runCurrent()
        val before=model.planning.executions.value
        val requests=source.requests.toList()
        val preferences=CampusPlanningPreferences(PlanningDirection.FAVORITES,false,
            listOf(TimeTemplate("morning","上午",listOf(StudyWindow(480,720)))))
        model.setPlanningPreferences(Campus.XITUCHENG,preferences); runCurrent()
        assertEquals(preferences,settings.planningPreferences.value.forCampus(Campus.XITUCHENG))
        assertEquals(CampusPlanningPreferences(),settings.planningPreferences.value.forCampus(Campus.SHAHE))
        assertEquals(before,model.planning.executions.value); assertEquals(requests,source.requests)
    }

    @Test fun storedPlanningDefaultsAreReadyBeforeInitializationFinishes() = checkModel(
        initialPlanning=PlanningPreferences().withCampus(Campus.XITUCHENG,
            CampusPlanningPreferences(PlanningDirection.BUILDINGS,false))) { model, _, settings ->
        assertFalse(model.state.value.initializing)
        assertEquals(PlanningDirection.BUILDINGS,model.state.value.planningPreferences.forCampus(Campus.XITUCHENG).direction)
        assertFalse(model.state.value.planningPreferences.forCampus(Campus.XITUCHENG).flexible)
        assertTrue(settings.planningSaved.isEmpty())
    }

    @Test fun rapidPlanningPreferenceChangesRetainBothCampusesAndIgnoreOlderEmissions() = checkModel { model, _, settings ->
        settings.planningGate=CompletableDeferred()
        model.setPlanningPreferences(Campus.XITUCHENG,CampusPlanningPreferences(flexible=false)); runCurrent()
        val first=model.state.value.planningPreferences
        model.setPlanningPreferences(Campus.SHAHE,CampusPlanningPreferences(PlanningDirection.BUILDINGS)); runCurrent()
        val latest=model.state.value.planningPreferences
        settings.planningPreferences.value=first; runCurrent()
        assertEquals(latest,model.state.value.planningPreferences)
        settings.planningGate!!.complete(Unit); runCurrent()
        assertEquals(listOf(first,latest),settings.planningSaved)
        assertEquals(latest,settings.planningPreferences.value)
    }

    @Test fun demoSettingsNeverWriteAndFormalPreferencesReturnBeforeLogin() = checkModel(false,
        initialAppearance=AppearancePreferences(CampusTone.MATISSE,CampusTone.DUNHUANG,Campus.SHAHE,true),
        initialPlanning=PlanningPreferences().withCampus(Campus.SHAHE,
            CampusPlanningPreferences(PlanningDirection.FAVORITES,false,
                listOf(TimeTemplate("real","正式",listOf(StudyWindow(480,600))))))) { model, source, settings ->
        val realStrip=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL,true)
        settings.periodStrip.value=realStrip;runCurrent()
        val before=model.state.value
        model.showDemo()
        model.setCampusTone(Campus.XITUCHENG,CampusTone.MONDRIAN)
        model.setCampusTone(Campus.SHAHE,CampusTone.RED_BEAN)
        model.setDarkMode(false)
        model.setDefaultCampus(Campus.XITUCHENG)
        model.setPeriodStrip(PeriodStripPreferences())
        for (campus in Campus.entries) model.setPlanningPreferences(campus,CampusPlanningPreferences(
            PlanningDirection.BUILDINGS,false,listOf(TimeTemplate("temporary","临时",listOf(StudyWindow(600,720))))))
        model.setFilters(model.state.value.filters.copy(outletsOnly=true,buildings=setOf("演示楼")))
        model.addCustomTag("演示标签")
        model.toggleFavorite(model.state.value.snapshot!!.classrooms.first())
        runCurrent()
        assertTrue(model.state.value.demo)
        assertFalse(model.state.value.appearance.darkMode!!)
        assertEquals(CampusTone.RED_BEAN,model.state.value.appearance.shaheTone)
        assertTrue(settings.appearanceSaved.isEmpty());assertTrue(settings.stripSaved.isEmpty())
        assertTrue(settings.planningSaved.isEmpty());assertTrue(settings.saved.isEmpty())
        assertTrue(settings.savedBuildings.isEmpty());assertTrue(source.profileWrites.isEmpty())
        assertEquals(before.appearance,settings.appearance.value)
        assertEquals(before.planningPreferences,settings.planningPreferences.value)
        assertEquals(realStrip,settings.periodStrip.value)
        model.openLogin();runCurrent()
        assertFalse(model.state.value.demo)
        assertEquals(before.appearance,model.state.value.appearance)
        assertEquals(before.planningPreferences,model.state.value.planningPreferences)
        assertEquals(realStrip,model.state.value.periodStrip)
        assertEquals(Campus.SHAHE,model.state.value.campus)
        assertFalse(model.state.value.filters.outletsOnly)
        assertFalse("演示标签" in model.state.value.tagOrder)
        assertTrue(model.state.value.profiles.isEmpty())
        model.login("dummy","dummy");runCurrent()
        assertTrue(model.state.value.loggedIn);assertFalse(model.state.value.snapshot!!.isDemo)
        assertEquals(before.appearance,model.state.value.appearance)
        assertEquals(before.planningPreferences,model.state.value.planningPreferences)
        assertEquals(listOf(Campus.SHAHE),source.requests)
    }

    @Test fun delayedRealPreferenceWritesCannotReplaceDemoChoices() = checkModel(false) { model, _, settings ->
        settings.appearanceGate=CompletableDeferred();settings.stripGate=CompletableDeferred()
        settings.planningGate=CompletableDeferred()
        val realStrip=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL,true)
        model.setCampusTone(Campus.XITUCHENG,CampusTone.DUNHUANG)
        model.setPeriodStrip(realStrip)
        model.setPlanningPreferences(Campus.XITUCHENG,CampusPlanningPreferences(flexible=false))
        runCurrent()
        val real=model.state.value
        model.showDemo()
        model.setCampusTone(Campus.XITUCHENG,CampusTone.MATISSE)
        model.setPeriodStrip(PeriodStripPreferences())
        model.setPlanningPreferences(Campus.XITUCHENG,CampusPlanningPreferences(PlanningDirection.FAVORITES))
        val demo=model.state.value
        settings.appearanceGate!!.complete(Unit);settings.stripGate!!.complete(Unit)
        settings.planningGate!!.complete(Unit);runCurrent()
        assertEquals(demo.appearance,model.state.value.appearance)
        assertEquals(demo.periodStrip,model.state.value.periodStrip)
        assertEquals(demo.planningPreferences,model.state.value.planningPreferences)
        assertEquals(listOf(real.appearance),settings.appearanceSaved)
        assertEquals(listOf(real.periodStrip),settings.stripSaved)
        assertEquals(listOf(real.planningPreferences),settings.planningSaved)
        model.openLogin();runCurrent()
        assertEquals(real.appearance,model.state.value.appearance)
        assertEquals(real.periodStrip,model.state.value.periodStrip)
        assertEquals(real.planningPreferences,model.state.value.planningPreferences)
    }

    @Test fun failedRealWritesDoNotRollBackDemoAndRestoreSavedValuesOnExit() = checkModel(false) { model, _, settings ->
        val saved=model.state.value
        settings.appearanceGate=CompletableDeferred();settings.appearanceFailure=true
        settings.stripGate=CompletableDeferred();settings.stripFailure=true
        settings.planningGate=CompletableDeferred();settings.planningFailure=true
        model.setCampusTone(Campus.XITUCHENG,CampusTone.DUNHUANG)
        model.setPeriodStrip(PeriodStripPreferences(showNumbers=true))
        model.setPlanningPreferences(Campus.XITUCHENG,CampusPlanningPreferences(flexible=false))
        runCurrent();model.showDemo()
        model.setCampusTone(Campus.XITUCHENG,CampusTone.MONDRIAN)
        val demo=model.state.value
        settings.appearanceGate!!.complete(Unit);settings.stripGate!!.complete(Unit)
        settings.planningGate!!.complete(Unit);runCurrent()
        assertEquals(demo.appearance,model.state.value.appearance)
        assertEquals(demo.periodStrip,model.state.value.periodStrip)
        assertEquals(demo.planningPreferences,model.state.value.planningPreferences)
        assertNull(model.state.value.error)
        model.openLogin();runCurrent()
        assertEquals(saved.appearance,model.state.value.appearance)
        assertEquals(saved.periodStrip,model.state.value.periodStrip)
        assertEquals(saved.planningPreferences,model.state.value.planningPreferences)
    }

    @Test fun incomingRealPreferenceChangesStayOutsideTheActiveDemo() = checkModel(false) { model, _, settings ->
        model.showDemo()
        val demo=model.state.value
        val appearance=AppearancePreferences(CampusTone.MORANDI,CampusTone.BURGUNDY,Campus.SHAHE,true)
        val strip=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL,true)
        val planning=PlanningPreferences().withCampus(Campus.SHAHE,CampusPlanningPreferences(flexible=false))
        settings.appearance.value=appearance;settings.periodStrip.value=strip;settings.planningPreferences.value=planning
        runCurrent()
        assertEquals(demo.appearance,model.state.value.appearance)
        assertEquals(demo.periodStrip,model.state.value.periodStrip)
        assertEquals(demo.planningPreferences,model.state.value.planningPreferences)
        model.openLogin();runCurrent()
        assertEquals(appearance,model.state.value.appearance)
        assertEquals(strip,model.state.value.periodStrip)
        assertEquals(planning,model.state.value.planningPreferences)
        assertEquals(Campus.SHAHE,model.state.value.campus)
    }

    @Test fun demoTemplateIsLegalForBothCampusesAndResetsWithoutPersistence() = checkModel(false) { model, _, settings ->
        model.showDemo()
        for (campus in Campus.entries) {
            model.setCampus(campus)
            val template=model.state.value.planningPreferences.forCampus(campus).templates.single()
            assertEquals("演示",template.name)
            assertTrue(TimeTemplates.valid(template.windows))
            assertEquals(3,template.windows.size)
            model.generatePlanning(PlanningDraft(savedWindows=template.windows));runCurrent()
            assertTrue(model.planning.executions.value.getValue(campus).result!!.options.isNotEmpty())
        }
        model.setPlanningPreferences(Campus.XITUCHENG,CampusPlanningPreferences())
        model.setCampusTone(Campus.XITUCHENG,CampusTone.MONDRIAN)
        model.openLogin();model.showDemo();runCurrent()
        assertEquals(DemoData.planningPreferences(),model.state.value.planningPreferences)
        assertEquals(AppearancePreferences(),model.state.value.appearance)
        assertTrue(settings.planningSaved.isEmpty());assertTrue(settings.appearanceSaved.isEmpty())
    }

    @Test fun loginDirectlyFromDemoAlsoRestoresTheRealSettings() = checkModel(false) { model, _, settings ->
        model.showDemo();model.setDarkMode(true);model.setDefaultCampus(Campus.SHAHE)
        model.login("dummy","dummy");runCurrent()
        assertTrue(model.state.value.loggedIn);assertFalse(model.state.value.demo)
        assertEquals(Campus.XITUCHENG,model.state.value.campus)
        assertFalse(model.state.value.snapshot!!.isDemo)
        assertEquals(settings.appearance.value,model.state.value.appearance)
        assertEquals(settings.planningPreferences.value,model.state.value.planningPreferences)
    }

    @Test fun failedPlanningPreferenceSaveRollsBackAndCanBeRetried() = checkModel { model, _, settings ->
        settings.planningFailure=true
        model.setPlanningPreferences(Campus.XITUCHENG,CampusPlanningPreferences(flexible=false)); runCurrent()
        assertEquals(CampusPlanningPreferences(),model.state.value.planningPreferences.forCampus(Campus.XITUCHENG))
        assertTrue(model.state.value.error!!.contains("规划偏好保存失败"))
        settings.planningFailure=false
        model.setPlanningPreferences(Campus.XITUCHENG,CampusPlanningPreferences(flexible=false)); runCurrent()
        assertFalse(model.state.value.planningPreferences.forCampus(Campus.XITUCHENG).flexible)
    }
}
