package com.relay.manager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.relay.manager.data.DayStat
import com.relay.manager.data.Station
import com.relay.manager.data.UsageLedger
import com.relay.manager.net.Sub2Api
import com.relay.manager.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubDetailScreen(station: Station, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val api = remember { Sub2Api(station) }
    val ledger = remember { UsageLedger(ctx, station.id) }
    var tab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = station.name, subtitle = "SUB 站 · ${station.host}", onBack = onBack)

        // Tabs
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("概览", "账号", "用户", "记录").forEachIndexed { i, t ->
                FilterChip(
                    selected = tab == i,
                    onClick = { tab = i },
                    label = { Text(t, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan.copy(alpha = 0.2f),
                        selectedLabelColor = Cyan,
                        containerColor = BgCardLight,
                        labelColor = TextMid
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when (tab) {
            0 -> SubOverview(api, ledger)
            1 -> SubAccounts(api) { scope.launch { toast = it } }
            2 -> SubUsers(api) { scope.launch { toast = it } }
            3 -> SubUsage(api)
        }
    }

    toast?.let {
        AlertDialog(
            onDismissRequest = { toast = null },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("好") } },
            title = { Text("操作结果") },
            text = { Text(it, color = TextMid, fontSize = 13.sp) },
            containerColor = BgCard
        )
    }
}

// ---------------- 概览 ----------------

