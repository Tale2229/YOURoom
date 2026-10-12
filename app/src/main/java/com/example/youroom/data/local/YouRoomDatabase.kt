package com.example.youroom.data.local

import androidx.room.*
import com.example.youroom.domain.RoomProfile
import com.example.youroom.domain.allTags
import com.example.youroom.domain.PersonalTags
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.*

@Entity(tableName = "classroom_snapshots")
data class SnapshotEntity(
    @PrimaryKey val campusId: String,
    val queryDate: String,
    val fetchedAtMillis: Long,
    val responseJson: String,
)

@Entity(tableName = "room_profiles")
data class ProfileEntity(
    @PrimaryKey val roomId: String,
    val name: String,
    val building: String,
    val favorite: Boolean,
    val excluded: Boolean,
    val quiet: Boolean,
    val outlets: Boolean,
    val note: String,
    @ColumnInfo(defaultValue = "'[]'") val tagsJson: String = "[]",
) {
    fun toDomain() = RoomProfile(roomId, name, building, favorite, excluded, quiet, outlets, note,
        Json.parseToJsonElement(tagsJson).jsonArray.map { it.jsonPrimitive.content }.toSet())
}

fun RoomProfile.toEntity() = ProfileEntity(roomId, name, building, favorite, excluded,
    PersonalTags.legacyQuiet in allTags,
    outlets, note, JsonArray(tags.sorted().map(::JsonPrimitive)).toString())

@Dao
interface YouRoomDao {
    @Query("SELECT * FROM classroom_snapshots WHERE campusId = :campusId")
    suspend fun snapshot(campusId: String): SnapshotEntity?

    @Upsert
    suspend fun saveSnapshot(snapshot: SnapshotEntity)

    @Query("SELECT * FROM room_profiles ORDER BY name")
    fun profiles(): Flow<List<ProfileEntity>>

    @Upsert
    suspend fun saveProfile(profile: ProfileEntity)

    @Query("DELETE FROM room_profiles WHERE roomId = :roomId")
    suspend fun deleteProfile(roomId: String)
}

@Database(entities = [SnapshotEntity::class, ProfileEntity::class], version = 2, exportSchema = true)
abstract class YouRoomDatabase : RoomDatabase() {
    abstract fun dao(): YouRoomDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE room_profiles ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}
