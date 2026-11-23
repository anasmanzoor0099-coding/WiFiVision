package com.wifivision.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class HeatmapScreenController(
    private val context: Context,
    private val initialBand: BandFilter,
    private val onBandChanged: (BandFilter) -> Unit,
    private val onRefreshClick: (BandFilter) -> Unit,
    private val onLocateMeClick: () -> Unit
) {

    val rootView: ScrollView
    private val contentCol: LinearLayout
    val mapView: OsmHeatmapView

    private var selectedBand: BandFilter = initialBand
    private var activeLayer: OsmHeatmapView.HeatmapLayer = OsmHeatmapView.HeatmapLayer.SIGNAL
    private var points: List<HeatmapPoint> = emptyList()
    private var densityByCoord: Map<String, NetworkDensityPoint> = emptyMap()
    private var apiAccuracySummary: HeatmapAccuracySummary? = null
    private var apiLocationSummary: HeatmapLocationSummary? = null
    private var isLoading = false
    private var errorMessage: String? = null
    private var showAllCells = false

    private val bandRow: LinearLayout
    private val signalLayerBtn: WiFiActionButton
    private val densityLayerBtn: WiFiActionButton
    private val refreshBtn: WiFiActionButton
    private val locateBtn: WiFiActionButton

    private val errorBanner: LinearLayout
    private val errorText: TextView
    private val nonGeoBanner: LinearLayout
    private val nonGeoText: TextView

    private val kpiMappedCells: TextView
    private val kpiMappedSub: TextView
    private val kpiGeoObs: TextView
    private val kpiGeoSub: TextView
    private val kpiBandAvg: TextView
    private val kpiBandAvgSub: TextView
    private val kpiPeakDensity: TextView
    private val kpiPeakDensitySub: TextView

    private val accPrecisionPill: TextView
    private val accAvgText: TextView
    private val accWithText: TextView
    private val accWithoutText: TextView
    private val accCoarseText: TextView

    private val legendText: TextView
    private val selectedCellCard: LinearLayout
    private val selectedCellTitle: TextView
    private val selectedCellDetails: TextView

    private val cellsHeaderBadge: TextView
    private val showAllCellsBtn: WiFiActionButton
    private val cellsListContainer: LinearLayout

    init {
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(24))
        }
        rootView = ScrollView(context).apply {
            addView(contentCol)
        }

        // 1. Filter & Layer Controls Card
        val ctrlCard = createCard(context, paddingDp = 14)
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(createSectionTitle(context, "SPATIAL WI-FI HEATMAP").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        refreshBtn = WiFiActionButton(context).apply {
            configure("Refresh", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 11.5f)
            setOnClickListener { onRefreshClick(selectedBand) }
        }
        topRow.addView(refreshBtn)

        bandRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val layerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(10), 0, 0)
        }
        signalLayerBtn = WiFiActionButton(context).apply {
            setOnClickListener { switchLayer(OsmHeatmapView.HeatmapLayer.SIGNAL) }
        }
        densityLayerBtn = WiFiActionButton(context).apply {
            setOnClickListener { switchLayer(OsmHeatmapView.HeatmapLayer.DENSITY) }
        }
        layerRow.addView(signalLayerBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) })
        layerRow.addView(densityLayerBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(6) })

        ctrlCard.addView(topRow)
        ctrlCard.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, context.dp(10), 0, 0)
            addView(bandRow)
        })
        ctrlCard.addView(layerRow)

        // Honest disclaimer
        val disclaimer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = roundedDrawable(context, WiFiVisionPalette.softGoldBg, WiFiVisionPalette.goldBorder, 12)
            addView(TextView(context).apply {
                text = "Coordinates show the scanning device GPS location at capture time, never physical router locations or unmeasured areas."
                textSize = 11.5f
                setTextColor(WiFiVisionPalette.bodySlate)
            })
        }

        // Error & non-geolocated banners
        errorBanner = LinearLayout(context).apply {
            visibility = View.GONE
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = roundedDrawable(context, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder, 12)
        }
        errorText = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.crimsonText)
        }
        errorBanner.addView(errorText)

        nonGeoBanner = LinearLayout(context).apply {
            visibility = View.GONE
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = roundedDrawable(context, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder, 12)
        }
        nonGeoText = TextView(context).apply {
            textSize = 11.5f
            setTextColor(WiFiVisionPalette.amberText)
        }
        nonGeoBanner.addView(nonGeoText)

        // 2. Interactive OpenStreetMap Card with Overlay Controls
        val mapCard = createCard(context, paddingDp = 10)
        val mapContainer = FrameLayout(context)
        mapView = OsmHeatmapView(context).apply {
            selectedBand = this@HeatmapScreenController.selectedBand
            onCellSelected = { point, density -> showSelectedCell(point, density) }
        }
        mapContainer.addView(mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, context.dp(330)))

        val mapButtonsOverlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val zoomInBtn = createOverlayButton("+") { mapView.zoomIn() }
        val zoomOutBtn = createOverlayButton("−") { mapView.zoomOut() }
        val fitBtn = createOverlayButton("Fit") { mapView.fitToData() }
        locateBtn = createOverlayButton("GPS") { onLocateMeClick() }

        mapButtonsOverlay.addView(zoomInBtn, overlayMarginParams())
        mapButtonsOverlay.addView(zoomOutBtn, overlayMarginParams())
        mapButtonsOverlay.addView(fitBtn, overlayMarginParams())
        mapButtonsOverlay.addView(locateBtn, overlayMarginParams())

        mapContainer.addView(mapButtonsOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = context.dp(8)
            marginEnd = context.dp(8)
        })

        legendText = TextView(context).apply {
            text = "Signal Scale: Emerald >= -55 dBm • Lime >= -67 • Gold >= -75 • Orange >= -85 • Red < -85"
            textSize = 11f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(context.dp(4), context.dp(8), context.dp(4), context.dp(2))
        }
        mapCard.addView(mapContainer)
        mapCard.addView(legendText)

        // 3. Selected Cell Details Card
        selectedCellCard = createCard(context, fillColor = WiFiVisionPalette.softIvory, paddingDp = 14)
        selectedCellTitle = TextView(context).apply {
            text = "Tap a map marker or cell below"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.darkSlate)
        }
        selectedCellDetails = TextView(context).apply {
            text = "Select any coordinate cell to inspect band-specific RSSI, unique SSIDs, samples, and GPS precision."
            textSize = 12f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, context.dp(4), 0, 0)
        }
        selectedCellCard.addView(selectedCellTitle)
        selectedCellCard.addView(selectedCellDetails)

        // 4. 4 Summary KPIs
        val (k1, kv1, ks1) = buildMiniCard("MAPPED CELLS", "0", "All bands")
        kpiMappedCells = kv1; kpiMappedSub = ks1
        val (k2, kv2, ks2) = buildMiniCard("GPS OBSERVATIONS", "0", "Valid coordinates")
        kpiGeoObs = kv2; kpiGeoSub = ks2
        val (k3, kv3, ks3) = buildMiniCard("BAND AVG SIGNAL", "—", "Weighted dBm")
        kpiBandAvg = kv3; kpiBandAvgSub = ks3
        val (k4, kv4, ks4) = buildMiniCard("PEAK CELL DENSITY", "0", "Max unique SSIDs")
        kpiPeakDensity = kv4; kpiPeakDensitySub = ks4

        // 5. GPS Accuracy Summary Card
        val accCard = createCard(context, paddingDp = 14)
        val accHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        accHeader.addView(createSectionTitle(context, "GPS ACCURACY SUMMARY").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        accPrecisionPill = createPill(context, "Unknown", WiFiVisionPalette.bodySlate, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline)
        accHeader.addView(accPrecisionPill)

        val accGridRow1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(10), 0, context.dp(4))
        }
        accAvgText = TextView(context).apply {
            textSize = 12.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.darkSlate)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        accWithText = TextView(context).apply {
            textSize = 12.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.emeraldDeep)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        accGridRow1.addView(accAvgText)
        accGridRow1.addView(accWithText)

        val accGridRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        accWithoutText = TextView(context).apply {
            textSize = 12f; setTextColor(WiFiVisionPalette.bodySlate)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        accCoarseText = TextView(context).apply {
            textSize = 12f; setTextColor(WiFiVisionPalette.crimsonText)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        accGridRow2.addView(accWithoutText)
        accGridRow2.addView(accCoarseText)

        accCard.addView(accHeader)
        accCard.addView(accGridRow1)
        accCard.addView(accGridRow2)

        // 6. Cell Breakdown List Card
        val cellsCard = createCard(context, paddingDp = 14)
        val cellsHdr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cellsHdr.addView(createSectionTitle(context, "CELL SIGNAL & DENSITY").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        cellsHeaderBadge = createPill(context, "0 cells", WiFiVisionPalette.bodySlate, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline)
        cellsHdr.addView(cellsHeaderBadge)

        showAllCellsBtn = WiFiActionButton(context).apply {
            configure("Show All Cells", textColor = WiFiVisionPalette.emeraldDeep, fillColor = WiFiVisionPalette.emeraldTint, strokeColor = WiFiVisionPalette.emeraldBorder, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            visibility = View.GONE
            setOnClickListener {
                showAllCells = !showAllCells
                renderCellsList()
            }
        }
        cellsListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(8), 0, 0)
        }

        cellsCard.addView(cellsHdr)
        cellsCard.addView(cellsListContainer)
        cellsCard.addView(showAllCellsBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(10)
        })

        contentCol.addView(ctrlCard, marginParams(10))
        contentCol.addView(disclaimer, marginParams(10))
        contentCol.addView(errorBanner, marginParams(10))
        contentCol.addView(nonGeoBanner, marginParams(10))
        contentCol.addView(mapCard, marginParams(10))
        contentCol.addView(selectedCellCard, marginParams(10))
        contentCol.addView(UiFormatters.twoColumnRow(context, k1, k2), marginParams(10))
        contentCol.addView(UiFormatters.twoColumnRow(context, k3, k4), marginParams(10))
        contentCol.addView(accCard, marginParams(10))
        contentCol.addView(cellsCard)

        renderBandButtons()
        switchLayer(OsmHeatmapView.HeatmapLayer.SIGNAL)
        updateMetricsAndRender()
    }

    private fun buildMiniCard(title: String, value: String, sub: String): Triple<LinearLayout, TextView, TextView> {
        val card = createCard(context, paddingDp = 13)
        val lbl = TextView(context).apply {
            text = title; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.mutedSlate)
        }
        val v = TextView(context).apply {
            text = value; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(4), 0, context.dp(2))
        }
        val s = TextView(context).apply {
            text = sub; textSize = 11f; setTextColor(WiFiVisionPalette.bodySlate)
        }
        card.addView(lbl); card.addView(v); card.addView(s)
        return Triple(card, v, s)
    }

    private fun createOverlayButton(label: String, onClick: () -> Unit): WiFiActionButton {
        return WiFiActionButton(context).apply {
            minimumHeight = context.dp(34)
            setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
            configure(label, textColor = WiFiVisionPalette.darkSlate, fillColor = 0xEEFFFFFF.toInt(), strokeColor = WiFiVisionPalette.borderStrong, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 8, textSizeSp = 12f)
            setOnClickListener { onClick() }
        }
    }

    private fun overlayMarginParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(context.dp(46), context.dp(34)).apply {
            bottomMargin = context.dp(6)
        }
    }

    fun setLoading(loading: Boolean) {
        isLoading = loading
        refreshBtn.isEnabled = !loading
        refreshBtn.text = if (loading) "Loading..." else "Refresh"
    }

    fun setUserLocation(location: ObservationLocation?) {
        mapView.setUserLocation(location)
    }

    fun setSelectedBand(band: BandFilter) {
        if (selectedBand == band) return
        selectedBand = band
        mapView.selectedBand = band
        renderBandButtons()
    }

    fun setHeatmapData(
        heatmapResponse: HeatmapResponse?,
        densityResponse: NetworkDensityResponse?,
        error: String?
    ) {
        errorMessage = error
        points = heatmapResponse?.heatmap ?: emptyList()
        apiAccuracySummary = heatmapResponse?.accuracySummary
        apiLocationSummary = heatmapResponse?.locationSummary

        val map = LinkedHashMap<String, NetworkDensityPoint>()
        densityResponse?.density?.forEach { cell ->
            map["${cell.latitude},${cell.longitude}"] = cell
        }
        densityByCoord = map

        mapView.selectedBand = selectedBand
        mapView.setData(points, densityByCoord, autoFit = true)
        updateMetricsAndRender()
    }

    private fun switchLayer(nextLayer: OsmHeatmapView.HeatmapLayer) {
        activeLayer = nextLayer
        mapView.layer = nextLayer
        if (nextLayer == OsmHeatmapView.HeatmapLayer.SIGNAL) {
            signalLayerBtn.configure("Signal Strength", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 12f)
            densityLayerBtn.configure("Observation Density", textColor = WiFiVisionPalette.bodySlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            legendText.text = "Signal Scale: Emerald >= -55 dBm • Lime >= -67 • Gold >= -75 • Orange >= -85 • Red < -85"
        } else {
            signalLayerBtn.configure("Signal Strength", textColor = WiFiVisionPalette.bodySlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            densityLayerBtn.configure("Observation Density", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 12f)
            legendText.text = "Density Scale: Jade = Low (1-2 obs) • Gold = Medium • Crimson = High observation count"
        }
    }

    private fun renderBandButtons() {
        bandRow.removeAllViews()
        BandFilter.entries.forEach { band ->
            val selected = band == selectedBand
            val chip = TextView(context).apply {
                text = if (band == BandFilter.ALL) "All Bands" else band.label
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
                if (selected) {
                    setTextColor(WiFiVisionPalette.pureWhite)
                    background = rippleRoundedDrawable(context, WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.rippleLight, 999)
                } else {
                    setTextColor(WiFiVisionPalette.bodySlate)
                    background = rippleRoundedDrawable(context, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline, WiFiVisionPalette.rippleDark, 999)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (selectedBand != band) {
                        selectedBand = band
                        mapView.selectedBand = band
                        renderBandButtons()
                        onBandChanged(band)
                    }
                }
            }
            bandRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    private fun updateMetricsAndRender() {
        if (errorMessage != null) {
            errorText.text = errorMessage
            errorBanner.visibility = View.VISIBLE
        } else {
            errorBanner.visibility = View.GONE
        }

        val locSum = apiLocationSummary
        if (locSum != null && locSum.nonGeolocatedObservations > 0) {
            nonGeoText.text = "${locSum.nonGeolocatedObservations} of ${locSum.totalObservations} observation(s) lack valid GPS coordinates (e.g., Location off) and are excluded from the map."
            nonGeoBanner.visibility = View.VISIBLE
        } else {
            nonGeoBanner.visibility = View.GONE
        }

        kpiMappedCells.text = points.size.toString()
        kpiMappedSub.text = "Filter: ${selectedBand.label}"

        val geoCount = locSum?.geolocatedObservations ?: points.sumOf { it.totalObservations }
        kpiGeoObs.text = geoCount.toString()
        kpiGeoSub.text = if (locSum != null && locSum.nonGeolocatedObservations > 0) {
            "${locSum.nonGeolocatedObservations} excluded (no GPS)"
        } else {
            "Geolocated samples"
        }

        var weightSum = 0.0
        var obsSum = 0
        for (p in points) {
            val sig = if (selectedBand == BandFilter.ALL) {
                p.averageSignal
            } else {
                p.frequencyDistribution.firstOrNull { it.band.equals(selectedBand.label, true) }?.averageSignal
            }
            val cnt = if (selectedBand == BandFilter.ALL) {
                p.totalObservations
            } else {
                p.frequencyDistribution.firstOrNull { it.band.equals(selectedBand.label, true) }?.count ?: 0
            }
            if (sig != null && cnt > 0) {
                weightSum += sig * cnt
                obsSum += cnt
            }
        }
        kpiBandAvg.text = if (obsSum > 0) UiFormatters.formatSignal(weightSum / obsSum) else "—"
        kpiBandAvgSub.text = "Weighted (${selectedBand.label})"

        val peakUnique = points.maxOfOrNull { densityByCoord["${it.latitude},${it.longitude}"]?.uniqueNetworkCount ?: 0 } ?: 0
        kpiPeakDensity.text = peakUnique.toString()

        val acc = apiAccuracySummary
        val avgAcc = acc?.averageAccuracyMeters
        val tier = NetworkPresentationUtils.classifyAccuracyTier(avgAcc)
        val (aTc, aBg, aBc) = when (tier) {
            AccuracyTier.HIGH -> Triple(WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
            AccuracyTier.MODERATE -> Triple(WiFiVisionPalette.deepGold, WiFiVisionPalette.softGoldBg, WiFiVisionPalette.goldBorder)
            AccuracyTier.LOW -> Triple(WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
            AccuracyTier.UNKNOWN -> Triple(WiFiVisionPalette.bodySlate, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline)
        }
        updatePillStyle(context, accPrecisionPill, tier.name, aTc, aBg, aBc)
        accAvgText.text = "Avg Accuracy: ${UiFormatters.formatMeters(avgAcc)}"
        accWithText.text = "With GPS Accuracy: ${acc?.withAccuracyCount ?: 0}"
        accWithoutText.text = "Missing Accuracy: ${acc?.withoutAccuracyCount ?: 0}"
        accCoarseText.text = "Coarse (> 50 m): ${acc?.coarseAccuracyCount ?: 0}"

        renderCellsList()
    }

    private fun showSelectedCell(point: HeatmapPoint, density: NetworkDensityPoint?) {
        selectedCellTitle.text = String.format(Locale.US, "Cell %.3f, %.3f", point.latitude, point.longitude)
        val b24 = point.frequencyDistribution.firstOrNull { it.band == "2.4 GHz" }
        val b5 = point.frequencyDistribution.firstOrNull { it.band == "5 GHz" }
        val b6 = point.frequencyDistribution.firstOrNull { it.band == "6 GHz" }

        selectedCellDetails.text = buildString {
            appendLine("Avg Signal: ${UiFormatters.formatSignal(point.averageSignal)} • Range: ${point.strongestSignal} to ${point.weakestSignal} dBm")
            appendLine("Samples: ${point.totalObservations} • Unique SSIDs: ${density?.uniqueNetworkCount ?: "—"}")
            appendLine("Avg GPS Accuracy: ${UiFormatters.formatMeters(point.avgAccuracyMeters)}")
            append("2.4 GHz: ${b24?.takeIf { it.count > 0 }?.let { "${UiFormatters.formatSignal(it.averageSignal)} (${it.count})" } ?: "—"}  •  ")
            append("5 GHz: ${b5?.takeIf { it.count > 0 }?.let { "${UiFormatters.formatSignal(it.averageSignal)} (${it.count})" } ?: "—"}  •  ")
            append("6 GHz: ${b6?.takeIf { it.count > 0 }?.let { "${UiFormatters.formatSignal(it.averageSignal)} (${it.count})" } ?: "—"}")
        }.trim()
    }

    private fun renderCellsList() {
        cellsHeaderBadge.text = "${points.size} cells"
        val displayed = if (showAllCells) points else points.take(20)
        showAllCellsBtn.visibility = if (points.size > 20) View.VISIBLE else View.GONE
        showAllCellsBtn.text = if (showAllCells) "Show Top 20" else "Show All ${points.size} Cells"

        cellsListContainer.removeAllViews()
        if (displayed.isEmpty()) {
            cellsListContainer.addView(TextView(context).apply {
                text = if (isLoading) "Loading mapped coordinate cells..." else "No mapped cells match this filter yet."
                textSize = 12.5f
                setTextColor(WiFiVisionPalette.mutedSlate)
                setPadding(0, context.dp(6), 0, context.dp(4))
            })
            return
        }

        displayed.forEachIndexed { idx, pt ->
            val key = "${pt.latitude},${pt.longitude}"
            val density = densityByCoord[key]
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, context.dp(8), 0, context.dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    mapView.selectCell(pt)
                    mapView.centerOn(pt.latitude, pt.longitude, 15.5)
                    showSelectedCell(pt, density)
                }
            }
            val leftCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = context.dp(8)
                }
            }
            leftCol.addView(TextView(context).apply {
                text = String.format(Locale.US, "%.3f, %.3f", pt.latitude, pt.longitude)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(WiFiVisionPalette.darkSlate)
            })
            leftCol.addView(TextView(context).apply {
                text = "${pt.totalObservations} samples • ${density?.uniqueNetworkCount ?: "—"} SSIDs • GPS ${UiFormatters.formatMeters(pt.avgAccuracyMeters)}"
                textSize = 11.5f
                setTextColor(WiFiVisionPalette.bodySlate)
            })

            val (tc, bg, bc) = UiFormatters.signalColors(pt.averageSignal.toInt())
            row.addView(leftCol)
            row.addView(createPill(context, UiFormatters.formatSignal(pt.averageSignal), tc, bg, bc))
            cellsListContainer.addView(row)
            if (idx < displayed.lastIndex) {
                cellsListContainer.addView(View(context).apply {
                    setBackgroundColor(WiFiVisionPalette.hairline)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(1).coerceAtLeast(1)))
            }
        }
    }

    private fun marginParams(bottomDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = context.dp(bottomDp)
        }
    }
}
