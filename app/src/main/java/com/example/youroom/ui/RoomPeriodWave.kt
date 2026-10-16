package com.example.youroom.ui

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import com.example.youroom.domain.PeriodWave
import kotlinx.coroutines.flow.first

/** An explicitly requested closing pulse. Once started, always finish right to left. */
internal class RoomPeriodWave {
    private var running by mutableStateOf(false)
    private var request by mutableIntStateOf(0)
    private val progress = Animatable(1f)

    val isRunning: Boolean get() = running

    fun play() {
        if (!running) { running = true; request++ }
    }

    suspend fun awaitStopped() { snapshotFlow { running }.first { !it } }

    fun amplitude(visualIndex: Int, count: Int): Float {
        if (!running) return 0f
        return PeriodWave.amplitude(progress.value, visualIndex, count)
    }

    @Composable
    fun Run() {
        LaunchedEffect(request) {
            if (request != 0) {
                try {
                    progress.snapTo(0f)
                    awaitRoomMotionDelay(PeriodWave.startDelayMillis)
                    progress.animateTo(1f, tween(PeriodWave.durationMillis, easing = LinearEasing))
                } finally { running = false }
            }
        }
    }
}
