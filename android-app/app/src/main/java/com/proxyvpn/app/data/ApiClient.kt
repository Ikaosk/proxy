package com.proxyvpn.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String) : IOException(message)

data class LinkStartResult(val code: String, val deepLink: String?, val expiresIn: Int)
data class LinkStatusResult(val valid: Boolean, val linked: Boolean, val sessionToken: String?)
data class Plan(val id: String, val title: String, val days: Int, val price: Int, val currency: String)
data class Subscription(
    val planId: String,
    val planTitle: String,
    val startedAt: String,
    val expiresAt: String,
    val active: Boolean,
)
data class ProxyInfo(val login: String, val password: String, val host: String, val httpPort: Int, val socksPort: Int)
data class SubscriptionStatus(
    val active: Boolean,
    val planId: String?,
    val planTitle: String?,
    val expiresAt: String?,
)
data class MeResult(
    val telegramId: Long,
    val username: String?,
    val firstName: String?,
    val createdAt: String,
    val subscription: SubscriptionStatus,
    val proxy: ProxyInfo,
)

private fun JSONObject.stringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

/**
 * Клиент backend API из telegram-bot/lib/apiServer.js. Все публичные методы
 * suspend и сами переключаются на Dispatchers.IO — вызывать можно прямо
 * из корутины на Main (например, из rememberCoroutineScope в Compose),
 * не блокируя UI-поток.
 */
class ApiClient(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val emptyBody = "".toRequestBody(jsonMedia)

    private fun executeObject(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException("HTTP ${response.code}: $bodyStr")
            return if (bodyStr.isBlank()) JSONObject() else JSONObject(bodyStr)
        }
    }

    private fun executeArray(request: Request): JSONArray {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException("HTTP ${response.code}: $bodyStr")
            return JSONArray(bodyStr)
        }
    }

    suspend fun linkStart(): LinkStartResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/link/start").post(emptyBody).build()
        val json = executeObject(request)
        LinkStartResult(
            code = json.getString("code"),
            deepLink = json.stringOrNull("deepLink"),
            expiresIn = json.optInt("expiresIn", 600),
        )
    }

    suspend fun linkStatus(code: String): LinkStatusResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/link/status?code=$code").get().build()
        val json = executeObject(request)
        LinkStatusResult(
            valid = json.optBoolean("valid", false),
            linked = json.optBoolean("linked", false),
            sessionToken = json.stringOrNull("sessionToken"),
        )
    }

    suspend fun plans(): List<Plan> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/plans").get().build()
        val array = executeArray(request)
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Plan(
                id = item.getString("id"),
                title = item.getString("title"),
                days = item.getInt("days"),
                price = item.getInt("price"),
                currency = item.getString("currency"),
            )
        }
    }

    suspend fun me(token: String): MeResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/me")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val json = executeObject(request)
        val sub = json.getJSONObject("subscription")
        val proxy = json.getJSONObject("proxy")
        MeResult(
            telegramId = json.getLong("telegramId"),
            username = json.stringOrNull("username"),
            firstName = json.stringOrNull("firstName"),
            createdAt = json.getString("createdAt"),
            subscription = SubscriptionStatus(
                active = sub.optBoolean("active", false),
                planId = sub.stringOrNull("planId"),
                planTitle = sub.stringOrNull("planTitle"),
                expiresAt = sub.stringOrNull("expiresAt"),
            ),
            proxy = ProxyInfo(
                login = proxy.getString("login"),
                password = proxy.getString("password"),
                host = proxy.getString("host"),
                httpPort = proxy.getInt("httpPort"),
                socksPort = proxy.getInt("socksPort"),
            ),
        )
    }

    suspend fun subscriptions(token: String): List<Subscription> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/subscriptions")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val array = executeArray(request)
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Subscription(
                planId = item.getString("planId"),
                planTitle = item.getString("planTitle"),
                startedAt = item.getString("startedAt"),
                expiresAt = item.getString("expiresAt"),
                active = item.getBoolean("active"),
            )
        }
    }

    suspend fun logout(token: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/logout")
            .header("Authorization", "Bearer $token")
            .post(emptyBody)
            .build()
        executeObject(request)
        Unit
    }
}
