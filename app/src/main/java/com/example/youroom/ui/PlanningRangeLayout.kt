package com.example.youroom.ui

import com.example.youroom.domain.LessonSlot
import com.example.youroom.domain.PlanningDisplay
import com.example.youroom.domain.StudyWindow
import com.example.youroom.domain.PeriodStripLayout
import kotlin.math.abs
import kotlin.math.roundToInt

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
    width: Float, gutter: Float, gap: Float, layout: PeriodStripLayout = PeriodStripLayout.EQUAL): List<PlanningRangeAnchor> {
    val inner = width - 2 * gutter
    val first = slots.firstOrNull()?.start?.toSecondOfDay()?.div(60f)
    val last = slots.lastOrNull()?.end?.toSecondOfDay()?.div(60f)
    return windows.map { window ->
        val start = PlanningDisplay.xAt(window.start.toFloat(), slots, inner, gap,layout)
        val end = PlanningDisplay.xAt(window.end.toFloat(), slots, inner, gap,layout)
        if (start == null || end == null || first == null || last == null) {
            PlanningRangeAnchor(width / 2, width / 2, known = false)
        } else PlanningRangeAnchor(gutter + start, gutter + end,
            openStart = window.start < first, openEnd = window.end > last,
            outside = window.end <= first || window.start >= last)
    }
}

internal data class PlanningRangeLabel(val width: Int, val height: Int)
internal data class PlanningRangePlacement(val left: Float, val top: Float, val lane: Int,
    val size: PlanningRangeLabel, val routeTrack: Int = 0) {
    val right get() = left + size.width
    val bottom get() = top + size.height
    val center get() = left + size.width / 2f
}
internal data class PlanningRangeArrangement(val labels: List<PlanningRangePlacement>, val labelBottom: Float,
    val leaders: List<PlanningRangeLeader> = emptyList(), val annotationHeight: Int = 0)

internal data class RangeLeaderPoint(val x: Float, val y: Float)

/** Center-to-center, with a vertical stem at both ends. Normal layouts need
 * only V-H-V (or one vertical line). Extremely crowded legacy histories may
 * need a side column, but never start/end sideways or cut through text. */
internal data class PlanningRangeLeader(val startX: Float, val endX: Float, val escapeX: Float? = null,
    val escapeStep: Float = 0f) {
    val straight get() = escapeX == null && startX==endX
    val verticalX get() = escapeX ?: startX

    fun points(textY: Float, corridorY: Float, bracketY: Float): List<RangeLeaderPoint> {
        val path = when {
            straight -> listOf(RangeLeaderPoint(startX,textY),RangeLeaderPoint(endX,bracketY))
            escapeX != null -> listOf(RangeLeaderPoint(startX,textY),RangeLeaderPoint(startX,textY+escapeStep),
                RangeLeaderPoint(escapeX,textY+escapeStep),RangeLeaderPoint(escapeX,corridorY),
                RangeLeaderPoint(endX,corridorY),RangeLeaderPoint(endX,bracketY))
            else -> listOf(RangeLeaderPoint(startX,textY),RangeLeaderPoint(startX,corridorY),
                RangeLeaderPoint(endX,corridorY),RangeLeaderPoint(endX,bracketY))
        }
        val compact=mutableListOf<RangeLeaderPoint>()
        path.forEach { point ->
            if (compact.lastOrNull()!=point) compact+=point
            if (compact.size>=3) {
                val a=compact[compact.size-3]; val b=compact[compact.size-2]; val c=compact.last()
                if ((a.x==b.x && b.x==c.x) || (a.y==b.y && b.y==c.y)) compact.removeAt(compact.size-2)
            }
        }
        return compact
    }
}

internal fun routePlanningRange(anchor: PlanningRangeAnchor, label: PlanningRangePlacement,
    labels: List<PlanningRangePlacement>, width: Float, gutter: Float, clearance: Float): PlanningRangeLeader {
    val nearer=labels.filter { it.lane<label.lane }
    // Placement already reserves a clear vertical column at the label center.
    if (nearer.none { label.center>it.left-clearance+.001f && label.center<it.right+clearance-.001f })
        return PlanningRangeLeader(label.center,anchor.center)
    fun freeColumn(low: Float, high: Float, desired: Float): Float? {
        if (low>high) return null
        val candidates=listOf(desired.coerceIn(low,high),low,high)+nearer.flatMap {
            listOf(it.left-clearance,it.right+clearance)
        }
        return candidates.filter { x -> x in low..high &&
            nearer.none { x>it.left-clearance+.001f && x<it.right+clearance-.001f } }
            .minByOrNull { abs(it-desired) }
    }
    // Only used when all centered columns cannot fit (very large fonts / long
    // room histories). Prefer a column between the endpoints, avoiding reversal.
    val column=freeColumn(minOf(label.center,anchor.center),maxOf(label.center,anchor.center),label.center)
        ?: freeColumn(gutter,width-gutter,label.center)
        ?: freeColumn(1f,width-1f,label.center) ?: label.center
    return PlanningRangeLeader(label.center,anchor.center,column,clearance/2)
}

