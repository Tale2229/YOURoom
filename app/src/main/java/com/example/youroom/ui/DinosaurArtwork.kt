/*
 * Copyright 2024 The Chromium Authors
 * Copyright 2025 The Chromium Authors
 * Use of this source code is governed by a BSD-style license that can be
 * found in assets/licenses/chromium_dino/LICENSE.txt.
 *
 * Adapted for YOURoom: pinned Chromium sprite coordinates and local theme
 * rendering. The upstream PNG is stored losslessly as base64 in res/raw.
 * Revision: 2e8c19f4f602c8756a42d6d5a5c5c37a69facfab
 */
package com.example.youroom.ui

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.youroom.R
import kotlin.math.roundToInt

/** Source offsets are in the original 2x sheet; dimensions are Chromium world pixels. */
internal data class DinoSprite(val x: Int, val y: Int, val width: Int, val height: Int)

internal object ChromiumDinoSprites {
    val standing = DinoSprite(1678, 2, 44, 47)
    val running = listOf(DinoSprite(1854, 2, 44, 47), DinoSprite(1942, 2, 44, 47))
    val crashed = DinoSprite(2118, 2, 44, 47)
    val cloud = DinoSprite(166, 2, 46, 14)
    val birds = listOf(DinoSprite(260, 2, 46, 40), DinoSprite(352, 2, 46, 40))
    val gameOver = DinoSprite(1294, 28, 191, 11)
    // A static arrow frame, not the unused Chrome-logo frames at the start of the strip.
    val restart = DinoSprite(146, 130, 36, 32)
    fun glyph(index: Int) = DinoSprite(1294 + index * 20, 2, 10, 13)
    fun horizon(bumpy: Boolean) = DinoSprite(if (bumpy) 1204 else 4, 104, 600, 12)
    fun cactus(large: Boolean, count: Int): DinoSprite {
        val width = if (large) 25 else 17
        return DinoSprite((if (large) 652 else 446) + width * count * (count - 1),
            2, width * count, if (large) 50 else 35)
    }
}

@Composable
internal fun rememberDinoSpriteSheet(): ImageBitmap {
    val resources = LocalContext.current.resources
    return remember(resources) { loadDinoSheet(resources) }
}

private fun loadDinoSheet(resources: Resources): ImageBitmap {
    val encoded = resources.openRawResource(R.raw.chromium_dino_sprite).bufferedReader(Charsets.US_ASCII).use { it.readText() }
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)).asImageBitmap()
}

/** Preserve white eyes / cutouts instead of flattening every opaque pixel with a tint. */
internal fun dinoSpriteColors(ink: Color, paper: Color): ColorFilter {
    val r = (paper.red - ink.red) / (1f - 83f / 255f)
    val g = (paper.green - ink.green) / (1f - 83f / 255f)
    val b = (paper.blue - ink.blue) / (1f - 83f / 255f)
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        r, 0f, 0f, 0f, ink.red * 255f - r * 83f,
        0f, g, 0f, 0f, ink.green * 255f - g * 83f,
        0f, 0f, b, 0f, ink.blue * 255f - b * 83f,
        0f, 0f, 0f, 1f, 0f,
    )))
}

internal fun DrawScope.drawDinoSprite(sheet: ImageBitmap, sprite: DinoSprite,
    x: Float, y: Float, scale: Float, colors: ColorFilter, alpha: Float = 1f) {
    drawImage(sheet, srcOffset = IntOffset(sprite.x, sprite.y),
        srcSize = IntSize(sprite.width * 2, sprite.height * 2),
        dstOffset = IntOffset((x * scale).roundToInt(), (y * scale).roundToInt()),
        dstSize = IntSize((sprite.width * scale).roundToInt().coerceAtLeast(1),
            (sprite.height * scale).roundToInt().coerceAtLeast(1)),
        colorFilter = colors, alpha = alpha, filterQuality = FilterQuality.None)
}

internal fun DrawScope.drawDinoText(sheet: ImageBitmap, text: String,
    x: Float, y: Float, scale: Float, colors: ColorFilter, alpha: Float = 1f) {
    text.forEachIndexed { position, char ->
        val index = when (char) { in '0'..'9' -> char - '0'; 'H' -> 10; 'I' -> 11; else -> -1 }
        if (index >= 0) drawDinoSprite(sheet, ChromiumDinoSprites.glyph(index),
            x + position * 11f, y, scale, colors, alpha)
    }
}
