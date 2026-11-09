package com.relay.manager.data

import java.util.UUID

enum class StationType(val label: String, val hint: String) {
    CPA("CPA 站", "CLIProxyAPI · 管理密钥"),
    SUB("SUB 站", "sub2api · 管理 API 密钥");

    companion object {
        fun of(name: String?): StationType =
            entries.firstOrNull { it.name == name } ?: CPA
    }
}

data class Station(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: StationType,
    val baseUrl: String,          // e.g. https://cpa.example.com  (no trailing slash)
    val password: String,         // 管理密钥
    val createdAt: Long = System.currentTimeMillis(),
) {
    val host: String get() = baseUrl.removePrefix("http://").removePrefix("https://").trimEnd('/')
}

/** 站点实时概况（用于列表与聚合大屏的健康指示） */
data class StationHealth(
    var stationId: String,
    var online: Boolean = false,
    var version: String = "",
    var detail: String = "",
    var error: String? = null,
    var todayRequests: Long = 0,
    var todayTokens: Long = 0,
)

/** 统一日度统计桶（本地聚合） */
data class DayStat(
    val date: String,             // yyyy-MM-dd
    val requests: Long = 0,
    val failed: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val cost: Double = 0.0,
    val models: Map<String, Long> = emptyMap(), // model -> totalTokens
)
