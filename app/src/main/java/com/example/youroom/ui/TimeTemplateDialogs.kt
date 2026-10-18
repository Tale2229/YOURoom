package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.youroom.domain.*
import java.util.UUID

/** Shared rhythm for management and import: 12dp / name / 9dp / one time row / 4dp / rule. */
@Composable
private fun TimeTemplateSummary(template: TimeTemplate, modifier: Modifier,
    trailing: (@Composable () -> Unit)? = null) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal=8.dp)) {
        // Always reserve the trailing action column, including in the importer.
        // Three fixed columns keep period #2 aligned for one/two/three-period rows.
        val periodWidth=((maxWidth-48.dp-8.dp)/3).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxWidth().padding(end=48.dp)
            .padding(top=12.dp,bottom=4.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text(template.name,Modifier.testTag("template_summary_name_${template.id}"),style=MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().testTag("template_summary_times_${template.id}"),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                template.windows.sortedBy { it.start }.forEachIndexed { index,window ->
                    WidthFittedText("${planningTime(window.start)}–${planningTime(window.end)}",
                        Modifier.width(periodWidth).testTag("template_summary_period_${template.id}_$index").semantics(mergeDescendants=true) {},
                        style=MaterialTheme.typography.bodySmall.copy(color=MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
        }
        if(trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
    ThemeDivider(Modifier.padding(horizontal=8.dp).testTag("template_summary_divider_${template.id}"))
}

@Composable
internal fun TimeTemplateManager(campus: Campus, preferences: CampusPlanningPreferences,
    onChange: (CampusPlanningPreferences) -> Unit, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<TimeTemplate?>(null) }
    var editor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TimeTemplate?>(null) }
    if (editor) {
        TimeTemplateEditor(campus, editing, emptyList(), "组合${preferences.templates.size+1}", { template ->
            val updated = preferences.templates.filterNot { it.id == template.id } + template
            onChange(preferences.copy(templates = updated)); editor = false
        }, { editor = false })
        return
    }
    SettingsSheet("常用时段组合", campus, onDismiss, titleStyle = MaterialTheme.typography.titleMedium,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                IconAction({ editing = null; editor = true }, Modifier.testTag("template_create")) {
                    Icon(Icons.Outlined.Add, "新建时段组合")
                }
                DialogExitAction(false,onDismiss,Modifier.testTag("settings_sheet_close"))
            }
        }) {
        Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).testTag("template_list")) {
            if (preferences.templates.isEmpty()) Text("还没有常用组合", Modifier.padding(vertical = 20.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            preferences.templates.forEach { template ->
                TimeTemplateSummary(template,Modifier.testTag("template_${template.id}").clickable(role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        editing = template; editor = true
                    }) {
                    IconAction({ deleting = template }, Modifier.testTag("template_delete_${template.id}").semantics {
                        contentDescription="删除${template.name}"
                    }) {
                        AnimatedTrashIcon(deleting?.id == template.id, Modifier.size(24.dp))
                    }
                }
            }
        }
    }
    deleting?.let { template -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text("删除这个组合？") }, text = { Text(template.name) },
        confirmButton = { TextButton({
            onChange(preferences.copy(templates = preferences.templates.filterNot { it.id == template.id })); deleting = null
        }, Modifier.testTag("template_delete_confirm")) { Text("删除") } },
        dismissButton = { TextButton({ deleting = null }) { Text("取消") } }) }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TimeTemplateEditor(campus: Campus, original: TimeTemplate?, current: List<StudyWindow>, defaultName: String,
    onSave: (TimeTemplate) -> Unit, onDismiss: () -> Unit) {
    val initialName = remember { original?.name ?: defaultName }
    val initialWindows = remember { original?.windows ?: current.takeIf(TimeTemplates::valid).orEmpty() }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var windows by remember { mutableStateOf(initialWindows) }
    var start by rememberSaveable { mutableIntStateOf(480) }
    var end by rememberSaveable { mutableIntStateOf(720) }
    var startMoving by remember { mutableStateOf(false) }
    var endMoving by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<PlanningResult?>(null) }
    var rejection by remember { mutableIntStateOf(0) }
    var accepted by remember { mutableStateOf(false) }
    val shake=remember { Animatable(0f) }
    val cooldown=remember { PlanningTapCooldown() }
    val scope=rememberCoroutineScope()
    val clock=remember { Clock.system(SchoolZone) }
    val editorScroll=rememberScrollState()
    LaunchedEffect(feedback,rejection) {
        if(feedback!=null) {
            withFrameNanos { }
            editorScroll.animateScrollTo(editorScroll.maxValue)
        }
    }
    LaunchedEffect(accepted) { if (accepted) { animate(0f,1f,animationSpec=tween(64)) { _,_ -> };accepted=false } }
    LaunchedEffect(rejection) {
        shake.snapTo(0f)
        if(rejection>0) shake.animateTo(0f,keyframes {
            durationMillis=280
            -1f at 35;1f at 70;-.75f at 110;.65f at 150;-.35f at 195;.15f at 235;0f at 280
        })
    }
    // A prefilled new combination still needs saving even without further edits.
    val dirty = original == null && initialWindows.isNotEmpty() || name.trim() != initialName.trim() ||
        windows.sortedBy { it.start } != initialWindows.sortedBy { it.start }
    fun validate(): Boolean {
        feedback=when {
            name.isBlank() -> PlanningResult(message="请填写组合名称")
            !TimeTemplates.valid(windows) -> PlanningResult(message="请添加1至3个合法时段")
            else -> null
        }
        return feedback==null
    }
    SettingsSheet(if (original == null) "新建组合" else "修改组合", campus, onDismiss, avoidIme = true,
        titleStyle = MaterialTheme.typography.titleMedium, footer = {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconAction({ windows = emptyList();feedback=null }, Modifier.testTag("template_clear").semantics {
                contentDescription="清空时段"
            }) {
                AnimatedTrashIcon(windows.isNotEmpty(),Modifier.size(24.dp))
            }
            DialogExitAction(dirty,{
                focus.clearFocus(); keyboard?.hide()
                if (dirty) {
                    if(validate()) onSave(TimeTemplate(original?.id ?: UUID.randomUUID().toString(),name.trim(),windows.sortedBy { it.start }))
                } else onDismiss()
            }, Modifier.testTag(if (dirty) "template_save" else "settings_sheet_close"),
                enabled = !dirty || (!startMoving && !endMoving),
                description=if(dirty) "保存组合" else "关闭",canConfirm=::validate)
        }
    }) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        Column(Modifier.weight(1f, false).verticalScroll(editorScroll).testTag("template_editor_content")) {
            CompactNameField(name, { if (it.length <= 20) { name=it;feedback=null } }, "组合名称",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
                modifier = Modifier.padding(top=16.dp).fillMaxWidth().testTag("template_name"),
                labelModifier=Modifier.testTag("template_name_label"))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().testTag("template_time_wheels"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WheelTimePicker("开始时间", start, "template_start", Modifier.weight(1f),
                    onMoving = { startMoving = it }, matchPlanningTitleGap=true,bottomInset=8.dp, onTime = { start = it;feedback=null })
                WheelTimePicker("结束时间", end, "template_end", Modifier.weight(1f),
                    onMoving = { endMoving = it }, matchPlanningTitleGap=true,bottomInset=8.dp, onTime = { end = it;feedback=null })
            }
            Row(Modifier.fillMaxWidth().testTag("template_period_controls"),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                BoxWithConstraints(Modifier.weight(1f).testTag("template_periods")) {
                    val cell=((maxWidth-12.dp)/3).coerceAtLeast(0.dp)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        windows.forEachIndexed { index,window ->
                            CompactChipFrame(true,"template_period_face_$index",Modifier.width(cell),
                                onDelete={ windows=windows.filterIndexed { i,_ -> i!=index };feedback=null },
                                deleteDescription="删除时段",deleteTag="template_remove_period_$index") {
                                Box(Modifier.fillMaxSize().padding(horizontal=4.dp),contentAlignment=Alignment.Center) {
                                    WidthFittedText("${planningTime(window.start)}–${planningTime(window.end)}",
                                        Modifier.fillMaxWidth().testTag("template_period_$index").semantics(mergeDescendants=true) {}
                                            .clickable(role=Role.Button,interactionSource=remember { MutableInteractionSource() },indication=null) {
                                                start=window.start;end=window.end;windows=windows.filterIndexed { i,_ -> i!=index };feedback=null
                                            },style=MaterialTheme.typography.bodySmall.copy(fontSize=11.sp,lineHeight=16.sp))
                                }
                            }
                        }
                    }
                }
                Text("${windows.size}/3",Modifier.testTag("template_count").padding(start=2.dp,end=2.dp),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                fun add() {
                    focus.clearFocus(); keyboard?.hide()
                    val addition = PlanningSchedule.add(PlanningDraft(start, end, savedWindows = windows))
                    feedback=addition.error
                    if(addition.goal!=null) {
                        windows=addition.goal.windows;start=addition.goal.start;end=addition.goal.end;accepted=true
                    } else rejection++
                }
                PlanningRailAction("添加时段","template_add_period",!startMoving && !endMoving && !accepted && windows.size<3,
                    confirmation=if(accepted) "已添加" else null,briefFeedback=true,onClick=::add,
                    modifier=Modifier.graphicsLayer { translationX=shake.value*4.dp.toPx() },
                    onDisabledClick=if(windows.size>=3 && !startMoving && !endMoving && !accepted) ({
                        if(cooldown.acquire(android.os.SystemClock.elapsedRealtime())) {
                            val full=PlanningResult(message="最多添加三个时段",problem=PlanningProblem.TOO_MANY)
                            feedback=full;rejection++
                            scope.launch { delay(2200);if(feedback===full) feedback=null }
                        }
                    }) else null) { Icon(Icons.Outlined.Add,null,Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            PlanningFeedbackHost(feedback,PlanningDraft(start,end,savedWindows=windows),clock,{
                start=it.start;end=it.end;feedback=null
            },tagPrefix="template",horizontalPadding=0.dp)
        }
    }
}

@Composable
internal fun TimeTemplateImportDialog(campus: Campus, templates: List<TimeTemplate>, existing: List<StudyWindow>,
    enabled: Boolean, notBefore: () -> Int?, onImport: (TimeTemplates.Import) -> Unit,
    onManage: () -> Unit, onDismiss: () -> Unit) {
    var skipInvalid by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var replacing by remember { mutableStateOf<TimeTemplate?>(null) }
    fun apply(template: TimeTemplate) {
        if (!enabled) return
        val imported = TimeTemplates.import(template.windows, skipInvalid, notBefore())
        if (imported.accepted) onImport(imported) else message = imported.message
    }
    SettingsSheet("导入时段组合", campus, onDismiss, titleStyle = MaterialTheme.typography.titleMedium) {
        Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).testTag("template_import_list")) {
            if (templates.isEmpty()) Text("还没有常用组合", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodySmall)
            templates.forEach { template ->
                TimeTemplateSummary(template,Modifier.testTag("template_import_${template.id}").clickable(enabled = enabled,
                    role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val checked = TimeTemplates.import(template.windows, skipInvalid, notBefore())
                    if (!checked.accepted) message = checked.message
                    else if (existing.isNotEmpty()) replacing = template else apply(template)
                })
            }
            message?.let { Text(it, Modifier.padding(vertical = 10.dp).testTag("template_import_error"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(skipInvalid, { skipInvalid = it }, enabled = enabled, modifier = Modifier.testTag("template_skip_invalid"))
            Text("某一时段不合法时仍导入剩余时段", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconAction(onManage, Modifier.testTag("template_import_manage"), enabled = enabled) {
                Icon(Icons.Outlined.Tune, "管理时段组合", Modifier.size(20.dp))
            }
        }
    }
    replacing?.let { template -> AlertDialog(onDismissRequest = { replacing = null },
        title = { Text("替换当前时段？") }, text = { Text("仅替换已添加的时段，保留期望教室与已有方案") },
        confirmButton = { TextButton({ replacing = null; apply(template) }, enabled = enabled,
            modifier = Modifier.testTag("template_replace_confirm")) { Text("替换") } },
        dismissButton = { TextButton({ replacing = null }) { Text("取消") } }) }
}
