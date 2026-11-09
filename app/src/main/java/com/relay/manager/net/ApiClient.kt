package com.relay.manager.net

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.relay.manager.data.Station
import com.relay.manager.data.StationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message)

/**
 * 统一 HTTP 客户端。
 * SUB: x-api-key: <管理密钥>
 * CPA: Authorization: Bearer <管理密钥> + X-Management-Key
 */
class ApiClient(private val station: Station) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val base: String get() = station.baseUrl.trimEnd('/')

    suspend fun getJson(path: String, query: Map<String, String?> = emptyMap()): JsonElement =
        requestJson("GET", path, null, query)

    suspend fun getText(path: String, query: Map<String, String?> = emptyMap()): String =
        requestRaw("GET", path, null, query)

    suspend fun sendJson(
        method: String,
        path: String,
        body: JsonElement? = null,
        query: Map<String, String?> = emptyMap(),
    ): JsonElement = requestJson(method, path, body, query)

    suspend fun requestJson(
        method: String,
        path: String,
        body: JsonElement?,
        query: Map<String, String?> = emptyMap(),
    ): JsonElement = withContext(Dispatchers.IO) {
        val text = execute(method, path, body, query)
        if (text.isBlank()) JsonObject() else JsonParser.parseString(text)
    }

    private suspend fun requestRaw(
        method: String,
        path: String,
        body: JsonElement?,
        query: Map<String, String?> = emptyMap(),
    ): String = withContext(Dispatchers.IO) { execute(method, path, body, query) }

    private fun execute(
        method: String,
        path: String,
        body: JsonElement?,
        query: Map<String, String?>,
    ): String {
        val url = buildString {
            append(base)
            append(if (path.startsWith("/")) path else "/$path")
            val qs = query.entries.filter { it.value != null }
                .joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
            if (qs.isNotEmpty()) append('?').append(qs)
        }

        val req = Request.Builder().url(url)
        when (station.type) {
            StationType.SUB -> req.header("x-api-key", station.password)
            StationType.CPA -> {
                req.header("Authorization", "Bearer ${station.password}")
                req.header("X-Management-Key", station.password)
            }
        }
        req.header("Accept", "application/json")
        req.header("User-Agent", "RelayManager-Android/1.0")

        when (method.uppercase()) {
            "GET" -> req.get()
            "DELETE" -> if (body != null) req.delete(jsonBody(body)) else req.delete()
            else -> req.method(method.uppercase(), jsonBody(body ?: JsonObject()))
        }

        client.newCall(req.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    val o = JsonParser.parseString(text).asJsonObject
                    o.get("message")?.asString ?: o.get("error")?.asString
                }.getOrNull()
                throw ApiException(resp.code, msg ?: text.take(160).ifBlank { "HTTP ${resp.code}" })
            }
            return text
        }
    }

    private fun jsonBody(el: JsonElement) =
        el.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
}
