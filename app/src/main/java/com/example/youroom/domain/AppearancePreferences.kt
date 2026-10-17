package com.example.youroom.domain

/** Stable storage IDs; artwork and names may evolve without losing a campus choice. */
enum class CampusTone(val id: String, val title: String) {
    SAND("sand", "砂岩"), PINE("pine", "松绿"), SLATE("slate", "岩灰"), CLAY("clay", "陶土");
    fun step(direction: Int) = entries[Math.floorMod(ordinal + direction, entries.size)]
    companion object {
        fun default(campus: Campus) = if (campus == Campus.XITUCHENG) SAND else PINE
    }
}

data class AppearancePreferences(
    val westTone: CampusTone = CampusTone.SAND,
    val shaheTone: CampusTone = CampusTone.PINE,
    val defaultCampus: Campus = Campus.XITUCHENG,
) {
    fun tone(campus: Campus) = if (campus == Campus.XITUCHENG) westTone else shaheTone
    fun withTone(campus: Campus, tone: CampusTone) = if (campus == Campus.XITUCHENG)
        copy(westTone = tone) else copy(shaheTone = tone)
}

internal object ThemeMosaicWave {
    const val grid = 6
    const val durationMillis = 560
    fun phase(progress: Float, row: Int, column: Int): Float =
        ((progress.coerceIn(0f, 1f) * durationMillis - (row + column) * 30f) / 260f).coerceIn(0f, 1f)
    fun blend(phase: Float): Float {
        val p = ((phase - .2f) / .6f).coerceIn(0f, 1f)
        return p * p * (3 - 2 * p)
    }
}
