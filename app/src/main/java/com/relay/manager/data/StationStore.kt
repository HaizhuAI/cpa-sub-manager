package com.relay.manager.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** 站点列表持久化（SharedPreferences + JSON） */
class StationStore(context: Context) {

    private val prefs = context.getSharedPreferences("stations", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun list(): List<Station> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JsonParser.parseString(raw).asJsonArray
            arr.mapNotNull { el ->
                runCatching {
                    val o = el.asJsonObject
                    Station(
                        id = o.str("id") ?: return@runCatching null,
                        name = o.str("name") ?: "未命名",
                        type = StationType.of(o.str("type")),
                        baseUrl = o.str("baseUrl") ?: return@runCatching null,
                        password = o.str("password") ?: "",
                        createdAt = o.long("createdAt") ?: 0L,
                    )
                }.getOrNull()
            }.sortedBy { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(station: Station) = save(list() + station)

    fun update(station: Station) = save(list().map { if (it.id == station.id) station else it })

    fun remove(id: String) = save(list().filterNot { it.id == id })

    fun get(id: String): Station? = list().firstOrNull { it.id == id }

    private fun save(stations: List<Station>) {
        val arr = JsonArray()
        stations.forEach { s ->
            arr.add(JsonObject().apply {
                addProperty("id", s.id)
                addProperty("name", s.name)
                addProperty("type", s.type.name)
                addProperty("baseUrl", s.baseUrl)
                addProperty("password", s.password)
                addProperty("createdAt", s.createdAt)
            })
        }
        prefs.edit().putString(KEY, gson.toJson(arr)).apply()
    }

    private fun JsonObject.str(k: String): String? =
        if (has(k) && !get(k).isJsonNull) get(k).asString else null

    private fun JsonObject.long(k: String): Long? =
        if (has(k) && !get(k).isJsonNull) runCatching { get(k).asLong }.getOrNull() else null

    companion object {
        private const val KEY = "station_list"
    }
}
