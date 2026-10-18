package com.example.youroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.themeRoles

/** A top-aligned comparison panel. The detail list scrolls beneath fixed input
 * controls; no bottom sheet or competing vertical list. */
@Composable
internal fun PlanningWorkspace(execution: PlanningExecution?, campus: Campus, interactive: Boolean,
    onOption: (Long,Int) -> Unit, content: @Composable () -> Unit) {
    val roles = themeRoles()
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
    fun decode(value: String)=value.takeIf { it.startsWith(prefix) }?.substringAfter(':') ?: (execution?.defaultDirection ?: PlanningDirection.BALANCED).name
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
    val arrowFlip=animateFloatAsState(if(expanded) -1f else 1f,
        tween(180,easing=FastOutSlowInEasing),label="alternative disclosure flip")
    LaunchedEffect(campus,execution?.id) { expanded=false }
    BackHandler(available && expanded) { expanded=false }
    Column(Modifier.fillMaxSize().testTag("planner_workspace")) {
            if (available) {
                Spacer(Modifier.fillMaxWidth().height(12.dp).testTag("planner_heading_spacing"))
                // Inset only the painted band. Keep the original A typography,
                // key, spacing, content anchors and 48dp height unchanged.
                Box(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("planner_alternatives")
                    .clickable(enabled=interactive,role=Role.Button,
                        interactionSource=remember { MutableInteractionSource() },indication=null) {
                        expanded=!expanded
                    }.semantics {
                        stateDescription=if (expanded) "已展开" else "已收起"
                    },contentAlignment=Alignment.Center) {
                    Box(Modifier.matchParentSize().padding(horizontal=16.dp)
                        .background(roles.planningHeading).testTag("planner_alternatives_face"))
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=24.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(22.dp).background(roles.planningHeadingKey)
                            .testTag("planner_alternatives_key"))
                        Spacer(Modifier.width(9.dp))
                        Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                            Text(PlanningDirection.valueOf(selectedGroup).title,
                                Modifier.weight(1f,fill=false).testTag("planner_alternatives_title"),
                                style=MaterialTheme.typography.labelMedium.copy(fontSize=13.sp,lineHeight=20.sp,
                                    fontWeight=FontWeight.SemiBold,letterSpacing=0.sp),
                                color=roles.onPlanningHeading,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.width(9.dp))
                            Box(Modifier.width(1.dp).height(12.dp).background(MaterialTheme.colorScheme.outlineVariant)
                                .testTag("planner_alternatives_separator"))
                            Spacer(Modifier.width(9.dp))
                            Text("${rank.toString().padStart(2,'0')} / ${selectedIndices.size.coerceAtLeast(1).toString().padStart(2,'0')}",
                                Modifier.testTag("planner_alternatives_count").semantics {
                                    contentDescription="方案 $rank/${selectedIndices.size.coerceAtLeast(1)}"
                                },style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp,lineHeight=14.sp,letterSpacing=0.sp),
                                color=roles.planningHeadingMuted,maxLines=1)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text("其他方案",Modifier.testTag("planner_alternatives_entry"),
                            style=MaterialTheme.typography.labelSmall.copy(letterSpacing=0.sp),
                            color=roles.planningHeadingEntry,maxLines=1)
                        // Reflect vertically through a flat midpoint, without a planar spin.
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.KeyboardArrowDown,null,Modifier.size(15.dp)
                            .graphicsLayer { scaleY=arrowFlip.value }.testTag("planner_alternatives_arrow"),
                            tint=roles.planningHeadingEntry)
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
                    key(direction,execution?.id) {
                    val summaries=remember { LazyListState() }
                    LazyRow(Modifier.fillMaxWidth().testTag("planner_alternative_list"),state=summaries,
                        contentPadding=PaddingValues(horizontal=24.dp,vertical=8.dp),
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
                    PlanningAlternativeScrollbar(summaries)
                    }
                }
                }
            }
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

/** A quiet, persistent overflow cue, not a loading/progress indicator. */
@Composable
private fun PlanningAlternativeScrollbar(state: LazyListState) {
    val density=LocalDensity.current
    val overflow by remember(state) { derivedStateOf { state.canScrollBackward || state.canScrollForward } }
    Column {
    Box(Modifier.fillMaxWidth().height(8.dp).padding(horizontal=24.dp),contentAlignment=Alignment.Center) {
        if(overflow) {
            val info=state.layoutInfo
            val thumb=planningScrollIndicator(info.totalItemsCount,info.visibleItemsInfo.firstOrNull()?.size ?: 0,
                with(density) { 8.dp.roundToPx() },info.beforeContentPadding+info.afterContentPadding,
                info.viewportEndOffset-info.viewportStartOffset,state.firstVisibleItemIndex,state.firstVisibleItemScrollOffset)
            if(thumb!=null) BoxWithConstraints(Modifier.fillMaxWidth().height(3.dp)
                .background(MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(2.dp))
                .testTag("planner_alternative_scrollbar").semantics { contentDescription="可左右滑动查看更多方案" }) {
                val width=maxWidth*thumb.sizeFraction
                Box(Modifier.offset(x=(maxWidth-width)*thumb.positionFraction).width(width).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha=.65f),RoundedCornerShape(2.dp))
                    .testTag("planner_alternative_scroll_thumb"))
            }
        }
    }
    // A scroll cue replaces the divider, rather than stacking two long rules.
    // Preserve the former divider's height so results below never jump by 1dp.
    if(overflow) Spacer(Modifier.height(1.dp))
    else ThemeDivider(Modifier.padding(horizontal=24.dp).testTag("planner_alternative_divider"))
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

internal fun planningRouteSummary(plan: StudyPlan): String = plan.steps.filterIsInstance<PlanningStep.Study>()
    .fold(emptyList<Classroom>()) { rooms,study ->
        if(rooms.lastOrNull()?.id==study.classroom.id) rooms else rooms+study.classroom
    }.joinToString(" → ") { it.name }
