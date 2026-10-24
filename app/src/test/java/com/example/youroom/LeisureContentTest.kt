package com.example.youroom

import com.example.youroom.domain.*
import com.example.youroom.ui.DinosaurRun
import com.example.youroom.ui.RunnerPhase
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Definition only: no tests were run during this implementation. */
class LeisureContentTest {
    @Test fun everyThemeHasAllFiveOfflineCategoriesAndSources() {
        for (tone in CampusTone.entries) {
            val items = LeisureContent.items(tone)
            assertEquals(LeisureKind.entries.toSet(), items.map { it.kind }.toSet())
            assertEquals(items.size, items.map { it.id }.distinct().size)
            assertTrue(items.all { it.body.isNotBlank() && it.source.isNotBlank() })
            assertEquals(tone, items.single { it.kind == LeisureKind.THEME }.theme)
        }
    }
    @Test fun nextNeverImmediatelyRepeatsTheSameItem() {
        var previous: String? = null
        repeat(60) { index ->
            val item = LeisureContent.next(CampusTone.MATISSE, previous, Random(index))
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
