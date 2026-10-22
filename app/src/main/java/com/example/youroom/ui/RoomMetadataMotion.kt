package com.example.youroom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class RoomMetadataMotion(initialLabels: List<String>, initialNote: String) {
    var labels by mutableStateOf(initialLabels); private set
    var note by mutableStateOf(initialNote); private set
    val labelsAlpha = Animatable(1f)
    val noteAlpha = Animatable(1f)
    fun settled(targetLabels:List<String>,targetNote:String) = labels==targetLabels && note==targetNote &&
        labelsAlpha.value>=.999f && noteAlpha.value>=.999f

    @Composable
    fun Run(targetLabels: List<String>, targetNote: String, editing: Boolean, enabled: Boolean, closed: () -> Boolean) {
        LaunchedEffect(targetLabels, targetNote, editing, enabled) {
            labelsAlpha.snapTo(1f); noteAlpha.snapTo(1f)
            if (!enabled) { labels = targetLabels; note = targetNote; return@LaunchedEffect }
            // Persistence remains immediate; only the row's presentation is frozen.
            if (editing || (labels == targetLabels && note == targetNote)) return@LaunchedEffect
            snapshotFlow(closed).first { it }
            awaitRoomMotionDelay(80)
            coroutineScope {
                if (labels != targetLabels) launch {
                    labelsAlpha.snapTo(0f); labels = targetLabels
                    labelsAlpha.animateTo(1f, tween(220))
                }
                if (note != targetNote) launch {
                    noteAlpha.snapTo(0f); note = targetNote
                    noteAlpha.animateTo(1f, tween(220))
                }
            }
        }
    }
}

/** Frame-clock based so delayed reveals obey Compose's animation/test clock. */
internal suspend fun awaitRoomMotionDelay(millis: Long) {
    if (millis <= 0L) return
    val start = withFrameNanos { it }
    var elapsed = 0L
    while (elapsed < millis * 1_000_000L) elapsed = withFrameNanos { it } - start
}
