package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.themeRoles
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun SettingsScreen(state: YOURoomState, onRoom: (String?) -> Unit,
    onClear: (String) -> Unit, onLogout: () -> Unit, onCampus: (Campus) -> Unit = {},
    showHeader: Boolean = true, sharedHeaderMoving: Boolean = false, interactive: Boolean = true,
    onPeriodStrip: (PeriodStripPreferences) -> Unit = {},
    onTone: (Campus, CampusTone) -> Unit = { _, _ -> }, onDefaultCampus: (Campus) -> Unit = {},
    onPlanningPreferences: (Campus, CampusPlanningPreferences) -> Unit = { _, _ -> },
    onDarkMode: (Boolean) -> Unit = {}, onUsageGuide: () -> Unit = {},onClearCache:()->Unit={}) {
    val westList = rememberLazyListState()
    val shaheList = rememberLazyListState()
    CampusFrame(state, onCampus, showHeader, sharedHeaderMoving, interactive,
        animateBodyChange = false, bodyTag = "settings_campus_body") { page, active, _, _ ->
        SettingsContent(page, { if (active) onRoom(it) }, { if (active) onClear(it) },
            { if (active) onLogout() }, if (page.campus == Campus.XITUCHENG) westList else shaheList, active,
            { if (active) onPeriodStrip(it) }, { campus, tone -> if (active) onTone(campus, tone) },
            { if (active) onDefaultCampus(it) },
            { if (active) onPlanningPreferences(page.campus, it) },
            { if (active) onDarkMode(it) }, { if(active) onUsageGuide() },{ if(active) onClearCache() })
    }
}

