package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.Campus
import com.example.youroom.ui.CompactFilterChip
import com.example.youroom.ui.theme.YouRoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompactFilterChipUiTest {
    @get:Rule val compose = createComposeRule()
    private val labels = listOf("已收藏", "有插座", "桌椅舒适", "人少", "＋", "窗", "安静靠窗区域", "教学楼 2")
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var night by mutableStateOf(false)
    private var scale by mutableFloatStateOf(1f)
    private var selected by mutableStateOf(false)
    private var clicks = 0
    private var deletions = 0
    @OptIn(ExperimentalLayoutApi::class)
    private fun render(deletable: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                YouRoomTheme(campus = campus, darkTheme = night) {
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        labels.forEach { label -> CompactFilterChip(label, selected,
                            { selected = !selected; clicks++ }, Modifier.testTag("chip_$label"),
                            onDelete = if (deletable) ({ deletions++ }) else null) }
                    }
                }
            }
        }
    }

    @Test fun shortLabelsAndPlusHaveFortyDpFacesWithNoHiddenHorizontalInsetInBothThemes() {
        render()
        for (target in Campus.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { campus = target; night = dark }
            labels.forEach { label ->
                val touch = compose.onNodeWithTag("chip_$label").assertHeightIsAtLeast(40.dp)
                    .fetchSemanticsNode().boundsInRoot
                val face = compose.onNodeWithTag("filter_chip_face_$label", useUnmergedTree = true)
                    .assertWidthIsAtLeast(40.dp).assertHeightIsEqualTo(28.dp).fetchSemanticsNode().boundsInRoot
                assertEquals(touch.left, face.left, 1f); assertEquals(touch.right, face.right, 1f)
                assertEquals(touch.center.y, face.center.y, 1f)
                assertTrue(face.width >= face.height * 1.4f)
            }
            for (label in listOf("＋", "窗")) compose.onNodeWithTag("filter_chip_face_$label", useUnmergedTree = true)
                .assertWidthIsEqualTo(40.dp)
        }
    }

    @Test fun pressingTagsAndPlusHasNoRippleButReleaseStillUpdatesSelectionInBothThemes() {
        render()
        for (target in Campus.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { campus = target; night = dark; selected = false }
            for (label in listOf("有插座", "教学楼 2", "＋")) {
                val touch = compose.onNodeWithTag("chip_$label")
                val face = compose.onNodeWithTag("filter_chip_face_$label", useUnmergedTree = true)
                for (wasSelected in listOf(false, true)) {
                    compose.runOnIdle { selected = wasSelected }
                    val before = face.captureToImage().toPixelMap()
                    touch.performTouchInput { down(center) }
                    try {
                        compose.mainClock.advanceTimeBy(240)
                        val held = face.captureToImage().toPixelMap()
                        assertEquals(before.width, held.width); assertEquals(before.height, held.height)
                        for (x in 0 until before.width) for (y in 0 until before.height) {
                            assertEquals("$target/$dark/$label/$wasSelected: pressed pixel $x,$y", before[x, y], held[x, y])
                        }
                    } finally { touch.performTouchInput { up() } }
                    if (wasSelected) touch.assertIsNotSelected() else touch.assertIsSelected()
                    val released = face.captureToImage().toPixelMap()
                    val palette = campusColorScheme(target, dark)
                    val expected = if (wasSelected) palette.surface else palette.primaryContainer
                    val actual = released[released.width / 2, 4]
                    assertEquals(expected.red, actual.red, .01f)
                    assertEquals(expected.green, actual.green, .01f)
                    assertEquals(expected.blue, actual.blue, .01f)
                }
            }
        }
    }

    @Test fun wrappedRowsAndFontScalingKeepFacesAlignedWithTheirTouchBoxes() {
        render()
        for (font in listOf(1f, 1.6f)) {
            compose.runOnIdle { scale = font }
            val boxes = labels.map { compose.onNodeWithTag("chip_$it").fetchSemanticsNode().boundsInRoot }
            labels.forEachIndexed { index, label ->
                val face = compose.onNodeWithTag("filter_chip_face_$label", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertEquals(boxes[index].left, face.left, 1f); assertEquals(boxes[index].right, face.right, 1f)
            }
            val rowStarts = boxes.groupBy { it.top }.values.map { row -> row.minOf { it.left } }
            rowStarts.forEach { assertEquals(rowStarts.first(), it, 1f) }
        }
    }

    @Test fun widerPlusAndDeleteButtonsRetainTheirSeparateActions() {
        render(deletable = true)
        compose.onNodeWithTag("chip_＋").performTouchInput { click() }
        compose.onNodeWithTag("chip_＋").assertIsSelected()
        assertEquals(1, clicks)
        compose.onNodeWithContentDescription("删除词条 窗").performTouchInput { click() }
        assertEquals(1, deletions); assertEquals(1, clicks)
    }

    @Test fun deleteCrossCutsOnlyTheOverlappingBorderInBothCampusPalettes() {
        render(deletable=true)
        for (target in Campus.entries) for (dark in listOf(false,true)) {
            compose.runOnIdle { campus=target;night=dark;selected=true }
            val pixels=compose.onNodeWithTag("filter_chip_face_有插座",true).captureToImage().toPixelMap()
            val density=compose.density.density
            val gap=pixels[(pixels.width-11*density).toInt(),0]
            val intact=pixels[pixels.width/2,0]
            val palette=campusColorScheme(target,dark)
            assertEquals(palette.primaryContainer.red,gap.red,.02f)
            assertEquals(palette.primaryContainer.green,gap.green,.02f)
            assertEquals(palette.primaryContainer.blue,gap.blue,.02f)
            assertEquals(palette.primary.red,intact.red,.02f)
            assertEquals(palette.primary.green,intact.green,.02f)
            assertEquals(palette.primary.blue,intact.blue,.02f)
        }
    }
}
