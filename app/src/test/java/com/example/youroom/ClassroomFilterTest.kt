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
        val result = ClassroomFilter.search(snapshot, SearchFilters((1..3).toSet()), emptyMap())
        assertEquals(listOf(c.id), result.rooms.map { it.classroom.id })
        assertTrue(result.hasUncoveredGaps)
    }

    @Test fun nonContiguousSelectionDoesNotRequireTheGapToBeFree() {
        val result = ClassroomFilter.search(snapshot, SearchFilters(setOf(1, 3)), emptyMap())
        assertEquals(setOf(b.id, c.id), result.rooms.map { it.classroom.id }.toSet())
        assertEquals(listOf(1, 3), result.selectedSlots.map { it.number })
    }

    @Test fun emptySelectionReturnsEveryUniqueRoomAcrossTheSnapshot() {
        val differentAvailability = snapshot.copy(slots = snapshot.slots.map {
            if (it.number == 1) it.copy(classrooms = listOf(a, c)) else it
        })
        val result = ClassroomFilter.search(differentAvailability, SearchFilters(emptySet()), emptyMap())
        assertEquals(listOf(a.id, b.id, c.id), result.rooms.map { it.classroom.id })
        assertEquals(setOf(1, 2), result.rooms.first { it.classroom.id == a.id }.freeSlots)
        assertEquals(setOf(3), result.rooms.first { it.classroom.id == b.id }.freeSlots)
        assertEquals(setOf(1, 2, 3), result.rooms.first { it.classroom.id == c.id }.freeSlots)
        assertTrue(result.selectedSlots.isEmpty())
        assertFalse(result.missingSlots)
        assertFalse(result.hasUncoveredGaps)
    }

    @Test fun emptySelectionStillAppliesBuildingsQueryAndAllPersonalConditions() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(favorite = true, outlets = true,
            note = "后排靠窗", tags = setOf(PersonalTags.comfortable)),
            b.id to b.emptyProfile().copy(favorite = true),
            c.id to c.emptyProfile().copy(favorite = true, outlets = true, note = "靠窗"))
        val filters = SearchFilters(buildings = setOf("2", "3"), query = "201", favoritesOnly = true,
            outletsOnly = true, tags = setOf(PersonalTags.comfortable))
        assertEquals(listOf(a.id), ClassroomFilter.search(snapshot, filters, profiles).rooms.map { it.classroom.id })
        assertEquals(listOf(a.id), ClassroomFilter.search(snapshot, filters, profiles +
            (a.id to profiles.getValue(a.id).copy(excluded = true))).rooms.map { it.classroom.id })
        assertTrue(ClassroomFilter.search(snapshot, filters.copy(selectedSlots = setOf(1)), profiles +
            (a.id to profiles.getValue(a.id).copy(excluded = true))).rooms.isEmpty())
    }

    @Test fun emptySelectionIncludesExcludedRoomsAndKeepsExcludedFavoritesFirst() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(excluded = true),
            c.id to c.emptyProfile().copy(favorite = true, excluded = true))
        val result = ClassroomFilter.search(snapshot, SearchFilters(), profiles)
        assertEquals(listOf(c.id, a.id, b.id), result.rooms.map { it.classroom.id })
        assertTrue(result.rooms.first().profile.favorite && result.rooms.first().profile.excluded)
        assertTrue(result.rooms.first { it.classroom.id == a.id }.profile.excluded)
        assertEquals(setOf(1, 2, 3), result.rooms.first().freeSlots)
    }

    @Test fun selectionOnlyChangesExclusionVisibilityNeverTheStoredMark() {
        val profile = a.emptyProfile().copy(favorite = true, excluded = true, outlets = true, note = "保留")
        val profiles = mapOf(a.id to profile)
        for (selected in listOf(emptySet(), setOf(1), setOf(1, 2), emptySet())) {
            val rooms = ClassroomFilter.search(snapshot, SearchFilters(selected), profiles).rooms
            assertEquals(selected.isEmpty(), rooms.any { it.classroom.id == a.id })
            assertEquals(profile, profiles[a.id])
        }
    }

    @Test fun emptySelectionExclusionRuleAlsoAppliesToShaheAndAllExcludedSnapshots() {
        val shahe = snapshot.copy(campusId = "04", slots = snapshot.slots.map { slot ->
            slot.copy(classrooms = slot.classrooms.map { it.copy(id = it.id.replaceFirst("01:", "04:")) })
        })
        val profiles = shahe.classrooms.associate { it.id to it.emptyProfile().copy(excluded = true, favorite = it.name == b.name) }
        val all = ClassroomFilter.search(shahe, SearchFilters(), profiles).rooms
        assertEquals(3, all.size)
        assertEquals("04:3-108", all.first().classroom.id)
        assertTrue(ClassroomFilter.search(shahe, SearchFilters(setOf(1)), profiles).rooms.isEmpty())
    }

    @Test fun emptySelectionAndEmptySnapshotProduceAnEmptyValidResult() {
        val result = ClassroomFilter.search(snapshot.copy(slots = emptyList()), SearchFilters(), emptyMap())
        assertTrue(result.rooms.isEmpty())
        assertFalse(result.missingSlots)
        assertFalse(result.hasUncoveredGaps)
    }

    @Test fun compoundRoomIdentifierIsPreserved() {
        assertEquals("未来学习大楼-202-203", c.name)
        assertEquals("未来学习大楼", c.building)
        assertEquals(60, c.capacity)
        assertEquals("01:未来学习大楼-202-203", c.id)
    }

    @Test fun exclusionsAreHardFiltersWhenPeriodsAreSelectedAndFavoritesSortFirst() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(excluded = true), b.id to b.emptyProfile().copy(favorite = true))
        val rooms = ClassroomFilter.search(snapshot, SearchFilters((1..1).toSet()), profiles).rooms
        assertEquals(listOf(b.id, c.id), rooms.map { it.classroom.id })
    }

    @Test fun unknownPreferencesDoNotMatchQuietOrOutletRequirements() {
        val profiles = mapOf(c.id to c.emptyProfile().copy(tags = setOf(PersonalTags.comfortable), outlets = true))
        assertEquals(listOf(c.id), ClassroomFilter.search(snapshot,
            SearchFilters((1..1).toSet(), tags = setOf(PersonalTags.comfortable), outletsOnly = true), profiles).rooms.map { it.classroom.id })
    }

    @Test fun capacityBuildingAndClassroomNameCanFilter() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(note = "后排靠窗"))
        assertEquals(listOf(a.id), ClassroomFilter.search(snapshot,
            SearchFilters((1..1).toSet(), buildings = setOf("2"), query = "201", minimumCapacity = 100), profiles).rooms.map { it.classroom.id })
    }

    @Test fun searchMatchesOnlyClassroomNamesAndNeverNotes() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(note = "后排靠窗"))
        assertTrue(ClassroomFilter.search(snapshot, SearchFilters(query = "靠窗"), profiles).rooms.isEmpty())
        assertEquals(listOf(a.id), ClassroomFilter.search(snapshot, SearchFilters(query = " 201 "), profiles)
            .rooms.map { it.classroom.id })
        val englishRoom = a.copy(name = "Lab-A101")
        val englishSnapshot = snapshot.copy(slots = listOf(snapshot.slots.first().copy(classrooms = listOf(englishRoom))))
        assertEquals(listOf(englishRoom.id), ClassroomFilter.search(englishSnapshot, SearchFilters(query = "lab-a"), emptyMap())
            .rooms.map { it.classroom.id })
    }

    @Test fun missingOrInvalidPeriodsCannotProduceAvailability() {
        assertTrue(ClassroomFilter.search(snapshot, SearchFilters((1..4).toSet()), emptyMap()).missingSlots)
        assertTrue(ClassroomFilter.search(snapshot, SearchFilters(setOf(0)), emptyMap()).rooms.isEmpty())
    }

    @Test fun favoriteOnlyReallyRequiresFavorite() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(favorite = true))
        assertEquals(listOf(a.id), ClassroomFilter.search(snapshot, SearchFilters((1..1).toSet(), favoritesOnly = true), profiles)
            .rooms.map { it.classroom.id })
    }

    @Test fun snapshotDoesNotBecomeTodayAfterMidnight() {
        assertTrue(snapshot.isCurrentOn(LocalDate.of(2026, 10, 11)))
        assertFalse(snapshot.isCurrentOn(LocalDate.of(2026, 10, 12)))
    }

    @Test fun buildingsAreUnionAndTagsAreIntersection() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(outlets = true,
            tags = setOf(PersonalTags.comfortable, PersonalTags.uncrowded, "靠窗")),
            b.id to b.emptyProfile().copy(outlets = true, tags = setOf(PersonalTags.comfortable)))
        val filters = SearchFilters(setOf(1), buildings = setOf("2", "3"))
        assertEquals(setOf(a.id, b.id), ClassroomFilter.search(snapshot, filters, profiles).rooms.map { it.classroom.id }.toSet())
        assertEquals(listOf(a.id), ClassroomFilter.search(snapshot, filters.copy(outletsOnly = true,
            tags = setOf(PersonalTags.comfortable, PersonalTags.uncrowded, "靠窗")), profiles).rooms.map { it.classroom.id })
    }

    @Test fun unmarkingLastBuildingRestoresAllBuildings() {
        val selected = SearchFilters(setOf(1)).toggleBuilding("2").toggleBuilding("3")
        assertEquals(setOf("2", "3"), selected.buildings)
        val all = selected.toggleBuilding("2").toggleBuilding("3")
        assertTrue(all.buildings.isEmpty())
        assertEquals(3, ClassroomFilter.search(snapshot, all, emptyMap()).rooms.size)
    }

    @Test fun legacyQuietMarkStillMatchesCustomQuietTag() {
        val profiles = mapOf(a.id to a.emptyProfile().copy(quiet = true))
        assertEquals(listOf(a.id), ClassroomFilter.search(snapshot,
            SearchFilters(setOf(1), tags = setOf(PersonalTags.legacyQuiet)), profiles).rooms.map { it.classroom.id })
    }
}
