package com.example.youroom

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DatabaseMigrationTest {
    @Test fun renamedDatabaseReopensTheExistingVersionTwoSchemaWithoutLosingData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "youroom-rename-test-${UUID.randomUUID()}.db"
        var room: YOURoomDatabase? = null
        try {
            // The identity is from the previously shipped schema, before the class rename.
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE classroom_snapshots (campusId TEXT NOT NULL PRIMARY KEY, queryDate TEXT NOT NULL, fetchedAtMillis INTEGER NOT NULL, responseJson TEXT NOT NULL)")
                old.execSQL("CREATE TABLE room_profiles (roomId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, building TEXT NOT NULL, favorite INTEGER NOT NULL, excluded INTEGER NOT NULL, quiet INTEGER NOT NULL, outlets INTEGER NOT NULL, note TEXT NOT NULL, tagsJson TEXT NOT NULL DEFAULT '[]')")
                old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                old.execSQL("INSERT INTO room_master_table VALUES (42, 'eb2720fd78716d1b4c11a2bc556aa42e')")
                old.execSQL("INSERT INTO classroom_snapshots VALUES ('01','2026-10-17',0,'{}')")
                old.execSQL("INSERT INTO room_profiles VALUES ('01:3-305','3-305','3',1,1,0,0,'保留备注','[\"靠窗\"]')")
                old.version = 2
            }
            room = Room.databaseBuilder(context, YOURoomDatabase::class.java, name).build()
            assertNotNull(room.dao().snapshot("01"))
            val profile = room.dao().profiles().first().single().toDomain()
            assertTrue(profile.favorite && profile.excluded)
            assertEquals("保留备注", profile.note)
            assertEquals(setOf("靠窗"), profile.tags)
        } finally {
            room?.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun upgradePreservesBothCampusCachesFavoritesNotesAndLegacyMarks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Only this randomly named test database is created or deleted.
        val name = "youroom-migration-test-${UUID.randomUUID()}.db"
        var room: YOURoomDatabase? = null
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE classroom_snapshots (campusId TEXT NOT NULL PRIMARY KEY, queryDate TEXT NOT NULL, fetchedAtMillis INTEGER NOT NULL, responseJson TEXT NOT NULL)")
                old.execSQL("CREATE TABLE room_profiles (roomId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, building TEXT NOT NULL, favorite INTEGER NOT NULL, excluded INTEGER NOT NULL, quiet INTEGER NOT NULL, outlets INTEGER NOT NULL, note TEXT NOT NULL)")
                old.execSQL("INSERT INTO classroom_snapshots VALUES ('01','2026-10-12',0,'{}'), ('04','2026-10-12',0,'{}')")
                old.execSQL("INSERT INTO room_profiles VALUES ('01:2-201','2-201','2',1,0,1,1,'原备注')")
                old.version = 1
            }
            room = Room.databaseBuilder(context, YOURoomDatabase::class.java, name)
                .addMigrations(YOURoomDatabase.MIGRATION_1_2).build()
            val dao = room.dao()
            assertNotNull(dao.snapshot("01"))
            assertNotNull(dao.snapshot("04"))
            val profile = dao.profiles().first().single().toDomain()
            assertTrue(profile.favorite && profile.outlets)
            assertEquals("原备注", profile.note)
            assertEquals(setOf(PersonalTags.legacyQuiet), profile.allTags)
            dao.saveProfile(profile.copy(tags = setOf(PersonalTags.comfortable, "靠窗")).toEntity())
            assertTrue(dao.profiles().first().single().toDomain().allTags.containsAll(setOf("靠窗", PersonalTags.comfortable)))
        } finally {
            room?.close()
            context.deleteDatabase(name)
        }
    }
}
