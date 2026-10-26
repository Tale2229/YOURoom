package com.example.youroom.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** One sizing/decoration contract for combination names and tag names. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactNameField(value: String,onValue: (String)->Unit,label: String,modifier: Modifier=Modifier,
    labelModifier: Modifier=Modifier,isError: Boolean=false,keyboardOptions: KeyboardOptions=KeyboardOptions.Default,
    keyboardActions: KeyboardActions=KeyboardActions.Default) {
    val interaction=remember { MutableInteractionSource() }
    BasicTextField(value,onValue,modifier.heightIn(min=38.dp),singleLine=true,interactionSource=interaction,
        keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,
        textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface),
        cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),decorationBox={ inner ->
            OutlinedTextFieldDefaults.DecorationBox(value,inner,true,true,VisualTransformation.None,interaction,
                isError=isError,label={ Text(label,labelModifier,style=MaterialTheme.typography.bodySmall) },
                contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp),container={
                    OutlinedTextFieldDefaults.Container(true,isError,interaction,
                        colors=OutlinedTextFieldDefaults.colors(),shape=dialogFieldShape())
                })
        })
}
