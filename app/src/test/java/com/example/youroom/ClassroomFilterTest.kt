package com.example.youroom

import com.example.youroom.data.SchoolJson
import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ClassroomFilterTest {
    private val a = SchoolJson.classroom("2-201(180)", "01")
    private val b = SchoolJson.classroom("3-108(56)", "01")
    private val c = SchoolJson.classroom("未来学习大楼-202-203(60)", "01")
    private val snapshot = ClassroomSnapshot("01", LocalDate.of(2026, 10, 11), Instant.EPOCH,
        listOf(LessonSlot(1, LocalTime.of(8, 0), LocalTime.of(8, 45), listOf(a, b, c)),
            LessonSlot(2, LocalTime.of(8, 50), LocalTime.of(9, 35), listOf(a, c)),
            LessonSlot(3, LocalTime.of(9, 50), LocalTime.of(10, 35), listOf(b, c))))

    @Test fun multipleSlotsUseIntersectionNotUnion() {
        val result = ClassroomFilter.search(snapshot, SearchFilters(1, 3), emptyMap())
        assertEquals(listOf(c.id), result.rooms.map { it.classroom.id })
        assertTrue(result.hasUncoveredGaps)
    }

    @Test fun compoundRoomIdentifierIsPreserved() {
        assertEquals("未来学习大楼-202-203", c.name)
        assertEquals("未来学习大楼", c.building)
        assertEquals(60, c.capacity)
        assertEquals("01:未来学习大楼-202-203", c.id)
    }

    @Test fun exclusionsAreHardFiltersAndFavoritesSortFirst() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(excluded = true), b.id to b.emptyProfile().copy(favorite = true))
        val rooms = ClassroomFilter.search(snapshot, SearchFilters(1, 1), profiles).rooms
        assertEquals(listOf(b.id, c.id), rooms.map { it.classroom.id })
    }

    @Test fun unknownPreferencesDoNotMatchQuietOrOutletRequirements() {
        val profiles = mapOf(c.id to c.emptyProfile().copy(quiet = true, outlets = true))
        assertEquals(listOf(c.id), ClassroomFilter.search(snapshot,
            SearchFilters(1, 1, quietOnly = true, outletsOnly = true), profiles).rooms.map { it.classroom.id })
    }

    @Test fun capacityBuildingAndPersonalNoteCanFilter() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(note = "后排靠窗"))
        assertEquals(listOf(a.id), ClassroomFilter.search(snapshot,
            SearchFilters(1, 1, building = "2", query = "靠窗", minimumCapacity = 100), profiles).rooms.map { it.classroom.id })
    }

    @Test fun missingOrReversedPeriodsCannotProduceAvailability() {
        assertTrue(ClassroomFilter.search(snapshot, SearchFilters(1, 4), emptyMap()).missingSlots)
        assertTrue(ClassroomFilter.search(snapshot, SearchFilters(3, 1), emptyMap()).rooms.isEmpty())
    }

    @Test fun favoriteOnlyReallyRequiresFavorite() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(favorite = true))
        assertEquals(listOf(a.id), ClassroomFilter.search(snapshot, SearchFilters(1, 1, favoritesOnly = true), profiles)
            .rooms.map { it.classroom.id })
    }

    @Test fun snapshotDoesNotBecomeTodayAfterMidnight() {
        assertTrue(snapshot.isCurrentOn(LocalDate.of(2026, 10, 11)))
        assertFalse(snapshot.isCurrentOn(LocalDate.of(2026, 10, 12)))
    }
}
