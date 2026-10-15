package com.example.youroom.ui

import com.example.youroom.domain.LessonSlot
import com.example.youroom.domain.PlanningDisplay
import com.example.youroom.domain.StudyWindow
import kotlin.math.abs

/** Screen geometry only: no candidate selection or planning scores are changed here. */
internal data class PlanningRangeAnchor(
    val start: Float,
    val end: Float,
    val openStart: Boolean = false,
    val openEnd: Boolean = false,
    val outside: Boolean = false,
    val known: Boolean = true,
) {
    val center get() = (start + end) / 2
}

internal fun planningRangeAnchors(windows: List<StudyWindow>, slots: List<LessonSlot>,
    width: Float, gutter: Float, gap: Float): List<PlanningRangeAnchor> {
    val inner = width - 2 * gutter
    val first = slots.firstOrNull()?.start?.toSecondOfDay()?.div(60f)
    val last = slots.lastOrNull()?.end?.toSecondOfDay()?.div(60f)
    return windows.map { window ->
        val start = PlanningDisplay.xAt(window.start.toFloat(), slots, inner, gap)
        val end = PlanningDisplay.xAt(window.end.toFloat(), slots, inner, gap)
        if (start == null || end == null || first == null || last == null) {
            PlanningRangeAnchor(width / 2, width / 2, known = false)
        } else PlanningRangeAnchor(gutter + start, gutter + end,
            openStart = window.start < first, openEnd = window.end > last,
            outside = window.end <= first || window.start >= last)
    }
}

internal data class PlanningRangeLabel(val width: Int, val height: Int)
internal data class PlanningRangePlacement(val left: Float, val top: Float, val lane: Int,
    val size: PlanningRangeLabel) {
    val right get() = left + size.width
    val bottom get() = top + size.height
    val center get() = left + size.width / 2f
}
internal data class PlanningRangeArrangement(val labels: List<PlanningRangePlacement>, val labelBottom: Float)

/** Nearest row first; add height only for real text collisions, including large font sizes.
 * Logical left-to-right coordinates are mirrored only at placement/drawing time. */
internal fun arrangePlanningRanges(anchors: List<PlanningRangeAnchor>, sizes: List<PlanningRangeLabel>,
    width: Float, gutter: Float, spacing: Float, top: Float, rowGap: Float): PlanningRangeArrangement {
    require(anchors.size == sizes.size)
    if (anchors.isEmpty()) return PlanningRangeArrangement(emptyList(), top)
    val placed = mutableListOf<PlanningRangePlacement>()
    anchors.zip(sizes).forEach { (anchor, size) ->
        val minimum = gutter + size.width / 2f
        val maximum = (width - gutter - size.width / 2f).coerceAtLeast(minimum)
        val desired = anchor.center.coerceIn(minimum, maximum)
        var lane = 0
        var center = desired
        while (true) {
            val row = placed.filter { it.lane == lane }
            val lower = placed.filter { it.lane < lane }
            val candidates = if (lane == 0) listOf(desired) else {
                (listOf(desired) + lower.flatMap { listOf(it.right + spacing, it.left - spacing) })
                    .filter { it in minimum..maximum }.distinct().sortedBy { abs(it - desired) }
            }
            val found = candidates.firstOrNull { x ->
                row.none { x + size.width / 2f + spacing > it.left && x - size.width / 2f < it.right + spacing } &&
                    lower.none { x > it.left - spacing / 2 && x < it.right + spacing / 2 }
            }
            if (found != null) { center = found; break }
            // Dense cases may have no free vertical leader column. Use an extra row;
            // the text's paper backing masks any leader that passes underneath it.
            if (row.isEmpty() && lane > 0) break
            lane++
        }
        placed += PlanningRangePlacement(center - size.width / 2f, 0f, lane, size)
    }
    val rowHeights = (0..placed.maxOf { it.lane }).map { lane ->
        placed.filter { it.lane == lane }.maxOf { it.size.height }.toFloat()
    }
    val labelBottom = top + rowHeights.sum() + rowGap * (rowHeights.size - 1)
    return PlanningRangeArrangement(placed.map { label ->
        val below = (0 until label.lane).sumOf { (rowHeights[it] + rowGap).toDouble() }.toFloat()
        label.copy(top = labelBottom - below - label.size.height)
    }, labelBottom)
}
