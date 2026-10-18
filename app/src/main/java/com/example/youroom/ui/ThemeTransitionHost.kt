package com.example.youroom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.YOURoomTheme
import kotlinx.coroutines.*

internal val ThemePageWaveProgress = SemanticsPropertyKey<Float>("ThemePageWaveProgress")
internal val ThemePendingTone = SemanticsPropertyKey<String>("ThemePendingTone")
internal data class ThemePreview(val appearance: AppearancePreferences, val busy: Boolean,
    val onBrowsing: (Boolean) -> Unit = {})
internal val LocalThemePreview = staticCompositionLocalOf<ThemePreview?> { null }
private data class ToneRequest(val campus: Campus, val tone: CampusTone, val revision: Long)

/** One mounted UI tree. Before/after frames are captured once, then deformed through
 * one shared mesh. No per-frame snapshots or global theme interpolation. */
@Composable
internal fun ThemeTransitionHost(campus: Campus, appearance: AppearancePreferences, contextKey: Any,
    enabled: Boolean, onTone: (Campus, CampusTone) -> Unit, darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable ((Campus, CampusTone) -> Unit) -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(1f) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val mesh = remember(rootSize, density.density) {
        if (rootSize.width > 0 && rootSize.height > 0)
            ThemeWaveMesh(rootSize.width.toFloat(), rootSize.height.toFloat(), with(density) { 60.dp.toPx() }) else null
    }
    var request by remember { mutableStateOf<ToneRequest?>(null) }
    var revision by remember { mutableLongStateOf(0) }
    var committedRevision by remember { mutableLongStateOf(-1) }
    var oldFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var frames by remember { mutableStateOf<ThemeWaveFrames?>(null) }
    var waveJob by remember { mutableStateOf<Job?>(null) }
    var browsing by remember { mutableStateOf(false) }
    val latestAppearance by rememberUpdatedState(appearance)
    val latestCampus by rememberUpdatedState(campus)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnTone by rememberUpdatedState(onTone)
    fun reset() {
        val pending = request
        request = null; waveJob?.cancel(); oldFrame = null; frames = null; browsing = false
        // Navigating away is not an implicit "cancel theme" action. Keep the final
        // choice, but don't carry a settings screenshot/wave into another page.
        if (pending != null && pending.revision != committedRevision && latestAppearance.tone(pending.campus) != pending.tone)
            latestOnTone(pending.campus, pending.tone)
    }
    // Leaving the page/campus, rotating, backgrounding or changing night mode never replays a wave.
    LaunchedEffect(campus, contextKey, enabled, darkTheme, rootSize) { reset() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { reset() }
    LaunchedEffect(request, browsing) {
        if (browsing) return@LaunchedEffect
        val selected = request ?: return@LaunchedEffect
        delay(ThemePageWave.debounceMillis)
        ensureActive()
        if (request != selected || !latestEnabled || latestCampus != selected.campus) return@LaunchedEffect
        if (latestAppearance.tone(selected.campus) == selected.tone) { request = null; return@LaunchedEffect }
        val frozen = try {
            if (layer.size.width > 0 && layer.size.height > 0) withTimeoutOrNull(1000) { layer.toImageBitmap() } else null
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null // Capture is decorative: unsupported surfaces must still be able to change theme.
        }
        ensureActive()
        if (request != selected || !latestEnabled || latestCampus != selected.campus) return@LaunchedEffect
        progress.snapTo(0f)
        oldFrame = frozen
        committedRevision = selected.revision
        latestOnTone(selected.campus, selected.tone)
        waveJob = scope.launch {
            try {
                // Frame callbacks run before drawing. A second callback ensures
                // the newly applied palette has actually been recorded first.
                withFrameNanos { }
                withFrameNanos { }
                val next = if (frozen == null) null else try {
                    withTimeoutOrNull(1000) { layer.toImageBitmap() }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    null
                }
                if (frozen != null && next != null && frozen.width == next.width && frozen.height == next.height) {
                    frames = ThemeWaveFrames(frozen, next)
                    progress.animateTo(1f, tween(ThemePageWave.durationMillis, easing = LinearEasing))
                }
            } finally {
                oldFrame = null; frames = null
                waveJob = null
                if (request == selected) request = null
            }
        }
    }
    val preview = request?.takeIf { it.campus == campus }?.let { appearance.withTone(campus, it.tone) } ?: appearance
    val moving by rememberUpdatedState(oldFrame != null)
    YOURoomTheme(campus = campus, appearance = appearance, darkTheme = darkTheme) {
        CompositionLocalProvider(LocalThemePreview provides ThemePreview(preview, oldFrame != null) {
            if (campus == latestCampus && oldFrame == null) browsing = it
        }) {
            Box(Modifier.fillMaxSize().testTag("theme_transition_page").clipToBounds()
                .onSizeChanged { rootSize = it }
                .semantics {
                    this[ThemePageWaveProgress] = if (oldFrame == null) 1f else progress.value
                    this[ThemePendingTone] = request?.tone?.id.orEmpty()
                }.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            // Frozen frames also contain the selector now. All
                            // theme entry points unlock as soon as the wave ends.
                            if (moving) event.changes.forEach { it.consume() }
                        }
                    }
                }.drawWithContent {
                    // Once both frames exist, only mesh vertices/colours change.
                    // Resume live drawing immediately when the transition finishes.
                    if (frames == null) layer.record { this@drawWithContent.drawContent() }
                    val old = oldFrame
                    if (old == null || old.width != size.width.toInt() || old.height != size.height.toInt()) drawLayer(layer)
                    else {
                        val pair = frames
                        if (pair == null || mesh == null) drawImage(old)
                        else pair.draw(this, mesh, progress.value, 4.dp.toPx())
                    }
                }) {
                content { selectedCampus, tone ->
                    if (enabled && oldFrame == null && selectedCampus == campus)
                        request = ToneRequest(selectedCampus, tone, ++revision)
                }
            }
        }
    }
}
