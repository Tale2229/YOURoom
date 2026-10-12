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
    @Test fun upgradePreservesBothCampusCachesFavoritesNotesAndLegacyMarks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Only this randomly named test database is created or deleted.
        val name = "youroom-migration-test-${UUID.randomUUID()}.db"
        var room: YouRoomDatabase? = null
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE classroom_snapshots (campusId TEXT NOT NULL PRIMARY KEY, queryDate TEXT NOT NULL, fetchedAtMillis INTEGER NOT NULL, responseJson TEXT NOT NULL)")
                old.execSQL("CREATE TABLE room_profiles (roomId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, building TEXT NOT NULL, favorite INTEGER NOT NULL, excluded INTEGER NOT NULL, quiet INTEGER NOT NULL, outlets INTEGER NOT NULL, note TEXT NOT NULL)")
                old.execSQL("INSERT INTO classroom_snapshots VALUES ('01','2026-10-12',0,'{}'), ('04','2026-10-12',0,'{}')")
                old.execSQL("INSERT INTO room_profiles VALUES ('01:2-201','2-201','2',1,0,1,1,'原备注')")
                old.version = 1
            }
            room = Room.databaseBuilder(context, YouRoomDatabase::class.java, name)
                .addMigrations(YouRoomDatabase.MIGRATION_1_2).build()
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
