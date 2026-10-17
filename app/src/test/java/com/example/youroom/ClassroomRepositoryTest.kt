package com.example.youroom

import com.example.youroom.data.*
import com.example.youroom.data.local.*
import com.example.youroom.domain.SchoolZone
import com.example.youroom.domain.Campus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Test fun shaheUses04AndDoesNotReplaceWestCampusCache() = runBlocking {
        val original = oldSnapshot()
        dao.savedSnapshot = original
        server.enqueue(MockResponse().setBody(VALID_RESPONSE))
        val snapshot = repository.refresh(Campus.SHAHE)
        assertEquals("04", snapshot.campusId)
        assertTrue(snapshot.classrooms.all { it.id.startsWith("04:") })
        assertEquals("04", server.takeRequest().requestUrl!!.queryParameter("campusId"))
        assertEquals(original, dao.savedSnapshot)
        assertNotNull(dao.snapshots["04"])
        assertEquals("04", repository.cached(Campus.SHAHE)!!.campusId)
    }

    @Test fun shaheFailureLeavesBothCampusCachesIntact() = runBlocking {
        dao.savedSnapshot = oldSnapshot()
        dao.snapshots["04"] = oldSnapshot().copy(campusId = "04")
        val original = dao.snapshots.toMap()
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"500"}"""))
        try { repository.refresh(Campus.SHAHE); fail("Expected service failure") }
        catch (_: SchoolRequestFailed) { }
        assertEquals(original, dao.snapshots)
    }

    @Test fun successfulRefreshPersistsValidatedSnapshotWithSchoolQueryDate() = runBlocking {
        server.enqueue(MockResponse().setBody(VALID_RESPONSE))
        val snapshot = repository.refresh(Campus.XITUCHENG)
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
        try { repository.refresh(Campus.XITUCHENG); fail("Expected schema failure") }
        catch (_: InvalidSchoolResponse) { }
        assertEquals(original, dao.savedSnapshot)
        assertEquals("dummy-test-token", session.token)
    }

    @Test fun serviceFailurePreservesCacheAndSession() = runBlocking {
        val original = oldSnapshot()
        dao.savedSnapshot = original
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"500","Msg":"failed"}"""))
        try { repository.refresh(Campus.XITUCHENG); fail("Expected service failure") }
        catch (_: SchoolRequestFailed) { }
        assertEquals(original, dao.savedSnapshot)
        assertEquals("dummy-test-token", session.token)
    }

    @Test fun expiredTokenClearsSessionButKeepsCachedRooms() = runBlocking {
        val original = oldSnapshot()
        dao.savedSnapshot = original
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"401","Msg":"非法访问"}"""))
        try { repository.refresh(Campus.XITUCHENG); fail("Expected authentication failure") }
        catch (_: AuthenticationRequired) { }
        assertNull(session.token)
        assertEquals(original, dao.savedSnapshot)
    }

    @Test fun absentOrBlankSessionNeverSendsAQuery() = runBlocking {
        for (token in listOf(null, "", "  ")) {
            session.token = token
            assertFalse(repository.hasSession())
            try { repository.refresh(Campus.XITUCHENG); fail("Expected authentication failure") }
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
        try { repository.refresh(Campus.XITUCHENG); fail("Expected midnight rejection") }
        catch (error: SchoolRequestFailed) { assertTrue(error.userMessage.contains("午夜")) }
        assertEquals(original, dao.savedSnapshot)
    }

    @Test fun cacheRetainsItsOriginalQueryDateRatherThanRelabelingAsToday() = runBlocking {
        dao.savedSnapshot = oldSnapshot()
        val cached = repository.cached(Campus.XITUCHENG)!!
        assertEquals(LocalDate.of(2026, 10, 10), cached.queryDate)
        assertFalse(cached.isCurrentOn(LocalDate.of(2026, 10, 11)))
        assertEquals(0, server.requestCount)
    }

    @Test fun corruptCacheIsIgnoredWithoutAnExternalRequest() = runBlocking {
        dao.savedSnapshot = oldSnapshot().copy(responseJson = "corrupted-json")
        assertNull(repository.cached(Campus.XITUCHENG))
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

    @Test fun rememberedLoginIsOptInAndUncheckingRemovesOldCredentials() = runBlocking {
        server.enqueue(loginReply())
        repository.login("dummy-user", "dummy-password", true)
        assertEquals("dummy-user", repository.rememberedAccount())
        assertEquals("dummy-password", session.remembered!!.password)
        server.enqueue(loginReply())
        repository.login("other-user", "other-password", false)
        assertNull(session.remembered)
        assertNull(repository.rememberedAccount())
    }

    @Test fun business401RelogsAndReplaysOriginalShaheQueryOnce() = runBlocking {
        remember()
        server.enqueue(expiredReply()); server.enqueue(loginReply()); server.enqueue(MockResponse().setBody(VALID_RESPONSE))
        assertEquals("04", repository.refresh(Campus.SHAHE).campusId)
        assertEquals("dummy-test-token", server.takeRequest().getHeader("Token"))
        val login = server.takeRequest()
        assertEquals("/bjyddx/login", login.requestUrl!!.encodedPath)
        assertEquals("dummy-user", login.requestUrl!!.queryParameter("userNo"))
        assertEquals(LegacyPasswordEncoder.encode("dummy-password"), login.requestUrl!!.queryParameter("pwd"))
        val replay = server.takeRequest()
        assertEquals("fresh-token", replay.getHeader("Token"))
        assertEquals("04", replay.requestUrl!!.queryParameter("campusId"))
        assertEquals("fresh-token", session.token)
        assertNotNull(session.remembered)
    }

    @Test fun concurrentCampusExpirySharesOneLogin() = runBlocking {
        remember()
        val expiredQueries = CountDownLatch(2)
        val logins = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("/login")) {
                    logins.incrementAndGet(); return loginReply().setBodyDelay(100, TimeUnit.MILLISECONDS)
                }
                if (request.getHeader("Token") == "dummy-test-token") {
                    expiredQueries.countDown()
                    check(expiredQueries.await(5, TimeUnit.SECONDS))
                    return expiredReply()
                }
                return MockResponse().setBody(VALID_RESPONSE)
            }
        }
        val results = withTimeout(8_000) { Campus.entries.map { async { repository.refresh(it) } }.awaitAll() }
        assertEquals(setOf("01", "04"), results.map { it.campusId }.toSet())
        assertEquals(1, logins.get())
        assertEquals(5, server.requestCount)
    }

    @Test fun late401UsesAlreadyRenewedTokenWithoutLoggingInAgain() = runBlocking {
        remember()
        val oldShaheStarted = CountDownLatch(1)
        val renewedWestReturned = CountDownLatch(1)
        val logins = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (url.encodedPath.endsWith("/login")) { logins.incrementAndGet(); return loginReply() }
                if (request.getHeader("Token") == "dummy-test-token") {
                    if (url.queryParameter("campusId") == "04") {
                        oldShaheStarted.countDown(); check(renewedWestReturned.await(5, TimeUnit.SECONDS))
                    } else check(oldShaheStarted.await(5, TimeUnit.SECONDS))
                    return expiredReply()
                }
                return MockResponse().setBody(VALID_RESPONSE)
            }
        }
        val shahe = async { repository.refresh(Campus.SHAHE) }
        repository.refresh(Campus.XITUCHENG)
        renewedWestReturned.countDown()
        shahe.await()
        assertEquals(1, logins.get())
    }

    @Test fun rejectedPasswordOrCaptchaStopsRecoveryAndClearsCredentials() = runBlocking {
        for (message in listOf("密码错误", "需要验证码")) {
            repository.logout()
            remember(); session.token = "dummy-test-token"
            server.enqueue(expiredReply())
            server.enqueue(MockResponse().setBody("""{"code":"0","Msg":"$message"}"""))
            try { repository.refresh(Campus.XITUCHENG); fail("Expected login rejection") }
            catch (error: AuthenticationRequired) { assertTrue(error.message!!.contains("已清除")) }
            assertNull(session.token); assertNull(session.remembered)
            val count = server.requestCount
            try { repository.refresh(Campus.SHAHE); fail("Expected manual login") }
            catch (_: AuthenticationRequired) { }
            assertEquals(count, server.requestCount)
        }
    }

    @Test fun transientLoginFailureKeepsCredentialsAndDoesNotBurstRetry() = runBlocking {
        remember()
        server.enqueue(expiredReply())
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"503"}"""))
        server.enqueue(expiredReply())
        repeat(2) {
            try { repository.refresh(Campus.XITUCHENG); fail("Expected service failure") }
            catch (_: SchoolRequestFailed) { }
        }
        assertEquals(3, server.requestCount)
        assertEquals("dummy-test-token", session.token)
        assertNotNull(session.remembered)
    }

    @Test fun malformedRecoveryReplyDoesNotErasePasswordOrCache() = runBlocking {
        remember(); dao.savedSnapshot = oldSnapshot()
        server.enqueue(expiredReply()); server.enqueue(MockResponse().setBody("not-json"))
        try { repository.refresh(Campus.XITUCHENG); fail("Expected malformed response") }
        catch (_: InvalidSchoolResponse) { }
        assertNotNull(session.remembered)
        assertEquals(oldSnapshot(), dao.savedSnapshot)
    }

    @Test fun renewedTokenRejectedAgainNeverLoops() = runBlocking {
        remember()
        server.enqueue(expiredReply()); server.enqueue(loginReply()); server.enqueue(expiredReply())
        try { repository.refresh(Campus.XITUCHENG); fail("Expected terminal rejection") }
        catch (_: AuthenticationRequired) { }
        assertEquals(3, server.requestCount)
        assertNull(session.token); assertNull(session.remembered)
    }

    @Test fun logoutDuringAutomaticLoginCannotResurrectSession() = runBlocking {
        remember(); dao.savedSnapshot = oldSnapshot()
        val started = CompletableDeferred<Unit>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("/login")) {
                    started.complete(Unit)
                    return loginReply().setBodyDelay(400, TimeUnit.MILLISECONDS)
                }
                return expiredReply()
            }
        }
        val query = async { repository.refresh(Campus.XITUCHENG) }
        withTimeout(5_000) { started.await() }
        repository.logout()
        try { query.await(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertNull(session.token); assertNull(session.remembered)
        assertEquals(oldSnapshot(), dao.savedSnapshot)
    }

    @Test fun canceledCampusRequestDoesNotCancelRecoveryNeededByNextCampus() = runBlocking {
        remember()
        val started = CompletableDeferred<Unit>()
        val logins = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("/login")) {
                    logins.incrementAndGet(); started.complete(Unit)
                    return loginReply().setBodyDelay(250, TimeUnit.MILLISECONDS)
                }
                return if (request.getHeader("Token") == "fresh-token") MockResponse().setBody(VALID_RESPONSE) else expiredReply()
            }
        }
        val first = launch { repository.refresh(Campus.XITUCHENG) }
        withTimeout(5_000) { started.await() }
        first.cancelAndJoin()
        assertEquals("04", withTimeout(5_000) { repository.refresh(Campus.SHAHE) }.campusId)
        assertEquals(1, logins.get())
    }

    @Test fun logoutDuringManualLoginDiscardsLateToken() = runBlocking {
        server.enqueue(loginReply().setBodyDelay(300, TimeUnit.MILLISECONDS))
        val login = async { repository.login("dummy-user", "dummy-password", true) }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        repository.logout()
        try { login.await(); fail("Expected superseded login") } catch (_: CancellationException) { }
        assertNull(session.token); assertNull(session.remembered)
    }

    @Test fun logoutWhileQueryInFlightDoesNotWriteNewCache() = runBlocking {
        dao.savedSnapshot = oldSnapshot()
        server.enqueue(MockResponse().setBody(VALID_RESPONSE).setBodyDelay(250, TimeUnit.MILLISECONDS))
        val query = async { repository.refresh(Campus.XITUCHENG) }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        repository.logout()
        try { query.await(); fail("Expected obsolete result") } catch (_: CancellationException) { }
        assertEquals(oldSnapshot(), dao.savedSnapshot)
    }

    @Test fun midnightDuringRecoveryStillRejectsNewSnapshot() = runBlocking {
        remember(); dao.savedSnapshot = oldSnapshot()
        now.set(Instant.parse("2026-10-11T15:59:59Z"))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("/login")) {
                    now.set(Instant.parse("2026-10-11T16:00:01Z")); return loginReply()
                }
                return if (request.getHeader("Token") == "fresh-token") MockResponse().setBody(VALID_RESPONSE) else expiredReply()
            }
        }
        try { repository.refresh(Campus.XITUCHENG); fail("Expected midnight rejection") }
        catch (error: SchoolRequestFailed) { assertTrue(error.userMessage.contains("午夜")) }
        assertEquals(oldSnapshot(), dao.savedSnapshot)
        assertEquals("fresh-token", session.token)
    }

    @Test fun oldAccount401CannotClearANewerManualLogin() = runBlocking {
        remember()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("/login")) return loginReply()
                started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)); return expiredReply()
            }
        }
        val oldQuery = async { repository.refresh(Campus.XITUCHENG) }
        withTimeout(5_000) { started.await() }
        repository.login("new-account", "new-password", true)
        release.countDown()
        try { oldQuery.await(); fail("Expected obsolete request") } catch (_: CancellationException) { }
        assertEquals("fresh-token", session.token)
        assertEquals("new-account", repository.rememberedAccount())
    }

    private fun remember() { session.remembered = RememberedCredentials("dummy-user", "dummy-password") }
    private fun expiredReply() = MockResponse().setResponseCode(500).setBody("""{"code":"401"}""")
    private fun loginReply() = MockResponse().setBody("""{"code":"1","data":{"token":"fresh-token"}}""")

    private fun oldSnapshot() = SnapshotEntity("01", "2026-10-10",
        Instant.parse("2026-10-10T01:00:00Z").toEpochMilli(), VALID_RESPONSE)

    private class MemorySession(var token: String?) : SessionStore {
        var remembered: RememberedCredentials? = null
        override fun read() = token
        override fun save(token: String) { this.token = token }
        override fun clear() { token = null; remembered = null }
        override fun clearToken() { token = null }
        override fun credentials() = remembered
        override fun saveLogin(token: String, credentials: RememberedCredentials?) { this.token = token; remembered = credentials }
    }

    private class MemoryDao : YouRoomDao {
        val snapshots = mutableMapOf<String, SnapshotEntity>()
        var savedSnapshot: SnapshotEntity?
            get() = snapshots["01"]
            set(value) { if (value == null) snapshots.remove("01") else snapshots["01"] = value }
        val profileRows = MutableStateFlow<List<ProfileEntity>>(emptyList())
        override suspend fun snapshot(campusId: String) = snapshots[campusId]
        override suspend fun saveSnapshot(snapshot: SnapshotEntity) { snapshots[snapshot.campusId] = snapshot }
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
