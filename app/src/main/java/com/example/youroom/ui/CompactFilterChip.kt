package com.example.youroom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CompactFilterChip(label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, onDelete: (() -> Unit)? = null, angle: State<Float>? = null,
    faded: Boolean = false) {
    Box(modifier.heightIn(min = 40.dp).widthIn(min = 56.dp, max = 168.dp).width(IntrinsicSize.Max)
        .toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() })
        .semantics { this.selected = selected; contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().graphicsLayer {
            rotationZ = angle?.value ?: 0f
            alpha = if (faded) .3f else 1f
        }.height(40.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.fillMaxWidth().height(28.dp).testTag("filter_chip_face_$label"), shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Box(Modifier.height(28.dp).padding(horizontal = 8.dp).widthIn(max = 152.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onDelete != null) IconButton(onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-1).dp).size(20.dp)) {
                Icon(Icons.Outlined.Close, "删除词条 $label", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
