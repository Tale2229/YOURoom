package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.Campus
import com.example.youroom.ui.CompactFilterChip
import com.example.youroom.ui.theme.YouRoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompactFilterChipUiTest {
    @get:Rule val compose = createComposeRule()
    private val labels = listOf("已收藏", "有插座", "桌椅舒适", "人少", "＋", "窗", "安静靠窗区域")
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

    @Test fun shortLabelsAndPlusHaveWideFacesWithNoHiddenHorizontalInsetInBothThemes() {
        render()
        for (target in Campus.entries) for (dark in listOf(false, true)) {
            compose.runOnIdle { campus = target; night = dark }
            labels.forEach { label ->
                val touch = compose.onNodeWithTag("chip_$label").assertHeightIsAtLeast(40.dp)
                    .fetchSemanticsNode().boundsInRoot
                val face = compose.onNodeWithTag("filter_chip_face_$label", useUnmergedTree = true)
                    .assertWidthIsAtLeast(56.dp).assertHeightIsEqualTo(28.dp).fetchSemanticsNode().boundsInRoot
                assertEquals(touch.left, face.left, 1f); assertEquals(touch.right, face.right, 1f)
                assertEquals(touch.center.y, face.center.y, 1f)
                assertTrue(face.width >= face.height * 1.9f)
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
}
