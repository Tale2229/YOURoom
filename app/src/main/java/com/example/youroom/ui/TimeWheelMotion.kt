package com.example.youroom.ui

import androidx.compose.animation.core.FloatDecayAnimationSpec
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.round

internal const val TimeWheelFriction = .62f

/** Retain Android's velocity curve but reduce deceleration, not the initial release velocity.
 * x(t) = x0 + (nativeX(f*t) - x0)/f; v(t) = nativeV(f*t).
 * Duration and travel become 1/f of native while the final snap remains a separate phase.
 */
internal class TimeWheelDecay(private val native: FloatDecayAnimationSpec,
    private val friction: Float = TimeWheelFriction) : FloatDecayAnimationSpec {
    init { require(friction > 0f && friction <= 1f) }
    override val absVelocityThreshold get() = native.absVelocityThreshold
    override fun getValueFromNanos(playTimeNanos: Long, initialValue: Float, initialVelocity: Float): Float =
        initialValue + (native.getValueFromNanos((playTimeNanos * friction.toDouble()).toLong(),
            initialValue, initialVelocity) - initialValue) / friction
    override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: Float, initialVelocity: Float): Float =
        native.getVelocityFromNanos((playTimeNanos * friction.toDouble()).toLong(), initialValue, initialVelocity)
    override fun getDurationNanos(initialValue: Float, initialVelocity: Float): Long =
        (native.getDurationNanos(initialValue, initialVelocity) / friction.toDouble()).toLong()
    override fun getTargetValue(initialValue: Float, initialVelocity: Float): Float =
        initialValue + (native.getTargetValue(initialValue, initialVelocity) - initialValue) / friction
}

// Count actual center passages, not nearest-selection changes at half a row. Counting the
// intervening integers also keeps fast flings and 23 -> 00 / 59 -> 00 from losing tick events.
internal fun timeWheelCenterCrossings(from: Float, to: Float): Int {
    // Odd pixel viewport heights / padding can leave a centered row half a pixel short.
    // Normalize only the immediate center neighborhood, never the half-row selection boundary.
    fun normalized(value: Float) = round(value).takeIf { abs(it - value) <= .01f } ?: value
    val start = normalized(from)
    val end = normalized(to)
    return when {
        end > start -> (floor(end) - floor(start)).toInt()
        end < start -> (ceil(start) - ceil(end)).toInt()
        else -> 0
    }
}
