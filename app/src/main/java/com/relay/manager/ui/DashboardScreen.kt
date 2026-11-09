package com.relay.manager.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relay.manager.data.*
import com.relay.manager.net.CpaApi
import com.relay.manager.net.Sub2Api
import com.relay.manager.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class StationSnap {
    var health = StationHealth(stationId = "")
    var series: List<DayStat> = emptyList()
    var models: Map<String, Long> = emptyMap()
    var accountOk = 0; var accountErr = 0
    var extra = ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    stations: List<Station>,
    onBack: () -> Unit,
    onOpenStation: (Station) -> Unit,
) {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var snaps by remember { mutableStateOf<Map<String, StationSnap>>(emptyMap()) }
    var lastRefresh by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }

    suspend fun load() {
        if (stations.isEmpty()) { loading = false; return }
        loading = snaps.isEmpty()
        error = null
        val map = mutableMapOf<String, StationSnap>()
        coroutineScope {
            stations.map { s ->
                async(Dispatchers.IO) {
                    val snap = StationSnap()
                    snap.health.stationId = s.id
                    val ledger = UsageLedger(ctx, s.id)
                    try {
                        if (s.type == StationType.SUB) {
                            loadSub(ctx, s, ledger, snap)
                        } else {
                            loadCpa(ctx, s, ledger, snap)
                        }
                        snap.health.online = true
                    } catch (e: Exception) {
                        snap.health.online = false
                        snap.health.error = e.message ?: "连接失败"
                    }
                    snap.series = ledger.series(30)
                    snap.models = ledger.modelTotals(30)
                    map[s.id] = snap
                }
            }.awaitAll()
        }
        snaps = map
        loading = false
        lastRefresh = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }

    LaunchedEffect(tick, stations.size) { load() }
    // 自动刷新 60s
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); tick++ }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "数据大屏",
            subtitle = "全部站点汇总 · 每 60s 自动刷新" + if (lastRefresh.isNotEmpty()) " · 更新于 $lastRefresh" else "",
            onBack = onBack,
            actions = {
                TextButton(onClick = { tick++ }) { Text("刷新", color = Cyan) }
            }
        )

        when {
            loading -> LoadingBox("正在汇总各站数据…")
            stations.isEmpty() -> EmptyBox("先添加站点再看大屏")
            else -> {
                val total = aggregate(snaps.values.toList())
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ---- KPI 行 ----
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("今日请求", "", Modifier.weight(1f), "全站合计", Cyan, valueNum = total.todayRequests)
                        StatCard("今日 Token", "", Modifier.weight(1f), "流出合计", Mint, valueNum = total.todayTokens)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("近30日 Token", "", Modifier.weight(1f), "本地账本累计", Violet, valueNum = total.monthTokens)
                        StatCard("在线站点", "${total.online}/${stations.size}", Modifier.weight(1f),
                            if (total.errAccounts > 0) "异常账号 ${total.errAccounts}" else "账号全部正常",
                            if (total.online == stations.size) Mint else Amber)
                    }

                    error?.let { Text(it, color = Amber, fontSize = 12.sp) }

                    // ---- 站点状态 ----
                    SectionTitle("站点状态")
                    stations.forEach { s ->
                        val snap = snaps[s.id]
                        StationHealthRow(s, snap, onOpenStation)
                    }

                    // ---- 趋势图 ----
                    val comb = combineSeries(snaps.values.flatMap { it.series }, 30)
                    if (comb.any { it.requests > 0 || it.totalTokens > 0 }) {
                        SectionTitle("每日请求 · Token 流出趋势（近30日）")
                        GlassCard {
                            TrendChart(
                                labels = comb.map { it.date },
                                seriesA = comb.map { it.requests.toFloat() },
                                seriesB = comb.map { it.totalTokens.toFloat() },
                                nameA = "请求数", nameB = "Token"
                            )
                        }
                    }

                    // ---- 站点对比 ----
                    val byStation = snaps.values.mapNotNull { sn ->
                        val st = stations.firstOrNull { it.id == sn.health.stationId } ?: return@mapNotNull null
                        val t = sn.series.takeLast(7).sumOf { it.totalTokens }
                        if (t > 0) "${st.name}" to t else null
                    }.sortedByDescending { it.second }
                    if (byStation.size > 1) {
                        SectionTitle("站点 Token 流出对比（近7日）", Violet)
                        GlassCard { RankBars(byStation.take(8)) }
                    }

                    // ---- 模型排行 ----
                    val modelAcc = LinkedHashMap<String, Long>()
                    snaps.values.forEach { sn -> sn.models.forEach { (m, v) -> modelAcc[m] = (modelAcc[m] ?: 0) + v } }
                    val topModels = modelAcc.toList().sortedByDescending { it.second }.take(10)
                    if (topModels.isNotEmpty()) {
                        SectionTitle("模型 Token 排行 TOP10（近30日）", Mint)
                        GlassCard { RankBars(topModels) }
                    }

                    // ---- 请求构成 ----
                    if (comb.any { it.requests > 0 }) {
                        SectionTitle("每日请求量（近14日）", Amber)
                        GlassCard {
                            DayBars(comb.takeLast(14).map { it.date to it.requests.toFloat() }, color = Amber)
                        }
                    }

                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun StationHealthRow(s: Station, snap: StationSnap?, onOpen: (Station) -> Unit) {
    val online = snap?.health?.online == true
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(online)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, color = TextHi, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Pill(s.type.name, if (s.type == StationType.CPA) Cyan else Mint)
                }
                if (snap != null) {
                    Text(
                        if (online)
                            "今日 ${fmtCount(snap.health.todayRequests)} 请求 · ${fmtTokens(snap.health.todayTokens)} Token" +
                                (snap.accountErr.takeIf { it > 0 }?.let { " · 异常账号 $it" } ?: "")
                        else "离线 · ${snap.health.error ?: "连接失败"}",
                        color = if (online) TextMid else Coral, fontSize = 11.sp
                    )
                    if (snap.health.version.isNotEmpty()) {
                        Text(snap.health.version, color = TextLow, fontSize = 10.sp)
                    }
                } else {
                    Text("探测中…", color = TextLow, fontSize = 11.sp)
                }
            }
            TextButton(onClick = { onOpen(s) }) { Text("管理 ›", color = Cyan, fontSize = 12.sp) }
        }
    }
}

