package com.example.youroom.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign

internal object AboutLinks {
    const val project = "https://github.com/Tale2229/YOURoom"
    const val feedback = "$project/issues"
}

@Composable
internal fun ColumnScope.AboutContent(version: String) {
    val context = LocalContext.current
    val browser = LocalUriHandler.current
    fun open(url: String) {
        runCatching { browser.openUri(url) }.onFailure {
            Toast.makeText(context, "无法打开链接，请检查浏览器", Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("settings_about_content"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("YOURoom", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Text(version, Modifier.testTag("settings_about_version"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("北邮空闲教室查询与自习规划", style = MaterialTheme.typography.bodySmall)
            Text("非学校官方应用", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column {
            HorizontalDivider()
            AboutLink("GitHub", Icons.Outlined.Code, "settings_about_project") { open(AboutLinks.project) }
            AboutLink("问题反馈", Icons.Outlined.ChatBubbleOutline, "settings_about_feedback") { open(AboutLinks.feedback) }
            AboutSection("数据与使用", Icons.Outlined.Info, "settings_about_data", listOf(
                "   - 空闲信息来自学校课表，并非实时空座",
                "   - 教室开放情况请以现场为准",
            ))
            AboutSection("本地与登录", Icons.Outlined.Lock, "settings_about_local", listOf(
                "   - 教室标记与设置保存在设备上，可能参与系统备份",
                "   - 退出登录会清除登录凭据，保留教室标记与设置",
            ))
        }
        Row(Modifier.fillMaxWidth().testTag("settings_about_author"), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            Text(buildAnnotatedString {
                append("开发者  ")
                withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)) {
                    append("23级 软工 小嚎猪")
                }
            }, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutSection(title: String, icon: ImageVector, tag: String, paragraphs: List<String>) {
    Column(Modifier.testTag(tag)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("${tag}_heading"), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            paragraphs.forEach { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun AboutLink(title: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
