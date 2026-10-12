package com.example.youroom.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.youroom.AppContainer
import com.example.youroom.data.*
import com.example.youroom.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
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
    val today: LocalDate = LocalDate.now(SchoolZone),
    val filters: SearchFilters = SearchFilters(),
    val profiles: Map<String, RoomProfile> = emptyMap(),
    val error: String? = null,
    val selectedRoomId: String? = null,
) {
    val showWorkspace: Boolean get() = loggedIn || demo || viewingCache
    val campusSnapshot: ClassroomSnapshot? get() = snapshot?.takeIf {
        it.campusId == campus.dataKey && (demo || campus.connected)
    }
    val campusProfiles: List<RoomProfile> get() = profiles.values.filter { it.roomId.startsWith("${campus.dataKey}:") }
    val campusUnavailable: Boolean get() = !demo && !campus.connected
    val stale: Boolean get() = campusSnapshot?.isCurrentOn(today) == false
    val result: SearchResult get() = campusSnapshot?.takeUnless { stale }?.let {
        ClassroomFilter.search(it, filters, profiles)
    } ?: SearchResult(emptyList(), emptyList(), false)
    val selectedRoom: RoomMatch? get() {
        val id = selectedRoomId ?: return null
        if (campusUnavailable) return null
        if (!id.startsWith("${campus.dataKey}:")) return null
        val profile = profiles[id]
        val room = campusSnapshot?.classrooms?.find { it.id == id }
            ?: profile?.let { Classroom(it.roomId, it.name, it.building, null) } ?: return null
        return RoomMatch(room, profile ?: room.emptyProfile(),
            if (stale) emptySet() else campusSnapshot?.slots?.filter { slot -> slot.classrooms.any { it.id == id } }
                ?.map { it.number }?.toSet().orEmpty())
    }
}

class YouRoomViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val mutableState = MutableStateFlow(YouRoomState())
    val state: StateFlow<YouRoomState> = mutableState.asStateFlow()
    private var cachedSnapshot: ClassroomSnapshot? = null
    private var storedProfiles: Map<String, RoomProfile> = emptyMap()
    private var storedFilters = SearchFilters()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            repository.profiles.catch { showError("无法读取个人偏好，请重新启动 App。") }
                .collect { profiles ->
                    storedProfiles = profiles
                    mutableState.update { if (it.demo) it else it.copy(profiles = profiles) }
                }
        }
        viewModelScope.launch {
            container.settings.filters.catch { showError("无法读取筛选设置。") }.collect { filters ->
                storedFilters = filters
                mutableState.update {
                    if (it.demo) it else it.copy(filters = filters.copy(firstSlot = it.filters.firstSlot,
                        lastSlot = it.filters.lastSlot, query = it.filters.query))
                }
            }
        }
        viewModelScope.launch {
            try {
                cachedSnapshot = repository.cached()
                val authenticated = repository.hasSession()
                mutableState.update { it.copy(initializing = false, loggedIn = authenticated) }
                cachedSnapshot?.let { applySnapshot(it, fromCache = true) }
                if (authenticated) refresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(initializing = false, error = message(error)) }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                mutableState.update { it.copy(today = LocalDate.now(SchoolZone)) }
            }
        }
    }

    fun login(account: String, password: String) {
        if (state.value.loggingIn || state.value.initializing) return
        if (account.isBlank() || password.isEmpty()) {
            showError("请填写教务账号和密码。")
            return
        }
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
        if (current.demo || current.campusUnavailable || current.refreshing || current.initializing) return
        if (!current.loggedIn) { openLogin(); return }
        mutableState.update { it.copy(refreshing = true, error = null) }
        refreshJob = viewModelScope.launch {
            try {
                val snapshot = repository.refresh()
                cachedSnapshot = snapshot
                applySnapshot(snapshot, fromCache = false)
                mutableState.update { it.copy(refreshing = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(refreshing = false,
                    loggedIn = if (error is AuthenticationRequired) false else it.loggedIn,
                    viewingCache = if (error is AuthenticationRequired) false else it.viewingCache,
                    error = message(error)) }
            }
        }
    }

    fun onForeground() {
        mutableState.update { it.copy(today = LocalDate.now(SchoolZone)) }
        val current = state.value
        if (current.initializing || current.demo || current.campusUnavailable || !current.loggedIn) return
        val snapshot = current.snapshot
        if (snapshot == null || current.stale || Duration.between(snapshot.fetchedAt, Instant.now()).toMinutes() >= 10) {
            refresh()
        }
    }

    fun showDemo() {
        mutableState.update { it.copy(demo = true, loggedIn = false, viewingCache = false,
            campus = Campus.XITUCHENG,
            snapshot = DemoData.snapshot(), profiles = emptyMap(), filters = SearchFilters(firstSlot = 1, lastSlot = 2),
            error = null, fromCache = false, selectedRoomId = null) }
    }

    fun viewCache() {
        if (cachedSnapshot == null) return
        mutableState.update { it.copy(viewingCache = true, fromCache = true, error = null) }
    }

    fun openLogin() {
        mutableState.update { it.copy(demo = false, viewingCache = false, snapshot = cachedSnapshot,
            campus = Campus.XITUCHENG,
            fromCache = cachedSnapshot != null, profiles = storedProfiles, filters = storedFilters,
            error = null, selectedRoomId = null) }
    }

    fun setCampus(campus: Campus) {
        if (state.value.campus == campus) return
        mutableState.update {
            if (it.demo) it.copy(campus = campus, snapshot = DemoData.snapshot(campus),
                filters = SearchFilters(1, 2), selectedRoomId = null, error = null)
            else it.copy(campus = campus, selectedRoomId = null, error = null)
        }
    }

    fun logout() {
        if (state.value.demo || state.value.viewingCache) { openLogin(); return }
        refreshJob?.cancel()
        mutableState.update { it.copy(refreshing = false) }
        viewModelScope.launch {
            try {
                repository.logout()
                mutableState.update { it.copy(loggedIn = false, viewingCache = false,
                    campus = Campus.XITUCHENG, selectedRoomId = null, error = null) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showError("退出未完成，无法清除本机登录状态，请重试。")
            }
        }
    }

    fun setFilters(filters: SearchFilters) {
        val old = state.value.filters
        mutableState.update { it.copy(filters = filters) }
        if (!state.value.demo && old.copy(firstSlot = 1, lastSlot = 1, query = "") !=
            filters.copy(firstSlot = 1, lastSlot = 1, query = "")) {
            viewModelScope.launch {
                try { container.settings.save(filters) }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    showError("筛选已生效，但设置保存失败。")
                }
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
        viewModelScope.launch {
            try { repository.saveProfile(profile) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                showError("个人标记保存失败，请重试。")
            }
        }
    }

    fun clearProfile(id: String) {
        if (state.value.demo) {
            mutableState.update { it.copy(profiles = it.profiles - id) }
            return
        }
        viewModelScope.launch {
            try { repository.deleteProfile(id) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                showError("个人标记清除失败，请重试。")
            }
        }
    }

    fun dismissError() { mutableState.update { it.copy(error = null) } }
    private fun showError(text: String) { mutableState.update { it.copy(error = text) } }

    private fun applySnapshot(snapshot: ClassroomSnapshot, fromCache: Boolean) {
        val first = snapshot.slots.firstOrNull { it.end > LocalTime.now(SchoolZone) } ?: snapshot.slots.first()
        mutableState.update {
            val preserve = it.snapshot != null && it.snapshot.queryDate == snapshot.queryDate &&
                snapshot.slots.any { slot -> slot.number == it.filters.firstSlot } &&
                snapshot.slots.any { slot -> slot.number == it.filters.lastSlot }
            it.copy(snapshot = snapshot, fromCache = fromCache, today = LocalDate.now(SchoolZone),
                filters = if (preserve) it.filters else it.filters.copy(firstSlot = first.number, lastSlot = first.number))
        }
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = YouRoomViewModel(container) as T
        }
    }
}
