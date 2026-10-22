package com.example.youroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.YOURoomTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** Stable production anchor IDs also keep the existing UI test tags intact. */
internal fun Modifier.guideTag(id: String): Modifier = composed {
    val guide=LocalUsageGuide.current
    val window=LocalGuideWindow.current
    val dangerTarget=guide?.stage in listOf(GuideStage.LOGOUT,GuideStage.CLEAR_CACHE) &&
        id in listOf("settings_logout","settings_clear_cache")
    if (guide==null || (id !in guide.spotlightTargets && id!="template_import_list" && !dangerTarget)) return@composed this.testTag(id)
    val owner=remember { Any() }
    val bring=remember { BringIntoViewRequester() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun registerAnchor(value:LayoutCoordinates) {
        if(!value.isAttached) return
        val root=value.findRootCoordinates()
        val bounds=value.boundsInRoot().intersect(Rect(0f,0f,root.size.width.toFloat(),root.size.height.toFloat()))
        if(bounds.width>1f && bounds.height>1f) guide.anchors[id]=GuideAnchor(window,bounds,owner)
        else if(guide.anchors[id]?.owner===owner) guide.anchors.remove(id)
    }
    suspend fun relocate() {
        if(!guide.layoutReady) return
        if(guide.target==id && !guide.fixedSettingsPosition) {
            try { bring.bringIntoView() }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:IllegalStateException) { /* The target may still be attaching. */ }
        }
        coordinates?.let(::registerAnchor)
    }
    DisposableEffect(guide,id,window) { onDispose {
        if(guide.anchors[id]?.owner===owner) guide.anchors.remove(id)
    } }
    LaunchedEffect(guide.stage,guide.target,id,window,guide.layoutReady) {
        if (guide.target==id && guide.window==window) {
            withFrameNanos { }
            relocate()
        }
    }
    // Recover late layout/scroll anchors quietly, without preparing the lesson
    // again (which would discard the user's practice). Retries are bounded and
    // cancelled when the target/window changes, disappears, or becomes ready.
    LaunchedEffect(guide.stage,guide.target,id,window,guide.ready) {
        if(guide.ready || guide.window!=window) return@LaunchedEffect
        for(wait in listOf(250L,750L,1500L)) {
            delay(wait)
            if(guide.ready) break
            relocate()
        }
    }
    this.testTag(id).bringIntoViewRequester(bring).onGloballyPositioned {
        coordinates=it
        registerAnchor(it)
    }
}

/** Practice modals share the guide's one physical window and persistent scrim.
 * Keep the caller's theme/density/focus locals when moving its content to the host. */
internal class GuideModal(val window:String,val stage:GuideStage,
    val locals:State<CompositionLocalContext>,val content:State<@Composable () -> Unit>) {
    var order=0
}

@Composable internal fun GuideDialog(guideWindow: String, onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) {
    val guide=LocalUsageGuide.current
    if(guide==null) {
        Dialog(onDismissRequest=onDismissRequest,properties=properties,content=content)
    } else if(guide.acceptsDialog(guideWindow)) {
        val owner=remember { Any() }
        val locals=rememberUpdatedState(currentCompositionLocalContext)
        val currentContent=rememberUpdatedState(content)
        val modal=remember(guideWindow,guide.dialogStage) { GuideModal(guideWindow,guide.dialogStage,locals,currentContent) }
        DisposableEffect(guide,modal) {
            guide.attachDialog(owner,modal)
            onDispose { if(guide.dialogs[owner]===modal) guide.dialogs.remove(owner) }
        }
    }
}

@Composable internal fun GuidePopup(position: PopupPositionProvider, onDismissRequest: () -> Unit,
    properties: PopupProperties, content: @Composable () -> Unit) {
    if(LocalUsageGuide.current==null) Popup(position,onDismissRequest,properties=properties,content=content)
    else GuideDialog("period",onDismissRequest,DialogProperties(usePlatformDefaultWidth=false)) { content() }
}

@Composable internal fun GuideAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null) {
    if(LocalUsageGuide.current==null) AlertDialog(onDismissRequest,confirmButton=confirmButton,
        dismissButton=dismissButton,title=title,text=text)
    else GuideDialog("alert",onDismissRequest,DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.padding(24.dp).widthIn(max=480.dp).fillMaxWidth(),shape=MaterialTheme.shapes.small) {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                title?.invoke();text?.invoke()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { dismissButton?.invoke();confirmButton() }
            }
        }
    }
}

