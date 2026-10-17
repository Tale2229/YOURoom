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
        for (r in 0 until 6) for (c in 0 until 6) {
            assertEquals(0f, ThemeMosaicWave.phase(0f, r, c))
            assertEquals(1f, ThemeMosaicWave.phase(1f, r, c))
            assertEquals(ThemeMosaicWave.phase(.4f, r, c), ThemeMosaicWave.phase(.4f, c, r))
            assertTrue(ThemeMosaicWave.phase(.4f, 0, 0) >= ThemeMosaicWave.phase(.4f, r, c))
        }
        assertTrue(ThemeMosaicWave.phase(.4f, 0, 0) > ThemeMosaicWave.phase(.4f, 5, 5))
        assertEquals(0f, ThemeMosaicWave.blend(0f)); assertEquals(1f, ThemeMosaicWave.blend(1f))
        assertEquals(.5f, ThemeMosaicWave.blend(.5f), .0001f)
    }
}
