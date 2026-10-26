package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*

@Composable
internal fun PlanningPreferencesSheet(campus: Campus, preferences: CampusPlanningPreferences,
    onChange: (CampusPlanningPreferences) -> Unit, onTemplates: () -> Unit, onDismiss: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val guide=LocalUsageGuide.current
    LaunchedEffect(guide?.preparedStage) {
        if(guide!=null) {
            if(guide.stage==GuideStage.TEMPLATES) { if(expanded) expanded=false }
            else if(guide.preparedStage==GuideStage.PLANNING) expanded=true
        }
    }
    SettingsSheet("规划偏好", campus, onDismiss) {
        Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState())) {
            SettingsChoiceGroup("默认偏好", preferences.direction.shortTitle, "planning_default_direction", true,
                expanded, { expanded = !expanded }) {
                PlanningDirection.entries.forEach { direction ->
                    SettingOption(direction.title, preferences.direction == direction, true,
                        "planning_default_${direction.name}", { onChange(preferences.copy(direction = direction)) })
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("planning_default_flexible_row"), verticalAlignment = Alignment.CenterVertically) {
                Text("默认宽松模式", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                CompactSettingsSwitch(preferences.flexible, true, { onChange(preferences.copy(flexible = it)) },
                    Modifier.testTag("planning_default_flexible"))
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).guideTag("planning_templates_entry")
                .clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClick = onTemplates), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("常用时段组合", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(preferences.templates.size.toString(), style = MaterialTheme.typography.bodySmall)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
