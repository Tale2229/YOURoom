package com.example.youroom.domain

import java.security.MessageDigest
import java.time.LocalDate

/** Stable identity of effective solver inputs, without persisting notes or full responses.
 * Bump the namespace when a planner policy/algorithm change invalidates saved portfolios. */
object PlanningRequestIdentity {
    fun of(snapshot: ClassroomSnapshot,campus: Campus,date: LocalDate,
        profiles: Map<String,RoomProfile>,goal: PlanningGoal): String {
        val digest=MessageDigest.getInstance("SHA-256")
        fun field(value: Any?) {
            val bytes=(value?.toString() ?: "<null>").toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8));digest.update(bytes)
        }
        field("planning-input-v1");field(campus.name);field(date);field(snapshot.campusId)
        field(snapshot.queryDate);field(snapshot.isDemo)
        field(goal.flexible);field(goal.origin)
        field(goal.windows.size)
        goal.windows.sortedWith(compareBy<StudyWindow> { it.start }.thenBy { it.end }).forEach { field(it.start);field(it.end) }
        field(goal.expectedRooms.size);goal.expectedRooms.sorted().forEach(::field)
        field(snapshot.slots.size)
        snapshot.slots.sortedWith(compareBy<LessonSlot> { it.number }.thenBy { it.start }.thenBy { it.end }).forEach { slot ->
            field(slot.number);field(slot.start);field(slot.end);field(slot.classrooms.size)
            slot.classrooms.sortedBy { it.id }.forEach { room ->
                field(room.id);field(room.name);field(room.building);field(room.capacity)
                field(profiles[room.id]?.favorite==true);field(profiles[room.id]?.excluded==true)
            }
        }
        // Fetch timestamps, notes, tag order and default display direction are not solver inputs.
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
