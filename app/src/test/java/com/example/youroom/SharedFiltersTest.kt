package com.example.youroom

import androidx.datastore.preferences.core.*
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SharedFiltersTest {
    @Test fun chipBadgeIncludesEachBuildingAndTagButNotAllBuildingsOrNumericFields() {
        assertEquals(0, SearchFilters(minimumCapacity = 100, query = "靠窗", selectedSlots = setOf(1, 2)).badgeCount)
        assertEquals(7, SearchFilters(buildings = setOf("2", "3"), favoritesOnly = true, outletsOnly = true,
            tags = setOf(PersonalTags.comfortable, PersonalTags.uncrowded, "靠窗")).badgeCount)
    }

    @Test fun sharedMigrationPreservesWestAttributesAndLegacyQuietWithoutCampusBuilding() = runTest {
        val values = mutablePreferencesOf()
        CampusFilterPreferences.write(values, Campus.XITUCHENG,
            SearchFilters(buildings = setOf("2"), favoritesOnly = true, outletsOnly = true,
                tags = setOf(PersonalTags.legacyQuiet), minimumCapacity = 60))
        CampusFilterPreferences.write(values, Campus.SHAHE, SearchFilters())
        val migration = SharedFilterMigration()
        val migrated = migration.migrate(values)
        val shared = SharedFilterPreferences.read(migrated)
        assertTrue(shared.buildings.isEmpty())
        assertTrue(shared.favoritesOnly && shared.outletsOnly)
        assertEquals(setOf(PersonalTags.legacyQuiet), shared.tags)
        assertEquals(60, shared.minimumCapacity)
        assertEquals(setOf(PersonalTags.legacyQuiet), migrated[SharedFilterPreferences.definitions])
        assertEquals("2", migrated[CampusFilterPreferences.building(Campus.XITUCHENG)])
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test fun sharedPersistenceDoesNotStoreSelectionQueryOrBuildings() {
        val values = mutablePreferencesOf()
        SharedFilterPreferences.write(values, SearchFilters(setOf(1, 3), buildings = setOf("2"), query = "201",
            favoritesOnly = true, tags = setOf("靠窗")))
        assertEquals(SearchFilters(favoritesOnly = true, tags = setOf("靠窗")), SharedFilterPreferences.read(values))
    }

    @Test fun customTagValidationRejectsBlankDuplicatesBuiltInsAndOverlongNames() {
        assertNull(PersonalTags.validate(" 靠窗 ", emptySet()))
        listOf("", "已收藏", "有插座", "桌椅舒适", "人少", "全部楼宇", "靠窗", "a".repeat(21), "前排\n后排")
            .forEach { assertNotNull(PersonalTags.validate(it, setOf("靠窗"))) }
    }

    @Test fun newTagsRoundTripWithoutLosingExistingProfileFields() {
        val profile = RoomProfile("04:教一-101", "教一-101", "教一", favorite = true, excluded = true,
            outlets = true, note = "后排", tags = setOf(PersonalTags.comfortable, "靠窗"))
        assertEquals(profile, profile.toEntity().toDomain())
        val legacy = ProfileEntity("01:2-201", "2-201", "2", true, false, true, false, "原备注").toDomain()
        assertTrue(legacy.favorite)
        assertEquals("原备注", legacy.note)
        assertEquals(setOf(PersonalTags.legacyQuiet), legacy.allTags)
    }
}
