package com.relay.manager.net

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.relay.manager.data.DayStat
import com.relay.manager.data.Station

/**
 * sub2api 管理 API。Base: {host}/api/v1/admin
 * 认证: x-api-key: <管理 API 密钥>
 * 响应封套: {code, message, data}，分页: data.{items,total,page,page_size}
 */
class Sub2Api(station: Station) {

    private val c = ApiClient(station)

    private suspend fun get(path: String, query: Map<String, String?> = emptyMap()): JsonElement {
        val root = c.getJson("/api/v1/admin$path", query)
        if (root.isJsonObject) {
            val o = root.asJsonObject
            val code = o.int("code") ?: 0
            if (code != 0) throw ApiException(code, o.str("message") ?: "业务错误 code=$code")
            return o.get("data") ?: JsonObject()
        }
        return root
    }

    private suspend fun send(method: String, path: String, body: JsonObject? = null): JsonElement {
        val root = c.sendJson(method, "/api/v1/admin$path", body)
        if (root.isJsonObject) {
            val o = root.asJsonObject
            val code = o.int("code") ?: 0
            if (code != 0) throw ApiException(code, o.str("message") ?: "业务错误 code=$code")
            return o.get("data") ?: JsonObject()
        }
        return root
    }

    // ---------- 仪表盘 ----------

    suspend fun stats(): JsonObject = get("/dashboard/stats").asObj()

    suspend fun trend(startDate: String, endDate: String, granularity: String = "day"): JsonObject =
        get("/dashboard/trend", mapOf("start_date" to startDate, "end_date" to endDate, "granularity" to granularity)).asObj()

    suspend fun trendSeries(startDate: String, endDate: String): List<DayStat> {
        val arr = trend(startDate, endDate).arr("trend") ?: return emptyList()
        return arr.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                DayStat(
                    date = o.str("date") ?: return@runCatching null,
                    requests = o.long("requests"),
                    inputTokens = o.long("input_tokens"),
                    outputTokens = o.long("output_tokens"),
                    totalTokens = o.long("total_tokens"),
                    cost = o.double("actual_cost").let { if (it == 0.0) o.double("cost") else it },
                )
            }.getOrNull()
        }
    }

    /** 模型维度统计（近 range 天） */
    suspend fun modelStats(startDate: String, endDate: String): JsonArray? {
        val data = get("/dashboard/models", mapOf("start_date" to startDate, "end_date" to endDate))
        return data.arrOrNull("models") ?: data.arrOrNull("items")
    }

    suspend fun groupStats(startDate: String, endDate: String): JsonElement =
        get("/dashboard/groups", mapOf("start_date" to startDate, "end_date" to endDate))

    suspend fun usersRanking(): JsonElement =
        get("/dashboard/users-ranking", mapOf("limit" to "10"))

    suspend fun opsRealtime(): JsonObject =
        runCatching { get("/ops/realtime-traffic").asObj() }.getOrDefault(JsonObject())

    suspend fun opsConcurrency(): JsonElement =
        runCatching { get("/ops/concurrency") }.getOrDefault(JsonObject())

    suspend fun opsAccountAvailability(): JsonElement =
        runCatching { get("/ops/account-availability") }.getOrDefault(JsonObject())

    // ---------- 账号 ----------

    suspend fun accounts(page: Int = 1, pageSize: Int = 50, search: String? = null, status: String? = null, platform: String? = null): JsonObject =
        get(
            "/accounts",
            mapOf(
                "page" to page.toString(),
                "page_size" to pageSize.toString(),
                "search" to search,
                "status" to status,
                "platform" to platform,
            )
        ).asObj()

    suspend fun accountAction(id: Long, action: String): JsonElement =
        send("POST", "/accounts/$id/$action", JsonObject())

    suspend fun setSchedulable(id: Long, schedulable: Boolean): JsonElement =
        send("POST", "/accounts/$id/schedulable", JsonObject().apply { addProperty("schedulable", schedulable) })

    suspend fun deleteAccount(id: Long): JsonElement = send("DELETE", "/accounts/$id")

    suspend fun accountStats(id: Long): JsonElement = get("/accounts/$id/stats")

    // ---------- 用户 / 分组 / Key ----------

    suspend fun users(page: Int = 1, pageSize: Int = 50, search: String? = null): JsonObject =
        get("/users", mapOf("page" to page.toString(), "page_size" to pageSize.toString(), "search" to search)).asObj()

    suspend fun setUserEnabled(id: Long, enabled: Boolean): JsonElement =
        send("PUT", "/users/$id", JsonObject().apply { addProperty("status", if (enabled) "active" else "disabled") })

    suspend fun updateUserBalance(id: Long, amount: Double, operation: String = "add", note: String = "app"): JsonElement =
        send("POST", "/users/$id/balance", JsonObject().apply {
            addProperty("balance", amount); addProperty("operation", operation); addProperty("notes", note)
        })

    suspend fun groupsAll(): JsonElement = get("/groups/all")

    suspend fun usage(page: Int = 1, pageSize: Int = 50): JsonObject =
        get("/usage", mapOf("page" to page.toString(), "page_size" to pageSize.toString())).asObj()

    suspend fun apiKeysOfUser(userId: Long): JsonElement = get("/users/$userId/api-keys")

    // ---------- helpers ----------

    private fun JsonElement.asObj(): JsonObject = if (isJsonObject) asJsonObject else JsonObject()

    private fun JsonObject.arr(k: String): JsonArray? =
        if (has(k) && get(k).isJsonArray) getAsJsonArray(k) else null

    private fun JsonElement.arrOrNull(k: String): JsonArray? =
        if (isJsonObject && asJsonObject.has(k) && asJsonObject.get(k).isJsonArray) asJsonObject.getAsJsonArray(k)
        else if (isJsonArray) asJsonArray else null

    private fun JsonObject.str(k: String): String? =
        if (has(k) && get(k).isJsonPrimitive) runCatching { get(k).asString }.getOrNull() else null

    private fun JsonObject.int(k: String): Int? =
        if (has(k)) runCatching { get(k).asInt }.getOrNull() else null

    private fun JsonObject.long(k: String): Long =
        if (has(k)) runCatching { get(k).asLong }.getOrDefault(0L) else 0L

    private fun JsonObject.double(k: String): Double =
        if (has(k)) runCatching { get(k).asDouble }.getOrDefault(0.0) else 0.0
}
