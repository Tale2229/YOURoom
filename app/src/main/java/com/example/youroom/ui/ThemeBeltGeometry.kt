package com.example.youroom.ui

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** Virtual repeats keep the carousel cheap and independent of the palette count. */
internal object ThemeBeltGeometry {
    const val cycles = 2001
    const val strideDp = 96f
    const val minScale = .62f
    const val holdMillis = 800L
    const val revealMillis = 160
    fun initial(value: Int, count: Int) = cycles / 2 * count + value
    fun value(position: Float, count: Int) = Math.floorMod(position.roundToInt(), count)
    fun nearest(value: Int, around: Int, count: Int): Int =
        ((around / count - 1)..(around / count + 1)).map { it * count + value }
            .filter { it in 0 until count * cycles }.minBy { abs(it - around) }
    fun scale(distance: Float) = minScale + (1f-minScale) * exp(-.8f * distance * distance)
    fun alpha(distance: Float) = exp(-.7f * distance * distance)
}
