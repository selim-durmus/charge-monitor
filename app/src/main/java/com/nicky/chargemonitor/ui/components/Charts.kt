package com.nicky.chargemonitor.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nicky.chargemonitor.PowerPoint
import com.nicky.chargemonitor.ui.theme.ChargeTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Immutable
data class ChartPoint(val x: Long, val y: Double)

@Immutable
data class ChartBand(val x: Long, val low: Double, val high: Double)

@Immutable
data class ChartSeries(
    val points: List<ChartPoint>,
    val color: Color,
    val onSecondaryAxis: Boolean = false,
    val band: List<ChartBand> = emptyList(),
    val strokeWidth: Dp = 2.dp,
)

@Immutable
data class ChartAxis(
    val unit: String,
    val decimals: Int = 0,
    val includeZero: Boolean = false,
    val minimumSpan: Double = 1.0,
)

/** Rolling 30-second power trace for the live screen, plotted against real time. */
@Composable
fun LivePowerChart(
    points: List<PowerPoint>,
    windowMillis: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val grid = ChargeTheme.colors.chartGrid
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        drawGrid(grid, rows = 3)
        if (points.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.3f),
                start = Offset(0f, size.height * 0.66f),
                end = Offset(size.width, size.height * 0.66f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }
        val end = points.last().elapsedRealtimeMillis
        val start = end - windowMillis
        val (low, high) = yRange(points.map { it.watts }, includeZero = true, minimumSpan = 1.0)
        fun x(t: Long) = (size.width * (t - start) / windowMillis.toFloat()).coerceIn(0f, size.width)
        fun y(v: Double) = (size.height * (1.0 - (v - low) / (high - low))).toFloat()

        val line = Path()
        val fill = Path()
        points.forEachIndexed { index, point ->
            val px = x(point.elapsedRealtimeMillis)
            val py = y(point.watts)
            if (index == 0) {
                line.moveTo(px, py)
                fill.moveTo(px, size.height)
                fill.lineTo(px, py)
            } else {
                line.lineTo(px, py)
                fill.lineTo(px, py)
            }
        }
        fill.lineTo(x(end), size.height)
        fill.close()
        drawZeroLine(y(0.0), labelColor)
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(
            line,
            color,
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(x(end), y(points.last().watts)))
        drawAxisLabel(measurer, "${high.formatNumber(0)} W", labelStyle, top = true, start = true)
        drawAxisLabel(measurer, "${low.formatNumber(0)} W", labelStyle, top = false, start = true)
    }
}

/**
 * Time-series chart with up to two y-axes, optional min/max bands, and event markers.
 * X labels show elapsed time from [xStart].
 */
