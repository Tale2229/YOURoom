package com.example.youroom.data

import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
    private val authentication = SchoolSession(api, session)
    override val profiles = dao.profiles().map { rows -> rows.associate { it.roomId to it.toDomain() } }

    override suspend fun hasSession(): Boolean = authentication.hasSession()
    override suspend fun rememberedAccount(): String? = authentication.rememberedAccount()

    override suspend fun login(account: String, password: String) {
        login(account, password, false)
    }

    override suspend fun login(account: String, password: String, rememberPassword: Boolean) =
        authentication.login(account, password, rememberPassword)

    override suspend fun logout() = authentication.logout()

    override suspend fun cached(campus: Campus): ClassroomSnapshot? {
        val cached = dao.snapshot(campus.dataKey) ?: return null
        return try {
            withContext(Dispatchers.Default) {
                SchoolJson.snapshot(cached.responseJson, cached.campusId,
                    LocalDate.parse(cached.queryDate), Instant.ofEpochMilli(cached.fetchedAtMillis)).also { it.index }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
    }

    override suspend fun refresh(campus: Campus): ClassroomSnapshot {
        var lease = authentication.lease()
        val date = LocalDate.now(clock.withZone(SchoolZone))
        val campusId = requireNotNull(campus.verifiedApiId)
        val json = try { api.classrooms(lease.token, campusId) } catch (_: AuthenticationRequired) {
            lease = authentication.recover(lease)
            try { api.classrooms(lease.token, campusId) }
            catch (_: AuthenticationRequired) { authentication.expire(lease) }
        }
        val now = clock.instant()
        if (now.atZone(SchoolZone).toLocalDate() != date) {
            throw SchoolRequestFailed("查询跨越了午夜，请重新刷新今日数据。")
        }
        val snapshot = withContext(Dispatchers.Default) {
            SchoolJson.snapshot(json, campus.dataKey, date, now).also { it.index }
        }
        // Replace only after full validation; failures preserve the previous snapshot.
        authentication.withCurrent(lease) {
            dao.saveSnapshot(SnapshotEntity(campus.dataKey, date.toString(), now.toEpochMilli(), json))
        }
        return snapshot
    }

    override suspend fun saveProfile(profile: RoomProfile) = dao.saveProfile(profile.toEntity())
    override suspend fun deleteProfile(roomId: String) = dao.deleteProfile(roomId)
}
