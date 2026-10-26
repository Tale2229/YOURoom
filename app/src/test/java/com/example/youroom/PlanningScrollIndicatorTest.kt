package com.example.youroom

import com.example.youroom.ui.planningScrollIndicator
import org.junit.Assert.*
import org.junit.Test

class PlanningScrollIndicatorTest {
    @Test fun noIndicatorWhenTheSummariesFitOrAreNotMeasured() {
        assertNull(planningScrollIndicator(0,176,8,48,360,0,0))
        assertNull(planningScrollIndicator(1,176,8,48,360,0,0))
        assertNull(planningScrollIndicator(5,0,8,48,360,0,0))
        assertNull(planningScrollIndicator(5,176,8,48,0,0,0))
        assertNull(planningScrollIndicator(2,176,8,48,408,0,0))
    }
    @Test fun theThumbAccountsForPaddingAndGapsAndReachesBothEnds() {
        val first=planningScrollIndicator(5,176,8,48,360,0,0)!!
        val middle=planningScrollIndicator(5,176,8,48,360,1,116)!!
        val last=planningScrollIndicator(5,176,8,48,360,3,48)!!
        assertEquals(.375f,first.sizeFraction,.0001f)
        assertEquals(0f,first.positionFraction,0f)
        assertEquals(.5f,middle.positionFraction,.0001f)
        assertEquals(1f,last.positionFraction,0f)
    }
    @Test fun clampedOffsetsAndDensityDoNotChangeThePositionOrSize() {
        val ordinary=planningScrollIndicator(5,176,8,48,360,1,116)!!
        assertEquals(ordinary,planningScrollIndicator(5,352,16,96,720,1,232))
        assertEquals(0f,planningScrollIndicator(5,176,8,48,360,0,-20)!!.positionFraction,0f)
        assertEquals(1f,planningScrollIndicator(5,176,8,48,360,9,0)!!.positionFraction,0f)
    }
}
