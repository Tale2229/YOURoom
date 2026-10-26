package com.example.youroom.ui

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.PlanningPanelGesture

private class PlanningPointer { var pressed=false }

/** Only attached below the editor divider. The wheel columns never participate. */
@Composable
internal fun Modifier.planningPanelGestures(collapsed: Boolean, enabled: Boolean, atTop: () -> Boolean,
    onCollapsed: (Boolean) -> Unit): Modifier {
    val density=LocalDensity.current
    val gate=remember(density) { with(density) { PlanningPanelGesture(32.dp.toPx(),64.dp.toPx()) } }
    val pointer=remember(gate) { PlanningPointer() }
    val currentCollapsed by rememberUpdatedState(collapsed)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentAtTop by rememberUpdatedState(atTop)
    val update by rememberUpdatedState(onCollapsed)
    val connection=remember(gate) { object: NestedScrollConnection {
        fun drag(delta: Float): Offset {
            // Semantics scroll actions, restoration and flings are not finger pulls.
            if (!currentEnabled || !pointer.pressed) return Offset.Zero
            val decision=gate.drag(delta,currentCollapsed,currentAtTop())
            when (decision.action) {
                PlanningPanelGesture.Action.COLLAPSE -> update(true)
                PlanningPanelGesture.Action.EXPAND -> update(false)
                null -> Unit
            }
            return if (decision.consume) Offset(0f,delta) else Offset.Zero
        }
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source==NestedScrollSource.UserInput) drag(available.y) else Offset.Zero
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source==NestedScrollSource.UserInput && available.y>0) drag(available.y) else Offset.Zero
        override suspend fun onPreFling(available: Velocity): Velocity {
            val owned=gate.claimed; gate.reset()
            return if (owned) Velocity(0f,available.y) else Velocity.Zero
        }
    } }
    return this.pointerInput(gate) {
        // Reset on every fresh pointer sequence, including after a cancelled drag.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            gate.reset()
            pointer.pressed=true
            try {
                do { val event=awaitPointerEvent(PointerEventPass.Final) } while (event.changes.any { it.pressed })
            } finally { pointer.pressed=false }
        }
    }.nestedScroll(connection)
        // Supplies vertical drags in blank space and over non-scrollable headers too.
        // Actual detail scrolling belongs to the inner LazyColumn; this state never moves it.
        .scrollable(rememberScrollableState { 0f },Orientation.Vertical,enabled=enabled)
}
