package com.example.youroom.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.launch

internal const val ConfirmationDrawMillis = 300
internal const val ConfirmationHoldMillis = 180
internal val ConfirmationDrawProgress = SemanticsPropertyKey<Float>("ConfirmationDrawProgress")
internal val ConfirmationRunning = SemanticsPropertyKey<Boolean>("ConfirmationRunning")

/** A check acknowledges changed content; persistence may be live or deferred until confirmation. */
@Composable
internal fun DialogExitIcon(needsSave: Boolean = false, description: String? = null, size: Dp = 24.dp) {
    Crossfade(needsSave, animationSpec = tween(100), label = "dialog-exit") { save ->
        Icon(if (save) Icons.Outlined.Check else Icons.Outlined.Close, description,
            Modifier.size(size).testTag(if (save) "dialog_exit_save" else "dialog_exit_close"),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = LocalContentColor.current.alpha))
    }
}

/** A finite, single-stroke acknowledgement. Repeated taps cannot save or dismiss twice. */
@Composable
internal fun DialogExitAction(needsSave: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, description: String = if(needsSave) "完成修改" else "关闭",
    iconSize: Dp = 24.dp, canConfirm: () -> Boolean = { true }) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(1f) }
    var running by remember { mutableStateOf(false) }
    val latestClick by rememberUpdatedState(onClick)
    val latestValidate by rememberUpdatedState(canConfirm)
    val ink = MaterialTheme.colorScheme.primary.copy(alpha=if(enabled) 1f else .38f)
    Box(modifier.size(48.dp).clickable(enabled=enabled && !running,role=Role.Button,
        interactionSource=remember { MutableInteractionSource() },indication=null) {
        if (!running) {
            if (!needsSave) latestClick()
            else if (latestValidate()) {
                running=true
                scope.launch {
                    try {
                        progress.snapTo(0f)
                        // Keep the complete check visible briefly before dismissing.
                        progress.animateTo(1f,keyframes {
                            durationMillis=ConfirmationDrawMillis+16+ConfirmationHoldMillis
                            0f at 0 using LinearEasing
                            0f at 16 using LinearEasing
                            1f at ConfirmationDrawMillis+16
                            1f at ConfirmationDrawMillis+16+ConfirmationHoldMillis
                        })
                        latestClick()
                    } finally { running=false }
                }
            }
        }
    }.semantics {
        contentDescription=description
        this[ConfirmationRunning]=running
        this[ConfirmationDrawProgress]=if(running) progress.value else 1f
        if(running) stateDescription="正在确认"
    },contentAlignment=Alignment.Center) {
        if(!running) CompositionLocalProvider(LocalContentColor provides ink) { DialogExitIcon(needsSave,size=iconSize) }
        else Canvas(Modifier.size(iconSize).testTag("dialog_exit_save").clearAndSetSemantics {}) {
            val u=size.minDimension/24f
            val full=Path().apply { moveTo(5.5f*u,11.5f*u);lineTo(10f*u,16f*u);lineTo(19f*u,6.5f*u) }
            val measure=PathMeasure().apply { setPath(full,false) }
            val drawn=Path()
            measure.getSegment(0f,measure.length*progress.value,drawn,true)
            drawPath(drawn,ink,style=Stroke(1.8f*u,cap=StrokeCap.Round,join=StrokeJoin.Round))
            drawLine(ink,Offset(5.5f*u,22f*u),Offset(19f*u,22f*u),1.5f*u,StrokeCap.Round)
        }
    }
}
