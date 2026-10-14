package com.example.youroom.ui

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import com.example.youroom.domain.PeriodWave
import kotlinx.coroutines.flow.first

/** One draw-only pulse per opening. Input changes never cancel an already-started pass. */
internal class RoomPeriodWave {
    private var running by mutableStateOf(false)
    private var request by mutableIntStateOf(0)
    private val progress = Animatable(1f)
    private var consumed = false
    private var hasContext = false
    private var previousContext: Any? = null

    val isRunning: Boolean get() = running

    fun interrupt() { consumed = true }

    suspend fun awaitStopped() { snapshotFlow { running }.first { !it } }

    fun amplitude(visualIndex: Int, count: Int): Float {
        if (!running) return 0f
        return PeriodWave.amplitude(progress.value, visualIndex, count)
    }

    @Composable
    fun Run(expanded: Boolean, enabled: Boolean, dragging: Boolean, settled: Boolean, context: Any?) {
        // Only start/end flags recompose; changing progress is read in drawBehind.
        val playing = running
        LaunchedEffect(request) {
            if (request != 0) {
                try {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(PeriodWave.durationMillis, easing = LinearEasing))
                } finally { running = false }
            }
        }
        SideEffect {
            val changed = hasContext && previousContext != context
            hasContext = true; previousContext = context
            when {
                !expanded -> consumed = false
                changed || !enabled || dragging -> consumed = true
                !settled -> Unit
                !consumed && !playing -> { consumed = true; running = true; request++ }
            }
        }
    }
}
