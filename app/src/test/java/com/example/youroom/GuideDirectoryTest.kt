package com.example.youroom

import com.example.youroom.domain.Campus
import com.example.youroom.domain.CampusTone
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.campusColorScheme
import com.example.youroom.ui.theme.campusThemeRoles
import org.junit.Assert.*
import org.junit.Test

class GuideDirectoryTest {
    @Test fun routeUsesOneSmoothMonotonicCubicBetweenActualNodeCenters() {
        for(heights in listOf(listOf(100f,100f,100f,100f),listOf(100f,148f,116f,180f))) {
            val nodes=guideRouteNodes(heights,28.5f,54.5f)
            var top=0f
            nodes.forEachIndexed { index,node ->
                assertEquals(top+heights[index]/2,node.y,.001f)
                assertEquals(if(index%2==0) 28.5f else 54.5f,node.x,.001f)
                top+=heights[index]
            }
            val spans=guideRouteSpans(nodes)
            assertEquals(3,spans.size)
            spans.forEachIndexed { index,span ->
                assertEquals(nodes[index],span.start)
                assertEquals(nodes[index+1],span.end)
                assertEquals(span.start.x,span.control1.x,.001f)
                assertEquals(span.end.x,span.control2.x,.001f)
                assertTrue(span.start.y<span.control1.y)
                assertEquals(span.control1.y,span.control2.y,.001f)
                assertTrue(span.control2.y<span.end.y)
                // dx/dt never stops or reverses between circles, unlike the old
                // two half-row curves with an extra vertical tangent in the gap.
                for(sample in 1..99) {
                    val t=sample/100f
                    val dx=6*t*(1-t)*(span.end.x-span.start.x)
                    assertTrue(dx*(span.end.x-span.start.x)>0f)
                }
            }
        }
    }

    @Test fun chaptersPartitionAllFortyNineStepsWithoutGapsOrReordering() {
        assertEquals(49,GuideStage.entries.size)
        assertEquals(GuideStage.entries,GuideChapters.indices.flatMap(::guideChapterSteps))
        assertEquals(listOf("第 1—9 步","第 10—15 步","第 16—37 步","第 38—49 步"),
            GuideChapters.indices.map(::guideChapterRange))
        assertEquals(GuideChapters.size,GuideChapterDescriptions.size)
    }

    @Test fun allTwelveThemesHaveReadableCoverBodyAndCurrentChapterInBothModes() {
        for(tone in CampusTone.entries) for(dark in listOf(false,true)) {
            val c=campusColorScheme(Campus.XITUCHENG,dark,tone)
            val p=guideDirectoryPalette(c,campusThemeRoles(c,tone,dark),tone,dark)
            for((background,foreground) in listOf(p.cover to p.onCover,p.paper to p.ink,
                p.paper to p.muted,p.control to p.muted,p.ribbon to p.ink,p.accent to p.onAccent)) {
                assertTrue("${tone.name}/$dark unreadable directory pair: ${guideContrast(background,foreground)}",
                    guideContrast(background,foreground)>=4.5f)
            }
            assertEquals(c.surface,p.paper)
            assertEquals(c.surfaceContainerLow,p.ribbon)
        }
    }
}
