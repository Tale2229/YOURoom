package com.example.youroom

import androidx.datastore.preferences.core.*
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

class AppearancePreferencesTest {
    @Test fun defaultsKeepTheOriginalCampusPalettesAndIndependentChoices() {
        val original = AppearancePreferences()
        assertEquals(CampusTone.SAND, original.tone(Campus.XITUCHENG))
        assertEquals(CampusTone.PINE, original.tone(Campus.SHAHE))
        val changed = original.withTone(Campus.XITUCHENG, CampusTone.DUNHUANG)
        assertEquals(CampusTone.PINE, changed.shaheTone)
        assertEquals(CampusTone.DUNHUANG, changed.westTone)
        assertEquals(Campus.XITUCHENG, changed.defaultCampus)
    }

    @Test fun previousAndNextWrapWithoutChangingTheStableStorageIds() {
        assertEquals(CampusTone.MONDRIAN, CampusTone.SAND.step(-1))
        assertEquals(CampusTone.MATISSE, CampusTone.PINE.step(1))
        assertEquals(CampusTone.DUNHUANG, CampusTone.MATISSE.step(1))
        assertEquals(CampusTone.OLD_PORT, CampusTone.DUNHUANG.step(1))
        assertEquals(CampusTone.PEACH_PLUM, CampusTone.OLD_PORT.step(1))
        assertEquals(CampusTone.BURGUNDY, CampusTone.PEACH_PLUM.step(1))
        assertEquals(CampusTone.MORANDI, CampusTone.BURGUNDY.step(1))
        assertEquals(CampusTone.RED_BEAN, CampusTone.MORANDI.step(1))
        assertEquals(CampusTone.AUTUMN_CITRUS, CampusTone.RED_BEAN.step(1))
        assertEquals(CampusTone.MORNING_BELL, CampusTone.AUTUMN_CITRUS.step(1))
        CampusTone.entries.forEach { assertEquals(it, it.step(1).step(-1)) }
        assertEquals(listOf("sand", "pine", "matisse", "dunhuang", "old_port", "peach_plum", "burgundy", "morandi", "red_bean", "autumn_citrus", "morning_bell", "mondrian"), CampusTone.entries.map { it.id })
    }

    @Test fun storageRoundTripKeepsFiltersTagsAndStripPreferencesUntouched() {
        val values = mutablePreferencesOf(SharedFilterPreferences.favorites to true,
            TagCatalogPreferences.order to "[\"已收藏\",\"靠窗\"]", PeriodStripPreferenceKeys.numbers to true)
        val pref = AppearancePreferences(CampusTone.MATISSE, CampusTone.DUNHUANG, Campus.SHAHE)
        AppearancePreferenceKeys.write(values, pref)
        assertEquals(pref, AppearancePreferenceKeys.read(values))
        assertEquals(true, values[SharedFilterPreferences.favorites])
        assertEquals(true, values[PeriodStripPreferenceKeys.numbers])
        assertEquals("[\"已收藏\",\"靠窗\"]", values[TagCatalogPreferences.order])
    }

    @Test fun missingOrUnknownStoredValuesFallBackIndependently() {
        assertEquals(AppearancePreferences(), AppearancePreferenceKeys.read(emptyPreferences()))
        val values = mutablePreferencesOf(AppearancePreferenceKeys.west to "unknown",
            AppearancePreferenceKeys.shahe to "dunhuang", AppearancePreferenceKeys.defaultCampus to "bad")
        assertEquals(AppearancePreferences(shaheTone = CampusTone.DUNHUANG), AppearancePreferenceKeys.read(values))
    }

    @Test fun retiredPalettesFallBackToCampusDefaultsWithoutChangingOtherSettings() {
        for (retired in listOf("slate", "clay")) {
            val values = mutablePreferencesOf(AppearancePreferenceKeys.west to retired,
                AppearancePreferenceKeys.shahe to "mondrian", AppearancePreferenceKeys.defaultCampus to Campus.SHAHE.dataKey)
            assertEquals(AppearancePreferences(CampusTone.SAND, CampusTone.MONDRIAN, Campus.SHAHE),
                AppearancePreferenceKeys.read(values))
            values[AppearancePreferenceKeys.west] = "matisse"
            values[AppearancePreferenceKeys.shahe] = retired
            assertEquals(AppearancePreferences(CampusTone.MATISSE, CampusTone.PINE, Campus.SHAHE),
                AppearancePreferenceKeys.read(values))
        }
    }

    @Test fun unsetModeKeepsFollowingSystemAndManualModeOverridesBothSystemStates() {
        val original = AppearancePreferences()
        assertNull(original.darkMode)
        assertFalse(original.isDark(false)); assertTrue(original.isDark(true))
        for (system in listOf(false, true)) {
            assertFalse(original.copy(darkMode = false).isDark(system))
            assertTrue(original.copy(darkMode = true).isDark(system))
        }
    }

    @Test fun darkModeRoundTripsIndependentlyFromCampusPalettesAndOtherSettings() {
        val values = mutablePreferencesOf(SharedFilterPreferences.favorites to true,
            PeriodStripPreferenceKeys.numbers to true)
        val original = AppearancePreferences(CampusTone.MATISSE, CampusTone.MONDRIAN, Campus.SHAHE)
        for (mode in listOf(true, false, null)) {
            val choice = original.copy(darkMode = mode)
            AppearancePreferenceKeys.write(values, choice)
            assertEquals(choice, AppearancePreferenceKeys.read(values))
            assertEquals(mode, values[AppearancePreferenceKeys.darkMode])
            assertEquals(true, values[SharedFilterPreferences.favorites])
            assertEquals(true, values[PeriodStripPreferenceKeys.numbers])
        }
    }

    @Test fun legacyAppearanceWithoutModeKeepsStoredCampusChoices() {
        val stored = mutablePreferencesOf(AppearancePreferenceKeys.west to "morandi",
            AppearancePreferenceKeys.shahe to "dunhuang")
        val choice = AppearancePreferenceKeys.read(stored)
        assertNull(choice.darkMode)
        assertEquals(CampusTone.MORANDI, choice.westTone)
        assertEquals(CampusTone.DUNHUANG, choice.shaheTone)
        assertTrue(choice.withTone(Campus.SHAHE, CampusTone.PINE).isDark(true))
        assertFalse(choice.withTone(Campus.XITUCHENG, CampusTone.SAND).isDark(false))
    }

    @Test fun mosaicTravelsDiagonallyAndAllTilesFinishWithinOneAnimation() {
        for (rows in listOf(4, 10, 24)) for (r in 0 until rows) for (c in 0 until 6) {
            assertEquals(0f, ThemePageWave.phase(0f, r, c, rows, 6), .00001f)
            assertEquals(1f, ThemePageWave.phase(1f, r, c, rows, 6), .00001f)
            assertTrue(ThemePageWave.phase(.4f, 0, 0, rows, 6) >= ThemePageWave.phase(.4f, r, c, rows, 6))
            if (r > 0) assertTrue(ThemePageWave.phase(.4f, r - 1, c, rows, 6) >= ThemePageWave.phase(.4f, r, c, rows, 6))
        }
        assertTrue(ThemePageWave.phase(.4f, 0, 0, 10, 6) > ThemePageWave.phase(.4f, 9, 5, 10, 6))
        assertEquals(0f, ThemePageWave.blend(0f)); assertEquals(1f, ThemePageWave.blend(1f))
        assertEquals(.5f, ThemePageWave.blend(.5f), .0001f)
        assertEquals(320L, ThemePageWave.debounceMillis)
    }
}