@Composable
private fun SubOverview(api: Sub2Api, ledger: UsageLedger) {
    var stats by remember { mutableStateOf<JsonObject?>(null) }
    var series by remember { mutableStateOf<List<DayStat>>(emptyList()) }
    var models by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            stats = api.stats()
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val end = fmt.format(Date())
            val start = fmt.format(Date(System.currentTimeMillis() - 29L * 86400_000))
            val t = runCatching { api.trendSeries(start, end) }.getOrDefault(emptyList())
            series = t
            ledger.mergeSubTrend(t)
            models = withContext(Dispatchers.IO) { ledger.modelTotals(30) }.toList().sortedByDescending { it.second }
        } catch (e: Exception) {
            err = e.message
        }
    }

    when {
        err != null -> ErrorBox(err!!) { }
        stats == null -> LoadingBox()
        else -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val s = stats!!
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("今日请求", "", Modifier.weight(1f), "RPM ${s.long("rpm")}", Cyan, valueNum = s.long("today_requests"))
                StatCard("今日 Token", "", Modifier.weight(1f), "TPM ${s.long("tpm")}", Mint, valueNum = s.long("today_tokens"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("今日费用", fmtMoney(s.double("today_actual_cost")), Modifier.weight(1f), "标准 ${fmtMoney(s.double("today_cost"))}", Violet)
                StatCard("累计 Token", "", Modifier.weight(1f), "累计 ${fmtCount(s.long("total_requests"))} 请求", Amber, valueNum = s.long("total_tokens"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("用户", "", Modifier.weight(1f), "今日新增 ${s.long("today_new_users")}", Teal, valueNum = s.long("total_users"))
                StatCard("账号", "${s.long("normal_accounts")}/${s.long("total_accounts")}", Modifier.weight(1f),
                    "异常 ${s.long("error_accounts")} · 限流 ${s.long("ratelimit_accounts")}", if (s.long("error_accounts") > 0) Coral else Mint)
            }

            if (series.any { it.requests > 0 || it.totalTokens > 0 }) {
                SectionTitle("近30日 请求·Token 趋势")
                GlassCard {
                    TrendChart(
                        labels = series.map { it.date },
                        seriesA = series.map { it.requests.toFloat() },
                        seriesB = series.map { it.totalTokens.toFloat() },
                    )
                }
                SectionTitle("每日费用（近14日）", Amber)
                GlassCard {
                    DayBars(series.takeLast(14).map { it.date to it.cost.toFloat() }, color = Amber)
                }
            }
            if (models.isNotEmpty()) {
                SectionTitle("模型 Token 排行（近30日）", Mint)
                GlassCard { RankBars(models.take(10)) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ---------------- 账号 ----------------

@Composable
private fun SubAccounts(api: Sub2Api, toast: suspend (String) -> Unit) {
    var data by remember { mutableStateOf<JsonObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var actionBusy by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try { data = api.accounts(pageSize = 100); err = null } catch (e: Exception) { err = e.message }
    }
    LaunchedEffect(reload) { refresh() }

    fun act(id: Long, label: String, call: suspend () -> Unit) {
        scope.launch {
            actionBusy = id
            try { call(); toast("$label 成功"); refresh() }
            catch (e: Exception) { toast("$label 失败：${e.message}") }
            actionBusy = null
        }
    }

    when {
        err != null -> ErrorBox(err!!) { reload++ }
        data == null -> SkeletonList(5)
        else -> {
            val items = data!!.arr("items") ?: JsonArray()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items.size()) { i ->
                    val a = items[i].asJsonObject
                    val id = a.long("id")
                    val status = a.str("status") ?: ""
                    val sched = a.bool("schedulable") ?: true
                    val statusColor = when {
                        status.contains("error", true) -> Coral
                        status.contains("rate", true) || status.contains("overload", true) -> Amber
                        else -> Mint
                    }
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.str("name") ?: a.str("email") ?: "#$id",
                                    color = TextHi, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                Text(
                                    buildString {
                                        append(a.str("platform") ?: ""); append(" · ")
                                        append(status.ifBlank { "unknown" })
                                        if (!sched) append(" · 已禁用调度")
                                        a.str("type")?.let { append(" · $it") }
                                    },
                                    color = TextMid, fontSize = 11.sp
                                )
                            }
                            Pill(status.ifBlank { "?" }, statusColor)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            SmallBtn("测试", Cyan, actionBusy == id) { act(id, "测试") { api.accountAction(id, "test") } }
                            SmallBtn("刷新", Mint, actionBusy == id) { act(id, "刷新") { api.accountAction(id, "refresh") } }
                            SmallBtn("清错", Amber, actionBusy == id) { act(id, "清错") { api.accountAction(id, "clear-error") } }
                            SmallBtn(if (sched) "停调度" else "开调度", if (sched) Coral else Mint, actionBusy == id) {
                                act(id, "调度切换") { api.setSchedulable(id, !sched) }
                            }
                            SmallBtn("恢复", Violet, actionBusy == id) { act(id, "恢复状态") { api.accountAction(id, "recover-state") } }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun SmallBtn(text: String, color: androidx.compose.ui.graphics.Color, busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !busy,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.defaultMinSize(minHeight = 30.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) { Text(text, fontSize = 11.sp) }
}

// ---------------- 用户 ----------------

@Composable
private fun SubUsers(api: Sub2Api, toast: suspend (String) -> Unit) {
    var data by remember { mutableStateOf<JsonObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try { data = api.users(pageSize = 100, search = search.ifBlank { null }); err = null } catch (e: Exception) { err = e.message }
    }
    LaunchedEffect(reload) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("搜索用户邮箱/用户名", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { reload++ }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("查", fontSize = 13.sp) }
        }
        Spacer(Modifier.height(8.dp))
        when {
            err != null -> ErrorBox(err!!) { reload++ }
            data == null -> SkeletonList(5)
            else -> {
                val items = data!!.arr("items") ?: JsonArray()
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items.size()) { i ->
                        val u = items[i].asJsonObject
                        val id = u.long("id")
                        val st = u.str("status") ?: "active"
                        GlassCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(u.str("email") ?: u.str("username") ?: "#$id", color = TextHi, fontSize = 13.sp)
                                    Text(
                                        "余额 ${fmtMoney(u.double("balance"))} · 并发 ${u.long("concurrency")} · ${u.str("role") ?: "user"}",
                                        color = TextMid, fontSize = 11.sp
                                    )
                                }
                                Pill(if (st == "active") "正常" else "禁用", if (st == "active") Mint else Coral)
                                Spacer(Modifier.width(6.dp))
                                SmallBtn(if (st == "active") "禁用" else "启用", if (st == "active") Coral else Mint, false) {
                                    scope.launch {
                                        try { api.setUserEnabled(id, st != "active"); toast("已更新"); refresh() }
                                        catch (e: Exception) { toast("失败：${e.message}") }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

// ---------------- 用量记录 ----------------

@Composable
private fun SubUsage(api: Sub2Api) {
    var data by remember { mutableStateOf<JsonObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try { data = api.usage(pageSize = 100) } catch (e: Exception) { err = e.message }
    }

    when {
        err != null -> ErrorBox(err!!)
        data == null -> SkeletonList(6)
        else -> {
            val items = data!!.arr("items") ?: data!!.arr("logs") ?: JsonArray()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items.size()) { i ->
                    val u = items[i].asJsonObject
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.str("model") ?: "?", color = TextHi, fontSize = 12.sp, maxLines = 1)
                                Text(
                                    (u.str("created_at") ?: u.str("time") ?: "").take(19).replace("T", " "),
                                    color = TextLow, fontSize = 10.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmtTokens(u.long("total_tokens")), color = Cyan, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(
                                    "入${fmtCount(u.long("input_tokens"))}/出${fmtCount(u.long("output_tokens"))}" +
                                        (if (u.double("actual_cost") > 0) " · ${fmtMoney(u.double("actual_cost"))}" else ""),
                                    color = TextMid, fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

// ---- ext ----
private fun JsonObject.str(k: String): String? =
    if (has(k) && get(k).isJsonPrimitive) runCatching { get(k).asString }.getOrNull() else null

private fun JsonObject.long(k: String): Long =
    if (has(k)) runCatching { get(k).asLong }.getOrDefault(0L) else 0L

private fun JsonObject.double(k: String): Double =
    if (has(k)) runCatching { get(k).asDouble }.getOrDefault(0.0) else 0.0

private fun JsonObject.bool(k: String): Boolean? =
    if (has(k)) runCatching { get(k).asBoolean }.getOrNull() else null

private fun JsonObject.arr(k: String): JsonArray? =
    if (has(k) && get(k).isJsonArray) getAsJsonArray(k) else null
