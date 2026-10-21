package com.example.youroom.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.youroom.R
import com.example.youroom.ui.theme.YOURoomTheme

@Composable
fun LoginScreen(state: YOURoomState, onLogin: (String, String) -> Unit,
    onDemo: () -> Unit,
    onRememberedLogin: (String, String, Boolean) -> Unit = { account, password, _ -> onLogin(account, password) }) {
    var account by rememberSaveable { mutableStateOf(state.rememberedAccount.orEmpty()) }
    var rememberPassword by rememberSaveable { mutableStateOf(state.rememberedAccount != null) }
    // Deliberately NOT saveable: no plaintext password in saved instance state.
    var password by remember(state.rememberedLogin) { mutableStateOf(state.rememberedLogin?.password.orEmpty()) }
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        onRememberedLogin(account, password, rememberPassword)
        password = ""
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            WidthFittedText("YOURoom", Modifier.weight(1f).testTag("login_title").semantics(mergeDescendants = true) {},
                style = MaterialTheme.typography.headlineLarge)
            // Same untinted, transparent g66 vector as the installed launcher icon.
            Image(painterResource(R.drawable.youroom_launcher), contentDescription = "YOURoom 标志",
                modifier = Modifier.size(64.dp).testTag("login_logo"), contentScale = ContentScale.Fit)
        }
        ThemeDivider(Modifier.padding(vertical = 8.dp))
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
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("remember-password")
            .toggleable(rememberPassword, enabled = !state.loggingIn, role = Role.Checkbox,
                onValueChange = { rememberPassword = it }), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rememberPassword, onCheckedChange = null, enabled = !state.loggingIn)
            Spacer(Modifier.width(10.dp))
            Text("记住密码", style = MaterialTheme.typography.bodyMedium)
        }
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
        OutlinedButton(onClick = onDemo, enabled = !state.loggingIn, shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("demo")) {
            Text("先看看演示")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginPreview() {
    YOURoomTheme { LoginScreen(YOURoomState(initializing = false), { _, _ -> }, {}) }
}
