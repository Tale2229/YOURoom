package com.example.youroom

import com.example.youroom.data.*
import com.example.youroom.data.local.*
import com.example.youroom.domain.SchoolZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

class ClassroomRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var dao: MemoryDao
    private lateinit var session: MemorySession
    private lateinit var repository: ClassroomRepository
    private val now = AtomicReference(Instant.parse("2026-10-11T01:00:00Z"))

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        dao = MemoryDao()
        session = MemorySession("dummy-test-token")
        repository = ClassroomRepository(SchoolApi(baseUrl = server.url("/bjyddx/")),
            dao, session, MutableClock(now))
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun successfulRefreshPersistsValidatedSnapshotWithSchoolQueryDate() = runBlocking {
        server.enqueue(MockResponse().setBody(VALID_RESPONSE))
        val snapshot = repository.refresh()
        assertEquals(LocalDate.of(2026, 10, 11), snapshot.queryDate)
        assertEquals(now.get(), snapshot.fetchedAt)
        assertEquals("2026-10-11", dao.savedSnapshot!!.queryDate)
        assertEquals(VALID_RESPONSE, dao.savedSnapshot!!.responseJson)
        assertEquals("dummy-test-token", server.takeRequest().getHeader("Token"))
    }

    @Test fun malformedResponseDoesNotReplaceWorkingCache() = runBlocking {
        val original = oldSnapshot()
        dao.savedSnapshot = original
        server.enqueue(MockResponse().setBody("""{"code":"1","data":[{"renamed":"unexpected"}]}"""))
        try { repository.refresh(); fail("Expected schema failure") }
        catch (_: InvalidSchoolResponse) { }
        assertEquals(original, dao.savedSnapshot)
        assertEquals("dummy-test-token", session.token)
    }

    @Test fun serviceFailurePreservesCacheAndSession() = runBlocking {
        val original = oldSnapshot()
        dao.savedSnapshot = original
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"500","Msg":"failed"}"""))
        try { repository.refresh(); fail("Expected service failure") }
        catch (_: SchoolRequestFailed) { }
        assertEquals(original, dao.savedSnapshot)
        assertEquals("dummy-test-token", session.token)
    }

    @Test fun expiredTokenClearsSessionButKeepsCachedRooms() = runBlocking {
        val original = oldSnapshot()
        dao.savedSnapshot = original
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"401","Msg":"非法访问"}"""))
        try { repository.refresh(); fail("Expected authentication failure") }
        catch (_: AuthenticationRequired) { }
        assertNull(session.token)
        assertEquals(original, dao.savedSnapshot)
    }

    @Test fun absentOrBlankSessionNeverSendsAQuery() = runBlocking {
        for (token in listOf(null, "", "  ")) {
            session.token = token
            assertFalse(repository.hasSession())
            try { repository.refresh(); fail("Expected authentication failure") }
            catch (_: AuthenticationRequired) { }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun aRequestCrossingMidnightIsNotCachedAsEitherDay() = runBlocking {
        val original = oldSnapshot()
        dao.savedSnapshot = original
        now.set(Instant.parse("2026-10-11T15:59:59Z"))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                now.set(Instant.parse("2026-10-11T16:00:01Z"))
                return MockResponse().setBody(VALID_RESPONSE)
            }
        }
        try { repository.refresh(); fail("Expected midnight rejection") }
        catch (error: SchoolRequestFailed) { assertTrue(error.userMessage.contains("午夜")) }
        assertEquals(original, dao.savedSnapshot)
    }

    @Test fun cacheRetainsItsOriginalQueryDateRatherThanRelabelingAsToday() = runBlocking {
        dao.savedSnapshot = oldSnapshot()
        val cached = repository.cached()!!
        assertEquals(LocalDate.of(2026, 10, 10), cached.queryDate)
        assertFalse(cached.isCurrentOn(LocalDate.of(2026, 10, 11)))
        assertEquals(0, server.requestCount)
    }

    @Test fun corruptCacheIsIgnoredWithoutAnExternalRequest() = runBlocking {
        dao.savedSnapshot = oldSnapshot().copy(responseJson = "corrupted-json")
        assertNull(repository.cached())
        assertEquals(0, server.requestCount)
    }

    @Test fun logoutRetainsPersonalProfilesAndNonSensitiveCache() = runBlocking {
        dao.savedSnapshot = oldSnapshot()
        dao.profileRows.value = listOf(ProfileEntity("01:2-201", "2-201", "2", true, false, true, false, "靠窗"))
        repository.logout()
        assertNull(session.token)
        assertNotNull(dao.savedSnapshot)
        assertEquals("靠窗", repository.profiles.first()["01:2-201"]!!.note)
    }

    private fun oldSnapshot() = SnapshotEntity("01", "2026-10-10",
        Instant.parse("2026-10-10T01:00:00Z").toEpochMilli(), VALID_RESPONSE)

    private class MemorySession(var token: String?) : SessionStore {
        override fun read() = token
        override fun save(token: String) { this.token = token }
        override fun clear() { token = null }
    }

    private class MemoryDao : YouRoomDao {
        var savedSnapshot: SnapshotEntity? = null
        val profileRows = MutableStateFlow<List<ProfileEntity>>(emptyList())
        override suspend fun snapshot(campusId: String) = savedSnapshot?.takeIf { it.campusId == campusId }
        override suspend fun saveSnapshot(snapshot: SnapshotEntity) { savedSnapshot = snapshot }
        override fun profiles(): Flow<List<ProfileEntity>> = profileRows
        override suspend fun saveProfile(profile: ProfileEntity) {
            profileRows.value = profileRows.value.filterNot { it.roomId == profile.roomId } + profile
        }
        override suspend fun deleteProfile(roomId: String) {
            profileRows.value = profileRows.value.filterNot { it.roomId == roomId }
        }
    }

    private class MutableClock(private val instant: AtomicReference<Instant>, private val zone: ZoneId = SchoolZone) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant.get()
    }

    companion object {
        private const val VALID_RESPONSE = """{"code":"1","Msg":"success","data":[{"NODENAME":"1","NODETIME":"08:00-08:45","CLASSROOMS":"2-201(180),未来学习大楼-202-203(60)"}]}"""
    }
}
