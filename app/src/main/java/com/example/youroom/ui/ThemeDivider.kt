package com.example.youroom.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.youroom.ui.theme.themeRoles

@Composable
internal fun ThemeDivider(modifier: Modifier=Modifier,thickness: Dp=DividerDefaults.Thickness,
    color: Color=themeRoles().divider) = HorizontalDivider(modifier,thickness,color)

/** List decorations may have an accent without changing dialog / input outlines. */
@Composable
internal fun ListDivider(modifier: Modifier=Modifier) =
    ThemeDivider(modifier,color=themeRoles().listDivider ?: themeRoles().divider)

/** Keep the original 1dp geometry even when the dark header already separates the page. */
@Composable
internal fun CampusHeaderDivider() {
    val modifier=Modifier.padding(horizontal=24.dp).testTag("campus_divider")
    if(themeRoles().hideHeaderDivider) Spacer(modifier.fillMaxWidth().height(DividerDefaults.Thickness))
    else ThemeDivider(modifier)
}
