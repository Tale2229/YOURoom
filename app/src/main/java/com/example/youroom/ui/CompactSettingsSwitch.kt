package com.example.youroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.youroom.ui.theme.themeRoles

/** A compact capsule and an overhanging white thumb inside a full 48dp target.
 * Page transitions disable input, not the saved state's colors or position. */
@Composable
internal fun CompactSettingsSwitch(checked: Boolean, enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val offset by animateDpAsState(if(checked) 18.dp else 0.dp,
        tween(160,easing=FastOutSlowInEasing),label="settings switch thumb")
    val roles=themeRoles()
    val track by animateColorAsState(if(checked) roles.switchTrackOn ?: MaterialTheme.colorScheme.primary else roles.switchTrackOff ?: MaterialTheme.colorScheme.outlineVariant,
        tween(160),label="settings switch track")
    Box(modifier.size(48.dp).toggleable(checked,enabled=enabled,role=Role.Switch,
        interactionSource=remember { MutableInteractionSource() },indication=null,
        onValueChange={ if(enabled) onCheckedChange(it) }),contentAlignment=Alignment.Center) {
        Box(Modifier.size(44.dp,26.dp).testTag("settings_strip_numbers_switch_face")) {
            Box(Modifier.align(Alignment.Center).size(40.dp,20.dp)
                .background(track,RoundedCornerShape(50)).testTag("settings_strip_numbers_switch_track"))
            Box(Modifier.align(Alignment.CenterStart).offset(x=offset).size(26.dp)
                .shadow(1.dp,CircleShape).background(roles.switchThumb ?: Color.White,CircleShape)
                .testTag("settings_strip_numbers_switch_thumb"))
        }
    }
}
