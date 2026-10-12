package com.example.youroom.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.youroom.AppContainer
import com.example.youroom.data.*
import com.example.youroom.data.local.FilterSettings
import com.example.youroom.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

data class YouRoomState(
    val initializing: Boolean = true,
    val loggedIn: Boolean = false,
    val loggingIn: Boolean = false,
    val refreshing: Boolean = false,
    val snapshot: ClassroomSnapshot? = null,
    val fromCache: Boolean = false,
    val viewingCache: Boolean = false,
    val demo: Boolean = false,
    val campus: Campus = Campus.XITUCHENG,
    val cachedCampuses: Set<Campus> = emptySet(),
    val today: LocalDate = LocalDate.now(SchoolZone),
    val filters: SearchFilters = SearchFilters(),
    val profiles: Map<String, RoomProfile> = emptyMap(),
    val customTags: Set<String> = emptySet(),
    val error: String? = null,
    val selectedRoomId: String? = null,
) {
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
        val room = campusSnapshot?.classrooms?.find { it.id == id }
            ?: profile?.let { Classroom(it.roomId, it.name, it.building, null) } ?: return null
        return RoomMatch(room, profile ?: room.emptyProfile(),
            if (stale) emptySet() else campusSnapshot?.slots?.filter { slot -> slot.classrooms.any { it.id == id } }
                ?.map { it.number }?.toSet().orEmpty())
    }
}

