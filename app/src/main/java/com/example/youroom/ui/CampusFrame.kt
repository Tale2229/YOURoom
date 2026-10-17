package com.example.youroom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.youroom.domain.Campus
import com.example.youroom.ui.theme.YouRoomTheme
import kotlinx.coroutines.flow.first

internal class CampusHeaderMotion(val pager: PagerState, private val desired: State<Int>) {
    val moving get() = pager.isScrollInProgress || pager.settledPage != desired.value
}

@Composable
internal fun rememberCampusHeaderMotion(campus: Campus): CampusHeaderMotion {
    val pager = rememberPagerState(initialPage = campus.ordinal) { Campus.entries.size }
    val desired = rememberUpdatedState(campus.ordinal)
    LaunchedEffect(campus) {
        if (pager.currentPage != campus.ordinal || pager.currentPageOffsetFraction != 0f)
            pager.animateScrollToPage(campus.ordinal, animationSpec = tween(320))
    }
    return remember(pager) { CampusHeaderMotion(pager, desired) }
}

@Composable
internal fun CampusHeaderPager(state: YouRoomState, motion: CampusHeaderMotion, onCampus: (Campus) -> Unit) {
    HorizontalPager(state = motion.pager, beyondViewportPageCount = 1, userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth().wrapContentHeight().clipToBounds().testTag("campus_pager"),
        key = { Campus.entries[it].dataKey }) { index ->
        val displayed = Campus.entries[index]
        val visibleSemantics = if (motion.pager.currentPage == index) Modifier else Modifier.clearAndSetSemantics {}
        YouRoomTheme(campus = displayed, appearance = state.appearance) {
            Surface(Modifier.fillMaxWidth().then(visibleSemantics)) {
                CampusHeader(state.copy(campus = displayed), onCampus)
            }
        }
    }
}

/** Shared title-led, stationary fade replacement. Room lists stay warm independently;
 * planner/settings reuse a single body so switching campus never duplicates time wheels.
 */
@Composable
internal fun CampusFrame(state: YouRoomState, onCampus: (Campus) -> Unit, showHeader: Boolean = true,
    sharedHeaderMoving: Boolean = false, interactive: Boolean = true, keepBothCampuses: Boolean = false,
    bodyTag: String, content: @Composable (YouRoomState, Boolean, Boolean, Boolean) -> Unit) {
    val localHeader = if (showHeader) rememberCampusHeaderMotion(state.campus) else null
    val headerMoving = localHeader?.moving ?: sharedHeaderMoving
    val latestHeaderMoving by rememberUpdatedState(headerMoving)
    val pages = remember(state.demo) { mutableMapOf<Campus, YouRoomState>() }
    pages[state.campus] = state
    var bodyCampus by remember { mutableStateOf(state.campus) }
    val alpha = remember { Animatable(1f) }
    var replacing by remember { mutableStateOf(false) }
    val moving = headerMoving || replacing || bodyCampus != state.campus
    LaunchedEffect(state.campus) {
        val target = state.campus
        if (bodyCampus == target && !headerMoving && alpha.value == 1f) return@LaunchedEffect
        replacing = true
        try {
            if (bodyCampus != target) {
                alpha.animateTo(0f, tween(durationMillis = 160, delayMillis = 160))
                bodyCampus = target
            }
            snapshotFlow { !latestHeaderMoving }.first { it }
            alpha.animateTo(1f, tween(160))
        } finally { replacing = false }
    }
    Column(Modifier.fillMaxSize()) {
        if (localHeader != null) {
            CampusHeaderPager(state, localHeader, onCampus)
            HorizontalDivider(Modifier.padding(horizontal = 24.dp).testTag("campus_divider"))
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag(bodyTag)) {
            val campuses = if (keepBothCampuses) Campus.entries else listOf(bodyCampus)
            campuses.forEach { displayed ->
                // No campus key for a single-body screen: its time controls remain mounted.
                key(if (keepBothCampuses) displayed.dataKey else "single", state.demo) {
                    val observed = if (displayed == state.campus) state else pages[displayed] ?: state.copy(
                        campus = displayed, snapshot = state.campusSnapshots[displayed],
                        filters = state.filters.copy(buildings = emptySet()), fromCache = !state.demo,
                        refreshing = false, error = null, selectedRoomId = null)
                    pages.putIfAbsent(displayed, observed)
                    val visible = displayed == bodyCampus
                    val ready = visible && displayed == state.campus && !headerMoving
                    var held by remember(displayed) { mutableStateOf(observed) }
                    val page = if (ready) observed else held
                    SideEffect { if (ready) held = observed }
                    val active = ready && !moving && interactive
                    YouRoomTheme(campus = displayed, appearance = state.appearance) {
                        Surface(Modifier.fillMaxSize().testTag(if (bodyTag == "campus_body")
                            "campus_content_${displayed.dataKey}" else "${bodyTag}_content_${displayed.dataKey}")
                            .zIndex(if (visible) 1f else 0f)
                            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
                            .pointerInput(active) {
                                if (!active) awaitPointerEventScope {
                                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }.graphicsLayer { this.alpha = if (visible) alpha.value else 0f }) {
                            content(page, active, moving || !interactive, ready)
                        }
                    }
                }
            }
        }
    }
}
