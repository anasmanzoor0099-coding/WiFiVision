package com.wifivision.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.util.LruCache
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Interactive OpenStreetMap-based native geographic Wi-Fi heatmap view.
 *
 * Respects OpenStreetMap tile usage policies:
 *  - Descriptive User-Agent identifying WiFiVision-Android/1.1.0
 *  - Bounded 2-thread tile downloader
 *  - In-memory LruCache + 7-day disk cache in app cacheDir
 *  - Visible "© OpenStreetMap contributors" attribution overlay
 */
class OsmHeatmapView(context: Context) : View(context) {

    enum class HeatmapLayer {
        SIGNAL,
        DENSITY
    }

    var layer: HeatmapLayer = HeatmapLayer.SIGNAL
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var selectedBand: BandFilter = BandFilter.ALL
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var onCellSelected: ((HeatmapPoint, NetworkDensityPoint?) -> Unit)? = null

    private var points: List<HeatmapPoint> = emptyList()
    private var densityByCoord: Map<String, NetworkDensityPoint> = emptyMap()
    private var userLocation: ObservationLocation? = null
    private var selectedKey: String? = null

    private var centerLat: Double = 29.3956
    private var centerLon: Double = 71.6836
    private var zoom: Double = 13.0
    private var hasInitializedCenter = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tileExecutor = Executors.newFixedThreadPool(2)
    private val tileCacheDir = File(context.cacheDir, "osm_tiles").apply { mkdirs() }
    private val tileMemoryCache = object : LruCache<String, Bitmap>(48) {}
    private val inFlightTiles = Collections.synchronizedSet(HashSet<String>())

