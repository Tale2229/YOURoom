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
        assertEquals(setOf("2"), west.buildings)
        assertTrue(west.favoritesOnly && (PersonalTags.legacyQuiet in west.tags))
        assertEquals(100, west.minimumCapacity)
        assertEquals(SearchFilters(), CampusFilterPreferences.read(migrated, Campus.SHAHE))
        assertEquals("2", migrated[stringPreferencesKey("building")])
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test fun scopedPreferencesDoNotAffectTheOtherCampus() {
        val values = mutablePreferencesOf()
        CampusFilterPreferences.write(values, Campus.XITUCHENG, SearchFilters(buildings = setOf("2"), favoritesOnly = true))
        CampusFilterPreferences.write(values, Campus.SHAHE, SearchFilters(buildings = setOf("教一"), tags = setOf(PersonalTags.legacyQuiet)))
        assertEquals(setOf("2"), CampusFilterPreferences.read(values, Campus.XITUCHENG).buildings)
        assertEquals(setOf("教一"), CampusFilterPreferences.read(values, Campus.SHAHE).buildings)
        CampusFilterPreferences.write(values, Campus.XITUCHENG, SearchFilters())
        assertTrue(CampusFilterPreferences.read(values, Campus.XITUCHENG).buildings.isEmpty())
        assertEquals(setOf("教一"), CampusFilterPreferences.read(values, Campus.SHAHE).buildings)
    }

    @Test fun migrationWorksWhenThereAreNoLegacyValues() = runTest {
        val values = CampusFilterMigration().migrate(emptyPreferences())
        assertEquals(SearchFilters(), CampusFilterPreferences.read(values, Campus.XITUCHENG))
        assertEquals(SearchFilters(), CampusFilterPreferences.read(values, Campus.SHAHE))
    }
}
