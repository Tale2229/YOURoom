package com.example.youroom.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

val SchoolZone: ZoneId = ZoneId.of("Asia/Shanghai")

data class Classroom(val id: String, val name: String, val building: String, val capacity: Int?) {
    val buildingLabel: String get() = if (building.all(Char::isDigit)) "教学楼 $building" else building
}

data class LessonSlot(
    val number: Int,
    val start: LocalTime,
    val end: LocalTime,
    val classrooms: List<Classroom>,
) {
    val timeLabel: String get() = "$start–$end"
}

data class ClassroomSnapshot(
    val campusId: String,
    val queryDate: LocalDate,
    val fetchedAt: Instant,
    val slots: List<LessonSlot>,
    val isDemo: Boolean = false,
) {
    val classrooms: List<Classroom> get() = slots.flatMap { it.classrooms }.distinctBy { it.id }
    fun isCurrentOn(date: LocalDate) = queryDate == date
}

data class RoomProfile(
    val roomId: String,
    val name: String,
    val building: String,
    val favorite: Boolean = false,
    val excluded: Boolean = false,
    val quiet: Boolean = false,
    val outlets: Boolean = false,
    val note: String = "",
    val tags: Set<String> = emptySet(),
)

data class SearchFilters(
    val selectedSlots: Set<Int> = emptySet(),
    val buildings: Set<String> = emptySet(),
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val outletsOnly: Boolean = false,
    val tags: Set<String> = emptySet(),
    val minimumCapacity: Int = 0,
) {
    // Capacity is a numeric constraint, not a selected chip.
    val badgeCount: Int get() = buildings.size + tags.size +
        (if (favoritesOnly) 1 else 0) + (if (outletsOnly) 1 else 0)
    fun toggleBuilding(building: String) = copy(buildings =
        if (building in buildings) buildings - building else buildings + building)
}

data class RoomMatch(val classroom: Classroom, val profile: RoomProfile, val freeSlots: Set<Int>)

data class SearchResult(
    val rooms: List<RoomMatch>,
    val selectedSlots: List<LessonSlot>,
    val hasUncoveredGaps: Boolean,
    val missingSlots: Boolean = false,
)

fun Classroom.emptyProfile() = RoomProfile(id, name, building)
