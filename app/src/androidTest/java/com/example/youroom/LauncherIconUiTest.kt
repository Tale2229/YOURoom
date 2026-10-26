package com.example.youroom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LauncherIconUiTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(drawable: Drawable, size: Int = 432): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun originalBlackGrayPathsRemainInTheAdaptiveSafeZoneWithoutClipping() {
        val foreground = context.getDrawable(R.drawable.youroom_foreground)!!
        val bitmap = render(foreground)
        val counts = mutableMapOf<Int, Int>()
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 8) {
                val dx = (x + .5f) / 4 - 54
                val dy = (y + .5f) / 4 - 54
                assertTrue("Artwork must fit the 66dp safe circle", dx * dx + dy * dy < 33 * 33)
            }
            if (Color.alpha(pixel) == 255) counts[pixel] = (counts[pixel] ?: 0) + 1
        }
        for (color in listOf(Color.BLACK, Color.rgb(102,102,102), Color.rgb(1,58,137),
                Color.rgb(80,45,22), Color.rgb(221,193,39))) {
            assertTrue("Original SVG fill is preserved: $color", (counts[color] ?: 0) > 20)
        }
        assertEquals(Color.TRANSPARENT,bitmap.getPixel(0,0))
        val background = render(context.getDrawable(R.drawable.youroom_background)!!)
        for (xy in listOf(0 to 0, 216 to 216, 431 to 431)) assertEquals(Color.TRANSPARENT,background.getPixel(xy.first,xy.second))
        assertTrue("Only a tiny original white highlight, no white plate", (counts[Color.WHITE] ?: 0) < 30)
        assertTrue("Original pale cyan glass remains translucent", (0 until 432).any { y ->
            (0 until 432).any { x ->
                val pixel = bitmap.getPixel(x,y)
                Color.alpha(pixel) in 213..216 && Color.green(pixel)>250 && Color.blue(pixel)>250
            }
        })
        assertEquals("Top-face negative space", Color.TRANSPARENT, bitmap.getPixel(216,168))
        save(bitmap,"launcher-g66-foreground.png")
    }

    @Test fun installedNormalAndRoundIconsBothResolveToTransparentArtwork() {
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        val normal = render(context.getDrawable(R.mipmap.ic_launcher)!!,256)
        val round = render(context.getDrawable(R.mipmap.ic_launcher_round)!!,256)
        assertTrue(normal.sameAs(round))
        assertTrue(normal.sameAs(render(icon,256)))
        assertEquals(Color.TRANSPARENT,normal.getPixel(128,8))
        assertEquals(Color.TRANSPARENT,normal.getPixel(124,85))
        save(normal,"launcher-g66-transparent.png")
        save(render(icon,48),"launcher-g66-48px.png")
    }

    @Test fun transparentPaddingAndTopFaceRevealUnderlyingColor() {
        for (color in listOf(Color.rgb(74,114,149), Color.rgb(221,199,159))) {
            val icon = context.getDrawable(R.mipmap.ic_launcher)!!
            val bitmap = Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(color)
            icon.setBounds(0,0,256,256)
            icon.draw(canvas)
            assertEquals(color,bitmap.getPixel(128,8))
            assertEquals(color,bitmap.getPixel(124,85))
            save(bitmap,"launcher-g66-on-${Integer.toHexString(color)}.png")
        }
    }

    @Test fun themedIconUsesTheSameTransparentSilhouette() {
        // Retain the optional mask for future themed-icon variants; the installed
        // entry is deliberately non-adaptive so Android cannot fill its holes.
        val foreground = render(context.getDrawable(R.drawable.youroom_foreground)!!)
        val mono = render(context.getDrawable(R.drawable.youroom_monochrome)!!)
        var transparentInterior = 0
        var solidInterior = 0
        // Native vectors and Inkscape rasterization use different edge coverage.
        // Compare interiors at least three pixels away from any alpha edge.
        val interiorOffsets = (-3..3).flatMap { dx -> (-3..3).map { dy -> dx to dy } }
        for (y in 3 until 429) for (x in 3 until 429) {
            val pixel=mono.getPixel(x,y)
            if (Color.alpha(pixel)>0) {
                assertEquals(0,Color.red(pixel));assertEquals(0,Color.green(pixel));assertEquals(0,Color.blue(pixel))
            }
            if (interiorOffsets.all {
                    Color.alpha(foreground.getPixel(x+it.first,y+it.second))==0 }) {
                transparentInterior++
                assertTrue("Transparent SVG space must remain holes in themed icons",Color.alpha(pixel)<8)
            }
            if (interiorOffsets.all {
                    Color.alpha(foreground.getPixel(x+it.first,y+it.second))==255 }) {
                solidInterior++
                assertTrue("Painted interior remains solid at $x,$y: ${Color.alpha(pixel)}",Color.alpha(pixel)>247)
            }
        }
        assertTrue(transparentInterior>100)
        assertTrue(solidInterior>100)
        save(mono,"launcher-g66-monochrome.png")
    }
}