    @Volatile
    private var isShutdown = false

    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fallbackBgPaint = Paint().apply {
        color = WiFiVisionPalette.softIvory
        style = Paint.Style.FILL
    }
    private val fallbackGridPaint = Paint().apply {
        color = WiFiVisionPalette.hairline
        strokeWidth = context.dpF(1f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = WiFiVisionPalette.darkSlate
        strokeWidth = context.dpF(1.8f)
    }
    private val selectedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = WiFiVisionPalette.champagneGold
        strokeWidth = context.dpF(3f)
    }
    private val userAccFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x222563EB
    }
    private val userAccStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x992563EB.toInt()
        strokeWidth = context.dpF(1.4f)
    }
    private val userDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2563EB.toInt()
    }
    private val userDotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = WiFiVisionPalette.pureWhite
        strokeWidth = context.dpF(2.2f)
    }
    private val attrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE6FFFFFF.toInt()
    }
    private val attrTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.darkSlate
        textSize = context.dpF(10f)
    }
    private val emptyHintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WiFiVisionPalette.bodySlate
        textSize = context.dpF(12.5f)
        textAlign = Paint.Align.CENTER
    }

    private val srcRect = Rect(0, 0, 256, 256)
    private val dstRect = RectF()
    private val attrRect = RectF()

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                if (scale.isFinite() && scale > 0f) {
                    val deltaZoom = kotlin.math.ln(scale.toDouble()) / kotlin.math.ln(2.0)
                    zoom = (zoom + deltaZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    invalidate()
                }
                return true
            }
        }
    )

    companion object {
        private const val TILE_SIZE = 256.0
        private const val MIN_ZOOM = 3.0
        private const val MAX_ZOOM = 17.5
        private const val MAX_LAT = 85.0511
        private const val TILE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        private const val USER_AGENT = "WiFiVision-Android/1.1.0 (https://wi-fi-vision-d6kt-theta.vercel.app)"
    }

    fun setData(
        newPoints: List<HeatmapPoint>,
        densityMap: Map<String, NetworkDensityPoint>,
        autoFit: Boolean = true
    ) {
        points = newPoints
        densityByCoord = densityMap
        if (autoFit || !hasInitializedCenter) {
            fitToData()
        } else {
            invalidate()
        }
    }

    fun setUserLocation(location: ObservationLocation?) {
        userLocation = location
        if (!hasInitializedCenter && points.isEmpty() && location != null && location.isValid()) {
            centerLat = location.latitude.coerceIn(-MAX_LAT, MAX_LAT)
            centerLon = location.longitude.coerceIn(-180.0, 180.0)
            zoom = 15.0
            hasInitializedCenter = true
        }
        invalidate()
    }

    fun selectCell(point: HeatmapPoint) {
        selectedKey = coordKey(point.latitude, point.longitude)
        invalidate()
    }

    fun centerOn(lat: Double, lon: Double, targetZoom: Double = zoom.coerceAtLeast(15.0)) {
        if (!lat.isFinite() || !lon.isFinite()) return
        centerLat = lat.coerceIn(-MAX_LAT, MAX_LAT)
        centerLon = lon.coerceIn(-180.0, 180.0)
        zoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        hasInitializedCenter = true
        invalidate()
    }

    fun zoomIn() {
        zoom = (zoom + 1.0).coerceIn(MIN_ZOOM, MAX_ZOOM)
        invalidate()
    }

    fun zoomOut() {
        zoom = (zoom - 1.0).coerceIn(MIN_ZOOM, MAX_ZOOM)
        invalidate()
    }

    fun fitToData() {
        val visible = visiblePoints()
        if (visible.isEmpty()) {
            userLocation?.takeIf { it.isValid() }?.let { loc ->
                centerOn(loc.latitude, loc.longitude, 15.0)
            } ?: invalidate()
            return
        }

        if (visible.size == 1) {
            centerOn(visible.first().latitude, visible.first().longitude, 15.0)
            return
        }

        val minLat = visible.minOf { it.latitude }
        val maxLat = visible.maxOf { it.latitude }
        val minLon = visible.minOf { it.longitude }
        val maxLon = visible.maxOf { it.longitude }
        centerLat = ((minLat + maxLat) / 2.0).coerceIn(-MAX_LAT, MAX_LAT)
        centerLon = ((minLon + maxLon) / 2.0).coerceIn(-180.0, 180.0)

        val w = (width.takeIf { it > 0 } ?: context.dp(320)).toDouble() * 0.72
        val h = (height.takeIf { it > 0 } ?: context.dp(320)).toDouble() * 0.72

        var bestZoom = 15.0
        while (bestZoom > MIN_ZOOM) {
            val dx = kotlin.math.abs(lonToWorldX(maxLon, bestZoom) - lonToWorldX(minLon, bestZoom))
            val dy = kotlin.math.abs(latToWorldY(minLat, bestZoom) - latToWorldY(maxLat, bestZoom))
            if (dx <= w && dy <= h) break
            bestZoom -= 0.5
        }
        zoom = bestZoom.coerceIn(MIN_ZOOM, 15.5)
        hasInitializedCenter = true
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(context.dp(340), widthMeasureSpec)
        val h = resolveSize(context.dp(330), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastTouchX = event.x
                lastTouchY = event.y
                downTouchX = event.x
                downTouchY = event.y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (!isDragging && hypot(event.x - downTouchX, event.y - downTouchY) > context.dpF(6f)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val cxWorld = lonToWorldX(centerLon, zoom) - dx.toDouble()
                        val cyWorld = latToWorldY(centerLat, zoom) - dy.toDouble()
                        centerLon = worldXToLon(cxWorld, zoom).coerceIn(-180.0, 180.0)
                        centerLat = worldYToLat(cyWorld, zoom).coerceIn(-MAX_LAT, MAX_LAT)
                        hasInitializedCenter = true
                        invalidate()
                    }
                }
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!isDragging && !scaleDetector.isInProgress) {
                    handleTap(event.x, event.y)
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(tapX: Float, tapY: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val centerWorldX = lonToWorldX(centerLon, zoom)
        val centerWorldY = latToWorldY(centerLat, zoom)
        val hitRadiusSq = context.dpF(28f).let { it * it }

        var nearest: HeatmapPoint? = null
        var nearestDistSq = Float.MAX_VALUE

        for (pt in visiblePoints()) {
            val sx = (w / 2.0 + (lonToWorldX(pt.longitude, zoom) - centerWorldX)).toFloat()
            val sy = (h / 2.0 + (latToWorldY(pt.latitude, zoom) - centerWorldY)).toFloat()
            val dSq = (sx - tapX) * (sx - tapX) + (sy - tapY) * (sy - tapY)
            if (dSq <= hitRadiusSq && dSq < nearestDistSq) {
                nearestDistSq = dSq
                nearest = pt
            }
        }

        if (nearest != null) {
            selectedKey = coordKey(nearest.latitude, nearest.longitude)
            invalidate()
            val density = densityByCoord[selectedKey]
            onCellSelected?.invoke(nearest, density)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Base canvas & subtle grid while OSM tiles load
        canvas.drawRect(0f, 0f, w, h, fallbackBgPaint)
        val gridStep = context.dpF(36f)
        var gx = gridStep
        while (gx < w) {
            canvas.drawLine(gx, 0f, gx, h, fallbackGridPaint)
            gx += gridStep
        }
        var gy = gridStep
        while (gy < h) {
            canvas.drawLine(0f, gy, w, gy, fallbackGridPaint)
            gy += gridStep
        }

        // Render OSM slippy map tiles
        val intZoom = floor(zoom).toInt().coerceIn(2, 17)
        val scale = 2.0.pow(zoom - intZoom)
        val tileScreenPx = (TILE_SIZE * scale).toFloat()
        val maxTileIndex = (1 shl intZoom)

        val centerTileWorldX = lonToWorldX(centerLon, intZoom.toDouble())
        val centerTileWorldY = latToWorldY(centerLat, intZoom.toDouble())

        val minWorldX = centerTileWorldX - (w / 2.0) / scale
        val maxWorldX = centerTileWorldX + (w / 2.0) / scale
        val minWorldY = centerTileWorldY - (h / 2.0) / scale
        val maxWorldY = centerTileWorldY + (h / 2.0) / scale

        val startTileX = floor(minWorldX / TILE_SIZE).toInt()
        val endTileX = ceil(maxWorldX / TILE_SIZE).toInt()
        val startTileY = max(0, floor(minWorldY / TILE_SIZE).toInt())
        val endTileY = min(maxTileIndex - 1, ceil(maxWorldY / TILE_SIZE).toInt())

        for (tx in startTileX..endTileX) {
            val wrappedTx = ((tx % maxTileIndex) + maxTileIndex) % maxTileIndex
            for (ty in startTileY..endTileY) {
                val bmp = getOrRequestTile(intZoom, wrappedTx, ty) ?: continue
                val left = (w / 2.0 + (tx * TILE_SIZE - centerTileWorldX) * scale).toFloat()
                val top = (h / 2.0 + (ty * TILE_SIZE - centerTileWorldY) * scale).toFloat()
                dstRect.set(left, top, left + tileScreenPx + 0.8f, top + tileScreenPx + 0.8f)
                canvas.drawBitmap(bmp, srcRect, dstRect, tilePaint)
            }
        }

        val centerWorldX = lonToWorldX(centerLon, zoom)
        val centerWorldY = latToWorldY(centerLat, zoom)
        val visible = visiblePoints()
        val maxDensity = visible.maxOfOrNull { bandCount(it, selectedBand) }?.coerceAtLeast(1) ?: 1

        // 1. Thermal Glow Layer
        for (pt in visible) {
            val sx = (w / 2.0 + (lonToWorldX(pt.longitude, zoom) - centerWorldX)).toFloat()
            val sy = (h / 2.0 + (latToWorldY(pt.latitude, zoom) - centerWorldY)).toFloat()
            if (sx < -context.dpF(60f) || sx > w + context.dpF(60f) || sy < -context.dpF(60f) || sy > h + context.dpF(60f)) {
                continue
            }

            val color = cellColor(pt, maxDensity)
            val glowRadius = if (layer == HeatmapLayer.SIGNAL) {
                context.dpF(34f)
            } else {
                val count = bandCount(pt, selectedBand)
                context.dpF(24f + (count.toFloat() / maxDensity.toFloat()) * 20f)
            }
            val rgb = color and 0x00FFFFFF
            glowPaint.shader = RadialGradient(
                sx,
                sy,
                glowRadius,
                intArrayOf(rgb or (0x8A shl 24), rgb or (0x44 shl 24), rgb),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(sx, sy, glowRadius, glowPaint)
        }

        // 2. Circle Markers Layer
        for (pt in visible) {
            val sx = (w / 2.0 + (lonToWorldX(pt.longitude, zoom) - centerWorldX)).toFloat()
            val sy = (h / 2.0 + (latToWorldY(pt.latitude, zoom) - centerWorldY)).toFloat()
            if (sx < -context.dpF(30f) || sx > w + context.dpF(30f) || sy < -context.dpF(30f) || sy > h + context.dpF(30f)) {
                continue
            }

            val color = cellColor(pt, maxDensity)
            val radius = if (layer == HeatmapLayer.SIGNAL) {
                context.dpF(8f)
            } else {
                val count = bandCount(pt, selectedBand)
                context.dpF(6.5f + (count.toFloat() / maxDensity.toFloat()) * 6.5f)
            }
            val key = coordKey(pt.latitude, pt.longitude)
            if (key == selectedKey) {
                canvas.drawCircle(sx, sy, radius + context.dpF(4f), selectedRingPaint)
            }
            markerFillPaint.color = color
            canvas.drawCircle(sx, sy, radius, markerFillPaint)
            canvas.drawCircle(sx, sy, radius, markerStrokePaint)
        }

        // 3. User GPS Marker Layer
        userLocation?.takeIf { it.isValid() }?.let { loc ->
            val ux = (w / 2.0 + (lonToWorldX(loc.longitude, zoom) - centerWorldX)).toFloat()
            val uy = (h / 2.0 + (latToWorldY(loc.latitude, zoom) - centerWorldY)).toFloat()
            loc.accuracyMeters?.takeIf { it > 0.0 }?.let { acc ->
                val metersPerPx = 156543.03392 * kotlin.math.cos(Math.toRadians(loc.latitude)) / 2.0.pow(zoom)
                val accRadius = (acc / metersPerPx.coerceAtLeast(0.05)).toFloat().coerceIn(context.dpF(12f), w * 0.45f)
                canvas.drawCircle(ux, uy, accRadius, userAccFillPaint)
                canvas.drawCircle(ux, uy, accRadius, userAccStrokePaint)
            }
            canvas.drawCircle(ux, uy, context.dpF(6.5f), userDotPaint)
            canvas.drawCircle(ux, uy, context.dpF(6.5f), userDotBorderPaint)
        }

        if (visible.isEmpty() && userLocation == null) {
            canvas.drawText(
                "No GPS-tagged observations match this filter",
                w / 2f,
                h / 2f,
                emptyHintPaint
            )
        }

        // Required OpenStreetMap Attribution Pill
        val attrLabel = "© OpenStreetMap contributors"
        val txtW = attrTextPaint.measureText(attrLabel)
        val padH = context.dpF(8f)
        val boxH = context.dpF(20f)
        attrRect.set(
            w - txtW - padH * 2f - context.dpF(8f),
            h - boxH - context.dpF(8f),
            w - context.dpF(8f),
            h - context.dpF(8f)
        )
        canvas.drawRoundRect(attrRect, context.dpF(6f), context.dpF(6f), attrBgPaint)
        canvas.drawText(attrLabel, attrRect.left + padH, attrRect.bottom - context.dpF(6f), attrTextPaint)
    }

    private fun visiblePoints(): List<HeatmapPoint> {
        return points.filter { pt ->
            pt.latitude in -MAX_LAT..MAX_LAT &&
                pt.longitude in -180.0..180.0 &&
                (selectedBand == BandFilter.ALL || bandCount(pt, selectedBand) > 0)
        }
    }

    private fun cellColor(pt: HeatmapPoint, maxDensity: Int): Int {
        return if (layer == HeatmapLayer.SIGNAL) {
            val sig = bandSignal(pt, selectedBand)
            if (sig != null) UiFormatters.rssiHeatmapColor(sig) else 0xFF868D98.toInt()
        } else {
            val cnt = bandCount(pt, selectedBand)
            UiFormatters.densityHeatmapColor(cnt, maxDensity)
        }
    }

    private fun bandCount(pt: HeatmapPoint, band: BandFilter): Int {
        if (band == BandFilter.ALL) return pt.totalObservations
        return pt.frequencyDistribution.firstOrNull { it.band.equals(band.label, ignoreCase = true) }?.count ?: 0
    }

    private fun bandSignal(pt: HeatmapPoint, band: BandFilter): Double? {
        if (band == BandFilter.ALL) return pt.averageSignal
        return pt.frequencyDistribution.firstOrNull { it.band.equals(band.label, ignoreCase = true) }?.averageSignal
    }

    private fun getOrRequestTile(z: Int, x: Int, y: Int): Bitmap? {
        val key = "${z}_${x}_$y"
        tileMemoryCache.get(key)?.let { return it }

        if (isShutdown || !inFlightTiles.add(key)) {
            return null
        }

        tileExecutor.execute {
            try {
                if (isShutdown) return@execute
                val file = File(tileCacheDir, "$key.png")
                var bitmap: Bitmap? = null

                if (file.exists() && (System.currentTimeMillis() - file.lastModified()) < TILE_TTL_MS) {
                    bitmap = BitmapFactory.decodeFile(file.absolutePath)
                }

                if (bitmap == null) {
                    bitmap = downloadAndCacheTile(z, x, y, file)
                }

                if (bitmap != null && !isShutdown) {
                    tileMemoryCache.put(key, bitmap)
                    mainHandler.post { if (!isShutdown) invalidate() }
                }
            } catch (_: Exception) {
                // Offline or blocked tile fails quietly with clean grid fallback
            } finally {
                inFlightTiles.remove(key)
            }
        }
        return null
    }

    private fun downloadAndCacheTile(z: Int, x: Int, y: Int, cacheFile: File): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://tile.openstreetmap.org/$z/$x/$y.png")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 7_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            try {
                FileOutputStream(cacheFile).use { fos ->
                    fos.write(bytes)
                }
            } catch (_: Exception) {
            }
            bmp
        } finally {
            conn?.disconnect()
        }
    }

    private fun lonToWorldX(lon: Double, z: Double): Double {
        return ((lon + 180.0) / 360.0) * TILE_SIZE * 2.0.pow(z)
    }

    private fun latToWorldY(lat: Double, z: Double): Double {
        val clamped = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val sinLat = sin(Math.toRadians(clamped))
        val yFrac = 0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)
        return yFrac * TILE_SIZE * 2.0.pow(z)
    }

    private fun worldXToLon(x: Double, z: Double): Double {
        val worldSize = TILE_SIZE * 2.0.pow(z)
        return (x / worldSize) * 360.0 - 180.0
    }

    private fun worldYToLat(y: Double, z: Double): Double {
        val worldSize = TILE_SIZE * 2.0.pow(z)
        val n = PI - 2.0 * PI * (y / worldSize)
        return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
    }

    private fun coordKey(lat: Double, lon: Double): String = "$lat,$lon"

    fun shutdown() {
        isShutdown = true
        tileExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
