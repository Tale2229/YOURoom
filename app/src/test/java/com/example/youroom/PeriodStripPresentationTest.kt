package com.example.youroom

import androidx.datastore.preferences.core.*
import com.example.youroom.data.DemoData
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import org.junit.Assert.*
import org.junit.Test

class PeriodStripPresentationTest {
    private val slots=DemoData.snapshot().slots
    private val proportional=PeriodStripLayout.TIME_PROPORTIONAL
    private fun bars(mode: PeriodStripLayout=proportional)=PeriodStripGeometry.bars(slots,312f,3f,mode)

    @Test fun defaultsAndUnknownSavedLayoutsPreserveOriginalAppearance() {
        assertEquals(PeriodStripPreferences(),PeriodStripPreferenceKeys.read(emptyPreferences()))
        val values=mutablePreferencesOf(PeriodStripPreferenceKeys.layout to "future")
        assertEquals(PeriodStripLayout.EQUAL,PeriodStripPreferenceKeys.read(values).layout)
        assertFalse(PeriodStripPreferenceKeys.read(values).showNumbers)
    }

    @Test fun preferencesRoundTripWithoutChangingLegacyFiltersOrTags() {
        val values=mutablePreferencesOf(SharedFilterPreferences.favorites to true,TagCatalogPreferences.order to "[\"已收藏\",\"靠窗\"]")
        val initial=values.toPreferences()
        for(layout in PeriodStripLayout.entries) for(numbers in listOf(false,true)) {
            val pref=PeriodStripPreferences(layout,numbers)
            PeriodStripPreferenceKeys.write(values,pref)
            assertEquals(pref,PeriodStripPreferenceKeys.read(values))
            initial.asMap().forEach { (key,value) -> assertEquals(value,values.asMap()[key]) }
        }
    }

    @Test fun proportionalBarsAndEveryGapUseTheSameMinuteScale() {
        val bars=bars();val scale=312f/775
        assertEquals(14,bars.size)
        bars.forEach { assertEquals(45*scale,it.width,.001f) }
        bars.zipWithNext().forEachIndexed { i,(a,b) ->
            val minutes=(slots[i+1].start.toSecondOfDay()-slots[i].end.toSecondOfDay())/60f
            assertEquals(minutes*scale,b.start-a.end,.001f)
        }
        assertEquals(bars[4].width,bars[5].start-bars[4].end,.001f) // 45-minute lunch
        assertEquals(0f,bars.first().start,0f);assertEquals(312f,bars.last().end,0f)
    }

    @Test fun defaultBarsHaveTheSameWidthsAndThreePixelGapsEvenForChangedResponseTimes() {
        val bars=bars(PeriodStripLayout.EQUAL)
        bars.forEach { assertEquals(19.5f,it.width,.001f) }
        bars.zipWithNext().forEach { (a,b) -> assertEquals(3f,b.start-a.end,.001f) }
        val changed=slots.map { if(it.number==6) it.copy(start=java.time.LocalTime.of(14,10),end=java.time.LocalTime.of(14,55)) else it }
        assertEquals(bars,PeriodStripGeometry.bars(changed,312f,3f,PeriodStripLayout.EQUAL))
    }

    @Test fun holdUsesTheNearestEdgeAcrossLunchAndMirrorsWithoutChangingSlotIdentity() {
        val bars=bars();val midpoint=(bars[4].end+bars[5].start)/2
        assertEquals(4,PeriodStripGeometry.nearestIndex(midpoint-.1f,312f,bars))
        assertEquals(5,PeriodStripGeometry.nearestIndex(midpoint+.1f,312f,bars))
        bars.forEachIndexed { i,bar ->
            assertEquals(i,PeriodStripGeometry.nearestIndex(bar.center,312f,bars))
            assertEquals(i,PeriodStripGeometry.nearestIndex(312-bar.center,312f,bars,true))
        }
        assertEquals(0,PeriodStripGeometry.nearestIndex(-50f,312f,bars))
        assertEquals(13,PeriodStripGeometry.nearestIndex(400f,312f,bars))
    }

    @Test fun allMinuteCoordinatesAndPlanningBracketsShareTheProportionalAxis() {
        val scale=312f/775
        for(minute in 480..1255) assertEquals((minute-480)*scale,
            PlanningDisplay.xAt(minute.toFloat(),slots,312f,3f,proportional)!!,.001f)
        val windows=listOf(StudyWindow(720,810),StudyWindow(1278,1338))
        val anchors=planningRangeAnchors(windows,slots,360f,24f,3f,proportional)
        assertEquals(24+(720-480)*scale,anchors[0].start,.001f)
        assertEquals(24+(810-480)*scale,anchors[0].end,.001f)
        assertEquals(336f,anchors[1].center,0f);assertTrue(anchors[1].outside)
    }

    @Test fun spatialWavePeaksAtConstantPixelSpeedAndStillFinishesAtBothEnds() {
        val bars=bars();val first=bars.first().center;val span=bars.last().center-first
        val pulse=3f/16
        val peaks=bars.map { bar ->
            val x=(bar.center-first)/span;val peak=(1-pulse)*(1-x)+pulse/2
            assertEquals(1f,PeriodWave.spatialAmplitude(peak,x,14),.0001f)
            val mirroredPeak=(1-pulse)*x+pulse/2
            assertEquals(1f,PeriodWave.spatialAmplitude(mirroredPeak,1-x,14),.0001f)
            assertEquals(0f,PeriodWave.spatialAmplitude(0f,x,14),0f)
            assertEquals(0f,PeriodWave.spatialAmplitude(1f,x,14),0f)
            peak
        }
        peaks.zipWithNext().forEachIndexed { i,(a,b) ->
            assertEquals((bars[i+1].center-bars[i].center)/span*(1-pulse),a-b,.0001f)
        }
        assertTrue(peaks[4]-peaks[5]>peaks[0]-peaks[1]) // lunch takes longer to traverse
        for(p in listOf(Float.NaN,-1f,0f,1f,2f)) assertEquals(0f,PeriodWave.spatialAmplitude(p,.5f,14),0f)
    }

    @Test fun invalidProportionalGeometryIsEmptyAndMissingDataDoesNotInventTimes() {
        assertTrue(PeriodStripGeometry.bars(emptyList(),312f,3f,proportional).isEmpty())
        assertTrue(PeriodStripGeometry.bars(slots,0f,3f,proportional).isEmpty())
        assertNull(PlanningDisplay.xAt(600f,slots.reversed(),312f,3f,proportional))
        assertNull(PlanningDisplay.xAt(Float.NaN,slots,312f,3f,proportional))
        assertNull(PeriodStripGeometry.nearestIndex(10f,312f,emptyList()))
    }
}