@Composable internal fun UsageGuideExperience(request: GuideRequest, original: YOURoomState,
    onRuntime: (GuideRuntime) -> Unit = {}, onProgress: (Int, GuideOutcome) -> Unit) {
    val position=rememberSaveable(request.serial) { mutableIntStateOf(request.step.coerceIn(0,GuideStage.entries.lastIndex)) }
    val directory=rememberSaveable(request.serial) { mutableStateOf(request.manual || request.resume) }
    val completed=rememberSaveable(request.serial) { mutableStateOf(emptyList<String>()) }
    val runtime=remember(request.serial) { GuideRuntime(position,original.appearance,original.periodStrip,directory,completed) }
    val practice by runtime.model.state.collectAsState()
    val plans by runtime.model.planning.executions.collectAsState()
    DisposableEffect(runtime) { onDispose { runtime.close() } }
    SideEffect { runtime.onFinish={ outcome -> onProgress(position.intValue,outcome) };onRuntime(runtime) }
    LaunchedEffect(runtime.stage,practice.initializing) {
        if (!practice.initializing) runtime.prepare()
    }
    LaunchedEffect(runtime) {
        snapshotFlow { runtime.preparationFrame() }.collectLatest { frame ->
            if(!runtime.preparing || frame.prepared!=frame.stage || !frame.ready || !frame.layoutReady || frame.window!=frame.presented) return@collectLatest
            // Child effects may still be opening a modal or snapping wheels in
            // this frame. Only reveal the cutouts after two stable layout frames.
            repeat(2) { withFrameNanos { } }
            if(runtime.preparationFrame()==frame) runtime.finishPreparation(frame.stage)
        }
    }
    LaunchedEffect(position.intValue) { onProgress(position.intValue,GuideOutcome.IN_PROGRESS) }
    LaunchedEffect(runtime.stage,runtime.preparedStage,practice,plans,runtime.goal,runtime.events,runtime.anchors.toMap()) { runtime.observe() }
    Dialog(onDismissRequest={ runtime.menu=true },properties=DialogProperties(
        usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        YOURoomTheme(campus=practice.campus,appearance=practice.appearance) {
            CompositionLocalProvider(LocalUsageGuide provides runtime) {
                GuideWindowHost("root",Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    directoryMessage=if(request.resume) "可以继续上次的练习，也可以选择章节重新开始" else "选择一个章节，或从头开始") {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("guide_workspace")) {
                        YOURoomApp(runtime.model,enableGuide=false)
                    }
                }
            }
        }
    }
}

private data class GuidePreparationFrame(val stage:GuideStage,val prepared:GuideStage?,
    val window:String,val presented:String,val ready:Boolean,val layoutReady:Boolean,val goal:PlanningDraft,val anchors:List<GuideAnchor?>)
private fun GuideRuntime.preparationFrame()=GuidePreparationFrame(stage,preparedStage,window,presentedWindow,ready,
    layoutReady,goal,spotlightTargets.map { anchors[it] })

@Composable private fun GuideWindowHost(window: String, modifier: Modifier=Modifier.fillMaxSize(),
    directoryMessage:String,
    content: @Composable BoxScope.() -> Unit) {
    val guide=LocalUsageGuide.current ?: return
    CompositionLocalProvider(LocalGuideWindow provides window) {
        Box(modifier) {
            content()
            guide.visibleDialogs.forEach { (owner,modal) -> key(owner) {
                CompositionLocalProvider(modal.locals.value) {
                    CompositionLocalProvider(LocalGuideWindow provides modal.window) {
                        BoxWithConstraints(Modifier.fillMaxSize().testTag("guide_modal_${modal.window}")) {
                            if(guide.stage==GuideStage.ABOUT && guide.entry>0) {
                                // Reserve the measured guide panel rather than pushing it
                                // into a tiny remainder under a centered 560dp sheet.
                                val reserve=with(LocalDensity.current) { guide.panelHeightPx.toDp() }+24.dp
                                val available=(maxHeight-reserve).coerceAtLeast(0.dp)
                                Box(Modifier.fillMaxWidth().height(available).padding(top=8.dp),
                                    contentAlignment=Alignment.BottomCenter) { modal.content.value() }
                            } else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { modal.content.value() }
                        }
                    }
                }
            } }
            // One layer stays mounted when pages/dialogs/chapters change; no
            // platform window animations or dim-layer hand-offs during setup.
            GuideLayer(guide,guide.presentedWindow)
            if(guide.menu) {
                BackHandler { guide.menu=false }
                Surface(Modifier.align(Alignment.Center).padding(24.dp).widthIn(max=480.dp).fillMaxWidth(),
                    shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("使用指引",style=MaterialTheme.typography.headlineSmall)
                        Text(directoryMessage,style=MaterialTheme.typography.bodySmall)
                        GuideChapters.forEachIndexed { chapter,label -> TextButton({
                            guide.select(GuideStage.entries.indexOfFirst { it.chapter==chapter })
                            guide.menu=false
                        },Modifier.fillMaxWidth().testTag("guide_chapter_$chapter")) { Text(label) } }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            TextButton({guide.onFinish(GuideOutcome.SKIPPED)},Modifier.testTag("guide_menu_skip")) { Text("跳过全部") }
                            TextButton({guide.menu=false},Modifier.testTag("guide_continue")) { Text("继续指引") }
                        }
                    }
                }
            }
        }
    }
}