private data class CampusWorkspace(
    val snapshot: ClassroomSnapshot? = null,
    val fromCache: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class YouRoomViewModel(
    private val repository: ClassroomDataSource,
    private val settings: FilterSettings,
    private val clock: Clock = Clock.system(SchoolZone),
) : ViewModel() {
    private val mutableState = MutableStateFlow(YouRoomState(today = today()))
    val state: StateFlow<YouRoomState> = mutableState.asStateFlow()
    private var realWorkspaces = Campus.entries.associateWith { CampusWorkspace() }
    private var demoWorkspaces = emptyMap<Campus, CampusWorkspace>()
    private var storedProfiles: Map<String, RoomProfile> = emptyMap()
    private var storedCustomTags: Set<String> = emptySet()
    private var realFilters = SearchFilters()
    private var demoFilters = SearchFilters()
    private var realSelectionDate: LocalDate? = null
    private var demoSelectionDate: LocalDate? = null
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    init {
        viewModelScope.launch {
            repository.profiles.catch { showError("无法读取个人偏好，请重新启动 App。") }.collect { profiles ->
                storedProfiles = profiles
                mutableState.update { if (it.demo) it else it.copy(profiles = profiles, customTags = realCustomTags()) }
            }
        }
        viewModelScope.launch {
            settings.filters.catch { showError("无法读取筛选设置。") }.collect { saved ->
                realFilters = saved.copy(selectedSlots = realFilters.selectedSlots,
                    query = realFilters.query, buildings = realFilters.buildings)
                if (!state.value.demo) mutableState.update { it.copy(filters = realFilters) }
            }
        }
        viewModelScope.launch {
            settings.customTags.catch { showError("无法读取自定义词条。") }.collect { tags ->
                storedCustomTags = tags
                if (!state.value.demo) mutableState.update { it.copy(customTags = realCustomTags()) }
            }
        }
        viewModelScope.launch {
            try {
                for (campus in Campus.entries) {
                    repository.cached(campus)?.let { snapshot ->
                        realWorkspaces = realWorkspaces + (campus to withSnapshot(realWorkspaces.getValue(campus), snapshot, true))
                    }
                }
                val authenticated = repository.hasSession()
                restoreWorkspace(Campus.XITUCHENG)
                mutableState.update { it.copy(initializing = false, loggedIn = authenticated,
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

    fun login(account: String, password: String) {
        if (state.value.loggingIn || state.value.initializing) return
        if (account.isBlank() || password.isEmpty()) { showError("请填写教务账号和密码。"); return }
        mutableState.update { it.copy(loggingIn = true, error = null) }
        viewModelScope.launch {
            try {
                repository.login(account.trim(), password)
                mutableState.update { it.copy(loggedIn = true, loggingIn = false, viewingCache = false) }
                refresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loggingIn = false, error = message(error)) }
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
                if (error is AuthenticationRequired) mutableState.update {
                    it.copy(loggedIn = false, viewingCache = false, selectedRoomId = null)
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
        cancelRefresh()
        demoFilters = SearchFilters(setOf(1, 2))
        demoSelectionDate = today()
        demoWorkspaces = Campus.entries.associateWith { campus ->
            CampusWorkspace(snapshot = DemoData.snapshot(campus, clock))
        }
        mutableState.update { it.copy(demo = true, loggedIn = false, viewingCache = false, profiles = emptyMap(), customTags = emptySet()) }
        restoreWorkspace(Campus.XITUCHENG)
    }

    fun viewCache() {
        val campus = state.value.campus.takeIf { realWorkspaces.getValue(it).snapshot != null }
            ?: Campus.entries.firstOrNull { realWorkspaces.getValue(it).snapshot != null } ?: return
        restoreWorkspace(campus)
        mutableState.update { it.copy(viewingCache = true, fromCache = true, error = null) }
    }

    fun openLogin() {
        cancelRefresh()
        demoWorkspaces = emptyMap()
        mutableState.update { it.copy(demo = false, loggedIn = false, viewingCache = false, profiles = storedProfiles, customTags = realCustomTags()) }
        restoreWorkspace(Campus.XITUCHENG)
        mutableState.update { it.copy(error = null) }
    }

    fun setCampus(campus: Campus) {
        updateToday()
        if (state.value.campus == campus) return
        captureWorkspace()
        // Time and attribute criteria are shared; buildings belong only to the current campus.
        if (state.value.demo) demoFilters = demoFilters.copy(buildings = emptySet())
        else realFilters = realFilters.copy(buildings = emptySet())
        cancelRefresh()
        restoreWorkspace(campus)
        if (!state.value.demo && state.value.loggedIn && needsRefresh(state.value.campusSnapshot)) refresh()
    }

    fun logout() {
        if (state.value.demo || state.value.viewingCache) { openLogin(); return }
        cancelRefresh()
        viewModelScope.launch {
            try { repository.logout(); openLogin() }
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
        if (!current.demo && current.filters.copy(selectedSlots = emptySet(), query = "", buildings = emptySet()) !=
            filters.copy(selectedSlots = emptySet(), query = "", buildings = emptySet())) {
            val campus = current.campus
            viewModelScope.launch {
                try { settings.save(filters) }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    updateWorkspace(campus) { it.copy(error = "筛选已生效，但设置保存失败。") }
                }
            }
        }
    }

    private fun realCustomTags() = storedCustomTags +
        storedProfiles.values.flatMap { it.allTags }.filterNot { it in PersonalTags.builtIn }

    fun addCustomTag(raw: String) {
        val name = raw.trim()
        PersonalTags.validate(raw, state.value.customTags)?.let { showError(it); return }
        if (state.value.demo) {
            mutableState.update { it.copy(customTags = it.customTags + name) }
        } else viewModelScope.launch {
            try { settings.addTag(name) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                showError("词条保存失败，请重试。")
            }
        }
    }

    fun selectRoom(id: String?) { mutableState.update { it.copy(selectedRoomId = id) } }

    fun toggleFavorite(room: Classroom) {
        val profile = state.value.profiles[room.id] ?: room.emptyProfile()
        saveProfile(profile.copy(favorite = !profile.favorite))
    }

    fun saveProfile(profile: RoomProfile) {
        if (state.value.demo) {
            mutableState.update { it.copy(profiles = it.profiles + (profile.roomId to profile)) }
            return
        }
        val campus = state.value.campus
        viewModelScope.launch {
            try { repository.saveProfile(profile) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                updateWorkspace(campus) { it.copy(error = "个人标记保存失败，请重试。") }
            }
        }
    }

    fun clearProfile(id: String) {
        if (state.value.demo) {
            mutableState.update { it.copy(profiles = it.profiles - id) }
            return
        }
        val campus = state.value.campus
        viewModelScope.launch {
            try { repository.deleteProfile(id) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                updateWorkspace(campus) { it.copy(error = "个人标记清除失败，请重试。") }
            }
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
            demoWorkspaces = demoWorkspaces + (current.campus to workspace)
        } else {
            realFilters = current.filters
            realWorkspaces = realWorkspaces + (current.campus to workspace)
        }
    }

    private fun restoreWorkspace(campus: Campus) {
        val workspace = (if (state.value.demo) demoWorkspaces else realWorkspaces).getValue(campus)
        mutableState.update { it.copy(campus = campus, snapshot = workspace.snapshot, filters = if (it.demo) demoFilters else realFilters,
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
        is AuthenticationRequired -> "登录状态已失效，请重新登录；本地收藏仍然保留。"
        is SchoolRequestFailed -> error.userMessage
        is InvalidSchoolResponse -> "教务返回的数据格式异常，旧缓存没有被覆盖。"
        else -> "操作未完成，请稍后重试。"
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                YouRoomViewModel(container.repository, container.settings) as T
        }
    }
}
