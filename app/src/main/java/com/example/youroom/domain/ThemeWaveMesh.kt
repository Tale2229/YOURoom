package com.example.youroom.domain

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One shared vertex array for the entire surface: neighbouring cells cannot pull
 * their edges apart. Buffers are reused, including during repeated theme changes. */
internal class ThemeWaveMesh(val width: Float, val height: Float, desiredCell: Float) {
    init { require(width.isFinite() && height.isFinite() && desiredCell.isFinite() && width > 0 && height > 0 && desiredCell > 0) }
    val columns = (width / desiredCell).roundToInt().coerceIn(4, 10)
    val rows = ceil(height / (width / columns)).toInt().coerceIn(1, 64)
    val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    val colors = IntArray((columns + 1) * (rows + 1))

    fun update(progress: Float, lift: Float) {
        val amplitude = min(lift.coerceAtLeast(0f), height / rows * .2f)
        for (row in 0..rows) for (column in 0..columns) {
            val index = row * (columns + 1) + column
            val phase = ThemePageWave.phase(progress, row, column, rows + 1, columns + 1)
            val displacement = if (row == 0 || row == rows || progress <= 0f || progress >= 1f) 0f
                else -amplitude * sin(phase * PI).toFloat()
            vertices[index * 2] = column * width / columns
            vertices[index * 2 + 1] = row * height / rows + displacement
            // Canvas multiplies these white vertex colours with the new bitmap.
            // Old mesh remains opaque underneath; only the new mesh fades in.
            val alpha = (ThemePageWave.blend(phase) * 255).roundToInt().coerceIn(0, 255)
            colors[index] = (alpha shl 24) or 0x00FFFFFF
        }
    }
}
