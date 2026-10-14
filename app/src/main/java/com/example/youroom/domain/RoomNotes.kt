package com.example.youroom.domain

/** Count Unicode code points, so supplementary characters never split in half. */
object RoomNotes {
    const val maximumLength = 20
    fun length(value: String): Int = value.codePointCount(0, value.length)
    fun limit(value: String): String = if (length(value) <= maximumLength) value
        else value.substring(0, value.offsetByCodePoints(0, maximumLength))
}
