package com.example.youroom.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
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
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.heightIn(min = 40.dp).widthIn(min = 40.dp, max = 168.dp).width(IntrinsicSize.Max)
        .toggleable(selected, interactionSource = interaction, indication = null,
            role = Role.Checkbox, onValueChange = { onClick() })
        .semantics { this.selected = selected; contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().graphicsLayer {
            rotationZ = angle?.value ?: 0f
            alpha = if (faded) .3f else 1f
        }.height(40.dp), contentAlignment = Alignment.Center) {
            CompactChipFrame(selected,"filter_chip_face_$label",Modifier.fillMaxWidth(),
                onDelete = onDelete,deleteDescription = "删除词条 $label") {
                Box(Modifier.height(28.dp).padding(horizontal = 8.dp).widthIn(max = 152.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
