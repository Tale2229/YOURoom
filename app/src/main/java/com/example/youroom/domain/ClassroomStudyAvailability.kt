package com.example.youroom.domain

/** School availability, independent of the user's study / rest schedule.
 * All known inter-lesson breaks are usable, including lunch, even when this room
 * is occupied in either neighboring lesson. Missing lessons are never fabricated.
 */
internal class ClassroomStudyAvailability(slots: List<LessonSlot>) {
    private val lessons = slots.sortedBy { it.number }
    private val breaks = lessons.zipWithNext().mapNotNull { (before, after) ->
        val start = before.end.hour * 60 + before.end.minute
        val end = after.start.hour * 60 + after.start.minute
        if (after.number == before.number + 1 && end > start) StudyWindow(start, end) else null
    }

    fun intervalsFor(freeSlots: Set<Int>): List<StudyWindow> {
        val intervals = (breaks + lessons.filter { it.number in freeSlots }.map {
            StudyWindow(it.start.hour * 60 + it.start.minute, it.end.hour * 60 + it.end.minute)
        }).filter { it.duration > 0 }.sortedWith(compareBy<StudyWindow> { it.start }.thenBy { it.end })
        val merged = mutableListOf<StudyWindow>()
        for (interval in intervals) {
            if (merged.isNotEmpty() && merged.last().end >= interval.start) {
                val previous = merged.removeAt(merged.lastIndex)
                merged += StudyWindow(previous.start, maxOf(previous.end, interval.end))
            } else merged += interval
        }
        return merged
    }
}
