package com.relay.manager.net

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.relay.manager.data.Station

/**
 * CLIProxyAPI 管理 API。
 * v8 路径 /v8/management ；v0 兼容路径 /v0/management 。
 * 认证: Authorization: Bearer + X-Management-Key: <管理密钥>
 * 先探测 v8，404 时回落 v0 路径。
 */
class CpaApi(station: Station) {

    private val c = ApiClient(station)
    private var v8: Boolean? = null

    private suspend fun isV8(): Boolean {
        v8?.let { return it }
        return try {
            c.getJson("/v8/management/config")
            v8 = true
            true
        } catch (e: ApiException) {
            if (e.code == 404) { v8 = false; false } else throw e
        }
    }

    private suspend fun path(v8Path: String, v0Path: String): String =
        if (isV8()) "/v8/management$v8Path" else "/v0/management$v0Path"

    // ---------- 配置 / 版本 ----------

    suspend fun config(): JsonObject =
        c.getJson(path("/config", "/config")).asObj()

    suspend fun latestVersion(): String? = runCatching {
        val o = c.getJson(path("/server/latest-version", "/latest-version")).asObj()
        o.str("latest-version") ?: o.str("latest_version") ?: o.str("version")
    }.getOrNull()

    // ---------- 凭证（上游账号） ----------

    suspend fun credentials(): JsonObject =
        c.getJson(path("/credentials", "/auth-files")).asObj()

    suspend fun setCredentialDisabled(name: String, disabled: Boolean): JsonElement =
        c.sendJson(
            "PATCH",
            path("/credentials/status", "/auth-files/status"),
            JsonObject().apply { addProperty("name", name); addProperty("disabled", disabled) }
        )

    suspend fun refreshCredential(name: String? = null, all: Boolean = false): JsonElement =
        c.sendJson(
            "POST",
            path("/credentials/refresh", "/auth-files/refresh"),
            JsonObject().apply {
                if (all) addProperty("all", true) else addProperty("name", name ?: "")
            }
        )

    suspend fun deleteCredential(name: String): JsonElement =
        c.sendJson("DELETE", path("/credentials", "/auth-files"), query = mapOf("name" to name))

    suspend fun verifyCredential(name: String): JsonElement =
        runCatching {
            c.sendJson("POST", "/v8/management/credentials/verify",
                JsonObject().apply { addProperty("name", name) })
        }.getOrElse { JsonObject().apply { addProperty("error", it.message) } }

    suspend fun resetCooldowns(): JsonElement =
        c.sendJson("POST", path("/routing/cooldown/reset", "/reset-quota"), JsonObject())

    // ---------- 用量 ----------

    /** 弹出式队列：取回即从服务端移除，调用方需本地落盘聚合 */
    suspend fun drainUsageQueue(count: Int = 200): JsonArray {
        val el = c.getJson(path("/observability/usage/queue", "/usage-queue"), mapOf("count" to count.toString()))
        return if (el.isJsonArray) el.asJsonArray else JsonArray()
    }

    suspend fun apiKeyUsage(): JsonElement =
        c.getJson(path("/observability/usage/api-keys", "/api-key-usage"))

    // ---------- 客户端 API Key ----------

    suspend fun apiKeys(): JsonElement = runCatching {
        // v0 端点在 v8 服务上同样可用，且语义最贴合"客户端 key 列表"
        c.getJson("/v0/management/api-keys")
    }.getOrElse {
        // 兜底：从完整配置里读 access/api-keys
        val cfg = runCatching { config() }.getOrDefault(JsonObject())
        cfg.deepFind("api-keys", "api_keys", "apiKeys") ?: JsonArray()
    }

    suspend fun apiKeyList(): List<String> {
        val el = apiKeys()
        val arr = when {
            el.isJsonArray -> el.asJsonArray
            el.isJsonObject && el.asJsonObject.has("api-keys") -> el.asJsonObject.getAsJsonArray("api-keys")
            else -> JsonArray()
        }
        return arr.mapNotNull { runCatching { it.asString }.getOrNull() }
    }

    suspend fun addApiKey(key: String): JsonElement {
        val cur = apiKeyList().toMutableList()
        if (key !in cur) cur += key
        val arr = JsonArray().apply { cur.forEach { add(it) } }
        return c.sendJson("PUT", "/v0/management/api-keys", arr)
    }

    suspend fun deleteApiKey(key: String): JsonElement =
        c.sendJson("DELETE", "/v0/management/api-keys", query = mapOf("value" to key))

    // ---------- 日志 ----------

    suspend fun logs(limit: Int = 200, after: String? = null): JsonElement =
        c.getJson(path("/observability/logs", "/logs"), mapOf("limit" to limit.toString(), "after" to after))

    suspend fun requestErrorLogs(): JsonElement =
        c.getJson(path("/observability/logs/errors", "/request-error-logs"))

    // ---------- helpers ----------

    private fun JsonElement.asObj(): JsonObject = if (isJsonObject) asJsonObject else JsonObject()

    private fun JsonObject.str(k: String): String? =
        if (has(k) && get(k).isJsonPrimitive) runCatching { get(k).asString }.getOrNull() else null

    private fun JsonObject.deepFind(vararg keys: String): JsonElement? {
        for (k in keys) if (has(k)) return get(k)
        for (e in entrySet()) {
            if (e.value.isJsonObject) {
                val r = e.value.asJsonObject.deepFind(*keys)
                if (r != null) return r
            }
        }
        return null
    }
}
