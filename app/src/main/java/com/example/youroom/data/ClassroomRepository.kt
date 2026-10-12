package com.example.youroom.data

import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class ClassroomRepository(
    private val api: SchoolApi,
    private val dao: YouRoomDao,
    private val session: SessionStore,
    private val clock: Clock = Clock.system(SchoolZone),
) : ClassroomDataSource {
    override val profiles = dao.profiles().map { rows -> rows.associate { it.roomId to it.toDomain() } }

    override suspend fun hasSession(): Boolean = withContext(Dispatchers.IO) { !session.read().isNullOrBlank() }

    override suspend fun login(account: String, password: String) {
        val token = api.login(account, password)
        withContext(Dispatchers.IO) { session.save(token) }
    }

    override suspend fun logout() = withContext(Dispatchers.IO) { session.clear() }

    override suspend fun cached(campus: Campus): ClassroomSnapshot? {
        val cached = dao.snapshot(campus.dataKey) ?: return null
        return try {
            SchoolJson.snapshot(cached.responseJson, cached.campusId,
                LocalDate.parse(cached.queryDate), Instant.ofEpochMilli(cached.fetchedAtMillis))
        } catch (_: Exception) { null }
    }

    override suspend fun refresh(campus: Campus): ClassroomSnapshot {
        val token = withContext(Dispatchers.IO) { session.read()?.takeIf { it.isNotBlank() } }
            ?: throw AuthenticationRequired()
        val date = LocalDate.now(clock.withZone(SchoolZone))
        val json = try { api.classrooms(token, requireNotNull(campus.verifiedApiId)) } catch (error: AuthenticationRequired) {
            logout()
            throw error
        }
        val now = clock.instant()
        if (now.atZone(SchoolZone).toLocalDate() != date) {
            throw SchoolRequestFailed("查询跨越了午夜，请重新刷新今日数据。")
        }
        val snapshot = SchoolJson.snapshot(json, campus.dataKey, date, now)
        // Replace only after full validation; failures preserve the previous snapshot.
        dao.saveSnapshot(SnapshotEntity(campus.dataKey, date.toString(), now.toEpochMilli(), json))
        return snapshot
    }

    override suspend fun saveProfile(profile: RoomProfile) = dao.saveProfile(profile.toEntity())
    override suspend fun deleteProfile(roomId: String) = dao.deleteProfile(roomId)
}
