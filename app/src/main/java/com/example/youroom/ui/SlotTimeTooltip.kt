package com.example.youroom.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.youroom.domain.LessonSlot
import com.example.youroom.domain.SlotBounds
import kotlin.math.roundToInt

private fun constrainTooltip(position: IntOffset, window: IntSize, content: IntSize, margin: Int): IntOffset {
    fun constrain(value: Int, space: Int, size: Int): Int {
        val maximum = (space - size - margin).coerceAtLeast(0)
        return value.coerceIn(margin.coerceAtMost(maximum), maximum)
    }
    return IntOffset(constrain(position.x, window.width, content.width), constrain(position.y, window.height, content.height))
}

/** The popup is anchored once to the grid; only its selected rectangle changes. */
internal class GridSlotTooltipPositionProvider(private val face: () -> SlotBounds?, private val origin: () -> Offset,
    private val delegate: PopupPositionProvider) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val rectangle = face()?.let { bounds ->
            val offset = origin()
            IntRect(anchorBounds.left + (bounds.left - offset.x).roundToInt(),
                anchorBounds.top + (bounds.top - offset.y).roundToInt(),
                anchorBounds.left + (bounds.right - offset.x).roundToInt(),
                anchorBounds.top + (bounds.bottom - offset.y).roundToInt())
        } ?: anchorBounds
        return delegate.calculatePosition(rectangle, windowSize, layoutDirection, popupContentSize)
    }
}

internal class TooltipPositionMotion {
    val target = mutableStateOf<IntOffset?>(null)
    val position = Animatable(Offset.Zero, Offset.VectorConverter)
    var initialized by mutableStateOf(false)
        private set
    @Composable fun Run() {
        val destination = target.value
        LaunchedEffect(destination) {
            destination ?: return@LaunchedEffect
            val offset = Offset(destination.x.toFloat(), destination.y.toFloat())
            if (!initialized) { position.snapTo(offset); initialized = true }
            else position.animateTo(offset, tween(110))
        }
    }
}

internal class SmoothTooltipPositionProvider(private val delegate: PopupPositionProvider,
    private val motion: TooltipPositionMotion, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val target = delegate.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
        if (motion.target.value != target) motion.target.value = target
        // Android Popup observes state reads in calculatePosition, so these frames
        // move the same window without rebuilding the grid or its text every frame.
        val position = if (motion.initialized) motion.position.value.let {
            IntOffset(it.x.roundToInt(), it.y.roundToInt())
        } else target
        return constrainTooltip(position, windowSize, popupContentSize, margin)
    }
}

internal class SlotTimeTooltipPositionProvider(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        return constrainTooltip(IntOffset(anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
            anchorBounds.top - gap - popupContentSize.height), windowSize, popupContentSize, margin)
    }
}

@Composable
internal fun SlotTimeTooltip(slot: LessonSlot, tagPrefix: String = "slot_time",
    positionOverride: PopupPositionProvider? = null, smoothPosition: Boolean = false, visible: Boolean = true) {
    val density = LocalDensity.current
    val tooltipRule = MaterialTheme.colorScheme.outline
    val position = remember(density) {
        with(density) { SlotTimeTooltipPositionProvider(gap = 28.dp.roundToPx(), margin = 8.dp.roundToPx()) }
    }
    val delegate = positionOverride ?: position
    val motion = remember { TooltipPositionMotion() }
    val margin = with(density) { 8.dp.roundToPx() }
    val provider = if (smoothPosition) {
        motion.Run()
        remember(delegate, motion, margin) { SmoothTooltipPositionProvider(delegate, motion, margin) }
    } else delegate
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false,
        dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.graphicsLayer { alpha = if (visible) 1f else 0f }
            .then(if (visible) Modifier.testTag("${tagPrefix}_tooltip").semantics { liveRegion = LiveRegionMode.Polite }
                else Modifier.clearAndSetSemantics {}),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary, tonalElevation = 0.dp, shadowElevation = 0.dp) {
            Text("${slot.number} · ${slot.timeLabel}",
                Modifier.drawBehind {
                    val stroke = 2.dp.toPx()
                    drawLine(tooltipRule, androidx.compose.ui.geometry.Offset(0f,size.height-stroke/2),
                        androidx.compose.ui.geometry.Offset(size.width,size.height-stroke/2),stroke)
                }.padding(horizontal = 12.dp, vertical = 6.dp).testTag("${tagPrefix}_label_${slot.number}"),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
        }
    }
}
