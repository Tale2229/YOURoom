package com.example.youroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*

/** A top-aligned comparison panel. No bottom sheet or competing vertical list; all
 * vertical movement belongs to the detail list and the editor's nested-scroll gate. */
@Composable
internal fun PlanningWorkspace(execution: PlanningExecution?, campus: Campus, interactive: Boolean,
    onOption: (Long,Int) -> Unit, content: @Composable () -> Unit) {
    val result=execution?.result
    val groups=result?.groups?.ifEmpty {
        listOf(PlanningGroup(PlanningDirection.BALANCED,result.options.indices.toList()))
    }.orEmpty()
    val available=result?.options?.isNotEmpty()==true
    // CampusFrame deliberately keeps one body mounted. Separate saved tokens avoid resetting
    // the other campus's browsing/selection when its content fades out.
    var westBrowse by rememberSaveable { mutableStateOf("") }
    var shaheBrowse by rememberSaveable { mutableStateOf("") }
    var westSelected by rememberSaveable { mutableStateOf("") }
    var shaheSelected by rememberSaveable { mutableStateOf("") }
    val prefix="${execution?.id}:"
    fun decode(value: String)=value.takeIf { it.startsWith(prefix) }?.substringAfter(':') ?: PlanningDirection.BALANCED.name
    val browsing=decode(if (campus==Campus.XITUCHENG) westBrowse else shaheBrowse)
    val selectedGroup=decode(if (campus==Campus.XITUCHENG) westSelected else shaheSelected)
    fun browse(direction: PlanningDirection) {
        if (campus==Campus.XITUCHENG) westBrowse=prefix+direction.name else shaheBrowse=prefix+direction.name
    }
    fun selectGroup(direction: PlanningDirection) {
        if (campus==Campus.XITUCHENG) westSelected=prefix+direction.name else shaheSelected=prefix+direction.name
    }
    val direction=PlanningDirection.valueOf(browsing)
    val selected=execution?.selectedOption ?: 0
    val selectedIndices=groups.firstOrNull { it.direction.name==selectedGroup }?.optionIndices.orEmpty()
    val rank=selectedIndices.indexOf(selected).takeIf { it>=0 }?.plus(1) ?: 1
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(campus,execution?.id) { expanded=false }
    BackHandler(available && expanded) { expanded=false }
    Column(Modifier.fillMaxSize().testTag("planner_workspace")) {
            if (available) {
                Column(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("planner_alternatives")
                    .clickable(enabled=interactive,role=Role.Button,
                        interactionSource=remember { MutableInteractionSource() },indication=null) {
                        expanded=!expanded
                    }.semantics {
                        stateDescription=if (expanded) "已展开" else "已收起"
                    }) {
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("${PlanningDirection.valueOf(selectedGroup).title} · 方案 $rank/${selectedIndices.size.coerceAtLeast(1)}",
                            Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text("其他方案",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        Icon(Icons.Outlined.KeyboardArrowDown,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                    }
                }
                AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().testTag("planner_drawer")) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
                    groups.forEach { item ->
                        val active=item.direction==direction
                        val enabled=item.optionIndices.isNotEmpty()
                        Column(Modifier.widthIn(min=56.dp).testTag("planner_direction_${item.direction.name}")
                            .clickable(enabled=interactive && enabled,role=Role.Tab,
                                interactionSource=remember { MutableInteractionSource() },indication=null) { browse(item.direction) }
                            .semantics { this.selected=active; if (!enabled) { disabled(); contentDescription="${item.direction.title}，${item.message}" } },
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            Box(Modifier.heightIn(min=48.dp).padding(horizontal=10.dp),contentAlignment=Alignment.Center) {
                                Text(item.direction.shortTitle,style=MaterialTheme.typography.labelLarge,
                                    color=if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha=.38f)
                                    else if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(Modifier.width(32.dp).height(2.dp).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface))
                        }
                    }
                }
                    val shownGroup=groups.firstOrNull { it.direction==direction }
                    LazyRow(Modifier.fillMaxWidth().testTag("planner_alternative_list"),contentPadding=PaddingValues(horizontal=24.dp,vertical=8.dp),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(shownGroup?.optionIndices.orEmpty(),key={ _,index -> result.options[index].plan.signature }) { position,index ->
                            val option=result.options[index]
                            PlanningSummary(option.plan,position+1,index==selected,Modifier.testTag("planner_alternative_${direction.name}_$position")) {
                                if (interactive) {
                                    selectGroup(direction)
                                    execution?.let { onOption(it.id,index) }
                                    expanded=false
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal=24.dp))
                }
                }
            }
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
private fun PlanningSummary(plan: StudyPlan, rank: Int, selected: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    val accent=MaterialTheme.colorScheme.primary
    Column(modifier.width(176.dp).background(if (selected) accent.copy(alpha=.055f) else MaterialTheme.colorScheme.surface)
        .clickable(role=Role.RadioButton,interactionSource=remember { MutableInteractionSource() },indication=null,onClick=onClick)
        .semantics(mergeDescendants=true) { this.selected=selected }
        .padding(horizontal=12.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("方案 $rank",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=accent)
            if (selected) Icon(Icons.Outlined.Check,"当前方案",Modifier.size(18.dp),tint=accent)
        }
        Text(planningRouteSummary(plan),style=MaterialTheme.typography.bodySmall,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis)
    }
}

internal fun planningRouteSummary(plan: StudyPlan): String = plan.windows.mapIndexed { index,window ->
    val rooms=plan.steps.filterIsInstance<PlanningStep.Study>().filter { it.start>=window.start && it.end<=window.end }
        .map { it.classroom.name }
    (if (plan.windows.size>1) "${index+1} · " else "")+rooms.joinToString(" → ")
}.joinToString("  /  ")