/** Four independent blockers leave the hole genuinely interactive. A full-screen
 * pointer handler would win hit-testing and swallow the underlying gesture. */
internal fun guideBlockers(width: Float, height: Float, hole: Rect?): List<Rect> {
    return guideMultipleBlockers(width,height,listOfNotNull(hole))
}

/** Subtract the union of every opening, keeping blockers disjoint even when
 * openings overlap. Visual cutouts and real pointer access use the same bounds. */
internal fun guideMultipleBlockers(width:Float,height:Float,holes:List<Rect>):List<Rect> {
    var remaining=listOf(Rect(0f,0f,width,height))
    for(hole in holes.filter { it.width>0 && it.height>0 }) remaining=remaining.flatMap { rect ->
        val h=rect.intersect(hole)
        if(h.width<=0 || h.height<=0) listOf(rect)
        else listOf(Rect(rect.left,rect.top,rect.right,h.top),Rect(rect.left,h.bottom,rect.right,rect.bottom),
            Rect(rect.left,h.top,h.left,h.bottom),Rect(h.right,h.top,rect.right,h.bottom))
            .filter { it.width>0 && it.height>0 }
    }
    return remaining
}

@Composable private fun BoxScope.GuideLayer(guide: GuideRuntime, window: String) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin=it.positionInRoot() }) {
        val density=LocalDensity.current
        val width=constraints.maxWidth.toFloat();val height=constraints.maxHeight.toFloat()
        val visibleAnchors=if(guide.preparing) listOfNotNull(guide.retainedOpening)
            else guide.spotlightTargets.mapNotNull { guide.anchors[it] }
        val holes=visibleAnchors.mapNotNull { anchor ->
            anchor.takeIf { it.window==window }?.bounds?.translate(-origin)
                ?.inflate(with(density) { 4.dp.toPx() })?.intersect(Rect(0f,0f,width,height))
                ?.takeIf { it.width>0 && it.height>0 }
        }
        val hole=holes.firstOrNull()
        Canvas(Modifier.fillMaxSize().testTag("guide_scrim")) {
            var openings=Path()
            for(rect in holes) {
                val opening=Path().apply { addRoundRect(RoundRect(rect,CornerRadius(8.dp.toPx()))) }
                openings=Path.combine(PathOperation.Union,openings,opening)
            }
            val mask=Path.combine(PathOperation.Difference,
                Path().apply { addRect(Rect(0f,0f,size.width,size.height)) },openings)
            drawPath(mask,Color(0xFF303030).copy(alpha=.62f))
        }
        guideMultipleBlockers(width,height,if(guide.menu || guide.preparing) emptyList() else holes).forEachIndexed { index, rect ->
            Box(Modifier.offset { IntOffset(rect.left.roundToInt(),rect.top.roundToInt()) }
                .size(with(density){rect.width.toDp()},with(density){rect.height.toDp()})
                .testTag("guide_blocker_$index").pointerInput(rect) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed=false).consume()
                        do { val event=awaitPointerEvent();event.changes.forEach { it.consume() } }
                        while(event.changes.any { it.pressed })
                    }
                })
        }
        // Keep the scrim mounted under the directory, including chapter changes.
        // Only the instructional panel is hidden by the directory dialog.
        if(guide.menu) return@BoxWithConstraints
        val navigationTarget=guide.stage==GuideStage.NAV || guide.target.startsWith("tab_")
        val aboveButton=guide.stage in listOf(GuideStage.LOGOUT,GuideStage.CLEAR_CACHE)
        // Layout attachment is independent of the visual cutouts: preparation
        // closes the holes but must not send the panel back to the screen bottom.
        val placementHole=if(aboveButton) (guide.anchors[guide.target] ?: guide.retainedPanelAnchor)
            ?.bounds?.translate(-origin)?.inflate(with(density) { 4.dp.toPx() }) else hole
        val bottomPadding=if((navigationTarget || aboveButton) && placementHole!=null)
            with(density) { (height-placementHole.top).coerceAtLeast(0f).toDp() }+8.dp else 12.dp
        // Every lesson stays bottom-anchored, including in native dialogs. Fit
        // below the spotlight where possible; full-height targets must not make
        // the scrolling guide disappear or remove access to its controls.
        val panelMaximum=if(guide.stage==GuideStage.ABOUT && guide.entry>0) maxHeight*.46f
            else if(aboveButton && placementHole!=null)
            minOf(maxHeight*.46f,(with(density) { placementHole.top.toDp() }-24.dp).coerceAtLeast(128.dp))
            else if(!navigationTarget && holes.isNotEmpty())
            minOf(maxHeight*.46f,(with(density) { (height-holes.maxOf { it.bottom }).toDp() }-bottomPadding-8.dp).coerceAtLeast(128.dp))
            else maxHeight*.46f
        Surface(Modifier.align(Alignment.BottomCenter).padding(start=12.dp,top=12.dp,end=12.dp,bottom=bottomPadding).widthIn(max=480.dp).fillMaxWidth()
            .heightIn(max=panelMaximum).testTag("guide_panel")
            .onGloballyPositioned { guide.panelHeightPx=it.size.height },shape=MaterialTheme.shapes.small) {
            val panelScroll=key(guide.stage) { rememberScrollState() }
            Column(Modifier.padding(horizontal=16.dp,vertical=10.dp).verticalScroll(panelScroll),
                verticalArrangement=Arrangement.spacedBy(0.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("${GuideChapters[guide.stage.chapter]} · ${guide.position.intValue+1}/${GuideStage.entries.size}",
                        Modifier.weight(1f).testTag("guide_progress"),style=MaterialTheme.typography.labelSmall)
                    if(guide.stage.practice) TextButton({guide.demonstrate()},Modifier.testTag("guide_demonstrate"),enabled=!guide.preparing) {
                        if(guide.done) Icon(Icons.Filled.Check,contentDescription="已完成操作",modifier=Modifier.size(20.dp))
                        else Text("替我演示")
                    }
                    TextButton({guide.onFinish(GuideOutcome.SKIPPED)},Modifier.testTag("guide_skip")){Text("跳过")}
                }
                AnimatedContent(guide.stage,modifier=Modifier.fillMaxWidth(),label="guide_lesson_copy",
                    transitionSpec={
                        val direction=if(targetState.ordinal>initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(220)) { it*direction } togetherWith
                            slideOutHorizontally(tween(220)) { -it*direction })
                            .using(SizeTransform(clip=true))
                    }) { lesson ->
                    // Outgoing content is visual only; expose a single current
                    // lesson to accessibility and automation during the slide.
                    val current=lesson==guide.stage
                    fun Modifier.lessonTag(id:String)=if(current) testTag(id) else this
                    Column(Modifier.fillMaxWidth().then(if(current) Modifier else Modifier.clearAndSetSemantics { })) {
                        Text(lesson.title,Modifier.lessonTag("guide_title").semantics {
                            if(current) liveRegion=LiveRegionMode.Polite
                        },style=MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        val instructionStyle=MaterialTheme.typography.bodySmall
                        val instructionScroll=rememberScrollState()
                        Box(Modifier.fillMaxWidth().height(with(density) { instructionStyle.lineHeight.toDp()*3 })
                            .lessonTag("guide_instruction_area").verticalScroll(instructionScroll)) {
                            Text(lesson.instruction,Modifier.lessonTag("guide_instruction"),style=instructionStyle)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(48.dp).testTag("guide_navigation")) {
                    IconButton({guide.select(guide.position.intValue-1)},enabled=guide.position.intValue>0,
                        modifier=Modifier.align(Alignment.CenterStart).testTag("guide_previous")) {
                        GuideArrow(forward=false,description="上一步")
                    }
                    TextButton({guide.menu=true},Modifier.align(Alignment.Center).testTag("guide_directory")){Text("目录")}
                    IconButton({guide.next()},Modifier.align(Alignment.CenterEnd).testTag("guide_next")) {
                        if(guide.stage==GuideStage.FINISH) Icon(Icons.Filled.Close,"完成指引",Modifier.size(18.dp).testTag("guide_finish_icon"))
                        else GuideArrow(forward=true,description="下一步")
                    }
                }
            }
        }
    }
}

/** Long shaft, compact 4dp arrowhead; retain the surrounding 48dp touch target. */
@Composable private fun GuideArrow(forward:Boolean,description:String) {
    val color=LocalContentColor.current
    val pointsRight=forward==(LocalLayoutDirection.current==LayoutDirection.Ltr)
    Canvas(Modifier.size(24.dp).semantics { contentDescription=description }) {
        fun x(value:Float)=if(pointsRight) value.dp.toPx() else size.width-value.dp.toPx()
        val middle=size.height/2
        drawLine(color,Offset(x(3f),middle),Offset(x(21f),middle),2.dp.toPx(),StrokeCap.Round)
        drawPath(Path().apply {
            moveTo(x(17f),middle-3.dp.toPx())
            lineTo(x(21f),middle)
            lineTo(x(17f),middle+3.dp.toPx())
        },color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
    }
}
