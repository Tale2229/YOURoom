package com.example.youroom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.example.youroom.domain.CacheClearPhase

/** Approved in dialogs-selected-20261023.json. Scoped to dialogs, never app-wide. */
internal enum class DialogDesign { A, B }
internal val LocalDialogDesign = staticCompositionLocalOf<DialogDesign?> { null }

@Composable internal fun dialogFieldShape() = when (LocalDialogDesign.current) {
    DialogDesign.A -> RoundedCornerShape(8.dp)
    DialogDesign.B -> RoundedCornerShape(5.dp)
    null -> MaterialTheme.shapes.small
}

internal fun cacheDialogDesign(phase: CacheClearPhase): DialogDesign = when (phase) {
    CacheClearPhase.CREDENTIALS, CacheClearPhase.VERIFYING -> DialogDesign.B
    else -> DialogDesign.A
}

internal fun dialogColorScheme(base: ColorScheme): ColorScheme {
    val ink = base.onSurfaceVariant.luminance()
    val readable = listOf(base.surface, base.surfaceContainer, base.surfaceContainerLow).all {
        val paper = it.luminance()
        (maxOf(ink, paper) + .05f) / (minOf(ink, paper) + .05f) >= 4.5f
    }
    return if (readable) base else base.copy(onSurfaceVariant = base.onSurface)
}

@Composable
internal fun DialogDesignTheme(design: DialogDesign, content: @Composable () -> Unit) {
    val field = RoundedCornerShape(if (design == DialogDesign.A) 8.dp else 4.dp)
    CompositionLocalProvider(LocalDialogDesign provides design) {
        MaterialTheme(colorScheme = dialogColorScheme(MaterialTheme.colorScheme),
            shapes = MaterialTheme.shapes.copy(small = field), content = content)
    }
}

@Composable
internal fun DialogPanel(design: DialogDesign, modifier: Modifier = Modifier, alert: Boolean = false,
    content: @Composable () -> Unit) {
    DialogDesignTheme(design) {
        Surface(modifier, shape = RoundedCornerShape(if (design == DialogDesign.A) { if (alert) 16.dp else 14.dp } else 8.dp),
            color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
            content = content)
    }
}

@Composable
internal fun DialogHeading(horizontal: Dp = 20.dp, content: @Composable RowScope.() -> Unit) {
    val design = LocalDialogDesign.current
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("dialog_design_heading")
        .background(if (design == DialogDesign.B) colors.surfaceContainer else colors.surface)) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, content = content)
        }
        ThemeDivider(if (design == DialogDesign.A) Modifier.padding(horizontal = horizontal) else Modifier)
    }
}

@Composable
internal fun DialogFooter(horizontal: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val band = LocalDialogDesign.current == DialogDesign.B
    Column(Modifier.fillMaxWidth().testTag("dialog_design_footer")
        .background(if (band) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)) {
        if (band) ThemeDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 4.dp), content = content)
    }
}

@Composable
internal fun DialogConfirmButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, destructive: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val design = LocalDialogDesign.current ?: DialogDesign.A
    val colors = MaterialTheme.colorScheme
    val tinted = destructive && design == DialogDesign.B
    val ink = if (destructive) colors.error else colors.primary
    Button(onClick, modifier, enabled = enabled,
        shape = RoundedCornerShape(if (design == DialogDesign.A) 9.dp else 5.dp),
        border = if (tinted) BorderStroke(1.dp, ink.copy(alpha = if (enabled) 1f else .38f)) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (tinted) lerp(colors.surface, ink, .08f) else ink,
            contentColor = if (tinted) ink else if (destructive) colors.onError else colors.onPrimary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), content = content)
}

@Composable
internal fun DialogDismissButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier, colors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant), content = content)
}

/** The same window/guide host is retained when a cache phase changes its design. */
@Composable
internal fun DesignedAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(), design: DialogDesign = DialogDesign.A,
    showActions: Boolean = true, guideHosted: Boolean = false) {
    val guide = LocalUsageGuide.current
    val density = LocalDensity.current
    val dialogContent: @Composable () -> Unit = {
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.fillMaxWidth().padding(horizontal = if (guideHosted && guide != null && properties.usePlatformDefaultWidth) 24.dp else 0.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center) {
                DialogPanel(design, modifier.widthIn(max = 480.dp).fillMaxWidth(), alert = true) {
                    Column {
                        if (title != null) {
                            if (design == DialogDesign.B) DialogHeading(24.dp) {
                                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold), content = title)
                            } else Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
                                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold), content = title)
                            }
                        }
                        if (text != null) Box(Modifier.weight(1f, fill = false).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                            .padding(top = if (design == DialogDesign.B) 16.dp else 0.dp, bottom = 16.dp)) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium, content = text)
                        }
                        if (showActions) DialogFooter(24.dp) {
                            // FlowRow keeps long translated/large-font actions within the dialog.
                            FlowRow(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                    dismissButton?.invoke()
                                }
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    }
    if (guideHosted) GuideDialog("alert", onDismissRequest, properties, dialogContent)
    else Dialog(onDismissRequest, properties, dialogContent)
}
