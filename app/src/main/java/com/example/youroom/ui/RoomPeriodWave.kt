package com.example.youroom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import com.example.youroom.domain.PeriodWave

/** One clock per row; its progress is read by drawing, never by list composition. */
internal class RoomPeriodWave {
    private val progress = Animatable(1f)
    private var interrupted by mutableStateOf(false)
    private var played = false
    private var hasContext = false
    private var previousContext: Any? = null

    fun interrupt() { interrupted = true }
    fun amplitude(visualIndex: Int, count: Int): Float =
        if (interrupted) 0f else PeriodWave.amplitude(progress.value, visualIndex, count)

    @Composable
    fun Run(expanded: Boolean, enabled: Boolean, dragging: Boolean, settled: Boolean, context: Any?) {
        LaunchedEffect(expanded, enabled, dragging, settled, context, interrupted) {
            // The incoming effect owns cleanup, so a cancelled old animation
            // cannot later overwrite the new opening's progress.
            progress.snapTo(1f)
            val changed = hasContext && previousContext != context
            hasContext = true; previousContext = context
            if (!expanded) { played = false; interrupted = false; return@LaunchedEffect }
            if (changed || !enabled || dragging || interrupted) {
                played = true
                if (changed || !enabled || dragging) interrupt()
                return@LaunchedEffect
            }
            if (!settled || played) return@LaunchedEffect
            played = true
            progress.snapTo(0f)
            progress.animateTo(1f, tween(PeriodWave.durationMillis, easing = LinearEasing))
        }
    }
}
