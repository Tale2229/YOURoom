package com.example.youroom.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun RoomActionButtons(profile: RoomProfile, onFavorite: () -> Unit, onTags: () -> Unit,
    onNote: () -> Unit, onExcluded: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val buttons = listOf(
            Triple(if (profile.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                if (profile.favorite) "取消收藏 ${profile.name}" else "收藏 ${profile.name}", onFavorite),
            Triple(Icons.AutoMirrored.Outlined.Label, "修改标签 ${profile.name}", onTags),
            Triple(Icons.Outlined.Edit, "修改备注 ${profile.name}", onNote),
            Triple(if (profile.excluded) Icons.Filled.Cancel else Icons.Outlined.Block,
                if (profile.excluded) "取消排除 ${profile.name}" else "排除 ${profile.name}", onExcluded))
        buttons.forEachIndexed { index, (icon, description, action) ->
            IconButton(onClick = action, enabled = enabled, modifier = Modifier.width(48.dp).heightIn(min = 48.dp)
                .testTag("room_action_${profile.roomId}_$index")) {
                Icon(icon, description, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun ExcludedRoomMarker(profile: RoomProfile) {
    if (profile.excluded) Icon(Icons.Outlined.HighlightOff, "已排除 ${profile.name}",
        Modifier.size(20.dp).testTag("room_excluded_${profile.roomId}"), tint = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun SwipeRoomRow(match: RoomMatch, slots: List<LessonSlot>, terms: List<String>, expanded: Boolean,
    active: Boolean, onExpanded: (Boolean) -> Unit, onFavorite: () -> Unit, onTags: () -> Unit,
    onNote: () -> Unit, onExcluded: () -> Unit, modifier: Modifier = Modifier, inspectionKey: Any? = null) {
    val profile = match.profile
    val swipe = remember(profile.roomId) { AnchoredDraggableState(false) }
    val railWidth = with(LocalDensity.current) { 192.dp.toPx() }
    val anchors = remember(railWidth) { DraggableAnchors { false at 0f; true at -railWidth } }
    SideEffect { swipe.updateAnchors(anchors) }
    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val latestExpanded by rememberUpdatedState(onExpanded)
    val latestActive by rememberUpdatedState(active)
    LaunchedEffect(expanded, active) {
        if (!active) { settleJob?.cancel(); swipe.snapTo(false) }
        else if (swipe.settledValue != expanded) swipe.animateTo(expanded, tween(160))
    }
    LaunchedEffect(swipe) {
        snapshotFlow { swipe.settledValue }.distinctUntilChanged().collect {
            if (latestActive) latestExpanded(it)
        }
    }
    val revealed by remember { derivedStateOf { !swipe.offset.isNaN() && swipe.offset < -.5f } }
    val labels = remember(profile, terms) { terms.filter { it != PersonalTags.favorite && profile.hasTerm(it) } }
    val colors = MaterialTheme.colorScheme
    val background = if (profile.favorite) colors.primaryContainer else colors.background
    Box(modifier.fillMaxWidth().clipToBounds().testTag("room_row_${profile.roomId}").semantics {
        stateDescription = listOfNotNull(if (profile.favorite) "已收藏" else null,
            if (profile.excluded) "已排除" else null).joinToString("，")
        if (active) customActions = listOf(CustomAccessibilityAction(if (expanded) "收起教室操作" else "展开教室操作") {
            latestExpanded(!expanded); true
        })
    }) {
        if (revealed && active) Box(Modifier.matchParentSize().background(colors.surfaceContainer)) {
            val actionsReady = expanded && swipe.settledValue
            RoomActionButtons(profile, onFavorite, onTags, onNote, onExcluded,
                Modifier.align(AbsoluteAlignment.CenterRight).width(192.dp).testTag("room_actions_${profile.roomId}")
                    .then(if (actionsReady) Modifier else Modifier.clearAndSetSemantics {}), enabled = actionsReady)
        }
        Column(Modifier.fillMaxWidth().absoluteOffset {
            IntOffset(if (swipe.offset.isNaN()) 0 else swipe.requireOffset().roundToInt(), 0)
        }.background(background, RectangleShape).testTag("room_face_${profile.roomId}")
            .then(if (expanded) Modifier.clickable { latestExpanded(false) } else Modifier)) {
            Column(Modifier.fillMaxWidth().testTag("room_swipe_${profile.roomId}").pointerInput(swipe, active, railWidth) {
                if (active) {
                    var before = false
                    val velocity = VelocityTracker()
                    fun settle(open: Boolean) {
                        settleJob?.cancel()
                        settleJob = scope.launch { swipe.animateTo(open, tween(160)) }
                    }
                    // Only the upper region owns row swipes; the lower region inspects time.
                    detectHorizontalDragGestures(onDragStart = {
                        settleJob?.cancel(); before = swipe.settledValue; velocity.resetTracking()
                    }, onDragEnd = {
                        val speed = velocity.calculateVelocity().x
                        val threshold = railWidth * 1.7f
                        settle(when {
                            speed < -threshold -> true
                            speed > threshold -> false
                            else -> swipe.requireOffset() < -railWidth / 2
                        })
                    }, onDragCancel = { settle(if (latestActive) before else false) }) { change, delta ->
                        change.consume(); velocity.addPosition(change.uptimeMillis, change.position)
                        swipe.dispatchRawDelta(delta)
                    }
                }
            }.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ExcludedRoomMarker(profile)
                }
                Text(listOfNotNull(match.classroom.buildingLabel, match.classroom.capacity?.let { "容量 $it" })
                    .plus(labels).joinToString(" · "), Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (profile.note.isNotBlank()) Text(profile.note, Modifier.padding(top = 4.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            RoomAvailabilityStrip(profile.roomId, match.freeSlots, slots, active && !expanded && !revealed,
                inspectionKey = inspectionKey)
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}
