package com.example.youroom.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/** A shared detent locks the whole strip, never an individual tile.
 * Use RAW drag coordinates for hysteresis: a pinned visual coordinate cannot escape itself.
 */
internal object ThemeBeltMagnet {
    // Physical distances stay fixed when the center-to-center stride changes.
    const val captureDp = 23.04f
    const val escapeDp = 36.48f
    const val captureRadius = captureDp / ThemeBeltGeometry.strideDp
    const val escapeRadius = escapeDp / ThemeBeltGeometry.strideDp

    /** One common position for the ENTIRE strip, never a per-tile displacement. */
    fun position(raw: Float, captured: Int?): Float = captured?.toFloat() ?: raw

    fun distance(index: Int, raw: Float, captured: Int?): Float = index - position(raw, captured)

    // A short flick within a detent must not escape later through release velocity.
    fun releaseVelocity(velocity: Float, captured: Int?): Float = if (captured == null) velocity else 0f

    fun captured(position: Float, previous: Int?): Int? {
        if (previous != null && abs(position - previous) <= escapeRadius) return previous
        val nearest = position.roundToInt()
        return nearest.takeIf { abs(position - it) <= captureRadius }
    }
}
