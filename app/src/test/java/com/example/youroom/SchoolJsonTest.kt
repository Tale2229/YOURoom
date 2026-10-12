package com.example.youroom

import com.example.youroom.data.InvalidSchoolResponse
import com.example.youroom.data.SchoolJson
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SchoolJsonTest {
    @Test(expected = com.example.youroom.data.InvalidSchoolResponse::class)
    fun nullClassroomListIsNotTreatedAsARoomNamedNull() {
        com.example.youroom.data.SchoolJson.snapshot(
            """{"code":"1","data":[{"NODENAME":"1","NODETIME":"08:00-08:45","CLASSROOMS":null}]}""",
            "01", java.time.LocalDate.of(2026, 10, 11), java.time.Instant.parse("2026-10-11T01:00:00Z"))
    }
    private fun parse(rows: String) = SchoolJson.snapshot("""{"code":"1","Msg":"success","data":[$rows]}""",
        "01", LocalDate.of(2026, 10, 11), Instant.EPOCH)

    @Test fun parsesStringFieldsAndEmptyClassroomList() {
        val snapshot = parse("""{"NODENAME":"1","NODETIME":"08:00-08:45","CLASSROOMS":"2-201(180),未来学习大楼-202-203(60)"},
            {"NODENAME":"2","NODETIME":"08:50-09:35","CLASSROOMS":""}""")
        assertEquals(2, snapshot.slots.size)
        assertEquals(2, snapshot.classrooms.size)
        assertTrue(snapshot.slots[1].classrooms.isEmpty())
    }

    @Test fun deduplicatesRoomsWithinOnePeriod() {
        assertEquals(1, parse("""{"NODENAME":"1","NODETIME":"08:00-08:45","CLASSROOMS":"2-201(180),2-201(180)"}""")
            .classrooms.size)
    }

    @Test(expected = InvalidSchoolResponse::class) fun rejectsDuplicatePeriods() {
        parse("""{"NODENAME":"1","NODETIME":"08:00-08:45","CLASSROOMS":""},
            {"NODENAME":"1","NODETIME":"08:50-09:35","CLASSROOMS":""}""")
    }

    @Test(expected = InvalidSchoolResponse::class) fun rejectsChangedSchema() {
        parse("""{"period":1,"rooms":[]}""")
    }

    @Test(expected = InvalidSchoolResponse::class) fun rejectsOverlappingPeriodTimes() {
        parse("""{"NODENAME":"1","NODETIME":"08:00-08:45","CLASSROOMS":""},
            {"NODENAME":"2","NODETIME":"08:40-09:35","CLASSROOMS":""}""")
    }
}
