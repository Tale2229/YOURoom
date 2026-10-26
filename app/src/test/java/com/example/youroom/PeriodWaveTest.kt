package com.example.youroom

import com.example.youroom.domain.PeriodWave
import org.junit.Assert.*
import org.junit.Test

class PeriodWaveTest {
    @Test fun invalidInputsAreSafeAndBothEndpointsAreFlat() {
        for (p in listOf(-1f, 0f, 1f, 2f, Float.NaN, Float.POSITIVE_INFINITY)) {
            for (i in 0..13) assertEquals(0f, PeriodWave.amplitude(p, i, 14), 0f)
        }
        assertEquals(0f, PeriodWave.amplitude(.5f, -1, 14), 0f)
        assertEquals(0f, PeriodWave.amplitude(.5f, 14, 14), 0f)
        assertEquals(0f, PeriodWave.amplitude(.5f, 0, 0), 0f)
    }

    @Test fun physicalRightPulsesFirstAndLeftPulsesLast() {
        assertTrue(PeriodWave.amplitude(.16f, 13, 14) > .9f)
        assertEquals(0f, PeriodWave.amplitude(.16f, 0, 14), 0f)
        assertTrue(PeriodWave.amplitude(.84f, 0, 14) > .9f)
        assertEquals(0f, PeriodWave.amplitude(.84f, 13, 14), 0f)
    }

    @Test fun everyBarHasOneSmoothPeakWithoutGoingBelowTheBaseline() {
        val count = 14
        val pulse = PeriodWave.pulseFraction(count)
        for (i in 0 until count) {
            val start = (1f - pulse) * (count - 1 - i) / (count - 1)
            assertEquals(0f, PeriodWave.amplitude(start, i, count), .0001f)
            assertEquals(1f, PeriodWave.amplitude(start + pulse / 2, i, count), .0001f)
            assertEquals(0f, PeriodWave.amplitude(start + pulse, i, count), .0001f)
            for (t in 0..1_000) assertTrue(PeriodWave.amplitude(t / 1_000f, i, count) in 0f..1f)
        }
    }

    @Test fun broaderCrestOverlapsSeveralAdjacentBarsWithoutBreakingContinuity() {
        var widest=0
        for (t in 1 until 1_000) {
            val moving = (0..13).filter { PeriodWave.amplitude(t / 1_000f, it, 14) > .0001f }
            widest=maxOf(widest,moving.size)
            assertTrue(moving.size <= 7)
            if (moving.isNotEmpty()) assertEquals(moving.size, moving.last() - moving.first() + 1)
        }
        assertTrue(widest>=5)
    }

    @Test fun sparseResponsesAndOnePeriodStillProduceBoundedPulses() {
        assertEquals(1f, PeriodWave.amplitude(.5f, 0, 1), .0001f)
        for (count in listOf(2, 3, 7, 20)) for (i in 0 until count) for (t in 0..100) {
            assertTrue(PeriodWave.amplitude(t / 100f, i, count) in 0f..1f)
        }
    }

    @Test fun motionDurationAndExtensionAreSmallAndFixed() {
        assertEquals(480, PeriodWave.durationMillis)
        assertEquals(250L, PeriodWave.startDelayMillis)
        assertEquals(1.25f,600f/PeriodWave.durationMillis,.0001f)
        assertEquals(3f, PeriodWave.maximumExtensionDp, 0f)
    }

    @Test fun spatialProfileVariesWithinOneBarAndHasSoftStationaryTails() {
        val p=.4f
        val samples=(0..1000).map { PeriodWave.spatialAmplitude(p,it/1000f,14) }
        assertTrue(samples.zipWithNext().maxOf { (a,b)->kotlin.math.abs(a-b) }<.01f)
        assertNotEquals(PeriodWave.spatialAmplitude(p,.6f,14),PeriodWave.spatialAmplitude(p,.65f,14))
        val pulse=PeriodWave.pulseFraction(14)
        assertTrue(PeriodWave.spatialAmplitude(.0001f,1f,14)<.00001f)
        assertTrue(PeriodWave.spatialAmplitude(pulse-.0001f,1f,14)<.00001f)
        assertEquals(PeriodWave.spatialAmplitude(.5f,0f,1),PeriodWave.amplitude(.5f,0,1),0f)
    }

}
