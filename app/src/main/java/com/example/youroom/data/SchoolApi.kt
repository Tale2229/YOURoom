package com.example.youroom.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthenticationRequired : Exception("登录状态已失效，请重新登录。")
class SchoolRequestFailed(val userMessage: String) : Exception(userMessage)

class SchoolApi(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: HttpUrl = "https://jwglweixin.bupt.edu.cn/bjyddx/".toHttpUrl(),
) {
    suspend fun login(account: String, password: String): String {
        val url = baseUrl.newBuilder().addPathSegment("login")
            .addQueryParameter("userNo", account)
            .addQueryParameter("pwd", LegacyPasswordEncoder.encode(password))
            .addQueryParameter("encode", "1")
            .addQueryParameter("captchaData", "")
            .addQueryParameter("codeVal", "").build()
        val response = execute(request(url))
        val root = withContext(Dispatchers.Default) { SchoolJson.response(response.body) }
        if (response.status !in 200..299 || root["code"]?.jsonPrimitive?.content != "1") {
            val message = root["Msg"]?.jsonPrimitive?.content.orEmpty()
            throw SchoolRequestFailed(when {
                "验证码" in message -> "学校要求验证码，当前版本暂不支持，请先使用官方教务页面。"
                response.status in 500..599 -> "教务登录服务暂不可用，请稍后重试。"
                else -> "登录未成功，请确认使用教务系统的账号和密码。"
            })
        }
        return try {
            root["data"]!!.jsonObject["token"]!!.jsonPrimitive.content.also {
                if (it.isBlank() || it == "null") throw InvalidSchoolResponse()
            }
        } catch (_: Exception) { throw InvalidSchoolResponse() }
    }

    suspend fun classrooms(token: String, campusId: String = "01"): String {
        val url = baseUrl.newBuilder().addPathSegment("todayClassrooms")
            .addQueryParameter("campusId", campusId).build()
        val response = execute(request(url).newBuilder().header("Token", token).build())
        if (response.status == 401) throw AuthenticationRequired()
        val root = withContext(Dispatchers.Default) { SchoolJson.response(response.body) }
        // This service reports missing/expired credentials as HTTP 500 + business code 401.
        if (root["code"]?.jsonPrimitive?.content == "401") {
            throw AuthenticationRequired()
        }
        if (response.status !in 200..299 || root["code"]?.jsonPrimitive?.content != "1") {
            throw SchoolRequestFailed("教务查询服务暂不可用，请稍后重试。")
        }
        return response.body
    }

    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("Accept", "application/json")
        .header("Origin", "https://jwglweixin.bupt.edu.cn")
        .header("Referer", "https://jwglweixin.bupt.edu.cn/sjd/")
        .post(ByteArray(0).toRequestBody(null)).build()

    private data class Reply(val status: Int, val body: String)

    private suspend fun execute(request: Request): Reply = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(
                    SchoolRequestFailed("无法连接教务服务，请检查网络；校外可尝试校园 VPN。")))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val body = it.body?.string().orEmpty()
                        if (continuation.isActive) continuation.resumeWith(Result.success(Reply(it.code, body)))
                    } catch (_: IOException) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(
                            SchoolRequestFailed("读取教务数据失败，请稍后刷新。")))
                    }
                }
            }
        })
    }

    companion object {
        // Do not attach HTTP loggers: login query parameters and responses contain credentials.
        fun defaultClient() = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    }
}
