package com.example.youroom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.time.format.DateTimeFormatter
import com.example.youroom.domain.SchoolZone

@Composable
fun AppMark(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(48.dp).semantics { contentDescription = "YouRoom" }) {
        drawRoundRect(accent, cornerRadius = CornerRadius(4.dp.toPx()))
        drawRect(Color.White, topLeft = Offset(size.width * .24f, size.height * .2f),
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
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun WorkspaceNavigation(tab: Int, onTab: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                listOf("找教室", "个人偏好").forEachIndexed { index, label ->
                    val selected = index == tab
                    Column(Modifier.weight(1f).testTag(if (index == 0) "tab_rooms" else "tab_preferences")
                        .selectable(selected, role = Role.Tab, onClick = { onTab(index) })
                        .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label, style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        val width by animateDpAsState(if (selected) 24.dp else 0.dp, tween(160), label = "navigation_line")
                        Box(Modifier.width(width).height(2.dp).background(MaterialTheme.colorScheme.primary))
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
fun AvailabilityStrip(freeSlots: Set<Int>, allSlots: List<Int>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.semantics { contentDescription = "课表空闲节次：${freeSlots.sorted().joinToString()}" },
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        allSlots.forEach { number ->
            Box(Modifier.weight(1f).height(5.dp).background(
                if (number in freeSlots) colors.primary else colors.outlineVariant,
                RectangleShape))
        }
    }
}

fun snapshotLabel(state: YouRoomState): String {
    val snapshot = state.campusSnapshot ?: return "尚未获取教室数据"
    if (state.demo) return "演示数据 · 非实时 · 标记仅本次有效"
    val date = snapshot.queryDate.format(DateTimeFormatter.ofPattern("M月d日"))
    val time = snapshot.fetchedAt.atZone(SchoolZone).format(DateTimeFormatter.ofPattern("HH:mm"))
    return when {
        state.stale -> "$date 的历史缓存 · 不用于今日筛选"
        state.fromCache -> "查询日 $date · $time 缓存 · 非实时"
        else -> "查询日 $date · $time 获取"
    }
}
