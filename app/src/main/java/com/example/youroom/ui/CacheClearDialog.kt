package com.example.youroom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.youroom.domain.*

@Composable
internal fun CacheClearDialog(state:CacheClearState,onConfirm:()->Unit,onCancel:()->Unit,
    onVerify:(String,String)->Unit) {
    // Neither field is saveable. Never prefill the remembered password or retain
    // it in the ViewModel, saved state, logs, history or a verification grant.
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focus=LocalFocusManager.current
    val information=state.phase==CacheClearPhase.INFORMATION
    val confirm=state.phase==CacheClearPhase.CONFIRM
    DesignedAlertDialog(onDismissRequest={ if(!state.irreversible) onCancel() },
        design=cacheDialogDesign(state.phase),
        showActions=!state.irreversible,
        modifier=Modifier.imePadding().testTag("cache_clear_dialog"),
        properties=DialogProperties(dismissOnBackPress=!state.irreversible,dismissOnClickOutside=!state.busy),
        title={ Text(when {
            information -> "清除缓存"
            confirm -> "清除全部本地数据？"
            state.phase==CacheClearPhase.REQUESTED -> "系统正在清除"
            else -> "身份校验"
        }) },
        text={ Column(Modifier.fillMaxWidth(),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when {
                information -> Text("请先登录后使用。演示和使用指引不会清除正式数据。")
                confirm -> {
                    Text("将删除本机保存的账号密码、登录状态、两校区教室缓存、收藏、标签、备注、排除记录、个人偏好、时段组合、全部设置和使用指引进度。")
                    Text("接下来需输入当前登录账号和教务密码，联网校验通过后才清除。此操作不可恢复。")
                    Text("应用会关闭，请重新打开；不会删除教务账号或学校服务端数据。")
                }
                state.irreversible -> Text("清除请求已交给系统，应用即将关闭。重新打开后需重新登录，使用指引也会重新开始。")
                else -> {
                    Text("请输入当前登录账号和教务密码。校验通过后立即清除全部本地数据并关闭应用。")
                    OutlinedTextField(account,{account=it},Modifier.fillMaxWidth().testTag("cache_clear_account"),
                        label={Text("当前登录账号")},singleLine=true,enabled=!state.busy,shape=dialogFieldShape(),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii,imeAction=ImeAction.Next))
                    OutlinedTextField(password,{password=it},Modifier.fillMaxWidth().testTag("cache_clear_password"),
                        label={Text("教务密码")},singleLine=true,enabled=!state.busy,shape=dialogFieldShape(),
                        visualTransformation=PasswordVisualTransformation(),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=ImeAction.Done))
                    state.error?.let { Text(it,Modifier.testTag("cache_clear_error"),color=MaterialTheme.colorScheme.error) }
                    if(state.busy) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                        Text("正在联网校验…",Modifier.testTag("cache_clear_verifying"))
                    }
                }
            }
        } },
        confirmButton={
            when {
                information -> DialogConfirmButton(onCancel,Modifier.testTag("cache_clear_acknowledge")) { Text("知道了") }
                confirm -> DialogConfirmButton(onConfirm,Modifier.testTag("cache_clear_confirm")) { Text("确认并校验身份") }
                !state.irreversible -> DialogConfirmButton(onClick={
                    focus.clearFocus()
                    val submitted=password
                    password=""
                    onVerify(account,submitted)
                },enabled=!state.busy && account.isNotBlank() && password.isNotEmpty(),
                    modifier=Modifier.testTag("cache_clear_submit"),destructive=true) { Text("校验并清除") }
            }
        },
        dismissButton={ if(!information && !state.irreversible)
            DialogDismissButton(onCancel,Modifier.testTag("cache_clear_cancel")) { Text(if(state.busy) "取消校验" else "取消") }
        })
}
