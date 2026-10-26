package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.Campus
import com.example.youroom.ui.CampusHeader
import com.example.youroom.ui.YOURoomState
import com.example.youroom.ui.theme.YOURoomTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CampusHeaderUiTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(YOURoomState(initializing = false, today = LocalDate.of(2026, 10, 13)))
    private var scale by mutableFloatStateOf(1f)
    private var switches = 0
    private fun render() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                YOURoomTheme(campus = state.campus) {
                    Box(Modifier.widthIn(max = 320.dp)) {
                        CampusHeader(state) { state = state.copy(campus = it); switches++ }
                    }
                }
            }
        }
    }

    @Test fun campusAndDateAreRightAlignedOppositeTheBrandAtNormalAndLargeFontSizes() {
        render()
        for (font in listOf(1f, 1.6f)) for (campus in Campus.entries) {
            compose.runOnIdle { scale = font; state = state.copy(campus = campus) }
            compose.onNodeWithTag("campus_label").assertTextEquals(campus.label)
            compose.onNodeWithTag("campus_date").assertTextEquals("10月13日 Tue.")
            val brand = compose.onNodeWithTag("campus_brand").fetchSemanticsNode().boundsInRoot
            val context = compose.onNodeWithTag("campus_context").fetchSemanticsNode().boundsInRoot
            val label = compose.onNodeWithTag("campus_label").fetchSemanticsNode().boundsInRoot
            val date = compose.onNodeWithTag("campus_date").fetchSemanticsNode().boundsInRoot
            val header = compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
            assertTrue(brand.right < context.left)
            assertEquals(date.right, label.right, 1f)
            assertTrue(label.bottom < date.top)
            assertTrue(date.right <= header.right)
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("campus_label").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertEquals(12.sp, layout.single().layoutInput.style.fontSize)
        }
    }

    @Test fun dateTracksTodayInsteadOfStaleQueryDateAndChangesAcrossMidnight() {
        render()
        compose.runOnIdle {
            state = state.copy(snapshot = DemoData.snapshot().copy(queryDate = LocalDate.of(2026, 10, 12)))
        }
        compose.onNodeWithTag("campus_date").assertTextEquals("10月13日 Tue.")
        compose.runOnIdle { state = state.copy(today = state.today.plusDays(1)) }
        compose.onNodeWithTag("campus_date").assertTextEquals("10月14日 Wed.")
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("campus_date").assertTextEquals("10月14日 Wed.")
    }

    @Test fun refreshControlAndItsLoadingPlaceholderAreGoneInEverySessionState() {
        render()
        for (demo in listOf(false, true)) for (cache in listOf(false, true)) for (refreshing in listOf(false, true)) {
            compose.runOnIdle { state = state.copy(demo = demo, viewingCache = cache, refreshing = refreshing) }
            compose.onNodeWithTag("refresh_rooms").assertDoesNotExist()
            compose.onNodeWithContentDescription("刷新教室").assertDoesNotExist()
            compose.onNodeWithContentDescription("登录并刷新").assertDoesNotExist()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
            compose.onNodeWithTag("campus_date").assertIsDisplayed()
        }
    }

    @Test fun swipingAcrossTheRightSideWorksAndOutwardOrVerticalSwipesStillDoNothing() {
        render()
        val region = compose.onNodeWithTag("campus_swipe_region")
        region.performTouchInput { swipeRight() }
        region.performTouchInput { swipeDown() }
        assertEquals(0, switches)
        region.performTouchInput { swipe(start = androidx.compose.ui.geometry.Offset(width * .94f, height / 2f),
            end = androidx.compose.ui.geometry.Offset(width * .35f, height / 2f), durationMillis = 240) }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        assertEquals(1, switches)
        region.performTouchInput { swipeLeft() }
        assertEquals(1, switches)
        region.performTouchInput { swipeRight() }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        assertEquals(2, switches)
    }
}
