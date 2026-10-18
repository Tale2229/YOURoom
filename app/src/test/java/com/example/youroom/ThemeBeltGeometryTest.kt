package com.example.youroom

import com.example.youroom.ui.ThemeBeltGeometry as Belt
import org.junit.Assert.*
import org.junit.Test

class ThemeBeltGeometryTest {
    @Test fun theCenterIsFullSizeAndDistantTilesShrinkSymmetrically() {
        assertEquals(1f,Belt.scale(0f),0f)
        assertEquals(1f,Belt.alpha(0f),0f)
        for (distance in listOf(.1f,.5f,1f,1.5f,2f)) {
            assertEquals(Belt.scale(distance),Belt.scale(-distance),0f)
            assertEquals(Belt.alpha(distance),Belt.alpha(-distance),0f)
            assertTrue(Belt.scale(distance) in Belt.minScale..1f)
            assertTrue(Belt.alpha(distance) in 0f..1f)
            assertTrue(Belt.scale(distance)<Belt.scale(distance-.05f))
            assertTrue(Belt.alpha(distance)<Belt.alpha(distance-.05f))
        }
    }

    @Test fun scaleAndOpacityChangeContinuouslyWithoutSquashingTheSquares() {
        for (i in -200..200) {
            val d=i/100f
            assertTrue(kotlin.math.abs(Belt.scale(d)-Belt.scale(d+.001f))<.001f)
            assertTrue(kotlin.math.abs(Belt.alpha(d)-Belt.alpha(d+.001f))<.002f)
        }
        assertTrue(Belt.scale(1f) in .78f.. .80f)
        assertEquals(Belt.minScale,Belt.scale(10f),.001f)
    }

    @Test fun repeatingIndicesSupportFourTenAndManyMoreSchemesWithoutSpecialCases() {
        for (count in listOf(4,10,16,37)) for (value in 0 until count) {
            val initial=Belt.initial(value,count)
            assertEquals(value,Belt.value(initial.toFloat(),count))
            assertEquals((value+3)%count,Belt.value(initial+3f,count))
            assertEquals(Math.floorMod(value-3,count),Belt.value(initial-3f,count))
            for (target in 0 until count) {
                val nearest=Belt.nearest(target,initial,count)
                assertEquals(target,Belt.value(nearest.toFloat(),count))
                assertTrue(kotlin.math.abs(nearest-initial)<=count/2+1)
            }
        }
    }
}
