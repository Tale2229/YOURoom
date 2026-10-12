package com.example.youroom.domain

object SlotSelection {
    fun toggle(selection: Set<Int>, number: Int): Set<Int> {
        require(number > 0)
        return if (number in selection) selection - number else selection + number
    }

    fun groups(slots: List<LessonSlot>): List<List<LessonSlot>> {
        val groups = mutableListOf<MutableList<LessonSlot>>()
        for (slot in slots.sortedBy { it.number }) {
            if (groups.isEmpty() || groups.last().last().number + 1 != slot.number) groups += mutableListOf(slot)
            else groups.last() += slot
        }
        return groups
    }

    fun summary(slots: List<LessonSlot>): String = groups(slots).joinToString(" · ") {
        "${it.first().start}—${it.last().end}"
    }
}

/** Uniform painting; a pointer may visit a cell repeatedly without flipping it again. */
class SlotDragSelection(initial: Set<Int>, first: Int) {
    val initial: Set<Int> = initial.toSet()
    private val selecting = first !in initial
    private val visited = mutableSetOf<Int>()
    var selection: Set<Int> = this.initial
        private set

    init { visit(listOf(first)) }

    fun visit(numbers: Iterable<Int>): Set<Int> {
        for (number in numbers) {
            require(number > 0)
            if (visited.add(number)) selection = if (selecting) selection + number else selection - number
        }
        return selection
    }

    fun cancel(): Set<Int> = initial
}
