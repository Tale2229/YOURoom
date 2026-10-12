package com.example.youroom.domain

/** IDs verified against the school's published classroom frontend. */
enum class Campus(val label: String, val dataKey: String, val verifiedApiId: String?) {
    XITUCHENG("西土城", "01", "01"),
    SHAHE("沙河", "04", "04");

    val connected: Boolean get() = verifiedApiId != null
}
