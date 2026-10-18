package com.example.youroom.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.youroom.AppContainer
import com.example.youroom.data.*
import com.example.youroom.data.local.FilterSettings
import com.example.youroom.data.local.RememberedCredentials
import com.example.youroom.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

data class YOURoomState(
    val initializing: Boolean = true,
    val loggedIn: Boolean = false,
    val loggingIn: Boolean = false,
    val rememberedAccount: String? = null,
    // Memory only, never saveable; the credential holder redacts its toString output.
    val rememberedLogin: RememberedCredentials? = null,
    val refreshing: Boolean = false,
    val snapshot: ClassroomSnapshot? = null,
    val campusSnapshots: Map<Campus, ClassroomSnapshot> = emptyMap(),
    val fromCache: Boolean = false,
    val viewingCache: Boolean = false,
    val demo: Boolean = false,
    val campus: Campus = Campus.XITUCHENG,
    val cachedCampuses: Set<Campus> = emptySet(),
    val today: LocalDate = LocalDate.now(SchoolZone),
    val filters: SearchFilters = SearchFilters(),
    val profiles: Map<String, RoomProfile> = emptyMap(),
    val tagCatalog: TagCatalog = TagCatalog(),
    val error: String? = null,
    val selectedRoomId: String? = null,
    val periodStrip: PeriodStripPreferences = PeriodStripPreferences(),
    val appearance: AppearancePreferences = AppearancePreferences(),
    val planningPreferences: PlanningPreferences = PlanningPreferences(),
) {
    val tagOrder: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        tagCatalog.available(profiles.values.flatMap { it.allTags })
    }
    val customTags: Set<String> get() = tagOrder.filterNot { it in PersonalTags.builtIn }.toSet()
    val showWorkspace: Boolean get() = loggedIn || demo || viewingCache
    val campusSnapshot: ClassroomSnapshot? get() = snapshot?.takeIf {
        it.campusId == campus.dataKey && it.isDemo == demo
    }
    val campusProfiles: List<RoomProfile> get() = profiles.values.filter { it.roomId.startsWith("${campus.dataKey}:") }
    val stale: Boolean get() = campusSnapshot?.isCurrentOn(today) == false
    val result: SearchResult get() = campusSnapshot?.takeUnless { stale }?.let {
        ClassroomFilter.search(it, filters, profiles)
    } ?: SearchResult(emptyList(), emptyList(), false)
    val selectedRoom: RoomMatch? get() {
        val id = selectedRoomId ?: return null
        if (!id.startsWith("${campus.dataKey}:")) return null
        // Demo snapshots must not leak into an authenticated or cached workspace.
        if (snapshot?.isDemo == true && !demo) return null
        val profile = profiles[id]
        val room = campusSnapshot?.index?.roomsById?.get(id)
            ?: profile?.let { Classroom(it.roomId, it.name, it.building, null) } ?: return null
        return RoomMatch(room, profile ?: room.emptyProfile(),
            if (stale) emptySet() else campusSnapshot?.index?.freeSlotsByRoom?.get(id).orEmpty())
    }
}

