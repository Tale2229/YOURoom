package com.example.youroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.youroom.domain.Campus
import com.example.youroom.domain.CampusTone
import com.example.youroom.ui.theme.LocalCampusAppearance
import com.example.youroom.ui.theme.ThemeRoles
import com.example.youroom.ui.theme.campusBadgeColor
import com.example.youroom.ui.theme.themeRoles

internal val GuideChapterDescriptions = listOf(
    "校区、节次、搜索与筛选", "收藏、标签、备注与排除",
    "设置时段、等待内容与历史回放", "配色、偏好与本地数据",
)

internal fun guideChapterSteps(chapter: Int) = GuideStage.entries.filter { it.chapter == chapter }
internal fun guideChapterRange(chapter: Int): String {
    val steps = guideChapterSteps(chapter)
    return "第 ${steps.first().ordinal + 1}—${steps.last().ordinal + 1} 步"
}

internal fun guideRouteNodes(heights: List<Float>, left: Float, right: Float): List<Offset> {
    var top=0f
    return heights.mapIndexed { index,height ->
        Offset(if(index%2==0) left else right,top+height/2).also {top+=height}
    }
}

internal data class GuideRouteSpan(val start:Offset,val control1:Offset,val control2:Offset,val end:Offset)

/** One cubic per pair of nodes: no extra midpoint stops or S-shaped half-row seams. */
internal fun guideRouteSpans(nodes:List<Offset>) = nodes.zipWithNext { start,end ->
    val half=(end.y-start.y)/2
    GuideRouteSpan(start,Offset(start.x,start.y+half),Offset(end.x,end.y-half),end)
}

internal fun guideContrast(a: Color, b: Color): Float =
    (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)

internal fun guideReadable(background: Color, first: Color, second: Color): Color =
    listOf(first, second, Color.White, Color(0xFF171A18)).maxBy { guideContrast(background, it) }

internal data class GuideDirectoryPalette(
    val cover: Color, val onCover: Color, val paper: Color, val ink: Color,
    val muted: Color, val ribbon: Color, val control: Color, val rule: Color,
    val outline: Color, val accent: Color, val onAccent: Color,
)

/** The same native roles used by the approved twelve-theme HTML study. */
internal fun guideDirectoryPalette(c: ColorScheme, roles: ThemeRoles, tone: CampusTone, dark: Boolean): GuideDirectoryPalette {
    val cover = if (dark) lerp(roles.header, c.surfaceContainerLowest, .2f) else campusBadgeColor(Campus.XITUCHENG, tone)
    return GuideDirectoryPalette(cover, guideReadable(cover, c.surface, c.onSurface), c.surface, c.onSurface,
        if (listOf(c.surface, c.surfaceContainer).all { guideContrast(it, c.onSurfaceVariant) >= 4.5f }) c.onSurfaceVariant else c.onSurface,
        c.surfaceContainerLow, c.surfaceContainer, c.outlineVariant, c.outline, roles.navigationSelected,
        guideReadable(roles.navigationSelected, roles.onNavigationSelected, c.onSurface))
}

/** One in-window surface: inspecting a chapter never prepares a new lesson.
 * Only the final entry action changes the practice workspace under the scrim. */
