package com.example.youroom.ui

import com.example.youroom.ui.theme.themeRoles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.campusColorScheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.tan

internal val ThemePreviewSlideProgress = SemanticsPropertyKey<Float>("ThemePreviewSlideProgress")
internal val ThemeBeltPosition = SemanticsPropertyKey<Float>("ThemeBeltPosition")
internal val ThemeBeltBrowsing = SemanticsPropertyKey<Boolean>("ThemeBeltBrowsing")
internal val ThemeBeltReveal = SemanticsPropertyKey<Float>("ThemeBeltReveal")
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
    val preview = LocalThemePreview.current
    val onBrowsing by rememberUpdatedState(preview?.onBrowsing ?: {})
    val selectable = enabled && preview?.busy != true
    // This small preview moves immediately; the page's separate wave is debounced.
    key(campus, pixels, night) {
        var displayedTone by remember { mutableStateOf(tone) }
        var frames by remember { impression(pixels,density,tone,night).let { mutableStateOf(ImpressionFrames(it,it)) } }
        val progress = remember { Animatable(1f) }
        var direction by remember { mutableIntStateOf(1) }
        val artworks = remember { CampusTone.entries.map { impression(pixels, density, it, night) } }
        val count = artworks.size
        val initial = remember { ThemeBeltGeometry.initial(tone.ordinal, count) }
        val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
        val scope = rememberCoroutineScope()
        var browsing by remember { mutableStateOf(false) }
        var dragRevision by remember { mutableLongStateOf(0L) }
        var awaitingFling by remember { mutableStateOf(false) }
        var flingRunning by remember { mutableStateOf(false) }
        val currentTone by rememberUpdatedState(tone)
        val currentSelectable by rememberUpdatedState(selectable)
        val position by remember { derivedStateOf { list.layoutInfo.themeBeltPosition(initial) } }
        val nearestTone = CampusTone.entries[ThemeBeltGeometry.value(position, count)]
        val reveal by animateFloatAsState(if (browsing) 1f else 0f, tween(160), label = "theme belt reveal")
        val nativeFling = remember(list, density) {
            snapFlingBehavior(SnapLayoutInfoProvider(list, SnapPosition.Center),
                SplineBasedFloatDecayAnimationSpec(density).generateDecayAnimationSpec(),
                tween(180, easing = FastOutSlowInEasing))
        }
        val fling = remember(nativeFling) { object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                flingRunning = true
                awaitingFling = false
                return try { with(nativeFling) { performFling(initialVelocity) } }
                finally { flingRunning = false }
            }
        } }
        DisposableEffect(Unit) { onDispose { onBrowsing(false) } }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            browsing = false; awaitingFling = false; onBrowsing(false)
            scope.launch { list.stopScroll() }
        }
        LaunchedEffect(list) {
            list.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> {
                        dragRevision++; awaitingFling = true; browsing = true; onBrowsing(true)
                    }
                    is DragInteraction.Cancel -> awaitingFling = false
                }
            }
        }
        LaunchedEffect(list) {
            // Native scrolling briefly reports idle at drag -> fling handoff.
            // Publish only after the final snap, never during that handoff.
            snapshotFlow { (list.isScrollInProgress || awaitingFling || flingRunning) to dragRevision }
                .distinctUntilChanged().collectLatest { (moving, _) ->
                    if (!moving && browsing) {
                        val selected = CampusTone.entries[ThemeBeltGeometry.value(position, count)]
                        val image = artworks[selected.ordinal]
                        displayedTone = selected; frames = ImpressionFrames(image, image)
                        progress.snapTo(1f)
                        // Keep the strip open after snapping. A new drag cancels
                        // this delay through collectLatest and starts a fresh one.
                        delay(ThemeBeltGeometry.holdMillis)
                        if (!browsing || !currentSelectable || list.isScrollInProgress || awaitingFling || flingRunning)
                            return@collectLatest
                        if (selected != currentTone) latestAction(campus, selected)
                        browsing = false; onBrowsing(false)
                    }
                }
        }
        LaunchedEffect(selectable) {
            if (!selectable) {
                browsing = false; awaitingFling = false; onBrowsing(false)
                list.stopScroll()
            }
        }
        LaunchedEffect(tone, browsing) {
            if (browsing) return@LaunchedEffect
            list.scrollToItem(ThemeBeltGeometry.nearest(tone.ordinal, position.roundToInt(), count))
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
        fun step(direction: Int) { if(selectable && !browsing) latestAction(campus,tone.step(direction)) }
        Column(Modifier.fillMaxWidth().padding(top=22.dp,bottom=18.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(95.dp).testTag("settings_theme_belt").semantics {
                this[ThemeBeltPosition] = position - initial
                this[ThemeBeltBrowsing] = browsing
                this[ThemeBeltReveal] = reveal
            }, contentAlignment = Alignment.TopCenter) {
                Box(Modifier.size(95.dp).testTag("settings_theme_frame")
                    .background(themeRoles().impressionFrame, RoundedCornerShape(14.dp))
                    .padding(10.dp)) {
                Box(Modifier.size(75.dp).testTag("settings_theme_impression")
                    .semantics {
                        contentDescription="${campus.label}配色预览"
                        stateDescription=if(browsing) nearestTone.title else tone.title
                        this[ThemePreviewSlideProgress]=progress.value
                        customActions=listOf(
                            CustomAccessibilityAction("上一配色") { if(selectable) { step(-1);true } else false },
                            CustomAccessibilityAction("下一配色") { if(selectable) { step(1);true } else false })
                        if(!selectable || browsing) disabled()
                    }
                    .onKeyEvent {
                        if(selectable && it.type==KeyEventType.KeyUp && it.key in listOf(Key.DirectionLeft,Key.DirectionRight)) {
                            step(if(it.key==Key.DirectionRight) 1 else -1);true
                        } else false
                    }.focusable(selectable && !browsing))
                }
                Canvas(Modifier.matchParentSize()) {
                    if (browsing || reveal > .001f) drawThemeBelt(artworks, position, reveal)
                    else inset((size.width-pixels)/2f, 10.dp.toPx(),
                        (size.width-pixels)/2f,size.height-10.dp.toPx()-pixels) {
                        clipPath(Path().apply { addRoundRect(RoundRect(Rect(0f,0f,pixels.toFloat(),pixels.toFloat()),
                            CornerRadius(4.dp.toPx()))) }) {
                            slide(frames,progress.value,direction)
                        }
                    }
                }
                // Only the lazy row owns touch/velocity; the art is drawn separately
                // so visual depth never changes the touch targets or layout.
                LazyRow(state = list, flingBehavior = fling, userScrollEnabled = selectable,
                    contentPadding = PaddingValues(horizontal = ((maxWidth-ThemeBeltGeometry.strideDp.dp)/2).coerceAtLeast(0.dp)),
                    modifier = Modifier.matchParentSize().graphicsLayer { alpha=0f }.clearAndSetSemantics { }) {
                    items(count * ThemeBeltGeometry.cycles) { Spacer(Modifier.width(ThemeBeltGeometry.strideDp.dp).fillMaxHeight()) }
                }
                if (reveal < .999f) {
                    Row(Modifier.align(Alignment.Center).graphicsLayer { alpha = 1f-reveal },
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(ThemeArrowFrameGap)) {
                        ThemeArrow(false,selectable && !browsing,enabled) { step(-1) }
                        Spacer(Modifier.size(95.dp))
                        ThemeArrow(true,selectable && !browsing,enabled) { step(1) }
                    }
                }
            }
            Text(if(browsing) nearestTone.title else tone.title,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.testTag("settings_theme_name"))
        }
    }
}

