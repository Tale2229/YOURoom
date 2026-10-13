package com.example.youroom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.youroom.domain.LessonSlot

internal class SlotTimeTooltipPositionProvider(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        fun constrain(position: Int, window: Int, content: Int): Int {
            val maximum = (window - content - margin).coerceAtLeast(0)
            return position.coerceIn(margin.coerceAtMost(maximum), maximum)
        }
        return IntOffset(
            constrain(anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
                windowSize.width, popupContentSize.width),
            constrain(anchorBounds.top - gap - popupContentSize.height, windowSize.height, popupContentSize.height))
    }
}

@Composable
internal fun SlotTimeTooltip(slot: LessonSlot) {
    val density = LocalDensity.current
    val position = remember(density) {
        with(density) { SlotTimeTooltipPositionProvider(gap = 28.dp.roundToPx(), margin = 8.dp.roundToPx()) }
    }
    Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false,
        dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.testTag("slot_time_tooltip").semantics { liveRegion = LiveRegionMode.Polite },
            shape = RectangleShape, color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), shadowElevation = 4.dp) {
            Text("${slot.start}—${slot.end}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("slot_time_label_${slot.number}"),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
        }
    }
}
