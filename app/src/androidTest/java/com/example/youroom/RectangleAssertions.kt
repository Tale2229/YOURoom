package com.example.youroom

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import org.junit.Assert.assertEquals

/** The numeric face must fill its corners, not reveal the surface behind a radius. */
internal fun assertSquareCorners(node: SemanticsNodeInteraction) {
    val pixels = node.captureToImage().toPixelMap()
    val background = pixels[pixels.width / 2, 1]
    for (x in listOf(1, pixels.width - 2)) {
        for (y in listOf(1, pixels.height - 2)) {
            assertEquals("Numeric rectangle corner ($x, $y)", background, pixels[x, y])
        }
    }
}
