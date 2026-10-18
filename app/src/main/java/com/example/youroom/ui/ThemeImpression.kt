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
import androidx.compose.ui.graphics.vector.PathParser
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
            CampusTone.MONDRIAN -> {
                withTransform({ scale(100f/75f,100f/75f,pivot=Offset.Zero) }) {
                    drawRect(Color(0xFFFAFAF6),size=Size(75f,75f))
                    drawRect(Color(0xFFCC3828),Offset(28f,0f),Size(47f,22f))
                    drawRect(Color(0xFF274997),Offset(0f,27f),Size(23f,25f))
                    drawRect(Color(0xFFEEE95D),Offset(58f,58f),Size(17f,17f))
                    drawPath(PathParser().parsePathString("M25.5 0v75M0 24.5h75M0 54.5h75M55.5 24.5V75").toPath(),
                        Color(0xFF060102),style=Stroke(3f,cap=StrokeCap.Butt))
                }
            }
            CampusTone.MORNING_BELL -> {
                // Accepted first-edition sky, silver bell and vermilion drum.
                withTransform({ scale(100f/75f,100f/75f,pivot=Offset.Zero) }) {
                    fun shape(data: String,argb: Long,width: Float=0f) {
                        drawPath(PathParser().parsePathString(data).toPath(),Color(argb),
                            style=if(width>0f) Stroke(width,cap=StrokeCap.Round,join=StrokeJoin.Round) else Fill)
                    }
                    drawRect(Color(0xFF8EA4CA),size=Size(75f,75f))
                    drawCircle(Color(0xFFEDEDF4),8f,Offset(56f,17f))
                    shape("M0 41Q25 28 75 39V75H0Z",0xFFD5DEEE)
                    shape("M0 57Q36 41 75 51V75H0Z",0xFFEDEDF4)
                    shape("M13 48V32a12 12 0 0 1 24 0v16Z",0xFFB9C4D6)
                    shape("M25 20a12 12 0 0 1 12 12v16H25Z",0xFF8091AC)
                    shape("M10 48h30v4H10Z",0xFF625466)
                    shape("M25 15v6M25 52v5",0xFF625466,2f)
                    drawCircle(Color(0xFF625466),2f,Offset(25f,58f))
                    shape("M48 53h17v12H48Z",0xFF9F463B)
                    drawOval(Color(0xFFC96C5C),Offset(48f,50f),Size(17f,6f))
                    shape("m46 44 19 10m-15-13 13 15",0xFF625466,1.8f)
                }
            }
            CampusTone.AUTUMN_CITRUS -> {
                // Third-edition theme keeps the accepted first-edition fruit artwork.
                withTransform({ scale(100f/75f,100f/75f,pivot=Offset.Zero) }) {
                    fun shape(data: String,argb: Long,width: Float=0f) {
                        drawPath(PathParser().parsePathString(data).toPath(),Color(argb),
                            style=if(width>0f) Stroke(width,cap=StrokeCap.Round,join=StrokeJoin.Round) else Fill)
                    }
                    drawRect(Color(0xFFF0EBD9),size=Size(75f,75f))
                    shape("M0 60c23 7 50-6 75-15v30H0Z",0xFFC2B892)
                    shape("M6 22c23 3 36-4 58-14M29 23l-3 14M50 14l7 16",0xFF56613C,1.6f)
                    shape("M19 23C9 21 7 13 9 8c9 1 13 7 10 15Z",0xFF667446)
                    shape("M30 21c-2-10 4-16 12-15 2 9-4 14-12 15Z",0xFF9AA066)
                    shape("M48 16c4 7 13 8 18 4-4-7-11-9-18-4Z",0xFF4C5639)
                    shape("M26 31c-11-3-20 5-19 15 0 12 16 18 25 10 11-9 6-23-6-25Z",0xFFC59359)
                    shape("M15 38c2-3 5-4 8-3",0xFFECD5A8,1.9f)
                    shape("M25 31l-1-5",0xFF56613C,1.4f)
                    shape("M56 29c-9-2-16 4-15 13 1 11 15 15 22 8 8-7 4-19-7-21Z",0xFF9AA066)
                    shape("M46 35c2-2 5-3 7-2",0xFFD7DDB2,1.6f)
                    shape("M54 29l-2-5",0xFF56613C,1.4f)
                    shape("M8 66h22M35 66h8",0xFF8E835E,.8f)
                }
            }
            CampusTone.RED_BEAN -> {
                // Accepted second-edition silver bell / red bean artwork, in preview coordinates.
                withTransform({ scale(100f/75f,100f/75f,pivot=Offset.Zero) }) {
                    fun shape(data: String,argb: Long,width: Float=0f) {
                        drawPath(PathParser().parsePathString(data).toPath(),Color(argb),
                            style=if(width>0f) Stroke(width,cap=StrokeCap.Round,join=StrokeJoin.Round) else Fill)
                    }
                    drawRect(Color(0xFFF5F4F0),size=Size(75f,75f))
                    shape("M0 68c25-7 49-14 75-33v40H0Z",0xFFE0E3E6)
                    shape("M55-3c0 12-7 15-11 22s-4 12-8 18-9 11-14 17",0xFFA9B3BE,.9f)
                    drawCircle(Color(0xFF7F8D9D),2.5f,Offset(45f,20f),style=Stroke(1.1f))
                    shape("M43 24c-6 3-9 10-9 17 0 8 24 8 24 0 0-7-3-14-9-17Z",0xFFB9C3CF)
                    shape("M49 25c5 3 7 8 7 14-1 3-5 4-9 4l-1-17Z",0xFF9DAABA)
                    shape("M38 32c1-3 3-5 5-6",0xFFFFFDFC,1.8f)
                    drawOval(Color(0xFF7D8B9C),Offset(34f,38f),Size(24f,8f))
                    shape("M39 41c4-1 9-1 13 0",0xFFDFE5EB,.8f)
                    shape("M46 40v7",0xFF4D5B6B,1.2f)
                    drawCircle(Color(0xFF78879B),1.7f,Offset(46f,48f))
                    shape("M15 54c4-6 11-5 14 0 3 6-1 13-6 14-7 1-12-7-8-14Z",0xFFB3392F)
                    shape("M18 55c1-2 3-3 4-3",0xFFE3A08A,1.4f)
                    shape("M24 61c-1 3-2 4-4 5",0xFF96372F,.8f)
                }
            }
            CampusTone.MORANDI -> {
                // The approved still-life artwork is unchanged by the fourth-edition accents.
                withTransform({ scale(100f/75f,100f/75f,pivot=Offset.Zero) }) {
                    drawRect(Color(0xFFE7EAE4),size=Size(75f,75f))
                    drawRect(Color(0xFFCCC6BA),Offset(0f,49f),Size(75f,26f))
                    drawPath(Path().apply {
                        moveTo(41f,8f);lineTo(51f,8f);lineTo(51f,21f)
                        cubicTo(51f,26f,59f,27f,59f,34f);lineTo(59f,63f);lineTo(33f,63f);lineTo(33f,34f)
                        cubicTo(33f,27f,41f,26f,41f,21f);close()
                    },Color(0xFF8D9DA5))
                    drawLine(Color(0xFFB7C4C8).copy(alpha=.7f),Offset(44f,12f),Offset(44f,57f),2f)
                    drawPath(Path().apply {
                        moveTo(12f,33f);lineTo(28f,33f);lineTo(28f,40f)
                        cubicTo(33f,43f,34f,47f,34f,52f);lineTo(34f,63f);lineTo(6f,63f);lineTo(6f,52f)
                        cubicTo(6f,47f,7f,43f,12f,40f);close()
                    },Color(0xFFC3A8AC))
                    drawOval(Color(0xFFAC9399),Offset(12f,31f),Size(16f,4f))
                    drawPath(Path().apply {
                        moveTo(29f,53f);lineTo(65f,53f)
                        cubicTo(64f,63f,58f,69f,47f,69f);cubicTo(36f,69f,30f,63f,29f,53f);close()
                    },Color(0xFFD8C9AD))
                    drawOval(Color(0xFFE6DCC8),Offset(29f,49f),Size(36f,8f))
                    drawLine(Color(0xFFA6AAA5),Offset(6f,69f),Offset(66f,69f),1f)
                }
            }
            CampusTone.BURGUNDY -> {
                // Accepted third-edition curtain / stemmed glass artwork, matching the preview.
                withTransform({ scale(100f/75f,100f/75f,pivot=Offset.Zero) }) {
                    drawRect(Color(0xFFE7D9C6),size=Size(75f,75f))
                    drawPath(Path().apply {
                        moveTo(50f,0f);lineTo(75f,0f);lineTo(75f,75f);lineTo(59f,75f)
                        cubicTo(40f,52f,66f,27f,50f,0f);close()
                    },Color(0xFF713F3D))
                    drawPath(Path().apply { moveTo(58f,0f);cubicTo(75f,27f,50f,47f,65f,75f) },
                        Color(0xFF916B58),style=Stroke(2f))
                    drawCircle(Color(0xFFCEB28D),8f,Offset(14f,17f))
                    val glass=Path().apply {
                        moveTo(23f,16f);lineTo(50f,16f);lineTo(52f,34f)
                        cubicTo(53f,46f,44f,53f,37f,53f);cubicTo(30f,53f,21f,46f,22f,34f);close()
                    }
                    drawPath(glass,Color(0xFFF7F2EB))
                    drawPath(Path().apply {
                        moveTo(23f,33f);cubicTo(32f,37f,43f,28f,51f,32f);lineTo(52f,35f)
                        cubicTo(52f,45f,44f,51f,37f,51f);cubicTo(30f,51f,22f,45f,22f,35f);close()
                    },Color(0xFF800020))
                    drawPath(glass,Color(0xFF5A2736),style=Stroke(1.4f))
                    drawPath(Path().apply {
                        moveTo(37f,53f);lineTo(37f,66f);moveTo(26f,67f);lineTo(48f,67f)
                    },Color(0xFF5A2736),style=Stroke(1.4f))
                    drawLine(Color(0xFFDCD2C6),Offset(27f,20f),Offset(27f,31f),1.5f)
                }
            }
            CampusTone.PEACH_PLUM -> {
                withTransform({ scale(100f/75f,100f/75f,pivot=Offset.Zero) }) {
                    drawRect(Color(0xFFFAEFE5),size=Size(75f,75f))
                    drawCircle(Color(0xFFFAC7B7),17f,Offset(23f,23f))
                    drawPath(Path().apply {
                        moveTo(50f,7f);cubicTo(41f,15f,59f,17f,50f,25f)
                        moveTo(61f,15f);cubicTo(54f,21f,66f,24f,60f,30f)
                    },Color(0xFF9B838E),style=Stroke(1.5f))
                    drawPath(Path().apply {
                        moveTo(15f,39f);lineTo(61f,39f)
                        cubicTo(61f,57f,53f,65f,38f,65f);cubicTo(23f,65f,15f,57f,15f,39f);close()
                    },Color(0xFF6B5B63))
                    drawPath(Path().apply {
                        moveTo(61f,43f);cubicTo(74f,40f,74f,57f,55f,56f)
                    },Color(0xFF6B5B63),style=Stroke(3f))
                    drawOval(Color(0xFFD7AF9F),Offset(15f,34f),Size(46f,10f))
                    drawPath(Path().apply {
                        moveTo(20f,39f);cubicTo(30f,35f,45f,35f,56f,39f)
                    },Color(0xFFFCECE3),style=Stroke(1.2f))
                    drawLine(Color(0xFFB99C9D),Offset(17f,69f),Offset(62f,69f),1.5f)
                }
            }
            CampusTone.OLD_PORT -> {
                // The accepted second-edition harbor, letter and postmark artwork.
                withTransform({ scale(100f/75f,100f/75f,pivot=Offset.Zero) }) {
                    drawRect(Color(0xFFE8DDC8),size=Size(75f,75f))
                    drawPath(Path().apply {
                        moveTo(0f,35f);cubicTo(17f,29f,26f,44f,43f,37f)
                        cubicTo(60f,30f,62f,32f,75f,38f);lineTo(75f,75f);lineTo(0f,75f);close()
                    },Color(0xFF788D81))
                    drawPath(Path().apply {
                        moveTo(0f,55f);cubicTo(21f,46f,33f,63f,53f,53f)
                        cubicTo(73f,43f,68f,52f,75f,48f);lineTo(75f,75f);lineTo(0f,75f);close()
                    },Color(0xFF38585A))
                    withTransform({ rotate(-10f,pivot=Offset(36f,34f)) }) {
                        drawRoundRect(Color(0xFFF3ECE0),Offset(13f,19f),Size(46f,31f),CornerRadius(1f))
                        drawPath(Path().apply {
                            moveTo(14f,20f);lineTo(36f,37f);lineTo(58f,20f)
                            moveTo(14f,49f);lineTo(29f,34f);moveTo(58f,49f);lineTo(43f,35f)
                        },Color(0xFF9B8062),style=Stroke(1.2f))
                    }
                    drawCircle(Color(0xFFDDC1A0),8f,Offset(55f,22f))
                    drawCircle(Color(0xFF885F48),8f,Offset(55f,22f),style=Stroke(1f))
                    drawPath(Path().apply {
                        moveTo(50f,22f);lineTo(60f,22f);moveTo(52f,19f);lineTo(58f,19f)
                        moveTo(52f,25f);lineTo(58f,25f)
                    },Color(0xFF885F48),style=Stroke(1f))
                    drawPath(Path().apply {
                        moveTo(5f,63f);lineTo(22f,63f);moveTo(29f,63f);lineTo(38f,63f)
                    },Color(0xFFE8DDC8),style=Stroke(1.2f))
                }
            }
            CampusTone.DUNHUANG -> {
                // Keep the approved FIRST-edition artwork in both modes, independent
                // of the fourth-edition page roles. Coordinates match the 75px preview.
                withTransform({ scale(100f/75f,100f/75f,pivot=Offset.Zero) }) {
                    drawRect(Color(0xFFCABEAF),size=Size(75f,75f))
                    drawPath(Path().apply {
                        moveTo(0f,0f);lineTo(75f,0f);lineTo(75f,35f)
                        cubicTo(49f,23f,26f,54f,0f,38f);close()
                    },Color(0xFF8FBFB0))
                    drawPath(Path().apply {
                        moveTo(0f,62f);cubicTo(21f,39f,44f,43f,75f,27f)
                        lineTo(75f,75f);lineTo(0f,75f);close()
                    },Color(0xFFB78F68))
                    drawPath(Path().apply {
                        moveTo(51f,75f);lineTo(51f,42f)
                        cubicTo(51f,35.925f,55.925f,31f,62f,31f)
                        cubicTo(68.075f,31f,73f,35.925f,73f,42f)
                        lineTo(73f,75f);close()
                    },Color(0xFFA64C3B))
                    drawPath(Path().apply {
                        moveTo(56f,75f);lineTo(56f,44f)
                        cubicTo(56f,40.686f,58.686f,38f,62f,38f)
                        cubicTo(65.314f,38f,68f,40.686f,68f,44f)
                        lineTo(68f,75f);close()
                    },Color(0xFF6B3B32))
                    drawCircle(Color(0xFFA64C3B),6f,Offset(19f,18f))
                }
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
