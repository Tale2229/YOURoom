package com.example.youroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import com.example.youroom.ui.theme.themeRoles

/** Animate control-state changes, not palette changes. The page wave owns palette
 * transitions and must capture the final colors, never an intermediate local tween. */
@Composable
internal fun animateThemeColorAsState(
    targetValue: Color,
    animationSpec: AnimationSpec<Color> = spring(),
    label: String = "ThemeColorAnimation",
): State<Color> = key(themeRoles(), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface) {
    animateColorAsState(targetValue, animationSpec, label = label)
}
