package com.example.youroom.domain

import kotlin.math.abs

/** All distances/velocity are in dp, measured on the stationary row, not its sliding face. */
object RoomSwipePolicy {
    fun target(startOffsetDp: Float, offsetDp: Float, widthDp: Float, velocityDpPerSecond: Float): Boolean {
        if (!widthDp.isFinite() || widthDp <= 0 || !offsetDp.isFinite()) return false
        val offset = offsetDp.coerceIn(-widthDp, 0f)
        // A small release-time reversal must never undo an already visible/hidden rail.
        val edge = minOf(12f, widthDp * .08f)
        if (offset <= -widthDp + edge) return true
        if (offset >= -edge) return false
        val distance = offset - startOffsetDp.coerceIn(-widthDp, 0f)
        if (startOffsetDp.isFinite() && velocityDpPerSecond.isFinite() &&
            abs(distance) >= 24f && abs(velocityDpPerSecond) >= 800f && distance * velocityDpPerSecond > 0f) {
            return distance < 0f
        }
        return offset < -widthDp / 2
    }
}
