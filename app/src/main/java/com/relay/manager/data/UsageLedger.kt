package com.relay.manager.data

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地每日用量账本。
 * CPA 的 usage-queue 是弹出式队列，App 每次拉取后在此聚合落盘，
 * 形成"每日站点 token 流出/请求数"的持久明细。
 */
class UsageLedger(context: Context, private val stationId: String) {

    private val file: File = File(context.filesDir, "ledger_$stationId.json")
    private val lock = Any()

    private fun read(): JsonObject = synchronized(lock) {
        try {
            if (file.exists()) JsonParser.parseString(file.readText()).asJsonObject else JsonObject()
        } catch (_: Exception) {
            JsonObject()
        }
    }

    private fun write(root: JsonObject) = synchronized(lock) {
        runCatching { file.writeText(root.toString()) }
    }

    /** 记录一条 CPA usage-queue 记录 */
    fun recordCpa(records: List<JsonObject>) {
        if (records.isEmpty()) return
        val root = read()
        val days = root.obj("days")
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        records.forEach { rec ->
            val ts = rec.str("timestamp") ?: return@forEach
            val date = runCatching {
                fmt.format(Date(java.time.Instant.parse(ts).toEpochMilli()))
            }.getOrElse { ts.take(10) }
            val day = days.obj(date)
            day.addProperty("requests", day.long("requests") + 1)
            if (rec.bool("failed")) day.addProperty("failed", day.long("failed") + 1)
            val t = rec.objOrNull("tokens")
            val input = t?.long("input_tokens") ?: 0
            val output = t?.long("output_tokens") ?: 0
            val total = t?.long("total_tokens") ?: (input + output)
            day.addProperty("input", day.long("input") + input)
            day.addProperty("output", day.long("output") + output)
            day.addProperty("total", day.long("total") + total)
            val model = rec.str("model")?.ifBlank { null } ?: rec.str("alias") ?: "unknown"
            val models = day.obj("models")
            val m = models.obj(model)
            m.addProperty("requests", m.long("requests") + 1)
            m.addProperty("total", m.long("total") + total)
        }
        root.addProperty("drained", root.long("drained") + records.size)
        root.addProperty("lastDrain", System.currentTimeMillis())
        write(root)
    }

    /** 写入 SUB 服务端 trend（覆盖式，服务端数据为准） */
    fun mergeSubTrend(trend: List<DayStat>) {
        val root = read()
        val days = root.obj("days")
        trend.forEach { s ->
            val day = days.obj(s.date)
            day.addProperty("requests", s.requests)
            day.addProperty("input", s.inputTokens)
            day.addProperty("output", s.outputTokens)
            day.addProperty("total", s.totalTokens)
            day.addProperty("cost", s.cost)
            day.add("models", JsonObject()) // 服务端 trend 不含模型拆分，由 /models 接口提供
        }
        write(root)
    }

    /** 合并 SUB 模型分布（按天不可拆分，记录到 models 汇总区） */
    fun mergeSubModels(models: Map<String, Long>) {
        val root = read()
        val agg = root.obj("subModels")
        models.forEach { (m, v) -> agg.addProperty(m, v) }
        write(root)
    }

    fun series(days: Int): List<DayStat> {
        val root = read()
        val obj = root.objOrNull("days") ?: return emptyList()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = ArrayList<DayStat>()
        for (i in days - 1 downTo 0) {
            val d = fmt.format(Date(System.currentTimeMillis() - i * 86400_000L))
            val e = obj.objOrNull(d)
            out += if (e == null) {
                DayStat(date = d)
            } else {
                DayStat(
                    date = d,
                    requests = e.long("requests"),
                    failed = e.long("failed"),
                    inputTokens = e.long("input"),
                    outputTokens = e.long("output"),
                    totalTokens = e.long("total"),
                    cost = e.double("cost"),
                    models = e.objOrNull("models")?.entrySet()?.associate { it.key to it.value.asJsonObject.long("total") } ?: emptyMap(),
                )
            }
        }
        return out
    }

    /** 最近 N 天的模型 token 汇总 */
    fun modelTotals(days: Int): Map<String, Long> {
        val s = series(days)
        val acc = LinkedHashMap<String, Long>()
        s.forEach { d -> d.models.forEach { (m, v) -> acc[m] = (acc[m] ?: 0) + v } }
        // 并入 SUB 汇总模型（无法按天拆的，记为全量展示）
        val root = read()
        root.objOrNull("subModels")?.entrySet()?.forEach { e ->
            if (!acc.containsKey(e.key)) acc[e.key] = e.value.asLong
        }
        return acc.toList().sortedByDescending { it.second }.toMap()
    }

    fun drainedCount(): Long = read().long("drained")

    fun clear() = synchronized(lock) { runCatching { file.delete() } }

    // ---- JsonObject helpers ----
    private fun JsonObject.obj(k: String): JsonObject {
        if (!has(k) || !get(k).isJsonObject) add(k, JsonObject())
        return getAsJsonObject(k)
    }

    private fun JsonObject.objOrNull(k: String): JsonObject? =
        if (has(k) && get(k).isJsonObject) getAsJsonObject(k) else null

    private fun JsonObject.str(k: String): String? =
        if (has(k) && get(k).isJsonPrimitive) runCatching { get(k).asString }.getOrNull() else null

    private fun JsonObject.long(k: String): Long =
        if (has(k)) runCatching { get(k).asLong }.getOrDefault(0L) else 0L

    private fun JsonObject.double(k: String): Double =
        if (has(k)) runCatching { get(k).asDouble }.getOrDefault(0.0) else 0.0

    private fun JsonObject.bool(k: String): Boolean =
        if (has(k)) runCatching { get(k).asBoolean }.getOrDefault(false) else false
}
