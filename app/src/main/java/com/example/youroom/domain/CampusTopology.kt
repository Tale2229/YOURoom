package com.example.youroom.domain

import kotlin.math.abs

/** Approximate door-to-door minutes, not surveyed distances or GPS navigation. */
object CampusTopology {
    private val west = mapOf(
        setOf("1", "4") to 3, setOf("1", "main") to 3, setOf("1", "2") to 5,
        setOf("1", "3") to 6, setOf("2", "main") to 2, setOf("2", "3") to 4,
        setOf("2", "4") to 6, setOf("3", "4") to 5, setOf("3", "main") to 5,
        setOf("4", "main") to 5)

    fun building(raw: String): String = when (raw) {
        "主楼", "未来学习大楼" -> "main"
        "教一", "教一楼", "教学楼1" -> "1"
        "教二", "教二楼", "教学楼2" -> "2"
        "教三", "教三楼", "教学楼3" -> "3"
        "教四", "教四楼", "教学楼4" -> "4"
        else -> raw
    }

    fun label(building: String) = when (building) {
        "main" -> "主楼"
        "1", "2", "3", "4" -> "教学楼 $building"
        else -> building
    }

    fun location(room: Classroom, campus: Campus): RoomLocation {
        val suffix = room.name.substringAfter('-', "")
        val parsed = Regex("^([A-Za-z]*)([0-9]{3,4})(?:-[0-9]+)?$").matchEntire(suffix)
        val number = parsed?.groupValues?.get(2)?.toIntOrNull()
        val wing = parsed?.groupValues?.get(1)?.ifEmpty { null }
        val key = if (campus == Campus.SHAHE && room.building == "演示楼" && wing != null)
            "演示楼-$wing" else building(room.building)
        return RoomLocation(key, number?.div(100)?.takeIf { it in 1..30 }, number, wing)
    }

    fun doorMinutes(campus: Campus, first: String, second: String): Int {
        if (first == second) return 0
        return if (campus == Campus.SHAHE) 4 else west[setOf(first, second)] ?: 8
    }

    fun transferMinutes(campus: Campus, first: RoomLocation, second: RoomLocation): Int {
        if (first.building == second.building) {
            if (first.floor == null || second.floor == null) return 4
            if (first.floor != second.floor) return 2 + (abs(first.floor - second.floor) + 1) / 2
            val difference = if (first.number != null && second.number != null) abs(first.number - second.number) else 30
            return 1 + (if (difference > 5 || first.wing != second.wing) 1 else 0) + (if (difference > 25) 1 else 0)
        }
        val floors = ((first.floor ?: 3) - 1).coerceAtLeast(0) + ((second.floor ?: 3) - 1).coerceAtLeast(0)
        return 2 + doorMinutes(campus, first.building, second.building) + (floors + 1) / 2
    }

    fun approachMinutes(campus: Campus, origin: String?, target: RoomLocation): Int =
        if (origin == null) 0 else doorMinutes(campus, building(origin), target.building) +
            ((target.floor ?: 3) - 1).coerceAtLeast(0).let { (it + 1) / 2 }
}
