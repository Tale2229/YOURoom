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
        val changed = original.withTone(Campus.XITUCHENG, CampusTone.CLAY)
        assertEquals(CampusTone.PINE, changed.shaheTone)
        assertEquals(CampusTone.CLAY, changed.westTone)
        assertEquals(Campus.XITUCHENG, changed.defaultCampus)
    }

    @Test fun previousAndNextWrapWithoutChangingTheStableStorageIds() {
        assertEquals(CampusTone.CLAY, CampusTone.SAND.step(-1))
        assertEquals(CampusTone.SAND, CampusTone.CLAY.step(1))
        CampusTone.entries.forEach { assertEquals(it, it.step(1).step(-1)) }
        assertEquals(listOf("sand", "pine", "slate", "clay"), CampusTone.entries.map { it.id })
    }

    @Test fun storageRoundTripKeepsFiltersTagsAndStripPreferencesUntouched() {
        val values = mutablePreferencesOf(SharedFilterPreferences.favorites to true,
            TagCatalogPreferences.order to "[\"已收藏\",\"靠窗\"]", PeriodStripPreferenceKeys.numbers to true)
        val pref = AppearancePreferences(CampusTone.SLATE, CampusTone.CLAY, Campus.SHAHE)
        AppearancePreferenceKeys.write(values, pref)
        assertEquals(pref, AppearancePreferenceKeys.read(values))
        assertEquals(true, values[SharedFilterPreferences.favorites])
        assertEquals(true, values[PeriodStripPreferenceKeys.numbers])
        assertEquals("[\"已收藏\",\"靠窗\"]", values[TagCatalogPreferences.order])
    }

    @Test fun missingOrUnknownStoredValuesFallBackIndependently() {
        assertEquals(AppearancePreferences(), AppearancePreferenceKeys.read(emptyPreferences()))
        val values = mutablePreferencesOf(AppearancePreferenceKeys.west to "unknown",
            AppearancePreferenceKeys.shahe to "clay", AppearancePreferenceKeys.defaultCampus to "bad")
        assertEquals(AppearancePreferences(shaheTone = CampusTone.CLAY), AppearancePreferenceKeys.read(values))
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
