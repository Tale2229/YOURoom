package com.example.youroom.domain

import java.time.Instant
import java.time.LocalDate

/** One completed request, including its whole deduplicated portfolio, not one selected route. */
data class PlanningHistoryRecord(
    val id: String,
    val owner: String,
    val completedAt: Instant,
    val date: LocalDate,
    val campus: Campus,
    val goal: PlanningGoal,
    val snapshot: ClassroomSnapshot,
    val result: PlanningResult,
    val selectedOption: Int,
    val defaultDirection: PlanningDirection,
    val inputKey: String? = null,
)

object PlanningHistoryPolicy {
    const val Limit = 10
    fun current(records: List<PlanningHistoryRecord>, date: LocalDate): List<PlanningHistoryRecord> =
        records.filter { it.date == date && it.snapshot.queryDate == date }
            .distinctBy { it.id }.groupBy { it.owner }.values.flatMap { account ->
                account.sortedByDescending { it.completedAt }
                    .distinctBy { it.inputKey?.let { key -> "${it.campus}:$key" } ?: "legacy:${it.id}" }.take(Limit)
            }.sortedByDescending { it.completedAt }
}
