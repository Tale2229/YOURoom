package com.example.youroom.ui

import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.youroom.domain.ThemeWaveMesh

/** Two immutable, transition-only frames. No per-frame captures, per-cell clipping,
 * independently transformed rectangles, or seams between separate draw calls. */
internal class ThemeWaveFrames(val before: ImageBitmap, val after: ImageBitmap) {
    private val old = before.asAndroidBitmap()
    private val new = after.asAndroidBitmap()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun draw(scope: DrawScope, mesh: ThemeWaveMesh, progress: Float, lift: Float) = with(scope) {
        mesh.update(progress, lift)
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.drawBitmapMesh(old, mesh.columns, mesh.rows, mesh.vertices, 0, null, 0, paint)
            canvas.drawBitmapMesh(new, mesh.columns, mesh.rows, mesh.vertices, 0, mesh.colors, 0, paint)
        }
    }
}
