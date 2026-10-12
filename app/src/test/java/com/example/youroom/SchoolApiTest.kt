package com.example.youroom

import com.example.youroom.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SchoolApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SchoolApi
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = SchoolApi(baseUrl = server.url("/bjyddx/"))
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun loginThenQueryUseFreshTokenAndNeverCookies() = runBlocking {
        server.enqueue(MockResponse().setHeader("Set-Cookie", "session=should-not-be-used; Path=/")
            .setBody("""{"code":"1","Msg":"登录成功！","data":{"token":"dummy-test-token"}}"""))
        server.enqueue(MockResponse().setBody("""{"code":"1","Msg":"success","data":[]}"""))
        val token = api.login("dummy-account", "Classroom_Test_123!")
        api.classrooms(token)
        val login = server.takeRequest()
        assertEquals("POST", login.method)
        assertEquals(0L, login.bodySize)
        assertEquals("dummy-account", login.requestUrl!!.queryParameter("userNo"))
        assertEquals(LegacyPasswordEncoder.encode("Classroom_Test_123!"), login.requestUrl!!.queryParameter("pwd"))
        assertEquals("1", login.requestUrl!!.queryParameter("encode"))
        assertNull(login.getHeader("Token"))
        val query = server.takeRequest()
        assertEquals("POST", query.method)
        assertEquals(0L, query.bodySize)
        assertEquals("01", query.requestUrl!!.queryParameter("campusId"))
        assertEquals("dummy-test-token", query.getHeader("Token"))
        assertNull(query.getHeader("Cookie"))
    }

    @Test fun recognizesBusiness401InsideHttp500() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500)
            .setBody("""{"code":"401","Msg":"非法访问：/todayClassrooms","data":""}"""))
        try { api.classrooms("dummy-expired"); fail("Expected authentication failure") }
        catch (_: AuthenticationRequired) { }
    }

    @Test fun neverFollowsRedirectWithCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/other")))
        try { api.classrooms("dummy-secret"); fail("Expected rejection") }
        catch (_: InvalidSchoolResponse) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun generic500IsNotMistakenForExpiredToken() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"500","Msg":"failed","data":""}"""))
        try { api.classrooms("dummy-test"); fail("Expected service failure") }
        catch (_: SchoolRequestFailed) { }
    }

    @Test fun recognizesHttp401EvenWhenBodyIsNotJson() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("<html>Unauthorized</html>"))
        try { api.classrooms("dummy-test"); fail("Expected authentication failure") }
        catch (_: AuthenticationRequired) { }
    }
}
