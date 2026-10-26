package com.example.youroom

import com.example.youroom.domain.*
import com.example.youroom.ui.DinosaurRun
import com.example.youroom.ui.RunnerPhase
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Offline poetry seeds remain available for guide fixtures and load failures. */
class LeisureContentTest {
    @Test fun offlineFallbackContainsOnlyAttributedVerseSeeds() {
        val items = LeisureContent.items()
        assertEquals(3,items.size)
        assertEquals(items.size,items.map { it.id }.distinct().size)
        assertTrue(items.all { it.id.startsWith("verse-") && it.body.isNotBlank() && '《' in it.source })
    }
    @Test fun nextNeverImmediatelyRepeatsTheSameItem() {
        var previous: String? = null
        repeat(60) { index ->
            val item = LeisureContent.next(previous, Random(index))
            assertNotEquals(previous, item.id)
            previous = item.id
        }
    }
    @Test fun pauseFreezesTheRoundUntilTheUserExplicitlyResumes() {
        val run = DinosaurRun()
        run.jump(); repeat(20) { run.tick(.016f, 320f) }
        run.pause()
        val elapsed = run.elapsed; val altitude = run.altitude
        repeat(100) { run.tick(.016f, 320f) }
        assertEquals(RunnerPhase.PAUSED, run.phase)
        assertEquals(elapsed, run.elapsed, 0f); assertEquals(altitude, run.altitude, 0f)
        run.jump(); run.tick(.016f, 320f)
        assertTrue(run.elapsed > elapsed)
    }
}
