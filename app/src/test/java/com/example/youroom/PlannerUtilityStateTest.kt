package com.example.youroom

import com.example.youroom.domain.Campus
import com.example.youroom.ui.PlannerPanel
import com.example.youroom.ui.PlannerUtilityState
import org.junit.Assert.*
import org.junit.Test

class PlannerUtilityStateTest {
    @Test fun closingEitherPanelAcknowledgesOnlyTheObservedCompletedGeneration() {
        val utility=PlannerUtilityState()
        assertTrue(utility.completionUnseen(Campus.XITUCHENG,1))
        for(panel in listOf(PlannerPanel.HISTORY,PlannerPanel.LEISURE)) {
            utility.panel=panel;utility.observedCompletion=Campus.XITUCHENG to 1
            utility.closePanel()
            assertEquals(PlannerPanel.RESULT,utility.panel)
            assertFalse(utility.completionUnseen(Campus.XITUCHENG,1))
        }
        assertTrue(utility.completionUnseen(Campus.XITUCHENG,2))
        assertTrue(utility.completionUnseen(Campus.SHAHE,1))
        utility.observedCompletion=null;utility.closePanel()
        assertTrue(utility.completionUnseen(Campus.XITUCHENG,2))
    }
    @Test fun resultsAlreadyOnScreenAreAcknowledgedWhileCompletionInsideAnOverlayCanNotify() {
        val utility=PlannerUtilityState()
        utility.observeCompletion(Campus.XITUCHENG,1,resultsVisible=true)
        assertFalse(utility.completionUnseen(Campus.XITUCHENG,1))
        utility.panel=PlannerPanel.LEISURE
        utility.observeCompletion(Campus.XITUCHENG,2,resultsVisible=false)
        assertTrue(utility.completionUnseen(Campus.XITUCHENG,2))
        utility.closePanel()
        assertFalse(utility.completionUnseen(Campus.XITUCHENG,2))
        utility.observeCompletion(Campus.SHAHE,1,resultsVisible=false)
        assertTrue(utility.completionUnseen(Campus.SHAHE,1))
        utility.observeCompletion(Campus.SHAHE,1,resultsVisible=true)
        assertFalse(utility.completionUnseen(Campus.SHAHE,1))
    }
}