@Composable
private fun SettingsContent(state: YOURoomState, onRoom: (String?) -> Unit,
    onClear: (String) -> Unit, onLogout: () -> Unit, listState: LazyListState, interactive: Boolean,
    onPeriodStrip: (PeriodStripPreferences) -> Unit, onTone: (Campus, CampusTone) -> Unit,
    onDefaultCampus: (Campus) -> Unit, onPlanningPreferences: (CampusPlanningPreferences) -> Unit,
    onDarkMode: (Boolean) -> Unit, onUsageGuide: () -> Unit,onClearCache:()->Unit) {
    var sheet by remember(state.campus) { mutableStateOf<String?>(null) }
    var confirmExit by remember(state.campus) { mutableStateOf(false) }
    val guide=LocalUsageGuide.current
    val fixedPosition=guide?.fixedSettingsPosition==true
    var listHeightPx by remember { mutableIntStateOf(0) }
    val density=LocalDensity.current
    LaunchedEffect(guide?.stage,guide?.entry,guide?.demonstration,if(fixedPosition) listHeightPx else 0) {
        if(guide==null) return@LaunchedEffect
        sheet=when(guide.stage) {
            GuideStage.MARKS -> if(guide.entry==2) "marks" else null
            GuideStage.PLANNING -> if(guide.entry>0) "planning" else null
            GuideStage.TEMPLATES -> if(guide.entry>0) "templates" else "planning"
            else -> null
        }
        confirmExit=false
        val item=when(guide.stage) {
            GuideStage.STRIP,GuideStage.NUMBERS,GuideStage.DEFAULT_CAMPUS -> 2
            GuideStage.REPLAY -> 5
            GuideStage.MARKS -> 3
            GuideStage.PLANNING,GuideStage.TEMPLATES -> 4
            GuideStage.ABOUT -> 6
            GuideStage.LOGOUT,GuideStage.CLEAR_CACHE -> 7
            else -> 0
        }
        listState.requestScrollToItem(item)
    }
    val context = LocalContext.current
    val version = remember(context) { runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty() }
    val profiles = remember(state.campusProfiles, state.tagOrder) { state.campusProfiles.filter {
        it.favorite || it.excluded || state.tagOrder.any(it::hasTerm) || it.note.isNotBlank()
    }.sortedWith(compareByDescending<RoomProfile> { it.favorite }.thenBy { it.name }) }
    // Measure existing items rather than changing their 52dp rows to fit a mockup.
    // Remember every height even after lazy disposal, so scrolling cannot move the fold.
    val sectionHeights = remember(state.campus, state.demo, density) { mutableStateMapOf<String, Int>() }
    val sectionKeys = listOf("theme", "dark", "appearance", "marks", "planning", "guide", "about") +
        if (state.demo) listOf("demo") else emptyList()
    fun Modifier.measureSection(key: String) = onSizeChanged { sectionHeights[key] = it.height }
    val leadingInset = if (fixedPosition) 0.dp else 20.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val viewportPx = constraints.maxHeight
    LazyColumn(Modifier.fillMaxSize().testTag("settings_list").onSizeChanged { listHeightPx=it.height }, state = listState,
        userScrollEnabled = interactive && !fixedPosition,
        // Leave enough trailing space to keep item 2 at the same origin even
        // after collapsing options. Otherwise LazyColumn clamps its scroll range
        // and shifts the whole page down. This applies only to these two lessons.
        contentPadding = PaddingValues(top=leadingInset,
            bottom=if(fixedPosition) with(density) { listHeightPx.toDp() } else 20.dp)) {
        item { Box(Modifier.fillMaxWidth().measureSection("theme")) { ThemeImpressionPicker(state.campus,
            (LocalThemePreview.current?.appearance ?: state.appearance).tone(state.campus), interactive, onTone) } }
        if (state.demo) item { Box(Modifier.fillMaxWidth().measureSection("demo")) {
            Text("演示模式", Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { Box(Modifier.fillMaxWidth().measureSection("dark")) {
            DarkModeSetting(state.appearance.isDark(isSystemInDarkTheme()), interactive, onDarkMode) } }
        item { Box(Modifier.fillMaxWidth().measureSection("appearance")) {
            AppearanceSettingChoices(state, interactive, onPeriodStrip, onDefaultCampus) } }
        item { Box(Modifier.fillMaxWidth().measureSection("marks")) {
            SettingsEntry("教室标记", Icons.Outlined.StarBorder, "settings_room_marks", interactive,
                value = profiles.size.toString()) { guide?.enteredMarks();sheet = "marks" } } }
        item { Box(Modifier.fillMaxWidth().measureSection("planning")) {
            SettingsEntry("规划偏好", Icons.Outlined.Tune, "settings_planning", interactive) { guide?.enteredPlanning();sheet = "planning" } } }
        item { Box(Modifier.fillMaxWidth().measureSection("guide")) {
            SettingsEntry("使用指引", Icons.AutoMirrored.Outlined.HelpOutline, "settings_usage_guide", interactive, onClick=onUsageGuide) } }
        item { Box(Modifier.fillMaxWidth().measureSection("about")) {
            SettingsEntry("关于", Icons.Outlined.Info, "settings_about", interactive && guide?.stage!=GuideStage.ABOUT) { sheet = "about" } } }
        item {
            Column(Modifier.fillMaxWidth()) {
                // Guide positioning already scrolls directly to this item. Do not put
                // its read-only danger lessons behind an extra screen of empty space.
                if (guide == null) {
                    val gap = if (sectionKeys.all { it in sectionHeights }) settingsFooterGapPx(viewportPx,
                        with(density) { leadingInset.roundToPx() }, sectionKeys.sumOf { sectionHeights[it] ?: 0 },
                        with(density) { 48.dp.roundToPx() }) else viewportPx
                    Spacer(Modifier.height(with(density) { gap.toDp() }))
                    Box(Modifier.fillMaxWidth().height(48.dp).testTag("settings_footer_handle"),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.size(30.dp, 3.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .6f),
                            RoundedCornerShape(4.dp)))
                    }
                }
                SettingsAccountPanel(version, state.demo, state.viewingCache, interactive,
                    allowLogout = guide?.stage != GuideStage.LOGOUT,
                    onClearCache = { if (guide == null) onClearCache() }, onLogout = { confirmExit = true })
            }
        }
    }
    }
    when (sheet) {
        "marks" -> RoomMarksSheet(state, profiles, onRoom, onClear) { sheet = null;guide?.exitedMarks() }
        "planning" -> PlanningPreferencesSheet(state.campus, state.planningPreferences.forCampus(state.campus),
            onPlanningPreferences, { guide?.enteredTemplates();sheet = "templates" }, { sheet = null })
        "templates" -> TimeTemplateManager(state.campus, state.planningPreferences.forCampus(state.campus),
            onPlanningPreferences, { guide?.exitedTemplates();sheet = "planning" })
        "about" -> AboutSheet(version, state.campus) { sheet = null }
    }
    if (confirmExit) GuideAlertDialog(onDismissRequest = { confirmExit = false },
        title = { Text(if (state.demo) "结束演示？" else "返回登录页？") },
        text = { Text(when {
            state.demo -> "本次演示中的设置、时段组合与个人标记将丢弃，不影响正式使用。"
            state.viewingCache -> "本地收藏、备注与教室缓存会保留。"
            else -> "本次登录状态将清除，本地收藏和备注会保留；已勾选记住的账号密码也会保留。"
        }) },
        confirmButton = { DialogConfirmButton(onClick = { confirmExit = false; onLogout() },modifier=Modifier.guideTag("settings_logout_confirm")) { Text("确认") } },
        dismissButton = { DialogDismissButton(onClick = { confirmExit = false }) { Text("取消") } })
}