private class Totals {
    var todayRequests = 0L
    var todayTokens = 0L
    var monthTokens = 0L
    var online = 0
    var errAccounts = 0
}

private fun aggregate(snaps: List<StationSnap>): Totals {
    val t = Totals()
    snaps.forEach { sn ->
        if (sn.health.online) t.online++
        t.todayRequests += sn.health.todayRequests
        t.todayTokens += sn.health.todayTokens
        t.monthTokens += sn.series.sumOf { it.totalTokens }
        t.errAccounts += sn.accountErr
    }
    return t
}

/** 跨站点按日期合并 */
private fun combineSeries(all: List<DayStat>, days: Int): List<DayStat> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val byDate = all.groupBy { it.date }
    return (days - 1 downTo 0).map { i ->
        val d = fmt.format(Date(System.currentTimeMillis() - i * 86400_000L))
        val items = byDate[d] ?: emptyList()
        DayStat(
            date = d,
            requests = items.sumOf { it.requests },
            failed = items.sumOf { it.failed },
            inputTokens = items.sumOf { it.inputTokens },
            outputTokens = items.sumOf { it.outputTokens },
            totalTokens = items.sumOf { it.totalTokens },
            cost = items.sumOf { it.cost },
        )
    }
}

// ---------- 站点数据加载 ----------

private suspend fun loadSub(ctx: Context, s: Station, ledger: UsageLedger, snap: StationSnap) {
    val api = Sub2Api(s)
    val stats = api.stats()
    snap.health.apply {
        todayRequests = stats.long("today_requests")
        todayTokens = stats.long("today_tokens")
        version = "用户 ${stats.long("total_users")} · 账号 ${stats.long("normal_accounts")}/${stats.long("total_accounts")} 正常 · RPM ${stats.long("rpm")}"
        detail = "累计 ${fmtCount(stats.long("total_tokens"))} Token · 费用 $${"%.2f".format(stats.double("total_actual_cost"))}"
    }
    snap.accountOk = stats.long("normal_accounts").toInt()
    snap.accountErr = (stats.long("error_accounts") + stats.long("ratelimit_accounts") + stats.long("overload_accounts")).toInt()

    // 近30天趋势 → 账本
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val end = fmt.format(Date())
    val start = fmt.format(Date(System.currentTimeMillis() - 29L * 86400_000))
    val trend = runCatching { api.trendSeries(start, end) }.getOrDefault(emptyList())
    ledger.mergeSubTrend(trend)

    // 模型分布
    val models = runCatching {
        val arr = api.modelStats(start, end)
        buildMap<String, Long> {
            arr?.forEach { el ->
                runCatching {
                    val o = el.asJsonObject
                    val name = o.get("model")?.asString ?: return@runCatching
                    val tok = runCatching { o.get("total_tokens")?.asLong ?: 0L }.getOrDefault(0L)
                    if (tok > 0) this[name] = tok
                }
            }
        }
    }.getOrDefault(emptyMap())
    if (models.isNotEmpty()) ledger.mergeSubModels(models)
}

private suspend fun loadCpa(ctx: Context, s: Station, ledger: UsageLedger, snap: StationSnap) {
    val api = CpaApi(s)
    // 抽取用量队列（弹出式）→ 本地账本
    val drained = runCatching {
        api.drainUsageQueue(500).mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
    }.getOrDefault(emptyList())
    ledger.recordCpa(drained)

    val creds = runCatching { api.credentials() }.getOrNull()
    val files = creds?.getAsJsonArray("files")
    var ok = 0; var err = 0
    files?.forEach { f ->
        runCatching {
            val o = f.asJsonObject
            val disabled = o.get("disabled")?.asBoolean == true
            val unavailable = o.get("unavailable")?.asBoolean == true
            val status = o.get("status")?.asString ?: ""
            if (disabled || unavailable || status.contains("error", true) || status.contains("cooldown", true)) err++ else ok++
        }
    }
    snap.accountOk = ok; snap.accountErr = err

    val ver = api.latestVersion()
    val today = ledger.series(1).firstOrNull()
    snap.health.apply {
        todayRequests = today?.requests ?: 0
        todayTokens = today?.totalTokens ?: 0
        version = buildString {
            append("凭证 $ok 正常 / $err 异常")
            if (!ver.isNullOrBlank()) append(" · 最新版 $ver")
            if (drained.isNotEmpty()) append(" · 刚抽取 ${drained.size} 条用量")
        }
    }
}

// ---- JsonObject ext ----
private fun com.google.gson.JsonObject.long(k: String): Long =
    if (has(k)) runCatching { get(k).asLong }.getOrDefault(0L) else 0L

private fun com.google.gson.JsonObject.double(k: String): Double =
    if (has(k)) runCatching { get(k).asDouble }.getOrDefault(0.0) else 0.0
