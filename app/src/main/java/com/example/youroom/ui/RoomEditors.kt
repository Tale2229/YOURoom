package com.example.youroom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.youroom.domain.*

enum class RoomEditor { ACTIONS, TAGS, NOTE }

@Composable
fun RoomTagsDialog(room: RoomMatch, state: YouRoomState, onDismiss: () -> Unit,
    onChange: (RoomProfile) -> Unit, onAddTag: (String) -> Unit) {
    var draft by remember(room.classroom.id) { mutableStateOf(room.profile) }
    LaunchedEffect(room.profile) { draft = room.profile }
    fun update(profile: RoomProfile) { draft = profile; onChange(profile) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("room_tag_dialog"),
                shape = MaterialTheme.shapes.small, tonalElevation = 0.dp) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("教室标签", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Text(state.campus.label, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(room.classroom.name, Modifier.padding(top = 8.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f, fill = false).heightIn(max = 480.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState())) {
                        TagEditor(state.copy(filters = draft.tagSelection()),
                            { update(draft.withTagSelection(it)) }, { name ->
                                onAddTag(name); update(draft.withPersonalTag(name))
                            }, {}, {}, showFavorite = false, manageCatalog = false)
                    }
                    HorizontalDivider(Modifier.padding(top = 12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { update(draft.withoutPersonalTags()) },
                            modifier = Modifier.testTag("reset_room_tags")) { Text("重置") }
                        TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_room_tags")) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
fun RoomNoteDialog(room: RoomMatch, onDismiss: () -> Unit, onChange: (RoomProfile) -> Unit) {
    var draft by remember(room.classroom.id) { mutableStateOf(room.profile) }
    LaunchedEffect(room.profile) { draft = room.profile }
    fun update(value: String) { draft = draft.copy(note = value.take(500)); onChange(draft) }
    AlertDialog(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.small,
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 20.dp).testTag("room_note_dialog"),
        properties = DialogProperties(usePlatformDefaultWidth = false), containerColor = MaterialTheme.colorScheme.background,
        title = { Text("${room.classroom.name} · 备注", style = MaterialTheme.typography.titleLarge) },
        text = { OutlinedTextField(draft.note, ::update, Modifier.fillMaxWidth().testTag("room_note"),
            label = { Text("个人备注") }, minLines = 2, maxLines = 4) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_room_note")) { Text("关闭") } },
        dismissButton = { TextButton(onClick = { update("") }, modifier = Modifier.testTag("clear_room_note")) { Text("清空") } })
}

/** A compact management entry for preference records and planning segments, not a detail sheet. */
@Composable
fun RoomActionsDialog(room: RoomMatch, onDismiss: () -> Unit, onFavorite: () -> Unit,
    onTags: () -> Unit, onNote: () -> Unit, onExcluded: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.small,
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 20.dp).testTag("room_actions_dialog"),
        properties = DialogProperties(usePlatformDefaultWidth = false), containerColor = MaterialTheme.colorScheme.background,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(room.classroom.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge)
                ExcludedRoomMarker(room.profile)
            }
        },
        text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            RoomActionButtons(room.profile, onFavorite, onTags, onNote, onExcluded)
        } },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_room_actions")) { Text("关闭") } })
}