@Composable
private fun SettingsAccountPanel(version: String, demo: Boolean, viewingCache: Boolean, enabled: Boolean,
    allowLogout: Boolean, onClearCache: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("settings_account_panel").padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ListDivider(Modifier.testTag("settings_footer_divider"))
        TextButton(onClick = onClearCache, enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp).guideTag("settings_clear_cache"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("清除缓存") }
        TextButton(onClick = onLogout, enabled = enabled && allowLogout,
            modifier = Modifier.heightIn(min = 48.dp).guideTag("settings_logout")) {
            Text(if (demo) "结束演示" else if (viewingCache) "返回登录" else "退出登录")
        }
        Text("YOURoom $version", Modifier.testTag("settings_version"), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DarkModeSetting(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 52.dp)
        .guideTag("settings_dark_mode")
        .clickable(enabled = enabled, role = Role.Switch, interactionSource = remember { MutableInteractionSource() },
            indication = null) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Outlined.DarkMode, null, Modifier.size(20.dp).testTag("settings_dark_mode_icon"),
            tint = themeRoles().settingsIcon)
        Text("深色模式", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        CompactSettingsSwitch(checked, enabled, onCheckedChange,
            Modifier.testTag("settings_dark_mode_switch").semantics { contentDescription = "深色模式" },
            tag = "settings_dark_mode_switch")
    }
}

@Composable
private fun SettingsEntry(title: String, icon: ImageVector, tag: String, enabled: Boolean,
    value: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).guideTag(tag)
        .clickable(enabled = enabled, role = Role.Button, interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(20.dp).testTag("${tag}_icon"), tint = themeRoles().settingsIcon)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp).testTag("${tag}_arrow"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsSheet(title: String, campus: Campus?, onDismiss: () -> Unit, avoidIme: Boolean = false,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    titleStyle: TextStyle? = null,
    showTitle: Boolean = true, changed: Boolean = false, modifier:Modifier=Modifier,exitEnabled:Boolean=true,
    content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val browser = LocalUriHandler.current
    GuideDialog("sheet",onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Dialog creates a new Android owner: keep the page's text scale and link handler.
        CompositionLocalProvider(LocalDensity provides density, LocalUriHandler provides browser) {
        DialogPanel(DialogDesign.B, modifier.then(if (avoidIme) Modifier.imePadding() else Modifier)
            .widthIn(max = 420.dp).fillMaxWidth(.9f).heightIn(max = 560.dp)) {
            Column {
                if (showTitle) DialogHeading(18.dp) {
                    Text(title, Modifier.weight(1f).testTag("settings_sheet_title"),
                        style = (titleStyle ?: MaterialTheme.typography.titleMedium).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                    if (campus != null) Text(campus.label, Modifier.testTag("settings_sheet_campus"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f, fill = false).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                DialogFooter(18.dp) {
                    if (footer != null) footer() else {
                        DialogExitAction(changed,onDismiss,Modifier.align(Alignment.End).testTag("settings_sheet_close"),enabled=exitEnabled)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun RoomMarksSheet(state: YOURoomState, profiles: List<RoomProfile>, onRoom: (String?) -> Unit,
    onClear: (String) -> Unit, onDismiss: () -> Unit) {
    var clearId by remember { mutableStateOf<String?>(null) }
    val initial = remember(state.campus) { profiles.toList() }
    SettingsSheet("教室标记", state.campus, onDismiss, changed=profiles!=initial) {
        if (profiles.isEmpty()) Text("还没有个人标记", Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium)
        else LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 360.dp).guideTag("settings_marks_list")) {
            items(profiles, key = { it.roomId }) { profile ->
                Column {
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() }, indication = null) { onRoom(profile.roomId) }
                        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(profile.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
                                ExcludedRoomMarker(profile)
                            }
                            val terms = listOfNotNull(if (profile.favorite) "已收藏" else null, if (profile.excluded) "已排除" else null) +
                                state.tagOrder.filter { it != PersonalTags.favorite && profile.hasTerm(it) }
                            if (terms.isNotEmpty()) Text(terms.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (profile.note.isNotBlank()) Text(profile.note, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconAction(onClick = { clearId = profile.roomId }, modifier = Modifier.semantics {
                            contentDescription = "清除 ${profile.name} 的个人标记"
                        }) {
                            AnimatedTrashIcon(clearId == profile.roomId, Modifier.size(24.dp))
                        }
                    }
                    ThemeDivider()
                }
            }
        }
    }
    clearId?.let { id -> DesignedAlertDialog(onDismissRequest = { clearId = null },
        title = { Text("清除此教室的个人标记？") },
        text = { Text("收藏、排除、标签和备注都会清除，学校数据不受影响。") },
        confirmButton = { DialogConfirmButton(onClick = { onClear(id); clearId = null }, destructive = true) { Text("清除") } },
        dismissButton = { DialogDismissButton(onClick = { clearId = null }) { Text("取消") } }) }
}
