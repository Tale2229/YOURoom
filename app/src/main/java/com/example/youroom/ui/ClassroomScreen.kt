package com.example.youroom.ui

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.YOURoomTheme

@Composable
fun ClassroomScreen(state: YOURoomState, onFilters: (SearchFilters) -> Unit,
    onRefresh: () -> Unit, onRoomEditor: (String, RoomEditor) -> Unit, onFavorite: (Classroom) -> Unit,
    onCampus: (Campus) -> Unit = {}, onAddTag: (String) -> Unit = {},
    onRemoveTag: (String) -> Unit = {}, onTagOrder: (List<String>) -> Unit = {},
    onExcluded: (Classroom) -> Unit = {}, showHeader: Boolean = true,
    sharedHeaderMoving: Boolean = false, interactive: Boolean = true) {
    val westList = androidx.compose.foundation.lazy.rememberLazyListState()
    val shaheList = androidx.compose.foundation.lazy.rememberLazyListState()
    val viewportDates = remember { mutableMapOf<Pair<Campus, Boolean>, java.time.LocalDate>() }
    RoomSearchFocusBoundary(Modifier.fillMaxSize()) {
    // A modal editor owns the IME. Resizing the list behind it can dispose its
    // open row and lose the rail's anchored position while typing a note.
    Box(Modifier.fillMaxSize().then(if(state.selectedRoomId==null) Modifier.imePadding() else Modifier)) {
        CampusFrame(state, onCampus, showHeader, sharedHeaderMoving, interactive,
            keepBothCampuses = true, bodyTag = "campus_body") { page, active, moving, ready ->
            val list = if (page.campus == Campus.XITUCHENG) westList else shaheList
            LaunchedEffect(page.campus, page.campusSnapshot?.queryDate, page.demo, active) {
                if (active) page.campusSnapshot?.queryDate?.let { date ->
                    val previous = viewportDates.put(page.campus to page.demo, date)
                    if (previous != null && previous != date) list.scrollToItem(0)
                }
            }
            ClassroomPage(page, list, active, moving, searchEnabled = ready,
                { if (active) onFilters(it) }, { if (active) onRefresh() },
                { id, editor -> if (active) onRoomEditor(id, editor) }, { if (active) onFavorite(it) },
                { if (active) onAddTag(it) }, { if (active) onRemoveTag(it) },
                { if (active) onTagOrder(it) }, { if (active) onExcluded(it) })
        }
    }
    }
}

