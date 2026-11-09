package com.relay.manager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.relay.manager.data.DayStat
import com.relay.manager.data.Station
import com.relay.manager.data.UsageLedger
import com.relay.manager.net.CpaApi
import com.relay.manager.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpaDetailScreen(station: Station, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val api = remember { CpaApi(station) }
    val ledger = remember { UsageLedger(ctx, station.id) }
    var tab by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = station.name, subtitle = "CPA 站 · ${station.host}", onBack = onBack)

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("概览", "凭证", "密钥", "日志").forEachIndexed { i, t ->
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
            0 -> CpaOverview(api, ledger) { toast = it }
            1 -> CpaCredentials(api) { toast = it }
            2 -> CpaKeys(api) { toast = it }
            3 -> CpaLogs(api)
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
private fun CpaOverview(api: CpaApi, ledger: UsageLedger, toast: (String) -> Unit) {
    var config by remember { mutableStateOf<JsonObject?>(null) }
    var keyUsage by remember { mutableStateOf<JsonElement?>(null) }
    var series by remember { mutableStateOf<List<DayStat>>(emptyList()) }
    var models by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var drained by remember { mutableStateOf(0) }
    var ver by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            drained = api.drainUsageQueue(500).let { arr ->
                val objs = arr.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
                ledger.recordCpa(objs); objs.size
            }
            config = runCatching { api.config() }.getOrNull()
            keyUsage = runCatching { api.apiKeyUsage() }.getOrNull()
            ver = api.latestVersion()
            series = ledger.series(30)
            models = ledger.modelTotals(30).toList().sortedByDescending { it.second }
            err = null
        } catch (e: Exception) {
            err = e.message
        }
    }
    LaunchedEffect(reload) { refresh() }

    when {
        err != null -> ErrorBox(err!!) { reload++ }
        config == null -> LoadingBox()
        else -> {
            val today = series.lastOrNull() ?: DayStat("")
            val month = series.sumOf { it.totalTokens }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("今日请求", "", Modifier.weight(1f), "失败 ${today.failed}", Cyan, valueNum = today.requests)
                    StatCard("今日 Token", "", Modifier.weight(1f), "本地账本", Mint, valueNum = today.totalTokens)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("近30日 Token", "", Modifier.weight(1f), "队列累计", Violet, valueNum = month)
                    StatCard("版本", config?.str("version") ?: ver ?: "-", Modifier.weight(1f),
                        if (ver != null) "最新 $ver" else null, Amber)
                }
                if (drained > 0) {
                    Text("已从用量队列抽取 $drained 条记录入库（累计 ${ledger.drainedCount()}）", color = Teal, fontSize = 11.sp)
                } else {
                    Text("CPA 用量为弹出式队列：本页每次刷新自动抽取并本地聚合", color = TextLow, fontSize = 11.sp)
                }

                if (series.any { it.requests > 0 }) {
                    SectionTitle("每日请求 · Token 趋势（近30日）")
                    GlassCard {
                        TrendChart(
                            labels = series.map { it.date },
                            seriesA = series.map { it.requests.toFloat() },
                            seriesB = series.map { it.totalTokens.toFloat() },
                        )
                    }
                }
                if (models.isNotEmpty()) {
                    SectionTitle("模型 Token 排行（本地账本）", Mint)
                    GlassCard { RankBars(models.take(10)) }
                }

                // API Key 用量
                val ku = keyUsage
                if (ku != null && ku.isJsonObject) {
                    SectionTitle("客户端 Key 用量", Amber)
                    GlassCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ku.asJsonObject.entrySet().take(10).forEach { (provider, perKey) ->
                                if (perKey.isJsonObject) {
                                    perKey.asJsonObject.entrySet().forEach { (key, v) ->
                                        val o = v.asJsonObject
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(key.take(24), color = TextHi, fontSize = 12.sp, maxLines = 1)
                                                Text(provider, color = TextLow, fontSize = 10.sp)
                                            }
                                            Text("✓${o.long("success")} ✗${o.long("failed")}", color = TextMid, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SectionTitle("运维操作", Coral)
                GlassCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    try { api.resetCooldowns(); toast("已重置所有凭证冷却") } catch (e: Exception) { toast("失败：${e.message}") }
                                    busy = false
                                }
                            }
                        ) { Text("重置冷却", fontSize = 12.sp) }
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    try { api.refreshCredential(all = true); toast("已触发全部凭证刷新") } catch (e: Exception) { toast("失败：${e.message}") }
                                    busy = false
                                }
                            }
                        ) { Text("刷新全部凭证", fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

// ---------------- 凭证 ----------------

@Composable
private fun CpaCredentials(api: CpaApi, toast: (String) -> Unit) {
    var files by remember { mutableStateOf<JsonArray?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            files = api.credentials().getAsJsonArray("files") ?: JsonArray()
            err = null
        } catch (e: Exception) { err = e.message }
    }
    LaunchedEffect(reload) { refresh() }

    when {
        err != null -> ErrorBox(err!!) { reload++ }
        files == null -> SkeletonList(4)
        files!!.size() == 0 -> EmptyBox("暂无凭证")
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(files!!.size()) { i ->
                val f = files!![i].asJsonObject
                val name = f.str("name") ?: "?"
                val disabled = f.bool("disabled") ?: false
                val unavailable = f.bool("unavailable") ?: false
                val status = f.str("status") ?: ""
                val ok = !disabled && !unavailable && !status.contains("error", true)
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(ok)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.str("email") ?: f.str("label") ?: name, color = TextHi, fontSize = 13.sp, maxLines = 1)
                            Text(
                                "${f.str("provider") ?: ""} · ${status.ifBlank { if (disabled) "disabled" else "active" }}" +
                                    " · ✓${f.long("success")} ✗${f.long("failed")}",
                                color = TextMid, fontSize = 11.sp
                            )
                            if (name != (f.str("email") ?: "")) Text(name, color = TextLow, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CpaBtn(if (disabled) "启用" else "停用", if (disabled) Mint else Amber) {
                            scope.launch {
                                busy = name
                                try { api.setCredentialDisabled(name, !disabled); toast("已${if (disabled) "启用" else "停用"} $name"); refresh() }
                                catch (e: Exception) { toast("失败：${e.message}") }
                                busy = null
                            }
                        }
                        CpaBtn("刷新", Cyan) {
                            scope.launch {
                                busy = name
                                try { api.refreshCredential(name); toast("已刷新 $name"); refresh() }
                                catch (e: Exception) { toast("失败：${e.message}") }
                                busy = null
                            }
                        }
                        CpaBtn("验证", Violet) {
                            scope.launch {
                                busy = name
                                try {
                                    val r = api.verifyCredential(name)
                                    toast(if (r.asObj().has("error")) "验证失败：${r.asObj().get("error").asString}" else "验证通过：$name")
                                } catch (e: Exception) { toast("失败：${e.message}") }
                                busy = null
                            }
                        }
                        CpaBtn("删除", Coral) {
                            scope.launch {
                                busy = name
                                try { api.deleteCredential(name); toast("已删除 $name"); refresh() }
                                catch (e: Exception) { toast("失败：${e.message}") }
                                busy = null
                            }
                        }
                    }
                    if (busy == name) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = Cyan)
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun CpaBtn(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.defaultMinSize(minHeight = 30.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) { Text(text, fontSize = 11.sp) }
}

// ---------------- 客户端密钥 ----------------

@Composable
private fun CpaKeys(api: CpaApi, toast: (String) -> Unit) {
    var keys by remember { mutableStateOf<List<String>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var newKey by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try { keys = api.apiKeyList(); err = null } catch (e: Exception) { err = e.message }
    }
    LaunchedEffect(reload) { refresh() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newKey, onValueChange = { newKey = it },
                placeholder = { Text("新客户端 Key", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        try { api.addApiKey(newKey.trim()); newKey = ""; toast("已添加"); refresh() }
                        catch (e: Exception) { toast("失败：${e.message}") }
                    }
                },
                enabled = newKey.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("加", fontSize = 13.sp) }
        }
        Spacer(Modifier.height(10.dp))
        when {
            err != null -> ErrorBox(err!!) { reload++ }
            keys == null -> SkeletonList(4)
            keys!!.isEmpty() -> EmptyBox("暂无客户端 Key")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(keys!!.size) { i ->
                    val k = keys!![i]
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(k, color = TextHi, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            CpaBtn("删除", Coral) {
                                scope.launch {
                                    try { api.deleteApiKey(k); toast("已删除"); refresh() }
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

// ---------------- 日志 ----------------

@Composable
private fun CpaLogs(api: CpaApi) {
    var lines by remember { mutableStateOf<List<String>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        try {
            val el = api.logs(300)
            lines = extractLogLines(el)
            err = null
        } catch (e: Exception) { err = e.message }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("应用日志（最近 300 行）", color = TextMid, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { reload++ }) { Text("刷新", color = Cyan, fontSize = 12.sp) }
        }
        when {
            err != null -> ErrorBox(err!!) { reload++ }
            lines == null -> LoadingBox()
            lines!!.isEmpty() -> EmptyBox("无日志（需服务端开启 logging-to-file）")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(lines!!.size) { i ->
                    val l = lines!![i]
                    val color = when {
                        l.contains("ERROR", true) || l.contains("error") -> Coral
                        l.contains("WARN", true) -> Amber
                        else -> TextMid
                    }
                    Text(l, color = color, fontSize = 10.sp, lineHeight = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

private fun extractLogLines(el: JsonElement): List<String> {
    val out = mutableListOf<String>()
    fun add(s: String) { out += s }
    when {
        el.isJsonArray -> el.asJsonArray.forEach { e ->
            out += if (e.isJsonPrimitive) e.asString else e.toString()
        }
        el.isJsonObject -> {
            val o = el.asJsonObject
            val arr = o.getAsJsonArray("lines") ?: o.getAsJsonArray("logs") ?: o.getAsJsonArray("items")
            if (arr != null) arr.forEach { e -> out += if (e.isJsonPrimitive) e.asString else e.toString() }
            else o.entrySet().forEach { (k, v) -> out += "$k: $v" }
        }
        el.isJsonPrimitive -> add(el.asString)
    }
    return out
}

// ---- ext ----
private fun JsonObject.str(k: String): String? =
    if (has(k) && get(k).isJsonPrimitive) runCatching { get(k).asString }.getOrNull() else null

private fun JsonObject.long(k: String): Long =
    if (has(k)) runCatching { get(k).asLong }.getOrDefault(0L) else 0L

private fun JsonObject.bool(k: String): Boolean? =
    if (has(k)) runCatching { get(k).asBoolean }.getOrNull() else null

private fun JsonElement.asObj(): JsonObject = if (isJsonObject) asJsonObject else JsonObject()
