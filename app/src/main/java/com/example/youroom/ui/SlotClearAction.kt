package com.example.youroom.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Fixed filled body with transparent grooves; only its separate lid rotates. */
@Composable
internal fun SlotClearAction(hasSelection: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.height(48.dp).testTag("clear_slots")
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            enabled = enabled && hasSelection, role = Role.Button, onClick = onClick)
        .semantics {
            contentDescription = "清空所选时段"
            stateDescription = if (hasSelection) "有选中时段" else "未选择时段"
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(36.dp).testTag("clear_slots_face"), contentAlignment = Alignment.Center) {
            AnimatedTrashIcon(hasSelection, Modifier.size(24.dp).testTag("clear_slots_icon"))
        }
    }
}

/** Shared icon only: callers supply their own hit area and accessibility description. */
@Composable
internal fun AnimatedTrashIcon(hasSelection: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val angle = animateFloatAsState(if (hasSelection) 12f else 0f, tween(220), label = "trash_lid")
    val tint = animateThemeColorAsState(if (hasSelection) colors.primary else colors.onSurfaceVariant,
        tween(220), label = "trash_tint")
    val body = remember {
        PathParser().parsePathString("M5.5 9H18.5L17.4 19.1Q17.3 20 16.4 20H7.6Q6.7 20 6.6 19.1Z " +
            "M9 11H10.5V17H9Z M13.5 11H15V17H13.5Z").toPath().apply { fillType = PathFillType.EvenOdd }
    }
    val lid = remember {
        PathParser().parsePathString("M4.5 6H19.5V8H4.5Z M9 6V4.5H15V6H13.5V5.8H10.5V6Z").toPath()
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        // Read animated state only while drawing; no per-frame grid recomposition or search.
        scale(size.width / 24f, size.height / 27f, Offset.Zero) {
            translate(top = 3f) {
                drawPath(body, tint.value)
                rotate(angle.value, Offset(19.5f, 7f)) { drawPath(lid, tint.value) }
            }
        }
    }
}
