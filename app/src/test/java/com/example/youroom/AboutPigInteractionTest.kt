package com.example.youroom

import com.example.youroom.ui.AboutPigInteraction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutPigInteractionTest {
    @Test fun burstOfClicksPlaysExactlyOncePerClickWithOnlyOneFrameBetween()=runTest {
        val pig=AboutPigInteraction()
        val starts=mutableListOf<Long>()
        var finished=0
        val worker=backgroundScope.launch {
            pig.play {
                assertTrue(pig.squinting)
                starts+=testScheduler.currentTime
                delay(280)
                finished++
            }
        }
        repeat(100) {pig.click()}
        runCurrent()
        advanceTimeBy(100*296L)
        runCurrent()
        assertEquals(100,finished)
        assertEquals((0L until 100).map {it*296},starts)
        assertFalse(pig.squinting)
        worker.cancel()
    }

    @Test fun clicksAtTheEndAndDuringRestCannotRaceTheExpressionReset()=runTest {
        val pig=AboutPigInteraction()
        var finished=0
        val worker=backgroundScope.launch {pig.play {delay(280);finished++}}
        pig.click();runCurrent()
        advanceTimeBy(280)
        pig.click() // Exactly at the first animation's completion boundary.
        runCurrent()
        assertFalse(pig.squinting)
        advanceTimeBy(8)
        pig.click() // Still in its one-frame gap.
        runCurrent()
        advanceTimeBy(1000);runCurrent()
        assertEquals(3,finished)
        assertFalse(pig.squinting)
        pig.click();runCurrent();assertTrue(pig.squinting)
        advanceTimeBy(400);runCurrent()
        assertEquals(4,finished)
        assertFalse(pig.squinting)
        worker.cancel()
    }

    @Test fun closingMidAnimationResetsExpressionAndDoesNotLeakPendingClicksToNewSheet()=runTest {
        val old=AboutPigInteraction()
        var oldStarts=0
        val worker=backgroundScope.launch {old.play {oldStarts++;delay(280)}}
        repeat(12) {old.click()};runCurrent();assertTrue(old.squinting)
        advanceTimeBy(60);worker.cancel();runCurrent()
        assertFalse(old.squinting)
        assertEquals(1,oldStarts)
        val fresh=AboutPigInteraction()
        var starts=0
        val freshWorker=backgroundScope.launch {fresh.play {starts++;delay(280)}}
        runCurrent();assertFalse(fresh.squinting);assertEquals(0,starts)
        fresh.click();advanceTimeBy(400);runCurrent()
        assertEquals(1,starts);assertFalse(fresh.squinting)
        freshWorker.cancel()
    }
}