@Composable
fun TraceChart(
    series: List<ChartSeries>,
    xStart: Long,
    xEnd: Long,
    primaryAxis: ChartAxis,
    modifier: Modifier = Modifier,
    secondaryAxis: ChartAxis? = null,
    eventMarkers: List<Long> = emptyList(),
    height: Dp = 180.dp,
) {
    val grid = ChargeTheme.colors.chartGrid
    val eventColor = ChargeTheme.colors.event
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val measurer = rememberTextMeasurer()
    val span = max(1L, xEnd - xStart)
    val primaryRange = remember(series, primaryAxis) {
        yRange(
            series.filterNot { it.onSecondaryAxis }.flatMap { s ->
                s.points.map { it.y } + s.band.flatMap { listOf(it.low, it.high) }
            },
            primaryAxis.includeZero,
            primaryAxis.minimumSpan,
        )
    }
    val secondaryRange = remember(series, secondaryAxis) {
        secondaryAxis?.let { axis ->
            yRange(
                series.filter { it.onSecondaryAxis }.flatMap { s -> s.points.map { it.y } },
                axis.includeZero,
                axis.minimumSpan,
            )
        }
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            drawGrid(grid, rows = 4)
            fun x(t: Long) = (size.width * (t - xStart) / span.toFloat()).coerceIn(0f, size.width)
            fun y(v: Double, range: Pair<Double, Double>) =
                (size.height * (1.0 - (v - range.first) / (range.second - range.first))).toFloat()

            // Start and end events are implied by the chart's edges; only mark the middle.
            val edge = span / 100
            eventMarkers.filter { it - xStart > edge && xEnd - it > edge }.forEach { t ->
                drawLine(
                    color = eventColor.copy(alpha = 0.55f),
                    start = Offset(x(t), 0f),
                    end = Offset(x(t), size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }
            if (primaryAxis.includeZero) {
                drawZeroLine(y(0.0, primaryRange), labelColor)
            }
            series.forEach { s ->
                val range = if (s.onSecondaryAxis) secondaryRange ?: primaryRange else primaryRange
                s.band.forEach { b ->
                    drawLine(
                        color = s.color.copy(alpha = 0.16f),
                        start = Offset(x(b.x), y(b.low, range)),
                        end = Offset(x(b.x), y(b.high, range)),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                if (s.points.size >= 2) {
                    val path = Path()
                    s.points.forEachIndexed { index, p ->
                        if (index == 0) path.moveTo(x(p.x), y(p.y, range)) else path.lineTo(x(p.x), y(p.y, range))
                    }
                    drawPath(
                        path,
                        s.color,
                        style = Stroke(s.strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            drawAxisLabel(
                measurer,
                "${primaryRange.second.formatNumber(primaryAxis.decimals)} ${primaryAxis.unit}",
                labelStyle,
                top = true,
                start = true,
            )
            drawAxisLabel(
                measurer,
                "${primaryRange.first.formatNumber(primaryAxis.decimals)} ${primaryAxis.unit}",
                labelStyle,
                top = false,
                start = true,
            )
            if (secondaryAxis != null && secondaryRange != null) {
                drawAxisLabel(
                    measurer,
                    "${secondaryRange.second.formatNumber(secondaryAxis.decimals)} ${secondaryAxis.unit}",
                    labelStyle,
                    top = true,
                    start = false,
                )
                drawAxisLabel(
                    measurer,
                    "${secondaryRange.first.formatNumber(secondaryAxis.decimals)} ${secondaryAxis.unit}",
                    labelStyle,
                    top = false,
                    start = false,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text("0:00", style = labelStyle)
            Spacer(Modifier.weight(1f))
            Text(span.formatClock(), style = labelStyle)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 14.dp, height = 3.dp)
                        .background(color, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Battery level as a ring. While [flowing] a soft highlight travels along the filled arc,
 * which reads as "energy moving" without adding another element to the screen.
 */
@Composable
fun BatteryRing(
    levelPercent: Int?,
    color: Color,
    flowing: Boolean,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val sweep by animateFloatAsState(
        targetValue = ((levelPercent ?: 0) / 100f) * 360f,
        animationSpec = tween(700),
        label = "ring sweep",
    )
    val transition = rememberInfiniteTransition(label = "ring flow")
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "flow",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (sweep > 0f) {
                drawArc(
                    color,
                    -90f,
                    sweep,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            if (flowing && sweep > 8f) {
                val angle = Math.toRadians((-90.0 + sweep * flow))
                val radius = arcSize.width / 2f
                val c = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                drawCircle(Color.White.copy(alpha = 0.55f), radius = stroke * 0.32f, center = c)
            }
        }
        content()
    }
}

/** Evenly thins a list to at most [maxPoints] while keeping the final point. */
fun <T> List<T>.downsample(maxPoints: Int): List<T> {
    if (size <= maxPoints) return this
    val stride = ceil(size.toDouble() / maxPoints).toInt()
    val sampled = filterIndexed { index, _ -> index % stride == 0 }
    return if (sampled.last() === last()) sampled else sampled + last()
}

/**
 * Pads the data range and snaps both ends to a round step (1, 2, 2.5 or 5 × 10ⁿ) so the
 * printed axis labels are exact values rather than rounded approximations.
 */
private fun yRange(values: List<Double>, includeZero: Boolean, minimumSpan: Double): Pair<Double, Double> {
    val finite = values.filter { it.isFinite() }
    if (finite.isEmpty()) return 0.0 to minimumSpan
    var low = finite.min()
    var high = finite.max()
    if (includeZero) {
        low = min(low, 0.0)
        high = max(high, 0.0)
    }
    if (high - low < minimumSpan) {
        val mid = (high + low) / 2
        low = if (includeZero && low >= 0.0) 0.0 else mid - minimumSpan / 2
        high = low + minimumSpan
    }
    val pad = (high - low) * 0.08
    val paddedLow = if (includeZero && low >= 0.0) low else low - pad
    val paddedHigh = high + pad
    val step = niceStep((paddedHigh - paddedLow) / 4)
    return floor(paddedLow / step) * step to ceil(paddedHigh / step) * step
}

private fun niceStep(raw: Double): Double {
    val magnitude = 10.0.pow(floor(log10(raw)))
    val fraction = raw / magnitude
    val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= fraction }
    return nice * magnitude
}

private fun DrawScope.drawGrid(color: Color, rows: Int) {
    repeat(rows + 1) { row ->
        val y = size.height * row / rows
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

private fun DrawScope.drawZeroLine(y: Float, color: Color) {
    if (y in 0f..size.height) {
        drawLine(color.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

private fun DrawScope.drawAxisLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    top: Boolean,
    start: Boolean,
) {
    val layout = measurer.measure(text, style)
    val pad = 4.dp.toPx()
    val x = if (start) pad else size.width - layout.size.width - pad
    val y = if (top) pad else size.height - layout.size.height - pad
    drawText(layout, topLeft = Offset(x, y))
}
