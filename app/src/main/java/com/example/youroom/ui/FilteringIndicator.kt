package com.example.youroom.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FilteringIndicator(label: String) {
    val animation = rememberInfiniteTransition(label = "filter_spinner")
    val angle = animation.animateFloat(0f, 360f,
        infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Restart), label = "filter_rotation")
    Row(Modifier.fillMaxWidth().testTag("filtering_indicator").padding(horizontal = 24.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Refresh, null, Modifier.size(24.dp).graphicsLayer { rotationZ = angle.value },
            tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
