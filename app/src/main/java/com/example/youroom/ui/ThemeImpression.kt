package com.example.youroom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.campusColorScheme
import kotlin.math.abs
import kotlin.math.tan

internal val ThemePreviewSlideProgress = SemanticsPropertyKey<Float>("ThemePreviewSlideProgress")
private data class ImpressionFrames(val from: ImageBitmap, val to: ImageBitmap)
private val ThemeArrowHeight = 30.dp
private val ThemeArrowWidth = (ThemeArrowHeight.value / (2f * tan(Math.toRadians(50.0)).toFloat())).dp
// The tips point away from the frame; measure their 35dp distance, not the hit boxes' gap.
private val ThemeArrowFrameGap = 35.dp - (48.dp + ThemeArrowWidth) / 2

@Composable
internal fun ThemeImpressionPicker(campus: Campus, tone: CampusTone, enabled: Boolean,
    onTone: (Campus, CampusTone) -> Unit) {
    val density = LocalDensity.current
    val pixels = with(density) { 75.dp.roundToPx() }
    val night = MaterialTheme.colorScheme.surface.luminance() < .5f
    val latestAction by rememberUpdatedState(onTone)
    val selectable = enabled && LocalThemePreview.current?.busy != true
    // This small preview moves immediately; the page's separate wave is debounced.
    key(campus, pixels, night) {
        var displayedTone by remember { mutableStateOf(tone) }
        var frames by remember { impression(pixels,density,tone,night).let { mutableStateOf(ImpressionFrames(it,it)) } }
        val progress = remember { Animatable(1f) }
        var direction by remember { mutableIntStateOf(1) }
        LaunchedEffect(tone) {
            if(displayedTone == tone) return@LaunchedEffect
            val frozen = bitmap(pixels,density) { slide(frames,progress.value,direction) }
            val next = impression(pixels,density,tone,night)
            direction = if(displayedTone.step(-1) == tone) -1 else 1
            displayedTone=tone
            progress.snapTo(0f)
            frames=ImpressionFrames(frozen,next)
            progress.animateTo(1f,tween(220,easing=FastOutSlowInEasing))
            frames=ImpressionFrames(next,next)
        }
        fun step(direction: Int) { if(selectable) latestAction(campus,tone.step(direction)) }
        Column(Modifier.fillMaxWidth().padding(top=22.dp,bottom=18.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(ThemeArrowFrameGap)) {
                ThemeArrow(false,selectable,enabled) { step(-1) }
                Box(Modifier.size(95.dp).testTag("settings_theme_frame")
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha=.3f), RoundedCornerShape(14.dp))
                    .padding(10.dp)) {
                Canvas(Modifier.size(75.dp).testTag("settings_theme_impression")
                    .clip(RoundedCornerShape(4.dp))
                    .semantics {
                        contentDescription="${campus.label}配色预览"
                        stateDescription=tone.title
                        this[ThemePreviewSlideProgress]=progress.value
                        customActions=listOf(
                            CustomAccessibilityAction("上一配色") { if(selectable) { step(-1);true } else false },
                            CustomAccessibilityAction("下一配色") { if(selectable) { step(1);true } else false })
                        if(!selectable) disabled()
                    }
                    .onKeyEvent {
                        if(selectable && it.type==KeyEventType.KeyUp && it.key in listOf(Key.DirectionLeft,Key.DirectionRight)) {
                            step(if(it.key==Key.DirectionRight) 1 else -1);true
                        } else false
                    }.focusable(selectable)
                    .pointerInput(selectable,campus,tone) {
                        var traveled=0f
                        detectHorizontalDragGestures(onDragStart={traveled=0f},onDragCancel={traveled=0f},
                            onDragEnd={ if(selectable && abs(traveled)>=24.dp.toPx()) step(if(traveled<0) 1 else -1) }) { change, dx ->
                            if(selectable) { traveled+=dx;change.consume() }
                        }
                    }) { slide(frames,progress.value,direction) }
                }
                ThemeArrow(true,selectable,enabled) { step(1) }
            }
            Text(tone.title,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.testTag("settings_theme_name"))
        }
    }
}

