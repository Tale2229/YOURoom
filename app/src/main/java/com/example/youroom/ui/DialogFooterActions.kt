package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

@Composable
internal fun DialogFooterActions(hasSelection: Boolean, resetTag: String, closeTag: String,
    onReset: () -> Unit, onClose: () -> Unit, middleTag: String? = null,
    middleDescription: String = "添加词条", middleSelected: Boolean = false,
    onMiddle: (() -> Unit)? = null, resetIconOffsetY: Dp = 0.dp, changed: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        FooterIcon("重置", resetTag, onReset) {
            AnimatedTrashIcon(hasSelection, Modifier.size(24.dp).offset(y=resetIconOffsetY).testTag("${resetTag}_icon"))
        }
        if (middleTag != null && onMiddle != null) FooterIcon(middleDescription, middleTag, onMiddle,
            Modifier.semantics { selected=middleSelected;stateDescription=if(middleSelected) "编辑中" else "未编辑" }) {
            Icon(Icons.Outlined.Add, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        }
        DialogExitAction(changed,onClose,Modifier.guideTag(closeTag))
    }
}

@Composable
private fun FooterIcon(label: String, tag: String, onClick: () -> Unit, modifier: Modifier=Modifier, icon: @Composable () -> Unit) {
    Box(modifier.size(48.dp).guideTag(tag)
        .clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) { icon() }
}
