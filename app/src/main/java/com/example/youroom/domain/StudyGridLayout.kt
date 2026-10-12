package com.example.youroom.domain

import java.time.format.DateTimeFormatter
import java.util.Locale

enum class StudyGridCellKind { SLOT, SPACER, SEARCH, FILTER }
data class StudyGridCell(val kind: StudyGridCellKind, val number: Int? = null, val span: Int = 1)
data class StudyTimeMarker(val number: Int, val start: Boolean, val label: String)

/** Six columns keep the search under periods 10/11 and the filter under period 12. */
object StudyGridLayout {
    const val columns = 6
    private val timeFormat = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)

    fun rows(numbers: List<Int>): List<List<StudyGridCell>> {
        val rows = numbers.distinct().sorted().chunked(columns).map { row ->
            row.map { StudyGridCell(StudyGridCellKind.SLOT, it) }.toMutableList()
        }.toMutableList()
        if (rows.isEmpty() || rows.last().size > 3) {
            rows.lastOrNull()?.let { row ->
                repeat(columns - row.size) { row.add(StudyGridCell(StudyGridCellKind.SPACER)) }
            }
            rows.add(mutableListOf())
        }
        val last = rows.last()
        repeat(3 - last.size) { last.add(StudyGridCell(StudyGridCellKind.SPACER)) }
        last.add(StudyGridCell(StudyGridCellKind.SEARCH, span = 2))
        last.add(StudyGridCell(StudyGridCellKind.FILTER))
        return rows
    }

    /** Boundary labels use the campus response, never a hardcoded timetable. */
    fun markers(rows: List<List<StudyGridCell>>, slots: List<LessonSlot>): List<List<StudyTimeMarker?>> {
        val bands = List(rows.size + 1) { MutableList<StudyTimeMarker?>(columns) { null } }
        val byNumber = slots.associateBy { it.number }
        rows.forEachIndexed { rowIndex, row ->
            var column = 0
            row.forEach { cell ->
                byNumber[cell.number]?.let { slot ->
                    if (slot.number in setOf(1, 7, 13)) {
                        bands[rowIndex][column] = StudyTimeMarker(slot.number, true, slot.start.format(timeFormat))
                    }
                    if (slot.number in setOf(6, 12, 14)) {
                        bands[rowIndex + 1][column] = StudyTimeMarker(slot.number, false, slot.end.format(timeFormat))
                    }
                }
                column += cell.span
            }
        }
        return bands
    }
}
