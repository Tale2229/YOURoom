package com.example.youroom

import com.example.youroom.ui.PlanningTapCooldown
import org.junit.Assert.*
import org.junit.Test

class PlanningTapCooldownTest {
    @Test fun firstTapRespondsImmediatelyAndSpamDoesNotExtendTheCooldown() {
        val gate=PlanningTapCooldown()
        assertTrue(gate.acquire(0))
        for(time in 1L..2499L) assertFalse(gate.acquire(time))
        assertTrue(gate.acquire(2500))
        assertFalse(gate.acquire(4999))
        assertTrue(gate.acquire(5000))
    }
    @Test fun freshCampusOrPageHasAnIndependentGate() {
        val west=PlanningTapCooldown();val shahe=PlanningTapCooldown()
        assertTrue(west.acquire(123456789))
        assertTrue(shahe.acquire(123456789))
        assertFalse(west.acquire(123456790))
    }
}
