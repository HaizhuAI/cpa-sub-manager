package com.relay.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relay.manager.data.Station
import com.relay.manager.data.StationType
import com.relay.manager.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(
    stations: List<Station>,
    onAdd: (Station) -> Unit,
    onUpdate: (Station) -> Unit,
    onDelete: (Station) -> Unit,
    onOpen: (Station) -> Unit,
    onOpenDashboard: () -> Unit,
) {
    var editing by remember { mutableStateOf<Station?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Station?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "中转站管家",
            subtitle = "CPA · SUB 远程运维",
            actions = {
                Button(
                    onClick = onOpenDashboard,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) { Text("数据大屏", fontSize = 13.sp) }
            }
        )

        if (stations.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("还没有站点", color = TextMid, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("添加 CPA / SUB 中转站，输入管理密钥即可远程运维", color = TextLow, fontSize = 13.sp)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { editing = null; showDialog = true }) { Text("＋ 添加站点") }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(stations, key = { it.id }) { s ->
                    StationCard(
                        s,
                        onOpen = { onOpen(s) },
                        onEdit = { editing = s; showDialog = true },
                        onDelete = { deleteTarget = s },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { editing = null; showDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("＋ 添加站点") }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDialog) {
        StationDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onSave = { st ->
                if (editing == null) onAdd(st) else onUpdate(st)
                showDialog = false
            }
        )
    }

    deleteTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除站点") },
            text = { Text("确认删除「${s.name}」？本地聚合的用量数据将一并清除。") },
            confirmButton = {
                TextButton(onClick = { onDelete(s); deleteTarget = null }) { Text("删除", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun StationCard(s: Station, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val accent = if (s.type == StationType.CPA) Cyan else Mint
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.3f), accent.copy(alpha = 0.08f))
                        )
                    )
                    .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(s.type.name, color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(s.host, color = TextMid, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Pill(s.type.hint, accent)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("编辑", fontSize = 12.sp, color = TextMid)
                    }
                    TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("删除", fontSize = 12.sp, color = Coral)
                    }
                }
                Button(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = BgDeep)
                ) { Text("进入", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationDialog(initial: Station?, onDismiss: () -> Unit, onSave: (Station) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: StationType.CPA) }
    var url by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var pwd by remember { mutableStateOf(initial?.password ?: "") }
    var showPwd by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text(if (initial == null) "添加站点" else "编辑站点", color = TextHi) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 类型选择
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StationType.entries.forEach { t ->
                        val sel = t == type
                        val ac = if (t == StationType.CPA) Cyan else Mint
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) ac.copy(alpha = 0.2f) else BgCardLight)
                                .clickable { type = t }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t.label, color = if (sel) ac else TextMid, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(if (t == StationType.CPA) "CLIProxyAPI" else "sub2api", color = TextLow, fontSize = 10.sp)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("站点名称") }, singleLine = true,
                    placeholder = { Text(if (type == StationType.CPA) "我的 CPA" else "我的 SUB") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("站点地址") }, singleLine = true,
                    placeholder = { Text("https://api.example.com:8317") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pwd, onValueChange = { pwd = it },
                    label = { Text("管理密钥") }, singleLine = true,
                    placeholder = { Text(if (type == StationType.CPA) "remote-management secret-key" else "管理 API 密钥 (x-api-key)") },
                    visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPwd = !showPwd }) { Text(if (showPwd) "隐藏" else "显示", fontSize = 11.sp) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (type == StationType.CPA)
                        "CPA 需在 config 开启 remote-management.allow-remote: true，密钥即 secret-key"
                    else
                        "SUB 管理密钥在后台「系统设置 → 管理 API 密钥」生成，形如 adm_sc_xxx 或全局密钥",
                    color = TextLow, fontSize = 11.sp, lineHeight = 15.sp
                )
                err?.let { Text(it, color = Coral, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val u = url.trim().trimEnd('/')
                when {
                    name.isBlank() -> err = "请填写站点名称"
                    u.isBlank() -> err = "请填写站点地址"
                    !u.startsWith("http://") && !u.startsWith("https://") -> err = "地址需以 http:// 或 https:// 开头"
                    pwd.isBlank() -> err = "请填写管理密钥"
                    else -> onSave(
                        Station(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(), type = type, baseUrl = u, password = pwd.trim(),
                            createdAt = initial?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
