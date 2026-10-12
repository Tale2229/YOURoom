package com.example.youroom

import androidx.datastore.preferences.core.*
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CampusFilterMigrationTest {
    @Test fun legacyPreferencesMoveToWestOnlyAndAreNotDeleted() = runTest {
        val old = mutablePreferencesOf(stringPreferencesKey("building") to "2",
            booleanPreferencesKey("favorites_only") to true, booleanPreferencesKey("quiet_only") to true,
            intPreferencesKey("minimum_capacity") to 100)
        val migration = CampusFilterMigration()
        assertTrue(migration.shouldMigrate(old))
        val migrated = migration.migrate(old)
        val west = CampusFilterPreferences.read(migrated, Campus.XITUCHENG)
        assertEquals("2", west.building)
        assertTrue(west.favoritesOnly && west.quietOnly)
        assertEquals(100, west.minimumCapacity)
        assertEquals(SearchFilters(), CampusFilterPreferences.read(migrated, Campus.SHAHE))
        assertEquals("2", migrated[stringPreferencesKey("building")])
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test fun scopedPreferencesDoNotAffectTheOtherCampus() {
        val values = mutablePreferencesOf()
        CampusFilterPreferences.write(values, Campus.XITUCHENG, SearchFilters(building = "2", favoritesOnly = true))
        CampusFilterPreferences.write(values, Campus.SHAHE, SearchFilters(building = "教一", quietOnly = true))
        assertEquals("2", CampusFilterPreferences.read(values, Campus.XITUCHENG).building)
        assertEquals("教一", CampusFilterPreferences.read(values, Campus.SHAHE).building)
        CampusFilterPreferences.write(values, Campus.XITUCHENG, SearchFilters())
        assertNull(CampusFilterPreferences.read(values, Campus.XITUCHENG).building)
        assertEquals("教一", CampusFilterPreferences.read(values, Campus.SHAHE).building)
    }

    @Test fun migrationWorksWhenThereAreNoLegacyValues() = runTest {
        val values = CampusFilterMigration().migrate(emptyPreferences())
        assertEquals(SearchFilters(), CampusFilterPreferences.read(values, Campus.XITUCHENG))
        assertEquals(SearchFilters(), CampusFilterPreferences.read(values, Campus.SHAHE))
    }
}
