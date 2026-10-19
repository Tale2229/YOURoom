package com.example.youroom.domain

/** Stable storage IDs; artwork and names may evolve without losing a campus choice. */
enum class CampusTone(val id: String, val title: String) {
    SAND("sand", "砂岩"), PINE("pine", "松绿"),
    MATISSE("matisse", "马蒂斯"), DUNHUANG("dunhuang", "敦煌"), OLD_PORT("old_port", "旧港来信"),
    PEACH_PLUM("peach_plum", "蜜桃乌梅"), BURGUNDY("burgundy", "勃艮第"), MORANDI("morandi", "莫兰迪"),
    RED_BEAN("red_bean", "入骨相思"), AUTUMN_CITRUS("autumn_citrus", "橙黄橘绿时"),
    MORNING_BELL("morning_bell", "晨钟暮鼓"), MONDRIAN("mondrian", "蒙德里安");
    fun step(direction: Int) = entries[Math.floorMod(ordinal + direction, entries.size)]
    companion object {
        fun default(campus: Campus) = if (campus == Campus.XITUCHENG) SAND else PINE
    }
}

data class AppearancePreferences(
    val westTone: CampusTone = CampusTone.SAND,
    val shaheTone: CampusTone = CampusTone.PINE,
    val defaultCampus: Campus = Campus.XITUCHENG,
    // Null preserves the existing system-following behavior until the first choice.
    val darkMode: Boolean? = null,
) {
    fun isDark(systemDark: Boolean) = darkMode ?: systemDark
    fun tone(campus: Campus) = if (campus == Campus.XITUCHENG) westTone else shaheTone
    fun withTone(campus: Campus, tone: CampusTone) = if (campus == Campus.XITUCHENG)
        copy(westTone = tone) else copy(shaheTone = tone)
}

internal object ThemePageWave {
    const val debounceMillis = 320L
    const val durationMillis = 760
    fun phase(progress: Float, row: Int, column: Int, rows: Int, columns: Int): Float {
        val distance = (row + column).toFloat() / (rows + columns - 2).coerceAtLeast(1)
        return ((progress.coerceIn(0f, 1f) - distance * .62f) / .38f).coerceIn(0f, 1f)
    }
    fun blend(phase: Float): Float {
        val p = ((phase - .2f) / .6f).coerceIn(0f, 1f)
        return p * p * (3 - 2 * p)
    }
}
