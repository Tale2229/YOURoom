package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import com.example.youroom.ui.theme.themeRoles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shared face and corner cross for room terms and planning periods. Only the border has a gap. */
@Composable
internal fun CompactChipFrame(selected: Boolean, faceTag: String, modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null, deleteDescription: String = "", deleteTag: String? = null,
    enabled: Boolean = true, content: @Composable () -> Unit) {
    val palette = MaterialTheme.colorScheme
    val roles = themeRoles()
    val selectedFill = roles.selectionFill ?: palette.primaryContainer
    val selectedInk = roles.onSelection ?: palette.primary
    val shape = MaterialTheme.shapes.small
    Box(modifier.height(40.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxWidth().height(28.dp).testTag(faceTag)
            .chipBorder(if (selected) selectedInk else palette.outlineVariant,shape,onDelete != null),
            shape = shape, color = if (selected) selectedFill else palette.surface,
            contentColor = if (selected) selectedInk else palette.onSurfaceVariant, content = content)
        if (onDelete != null) Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp,y = (-1).dp)
            .size(20.dp).then(if (deleteTag != null) Modifier.testTag(deleteTag) else Modifier)
            .clickable(enabled = enabled,role = Role.Button,interactionSource = remember { MutableInteractionSource() },
                indication = null,onClick = onDelete).semantics { contentDescription = deleteDescription },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close,null,Modifier.size(14.dp),tint = if (selected) selectedInk else palette.primary)
        }
    }
}

private fun Modifier.chipBorder(color: Color, shape: Shape, hasDelete: Boolean) = drawWithCache {
    val stroke = 1.dp.toPx()
    val inset = stroke / 2
    val outline = shape.createOutline(Size((size.width-stroke).coerceAtLeast(0f),
        (size.height-stroke).coerceAtLeast(0f)),layoutDirection,this)
    // The face starts 6dp below its 40dp frame; the cross center is 5dp from the right, 3dp below the top.
    val x = size.width - 5.dp.toPx() - inset
    val y = 3.dp.toPx() - inset
    val radius = 8.dp.toPx()
    val gap = Path().apply { addOval(Rect(x-radius,y-radius,x+radius,y+radius)) }
    onDrawWithContent {
        drawContent()
        translate(inset,inset) {
            if (hasDelete) clipPath(gap,ClipOp.Difference) { drawOutline(outline,color,style = Stroke(stroke)) }
            else drawOutline(outline,color,style = Stroke(stroke))
        }
    }
}