@Composable
private fun ThemeArrow(next: Boolean, enabled: Boolean, showEnabled: Boolean, onClick: () -> Unit) {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale=animateFloatAsState(if(pressed) .82f else 1f,tween(90),label="theme arrow press")
    // Lock input without flashing between a captured bright arrow and a dim live one.
    val ink=MaterialTheme.colorScheme.primary.copy(alpha=if(showEnabled) 1f else .38f)
    Box(Modifier.size(48.dp).testTag(if(next) "settings_theme_next" else "settings_theme_previous")
        .clickable(enabled=enabled,role=Role.Button,interactionSource=interaction,indication=null,onClick=onClick)
        .semantics { contentDescription=if(next) "下一配色" else "上一配色" },contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(ThemeArrowWidth, ThemeArrowHeight)
            .testTag(if(next) "settings_theme_next_triangle" else "settings_theme_previous_triangle")
            .graphicsLayer { scaleX=scale.value;scaleY=scale.value }) {
            val triangle=Path().apply {
                moveTo(if(next) size.width else 0f,size.height*.5f)
                lineTo(if(next) 0f else size.width,0f)
                lineTo(if(next) 0f else size.width,size.height)
                close()
            }
            drawPath(triangle,ink)
        }
    }
}

private fun bitmap(pixels: Int,density: Density,paint: DrawScope.() -> Unit): ImageBitmap = ImageBitmap(pixels,pixels).also {
    CanvasDrawScope().draw(density,LayoutDirection.Ltr,GraphicsCanvas(it),Size(pixels.toFloat(),pixels.toFloat()),paint)
}

/** Code-native stand-ins. Replace this painter with an artwork asset per stable tone ID later. */
private fun impression(pixels: Int,density: Density,tone: CampusTone,dark: Boolean): ImageBitmap = bitmap(pixels,density) {
    val colors=campusColorScheme(Campus.XITUCHENG,dark,tone)
    drawRect(colors.primaryContainer)
    withTransform({ scale(size.width/100f,size.height/100f,pivot=Offset.Zero) }) {
        when(tone) {
            CampusTone.SAND -> {
                drawCircle(colors.primary.copy(alpha=.25f),35f,Offset(26f,27f))
                drawPath(Path().apply { moveTo(0f,74f);lineTo(100f,26f);lineTo(100f,100f);lineTo(0f,100f);close() },colors.primary)
                drawPath(Path().apply { moveTo(0f,96f);lineTo(100f,48f);lineTo(100f,66f);lineTo(28f,100f);close() },colors.primaryContainer)
            }
            CampusTone.PINE -> {
                drawCircle(colors.primary.copy(alpha=.22f),46f,Offset(95f,9f))
                drawRect(colors.primary,Offset(15f,28f),Size(17f,72f))
                drawRect(colors.primary.copy(alpha=.67f),Offset(40f,46f),Size(17f,54f))
                drawRect(colors.primary.copy(alpha=.4f),Offset(65f,65f),Size(20f,35f))
            }
            CampusTone.SLATE -> {
                drawRect(colors.primary.copy(alpha=.2f),Offset(0f,25f),Size(64f,18f))
                drawRect(colors.primary.copy(alpha=.55f),Offset(22f,43f),Size(78f,18f))
                drawRect(colors.primary,Offset(43f,61f),Size(57f,39f))
            }
            CampusTone.CLAY -> {
                drawCircle(colors.primary,36f,Offset(50f,58f))
                drawRect(colors.primaryContainer,Offset(0f,58f),Size(100f,42f))
                drawRect(colors.primary.copy(alpha=.28f),Offset(0f,70f),Size(100f,10f))
                drawRect(colors.primary.copy(alpha=.55f),Offset(0f,86f),Size(100f,14f))
            }
        }
    }
}

private fun DrawScope.slide(images: ImpressionFrames,progress: Float,direction: Int) {
    if(progress>=1f) { drawImage(images.to);return }
    if(progress<=0f) { drawImage(images.from);return }
    clipRect {
        drawImage(images.from,Offset(-direction * size.width * progress,0f))
        drawImage(images.to,Offset(direction * size.width * (1f-progress),0f))
    }
}