private data class CampusWorkspace(
    val snapshot: ClassroomSnapshot? = null,
    val fromCache: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

private data class ProfileChange(val id: String, val profile: RoomProfile?, val campus: Campus,
    val revision: Long, val persisted: Boolean = false)

class YOURoomViewModel(
    private val repository: ClassroomDataSource,
    private val settings: FilterSettings,
    private val clock: Clock = Clock.system(SchoolZone),
    planningDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow(YOURoomState(today = today()))
    val state: StateFlow<YOURoomState> = mutableState.asStateFlow()
    val planning = PlanningSession(viewModelScope,planningDispatcher)

    fun cancelPlanning() { planning.cancel(state.value.campus) }

    fun generatePlanning(draft: PlanningDraft) {
        updateToday()
        planning.generate(state.value,draft)
    }
    private var realWorkspaces = Campus.entries.associateWith { CampusWorkspace() }
    private var demoWorkspaces = emptyMap<Campus, CampusWorkspace>()
    private var storedProfiles: Map<String, RoomProfile> = emptyMap()
    private val pendingProfileChanges = linkedMapOf<String, ProfileChange>()
    private val queuedProfileChanges = linkedMapOf<String, ProfileChange>()
    private var profileRevision = 0L
    private var profileWriteJob: Job? = null
    private var storedCatalog = TagCatalog()
    private var pendingCatalog: TagCatalog? = null
    private var pendingSharedFilters: SearchFilters? = null
    private var realBuildings = Campus.entries.associateWith { emptySet<String>() }
    private var demoBuildings = Campus.entries.associateWith { emptySet<String>() }
    private val pendingBuildings = mutableMapOf<Campus, Set<String>>()
    private var realFilters = SearchFilters()
    private var demoFilters = SearchFilters()
    private var realSelectionDate: LocalDate? = null
    private var demoSelectionDate: LocalDate? = null
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    private var loginJob: Job? = null
    private var loginGeneration = 0L
    private var persistedStrip=PeriodStripPreferences()
    private var pendingStrip: PeriodStripPreferences?=null
    private val stripWrites=Channel<PeriodStripPreferences>(Channel.CONFLATED)
    private val appearanceReady = kotlinx.coroutines.CompletableDeferred<Unit>()
    private var persistedAppearance = AppearancePreferences()
    private var pendingAppearance: AppearancePreferences? = null
    private val appearanceWrites = Channel<AppearancePreferences>(Channel.CONFLATED)
    private val planningPreferencesReady = kotlinx.coroutines.CompletableDeferred<Unit>()
    private var persistedPlanning = PlanningPreferences()
    private var pendingPlanning: PlanningPreferences? = null
    private val planningWrites = Channel<PlanningPreferences>(Channel.CONFLATED)

    fun setPlanningPreferences(campus: Campus, value: CampusPlanningPreferences) {
        if (state.value.initializing) return
        val updated = state.value.planningPreferences.withCampus(campus, value.copy(
            templates = value.templates.map { it.copy(windows = it.windows.toList()) }.toList()))
        if (updated == state.value.planningPreferences) return
        pendingPlanning = updated
        mutableState.update { it.copy(planningPreferences = updated) }
        planningWrites.trySend(updated)
    }

    fun setCampusTone(campus: Campus, tone: CampusTone) {
        if(state.value.initializing) return
        setAppearance(state.value.appearance.withTone(campus,tone))
    }

    fun setDefaultCampus(campus: Campus) {
        if(state.value.initializing) return
        setAppearance(state.value.appearance.copy(defaultCampus=campus))
    }

    private fun setAppearance(value: AppearancePreferences) {
        if(value==state.value.appearance) return
        pendingAppearance=value
        mutableState.update { it.copy(appearance=value) }
        appearanceWrites.trySend(value)
    }

    fun setPeriodStrip(value: PeriodStripPreferences) {
        if(value==state.value.periodStrip) return
        pendingStrip=value
        mutableState.update { it.copy(periodStrip=value) }
        stripWrites.trySend(value)
    }

    init {
        viewModelScope.launch {
            settings.planningPreferences.catch { showError("无法读取规划偏好"); emit(PlanningPreferences()) }.collect { value ->
                persistedPlanning = value
                if (pendingPlanning == null || pendingPlanning == value) {
                    pendingPlanning = null
                    mutableState.update { it.copy(planningPreferences = value) }
                }
                planningPreferencesReady.complete(Unit)
            }
        }
        viewModelScope.launch {
            for (value in planningWrites) try { settings.savePlanningPreferences(value) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                if (pendingPlanning == value) {
                    pendingPlanning = null
                    mutableState.update { it.copy(planningPreferences = persistedPlanning) }
                }
                showError("规划偏好保存失败，请重试")
            }
        }
        viewModelScope.launch {
            settings.appearance.catch { showError("无法读取外观设置。"); emit(AppearancePreferences()) }.collect { value ->
                persistedAppearance=value
                if(pendingAppearance==null || pendingAppearance==value) {
                    pendingAppearance=null
                    mutableState.update { it.copy(appearance=value) }
                }
                appearanceReady.complete(Unit)
            }
        }
        viewModelScope.launch {
            for(value in appearanceWrites) try { settings.saveAppearance(value) }
            catch(error: Exception) {
                if(error is CancellationException) throw error
                if(pendingAppearance==value) {
                    pendingAppearance=null
                    mutableState.update { it.copy(appearance=persistedAppearance) }
                }
                showError("外观设置保存失败，请重试。")
            }
        }
        viewModelScope.launch {
            settings.periodStrip.catch { showError("无法读取时段条设置。") }.collect { value ->
                persistedStrip=value
                if(pendingStrip!=null && pendingStrip!=value) return@collect
                pendingStrip=null
                mutableState.update { it.copy(periodStrip=value) }
            }
        }
        viewModelScope.launch {
            for(value in stripWrites) try { settings.savePeriodStrip(value) }
            catch(error: Exception) {
                if(error is CancellationException) throw error
                if(pendingStrip==value) {
                    pendingStrip=null
                    mutableState.update { it.copy(periodStrip=persistedStrip) }
                }
                showError("时段条设置保存失败，请重试。")
            }
        }
        viewModelScope.launch {
            repository.profiles.catch { showError("无法读取个人偏好，请重新启动 App。") }.collect { profiles ->
                storedProfiles = profiles
                // A prior Room emission must not undo a newer tap or text edit. Only
                // release the optimistic overlay after the latest write is confirmed.
                pendingProfileChanges.entries.removeAll { (_, change) ->
                    change.persisted && profiles[change.id] == change.profile
                }
                publishRealProfiles()
            }
        }
        viewModelScope.launch {
            settings.filters.catch { showError("无法读取筛选设置。") }.collect { saved ->
                val cleaned = storedCatalog.removed.fold(saved) { filters, name -> filters.withoutTerm(name) }
                if (pendingSharedFilters != null && pendingSharedFilters != sharedConditions(cleaned)) return@collect
                pendingSharedFilters = null
                realFilters = cleaned.copy(selectedSlots = realFilters.selectedSlots, query = realFilters.query,
                        buildings = realBuildings.getValue(state.value.campus))
                if (!state.value.demo) mutableState.update { it.copy(filters = realFilters) }
            }
        }
        viewModelScope.launch {
            settings.catalog.catch { showError("无法读取词条。") }.collect { catalog ->
                if (pendingCatalog != null && pendingCatalog != catalog) return@collect
                pendingCatalog = null
                storedCatalog = catalog
                realFilters = catalog.removed.fold(realFilters) { filters, name -> filters.withoutTerm(name) }
                if (!state.value.demo) mutableState.update { it.copy(tagCatalog = catalog, filters = realFilters) }
            }
        }
        viewModelScope.launch {
            settings.buildings.catch { showError("无法读取教学楼筛选。") }.collect { values ->
                realBuildings = Campus.entries.associateWith { campus ->
                    val saved = values[campus].orEmpty()
                    val pending = pendingBuildings[campus]
                    if (pending != null && pending != saved) pending else {
                        pendingBuildings.remove(campus); saved
                    }
                }
                realFilters = realFilters.copy(buildings = realBuildings.getValue(state.value.campus))
                if (!state.value.demo) mutableState.update { it.copy(filters = realFilters) }
            }
        }
        viewModelScope.launch {
            try {
                for (campus in Campus.entries) {
                    repository.cached(campus)?.let { snapshot ->
                        realWorkspaces = realWorkspaces + (campus to withSnapshot(realWorkspaces.getValue(campus), snapshot, true))
                    }
                }
                val remembered = repository.rememberedAccount()
                val authenticated = repository.hasSession()
                val rememberedLogin = if (authenticated) null else repository.rememberedCredentials()
                appearanceReady.await()
                planningPreferencesReady.await()
                restoreWorkspace(state.value.appearance.defaultCampus)
                mutableState.update { it.copy(initializing = false, loggedIn = authenticated, rememberedAccount = remembered,
                    rememberedLogin = rememberedLogin,
                    cachedCampuses = realWorkspaces.filterValues { workspace -> workspace.snapshot != null }.keys) }
                if (authenticated) refresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(initializing = false, error = message(error)) }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                updateToday()
            }
        }
    }

    fun login(account: String, password: String) = login(account, password, false)

    fun login(account: String, password: String, rememberPassword: Boolean) {
        if (state.value.loggingIn || state.value.initializing) return
        if (account.isBlank() || password.isEmpty()) { showError("请填写教务账号和密码。"); return }
        mutableState.update { it.copy(loggingIn = true, rememberedLogin = null, error = null) }
        val generation = ++loginGeneration
        loginJob = viewModelScope.launch {
            try {
                repository.login(account.trim(), password, rememberPassword)
                if (generation != loginGeneration) return@launch
                planning.clear()
                mutableState.update { it.copy(loggedIn = true, loggingIn = false, viewingCache = false,
                    rememberedAccount = account.trim().takeIf { rememberPassword }) }
                refresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation != loginGeneration) return@launch
                mutableState.update { it.copy(loggingIn = false, rememberedAccount = null, rememberedLogin = null, error = message(error)) }
            }
        }
    }

    fun refresh() {
        val current = state.value
        if (current.demo || current.refreshing || current.initializing) return
        if (!current.loggedIn) { openLogin(); return }
        val campus = current.campus
        val generation = ++refreshGeneration
        updateWorkspace(campus) { it.copy(refreshing = true, error = null) }
        refreshJob = viewModelScope.launch {
            try {
                val snapshot = repository.refresh(campus)
                if (!isCurrentRequest(campus, generation)) return@launch
                require(snapshot.campusId == campus.dataKey && !snapshot.isDemo)
                realWorkspaces = realWorkspaces + (campus to withSnapshot(realWorkspaces.getValue(campus), snapshot, false))
                restoreWorkspace(campus)
                mutableState.update { it.copy(cachedCampuses = it.cachedCampuses + campus) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!isCurrentRequest(campus, generation)) return@launch
                updateWorkspace(campus) { it.copy(refreshing = false, error = message(error)) }
                if (error is AuthenticationRequired) {
                    val remembered = repository.rememberedCredentials()
                    if (!isCurrentRequest(campus, generation)) return@launch
                    mutableState.update {
                        it.copy(loggedIn = false, viewingCache = false, selectedRoomId = null,
                            rememberedAccount = remembered?.account, rememberedLogin = remembered)
                    }
                }
            }
        }
    }

    fun onForeground() {
        updateToday()
        val current = state.value
        if (!current.initializing && !current.demo && current.loggedIn && needsRefresh(current.campusSnapshot)) refresh()
    }

    fun showDemo() {
        planning.clear()
        cancelRefresh()
        demoFilters = SearchFilters(setOf(1, 2))
        demoBuildings = Campus.entries.associateWith { emptySet() }
        demoSelectionDate = today()
        demoWorkspaces = Campus.entries.associateWith { campus ->
            CampusWorkspace(snapshot = DemoData.snapshot(campus, clock))
        }
        mutableState.update { it.copy(demo = true, loggedIn = false, viewingCache = false, rememberedLogin = null,
            profiles = emptyMap(), tagCatalog = TagCatalog()) }
        restoreWorkspace(state.value.appearance.defaultCampus)
    }

    fun viewCache() {
        val campus = state.value.campus.takeIf { realWorkspaces.getValue(it).snapshot != null }
            ?: Campus.entries.firstOrNull { realWorkspaces.getValue(it).snapshot != null } ?: return
        restoreWorkspace(campus)
        mutableState.update { it.copy(viewingCache = true, fromCache = true, error = null) }
    }

    fun openLogin() {
        loginGeneration++
        loginJob?.cancel()
        planning.clear()
        cancelRefresh()
        demoWorkspaces = emptyMap()
        mutableState.update { it.copy(demo = false, loggedIn = false, loggingIn = false, viewingCache = false,
            profiles = realProfiles(), tagCatalog = storedCatalog) }
        restoreWorkspace(state.value.appearance.defaultCampus)
        mutableState.update { it.copy(error = null) }
    }

    fun setCampus(campus: Campus) {
        updateToday()
        if (state.value.campus == campus) return
        captureWorkspace()
        // Time/attribute criteria are shared; each campus restores its own building choices.
        if (state.value.demo) demoFilters = demoFilters.copy(buildings = demoBuildings.getValue(campus))
        else realFilters = realFilters.copy(buildings = realBuildings.getValue(campus))
        cancelRefresh()
        restoreWorkspace(campus)
        if (!state.value.demo && state.value.loggedIn && needsRefresh(state.value.campusSnapshot)) refresh()
    }

    fun logout() {
        cancelRefresh()
        loginGeneration++
        loginJob?.cancel()
        viewModelScope.launch {
            try {
                repository.logout()
                val remembered = repository.rememberedCredentials()
                mutableState.update { it.copy(rememberedAccount = remembered?.account, rememberedLogin = remembered) }
                openLogin()
            }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                showError("退出未完成，无法清除本机登录状态，请重试。")
            }
        }
    }

    fun setFiltersFor(campus: Campus, filters: SearchFilters) {
        if (state.value.campus == campus) setFilters(filters)
    }

    fun setFilters(filters: SearchFilters) {
        val current = state.value
        mutableState.update { it.copy(filters = filters) }
        captureWorkspace()
        if (!current.demo && current.filters.buildings != filters.buildings) {
            val campus = current.campus
            pendingBuildings[campus] = filters.buildings
            viewModelScope.launch {
                try { settings.saveBuildings(campus, filters.buildings) }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    pendingBuildings.remove(campus)
                    updateWorkspace(campus) { it.copy(error = "教学楼筛选已生效，但保存失败。") }
                }
            }
        }
        if (!current.demo && current.filters.copy(selectedSlots = emptySet(), query = "", buildings = emptySet()) !=
            filters.copy(selectedSlots = emptySet(), query = "", buildings = emptySet())) {
            pendingSharedFilters = sharedConditions(filters)
            val campus = current.campus
            viewModelScope.launch {
                try { settings.save(filters) }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (pendingSharedFilters == sharedConditions(filters)) pendingSharedFilters = null
                    updateWorkspace(campus) { it.copy(error = "筛选已生效，但设置保存失败。") }
                }
            }
        }
    }

    private fun updateCatalog(catalog: TagCatalog) {
        if (!state.value.demo) {
            storedCatalog = catalog
            pendingCatalog = catalog
        }
        mutableState.update { it.copy(tagCatalog = catalog) }
    }

    private fun sharedConditions(filters: SearchFilters) = filters.copy(selectedSlots = emptySet(), query = "", buildings = emptySet())

    fun addCustomTag(raw: String) {
        val name = raw.trim()
        PersonalTags.validate(raw, state.value.tagOrder.toSet())?.let { showError(it); return }
        updateCatalog(state.value.tagCatalog.added(name))
        if (!state.value.demo) viewModelScope.launch {
            try { settings.addTag(name) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                pendingCatalog = null; showError("词条已添加，但保存失败，请重试。")
            }
        }
    }

    fun removePersonalTag(name: String) {
        if (name == PersonalTags.favorite || name !in state.value.tagOrder) return
        updateCatalog(state.value.tagCatalog.deleted(name))
        mutableState.update { it.copy(filters = it.filters.withoutTerm(name)) }
        captureWorkspace()
        if (!state.value.demo) pendingSharedFilters = sharedConditions(state.value.filters)
        if (!state.value.demo) viewModelScope.launch {
            try { settings.removeTag(name) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                pendingCatalog = null; showError("词条已隐藏，但保存失败，请重试。")
            }
        }
    }

    fun reorderPersonalTags(names: List<String>) {
        val valid = names.filter { it in state.value.tagOrder }
        val catalog = state.value.tagCatalog.reordered(valid)
        if (catalog == state.value.tagCatalog) return
        updateCatalog(catalog)
        if (!state.value.demo) viewModelScope.launch {
            try { settings.reorderTags(valid) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                pendingCatalog = null; showError("词条顺序已调整，但保存失败。")
            }
        }
    }

    fun selectRoom(id: String?) { mutableState.update { it.copy(selectedRoomId = id) } }

    fun toggleFavorite(room: Classroom) {
        val profile = state.value.profiles[room.id] ?: room.emptyProfile()
        saveProfile(profile.copy(favorite = !profile.favorite))
    }

    fun toggleExcluded(room: Classroom) {
        val profile = state.value.profiles[room.id] ?: room.emptyProfile()
        saveProfile(profile.copy(excluded = !profile.excluded))
    }

    fun saveProfile(profile: RoomProfile) {
        if (state.value.demo) {
            mutableState.update { it.copy(profiles = it.profiles + (profile.roomId to profile)) }
            return
        }
        queueProfileChange(profile.roomId, profile)
    }

    fun clearProfile(id: String) {
        if (state.value.demo) {
            mutableState.update { it.copy(profiles = it.profiles - id) }
            return
        }
        queueProfileChange(id, null)
    }

    private fun realProfiles(): Map<String, RoomProfile> = storedProfiles.toMutableMap().apply {
        pendingProfileChanges.values.forEach { change ->
            if (change.profile == null) remove(change.id) else put(change.id, change.profile)
        }
    }

    private fun publishRealProfiles() {
        mutableState.update { if (it.demo) it else it.copy(profiles = realProfiles()) }
    }

    private fun queueProfileChange(id: String, profile: RoomProfile?) {
        val campus = Campus.entries.firstOrNull { id.startsWith("${it.dataKey}:") } ?: return
        val change = ProfileChange(id, profile, campus, ++profileRevision)
        pendingProfileChanges[id] = change
        queuedProfileChanges[id] = change
        publishRealProfiles()
        if (profileWriteJob?.isActive == true) return
        profileWriteJob = viewModelScope.launch {
            try {
                while (queuedProfileChanges.isNotEmpty()) {
                    val next = queuedProfileChanges.values.first()
                    queuedProfileChanges.remove(next.id)
                    try {
                        if (next.profile == null) repository.deleteProfile(next.id)
                        else repository.saveProfile(next.profile)
                        if (pendingProfileChanges[next.id]?.revision == next.revision) {
                            pendingProfileChanges[next.id] = next.copy(persisted = true)
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        updateWorkspace(next.campus) { it.copy(error =
                            if (next.profile == null) "个人标记清除失败，请重试。"
                            else "个人标记已更新，但保存失败，请再次修改重试。") }
                    }
                }
            } finally { profileWriteJob = null }
        }
    }

    fun dismissError() { mutableState.update { it.copy(error = null) }; captureWorkspace() }
    private fun showError(text: String) { mutableState.update { it.copy(error = text) }; captureWorkspace() }

    private fun today() = LocalDate.now(clock.withZone(SchoolZone))
    private fun updateToday() {
        val date = today()
        if (state.value.today == date) return
        mutableState.update { it.copy(today = date) }
        if (state.value.demo) {
            demoWorkspaces = demoWorkspaces.mapValues { (campus, workspace) ->
                withSnapshot(workspace, DemoData.snapshot(campus, clock), false)
            }
            restoreWorkspace(state.value.campus)
        }
    }
    private fun needsRefresh(snapshot: ClassroomSnapshot?) = snapshot == null || !snapshot.isCurrentOn(today()) ||
        Duration.between(snapshot.fetchedAt, clock.instant()).toMinutes() >= 10

    private fun isCurrentRequest(campus: Campus, generation: Long) =
        generation == refreshGeneration && state.value.campus == campus && !state.value.demo

    private fun cancelRefresh() {
        ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = null
        realWorkspaces = realWorkspaces.mapValues { (_, workspace) -> workspace.copy(refreshing = false) }
        mutableState.update { it.copy(refreshing = false) }
    }

    private fun captureWorkspace() {
        val current = state.value
        val workspace = CampusWorkspace(current.snapshot, current.fromCache, current.refreshing, current.error)
        if (current.demo) {
            demoFilters = current.filters
            demoBuildings = demoBuildings + (current.campus to current.filters.buildings)
            demoWorkspaces = demoWorkspaces + (current.campus to workspace)
        } else {
            realFilters = current.filters
            realBuildings = realBuildings + (current.campus to current.filters.buildings)
            realWorkspaces = realWorkspaces + (current.campus to workspace)
        }
    }

    private fun restoreWorkspace(campus: Campus) {
        val workspace = (if (state.value.demo) demoWorkspaces else realWorkspaces).getValue(campus)
        if (state.value.demo) demoFilters = demoFilters.copy(buildings = demoBuildings.getValue(campus))
        else realFilters = realFilters.copy(buildings = realBuildings.getValue(campus))
        mutableState.update { it.copy(campus = campus, snapshot = workspace.snapshot, filters = if (it.demo) demoFilters else realFilters,
            campusSnapshots = (if (it.demo) demoWorkspaces else realWorkspaces).mapNotNull { (key, value) ->
                value.snapshot?.let { snapshot -> key to snapshot }
            }.toMap(),
            fromCache = workspace.fromCache, refreshing = workspace.refreshing, error = workspace.error,
            today = today(), selectedRoomId = null) }
    }

    private fun updateWorkspace(campus: Campus, transform: (CampusWorkspace) -> CampusWorkspace) {
        realWorkspaces = realWorkspaces + (campus to transform(realWorkspaces.getValue(campus)))
        if (!state.value.demo && state.value.campus == campus) {
            val workspace = realWorkspaces.getValue(campus)
            mutableState.update { it.copy(snapshot = workspace.snapshot, filters = realFilters,
                fromCache = workspace.fromCache, refreshing = workspace.refreshing, error = workspace.error) }
        }
    }

    private fun withSnapshot(previous: CampusWorkspace, snapshot: ClassroomSnapshot, fromCache: Boolean): CampusWorkspace {
        val first = snapshot.slots.firstOrNull { it.end > LocalTime.now(clock.withZone(SchoolZone)) } ?: snapshot.slots.firstOrNull()
        val selectedDate = if (state.value.demo) demoSelectionDate else realSelectionDate
        if (selectedDate != snapshot.queryDate && first != null &&
            (selectedDate == null || snapshot.isCurrentOn(today()))) {
            if (state.value.demo) {
                demoSelectionDate = snapshot.queryDate
                demoFilters = demoFilters.copy(selectedSlots = setOf(first.number))
            } else {
                realSelectionDate = snapshot.queryDate
                realFilters = realFilters.copy(selectedSlots = setOf(first.number))
            }
        }
        return previous.copy(snapshot = snapshot, fromCache = fromCache, refreshing = false, error = null)
    }

    private fun message(error: Exception): String = when (error) {
        is AuthenticationRequired -> "${error.message} 本地收藏仍然保留。"
        is SchoolRequestFailed -> error.userMessage
        is InvalidSchoolResponse -> "教务返回的数据格式异常，旧缓存没有被覆盖。"
        else -> "操作未完成，请稍后重试。"
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                YOURoomViewModel(container.repository, container.settings) as T
        }
    }
}
