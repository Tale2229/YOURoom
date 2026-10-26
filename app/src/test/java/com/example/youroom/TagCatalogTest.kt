package com.example.youroom

import androidx.datastore.preferences.core.*
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TagCatalogTest {
    @Test fun favoriteIsPermanentAndFixedAtTheFront() {
        val catalog = TagCatalog().deleted(PersonalTags.favorite).reordered(PersonalTags.defaultOrder.reversed())
        assertEquals(PersonalTags.favorite, catalog.available().first())
        assertEquals(1, catalog.available().count { it == PersonalTags.favorite })
        assertFalse(PersonalTags.favorite in catalog.removed)
        assertEquals(PersonalTags.defaultOrder, TagOrder.move(PersonalTags.defaultOrder, PersonalTags.favorite, PersonalTags.outlets))
        assertEquals(PersonalTags.defaultOrder, TagOrder.move(PersonalTags.defaultOrder, PersonalTags.outlets, PersonalTags.favorite))
    }

    @Test fun dragOrderMovesAcrossRowsAndReversalRestoresTheOriginalOrder() {
        val names = PersonalTags.defaultOrder + listOf("靠窗", "前排")
        val moved = TagOrder.move(names, PersonalTags.outlets, "前排")
        assertEquals(listOf("已收藏", "桌椅舒适", "人少", "靠窗", "前排", "有插座"), moved)
        assertEquals(names, TagOrder.move(moved, PersonalTags.outlets, PersonalTags.comfortable))
        assertEquals(names, TagOrder.move(names, "不存在", "靠窗"))
    }

    @Test fun tombstonesPreventOldProfileMarksFromResurrectingDeletedTerms() {
        val catalog = TagCatalog().deleted(PersonalTags.outlets).deleted(PersonalTags.legacyQuiet)
        assertFalse(PersonalTags.outlets in catalog.available())
        assertFalse(PersonalTags.legacyQuiet in catalog.available(setOf(PersonalTags.legacyQuiet)))
        assertNull(PersonalTags.validate(PersonalTags.outlets, catalog.available().toSet()))
        assertTrue(PersonalTags.outlets in catalog.added(PersonalTags.outlets).available())
    }

    @Test fun deletionClearsOnlyTheMatchingConditionAndPreservesRoomAnnotations() {
        val filters = SearchFilters(setOf(1, 6), buildings = setOf("2"), favoritesOnly = true,
            outletsOnly = true, tags = setOf("靠窗", PersonalTags.comfortable))
        assertFalse(filters.withoutTerm(PersonalTags.outlets).outletsOnly)
        assertTrue(filters.withoutTerm(PersonalTags.outlets).favoritesOnly)
        assertEquals(setOf(PersonalTags.comfortable), filters.withoutTerm("靠窗").tags)
        assertEquals(filters, filters.withoutTerm(PersonalTags.favorite))
        val room = RoomProfile("01:2-201", "2-201", "2", outlets = true, tags = setOf("靠窗"))
        assertTrue(room.hasTerm(PersonalTags.outlets))
        assertTrue(room.hasTerm("靠窗"))
    }

    @Test fun orderedCatalogRoundTripsThroughPreferencesWithoutSortingOrLosingTombstones() {
        val values = mutablePreferencesOf()
        val catalog = TagCatalog().added("靠窗").deleted(PersonalTags.comfortable)
            .reordered(listOf("已收藏", "靠窗", "人少", "有插座"))
        TagCatalogPreferences.write(values, catalog)
        val restored = TagCatalogPreferences.read(values)
        assertEquals(catalog.available(), restored.available())
        assertEquals(catalog.removed, restored.removed)
    }

    @Test fun migrationPreservesLegacyDefinitionsAndSeparateBuildingSelections() = runTest {
        val values = mutablePreferencesOf(SharedFilterPreferences.definitions to setOf("靠窗", "安静"),
            CampusFilterPreferences.building(Campus.XITUCHENG) to "2",
            CampusFilterPreferences.building(Campus.SHAHE) to "教一")
        val migration = TagCatalogMigration()
        assertTrue(migration.shouldMigrate(values))
        val migrated = migration.migrate(values)
        assertEquals(PersonalTags.defaultOrder + listOf("安静", "靠窗"), TagCatalogPreferences.read(migrated).available())
        assertEquals(setOf("2"), BuildingFilterPreferences.read(migrated, Campus.XITUCHENG))
        assertEquals(setOf("教一"), BuildingFilterPreferences.read(migrated, Campus.SHAHE))
        assertEquals("2", migrated[CampusFilterPreferences.building(Campus.XITUCHENG)])
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test fun allBuildingsDoesNotFallBackToOldSingleBuildingWhenExplicitlyStored() {
        val values = mutablePreferencesOf(CampusFilterPreferences.building(Campus.XITUCHENG) to "2",
            BuildingFilterPreferences.key(Campus.XITUCHENG) to emptySet())
        assertTrue(BuildingFilterPreferences.read(values, Campus.XITUCHENG).isEmpty())
        values[BuildingFilterPreferences.key(Campus.XITUCHENG)] = setOf("2", "3")
        values[BuildingFilterPreferences.key(Campus.SHAHE)] = setOf("教一")
        assertEquals(setOf("2", "3"), BuildingFilterPreferences.read(values, Campus.XITUCHENG))
        assertEquals(setOf("教一"), BuildingFilterPreferences.read(values, Campus.SHAHE))
    }
}