private data class RangeLayoutChoice(val lanes: List<Int>, val centers: FloatArray, val score: Double)
private data class RangeSeparation(val from: Int, val to: Int, val distance: Float)

/** Allocate elbow heights jointly, rather than simply by time order. In canonical
 * (labels-above-bars) coordinates, a horizontal leg crossing another text-side
 * column must pass closer to the bars; crossing a bar-side column must pass closer
 * to the labels. These precedence constraints mirror unchanged below the bars/RTL.
 * Only heights change: label centers, bracket centers and the two-bend shape stay put. */
private fun planningRangeTracks(leaders: List<PlanningRangeLeader>, order: List<Int>): Map<Int,Int> {
    val bent=order.filter { !leaders[it].straight }
    val before=Array(leaders.size) { mutableSetOf<Int>() }
    bent.forEach { i ->
        val wire=leaders[i]
        fun inside(x: Float)=x>minOf(wire.verticalX,wire.endX)+.001f &&
            x<maxOf(wire.verticalX,wire.endX)-.001f
        bent.filter { it!=i }.forEach { j ->
            if(inside(leaders[j].verticalX)) before[i]+=j
            if(inside(leaders[j].endX)) before[j]+=i
        }
    }
    val remaining=bent.toMutableList()
    val assigned=linkedMapOf<Int,Int>()
    while(remaining.isNotEmpty()) {
        // Stable topological order. In extreme fallback layouts some constraints
        // may conflict; keep this bounded and choose the fewest remaining conflicts.
        val next=remaining.minBy { i -> before[i].count { it in remaining } }
        assigned[next]=assigned.size
        remaining.remove(next)
    }
    return assigned
}

/** Jointly place labels, with bounded displacement and chronological center order.
 * Search every row assignment for the at-most-three target periods. Longer room histories
 * use a small beam, not an unbounded combinatorial search on the UI thread.
 * Reserve center columns while fitting labels: moving the text to the long leg
 * is preferred to a wire that doubles back. Chronology and screen bounds stay hard. */
internal fun arrangePlanningRanges(anchors: List<PlanningRangeAnchor>, sizes: List<PlanningRangeLabel>,
    width: Float, gutter: Float, spacing: Float, top: Float, rowGap: Float,
    chronologicalOrder: List<Int> = anchors.indices.sortedWith(compareBy({ anchors[it].start },{ anchors[it].end },{ it }))): PlanningRangeArrangement {
    require(anchors.size == sizes.size)
    if (anchors.isEmpty()) return PlanningRangeArrangement(emptyList(), top)
    require(chronologicalOrder.sorted()==anchors.indices.toList())
    val orderedSizes=chronologicalOrder.map { sizes[it] }
    val desired=chronologicalOrder.mapIndexed { i,index ->
        val minimum=gutter+orderedSizes[i].width/2f
        anchors[index].center.coerceIn(minimum,(width-minimum).coerceAtLeast(minimum))
    }
    fun evaluate(lanes: List<Int>, centeredColumns: Boolean = true): RangeLayoutChoice? {
        val values=desired.take(lanes.size);val labels=orderedSizes.take(lanes.size)
        val centers=(if(centeredColumns) fitRangeCenters(lanes,values,labels,width,gutter,spacing,
            bounded=true,centeredColumns=true,pinNearest=true) else null)
            ?: fitRangeCenters(lanes,values,labels,width,gutter,spacing,bounded=true,centeredColumns=centeredColumns)
            ?: fitRangeCenters(lanes,values,labels,width,gutter,spacing,bounded=false,centeredColumns=centeredColumns)
            ?: return null
        val movement=centers.indices.sumOf { i ->
            val relative=(centers[i]-desired[i])/maxOf(1f,orderedSizes[i].width/2f)
            (relative*relative).toDouble()
        }
        val chronologicalLaneBias=lanes.indices.sumOf { i -> lanes[i]*(lanes.size-i)*.002 }
        val score=(lanes.maxOrNull() ?: 0)+lanes.sum()*.04+chronologicalLaneBias+movement*1.2
        return RangeLayoutChoice(lanes,centers,score)
    }
    fun choose(centeredColumns: Boolean): RangeLayoutChoice? = if(anchors.size<=3) {
        val candidates=mutableListOf<RangeLayoutChoice>()
        fun visit(lanes: List<Int>) {
            if(lanes.size==anchors.size) {
                if((0..lanes.max()).all { it in lanes }) evaluate(lanes,centeredColumns)?.let(candidates::add)
            } else repeat(anchors.size) { visit(lanes+it) }
        }
        visit(emptyList())
        candidates.minByOrNull { it.score }
    } else {
        var beam=listOf(RangeLayoutChoice(emptyList(),floatArrayOf(),0.0))
        repeat(anchors.size) {
            beam=beam.flatMap { previous ->
                (0..((previous.lanes.maxOrNull() ?: -1)+1)).mapNotNull { evaluate(previous.lanes+it,centeredColumns) }
            }.sortedBy { it.score }.take(8)
        }
        beam.firstOrNull()
    }
    val choice=choose(centeredColumns=true) ?: choose(centeredColumns=false)
        ?: checkNotNull(evaluate(anchors.indices.toList(),centeredColumns=false))
    val placed=MutableList(anchors.size) { i -> PlanningRangePlacement(0f,0f,0,sizes[i]) }
    chronologicalOrder.forEachIndexed { i,index ->
        placed[index]=PlanningRangePlacement(choice.centers[i]-sizes[index].width/2f,0f,choice.lanes[i],sizes[index])
    }
    val rowHeights = (0..placed.maxOf { it.lane }).map { lane ->
        placed.filter { it.lane == lane }.maxOf { it.size.height }.toFloat()
    }
    val labelBottom = top + rowHeights.sum() + rowGap * (rowHeights.size - 1)
    val positioned=placed.map { label ->
        val below = (0 until label.lane).sumOf { (rowHeights[it] + rowGap).toDouble() }.toFloat()
        label.copy(top=labelBottom-below-label.size.height)
    }
    val leaders=positioned.mapIndexed { index,label -> routePlanningRange(anchors[index],label,positioned,width,gutter,spacing/2) }
    val reservedTracks=(leaders.count { !it.straight }-1).coerceAtLeast(0)
    val annotationHeight=(labelBottom+spacing*1.5f+reservedTracks*spacing*3/8).roundToInt()
    val tracks=planningRangeTracks(leaders,chronologicalOrder)
    return PlanningRangeArrangement(positioned.mapIndexed { index,label -> label.copy(routeTrack=tracks[index] ?: 0) },
        labelBottom,leaders,annotationHeight)
}

