package com.example.youroom

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.domain.*
import com.example.youroom.ui.ThemeImpressionPicker
import com.example.youroom.ui.themeAvatarResource
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class ThemeAvatarEdgeUiTest {
    @get:Rule val compose = createComposeRule()
    private var scale by mutableFloatStateOf(2.75f)
    private var tone by mutableStateOf(CampusTone.DUNHUANG)
    private var dark by mutableStateOf(false)

    private fun render() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(scale)) {
                YOURoomTheme(campus = Campus.XITUCHENG,
                    appearance = AppearancePreferences(darkMode = dark).withTone(Campus.XITUCHENG, tone)) {
                    Box(Modifier.width(321.dp).background(if (dark) Color.Black else Color.White)) {
                        ThemeImpressionPicker(Campus.XITUCHENG, tone, true) { _, next -> tone = next }
                    }
                }
            }
        }
    }

    @Test fun avatarEdgesMeetTheFrameWithoutPageColoredSeamsAtFractionalDensities() {
        render()
        val instrument = InstrumentationRegistry.getInstrumentation()
        for (density in listOf(2f, 2.625f, 2.75f, 3f, 3.5f)) {
            compose.runOnIdle { scale = density }
            compose.waitForIdle()
            File(instrument.targetContext.getExternalFilesDir(null), "avatar-seam-$density.png")
                .outputStream().use { instrument.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
            assertEdgesMatchArtwork()
        }
    }

    @Test fun allTwelveThemesKeepCleanEdgesOnLightAndDarkPages() {
        render()
        for (night in listOf(false,true)) for (density in listOf(2.625f,2.75f)) for (next in CampusTone.entries) {
            compose.runOnIdle { scale = density; dark = night; tone = next }
            compose.waitForIdle()
            assertEdgesMatchArtwork()
        }
    }

    private fun assertEdgesMatchArtwork() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val actual = compose.onNodeWithTag("settings_theme_frame").captureToImage().toPixelMap()
        val edge = (75*scale).roundToInt()
        val left = ((actual.width-edge)/2f).roundToInt()
        val top = ((actual.height-edge)/2f).roundToInt()
        val source = BitmapFactory.decodeResource(instrument.targetContext.resources, themeAvatarResource(tone))
        val expected = Bitmap.createScaledBitmap(source, edge, edge, true)
        for (fraction in listOf(.25f,.5f,.75f)) {
            val y = (fraction*(edge-1)).roundToInt()
            for (x in listOf(0,edge-1)) {
                val pixel = actual[left+x,top+y]
                val rgb = expected.getPixel(x,y)
                val error = (abs(pixel.red-android.graphics.Color.red(rgb)/255f) +
                    abs(pixel.green-android.graphics.Color.green(rgb)/255f) +
                    abs(pixel.blue-android.graphics.Color.blue(rgb)/255f))/3
                assertTrue("${tone.id} dark=$dark scale=$scale edge $x,$y must match artwork, error=$error",error<.07f)
            }
        }
        expected.recycle(); source.recycle()
    }
}
