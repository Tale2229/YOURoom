package com.example.youroom

import com.example.youroom.data.DemoData
import com.example.youroom.data.local.PlanningHistoryCodec
import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Added with the feature; deliberately not executed per the user's instruction. */
class PlanningHistoryCodecTest {
    private val instant = Instant.parse("2026-10-24T04:00:00Z")
    private val date = LocalDate.of(2026, 10, 24)
    private fun record(index: Int, owner: String = "account:fixture", campus: Campus = Campus.XITUCHENG): PlanningHistoryRecord {
        val snapshot = DemoData.snapshot(campus, Clock.fixed(instant, SchoolZone)).copy(isDemo = false)
        val options = snapshot.classrooms.take(2).map { room ->
            PlanningOption(room.name, StudyPlan(StudyWindow(480, 540), listOf(PlanningStep.Study(480, 540, room, true)),
                60, 60, 0, 0, 0, 0, 0, 0, 0))
        }
        return PlanningHistoryRecord("record-$index", owner, instant.plusSeconds(index.toLong()), date, campus,
            PlanningGoal(480, 540, expectedRooms = setOf(snapshot.classrooms.first().id)), snapshot,
            PlanningResult(options, groups = listOf(PlanningGroup(PlanningDirection.BALANCED, listOf(0, 1)))), 1, PlanningDirection.BALANCED)
    }
    @Test fun tenIsSharedAcrossBothCampusesAndNewestComesFirst() {
        val records = (0..14).map { record(it, campus = if (it % 2 == 0) Campus.XITUCHENG else Campus.SHAHE) }
        val current = PlanningHistoryPolicy.current(records, date)
        assertEquals(10, current.size)
        assertEquals((14 downTo 5).map { "record-$it" }, current.map { it.id })
        assertEquals(Campus.entries.toSet(), current.map { it.campus }.toSet())
    }
    @Test fun fullPortfolioAndFrozenInputRoundTripWithoutAccountCredentials() {
        val value = record(1)
        assertEquals(value, PlanningHistoryCodec.read(PlanningHistoryCodec.write(listOf(value))).single())
        val json = PlanningHistoryCodec.write(listOf(value))
        assertFalse(json.contains("password")); assertFalse(json.contains("note"))
    }
    @Test fun followingCalendarDayExpiresRecordsAndAccountsRemainIndependent() {
        val values = (0..12).map { record(it) } + (20..32).map { record(it, "account:other") }
        assertEquals(20, PlanningHistoryPolicy.current(values, date).size)
        assertTrue(PlanningHistoryPolicy.current(values, date.plusDays(1)).isEmpty())
    }
    @Test fun corruptEntryDoesNotHideTheOtherValidRecords() {
        val json = PlanningHistoryCodec.write(listOf(record(1)))
        assertEquals(1, PlanningHistoryCodec.read(json.dropLast(1) + ",{}]").size)
        assertTrue(PlanningHistoryCodec.read("invalid").isEmpty())
    }
}
