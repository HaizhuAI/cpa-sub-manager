package com.relay.manager.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relay.manager.ui.theme.*

// ---------- 格式化 ----------

fun fmtCount(v: Long): String = when {
    v >= 1_000_000_000 -> String.format("%.2fB", v / 1e9)
    v >= 1_000_000 -> String.format("%.2fM", v / 1e6)
    v >= 10_000 -> String.format("%.1fK", v / 1e3)
    else -> v.toString()
}

fun fmtCountFull(v: Long): String = String.format("%,d", v)

fun fmtMoney(v: Double): String = when {
    v >= 1000 -> String.format("$%,.0f", v)
    else -> String.format("$%.3f", v)
}

fun fmtTokens(v: Long): String = fmtCount(v)

// ---------- 动态背景 ----------

/** 全局动态背景：呼吸光斑 + 细网格 */
@Composable
fun GlowBackground(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "bg")
    val t by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "t"
    )
    androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
        // 网格
        val step = 90f
        var x = 0f
        while (x < size.width) {
            drawLine(BorderDim.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(BorderDim.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        // 呼吸光斑
        drawCircle(
            Brush.radialGradient(
                listOf(Cyan.copy(alpha = 0.10f + 0.05f * t), Color.Transparent),
                center = Offset(size.width * (0.2f + 0.1f * t), size.height * 0.12f),
                radius = size.width * 0.9f
            ),
            center = Offset(size.width * (0.2f + 0.1f * t), size.height * 0.12f),
            radius = size.width * 0.9f
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Violet.copy(alpha = 0.08f + 0.04f * (1f - t)), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * (0.55f - 0.08f * t)),
                radius = size.width * 0.8f
            ),
            center = Offset(size.width * 0.9f, size.height * (0.55f - 0.08f * t)),
            radius = size.width * 0.8f
        )
    }
}

// ---------- 大屏通用组件 ----------

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(BgCardLight.copy(alpha = 0.7f), BgCard.copy(alpha = 0.95f))
                )
            )
            .border(1.dp, BorderDim.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        if (accent != null) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.1f)))
                    )
            )
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

/** 数字滚动动画 */
@Composable
private fun AnimatedNumber(target: Long, color: Color, fmt: (Long) -> String) {
    var started by remember { mutableStateOf(false) }
    val p by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing), label = "num"
    )
    LaunchedEffect(Unit) { started = true }
    Text(
        fmt((target * p).toLong()),
        color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    color: Color = Cyan,
    valueNum: Long? = null,
) {
    GlassCard(modifier = modifier, accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextMid, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
        Spacer(Modifier.height(6.dp))
        if (valueNum != null) {
            AnimatedNumber(valueNum, TextHi, ::fmtCount)
        } else {
            Text(value, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, color = TextLow, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SectionTitle(text: String, color: Color = Cyan, tail: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.2f))))
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        tail?.invoke(this)
    }
}

@Composable
fun Pill(text: String, color: Color, bg: Color? = null) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg ?: color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** 呼吸状态灯 */
@Composable
fun StatusDot(ok: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "dot")
    val a by inf.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "a"
    )
    val c = if (ok) Mint else Coral
    Box(modifier.size(8.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(50))
                .background(c.copy(alpha = 0.35f * a))
        )
        Box(
            Modifier
                .fillMaxSize(0.55f)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(50))
                .background(c)
        )
    }
}

/** 骨架屏行 */
@Composable
private fun ShimmerRow() {
    val inf = rememberInfiniteTransition(label = "sh")
    val a by inf.animateFloat(
        initialValue = 0.25f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCardLight.copy(alpha = a))
    )
}

@Composable
fun SkeletonList(rows: Int = 4) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) { ShimmerRow() }
    }
}

@Composable
fun LoadingBox(text: String = "加载中…") {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Cyan, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text(text, color = TextMid, fontSize = 13.sp)
        }
    }
}

@Composable
fun ErrorBox(msg: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, color = Coral, fontSize = 13.sp)
            if (onRetry != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

@Composable
fun EmptyBox(text: String = "暂无数据") {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextLow, fontSize = 13.sp)
    }
}

/** 大屏顶栏（渐变标题） */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("‹ 返回", color = Cyan, fontSize = 15.sp)
            }
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.horizontalGradient(listOf(TextHi, Cyan.copy(alpha = 0.85f)))
                )
            )
            if (subtitle != null) Text(subtitle, color = TextLow, fontSize = 12.sp)
        }
        actions?.invoke(this)
    }
}
