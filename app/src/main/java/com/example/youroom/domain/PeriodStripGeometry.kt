package com.example.youroom.domain

import kotlin.math.floor

/** Equal-width bars; gaps belong to the nearest bar, and outside positions clamp to an end. */
internal object PeriodStripGeometry {
    fun bars(slots: List<LessonSlot>, width: Float, gap: Float, layout: PeriodStripLayout): List<PeriodBar> {
        if(!width.isFinite() || !gap.isFinite() || width<=0 || gap<0 || slots.isEmpty()) return emptyList()
        if(layout==PeriodStripLayout.EQUAL) {
            val bar=(width-gap*(slots.size-1))/slots.size
            if(bar<=0) return emptyList()
            return slots.indices.map { PeriodBar(it*(bar+gap),it*(bar+gap)+bar) }
        }
        return slots.map { slot ->
            val start=PlanningDisplay.xAt(slot.start.toSecondOfDay()/60f,slots,width,gap,layout) ?: return emptyList()
            val end=PlanningDisplay.xAt(slot.end.toSecondOfDay()/60f,slots,width,gap,layout) ?: return emptyList()
            PeriodBar(start,end)
        }
    }

    fun nearestIndex(x: Float, width: Float, bars: List<PeriodBar>, reversed: Boolean = false): Int? {
        if(!x.isFinite() || !width.isFinite() || width<=0 || bars.isEmpty()) return null
        val logical=if(reversed) width-x else x
        return bars.indices.firstOrNull { i -> i==bars.lastIndex || logical<(bars[i].end+bars[i+1].start)/2 }
    }

    fun nearestIndex(x: Float, width: Float, count: Int, gap: Float, reversed: Boolean = false): Int? {
        if (!x.isFinite() || !width.isFinite() || !gap.isFinite() || count <= 0 || gap < 0f) return null
        val bar = (width - gap * (count - 1)) / count
        if (bar <= 0f) return null
        val index = floor((x + gap / 2) / (bar + gap)).toInt().coerceIn(0, count - 1)
        return if (reversed) count - 1 - index else index
    }
}