@Composable internal fun GuideDirectory(currentStep: Int, campus: Campus,
    onClose: () -> Unit, onSkip: () -> Unit, onEnter: (Int) -> Unit) {
    var chapter by rememberSaveable { mutableIntStateOf(-1) }
    var chosenStep by rememberSaveable { mutableIntStateOf(currentStep) }
    val appearance = LocalCampusAppearance.current
    val p = guideDirectoryPalette(MaterialTheme.colorScheme, themeRoles(), appearance.tone(campus),
        appearance.isDark(isSystemInDarkTheme()))
    BackHandler { if (chapter >= 0) chapter = -1 else onClose() }
    Box(Modifier.fillMaxSize().padding(horizontal=12.dp, vertical=16.dp), contentAlignment=Alignment.Center) {
        Surface(Modifier.widthIn(max=480.dp).fillMaxWidth().fillMaxHeight().testTag("guide_directory_surface"),
            shape=RoundedCornerShape(20.dp), color=p.paper, contentColor=p.ink) {
            if (chapter < 0) {
                Column {
                    // Short screens and large system fonts scroll the cover/list,
                    // while the continue action remains reachable.
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val density=LocalDensity.current
                        var coverHeight by remember {mutableStateOf(202.dp)}
                        val rowHeight=maxOf(94.dp,(maxHeight-coverHeight)/GuideChapters.size)
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Box(Modifier.onSizeChanged {coverHeight=with(density) {it.height.toDp()}}) {
                                DirectoryCover(p, onSkip)
                            }
                            ChapterRoutes(GuideStage.entries[currentStep].chapter,p,rowHeight) { index ->
                                chosenStep = if (GuideStage.entries[currentStep].chapter == index) currentStep
                                    else guideChapterSteps(index).first().ordinal
                                chapter = index
                            }
                        }
                    }
                    DirectoryFooter(p, currentStep, onClose)
                }
            } else {
                Column(Modifier.testTag("guide_chapter_directory")) {
                    Box(Modifier.fillMaxWidth().background(p.control)) {
                        Column(Modifier.padding(start=24.dp, top=24.dp, end=56.dp, bottom=20.dp)) {
                            Text("CHAPTER 0${chapter + 1} / 04", color=p.muted, fontSize=10.sp, letterSpacing=1.sp)
                            Text(GuideChapters[chapter], Modifier.padding(top=14.dp, bottom=8.dp).semantics { heading() },
                                fontSize=28.sp, lineHeight=36.sp, fontWeight=FontWeight.Medium)
                            Text(guideChapterRange(chapter), color=p.muted, fontSize=12.sp)
                        }
                        IconButton({chapter=-1}, Modifier.align(Alignment.TopEnd).padding(4.dp).testTag("guide_chapter_back")) {
                            GuideArrow(forward=false,description="返回目录",color=p.ink)
                        }
                    }
                    val steps=guideChapterSteps(chapter)
                    LazyColumn(Modifier.weight(1f).testTag("guide_step_list"),
                        contentPadding=PaddingValues(horizontal=20.dp, vertical=8.dp)) {
                        items(steps, key={it.name}) { step ->
                            val isSelected=chosenStep==step.ordinal
                            Row(Modifier.fillMaxWidth().background(if(isSelected) p.ribbon else Color.Transparent)
                                .clickable {chosenStep=step.ordinal}.semantics {selected=isSelected}
                                .testTag("guide_step_${step.ordinal}").heightIn(min=52.dp).padding(horizontal=8.dp,vertical=10.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Text("%02d".format(step.ordinal+1), Modifier.width(34.dp), color=p.muted,
                                    fontSize=17.sp,fontFamily=FontFamily.Serif,fontStyle=FontStyle.Italic)
                                Text(step.title, Modifier.weight(1f),fontSize=14.sp,lineHeight=21.sp)
                                if(isSelected) Icon(Icons.Filled.Check,"已选步骤",Modifier.padding(start=8.dp).size(18.dp))
                            }
                            HorizontalDivider(color=p.rule)
                        }
                    }
                    Column(Modifier.background(p.control).padding(horizontal=24.dp,vertical=14.dp)) {
                        DirectoryAction("进入第${chosenStep+1}步", "guide_enter_step", p.ink) {onEnter(chosenStep)}
                    }
                }
            }
        }
    }
}

@Composable private fun DirectoryCover(p: GuideDirectoryPalette, onSkip: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min=202.dp).clip(RoundedCornerShape(0.dp)).background(p.cover)) {
        Text(GuideStage.entries.size.toString(),Modifier.align(Alignment.BottomEnd).offset(x=(-12).dp,y=30.dp)
            .clearAndSetSemantics {}, color=p.onCover.copy(alpha=.13f),fontSize=132.sp,lineHeight=132.sp,
            fontFamily=FontFamily.Serif,fontStyle=FontStyle.Italic)
        Column(Modifier.padding(24.dp)) {
            Text("YOURoom / CONTENTS",Modifier.padding(end=32.dp).testTag("guide_directory_eyebrow"),
                color=p.onCover,fontSize=11.sp,letterSpacing=1.sp,fontWeight=FontWeight.Medium)
            Text("使用\n指引",Modifier.padding(top=20.dp).semantics {heading()},color=p.onCover,
                fontSize=43.sp,lineHeight=49.sp,fontWeight=FontWeight.Medium)
        }
        IconButton(onSkip,Modifier.align(Alignment.TopEnd).padding(4.dp).testTag("guide_menu_close")) {
            Icon(Icons.Filled.Close,"跳过指引",Modifier.size(18.dp),tint=p.onCover)
        }
    }
}

