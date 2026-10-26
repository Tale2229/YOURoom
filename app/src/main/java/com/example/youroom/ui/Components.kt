package com.example.youroom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.youroom.ui.theme.themeRoles

@Composable
fun AppMark(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(48.dp).semantics { contentDescription = "YOURoom" }) {
        drawRoundRect(accent, cornerRadius = CornerRadius(4.dp.toPx()))
        drawRect(onAccent, topLeft = Offset(size.width * .24f, size.height * .2f),
            size = Size(size.width * .52f, size.height * .6f))
        drawRect(accent, topLeft = Offset(size.width * .38f, size.height * .42f),
            size = Size(size.width * .38f, size.height * .38f))
    }
}

@Composable
fun IconAction(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    filled: Boolean = false, content: @Composable () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp),
        shape = MaterialTheme.shapes.small,
        color = if (filled) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .38f)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun WorkspaceNavigation(tab: Int, onTab: (Int) -> Unit) {
    val roles = themeRoles()
    Surface(color = roles.navigation) {
        Column(Modifier.navigationBarsPadding()) {
            ThemeDivider()
            Row(Modifier.fillMaxWidth().selectableGroup().guideTag("workspace_navigation")) {
                listOf(Triple("找教室","tab_rooms",NavigationIcons.building),
                    Triple("自习规划","tab_planner",NavigationIcons.route),
                    Triple("设置","tab_settings",Icons.Outlined.Settings)).forEachIndexed { index, (label, tag, icon) ->
                    val selected = index == tab
                    val background by animateThemeColorAsState(if(selected) roles.navigationSelected else roles.navigation,tween(140),label="navigation face")
                    val foreground by animateThemeColorAsState(if(selected) roles.onNavigationSelected else roles.navigationIdle,tween(140),label="navigation icon")
                    Box(Modifier.weight(1f).height(56.dp).guideTag(tag)
                        .selectable(selected, role = Role.Tab, interactionSource=remember { MutableInteractionSource() },
                            indication=null,onClick = { onTab(index) }).semantics { contentDescription=label },
                        contentAlignment=Alignment.Center) {
                        Surface(Modifier.size(width=if(index==1) 44.dp else 48.dp,
                            height=if(index==1) 36.dp else 40.dp).testTag("${tag}_face"),
                            shape=MaterialTheme.shapes.small,color=background,contentColor=foreground) {
                            Box(contentAlignment=Alignment.Center) {
                                Icon(icon,null,Modifier.size(if(index==1) 21.dp else 25.dp).testTag("${tag}_icon"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
    }
}

@Composable
fun StatusNotice(text: String, isError: Boolean = false, onDismiss: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(color = if (isError) colors.errorContainer else colors.primaryContainer,
        contentColor = if (isError) colors.onErrorContainer else colors.onPrimaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 10.dp, bottom = 10.dp,
            end = if (onDismiss == null) 20.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (onDismiss != null) IconAction(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, "关闭提示", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun availabilityColor(free: Boolean): Color =
    if (free) MaterialTheme.colorScheme.primary else themeRoles().periodIdle ?: MaterialTheme.colorScheme.outlineVariant

@Composable
fun AvailabilityStrip(freeSlots: Set<Int>, allSlots: List<Int>, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "课表空闲节次：${freeSlots.sorted().joinToString()}" },
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        allSlots.forEach { number ->
            Box(Modifier.weight(1f).height(5.dp).background(
                availabilityColor(number in freeSlots),
                RectangleShape))
        }
    }
}
