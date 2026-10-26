package com.example.youroom.domain

enum class PeriodStripLayout(val title: String) {
    EQUAL("等间距"), TIME_PROPORTIONAL("时间等比例")
}

/** App-wide presentation only; never part of a search or planning input. */
data class PeriodStripPreferences(
    val layout: PeriodStripLayout = PeriodStripLayout.EQUAL,
    val showNumbers: Boolean = false,
)

internal data class PeriodBar(val start: Float, val end: Float) {
    val center get() = (start+end)/2
    val width get() = end-start
}
