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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A check means there is an explicit save to perform, not merely that a live value changed. */
@Composable
internal fun DialogExitIcon(needsSave: Boolean = false, description: String? = null, size: Dp = 24.dp) {
    Crossfade(needsSave, animationSpec = tween(100), label = "dialog-exit") { save ->
        Icon(if (save) Icons.Outlined.Check else Icons.Outlined.Close, description,
            Modifier.size(size).testTag(if (save) "dialog_exit_save" else "dialog_exit_close"),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = LocalContentColor.current.alpha))
    }
}