@Composable
private fun ClassroomPage(state: YOURoomState, listState: LazyListState, active: Boolean, moving: Boolean, searchEnabled: Boolean,
    onFilters: (SearchFilters) -> Unit,
    onRefresh: () -> Unit, onRoomEditor: (String, RoomEditor) -> Unit, onFavorite: (Classroom) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit, onTagOrder: (List<String>) -> Unit, onExcluded: (Classroom) -> Unit) {
    var showFilters by remember { mutableStateOf(false) }
    var expandedRoom by remember(state.campus, state.demo) { mutableStateOf<String?>(null) }
    var pendingActionRoom by remember(state.campus, state.demo) { mutableStateOf<String?>(null) }
    var previousEditorRoom by remember(state.campus, state.demo) { mutableStateOf<String?>(null) }
    val snapshot = state.campusSnapshot
    val search = rememberClassroomSearch(state, enabled = searchEnabled)
    val result = search.result
    val favorites = remember(state.campus, state.demo) { FavoriteRoomMotion() }
    favorites.Run(search.pending, state.profiles, active, snapshot to state.filters)
    val displayedRooms = favorites.rooms(result.rooms)
    val waiting = search.pending || (snapshot == null && (state.refreshing || moving))
    val slots = snapshot?.slots.orEmpty()
    LaunchedEffect(state.selectedRoomId) {
        // Opening an editor keeps its rail visible. Only a previously attached
        // editor ending (button, Back, or outside touch) closes the matching row.
        val finished = previousEditorRoom
        if (finished != null && finished != state.selectedRoomId && expandedRoom == finished) expandedRoom = null
        previousEditorRoom = state.selectedRoomId
    }
    LaunchedEffect(active) { if (!active) { showFilters = false; expandedRoom = null } }
    LaunchedEffect(result.rooms.map { it.classroom.id }, state.campusSnapshot?.queryDate) {
        if (result.rooms.none { it.classroom.id == expandedRoom }) expandedRoom = null
    }
    if (showFilters && active) FilterDialog(state, { showFilters = false }, onFilters, onAddTag, onRemoveTag, onTagOrder)
    LazyColumn(Modifier.fillMaxSize().testTag("room_list"), state = listState,
        userScrollEnabled = active, contentPadding = PaddingValues(bottom = 24.dp)) {
        if (state.demo) item { StatusNotice("演示数据 · 空闲安排为虚构，个人标记仅本次有效。") }
        if (state.stale) item { StatusNotice("缓存已跨日，刷新后才能筛选今天的教室。", isError = true) }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Keep six columns and 48dp targets even on a 320dp-wide phone.
                val gutter = ((maxWidth - 308.dp) / 2).coerceIn(0.dp, 24.dp)
                Box(Modifier.padding(horizontal = gutter, vertical = 14.dp)) {
                    SlotPicker(state.campus, snapshot?.queryDate, state.demo, state.filters, slots, onFilters,
                        enabled = active, onShowFilters = { showFilters = true })
                }
            }
        }
        item {
            ListDivider(Modifier.padding(horizontal = 24.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("可选教室", style = MaterialTheme.typography.titleMedium)
                }
                if (!waiting || displayedRooms.isNotEmpty()) Text("${displayedRooms.size}", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        if (!waiting && displayedRooms.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when {
                    state.stale -> "需要刷新今日数据"
                    snapshot == null -> "还没有教室数据"
                    result.missingSlots -> "所选节次数据不完整"
                    else -> "没有符合条件的教室"
                }, style = MaterialTheme.typography.titleLarge)
                Text(if (snapshot == null || state.stale) "检查网络后重试，旧缓存不作为今日数据。"
                    else "试试缩短时段，或放宽楼宇与个人标记筛选。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (snapshot == null || state.stale) TextButton(onClick = onRefresh,
                    shape = MaterialTheme.shapes.small, enabled = active && !state.refreshing && !state.demo) {
                    Text(if (state.viewingCache) "登录并刷新" else "重新获取")
                } else TextButton(onClick = { onFilters(SearchFilters(selectedSlots = state.filters.selectedSlots)) },
                    shape = MaterialTheme.shapes.small, enabled = active) { Text("清除额外筛选") }
            }
        }
        items(displayedRooms, key = { it.classroom.id }) { room ->
            val id = room.classroom.id
            val current = room.copy(profile = state.profiles[id] ?: room.classroom.emptyProfile())
            val isMovingFavorite = favorites.roomId == id
            val rowMotion = when {
                moving -> Modifier
                // The custom fade owns all visibility. A lazy removal fade must
                // not retain a ghost when unfavoriting under favorites-only.
                isMovingFavorite -> Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                else -> Modifier.animateItem(placementSpec = tween(FavoriteMotionTiming.Move))
            }
            SwipeRoomRow(current, slots, state.tagOrder, expanded = expandedRoom == id, active = active,
                onExpanded = { open -> if (open) expandedRoom = id else if (expandedRoom == id) expandedRoom = null },
                onFavorite = {
                    if (favorites.start(room.classroom, result.rooms, state.profiles)) onFavorite(room.classroom)
                },
                onTags = { onRoomEditor(id, RoomEditor.TAGS) },
                onNote = { onRoomEditor(id, RoomEditor.NOTE) },
                onExcluded = { onExcluded(room.classroom) },
                modifier = rowMotion.graphicsLayer { alpha = if (isMovingFavorite) favorites.alpha.value else 1f },
                inspectionKey = snapshot?.let { it.queryDate to it.fetchedAt },
                interactionEnabled = active && !favorites.busy && (pendingActionRoom == null || pendingActionRoom == id),
                favoriteClosing = isMovingFavorite,
                onActionPending = { pending ->
                    if (pending) pendingActionRoom = id else if (pendingActionRoom == id) pendingActionRoom = null
                }, editorOpen = state.selectedRoomId == id)
        }
    }
}

@Preview(name = "西土城", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "小屏", showBackground = true, widthDp = 320, heightDp = 740)
@Preview(name = "夜间", showBackground = true, widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RoomsPreview() {
    YOURoomTheme { ClassroomScreen(YOURoomState(initializing = false, demo = true,
        snapshot = DemoData.snapshot(), filters = SearchFilters(setOf(1, 2))), {}, {}, { _, _ -> }, {}) }
}

@Preview(name = "沙河演示", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ShahePreview() {
    YOURoomTheme(campus = Campus.SHAHE) { ClassroomScreen(YOURoomState(initializing = false,
        campus = Campus.SHAHE, demo = true, snapshot = DemoData.snapshot(Campus.SHAHE),
        filters = SearchFilters(setOf(1, 2))), {}, {}, { _, _ -> }, {}) }
}