private fun LazyListLayoutInfo.themeBeltPosition(fallback: Int): Float {
    val center = (viewportStartOffset + viewportEndOffset) / 2f
    val item = visibleItemsInfo.minByOrNull { abs(it.offset + it.size/2f-center) } ?: return fallback.toFloat()
    return item.index - (item.offset + item.size/2f-center)/item.size
}

private fun DrawScope.drawThemeBelt(artworks: List<ImageBitmap>, position: Float, reveal: Float) {
    val stride = ThemeBeltGeometry.strideDp.dp.toPx()
    val edge = minOf(size.width/2-24.dp.toPx(),168.dp.toPx()).coerceAtLeast(1f)
    val bounds = Rect(size.width/2-edge,0f,size.width/2+edge,size.height)
    drawContext.canvas.saveLayer(bounds,Paint())
    clipRect(bounds.left,bounds.top,bounds.right,bounds.bottom) {
        for (index in floor(position).toInt()-3..ceil(position).toInt()+3) {
            val distance = index-position
            val image = artworks[Math.floorMod(index,artworks.size)]
            val center = Offset(size.width/2 + distance*stride,size.height/2)
            val x = center.x-image.width/2f
            val y = center.y-image.height/2f
            val area = Rect(x,y,x+image.width,y+image.height)
            val opacity = ThemeBeltGeometry.alpha(distance) *
                (reveal+(1f-reveal)*(1f-abs(distance)).coerceIn(0f,1f))
            scale(ThemeBeltGeometry.scale(distance),pivot=center) {
                clipPath(Path().apply { addRoundRect(RoundRect(area,CornerRadius(4.dp.toPx()))) }) {
                    translate(x,y) {
                        drawImage(image,dstOffset=IntOffset.Zero,alpha=opacity,filterQuality=FilterQuality.Medium)
                    }
                }
            }
        }
        val fade=(32.dp.toPx()/bounds.width).coerceAtMost(.45f)
        drawRect(Brush.horizontalGradient(0f to Color.Transparent,fade to Color.White,
            1f-fade to Color.White,1f to Color.Transparent,startX=bounds.left,endX=bounds.right),
            topLeft=Offset(bounds.left,0f),size=Size(bounds.width,bounds.height),blendMode=BlendMode.DstIn)
    }
    drawContext.canvas.restore()
}

@Composable
private fun ThemeArrow(next: Boolean, enabled: Boolean, showEnabled: Boolean, onClick: () -> Unit) {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale=animateFloatAsState(if(pressed) .82f else 1f,tween(90),label="theme arrow press")
    // Lock input without flashing between a captured bright arrow and a dim live one.
    val ink=themeRoles().settingsIcon.copy(alpha=if(showEnabled) 1f else .38f)
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
            CampusTone.MATISSE -> {
                drawRect(Color(0xFFD2DECD))
                drawOval(Color(0xFF343E47),Offset(62f,-28f),Size(36f,93f))
                drawPath(Path().apply {
                    moveTo(0f,48f); cubicTo(29f,17f,56f,59f,57f,100f)
                    lineTo(0f,100f);close()
                },Color(0xFFAA3D36))
                drawPath(Path().apply {
                    moveTo(54f,100f);cubicTo(56f,72f,76f,62f,100f,62f)
                    lineTo(100f,100f);close()
                },Color(0xFFE3EAAB))
                drawCircle(Color(0xFF8F603D),8f,Offset(25f,20f))
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
