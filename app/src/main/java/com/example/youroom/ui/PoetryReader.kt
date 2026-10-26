package com.example.youroom.ui

import android.graphics.Matrix
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.youroom.R
import com.example.youroom.domain.LeisureItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot

/* Visual thesis: matte theme-colored paper with a quiet diagonal fold and soft occlusion.
 * Content plan: inset verse, smaller attribution, refresh printed into each page's upper-right corner.
 * Interaction thesis: follow a thumb arc, settle or spring back, reveal the actual next poem.
 * Gestures never append reading history until the page has finished turning. */
@Stable
private class PoetryTurn {
    var preview by mutableStateOf<LeisureItem?>(null)
    var direction by mutableIntStateOf(1)
    var progress by mutableFloatStateOf(0f)
    var slope by mutableFloatStateOf(.25f)
    var settling by mutableStateOf(false)
    var job: Job?=null
    fun reset() { preview=null;progress=0f;settling=false }
}

@Composable
internal fun PoetryPageReader(item: LeisureItem,active: Boolean,onPreview: (Int)->LeisureItem,
    onNavigate: (Int)->Unit,modifier: Modifier=Modifier) {
    val turn=remember(item.id) { PoetryTurn() }
    val scope=rememberCoroutineScope()
    val currentId by rememberUpdatedState(item.id)
    val enabled by rememberUpdatedState(active)
    val preview by rememberUpdatedState(onPreview)
    val navigate by rememberUpdatedState(onNavigate)
    val scroll=remember(item.id) { ScrollState(0) }
    val paper=plannerUtilityBackground(MaterialTheme.colorScheme,PlannerPanel.LEISURE)
    val ink=MaterialTheme.colorScheme.onSurface
    val shade=MaterialTheme.colorScheme.surfaceContainerHighest
    val reflection=remember { Matrix() }
    val bleed=remember { Paint().apply { alpha=.11f } }

    DisposableEffect(turn) { onDispose { turn.job?.cancel() } }
    LaunchedEffect(active,turn) { if(!active) { turn.job?.cancel();turn.reset() } }
    fun settle(commit: Boolean) {
        if(turn.settling || turn.preview==null) return
        turn.settling=true
        val id=item.id;val direction=turn.direction
        turn.job=scope.launch {
            try {
                val spec=if(commit) tween<Float>((520*(1f-turn.progress)).toInt().coerceIn(260,520),easing=FastOutSlowInEasing)
                    else spring(dampingRatio=.88f,stiffness=550f)
                animate(turn.progress,if(commit) 1f else 0f,animationSpec=spec) { value,_ -> turn.progress=value.coerceIn(0f,1f) }
                if(commit && enabled && currentId==id) navigate(direction)
            } finally { turn.reset();turn.job=null }
        }
    }
    fun request(direction: Int): Boolean {
        if(!enabled || turn.preview!=null) return false
        turn.direction=direction;turn.slope=.28f;turn.preview=preview(direction)
        settle(true);return true
    }
    Box(modifier.fillMaxWidth().clipToBounds().testTag("planner_poetry_pages")
        .semantics { customActions=listOf(
            CustomAccessibilityAction("下一则诗文") { request(1) },
            CustomAccessibilityAction("上一则诗文") { request(-1) }) }
        .pointerInput(active,item.id) {
            if(!active) return@pointerInput
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                if(down.isConsumed) return@awaitEachGesture
                if(turn.settling) { down.consume();return@awaitEachGesture }
                var locked=false
                var delta=Offset.Zero
                var commit=false
                try {
                    while(true) {
                        val event=awaitPointerEvent(PointerEventPass.Initial)
                        val change=event.changes.firstOrNull { it.id==down.id }
                        if(change==null || change.isConsumed || event.changes.count { it.pressed }>1) break
                        delta=change.position-down.position
                        if(!locked) {
                            when(poetryDragIntent(delta.x,delta.y,viewConfiguration.touchSlop)) {
                                PoetryDragIntent.SCROLL -> break
                                PoetryDragIntent.PAGE -> {
                                    turn.direction=if(delta.x<0f) 1 else -1
                                    turn.preview=preview(turn.direction);locked=true
                                }
                                PoetryDragIntent.UNDECIDED -> { if(!change.pressed) break else continue }
                            }
                        }
                        change.consume()
                        val horizontal=(-turn.direction*delta.x).coerceAtLeast(0f)
                        turn.progress=if(horizontal>0f) (hypot(horizontal,delta.y*.45f)/(size.width*.92f)).coerceIn(0f,.94f) else 0f
                        val corner=if(down.position.y>size.height*.5f) 1f else -1f
                        turn.slope=(corner*.20f-delta.y/size.height.coerceAtLeast(1)*.38f).coerceIn(-.65f,.65f)
                        if(!change.pressed) {
                            commit=poetrySwipeDirection(delta.x,delta.y,size.width.toFloat(),48.dp.toPx())==turn.direction
                            break
                        }
                    }
                } finally { if(locked) settle(commit) }
            }
        }) {
        turn.preview?.let { incoming ->
            key(incoming.id) {
                Box(Modifier.fillMaxSize().background(paper).clearAndSetSemantics { }) {
                    PoetryTextPage(incoming,rememberScrollState(),false,Modifier.fillMaxSize())
                    PoetryRefreshButton(front=false,active=active,busy=true,onClick={})
                }
            }
        }
        Box(Modifier.fillMaxSize().drawWithContent {
            val progress=turn.progress
            if(turn.preview==null || progress<=.0001f) { drawContent();return@drawWithContent }
            val fold=poetryPageFold(size.width,size.height,progress,turn.slope,turn.direction)
            fun polygon(points: List<Offset>)=Path().apply {
                if(points.isNotEmpty()) { moveTo(points[0].x,points[0].y);points.drop(1).forEach { lineTo(it.x,it.y) };close() }
            }
            val front=polygon(fold.front);val back=polygon(fold.back)
            clipPath(front) { this@drawWithContent.drawContent() }
            val normal=fold.normal/fold.normal.getDistance()
            val center=fold.normal*(fold.distance/fold.normal.getDistanceSquared())
            val curl=24.dp.toPx()*(kotlin.math.sin(progress*Math.PI).toFloat()*.6f+.4f)
            val shadow=3.dp.toPx()
            // Matte paper: an even back tone and a narrow occlusion shadow,
            // without a bright ridge or a specular highlight travelling along it.
            withTransform({ translate(normal.x*shadow,normal.y*shadow) }) {
                drawPath(back,ink.copy(alpha=.025f))
            }
            drawPath(back,lerp(paper,shade,.25f))
            clipPath(back) {
                val n=fold.normal;val q=n.getDistanceSquared();val d=fold.distance
                reflection.setValues(floatArrayOf(1-2*n.x*n.x/q,-2*n.x*n.y/q,2*n.x*d/q,
                    -2*n.x*n.y/q,1-2*n.y*n.y/q,2*n.y*d/q,0f,0f,1f))
                val canvas=drawContext.canvas
                canvas.saveLayer(Rect(Offset.Zero,size),bleed)
                canvas.nativeCanvas.concat(reflection)
                this@drawWithContent.drawContent()
                canvas.restore()
                drawRect(Brush.linearGradient(listOf(ink.copy(alpha=.018f),ink.copy(alpha=.009f),ink.copy(alpha=0f)),
                    center-normal*2.dp.toPx(),center-normal*curl))
            }
            if(fold.crease.size>=2) {
                drawLine(ink.copy(alpha=.06f),fold.crease.first(),fold.crease.last(),.55.dp.toPx(),StrokeCap.Round)
            }
        }.background(paper)) {
            PoetryTextPage(item,scroll,true,Modifier.fillMaxSize(),scrollEnabled=active && turn.preview==null)
            // The control is part of drawContent: it shares the page's clipping,
            // reflection and departure rather than staying above the fold.
            PoetryRefreshButton(front=true,active=active,busy=turn.preview!=null,onClick={ request(1) })
        }
    }
}

