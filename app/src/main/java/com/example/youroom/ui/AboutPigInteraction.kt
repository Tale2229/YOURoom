package com.example.youroom.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/** Session-local FIFO: every tap gets exactly one nudge, never cancels a nudge. */
internal class AboutPigInteraction {
    private val clicks=Channel<Unit>(Channel.UNLIMITED)
    var squinting by mutableStateOf(false)
        private set

    fun click() { clicks.trySend(Unit) }

    suspend fun play(animate:suspend ()->Unit) {
        try {
            for(click in clicks) {
                squinting=true
                try { animate() } finally { squinting=false }
                // Only one frame of rest; no effect restarts or queued cooldown.
                delay(16)
            }
        } finally {
            squinting=false
            clicks.cancel() // Disposing the sheet discards its private pending taps.
        }
    }
}
