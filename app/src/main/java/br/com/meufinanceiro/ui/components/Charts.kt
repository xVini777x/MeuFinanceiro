package br.com.meufinanceiro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.ui.theme.Border
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.Income
import br.com.meufinanceiro.ui.theme.Surface3
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class ChartSlice(val label: String, val value: Double, val color: Color)

data class BarDatum(val month: YearMonth, val income: Double, val expense: Double)

data class LinePoint(val label: String, val value: Double)

@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 190.dp,
    strokeWidth: Dp = 18.dp,
    centerLabel: String? = null,
    centerValue: String? = null,
) {
    val total = slices.sumOf { it.value }
    val progress by animateFloatAsState(
        targetValue = if (total > 0.0) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "donut",
    )
    // Furo disponível para o texto: diâmetro menos a espessura do anel, com uma
    // folga para o valor não encostar na borda interna.
    val holeWidth = (diameter - strokeWidth * 2).coerceAtLeast(0.dp)
    val textWidth = (holeWidth * 0.84f).coerceAtLeast(0.dp)

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = Surface3.copy(alpha = 0.65f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (total > 0.0) {
                var start = -90f
                val gap = if (slices.size > 1) 3.2f else 0f
                slices.forEach { slice ->
                    val sweep = (slice.value / total).toFloat() * 360f * progress
                    if (sweep > 0f) {
                        drawArc(
                            color = slice.color,
                            startAngle = start + gap / 2f,
                            sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    start += sweep
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(textWidth),
        ) {
            if (centerValue != null) {
                AutoFitText(
                    text = centerValue,
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    maxWidth = textWidth,
                    maxSize = 17.sp,
                    minSize = 9.sp,
                )
                Spacer(Modifier.height(2.dp))
            }
            if (centerLabel != null) {
                Text(
                    text = centerLabel,
                    color = TextMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/**
 * Ajusta o valor central do donut ao espaço real do furo. Mede o texto no
 * tamanho máximo e reduz até caber na largura disponível. A medição acontece
 * dentro do layout, então respeita a escala de fonte do sistema — sem isso,
 * "R$ 1.568,46" invade o anel em telas estreitas ou com letra grande.
 */
@Composable
private fun AutoFitText(
    text: String,
    color: Color,
    fontWeight: FontWeight,
    maxWidth: Dp,
    maxSize: TextUnit = 17.sp,
    minSize: TextUnit = 9.sp,
) {
    SubcomposeLayout(Modifier.width(maxWidth)) { constraints ->
        val available = constraints.maxWidth.coerceAtLeast(0)
        var size = maxSize
        var placeable: Placeable
        while (true) {
            placeable = subcompose(size.value, {
                Text(
                    text = text,
                    color = color,
                    fontWeight = fontWeight,
                    fontSize = size,
                    maxLines = 1,
                    softWrap = false,
                )
            }).first().measure(Constraints())
            if (placeable.width <= available || size.value <= minSize.value) break
            size = (size.value * 0.94f).coerceAtLeast(minSize.value).sp
        }
        layout(available, placeable.height) {
            // Centraliza mesmo no caso extremo em que o texto mínimo ainda
            // passa da largura (aí ele transborda igualmente para os lados).
            placeable.place((available - placeable.width) / 2, 0)
        }
    }
}

@Composable
fun BarsChart(
    data: List<BarDatum>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val progress by animateFloatAsState(
        targetValue = if (data.isEmpty()) 0f else 1f,
        animationSpec = tween(durationMillis = 750),
        label = "bars",
    )
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (data.isEmpty()) return@Canvas
            val maxValue = data.maxOfOrNull { max(it.income, it.expense) } ?: 0.0
            val groupWidth = size.width / data.size
            val barWidth = (groupWidth * 0.2f).coerceAtMost(14.dp.toPx())
            val usableHeight = size.height - 2.dp.toPx()

            // Grade horizontal discreta.
            val gridColor = Border
            repeat(3) { index ->
                val y = usableHeight * index / 2f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            data.forEachIndexed { index, datum ->
                val centerX = groupWidth * index + groupWidth / 2f
                val incomeHeight = if (maxValue > 0.0) {
                    (datum.income / maxValue).toFloat() * usableHeight * progress
                } else {
                    0f
                }
                val expenseHeight = if (maxValue > 0.0) {
                    (datum.expense / maxValue).toFloat() * usableHeight * progress
                } else {
                    0f
                }

                if (incomeHeight > 0f) {
                    val left = centerX - barWidth - 1.5.dp.toPx()
                    drawRoundRect(
                        color = Income,
                        topLeft = Offset(left, size.height - incomeHeight),
                        size = Size(barWidth, incomeHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                    // Deixa a base reta, encostada no eixo.
                    drawRect(
                        color = Income,
                        topLeft = Offset(left, size.height - barWidth / 2f),
                        size = Size(barWidth, min(barWidth / 2f, incomeHeight)),
                    )
                }
                if (expenseHeight > 0f) {
                    val left = centerX + 1.5.dp.toPx()
                    drawRoundRect(
                        color = Danger,
                        topLeft = Offset(left, size.height - expenseHeight),
                        size = Size(barWidth, expenseHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                    drawRect(
                        color = Danger,
                        topLeft = Offset(left, size.height - barWidth / 2f),
                        size = Size(barWidth, min(barWidth / 2f, expenseHeight)),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            data.forEach { datum ->
                Text(
                    text = FinanceCalculator.formatMonthShort(datum.month),
                    color = TextFaint,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Gráfico de linha com grade, área suave e ponto final.
 * As etiquetas do eixo X são distribuídas para no máximo 4 rótulos visíveis.
 */
@Composable
fun LineChart(
    points: List<LinePoint>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    color: Color = Income,
    showLabels: Boolean = true,
) {
    val progress by animateFloatAsState(
        targetValue = if (points.size < 2) 0f else 1f,
        animationSpec = tween(durationMillis = 800),
        label = "line",
    )
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (points.size < 2) return@Canvas
            val values = points.map { it.value.toFloat() }
            var minValue = values.minOrNull() ?: 0f
            var maxValue = values.maxOrNull() ?: 0f
            if (maxValue - minValue < 0.01f) {
                maxValue += 1f
                minValue -= 1f
            }
            val range = maxValue - minValue
            val paddingY = 6.dp.toPx()
            val usableHeight = size.height - paddingY * 2

            repeat(3) { index ->
                val y = paddingY + usableHeight * index / 2f
                drawLine(
                    color = Border,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // Linha do zero quando o saldo cruza negativo/positivo.
            if (minValue < 0f && maxValue > 0f) {
                val zeroY = paddingY + (1f - (0f - minValue) / range) * usableHeight
                drawLine(
                    color = TextFaint.copy(alpha = 0.45f),
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val stepX = size.width / (points.size - 1)
            val offsets = values.mapIndexed { index, value ->
                Offset(
                    x = stepX * index,
                    y = paddingY + (1f - (value - minValue) / range) * usableHeight,
                )
            }
            val path = smoothPath(offsets)

            clipRect(right = size.width * progress) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(offsets.last().x, size.height)
                    lineTo(offsets.first().x, size.height)
                    close()
                }
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.0f)),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                offsets.forEachIndexed { index, offset ->
                    if (index == offsets.lastIndex) {
                        drawCircle(color.copy(alpha = 0.22f), radius = 7.dp.toPx(), center = offset)
                        drawCircle(color, radius = 3.5.dp.toPx(), center = offset)
                    }
                }
            }
        }
        if (showLabels && points.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val step = max(1, ceil((points.size - 1) / 3f).toInt())
            val labelIndices = buildList {
                var index = 0
                while (index < points.size) {
                    add(index)
                    index += step
                }
                if (last() != points.lastIndex) add(points.lastIndex)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                labelIndices.forEach { index ->
                    Text(
                        text = points[index].label,
                        color = TextFaint,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun Sparkline(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val maxValue = points.maxOrNull() ?: 0f
        val minValue = points.minOrNull() ?: 0f
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (points.size - 1)
        val verticalPadding = 5.dp.toPx()
        val usableHeight = size.height - verticalPadding * 2

        val offsets = points.mapIndexed { index, value ->
            Offset(
                x = stepX * index,
                y = verticalPadding + (1f - (value - minValue) / range) * usableHeight,
            )
        }
        val path = smoothPath(offsets)

        if (fill) {
            val fillPath = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.0f)),
                ),
            )
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val last = offsets.last()
        drawCircle(color.copy(alpha = 0.22f), radius = 6.dp.toPx(), center = last)
        drawCircle(color, radius = 3.dp.toPx(), center = last)
    }
}

@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2f)
        }
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Converte os pontos em uma curva suave (Catmull-Rom -> Bézier). */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    for (index in 0 until points.size - 1) {
        val current = points[index]
        val next = points[index + 1]
        val previous = points.getOrNull(index - 1) ?: current
        val after = points.getOrNull(index + 2) ?: next
        val control1 = Offset(
            x = current.x + (next.x - previous.x) / 6f,
            y = current.y + (next.y - previous.y) / 6f,
        )
        val control2 = Offset(
            x = next.x - (after.x - current.x) / 6f,
            y = next.y - (after.y - current.y) / 6f,
        )
        path.cubicTo(control1.x, control1.y, control2.x, control2.y, next.x, next.y)
    }
    return path
}
