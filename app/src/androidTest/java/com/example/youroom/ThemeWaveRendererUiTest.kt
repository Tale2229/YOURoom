package com.example.youroom

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.ThemeWaveMesh
import com.example.youroom.ui.ThemeWaveFrames
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemeWaveRendererUiTest {
    @get:Rule val compose = createComposeRule()
    private var phase by mutableFloatStateOf(0f)

    @Test fun aPatternInsideTheFormerPreviewMaskMovesWithTheRestOfTheSurface() {
        compose.setContent {
            val density = LocalDensity.current
            BoxWithConstraints(Modifier.fillMaxWidth().height(389.dp)) {
                val w = with(density) { maxWidth.roundToPx() }; val h = with(density) { 389.dp.roundToPx() }
                val mesh = remember(w, h) { ThemeWaveMesh(w.toFloat(), h.toFloat(), with(density) { 60.dp.toPx() }) }
                val frames = remember(w, h) {
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    android.graphics.Canvas(bitmap).drawRect(0f, with(density) { 151.dp.toPx() }, w.toFloat(),
                        with(density) { 153.dp.toPx() }, android.graphics.Paint().apply { color = android.graphics.Color.BLACK })
                    ThemeWaveFrames(bitmap.asImageBitmap(), bitmap.asImageBitmap())
                }
                Canvas(Modifier.fillMaxSize().testTag("mesh_pattern")) { frames.draw(this, mesh, phase, 4.dp.toPx()) }
            }
        }
        val before = compose.onNodeWithTag("mesh_pattern").captureToImage().toPixelMap()
        compose.runOnIdle { phase=.4f }
        val during = compose.onNodeWithTag("mesh_pattern").captureToImage().toPixelMap()
        // These columns run through the 90dp preview's lower half on a 360dp screen.
        for (fraction in listOf(.42f, .5f, .58f)) {
            val x = (before.width*fraction).toInt()
            val oldY = (0 until before.height).first { before[x,it].red < .2f }
            val newY = (0 until during.height).first { during[x,it].red < .2f }
            assertTrue("preview-area content is no longer pinned at $x", newY < oldY-2)
            assertTrue("small lift only", oldY-newY <= with(compose.density) { 4.dp.toPx() }+2)
        }
    }

    @Test fun nativeMeshCoversEveryPixelWithoutSeamsIncludingEdgesInBothColourDirections() {
        val before = Color(0xFF173528); val after = Color(0xFFE4C8A5)
        var reverse by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            BoxWithConstraints(Modifier.fillMaxWidth().height(389.dp)) {
                val w = with(density) { maxWidth.roundToPx() }; val h = with(density) { 389.dp.roundToPx() }
                val mesh = remember(w, h) { ThemeWaveMesh(w.toFloat(), h.toFloat(), with(density) { 60.dp.toPx() }) }
                fun solid(color: Color) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(color.toArgb()) }.asImageBitmap()
                val frames = remember(w, h, reverse) {
                    ThemeWaveFrames(solid(if(reverse) after else before), solid(if(reverse) before else after))
                }
                Canvas(Modifier.fillMaxSize().testTag("mesh_surface")) {
                    // Any geometric crack or transparent edge exposes this diagnostic colour.
                    drawRect(Color.Magenta)
                    frames.draw(this, mesh, phase, 4.dp.toPx())
                }
            }
        }
        for (back in listOf(false, true)) for (p in listOf(0f, .09f, .2f, .37f, .5f, .63f, .8f, 1f)) {
            compose.runOnIdle { reverse = back; phase = p }
            val pixels = compose.onNodeWithTag("mesh_surface").captureToImage().toPixelMap()
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val c = pixels[x,y]
                if (c.alpha <= .99f || c.red !in (before.red-.009f)..(after.red+.009f) ||
                    c.green !in (before.green-.009f)..(after.green+.009f) || c.blue !in (before.blue-.009f)..(after.blue+.009f))
                    fail("Uncovered pixel at $x,$y phase $p: $c")
                if (p == 0f || p == 1f) {
                    val expected = if ((p == 0f) != back) before else after
                    if (kotlin.math.abs(expected.red-c.red) > .009f || kotlin.math.abs(expected.green-c.green) > .009f ||
                        kotlin.math.abs(expected.blue-c.blue) > .009f) fail("Wrong endpoint at $x,$y: $c != $expected")
                }
            }
        }
    }
}
