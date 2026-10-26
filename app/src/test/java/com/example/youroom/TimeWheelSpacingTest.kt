package com.example.youroom

import com.example.youroom.ui.PlanningWheelScale
import com.example.youroom.ui.timeWheelPlanningTitleSpacing
import com.example.youroom.ui.timeWheelVisibleEdge
import org.junit.Assert.*
import org.junit.Test

class TimeWheelSpacingTest {
    @Test fun fullCylinderSharesItsUnusedCapSoTheVisibleGapMatchesTheScaledPlanningWheel() {
        for(line in listOf(36f,45f,54f,72f)) {
            val row=(line+12f).coerceAtLeast(48f)
            val reference=8f*PlanningWheelScale+timeWheelVisibleEdge(3.5f,row,line)*PlanningWheelScale
            val full=timeWheelPlanningTitleSpacing(4.5f,row,line)+timeWheelVisibleEdge(4.5f,row,line)
            assertEquals(reference,full,.001f)
            assertTrue(timeWheelPlanningTitleSpacing(4.5f,row,line)<0)
        }
    }
}
