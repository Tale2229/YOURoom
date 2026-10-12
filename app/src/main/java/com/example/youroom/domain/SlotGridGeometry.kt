package com.example.youroom.domain

import kotlin.math.max
import kotlin.math.min

data class SlotBounds(val number: Int, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = right > left && bottom > top && x >= left && x < right && y >= top && y < bottom

    /** Clip a pointer segment against actual cell bounds, including fast skipped frames. */
    fun intersection(x0: Float, y0: Float, x1: Float, y1: Float): Float? {
        if (right <= left || bottom <= top) return null
        var enter = 0f
        var leave = 1f
        fun clip(origin: Float, delta: Float, low: Float, high: Float): Boolean {
            if (delta == 0f) return origin in low..high
            val a = (low - origin) / delta
            val b = (high - origin) / delta
            enter = max(enter, min(a, b))
            leave = min(leave, max(a, b))
            return enter <= leave
        }
        return if (clip(x0, x1 - x0, left, right) && clip(y0, y1 - y0, top, bottom)) enter else null
    }
}

object SlotGridGeometry {
    fun hit(bounds: Collection<SlotBounds>, x: Float, y: Float): Int? = bounds.firstOrNull { it.contains(x, y) }?.number
    fun crossed(bounds: Collection<SlotBounds>, x0: Float, y0: Float, x1: Float, y1: Float): List<Int> =
        bounds.mapNotNull { cell -> cell.intersection(x0, y0, x1, y1)?.let { cell.number to it } }
            .sortedBy { it.second }.map { it.first }
}
