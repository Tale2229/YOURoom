package com.example.youroom

import com.example.youroom.domain.PlanningPanelGesture
import org.junit.Assert.*
import org.junit.Test

class PlanningPanelGestureTest {
    private val gate=PlanningPanelGesture(32f,64f)
    @Test fun upwardDragCollapsesAtTheThresholdAndOnlyOncePerGesture() {
        assertNull(gate.drag(-20f,false,true).action)
        assertEquals(PlanningPanelGesture.Action.COLLAPSE,gate.drag(-12f,false,true).action)
        assertNull(gate.drag(100f,true,true).action)
        assertTrue(gate.drag(-100f,true,false).consume)
    }
    @Test fun aCollapsedPanelOnlyReopensAfterAnExtraPullAtTheTop() {
        assertFalse(gate.drag(500f,true,false).consume)
        assertNull(gate.drag(40f,true,true).action)
        assertEquals(PlanningPanelGesture.Action.EXPAND,gate.drag(24f,true,true).action)
    }
    @Test fun upwardScrollingAfterCollapsingDoesNotTouchThePanel() {
        assertFalse(gate.drag(-100f,true,false).consume)
        assertFalse(gate.drag(-100f,true,true).consume)
        assertFalse(gate.claimed)
    }
    @Test fun separateShortPullsDoNotAccumulateAcrossGestures() {
        repeat(4) { assertNull(gate.drag(40f,true,true).action);gate.reset() }
        assertFalse(gate.claimed)
    }
    @Test fun reversingDirectionResetsTheThreshold() {
        gate.drag(40f,true,true)
        gate.drag(-10f,true,true)
        assertNull(gate.drag(40f,true,true).action)
        assertEquals(PlanningPanelGesture.Action.EXPAND,gate.drag(24f,true,true).action)
    }
    @Test fun resetAllowsTheOppositeTransitionAfterACommittedDrag() {
        gate.drag(-40f,false,true);gate.reset()
        assertEquals(PlanningPanelGesture.Action.EXPAND,gate.drag(70f,true,true).action)
    }
    @Test fun downScrollingAnAlreadyExpandedPanelIsNotConsumed() {
        assertFalse(gate.drag(100f,false,true).consume)
        assertFalse(gate.drag(100f,false,false).consume)
    }
}
