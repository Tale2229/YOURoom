package com.example.youroom.data

import com.example.youroom.domain.Classroom
import com.example.youroom.domain.ClassroomSnapshot
import com.example.youroom.domain.LessonSlot
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class InvalidSchoolResponse : Exception("教务数据格式发生变化，请稍后重试。")

object SchoolJson {
    private val roomPattern = Regex("^(.+?)[(（](\\d+)[)）]$")

    fun response(text: String): JsonObject = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (_: Exception) { throw InvalidSchoolResponse() }

    fun snapshot(
        text: String,
        campusId: String,
        queryDate: LocalDate,
        fetchedAt: Instant,
    ): ClassroomSnapshot = try {
        val root = response(text)
        if (root["code"]?.jsonPrimitive?.content != "1") throw InvalidSchoolResponse()
        val slots = root["data"]!!.jsonArray.map { element ->
            val row = element.jsonObject
            val number = row["NODENAME"]!!.jsonPrimitive.content.toInt()
            val times = row["NODETIME"]!!.jsonPrimitive.content.replace('–', '-').split('-')
            require(number > 0 && times.size == 2)
            val start = LocalTime.parse(times[0].trim())
            val end = LocalTime.parse(times[1].trim())
            require(start < end)
            val classroomText = row["CLASSROOMS"]!!.jsonPrimitive
            require(classroomText.isString)
            val rooms = classroomText.content.replace('，', ',')
                .split(',').filter { it.isNotBlank() }.map { classroom(it.trim(), campusId) }.distinctBy { it.id }
            LessonSlot(number, start, end, rooms)
        }.sortedBy { it.number }
        require(slots.isNotEmpty() && slots.map { it.number }.distinct().size == slots.size)
        require(slots.zipWithNext().all { (a, b) -> a.end <= b.start })
        ClassroomSnapshot(campusId, queryDate, fetchedAt, slots)
    } catch (error: InvalidSchoolResponse) { throw error }
    catch (_: Exception) { throw InvalidSchoolResponse() }

    fun classroom(value: String, campusId: String): Classroom {
        val match = roomPattern.matchEntire(value)
        val name = (match?.groupValues?.get(1) ?: value).trim()
        require(name.isNotEmpty())
        return Classroom("$campusId:$name", name, name.substringBefore('-'),
            match?.groupValues?.get(2)?.toIntOrNull())
    }
}
