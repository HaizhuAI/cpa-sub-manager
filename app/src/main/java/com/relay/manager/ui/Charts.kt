package com.relay.manager.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relay.manager.ui.theme.*
import kotlin.math.ceil
import kotlin.math.max

/** 入场动画进度 0→1 */
@Composable
private fun drawProgress(): Float {
    var started by remember { mutableStateOf(false) }
    val p by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "draw"
    )
    LaunchedEffect(Unit) { started = true }
    return p
}

private fun labelPaint(size: Float = 20f, alpha: Int = 140): android.graphics.Paint =
    android.graphics.Paint().apply {
        setColor(android.graphics.Color.argb(alpha, 151, 165, 206))
        textSize = size
        isAntiAlias = true
    }

private fun valuePaint(color: Int, size: Float = 22f): android.graphics.Paint =
    android.graphics.Paint().apply {
        setColor(color)
        textSize = size
        isAntiAlias = true
        isFakeBoldText = true
    }

private fun argb(c: Color, alpha: Int = 255): Int =
    android.graphics.Color.argb(alpha, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

/** 双序列折线图（大屏主趋势）：左轴 seriesA、右轴 seriesB，带入场动画 */
@Composable
fun TrendChart(
    labels: List<String>,
    seriesA: List<Float>,
    seriesB: List<Float>,
    nameA: String = "请求数",
    nameB: String = "Token",
    colorA: Color = Cyan,
    colorB: Color = Mint,
    modifier: Modifier = Modifier,
) {
    val prog = drawProgress()
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(colorA); Spacer(Modifier.width(4.dp)); Text(nameA, color = TextMid, fontSize = 11.sp)
            Spacer(Modifier.width(14.dp))
            LegendDot(colorB); Spacer(Modifier.width(4.dp)); Text(nameB, color = TextMid, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            val w = size.width
            val h = size.height
            val padL = 8f; val padR = 8f; val padT = 16f; val padB = 26f
            val cw = w - padL - padR
            val ch = h - padT - padB
            val n = max(labels.size, 2)

            val gridPaint = labelPaint()
            val maxA = max(seriesA.maxOrNull() ?: 0f, 1f)
            val maxB = max(seriesB.maxOrNull() ?: 0f, 1f)

            for (i in 0..3) {
                val y = padT + ch * i / 3f
                drawLine(BorderDim.copy(alpha = 0.35f), Offset(padL, y), Offset(w - padR, y), 1f)
            }

            fun xAt(i: Int) = padL + cw * i / (n - 1).coerceAtLeast(1)
            fun yA(v: Float) = padT + ch * (1f - v / maxA)
            fun yB(v: Float) = padT + ch * (1f - v / maxB)

            val countA = (seriesA.size * prog).toInt().coerceIn(0, seriesA.size)
            val countB = (seriesB.size * prog).toInt().coerceIn(0, seriesB.size)

            if (countB > 0) {
                val path = Path()
                val area = Path()
                for (i in 0 until countB) {
                    val x = xAt(i); val y = yB(seriesB[i])
                    if (i == 0) { path.moveTo(x, y); area.moveTo(x, padT + ch); area.lineTo(x, y) }
                    else { path.lineTo(x, y); area.lineTo(x, y) }
                }
                area.lineTo(xAt(countB - 1), padT + ch); area.close()
                drawPath(area, Brush.verticalGradient(listOf(colorB.copy(alpha = 0.28f), Color.Transparent)))
                drawPath(path, colorB, style = Stroke(4.5f))
                // 末点光斑 + 数值
                val ex = xAt(countB - 1); val ey = yB(seriesB[countB - 1])
                drawCircle(colorB.copy(alpha = 0.3f), 12f, Offset(ex, ey))
                drawCircle(colorB, 5f, Offset(ex, ey))
                if (prog >= 1f) {
                    drawContext.canvas.nativeCanvas.drawText(
                        fmtCount(seriesB.last().toLong()), ex - 30f, ey - 14f, valuePaint(argb(colorB))
                    )
                }
            }
            if (countA > 0) {
                val path = Path()
                for (i in 0 until countA) {
                    val x = xAt(i); val y = yA(seriesA[i])
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, colorA, style = Stroke(4.5f))
                for (i in 0 until countA) {
                    drawCircle(BgDeep, 6f, Offset(xAt(i), yA(seriesA[i])))
                    drawCircle(colorA, 4f, Offset(xAt(i), yA(seriesA[i])))
                }
            }

            // x labels（抽稀）
            val step = ceil(n / 7.0).toInt().coerceAtLeast(1)
            labels.forEachIndexed { i, lab ->
                if (i % step == 0 || i == n - 1) {
                    drawContext.canvas.nativeCanvas.drawText(
                        lab.takeLast(5),
                        xAt(i) - 26f,
                        h - 4f,
                        gridPaint
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
}

/** 每日柱状图（入场生长动画） */
@Composable
fun DayBars(
    data: List<Pair<String, Float>>,
    color: Color = Violet,
    modifier: Modifier = Modifier,
) {
    val prog = drawProgress()
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        if (data.isEmpty()) return@Canvas
        val w = size.width; val h = size.height
        val padB = 24f; val padT = 12f
        val ch = h - padT - padB
        val maxV = max(data.maxOf { it.second }, 1f)
        val n = data.size
        val gap = 6f
        val bw = (w - gap * (n - 1)) / n
        val lp = labelPaint(18f, 120)
        data.forEachIndexed { i, (lab, v) ->
            val x = i * (bw + gap)
            val bh = ch * (v / maxV) * prog
            drawRoundRect(
                Brush.verticalGradient(listOf(color, color.copy(alpha = 0.3f))),
                topLeft = Offset(x, padT + ch - bh),
                size = androidx.compose.ui.geometry.Size(bw.coerceAtLeast(2f), bh.coerceAtLeast(2f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            if (n <= 15) {
                drawContext.canvas.nativeCanvas.drawText(
                    lab.takeLast(2), x + bw / 2 - 12f, h - 4f, lp
                )
            }
        }
    }
}

/** 横向排行条（模型/站点对比），条带生长动画 */
@Composable
fun RankBars(
    items: List<Pair<String, Long>>,
    modifier: Modifier = Modifier,
    valueFmt: (Long) -> String = ::fmtCount,
    palette: List<Color> = ChartPalette,
) {
    val prog = drawProgress()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val maxV = max(items.maxOfOrNull { it.second } ?: 1, 1)
        items.forEachIndexed { i, (name, v) ->
            val c = palette[i % palette.size]
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}. $name",
                        color = TextMid, fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(valueFmt(v), color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(BgCardLight)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(((v / maxV.toFloat()) * prog).coerceIn(0.015f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(listOf(c.copy(alpha = 0.55f), c)))
                    )
                }
            }
        }
    }
}

/** 环形占比图 + 图例（扫入动画） */
@Composable
fun DonutChart(
    items: List<Pair<String, Long>>,
    modifier: Modifier = Modifier,
    palette: List<Color> = ChartPalette,
) {
    val prog = drawProgress()
    val total = items.sumOf { it.second }.coerceAtLeast(1)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(130.dp)) {
            var start = -90f
            items.forEachIndexed { i, (_, v) ->
                val sweep = 360f * v / total * prog
                if (sweep > 0f) drawArc(
                    palette[i % palette.size],
                    startAngle = start,
                    sweepAngle = (sweep - 2f).coerceAtLeast(0.5f),
                    useCenter = false,
                    style = Stroke(26f)
                )
                start += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.take(6).forEachIndexed { i, (name, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(palette[i % palette.size])
                    Spacer(Modifier.width(6.dp))
                    Text(name, color = TextMid, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Text("${"%.1f".format(v * 100f / total)}%", color = TextHi, fontSize = 11.sp)
                }
            }
        }
    }
}
