package com.example.youroom.data

import com.example.youroom.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Deliberately synthetic availability. Never persisted or presented as school data. */
object DemoData {
    fun snapshot(campus: Campus = Campus.XITUCHENG): ClassroomSnapshot {
        val names = if (campus == Campus.SHAHE) listOf("演示楼-A101(60)", "演示楼-A102(80)",
            "演示楼-A201(100)", "演示楼-A202(60)", "演示楼-B101(80)", "演示楼-B102(60)")
        else listOf("2-201(180)", "2-240(180)", "2-306(64)", "2-435(58)",
            "3-108(56)", "3-205(68)", "3-208(98)", "4-201(80)", "4-302(60)",
            "未来学习大楼-101(40)", "未来学习大楼-202-203(60)", "未来学习大楼-217-218(60)")
        val rooms = names.map { SchoolJson.classroom(it, campus.dataKey) }
        val times = listOf("08:00-08:45", "08:50-09:35", "09:50-10:35", "10:40-11:25",
            "11:30-12:15", "13:00-13:45", "13:50-14:35", "14:45-15:30", "15:40-16:25",
            "16:35-17:20", "17:25-18:10", "18:30-19:15", "19:20-20:05", "20:10-20:55")
        val slots = times.mapIndexed { index, value ->
            val (start, end) = value.split('-')
            LessonSlot(index + 1, LocalTime.parse(start), LocalTime.parse(end),
                rooms.filterIndexed { roomIndex, _ -> roomIndex < 3 || (roomIndex + index) % 4 != 0 })
        }
        return ClassroomSnapshot(campus.dataKey, LocalDate.now(SchoolZone), Instant.now(), slots, isDemo = true)
    }
}
