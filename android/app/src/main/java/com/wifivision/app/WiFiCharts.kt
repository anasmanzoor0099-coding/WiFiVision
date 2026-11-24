package com.wifivision.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Clean native ring chart with center total count and aligned legend rows.
 */
class DonutChartView(context: Context) : View(context) {

    data class Slice(
        val label: String,
        val value: Int,
        val color: Int
    )

    private var slices: List<Slice> = emptyList()
    private var centerSubtitle: String = "Total"

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val centerValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.darkSlate
        textSize = context.dpF(20f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val centerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.mutedSlate
        textSize = context.dpF(10.5f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val legendLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.darkSlate
        textSize = context.dpF(12f)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val legendValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.bodySlate
        textSize = context.dpF(11.5f)
        textAlign = Paint.Align.RIGHT
    }
    private val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.mutedSlate
        textSize = context.dpF(12.5f)
        textAlign = Paint.Align.CENTER
    }
    private val oval = RectF()

    fun setData(items: List<Slice>, subtitle: String = "Total") {
        slices = items.filter { it.value > 0 }
        centerSubtitle = subtitle
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = if (slices.isEmpty()) {
            context.dp(88)
        } else {
            max(context.dp(146), slices.size * context.dp(28) + context.dp(20))
        }
        val w = resolveSize(context.dp(320), widthMeasureSpec)
        val h = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val total = slices.sumOf { it.value }
        if (total <= 0) {
            canvas.drawText("No distribution data recorded yet", w / 2f, h / 2f + context.dpF(4f), emptyPaint)
            return
        }

        val strokeW = context.dpF(18f)
        ringPaint.strokeWidth = strokeW

        val ringAreaWidth = min(w * 0.42f, context.dpF(146f))
        val diameter = min(ringAreaWidth - strokeW - context.dpF(12f), h - strokeW - context.dpF(16f))
            .coerceAtLeast(context.dpF(64f))
        val cx = context.dpF(8f) + (ringAreaWidth - context.dpF(8f)) / 2f
        val cy = h / 2f
        val half = diameter / 2f
        oval.set(cx - half, cy - half, cx + half, cy + half)

        // Track background circle
        ringPaint.color = WiFiVisionPalette.softIvory
        canvas.drawArc(oval, 0f, 360f, false, ringPaint)

        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.value.toFloat() / total.toFloat()) * 360f
            ringPaint.color = slice.color
            val gap = if (slices.size > 1 && sweep > 4f) 1.5f else 0f
            canvas.drawArc(oval, startAngle, (sweep - gap).coerceAtLeast(1f), false, ringPaint)
            startAngle += sweep
        }

        canvas.drawText(total.toString(), cx, cy + context.dpF(4f), centerValuePaint)
        canvas.drawText(centerSubtitle, cx, cy + context.dpF(19f), centerSubPaint)

        // Legend rows on right
        val legendLeft = ringAreaWidth + context.dpF(12f)
        val legendRight = w - context.dpF(4f)
        val rowHeight = context.dpF(26f)
        val startY = (h - slices.size * rowHeight) / 2f + context.dpF(17f)

        slices.forEachIndexed { index, slice ->
            val y = startY + index * rowHeight
            dotPaint.color = slice.color
            canvas.drawCircle(legendLeft + context.dpF(5f), y - context.dpF(4f), context.dpF(4.5f), dotPaint)

            val pct = (slice.value * 100.0) / total
            val valStr = String.format(Locale.US, "%d (%.0f%%)", slice.value, pct)
            canvas.drawText(valStr, legendRight, y, legendValuePaint)

            val valWidth = legendValuePaint.measureText(valStr)
            val availLabel = (legendRight - valWidth - legendLeft - context.dpF(20f)).coerceAtLeast(context.dpF(24f))
            val clippedLabel = TextUtils.ellipsize(slice.label, legendLabelPaint, availLabel, TextUtils.TruncateAt.END).toString()
            canvas.drawText(clippedLabel, legendLeft + context.dpF(15f), y, legendLabelPaint)
        }
    }
}

/**
 * Native horizontal progress/bar chart used for Security distribution, Quality tiers,
 * 4-factor component breakdown, and RSSI signal buckets.
 */
