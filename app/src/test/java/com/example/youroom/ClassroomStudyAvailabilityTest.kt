package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class ClassroomStudyAvailabilityTest {
    private fun slot(number: Int, start: Int, end: Int) = LessonSlot(number,
        LocalTime.ofSecondOfDay(start.toLong() * 60), LocalTime.ofSecondOfDay(end.toLong() * 60), emptyList())

    @Test fun lunchAndOtherBreaksAreIndependentOfRoomLessonAvailability() {
        val availability = ClassroomStudyAvailability(listOf(slot(5, 690, 735), slot(6, 780, 825), slot(7, 830, 875)))
        assertEquals(listOf(StudyWindow(735, 780), StudyWindow(825, 830)), availability.intervalsFor(emptySet()))
        assertEquals(listOf(StudyWindow(690, 830)), availability.intervalsFor(setOf(5, 6)))
    }

    @Test fun breakBeforeOrAfterAFreeLessonExtendsItToTheClassBoundary() {
        val availability = ClassroomStudyAvailability(listOf(slot(1, 480, 525), slot(2, 530, 575), slot(3, 580, 625)))
        assertEquals(listOf(StudyWindow(525, 580)), availability.intervalsFor(setOf(2)))
    }

    @Test fun missingLessonNumbersAreNeverTurnedIntoBreaks() {
        val availability = ClassroomStudyAvailability(listOf(slot(1, 480, 525), slot(3, 530, 575)))
        assertTrue(availability.intervalsFor(emptySet()).isEmpty())
        assertEquals(listOf(StudyWindow(480, 525), StudyWindow(530, 575)), availability.intervalsFor(setOf(1, 3)))
    }

    @Test fun longBreaksHaveNoArtificialLengthLimitAndSlotsMayArriveUnsorted() {
        val availability = ClassroomStudyAvailability(listOf(slot(2, 630, 675), slot(1, 480, 525)))
        assertEquals(listOf(StudyWindow(525, 630)), availability.intervalsFor(emptySet()))
        assertEquals(listOf(StudyWindow(480, 675)), availability.intervalsFor(setOf(1, 2)))
    }

    @Test fun occupiedLessonsSplitAvailabilityEvenWhenSurroundedByBreaks() {
        val availability = ClassroomStudyAvailability(listOf(slot(1, 480, 525), slot(2, 530, 575), slot(3, 580, 625)))
        assertEquals(listOf(StudyWindow(480, 530), StudyWindow(575, 625)), availability.intervalsFor(setOf(1, 3)))
    }

    @Test fun beforeAndAfterTheKnownTimetableRemainUnavailable() {
        val availability = ClassroomStudyAvailability(listOf(slot(1, 480, 525), slot(2, 530, 575)))
        assertEquals(listOf(StudyWindow(480, 575)), availability.intervalsFor(setOf(1, 2)))
        assertEquals(emptyList<StudyWindow>(), ClassroomStudyAvailability(emptyList()).intervalsFor(emptySet()))
    }
}
