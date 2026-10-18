package com.example.youroom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.youroom.domain.*

enum class RoomEditor { ACTIONS, TAGS, NOTE }

@Composable
fun RoomTagsDialog(room: RoomMatch, state: YOURoomState, onDismiss: () -> Unit,
    onChange: (RoomProfile) -> Unit, onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit, onTagOrder: (List<String>) -> Unit) {
    var draft by remember(room.classroom.id) { mutableStateOf(room.profile) }
    var editingTags by remember { mutableStateOf(false) }
    LaunchedEffect(room.profile) { draft = room.profile }
    fun update(profile: RoomProfile) { draft = profile; onChange(profile) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("room_tag_dialog"),
                shape = MaterialTheme.shapes.small, tonalElevation = 0.dp) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("教室标签", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(state.campus.label, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(room.classroom.name, Modifier.padding(top = 8.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f, fill = false).heightIn(max = 480.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState())) {
                        TagEditor(state.copy(filters = draft.tagSelection()),
                            { update(draft.withTagSelection(it)) }, { name ->
                                onAddTag(name); update(draft.withPersonalTag(name))
                            }, onRemoveTag, onTagOrder, showFavorite = false,
                            editing = editingTags)
                    }
                    HorizontalDivider(Modifier.padding(top = 12.dp))
                    DialogFooterActions(draft.allTags.isNotEmpty() || draft.outlets, "reset_room_tags", "close_room_tags",
                        { update(draft.withoutPersonalTags()) }, onDismiss,
                        middleTag = "edit_tags", middleDescription = if (editingTags) "结束词条编辑" else "添加词条",
                        middleSelected = editingTags, onMiddle = { editingTags = !editingTags },
                        resetIconOffsetY = (-1).dp)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RoomNoteDialog(room: RoomMatch, onDismiss: () -> Unit, onChange: (RoomProfile) -> Unit) {
    // Old notes remain stored until explicitly edited; just limit the editor's draft.
    var draft by remember(room.classroom.id) { mutableStateOf(room.profile.copy(note = RoomNotes.limit(room.profile.note))) }
    LaunchedEffect(room.profile) { draft = room.profile.copy(note = RoomNotes.limit(room.profile.note)) }
    fun update(value: String) { draft = draft.copy(note = RoomNotes.limit(value)); onChange(draft) }
    val interaction=remember { MutableInteractionSource() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.imePadding().widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 20.dp)
            .testTag("room_note_dialog"), shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${room.classroom.name} · 备注", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().testTag("room_note_frame")) {
                        BasicTextField(draft.note,::update,Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("room_note")
                            .semantics { contentDescription="个人备注" },interactionSource=interaction,
                            minLines=1,maxLines=4,textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),
                            cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),decorationBox={ inner ->
                                OutlinedTextFieldDefaults.DecorationBox(draft.note,inner,true,false,VisualTransformation.None,interaction,
                                    label={ Text("个人备注",Modifier.testTag("room_note_label")) },
                                    trailingIcon={
                                        IconButton(onClick={update("")},modifier=Modifier.size(48.dp).testTag("clear_room_note")) {
                                            AnimatedTrashIcon(draft.note.isNotEmpty(),Modifier.size(24.dp)
                                                .semantics { contentDescription="清空备注" })
                                        }
                                    },contentPadding=PaddingValues(start=12.dp,top=16.dp,end=12.dp,bottom=24.dp),
                                    container={ OutlinedTextFieldDefaults.Container(true,false,interaction,
                                        colors=OutlinedTextFieldDefaults.colors(),shape=MaterialTheme.shapes.small) })
                            })
                        Text("${RoomNotes.length(draft.note)}/${RoomNotes.maximumLength}",
                            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 4.dp)
                                .testTag("room_note_count"), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)
                        .testTag("close_room_note")) {
                        DialogExitIcon(description = "关闭", size = 22.dp)
                    }
                }
            }
        }
    }
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