class HorizontalBarChartView(context: Context) : View(context) {

    data class BarEntry(
        val label: String,
        val value: Float,
        val valueLabel: String,
        val color: Int,
        val maxOverride: Float? = null
    )

    private var entries: List<BarEntry> = emptyList()
    private var emptyMessage: String = "No data available yet"

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.darkSlate
        textSize = context.dpF(12f)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.bodySlate
        textSize = context.dpF(11.5f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
    }
    private val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.mutedSlate
        textSize = context.dpF(12.5f)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setEntries(items: List<BarEntry>, emptyText: String = "No data available yet") {
        entries = items
        emptyMessage = emptyText
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredH = if (entries.isEmpty()) {
            context.dp(70)
        } else {
            entries.size * context.dp(40) + context.dp(6)
        }
        val w = resolveSize(context.dp(320), widthMeasureSpec)
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (entries.isEmpty()) {
            canvas.drawText(emptyMessage, w / 2f, h / 2f + context.dpF(4f), emptyPaint)
            return
        }

        val autoMax = entries.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
        val rowStep = context.dpF(40f)
        val trackHeight = context.dpF(8f)
        val radius = trackHeight / 2f
        val padH = context.dpF(2f)

        entries.forEachIndexed { idx, entry ->
            val topY = idx * rowStep + context.dpF(14f)
            canvas.drawText(entry.valueLabel, w - padH, topY, valuePaint)

            val valW = valuePaint.measureText(entry.valueLabel)
            val availLabel = (w - valW - context.dpF(14f)).coerceAtLeast(context.dpF(40f))
            val clipped = TextUtils.ellipsize(entry.label, labelPaint, availLabel, TextUtils.TruncateAt.END).toString()
            canvas.drawText(clipped, padH, topY, labelPaint)

            val trackTop = topY + context.dpF(7f)
            rect.set(padH, trackTop, w - padH, trackTop + trackHeight)
            barPaint.color = WiFiVisionPalette.softIvory
            canvas.drawRoundRect(rect, radius, radius, barPaint)

            val denom = (entry.maxOverride ?: autoMax).coerceAtLeast(1f)
            val ratio = (entry.value / denom).coerceIn(0f, 1f)
            if (entry.value > 0f) {
                val fillW = ((w - padH * 2) * ratio).coerceAtLeast(trackHeight)
                rect.set(padH, trackTop, (padH + fillW).coerceAtMost(w - padH), trackTop + trackHeight)
                barPaint.color = entry.color
                canvas.drawRoundRect(rect, radius, radius, barPaint)
            }
        }
    }
}

/**
 * Multi-series chronological trend chart for Download/Upload Mbps and Latency/Jitter/Loss.
 */
class TrendLineChartView(context: Context) : View(context) {

    data class Series(
        val name: String,
        val color: Int,
        val points: List<Float>,
        val fillArea: Boolean = false
    )