/** Euclidean projection onto bounds + ordered separation half-spaces (Dykstra).
 * Every row can shift left or right. A final forward pass removes subpixel residuals. */
private fun fitRangeCenters(lanes: List<Int>, desired: List<Float>, sizes: List<PlanningRangeLabel>,
    width: Float, gutter: Float, spacing: Float, bounded: Boolean,
    centeredColumns: Boolean = false, pinNearest: Boolean = false): FloatArray? {
    val count=lanes.size
    val low=FloatArray(count) { i ->
        val edge=gutter+sizes[i].width/2f
        if(pinNearest && lanes[i]==0) desired[i] else if(bounded) maxOf(edge,desired[i]-sizes[i].width/2f) else edge
    }
    val high=FloatArray(count) { i ->
        val edge=(width-gutter-sizes[i].width/2f).coerceAtLeast(gutter+sizes[i].width/2f)
        if(pinNearest && lanes[i]==0) desired[i] else if(bounded) minOf(edge,desired[i]+sizes[i].width/2f) else edge
    }
    val edges=mutableListOf<RangeSeparation>()
    for(i in 1 until count) edges+=RangeSeparation(i-1,i,0f)
    val previous=mutableMapOf<Int,Int>()
    lanes.forEachIndexed { i,lane ->
        previous.put(lane,i)?.let { before ->
            edges+=RangeSeparation(before,i,(sizes[before].width+sizes[i].width)/2f+spacing)
        }
    }
    if(centeredColumns) for(i in 0 until count) for(j in i+1 until count) {
        if(lanes[i]!=lanes[j]) {
            val nearer=if(lanes[i]<lanes[j]) i else j
            edges+=RangeSeparation(i,j,sizes[nearer].width/2f+spacing/2)
        }
    }
    for(i in 0 until count) {
        edges.filter { it.to==i }.forEach { low[i]=maxOf(low[i],low[it.from]+it.distance) }
        if(low[i]>high[i]+.001f) return null
    }
    for(i in count-1 downTo 0) {
        edges.filter { it.from==i }.forEach { high[i]=minOf(high[i],high[it.to]-it.distance) }
        if(low[i]>high[i]+.001f) return null
    }
    val x=desired.toFloatArray()
    val boundsCorrection=FloatArray(count)
    val edgeCorrection=FloatArray(edges.size)
    repeat(96) {
        for(i in 0 until count) {
            val y=x[i]+boundsCorrection[i]
            x[i]=y.coerceIn(low[i],maxOf(low[i],high[i])); boundsCorrection[i]=y-x[i]
        }
        edges.forEachIndexed { e,edge ->
            val a=x[edge.from]+edgeCorrection[e]; val b=x[edge.to]-edgeCorrection[e]
            val move=maxOf(0f,(edge.distance-b+a)/2)
            x[edge.from]=a-move; x[edge.to]=b+move; edgeCorrection[e]=move
        }
    }
    for(i in 0 until count) {
        x[i]=x[i].coerceIn(low[i],maxOf(low[i],high[i]))
        edges.filter { it.to==i }.forEach { x[i]=maxOf(x[i],x[it.from]+it.distance) }
    }
    return x
}
