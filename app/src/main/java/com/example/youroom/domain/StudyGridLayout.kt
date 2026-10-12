package com.example.youroom.domain

enum class StudyGridCellKind { SLOT, SPACER, SEARCH, FILTER }
data class StudyGridCell(val kind: StudyGridCellKind, val number: Int? = null, val span: Int = 1)

/** Six columns keep the search under periods 10/11 and the filter under period 12. */
object StudyGridLayout {
    const val columns = 6

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
}
