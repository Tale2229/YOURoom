package com.example.youroom

import com.example.youroom.ui.ThemeBeltMagnet as Magnet
import com.example.youroom.ui.ThemeBeltGeometry as Belt
import org.junit.Assert.*
import org.junit.Test

class ThemeBeltMagnetTest {
    private fun dp(value: Float) = value / Belt.strideDp

    @Test fun approvedSpacingKeepsTheOriginalPhysicalCaptureAndEscapeDistances() {
        assertEquals(120f,Belt.strideDp,0f)
        assertEquals(23.04f,Magnet.captureRadius*Belt.strideDp,.0001f)
        assertEquals(36.48f,Magnet.escapeRadius*Belt.strideDp,.0001f)
    }
    @Test fun aSingleMagneticPositionMovesAllCentersWithoutChangingTheirSpacing() {
        var captured: Int? = 12000
        for (step in -250..250) {
            val raw = 12000f + step / 100f
            captured = Magnet.captured(raw, captured)
            val shift = 12000 - raw - Magnet.distance(12000,raw,captured)
            for(index in 11996..12004) {
                assertEquals(1f,Magnet.distance(index+1,raw,captured)-Magnet.distance(index,raw,captured),.00001f)
                assertEquals(shift,index-raw-Magnet.distance(index,raw,captured),.00001f)
            }
        }
        assertEquals(0f,Magnet.position(.2f,0),0f)
        assertEquals(12f,Magnet.position(12f,12),0f)
    }

    @Test fun aCapturedStripCannotBeMovedUntilRawTravelExceedsTheEscapeThreshold() {
        for (step in -36..36) {
            val raw=12000f+dp(step.toFloat())
            val captured=Magnet.captured(raw,12000)
            assertEquals(12000,captured)
            assertEquals(12000f,Magnet.position(raw,captured),0f)
        }
        for(raw in listOf(12000f-dp(37f),12000f+dp(37f))) {
            val captured=Magnet.captured(raw,12000)
            assertNull(captured)
            assertEquals(raw,Magnet.position(raw,captured),0f)
        }
    }
    @Test fun nearbyCapturesButOnlyFurtherMovementEscapes() {
        assertEquals(10, Magnet.captured(10f+dp(22f), null))
        assertEquals(10, Magnet.captured(10f+dp(36f), 10))
        assertNull(Magnet.captured(10f+dp(24f), null))
        assertNull(Magnet.captured(10f+dp(37f), 10))
        assertNull(Magnet.captured(10.5f, null))
        assertEquals(11, Magnet.captured(11f-dp(22f), null))
        assertEquals(11f,Magnet.position(11f-dp(22f),11),0f)
    }
    @Test fun tremorDoesNotAlternateBetweenCaptureAndEscape() {
        var captured: Int? = 24000
        for (d in listOf(20f,28f,35f,36f,25f)) {
            captured = Magnet.captured(24000f + dp(d), captured)
            assertEquals(24000, captured)
        }
        captured = Magnet.captured(24000f+dp(37f), captured)
        assertNull(captured)
        for (d in listOf(36f,30f,24f)) assertNull(Magnet.captured(24000f+dp(d), captured))
        assertEquals(24000,Magnet.captured(24000f+dp(22f),captured))
    }
    @Test fun fastSwipesCanSkipAcrossThemesAndCycles() {
        assertEquals(42, Magnet.captured(42.02f, 10))
        assertEquals(23999, Magnet.captured(23999.02f, 24000))
    }
    @Test fun bothDirectionsAndNegativeCycleBoundariesHaveTheSameDetent() {
        for(anchor in listOf(-12,-1,0,1,12,24000)) {
            for(direction in listOf(-1,1)) {
                assertEquals(anchor,Magnet.captured(anchor+direction*dp(36f),anchor))
                assertNull(Magnet.captured(anchor+direction*dp(37f),anchor))
                assertNull(Magnet.captured(anchor+direction*(1-dp(24f)),null))
                val next=anchor+direction
                assertEquals(next,Magnet.captured(next-direction*dp(22f),null))
                assertEquals(next.toFloat(),Magnet.position(next-direction*dp(22f),next),0f)
            }
        }
    }
    @Test fun aShortFlickCannotEscapeThroughReleaseVelocityButAnEscapedFlingKeepsItsInertia() {
        for(velocity in listOf(-10000f,-500f,0f,500f,10000f)) {
            assertEquals(0f,Magnet.releaseVelocity(velocity,12000),0f)
            assertEquals(velocity,Magnet.releaseVelocity(velocity,null),0f)
        }
    }
}
