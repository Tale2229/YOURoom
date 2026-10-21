package com.example.youroom.ui

import com.example.youroom.ui.theme.themeRoles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.example.youroom.domain.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.tan

internal val ThemePreviewSlideProgress = SemanticsPropertyKey<Float>("ThemePreviewSlideProgress")
internal val ThemeBeltPosition = SemanticsPropertyKey<Float>("ThemeBeltPosition")
internal val ThemeBeltVisualPosition = SemanticsPropertyKey<Float>("ThemeBeltVisualPosition")
internal val ThemeBeltBrowsing = SemanticsPropertyKey<Boolean>("ThemeBeltBrowsing")
internal val ThemeBeltReveal = SemanticsPropertyKey<Float>("ThemeBeltReveal")
internal val ThemeFrameTone = SemanticsPropertyKey<String>("ThemeFrameTone")
internal val ThemeFrameAttached = SemanticsPropertyKey<Boolean>("ThemeFrameAttached")
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
    val resources = LocalContext.current.resources
    // Decode/resize once per density, not on each gesture, frame or mode change.
    // All twelve approved images have the same artwork in both modes.
    val artworks = remember(resources, pixels, density.density) {
        CampusTone.entries.map { impression(resources, pixels, density, it) }
    }
    val latestAction by rememberUpdatedState(onTone)
    val preview = LocalThemePreview.current
    val onBrowsing by rememberUpdatedState(preview?.onBrowsing ?: {})
    val onSettledTone by rememberUpdatedState(preview?.onSettledTone)
    val selectable = enabled && preview?.busy != true
    // This small preview moves immediately; the page's separate wave is debounced.
    key(campus, pixels, night) {
        var displayedTone by remember { mutableStateOf(tone) }
        var frames by remember { artworks[tone.ordinal].let { mutableStateOf(ImpressionFrames(it,it)) } }
        val progress = remember { Animatable(1f) }
        var direction by remember { mutableIntStateOf(1) }
        val count = artworks.size
        val initial = remember { ThemeBeltGeometry.initial(tone.ordinal, count) }
        val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
        val scope = rememberCoroutineScope()
        var browsing by remember { mutableStateOf(false) }
        var revealingArrows by remember { mutableStateOf(false) }
        var dragRevision by remember { mutableLongStateOf(0L) }
        var awaitingFling by remember { mutableStateOf(false) }
        var flingRunning by remember { mutableStateOf(false) }
        val currentTone by rememberUpdatedState(tone)
        val currentSelectable by rememberUpdatedState(selectable)
        val position by remember { derivedStateOf { list.layoutInfo.themeBeltPosition(initial) } }
        var captured by remember { mutableStateOf<Int?>(initial) }
        // Raw touch travel escapes the detent; the shared visual target stays exactly
        // centered until then. A short critically damped pull avoids teleporting.
        val visualPosition by animateFloatAsState(
            ThemeBeltMagnet.position(position, captured),
            spring(dampingRatio = 1f, stiffness = 1800f, visibilityThreshold = .001f),
            label = "theme belt detent")
        var lastCapturedTone by remember { mutableStateOf(tone) }
        val frameTone = if (browsing || revealingArrows) lastCapturedTone else tone
        val nearestTone = CampusTone.entries[ThemeBeltGeometry.value(position, count)]
        val reveal by animateFloatAsState(if (browsing) 1f else 0f, tween(ThemeBeltGeometry.revealMillis), label = "theme belt reveal")
        val nativeFling = remember(list, density) {
            snapFlingBehavior(SnapLayoutInfoProvider(list, SnapPosition.Center),
                SplineBasedFloatDecayAnimationSpec(density).generateDecayAnimationSpec(),
                tween(180, easing = FastOutSlowInEasing))
        }
        val releaseCapture by rememberUpdatedState { ThemeBeltMagnet.captured(position, captured) }
        val fling = remember(nativeFling) { object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                flingRunning = true
                awaitingFling = false
                val velocity = ThemeBeltMagnet.releaseVelocity(initialVelocity, releaseCapture())
                return try { with(nativeFling) { performFling(velocity) } }
                finally { flingRunning = false }
            }
        } }
        DisposableEffect(Unit) { onDispose { onBrowsing(false) } }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            browsing = false; revealingArrows = false; awaitingFling = false; onBrowsing(false)
            scope.launch { list.stopScroll() }
        }
        LaunchedEffect(list) {
            snapshotFlow { position to if (!browsing && !revealingArrows) tone else null }
                .collect { (position, resetTone) ->
                    captured = ThemeBeltMagnet.captured(position, captured)
                    // A gap releases capture, not its color. Only a new capture replaces it.
                    lastCapturedTone = resetTone ?: captured?.let { CampusTone.entries[Math.floorMod(it, count)] } ?: lastCapturedTone
                }
        }
        LaunchedEffect(list) {
            list.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> {
                        dragRevision++; awaitingFling = true; revealingArrows = false; browsing = true; onBrowsing(true)
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
                        // Publish the preview before closing the belt so the tone
                        // effect cannot snap it back to the old selected artwork.
                        val settled = onSettledTone
                        if (settled != null) settled(campus, selected)
                        else if (selected != currentTone) latestAction(campus, selected)
                        revealingArrows = true
                        browsing = false
                    }
                }
        }
        // Separate from lazy-list idle collection: programmatic recentring must
        // not cancel this completion and leave the page's browsing lock held.
        LaunchedEffect(browsing, revealingArrows) {
            if (!browsing && revealingArrows) {
                snapshotFlow { reveal }.first { it <= .001f }
                revealingArrows = false
                onBrowsing(false)
            }
        }
        LaunchedEffect(selectable) {
            if (!selectable) {
                browsing = false; revealingArrows = false; awaitingFling = false; onBrowsing(false)
                list.stopScroll()
            }
        }
        LaunchedEffect(tone, browsing) {
            if (browsing) return@LaunchedEffect
            if (ThemeBeltGeometry.value(position, count) != tone.ordinal)
                list.scrollToItem(ThemeBeltGeometry.nearest(tone.ordinal, position.roundToInt(), count))
            if(displayedTone == tone) return@LaunchedEffect
            val frozen = bitmap(pixels,density) { slide(frames,progress.value,direction) }
            val next = artworks[tone.ordinal]
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
                this[ThemeBeltVisualPosition] = visualPosition - initial
                this[ThemeBeltBrowsing] = browsing
                this[ThemeBeltReveal] = reveal
            }, contentAlignment = Alignment.TopCenter) {
                Box(Modifier.size(95.dp).zIndex(1f).testTag("settings_theme_frame")
                    .semantics {
                        this[ThemeFrameTone] = frameTone.id
                        this[ThemeFrameAttached] = (!browsing && !revealingArrows) || captured != null
                    }) {
                ThemeAvatarFrame(campus, frameTone, night, Modifier.matchParentSize())
                Box(Modifier.size(75.dp).align(Alignment.Center).testTag("settings_theme_impression")
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
                    if (browsing || reveal > .001f) drawThemeBelt(artworks, visualPosition, reveal)
                    else {
                        val square = themeAvatarPixelSquare(size.width.roundToInt(), 95.dp.roundToPx(),
                            size.height.roundToInt(), pixels)
                        inset(square.left.toFloat(), square.top.toFloat(),
                            size.width-square.left-pixels, size.height-square.top-pixels) {
                            clipPath(Path().apply { addRoundRect(RoundRect(Rect(0f,0f,pixels.toFloat(),pixels.toFloat()),
                                CornerRadius(4.dp.toPx()))) }) {
                                slide(frames,progress.value,direction)
                            }
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

private fun DrawScope.drawThemeBelt(artworks: List<ImageBitmap>, visualPosition: Float, reveal: Float) {
    val stride = ThemeBeltGeometry.strideDp.dp.toPx()
    val square = themeAvatarPixelSquare(size.width.roundToInt(), 95.dp.roundToPx(),
        size.height.roundToInt(), artworks.first().width)
    val origin = Offset(square.left+square.edge/2f, square.top+square.edge/2f)
    val edge = minOf(size.width/2-24.dp.toPx(),168.dp.toPx()).coerceAtLeast(1f)
    val bounds = Rect(size.width/2-edge,0f,size.width/2+edge,size.height)
    // Every tile uses this one attracted position: all center distances stay 120dp.
    drawContext.canvas.saveLayer(bounds,Paint())
    clipRect(bounds.left,bounds.top,bounds.right,bounds.bottom) {
        for (index in floor(visualPosition).toInt()-3..ceil(visualPosition).toInt()+3) {
            val distance = index-visualPosition
            val image = artworks[Math.floorMod(index,artworks.size)]
            val center = Offset(origin.x + distance*stride,origin.y)
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
    val ink=themeRoles().themeArrow.copy(alpha=if(showEnabled) 1f else .38f)
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

/** Raster artwork is rendered once at the picker size; animations reuse these frames. */
private fun impression(resources: android.content.res.Resources, pixels: Int,
    density: Density, tone: CampusTone): ImageBitmap {
    val artwork = ImageBitmap.imageResource(resources, themeAvatarResource(tone))
    return bitmap(pixels, density) {
        drawImage(artwork, dstSize = IntSize(pixels, pixels), filterQuality = FilterQuality.High)
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