@Composable private fun ChapterRoutes(currentChapter:Int,p:GuideDirectoryPalette,minHeight:Dp,onChapter:(Int)->Unit) {
    val heights=remember {mutableStateMapOf<Int,Int>()}
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            drawRect(p.ribbon,size=Size(83.dp.toPx(),size.height))
            if(GuideChapters.indices.all { (heights[it] ?: 0)>0 }) {
                val nodes=guideRouteNodes(GuideChapters.indices.map {heights.getValue(it).toFloat()},28.5.dp.toPx(),54.5.dp.toPx())
                val path=Path().apply {
                    moveTo(nodes.first().x,0f)
                    lineTo(nodes.first().x,nodes.first().y)
                    guideRouteSpans(nodes).forEach {span ->
                        cubicTo(span.control1.x,span.control1.y,span.control2.x,span.control2.y,span.end.x,span.end.y)
                    }
                    lineTo(nodes.last().x,size.height)
                }
                drawPath(path,p.outline,style=Stroke(1.dp.toPx()))
            }
            drawLine(p.cover,Offset(24.dp.toPx(),1.5.dp.toPx()),Offset(58.dp.toPx(),1.5.dp.toPx()),3.dp.toPx())
        }
        Column {
            GuideChapters.forEachIndexed { index,title ->
                ChapterRoute(index,title,index==currentChapter,p,minHeight,
                    Modifier.onSizeChanged {heights[index]=it.height}) {onChapter(index)}
            }
        }
    }
}

@Composable private fun ChapterRoute(index: Int, title: String, current: Boolean, p: GuideDirectoryPalette,
    minHeight: Dp,modifier:Modifier,onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().clickable(onClick=onClick).testTag("guide_chapter_$index")) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            if(index<GuideChapters.lastIndex) drawLine(p.rule,Offset(107.dp.toPx(),size.height),Offset(size.width-24.dp.toPx(),size.height),1.dp.toPx())
        }
        Row(Modifier.heightIn(min=minHeight).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.width(83.dp),contentAlignment=Alignment.Center) {
                Box(Modifier.offset(x=if(index%2==0) (-13).dp else 13.dp).size(36.dp)
                    .background(if(current) p.accent else p.ribbon,CircleShape)
                    .border(1.dp,if(current) p.onAccent else p.outline,CircleShape),contentAlignment=Alignment.Center) {
                    Text("0${index+1}",color=if(current) p.onAccent else p.ink,fontFamily=FontFamily.Serif,
                        fontStyle=FontStyle.Italic,fontSize=20.sp)
                }
            }
            Column(Modifier.weight(1f).padding(start=12.dp,top=16.dp,bottom=16.dp)) {
                Text(title,fontWeight=FontWeight.SemiBold,fontSize=18.sp,lineHeight=24.sp)
                Text(GuideChapterDescriptions[index],Modifier.padding(top=5.dp),color=p.muted,fontSize=11.sp,lineHeight=17.sp)
                Text(guideChapterRange(index)+(if(current) " · 当前章节" else ""),Modifier.padding(top=3.dp),
                    color=p.muted,fontSize=10.sp,lineHeight=16.sp)
            }
            GuideArrow(forward=true,description=null,modifier=Modifier.padding(start=8.dp,end=22.dp),color=p.ink)
        }
    }
}

@Composable private fun DirectoryFooter(p: GuideDirectoryPalette, currentStep: Int, onContinue: () -> Unit) {
    HorizontalDivider(color=p.rule)
    Column(Modifier.background(p.paper).padding(horizontal=24.dp,vertical=10.dp)) {
        DirectoryAction(if(currentStep==0) "开始使用指引" else "继续第${currentStep+1}步", "guide_continue",p.ink,onContinue)
    }
}

@Composable private fun DirectoryAction(label: String, tag: String, ink: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).testTag(tag).heightIn(min=48.dp).padding(vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),color=ink,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        GuideArrow(forward=true,description=null,color=ink)
    }
}
