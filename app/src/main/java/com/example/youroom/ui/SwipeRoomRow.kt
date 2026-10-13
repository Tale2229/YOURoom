package com.example.youroom.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt

private val RoomActionRailWidth = 184.dp // Four compact 40dp buttons, plus 12dp on each side.
private class RowSwipeAnimation { var job: Job? = null }

@Composable
private fun FavoriteCornerMarker(roomId: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(22.dp).testTag("room_favorite_$roomId").clearAndSetSemantics {}) {
        drawPath(Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); close()
        }, color)
    }
}

@Composable
internal fun RoomActionButtons(profile: RoomProfile, onFavorite: () -> Unit, onTags: () -> Unit,
    onNote: () -> Unit, onExcluded: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val buttons = listOf(
            Triple(if (profile.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                if (profile.favorite) "取消收藏 ${profile.name}" else "收藏 ${profile.name}", onFavorite),
            Triple(Icons.AutoMirrored.Outlined.Label, "修改标签 ${profile.name}", onTags),
            Triple(Icons.Outlined.Edit, "修改备注 ${profile.name}", onNote),
            Triple(if (profile.excluded) Icons.Filled.Cancel else Icons.Outlined.Block,
                if (profile.excluded) "取消排除 ${profile.name}" else "排除 ${profile.name}", onExcluded))
        buttons.forEachIndexed { index, (icon, description, action) ->
            IconButton(onClick = action, enabled = enabled, modifier = Modifier.width(if (compact) 40.dp else 48.dp).heightIn(min = 48.dp)
                .testTag("room_action_${profile.roomId}_$index")) {
                Icon(icon, description, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RoomActionRail(profile: RoomProfile, onFavorite: () -> Unit, onTags: () -> Unit,
    onNote: () -> Unit, onExcluded: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        HorizontalDivider(Modifier.fillMaxWidth()
            .testTag("room_action_line_${profile.roomId}_top_thin"), color = colors.outlineVariant)
        RoomActionButtons(profile, onFavorite, onTags, onNote, onExcluded, enabled = enabled, compact = true)
        // Both separators end 16dp above the row bottom, so they share the same baseline.
        HorizontalDivider(Modifier.fillMaxWidth()
            .testTag("room_action_line_${profile.roomId}_bottom_thin"), color = colors.outlineVariant)
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
    onNote: () -> Unit, onExcluded: () -> Unit, modifier: Modifier = Modifier, inspectionKey: Any? = null,
    interactionEnabled: Boolean = active, favoriteClosing: Boolean = false) {
    val profile = match.profile
    val swipe = remember(profile.roomId) { AnchoredDraggableState(false) }
    val density = LocalDensity.current.density
    val railWidth = with(LocalDensity.current) { RoomActionRailWidth.toPx() }
    val anchors = remember(railWidth) { DraggableAnchors { false at 0f; true at -railWidth } }
    SideEffect { swipe.updateAnchors(anchors) }
    val animation = remember(profile.roomId) { RowSwipeAnimation() }
    var dragging by remember(profile.roomId) { mutableStateOf(false) }
    var settlement by remember(profile.roomId) { mutableIntStateOf(0) }
    val latestExpanded by rememberUpdatedState(onExpanded)
    val latestActive by rememberUpdatedState(active)
    val latestIntent by rememberUpdatedState(expanded)
    // One owner animates toward the explicit request. Never feed an old settledValue
    // back into the parent while a newer drag or favorite-close is in progress.
    LaunchedEffect(expanded, active, railWidth, dragging, settlement, favoriteClosing) {
        val ownJob = currentCoroutineContext().job
        animation.job = ownJob
        try {
            if (!active) swipe.snapTo(false)
            else if (!dragging) swipe.animateTo(expanded,
                tween(if (favoriteClosing && !expanded) FavoriteMotionTiming.Close else 160))
        } finally {
            if (animation.job === ownJob) animation.job = null
        }
    }
    val revealed by remember { derivedStateOf { !swipe.offset.isNaN() && swipe.offset < -.5f } }
    val labels = remember(profile, terms) { terms.filter { it != PersonalTags.favorite && profile.hasTerm(it) } }
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().clipToBounds().testTag("room_row_${profile.roomId}").semantics {
        stateDescription = listOfNotNull(if (profile.favorite) "已收藏" else null,
            if (profile.excluded) "已排除" else null).joinToString("，")
        if (active && interactionEnabled) customActions = listOf(CustomAccessibilityAction(if (expanded) "收起教室操作" else "展开教室操作") {
            latestExpanded(!expanded); true
        })
    }.pointerInput(swipe, active, interactionEnabled, railWidth, density, inspectionKey) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!active || !interactionEnabled) return@awaitEachGesture
            val before = latestIntent
            val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var startOffset = 0f
            var claimed = false
            var finished = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    // The strip consumes Initial-pass events after its 250ms dwell.
                    // Before that it observes only, so this stable row can own a swipe.
                    if (change.isConsumed || event.changes.count { it.pressed } > 1) break
                    velocity.addPosition(change.uptimeMillis, change.position)
                    val delta = change.position - down.position
                    if (!claimed) {
                        if (!change.pressed) break
                        if (abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) >= abs(delta.x)) break
                        if (abs(delta.x) <= viewConfiguration.touchSlop || abs(delta.x) <= abs(delta.y) * 1.2f) continue
                        animation.job?.cancel()
                        startOffset = if (swipe.offset.isNaN()) 0f else swipe.requireOffset()
                        claimed = true; dragging = true
                        swipe.dispatchRawDelta(delta.x - sign(delta.x) * viewConfiguration.touchSlop)
                    } else {
                        swipe.dispatchRawDelta(change.positionChange().x)
                    }
                    change.consume()
                    if (!change.pressed) {
                        val open = RoomSwipePolicy.target(startOffset / density, swipe.requireOffset() / density,
                            railWidth / density, velocity.calculateVelocity().x / density)
                        finished = true
                        if (latestActive) latestExpanded(open)
                        break
                    }
                }
            } finally {
                if (claimed) {
                    dragging = false
                    if (!finished && latestActive) latestExpanded(before)
                    // A whole fast/cancelled gesture may happen between compositions:
                    // true -> false alone would not restart the cancelled effect.
                    settlement++
                }
            }
        }
    }) {
        if (revealed && active) Box(Modifier.matchParentSize().background(colors.surfaceContainer)) {
            val actionsReady = expanded && swipe.settledValue && interactionEnabled
            RoomActionRail(profile, { latestExpanded(false); onFavorite() }, onTags, onNote, onExcluded,
                Modifier.align(AbsoluteAlignment.CenterRight).width(RoomActionRailWidth).testTag("room_actions_${profile.roomId}")
                    .then(if (actionsReady) Modifier else Modifier.clearAndSetSemantics {}), enabled = actionsReady)
        }
        Box(Modifier.fillMaxWidth().absoluteOffset {
            IntOffset(if (swipe.offset.isNaN()) 0 else swipe.requireOffset().roundToInt(), 0)
        }.background(colors.background, RectangleShape).testTag("room_face_${profile.roomId}")
            .then(if (expanded) Modifier.clickable(enabled = interactionEnabled) { latestExpanded(false) } else Modifier)) {
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().testTag("room_swipe_${profile.roomId}")
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
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
                RoomAvailabilityStrip(profile.roomId, match.freeSlots, slots, active && interactionEnabled && !expanded && !revealed,
                    inspectionKey = inspectionKey)
                HorizontalDivider(Modifier.padding(horizontal = 24.dp).testTag("room_divider_${profile.roomId}"))
                Spacer(Modifier.height(16.dp))
            }
            if (profile.favorite) FavoriteCornerMarker(profile.roomId,
                Modifier.align(AbsoluteAlignment.TopRight).absoluteOffset(x = (-24).dp))
        }
    }
}
