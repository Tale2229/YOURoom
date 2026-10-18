package com.example.youroom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Separate the compact painted surface from adjacent, full-size hit areas. */
@Composable
internal fun PlanningPeriodMenu(expanded: Boolean, enabled: Boolean, index: Int,
    onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onSave: (() -> Unit)? = null) {
    val visibility=remember { MutableTransitionState(false) }
    visibility.targetState=expanded
    val density=LocalDensity.current
    val position=remember(density) { PlanningPeriodMenuPosition(with(density) { 8.dp.roundToPx() },with(density) { 4.dp.roundToPx() }) }
    if(visibility.currentState || visibility.targetState) Popup(position,onDismiss,
        properties=PopupProperties(focusable=true)) {
        AnimatedVisibility(visibility,enter=fadeIn(tween(100)),exit=fadeOut(tween(80))) {
            Box(Modifier.size(if (onSave != null) 144.dp else 96.dp,48.dp).testTag("planner_period_actions_$index"),contentAlignment=Alignment.Center) {
                Surface(Modifier.fillMaxWidth().height(37.dp).testTag("planner_period_actions_face_$index"),
                    shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surface,
                    shadowElevation=2.dp) {}
                Row {
                    IconButton(onClick=onEdit,enabled=enabled && expanded,
                        modifier=Modifier.size(48.dp).testTag("planner_edit_period_$index")) {
                        Icon(Icons.Outlined.Edit,"编辑时段",Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick=onDelete,enabled=enabled && expanded,
                        modifier=Modifier.size(48.dp).testTag("planner_remove_period_$index").semantics { contentDescription="删除时段" }) {
                        AnimatedTrashIcon(enabled && expanded,Modifier.size(22.dp))
                    }
                    if (onSave != null) IconButton(onClick=onSave,enabled=enabled && expanded,
                        modifier=Modifier.size(48.dp).testTag("planner_save_periods_$index")) {
                        Icon(Icons.Outlined.Save,"保存时段组合",Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Prefer below the label, then above; keep both endpoints inside the window. */
internal class PlanningPeriodMenuPosition(private val margin: Int, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        fun clamp(value: Int,window: Int,content: Int): Int {
            val maximum=(window-content).coerceAtLeast(0)
            val edge=margin.coerceAtMost(maximum/2)
            return value.coerceIn(edge,(maximum-edge).coerceAtLeast(edge))
        }
        val x=if(layoutDirection==LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right-popupContentSize.width
        val below=anchorBounds.bottom+gap
        val y=if(below+popupContentSize.height<=windowSize.height-margin) below else anchorBounds.top-gap-popupContentSize.height
        return IntOffset(clamp(x,windowSize.width,popupContentSize.width),clamp(y,windowSize.height,popupContentSize.height))
    }
}
