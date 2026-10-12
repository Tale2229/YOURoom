package com.example.youroom.domain

/** First tap anchors a single slot; the next closes the range in either direction. */
object SlotSelection {
    fun select(filters: SearchFilters, number: Int, choosingEnd: Boolean): SearchFilters {
        require(number > 0)
        return if (choosingEnd) filters.copy(
            firstSlot = minOf(filters.firstSlot, number), lastSlot = maxOf(filters.firstSlot, number))
        else filters.copy(firstSlot = number, lastSlot = number)
    }
}
