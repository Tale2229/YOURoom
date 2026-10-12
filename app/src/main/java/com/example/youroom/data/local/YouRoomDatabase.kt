package com.example.youroom.data.local

import androidx.room.*
import com.example.youroom.domain.RoomProfile
import kotlinx.coroutines.flow.Flow

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
) {
    fun toDomain() = RoomProfile(roomId, name, building, favorite, excluded, quiet, outlets, note)
}

fun RoomProfile.toEntity() = ProfileEntity(roomId, name, building, favorite, excluded, quiet, outlets, note)

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

@Database(entities = [SnapshotEntity::class, ProfileEntity::class], version = 1, exportSchema = true)
abstract class YouRoomDatabase : RoomDatabase() {
    abstract fun dao(): YouRoomDao
}
