package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
internal fun DialogFooterActions(hasSelection: Boolean, resetTag: String, closeTag: String,
    onReset: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        FooterIcon("重置", resetTag, onReset) {
            AnimatedTrashIcon(hasSelection, Modifier.size(24.dp).testTag("${resetTag}_icon"))
        }
        FooterIcon("关闭", closeTag, onClose) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardReturn, null, Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FooterIcon(label: String, tag: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(Modifier.size(48.dp).testTag(tag)
        .clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) { icon() }
}
