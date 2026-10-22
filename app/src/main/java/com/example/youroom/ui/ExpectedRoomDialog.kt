package com.example.youroom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.themeRoles

// Headers, wheels / selection list, and footer actions share identical thirds.
private const val ColumnWeight = 1f
internal const val ExpectedPinTilt = 35f

/** Display aliases never change selection or planning IDs. */
private fun expectedRoomLabel(room: Classroom): String = if (CampusTopology.building(room.building) == "main")
    "主楼\n${room.name.substringAfter('-',room.name)}" else room.name

private fun favoriteWheelLabel(room: Classroom, campus: Campus): String {
    val building = CampusTopology.building(room.building)
    val short = if (building.all(Char::isDigit)) "教$building" else CampusTopology.label(building)
    val number = room.name.substringAfter('-',room.name)
    return if (campus == Campus.XITUCHENG) "$short-$number" else "$short\n$number"
}

@Composable
internal fun ExpectedRoomDialog(state: YOURoomState, selected: Set<String>,
    onSelected: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val initialSelection = remember(state.campus) { selected.filter { it.startsWith("${state.campus.dataKey}:") }.toSet() }
    val rooms = remember(state.campusSnapshot) { state.campusSnapshot?.classrooms.orEmpty()
        .filter { it.id.startsWith("${state.campus.dataKey}:") }.sortedBy { it.name } }
    val favorites = remember(rooms,state.profiles) { rooms.filter { state.profiles[it.id]?.favorite == true } }
    val buildings = remember(rooms) { rooms.map { it.building }.distinct().sorted() }
    // null is the first, peer-level '收藏' category, never an actual building ID.
    val categories = remember(buildings) { listOf<String?>(null)+buildings }
    val categoryLabels = remember(categories) { categories.map { raw ->
        raw?.let { CampusTopology.label(CampusTopology.building(it)) } ?: "收藏"
    } }
    // A shared fitting pool keeps the actual West font equal in both columns, even for long favorites.
    val westFittingLabels = remember(categoryLabels,rooms,favorites) {
        categoryLabels + rooms.map { it.name.substringAfter('-',it.name) } +
            favorites.map { favoriteWheelLabel(it,Campus.XITUCHENG) }
    }
    var building by remember(buildings) { mutableStateOf(rooms.firstOrNull { it.id in selected }?.let {
        if (it in favorites) null else it.building
    } ?: if (favorites.isNotEmpty()) null else buildings.firstOrNull()) }
    val buildingRooms = remember(rooms,favorites,building) { if (building == null) favorites else rooms.filter { it.building == building } }
    var roomId by remember(buildingRooms) { mutableStateOf(buildingRooms.firstOrNull { it.id in selected }?.id ?: buildingRooms.firstOrNull()?.id) }
    var buildingMoving by remember { mutableStateOf(false) }
    var roomMoving by remember { mutableStateOf(false) }
    val current = buildingRooms.firstOrNull { it.id == roomId }
    val campusSelection = selected.filter { it.startsWith("${state.campus.dataKey}:") }.sorted()
    val wheelHeight = with(LocalDensity.current) {
        (MaterialTheme.typography.headlineMedium.lineHeight.toDp()+12.dp).coerceAtLeast(48.dp)*3.5f
    }
    val byId = remember(rooms) { rooms.associateBy { it.id } }
    GuideDialog("expected",onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().guideTag("expected_room_dialog"),
                shape = MaterialTheme.shapes.small, tonalElevation = 0.dp) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("期望教室", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(state.campus.label, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExpectedColumnTitle("教学楼",Modifier.weight(ColumnWeight))
                            ExpectedColumnTitle("教室",Modifier.weight(ColumnWeight))
                            ExpectedColumnTitle("已选 ${campusSelection.size}",Modifier.weight(ColumnWeight))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CylinderWheel("教学楼", categoryLabels,
                                categories.indexOf(building).coerceAtLeast(0), "expected_building_wheel",
                                Modifier.weight(ColumnWeight), textStyle = if (state.campus == Campus.XITUCHENG)
                                    MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                fittingLabels = if (state.campus == Campus.XITUCHENG) westFittingLabels else categoryLabels,
                                onMoving = { buildingMoving = it }) { building = categories[it] }
                            Box(Modifier.weight(ColumnWeight).height(wheelHeight), contentAlignment = Alignment.Center) {
                                if (buildingRooms.isEmpty()) Text(if (building == null) "暂无收藏" else "暂无教室",
                                    Modifier.testTag("expected_empty_classrooms"), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                else key(building,buildingRooms.map { it.id }) {
                                    val labels = buildingRooms.map { if (building == null) favoriteWheelLabel(it,state.campus) else it.name.substringAfter('-',it.name) }
                                    CylinderWheel("教室", labels,
                                        buildingRooms.indexOfFirst { it.id == roomId }.coerceAtLeast(0), "expected_classroom_wheel",
                                        Modifier.fillMaxWidth(), labelLines = if (building == null && state.campus != Campus.XITUCHENG) 2 else 1,
                                        textStyle = if (state.campus == Campus.XITUCHENG) MaterialTheme.typography.titleMedium
                                            else if (building == null) MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp,lineHeight = 22.sp)
                                            else MaterialTheme.typography.titleLarge,
                                        fittingLabels = if (state.campus == Campus.XITUCHENG) westFittingLabels else labels,
                                        onMoving = { roomMoving = it }) { roomId = buildingRooms[it].id }
                                }
                            }
                            LazyColumn(Modifier.weight(ColumnWeight).height(wheelHeight).testTag("expected_selected"),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(campusSelection,key = { it }) { id ->
                                    val room = byId[id]
                                    val label = room?.let(::expectedRoomLabel) ?: id.substringAfter(':')
                                    ExpectedSelectedRoom(label,room?.name ?: label,id) { onSelected(selected-id) }
                                }
                            }
                        }
                    }
                    ThemeDivider()
                    Row(Modifier.fillMaxWidth().guideTag("expected_actions"), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ExpectedDialogAction("清空期望教室",Modifier.weight(ColumnWeight).testTag("expected_clear"),
                            enabled = campusSelection.isNotEmpty(),onClick = { onSelected(selected-campusSelection.toSet()) }) {
                            AnimatedTrashIcon(campusSelection.isNotEmpty(),Modifier.size(24.dp).offset(y=(-1).dp).testTag("expected_clear_icon"))
                        }
                        ExpectedDialogAction(if (current?.id in selected) "取消期望教室" else "添加期望教室",
                            Modifier.weight(ColumnWeight).testTag("expected_pin"),
                            enabled = current != null && !buildingMoving && !roomMoving,onClick = { current?.let { room ->
                                onSelected(if (room.id in selected) selected-room.id else selected+room.id)
                            } }) {
                            Icon(if (current?.id in selected) Icons.Filled.PushPin else Icons.Outlined.PushPin,null,
                                Modifier.size(24.dp).graphicsLayer { rotationZ = ExpectedPinTilt }.testTag("expected_pin_icon"))
                        }
                        DialogExitAction(campusSelection.toSet()!=initialSelection,onDismiss,
                            Modifier.weight(ColumnWeight).testTag("expected_close"),
                            enabled=LocalUsageGuide.current?.stage!=GuideStage.EXPECT)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpectedColumnTitle(label: String,modifier: Modifier) {
    Text(label,modifier,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,maxLines = 1,overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ExpectedDialogAction(label: String,modifier: Modifier,enabled: Boolean = true,onClick: () -> Unit,
    icon: @Composable () -> Unit) {
    Box(modifier.height(48.dp).clickable(enabled = enabled,role = Role.Button,
        interactionSource = remember { MutableInteractionSource() },indication = null,onClick = onClick)
        .semantics { contentDescription = label },contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),content = icon)
    }
}

@Composable
private fun ExpectedSelectedRoom(label: String,fullName: String,id: String,onRemove: () -> Unit) {
    val roles = themeRoles()
    Surface(Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag("expected_selected_$id")
        .clickable(role = Role.Button,interactionSource = remember { MutableInteractionSource() },indication = null,onClick = onRemove)
        .semantics { contentDescription = "移除期望教室 $fullName" },shape = MaterialTheme.shapes.small,
        color = roles.selectionFill ?: MaterialTheme.colorScheme.primaryContainer,
        contentColor = roles.onSelection ?: MaterialTheme.colorScheme.primary,
        border = if (roles.outlineExpectedSelection) BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant) else null) {
        Row(Modifier.padding(horizontal = 6.dp,vertical = 4.dp),verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            WidthFittedText(label,Modifier.weight(1f),style = MaterialTheme.typography.labelMedium,maxLines = 2)
            Icon(Icons.Outlined.Close,null,Modifier.size(12.dp))
        }
    }
}
