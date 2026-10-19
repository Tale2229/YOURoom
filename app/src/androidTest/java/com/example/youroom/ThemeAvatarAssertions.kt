package com.example.youroom

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.domain.CampusTone
import com.example.youroom.ui.themeAvatarResource
import org.junit.Assert.assertTrue
import kotlin.math.abs

/** Check the approved artwork itself, not exact flat colors from the retired SVG painter. */
internal fun assertThemeAvatar(node: SemanticsNodeInteraction, tone: CampusTone) {
    val actual = node.captureToImage().toPixelMap()
    val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    val source = BitmapFactory.decodeResource(resources, themeAvatarResource(tone))
    val expected = Bitmap.createScaledBitmap(source, actual.width, actual.height, true)
    var error = 0.0
    var samples = 0
    // Stay clear of the existing rounded corners; compare all major composition regions.
    for (row in 1..15) for (column in 1..15) {
        val x = column * (actual.width - 1) / 16
        val y = row * (actual.height - 1) / 16
        val c = actual[x, y]
        val argb = expected.getPixel(x, y)
        error += abs(c.red - android.graphics.Color.red(argb) / 255f)
        error += abs(c.green - android.graphics.Color.green(argb) / 255f)
        error += abs(c.blue - android.graphics.Color.blue(argb) / 255f)
        samples += 3
    }
    if (expected !== source) expected.recycle()
    source.recycle()
    assertTrue("${tone.id}: avatar must match approved image (mean error ${error / samples})",
        error / samples < .035)
}
