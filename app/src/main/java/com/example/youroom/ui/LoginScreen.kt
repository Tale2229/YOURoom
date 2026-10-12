package com.example.youroom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.youroom.ui.theme.YouRoomTheme

@Composable
fun LoginScreen(state: YouRoomState, onLogin: (String, String) -> Unit,
    onDemo: () -> Unit, onCache: () -> Unit) {
    var account by rememberSaveable { mutableStateOf("") }
    // Deliberately NOT saveable: no plaintext password in saved instance state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        onLogin(account, password)
        password = ""
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            AppMark()
            Text("北邮 · 自习", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("YouRoom", style = MaterialTheme.typography.headlineLarge)
            Text("北邮空闲教室", style = MaterialTheme.typography.titleLarge)
            Text("登录教务账号，按时段找到合适的自习教室。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        OutlinedTextField(account, { account = it }, Modifier.fillMaxWidth().testTag("account"),
            label = { Text("学号 / 工号") }, singleLine = true, enabled = !state.loggingIn,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().testTag("password"),
            label = { Text("教务密码") }, singleLine = true, enabled = !state.loggingIn,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!state.loggingIn && account.isNotBlank() && password.isNotEmpty()) submit() }),
            trailingIcon = { IconAction(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (visible) "隐藏密码" else "显示密码")
            } })
        Button(onClick = submit, Modifier.fillMaxWidth().height(52.dp).testTag("login"),
            shape = MaterialTheme.shapes.small,
            enabled = !state.loggingIn && account.isNotBlank() && password.isNotEmpty()) {
            if (state.loggingIn) CircularProgressIndicator(Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else {
                Text("登录并获取教室")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("密码只用于本次 HTTPS 登录，不会保存。登录状态加密保存在本机。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("连接提示", style = MaterialTheme.typography.titleSmall)
        Text("请使用能够访问教务服务的网络。校外可尝试连接校园 VPN；网页登录式 WebVPN 不一定接管 App 网络。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onDemo, enabled = !state.loggingIn, shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("demo")) {
            Text("先看看演示")
        }
        AnimatedVisibility(state.snapshot != null && !state.loggingIn) {
            TextButton(onClick = onCache, shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()) { Text("查看本地缓存（非实时）") }
        }
        Text("个人工具 · 非学校官方应用 · 首版支持西土城校区",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginPreview() {
    YouRoomTheme { LoginScreen(YouRoomState(initializing = false), { _, _ -> }, {}, {}) }
}
