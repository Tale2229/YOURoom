package com.example.youroom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.themeRoles

internal val SettingsDisclosureRotation = SemanticsPropertyKey<Float>("SettingsDisclosureRotation")

@Composable
internal fun AppearanceSettingChoices(state: YOURoomState, enabled: Boolean,
    onStrip: (PeriodStripPreferences) -> Unit, onCampus: (Campus) -> Unit) {
    var stripExpanded by rememberSaveable { mutableStateOf(false) }
    var campusExpanded by rememberSaveable { mutableStateOf(false) }
    val sample = remember { DemoData.snapshot().slots.map { it.copy(classrooms = emptyList()) } }
    val slots = state.snapshot?.slots?.takeIf { it.size == 14 } ?: sample
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        SettingsChoiceGroup("时段条样式", state.periodStrip.layout.shortTitle, "settings_strip_layout", enabled,
            stripExpanded, { stripExpanded = !stripExpanded }, icon=Icons.Outlined.ViewWeek) {
            PeriodStripLayout.entries.forEach { layout ->
                SettingOption(layout.shortTitle, state.periodStrip.layout == layout, enabled, "settings_strip_${layout.name}",
                    { onStrip(state.periodStrip.copy(layout = layout)) }, preview = { modifier ->
                        StripStylePreview(slots, layout, modifier.testTag("settings_strip_preview_${layout.name}"))
                    })
            }
        }
        SettingsChoiceGroup("默认校区", state.appearance.defaultCampus.label, "settings_default_campus", enabled,
            campusExpanded, { campusExpanded = !campusExpanded }, icon=NavigationIcons.building) {
            Campus.entries.forEach { campus ->
                SettingOption(campus.label, state.appearance.defaultCampus == campus, enabled,
                    "settings_default_${campus.dataKey}", { onCampus(campus) })
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("settings_strip_numbers")
            .clickable(enabled = enabled, role = Role.Switch, interactionSource = remember { MutableInteractionSource() },
                indication = null) { onStrip(state.periodStrip.copy(showNumbers = !state.periodStrip.showNumbers)) },
            verticalAlignment = Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.FormatListNumbered,null,Modifier.size(20.dp).testTag("settings_strip_numbers_icon"),
                tint=themeRoles().settingsIcon)
            Text("节次编号", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            CompactSettingsSwitch(state.periodStrip.showNumbers, enabled, onCheckedChange = {
                if (enabled) onStrip(state.periodStrip.copy(showNumbers = it))
            }, modifier = Modifier.testTag("settings_strip_numbers_switch").semantics { contentDescription = "显示节次编号" })
        }
    }
}

private val PeriodStripLayout.shortTitle: String get() = if (this == PeriodStripLayout.EQUAL) "等间距" else "等比例"

@Composable
internal fun SettingsChoiceGroup(title: String, summary: String, tag: String, enabled: Boolean,
    expanded: Boolean, onToggle: () -> Unit, icon: ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    val rotation = animateFloatAsState(if (expanded) -90f else 0f,
        tween(180, easing = FastOutSlowInEasing), label = "$tag disclosure")
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag)
            .clickable(enabled = enabled, role = Role.Button,
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if(icon != null) 14.dp else 10.dp)) {
            if(icon != null) Icon(icon,null,Modifier.size(20.dp).testTag("${tag}_icon"),tint=themeRoles().settingsIcon)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(summary, Modifier.testTag("${tag}_summary"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = ink, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, Modifier.size(18.dp).testTag("${tag}_arrow")
                .semantics { this[SettingsDisclosureRotation] = rotation.value }
                .graphicsLayer { rotationZ = rotation.value }, tint = ink)
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(180)) + fadeIn(tween(140)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(100))) {
            Column(Modifier.fillMaxWidth().selectableGroup().padding(start = if(icon != null) 46.dp else 12.dp, bottom = 4.dp), content = content)
        }
    }
}

@Composable
internal fun SettingOption(title: String, selected: Boolean, enabled: Boolean, tag: String,
    onSelect: () -> Unit, preview: (@Composable (Modifier) -> Unit)? = null) {
    val ink = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val dot = animateFloatAsState(if (selected) 1f else 0f, tween(120), label = "$tag selection")
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .selectable(selected, enabled = enabled, role = Role.RadioButton,
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSelect)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, Modifier.widthIn(min = 60.dp), style = MaterialTheme.typography.bodySmall)
        if (preview != null) preview(Modifier.weight(1f).height(5.dp)) else Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(20.dp).testTag("${tag}_radio")) {
            drawCircle(if (selected) ink else outline, size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(1.5.dp.toPx()))
            drawCircle(ink, 5.dp.toPx() * dot.value)
        }
    }
}

@Composable
private fun StripStylePreview(slots: List<LessonSlot>, layout: PeriodStripLayout, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = availabilityColor(false)
    Canvas(modifier) {
        PeriodStripGeometry.bars(slots, size.width, if (layout == PeriodStripLayout.EQUAL) 3.dp.toPx() else 0f, layout)
            .forEachIndexed { i, bar -> drawRect(if (i in setOf(1, 2, 3, 6, 7, 10, 11)) primary else muted,
                Offset(bar.start, 0f), Size(bar.end - bar.start, size.height)) }
    }
}
