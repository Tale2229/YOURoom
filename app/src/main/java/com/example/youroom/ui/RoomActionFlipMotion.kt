package com.example.youroom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import com.example.youroom.domain.RoomActionFlip
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Only the icon layers read these angles. No list recomposition on each frame. */
internal class RoomActionFlipMotion {
    val angles = List(4) { Animatable(0f) }

    @Composable
    fun Observe(offsetDp: () -> Float, dragSpeed: () -> Float, dragging: () -> Boolean, enabled: Boolean) {
        LaunchedEffect(enabled) {
            val jobs = arrayOfNulls<Job>(4)
            if (!enabled) { angles.forEach { it.snapTo(0f) }; return@LaunchedEffect }
            var before = (-offsetDp()).takeIf { it.isFinite() }?.coerceIn(0f, RoomActionFlip.railWidthDp) ?: 0f
            var time = System.nanoTime()
            val armed = BooleanArray(4) { before < RoomActionFlip.threshold(it) }
            try {
                snapshotFlow { offsetDp() }.collect { offset ->
                    if (!offset.isFinite()) return@collect
                    val after = (-offset).coerceIn(0f, RoomActionFlip.railWidthDp)
                    val now = System.nanoTime()
                    val elapsed = (now - time) / 1_000_000_000f
                    val speed = if (dragging()) dragSpeed()
                        else if (elapsed > 0f) (after - before) / elapsed else 0f
                    for (index in 0..3) if (after < RoomActionFlip.threshold(index) - 4f) armed[index] = true
                    RoomActionFlip.crossings(before, after).filter { armed[it] }.forEachIndexed { batch, index ->
                        armed[index] = false
                        jobs[index]?.cancel()
                        jobs[index] = launch {
                            // Multiple thresholds can be crossed in a single input frame.
                            // Keep a small right-to-left sequence even for that fast pull.
                            awaitRoomMotionDelay(batch * RoomActionFlip.batchDelayMillis)
                            angles[index].snapTo(0f)
                            angles[index].animateTo(360f, spring(dampingRatio = RoomActionFlip.dampingRatio,
                                stiffness = RoomActionFlip.stiffness(speed)),
                                initialVelocity = RoomActionFlip.initialVelocity(speed))
                        }
                    }
                    before = after; time = now
                }
            } finally { jobs.forEach { it?.cancel() } }
        }
    }
}