    private var seriesList: List<Series> = emptyList()
    private var xLabels: List<String> = emptyList()
    private var unitSuffix: String = ""
    private var emptyMessage: String = "No measurements recorded yet"

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.hairline
        strokeWidth = context.dpF(1f)
    }
    private val axisTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.mutedSlate
        textSize = context.dpF(10f)
    }
    private val legendTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.bodySlate
        textSize = context.dpF(11f)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(2.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.mutedSlate
        textSize = context.dpF(12.5f)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val areaPath = Path()

    fun setData(
        series: List<Series>,
        labels: List<String>,
        unit: String,
        emptyText: String = "No measurements recorded yet"
    ) {
        seriesList = series.filter { it.points.isNotEmpty() }
        xLabels = labels
        unitSuffix = unit
        emptyMessage = emptyText
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredH = if (seriesList.isEmpty()) context.dp(86) else context.dp(206)
        val w = resolveSize(context.dp(320), widthMeasureSpec)
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (seriesList.isEmpty()) {
            canvas.drawText(emptyMessage, w / 2f, h / 2f + context.dpF(4f), emptyPaint)
            return
        }

        // Top Legend
        var legendX = context.dpF(4f)
        val legendY = context.dpF(14f)
        seriesList.forEach { s ->
            dotPaint.color = s.color
            canvas.drawCircle(legendX + context.dpF(4f), legendY - context.dpF(4f), context.dpF(4f), dotPaint)
            val labelText = if (unitSuffix.isNotEmpty()) "${s.name} ($unitSuffix)" else s.name
            canvas.drawText(labelText, legendX + context.dpF(12f), legendY, legendTextPaint)
            legendX += legendTextPaint.measureText(labelText) + context.dpF(24f)
        }

        val allValues = seriesList.flatMap { it.points }.filter { it.isFinite() }
        if (allValues.isEmpty()) {
            canvas.drawText(emptyMessage, w / 2f, h / 2f, emptyPaint)
            return
        }

        val rawMax = allValues.maxOrNull() ?: 1f
        val maxVal = max(rawMax * 1.15f, 1f)
        val minVal = 0f

        val chartLeft = context.dpF(42f)
        val chartRight = w - context.dpF(10f)
        val chartTop = context.dpF(30f)
        val chartBottom = h - context.dpF(24f)
        val chartW = (chartRight - chartLeft).coerceAtLeast(1f)
        val chartH = (chartBottom - chartTop).coerceAtLeast(1f)

        // Horizontal Grid & Y labels (0, mid, max)
        axisTextPaint.textAlign = Paint.Align.RIGHT
        val gridSteps = 2
        for (i in 0..gridSteps) {
            val frac = i / gridSteps.toFloat()
            val y = chartBottom - frac * chartH
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            val v = minVal + frac * (maxVal - minVal)
            val label = if (v >= 10f) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)
            canvas.drawText(label, chartLeft - context.dpF(6f), y + context.dpF(3.5f), axisTextPaint)
        }

        val maxPoints = seriesList.maxOf { it.points.size }
        seriesList.forEach { s ->
            val count = s.points.size
            path.reset()
            areaPath.reset()
            val coords = ArrayList<Pair<Float, Float>>(count)

            for (i in 0 until count) {
                val x = if (maxPoints <= 1) {
                    chartLeft + chartW / 2f
                } else {
                    chartLeft + (i.toFloat() / (maxPoints - 1).toFloat()) * chartW
                }
                val norm = ((s.points[i] - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                val y = chartBottom - norm * chartH
                coords.add(x to y)
                if (i == 0) {
                    path.moveTo(x, y)
                    areaPath.moveTo(x, chartBottom)
                    areaPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }
            }

            if (s.fillArea && coords.size >= 2) {
                areaPath.lineTo(coords.last().first, chartBottom)
                areaPath.close()
                areaPaint.color = (s.color and 0x00FFFFFF) or 0x22000000
                canvas.drawPath(areaPath, areaPaint)
            }

            if (coords.size >= 2) {
                linePaint.color = s.color
                canvas.drawPath(path, linePaint)
            }

            dotPaint.color = s.color
            val dotRadius = if (coords.size > 35) context.dpF(2.2f) else context.dpF(3.6f)
            coords.forEachIndexed { idx, (cx, cy) ->
                canvas.drawCircle(cx, cy, dotRadius, dotPaint)
                if (coords.size <= 8 && idx < s.points.size && s.points[idx].isFinite()) {
                    val v = s.points[idx]
                    val valLabel = if (v >= 100f) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)
                    axisTextPaint.textAlign = Paint.Align.CENTER
                    axisTextPaint.color = s.color
                    val lblY = (cy - context.dpF(7f)).coerceAtLeast(chartTop + context.dpF(8f))
                    canvas.drawText(valLabel, cx, lblY, axisTextPaint)
                }
            }
            axisTextPaint.color = WiFiVisionPalette.mutedSlate
        }

        // Bottom X Labels (Start / Latest)
        if (xLabels.isNotEmpty()) {
            val yLbl = h - context.dpF(6f)
            axisTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(xLabels.first(), chartLeft, yLbl, axisTextPaint)
            if (xLabels.size > 1) {
                axisTextPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(xLabels.last(), chartRight, yLbl, axisTextPaint)
            }
        }
    }
}
