package com.example.youroom.domain

import kotlin.math.floor

/** Equal-width bars; gaps belong to the nearest bar, and outside positions clamp to an end. */
internal object PeriodStripGeometry {
    fun nearestIndex(x: Float, width: Float, count: Int, gap: Float, reversed: Boolean = false): Int? {
        if (!x.isFinite() || !width.isFinite() || !gap.isFinite() || count <= 0 || gap < 0f) return null
        val bar = (width - gap * (count - 1)) / count
        if (bar <= 0f) return null
        val index = floor((x + gap / 2) / (bar + gap)).toInt().coerceIn(0, count - 1)
        return if (reversed) count - 1 - index else index
    }
}
