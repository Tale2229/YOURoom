package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class ClassroomStudyAvailabilityTest {
    private fun daytime(windows: List<StudyWindow>) = windows.map {
        StudyWindow(maxOf(480, it.start), minOf(1255, it.end))
    }.filter { it.duration > 0 }
    private fun slot(number: Int, start: Int, end: Int) = LessonSlot(number,
        LocalTime.ofSecondOfDay(start.toLong() * 60), LocalTime.ofSecondOfDay(end.toLong() * 60), emptyList())

    @Test fun lunchAndOtherBreaksAreIndependentOfRoomLessonAvailability() {
        val availability = ClassroomStudyAvailability(listOf(slot(5, 690, 735), slot(6, 780, 825), slot(7, 830, 875)))
        assertEquals(listOf(StudyWindow(735, 780), StudyWindow(825, 830)), daytime(availability.intervalsFor(emptySet())))
        assertEquals(listOf(StudyWindow(690, 830)), daytime(availability.intervalsFor(setOf(5, 6))))
    }

    @Test fun breakBeforeOrAfterAFreeLessonExtendsItToTheClassBoundary() {
        val availability = ClassroomStudyAvailability(listOf(slot(1, 480, 525), slot(2, 530, 575), slot(3, 580, 625)))
        assertEquals(listOf(StudyWindow(525, 580)), daytime(availability.intervalsFor(setOf(2))))
    }

    @Test fun missingLessonNumbersAreNeverTurnedIntoBreaks() {
        val availability = ClassroomStudyAvailability(listOf(slot(1, 480, 525), slot(3, 530, 575)))
        assertTrue(daytime(availability.intervalsFor(emptySet())).isEmpty())
        assertEquals(listOf(StudyWindow(480, 525), StudyWindow(530, 575)), daytime(availability.intervalsFor(setOf(1, 3))))
    }

    @Test fun longBreaksHaveNoArtificialLengthLimitAndSlotsMayArriveUnsorted() {
        val availability = ClassroomStudyAvailability(listOf(slot(2, 630, 675), slot(1, 480, 525)))
        assertEquals(listOf(StudyWindow(525, 630)), daytime(availability.intervalsFor(emptySet())))
        assertEquals(listOf(StudyWindow(480, 675)), daytime(availability.intervalsFor(setOf(1, 2))))
    }

    @Test fun occupiedLessonsSplitAvailabilityEvenWhenSurroundedByBreaks() {
        val availability = ClassroomStudyAvailability(listOf(slot(1, 480, 525), slot(2, 530, 575), slot(3, 580, 625)))
        assertEquals(listOf(StudyWindow(480, 530), StudyWindow(575, 625)), daytime(availability.intervalsFor(setOf(1, 3))))
    }

    @Test fun fixedOutsideTeachingHoursAreAvailableAndMergeWithAdjacentFreeLessons() {
        val availability = ClassroomStudyAvailability(listOf(slot(1, 480, 525), slot(2, 530, 575)))
        assertEquals(listOf(StudyWindow(0, 575), StudyWindow(1255, 1440)), availability.intervalsFor(setOf(1, 2)))
        assertEquals(listOf(StudyWindow(0, 480), StudyWindow(1255, 1440)),
            ClassroomStudyAvailability(emptyList()).intervalsFor(emptySet()))
    }

    @Test fun outsideHoursDoNotExtendToTheFirstOrLastReportedLesson() {
        val availability = ClassroomStudyAvailability(listOf(slot(3, 580, 625), slot(12, 1110, 1155)))
        assertEquals(listOf(StudyWindow(0, 480), StudyWindow(1255, 1440)), availability.intervalsFor(emptySet()))
    }

    @Test fun afterEightFiftyFiveJoinsTheLastFreePeriodWithoutAFalseRoomChange() {
        val availability = ClassroomStudyAvailability(listOf(slot(14, 1210, 1255)))
        assertEquals(listOf(StudyWindow(0, 480), StudyWindow(1210, 1440)), availability.intervalsFor(setOf(14)))
        assertEquals(listOf(StudyWindow(0, 480), StudyWindow(1255, 1440)), availability.intervalsFor(emptySet()))
    }
}
