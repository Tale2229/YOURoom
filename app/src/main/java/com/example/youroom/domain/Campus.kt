package com.example.youroom.domain

/** Presentation identity is separate from the school's as-yet-unverified campus codes. */
enum class Campus(val label: String, val dataKey: String, val verifiedApiId: String?) {
    XITUCHENG("西土城", "01", "01"),
    SHAHE("沙河", "shahe", null);

    val connected: Boolean get() = verifiedApiId != null
}