@Composable
private fun BoxScope.PoetryRefreshButton(front: Boolean,active: Boolean,busy: Boolean,onClick: ()->Unit) {
    val ink=MaterialTheme.colorScheme.onSurface
    IconButton(onClick=onClick,enabled=front && active && !busy,
        colors=IconButtonDefaults.iconButtonColors(contentColor=ink,
            disabledContentColor=if(active) ink else ink.copy(alpha=.38f)),
        modifier=Modifier.align(Alignment.TopEnd).padding(end=6.dp,top=4.dp)
            .then(if(front) Modifier.guideTag("planner_leisure_next") else Modifier)) {
        Icon(Icons.Outlined.Refresh,if(front) "刷新按钮" else null,Modifier.size(20.dp))
    }
}

@Composable
private fun PoetryTextPage(item: LeisureItem,scroll: ScrollState,front: Boolean,modifier: Modifier,
    scrollEnabled: Boolean=false) {
    Column(modifier.then(if(front) Modifier.guideTag("planner_leisure_reader") else Modifier)
        .verticalScroll(scroll,enabled=scrollEnabled)
        .padding(start=36.dp,end=52.dp,top=34.dp,bottom=18.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(item.body.replace(PoetryPause,"\n"),if(front) Modifier.testTag("planner_poetry_body") else Modifier,
            fontFamily=PoetryFont,fontWeight=FontWeight.Normal,fontSize=19.sp,lineHeight=30.sp,letterSpacing=.8.sp)
        Text(item.source,if(front) Modifier.testTag("planner_poetry_source") else Modifier,
            fontFamily=PoetryFont,fontWeight=FontWeight.Normal,fontSize=11.sp,lineHeight=18.sp,letterSpacing=.3.sp,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val PoetryFont=FontFamily(Font(R.font.poetry_regular,FontWeight.Normal))
private val PoetryPause=Regex("(?<=[，。！？；])(?=\\S)")
