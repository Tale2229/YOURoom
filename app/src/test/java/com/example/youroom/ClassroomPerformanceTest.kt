package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.system.measureNanoTime

class ClassroomPerformanceTest {
    private class CountingRooms(private val rooms: List<Classroom>) : AbstractList<Classroom>() {
        var reads = 0
        override val size get() = rooms.size
        override fun get(index: Int): Classroom { reads++; return rooms[index] }
    }
    private fun schedule(rooms: List<Classroom>, campus: String = "01") = ClassroomSnapshot(campus,
        LocalDate.of(2026, 10, 12), Instant.EPOCH, (1..14).map { number ->
            LessonSlot(number, LocalTime.of(number + 5, 0), LocalTime.of(number + 5, 45), rooms)
        })
    private fun rooms(count: Int) = (1..count).map { Classroom("01:2-$it", "2-$it", "2", 60) }

    @Test fun largeScheduleBuildsIndexOnceAndNeverRescansRoomListsWhenFiltering() {
        val counted = CountingRooms(rooms(1000))
        val snapshot = schedule(counted)
        assertEquals(1000, snapshot.classrooms.size)
        val reads = counted.reads
        assertEquals(14_000, reads)
        repeat(50) {
            val result = ClassroomFilter.search(snapshot, SearchFilters(setOf(1, 3, 6)), emptyMap())
            assertEquals(1000, result.rooms.size)
            assertEquals((1..14).toSet(), result.rooms.first().freeSlots)
        }
        assertEquals("Repeated filtering must not rescan all rooms in every period", reads, counted.reads)
        assertSame(snapshot.index, snapshot.index)
        assertSame(snapshot.classrooms, snapshot.classrooms)
    }

    @Test fun indexPreservesFirstRoomMetadataAndExactNonContiguousAvailability() {
        val a = rooms(1).single()
        val b = a.copy(id = "01:3-1", name = "3-1", building = "3")
        val snapshot = schedule(listOf(a)).copy(slots = listOf(
            LessonSlot(1, LocalTime.of(8, 0), LocalTime.of(8, 45), listOf(a, b)),
            LessonSlot(3, LocalTime.of(10, 0), LocalTime.of(10, 45), listOf(a.copy(capacity = 100)))))
        assertEquals(listOf(a, b), snapshot.classrooms)
        assertEquals(setOf(1, 3), snapshot.index.freeSlotsByRoom[a.id])
        assertEquals(setOf(1), snapshot.index.freeSlotsByRoom[b.id])
        assertEquals(setOf(a.id), snapshot.index.roomsBySlot[3])
    }

    @Test fun searchCacheRequiresMatchingSnapshotFiltersAndProfiles() {
        val snapshot = schedule(rooms(3))
        val profiles = emptyMap<String, RoomProfile>()
        val request = ClassroomSearchRequest(snapshot, SearchFilters(setOf(1)), profiles)
        val cache = ClassroomSearchCache()
        assertNull(cache.peek(request))
        val result = ClassroomFilter.search(snapshot, request.filters, profiles)
        cache.put(request, result)
        assertSame(result, cache.peek(request.copy()))
        assertNull(cache.peek(request.copy(snapshot = snapshot.copy(campusId = "04"))))
        assertNull(cache.peek(request.copy(filters = request.filters.copy(selectedSlots = setOf(2)))))
        assertNull(cache.peek(request.copy(filters = request.filters.copy(buildings = setOf("3")))))
        assertNull(cache.peek(request.copy(profiles = mapOf(snapshot.classrooms.first().id to
            snapshot.classrooms.first().emptyProfile().copy(excluded = true)))))
    }

    @Test fun indexedAvailabilityMatchesLegacyScanAndReportsHostTiming() {
        val snapshot = schedule(rooms(600))
        val indexed = snapshot.index
        fun oldScan() = snapshot.classrooms.associate { room -> room.id to snapshot.slots
            .filter { slot -> slot.classrooms.any { it.id == room.id } }.map { it.number }.toSet() }
        assertEquals(oldScan(), indexed.freeSlotsByRoom)
        repeat(5) { oldScan(); ClassroomFilter.search(snapshot, SearchFilters(setOf(1, 3)), emptyMap()) }
        val legacy = List(10) { measureNanoTime { oldScan() } }.sorted()[5] / 1_000_000.0
        val filtered = List(10) { measureNanoTime {
            ClassroomFilter.search(snapshot, SearchFilters(setOf(1, 3)), emptyMap())
        } }.sorted()[5] / 1_000_000.0
        println("HOST ONLY: 600 rooms x 14 periods; legacy availability scan median = $legacy ms; indexed full search median = $filtered ms")
        // Timing is diagnostic, not a flaky assertion or a claim about phone frame rate.
    }
}
