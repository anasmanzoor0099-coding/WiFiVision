package com.wifivision.app

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class OverviewScreenController(
    private val context: Context,
    private val onScanClick: () -> Unit,
    private val onToggleMonitorClick: () -> Unit,
    private val onReviewUploadClick: () -> Unit,
    private val onRefreshGpsClick: () -> Unit,
    private val onRefreshCloudClick: () -> Unit,
    private val onOpenCloudExplorerClick: () -> Unit,
    private val onOpenPerformanceClick: () -> Unit,
    private val onOpenSettingsClick: () -> Unit
) {

    private val preferences = AppPreferences(context)
    val rootView: ScrollView
    private val contentCol: LinearLayout

    // Hero card views
    private val heroMonitorPill: TextView
    private val heroSsidText: TextView
    private val activeWifiDetailText: TextView
    private val heroSignalBars: SignalBarsView
    private val heroRssiText: TextView
    private val heroQualityText: TextView
    private val heroChipsRow: LinearLayout

    // Local Scan & Cloud Sync Controls
    private val permissionBadgePill: TextView
    private val permissionHintText: TextView
    private val scanStatusText: TextView
    private val syncStatusText: TextView
    val scanButton: WiFiActionButton
    val monitorButton: WiFiActionButton
    val uploadButton: WiFiActionButton
    val refreshCloudButton: WiFiActionButton

    // KPI Cards (6 real-data cards)
    private val kpiNearbyCount: TextView
    private val kpiNearbyRange: TextView
    private val kpiCloudTotal: TextView
    private val kpiCloudAvgSignal: TextView
    private val kpiAvgQuality: TextView
    private val kpiAvgQualityLabel: TextView
    private val kpiPerfDown: TextView
    private val kpiPerfMeta: TextView
    private val kpiSignalExtremes: TextView
    private val kpiSignalExtremesSub: TextView
    private val kpiSyncQueue: TextView
    private val kpiSyncQueueSub: TextView

    // Backend & DB Card
    private val backendStatusPill: TextView
    private val backendDetailText: TextView
    private val backendUrlText: TextView

    // GPS Card
    private val gpsPrecisionPill: TextView
    private val gpsStatusText: TextView
    private val gpsChipsRow: LinearLayout
    val refreshGpsButton: WiFiActionButton

    // Recent Cloud Observations Card
    private val recentCloudStatusText: TextView
    private val recentCloudListContainer: LinearLayout

    init {
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(24))
        }
        rootView = ScrollView(context).apply {
            addView(contentCol)
        }

        // 1. Active Wi-Fi Emerald Gradient Hero Card
        val heroCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = heroGradientDrawable(context)
            elevation = context.dpF(4f)
            setPadding(context.dp(18), context.dp(16), context.dp(18), context.dp(16))
        }

        val heroTop = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heroEyebrow = TextView(context).apply {
            text = "ACTIVE WI-FI CONNECTION"
            textSize = 10.5f
            letterSpacing = 0.09f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.champagneGold)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        heroMonitorPill = createPill(
            context,
            label = "LIVE MONITOR: OFF",
            textColor = WiFiVisionPalette.heroMutedText,
            bgColor = WiFiVisionPalette.heroGlassBg,
            borderColor = 0x33FFFFFF,
            dotColor = WiFiVisionPalette.heroMutedText
        )
        heroTop.addView(heroEyebrow)
        heroTop.addView(heroMonitorPill)

        val heroMain = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(10), 0, context.dp(8))
        }
        val heroSsidCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = context.dp(10)
            }
        }
        heroSsidText = TextView(context).apply {
            text = "Checking Wi-Fi..."
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.pureWhite)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        activeWifiDetailText = TextView(context).apply {
            text = "Reading active adapter telemetry"
            textSize = 12f
            setTextColor(WiFiVisionPalette.heroMutedText)
            setPadding(0, context.dp(3), 0, 0)
        }
        heroSsidCol.addView(heroSsidText)
        heroSsidCol.addView(activeWifiDetailText)

        val heroSignalBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
            background = roundedDrawable(context, WiFiVisionPalette.heroGlassBg, WiFiVisionPalette.heroGlassBorder, 14)
        }
        heroSignalBars = SignalBarsView(context).apply {
            activeBars = 0
            activeColor = WiFiVisionPalette.champagneGold
            inactiveColor = 0x44FFFFFF
        }
        heroRssiText = TextView(context).apply {
            text = "-- dBm"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.pureWhite)
            setPadding(0, context.dp(5), 0, 0)
        }
        heroQualityText = TextView(context).apply {
            text = "Idle"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.champagneGold)
        }
        heroSignalBox.addView(heroSignalBars, LinearLayout.LayoutParams(context.dp(28), context.dp(18)))
        heroSignalBox.addView(heroRssiText)
        heroSignalBox.addView(heroQualityText)

        heroMain.addView(heroSsidCol)
        heroMain.addView(heroSignalBox)

        heroChipsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val heroChipsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(heroChipsRow)
        }

        heroCard.addView(heroTop)
        heroCard.addView(heroMain)
        heroCard.addView(heroChipsScroll)

        // 2. Scanner & Cloud Sync Control Card
        val controlsCard = createCard(context)
        val ctrlHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ctrlHeader.addView(
            createSectionTitle(context, "SCAN & CLOUD SYNC CONTROLS").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        permissionBadgePill = createPill(
            context,
            label = "READY",
            textColor = WiFiVisionPalette.emeraldDeep,
            bgColor = WiFiVisionPalette.emeraldTint,
            borderColor = WiFiVisionPalette.emeraldBorder
        )
        ctrlHeader.addView(permissionBadgePill)

        permissionHintText = TextView(context).apply {
            textSize = 12f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, context.dp(8), 0, context.dp(2))
        }
        scanStatusText = TextView(context).apply {
            text = "Tap Scan Once for one unique nearby scan. Live Monitor is OFF by default."
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(4), 0, context.dp(2))
        }
        syncStatusText = TextView(context).apply {
            text = "Last sync: not synced yet"
            textSize = 11.5f
            setTextColor(WiFiVisionPalette.mutedSlate)
            setPadding(0, 0, 0, context.dp(12))
        }

        scanButton = WiFiActionButton(context).apply {
            configure(
                label = "Scan Once",
                iconRes = R.drawable.ic_scan_radar,
                textColor = WiFiVisionPalette.pureWhite,
                fillColor = WiFiVisionPalette.emeraldDeep,
                strokeColor = WiFiVisionPalette.emeraldDeep,
                rippleColor = WiFiVisionPalette.rippleLight,
                radiusDp = 12,
                textSizeSp = 13.5f
            )
            setOnClickListener { onScanClick() }
        }

        monitorButton = WiFiActionButton(context).apply {
            configure(
                label = "Start Live Monitor",
                textColor = WiFiVisionPalette.emeraldDeep,
                fillColor = WiFiVisionPalette.emeraldTint,
                strokeColor = WiFiVisionPalette.emeraldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 12.5f
            )
            setOnClickListener { onToggleMonitorClick() }
        }
        uploadButton = WiFiActionButton(context).apply {
            configure(
                label = "Upload",
                iconRes = R.drawable.ic_upload_cloud,
                textColor = WiFiVisionPalette.deepGold,
                fillColor = WiFiVisionPalette.softGoldBg,
                strokeColor = WiFiVisionPalette.goldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 12.5f
            )
            setOnClickListener { onReviewUploadClick() }
        }
        refreshCloudButton = WiFiActionButton(context).apply {
            configure(
                label = "Refresh Cloud Dashboard",
                textColor = WiFiVisionPalette.darkSlate,
                fillColor = WiFiVisionPalette.softIvory,
                strokeColor = WiFiVisionPalette.hairline,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 12.5f
            )
            setOnClickListener { onRefreshCloudClick() }
        }

        val secondaryRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(8), 0, 0)
            addView(monitorButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) })
            addView(refreshCloudButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(6) })
        }

        controlsCard.addView(ctrlHeader)
        controlsCard.addView(permissionHintText)
        controlsCard.addView(scanStatusText)
        controlsCard.addView(syncStatusText)
        controlsCard.addView(scanButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        controlsCard.addView(secondaryRow)

        // Connected Wi-Fi Focused 4-Card Grid (Download, Upload, Connected Quality, Last Measurement)
        val (c1, v1, s1) = buildKpiCard("DOWNLOAD SPEED", "Not measured", "Tap to measure internet speed")
        kpiNearbyCount = v1
        kpiNearbyRange = s1
        c1.isClickable = true
        c1.setOnClickListener { onOpenPerformanceClick() }

        val (c2, v2, s2) = buildKpiCard("UPLOAD SPEED", "Not measured", "Tap to measure internet speed")
        kpiCloudTotal = v2
        kpiCloudAvgSignal = s2
        c2.isClickable = true
        c2.setOnClickListener { onOpenPerformanceClick() }

        val (c3, v3, s3) = buildKpiCard("NETWORK QUALITY", "Unavailable", "Not connected")
        kpiAvgQuality = v3
        kpiAvgQualityLabel = s3

        val (c4, v4, s4) = buildKpiCard("LAST MEASUREMENT", "Not measured", "Sync status: Idle")
        kpiPerfDown = v4
        kpiPerfMeta = s4

        val (c5, v5, s5) = buildKpiCard("SIGNAL EXTREMES", "— / —", "Strongest / Weakest (dBm)")
        kpiSignalExtremes = v5
        kpiSignalExtremesSub = s5

        val (c6, v6, s6) = buildKpiCard("LOCAL SYNC QUEUE", "0", "0 uploaded total")
        kpiSyncQueue = v6
        kpiSyncQueueSub = s6

        // 4. Backend & MongoDB Status Card
        val backendCard = createCard(context)
        val backendHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        backendHeader.addView(
            createSectionTitle(context, "BACKEND & DATABASE STATUS").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        backendStatusPill = createPill(
            context,
            label = "CHECKING",
            textColor = WiFiVisionPalette.amberText,
            bgColor = WiFiVisionPalette.amberBg,
            borderColor = WiFiVisionPalette.amberBorder
        )
        backendHeader.addView(backendStatusPill)
        backendDetailText = TextView(context).apply {
            text = "Checking backend reachability and MongoDB connection..."
            textSize = 13f
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(8), 0, context.dp(2))
        }
        backendUrlText = TextView(context).apply {
            text = "Endpoint: ${preferences.loadBaseUrl()}"
            textSize = 11.5f
            setTextColor(WiFiVisionPalette.mutedSlate)
        }
        backendCard.isClickable = true
        backendCard.setOnClickListener { onOpenSettingsClick() }
        backendCard.addView(backendHeader)
        backendCard.addView(backendDetailText)
        backendCard.addView(backendUrlText)

        // 5. Device GPS Precision Card
        val gpsCard = createCard(context)
        val gpsHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val gpsPin = ImageView(context).apply {
            setImageResource(R.drawable.ic_gps_pin)
            setColorFilter(WiFiVisionPalette.deepGold)
            contentDescription = null
        }
        gpsHeader.addView(gpsPin, LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(8) })
        gpsHeader.addView(
            createSectionTitle(context, "DEVICE GPS PRECISION").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        gpsPrecisionPill = createPill(
            context,
            label = "Standby",
            textColor = WiFiVisionPalette.bodySlate,
            bgColor = WiFiVisionPalette.softIvory,
            borderColor = WiFiVisionPalette.hairline
        )
        gpsHeader.addView(gpsPrecisionPill)

        gpsStatusText = TextView(context).apply {
            text = "Tap Refresh GPS or Scan to capture genuine device coordinates."
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(8), 0, context.dp(6))
        }
        gpsChipsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        val gpsChipsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, context.dp(8))
            addView(gpsChipsRow)
        }
        refreshGpsButton = WiFiActionButton(context).apply {
            configure(
                label = "Refresh GPS",
                iconRes = R.drawable.ic_gps_pin,
                textColor = WiFiVisionPalette.emeraldDeep,
                fillColor = WiFiVisionPalette.emeraldTint,
                strokeColor = WiFiVisionPalette.emeraldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 12f
            )
            setOnClickListener { onRefreshGpsClick() }
        }
        gpsCard.addView(gpsHeader)
        gpsCard.addView(gpsStatusText)
        gpsCard.addView(gpsChipsScroll)
        gpsCard.addView(refreshGpsButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 6. Recent Cloud Observations Card
        val recentCard = createCard(context)
        val recentHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        recentHeader.addView(
            createSectionTitle(context, "CURRENT SCAN RESULTS").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        val openExplorerBtn = WiFiActionButton(context).apply {
            configure(
                label = "View Full History",
                textColor = WiFiVisionPalette.emeraldDeep,
                fillColor = WiFiVisionPalette.emeraldTint,
                strokeColor = WiFiVisionPalette.emeraldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 11.5f
            )
            setOnClickListener { onOpenCloudExplorerClick() }
        }
        recentHeader.addView(openExplorerBtn)
        recentCloudStatusText = TextView(context).apply {
            text = "No scan completed yet. Tap Scan Once to inspect nearby networks."
            textSize = 12f
            setTextColor(WiFiVisionPalette.mutedSlate)
            setPadding(0, context.dp(6), 0, context.dp(8))
        }
        recentCloudListContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        recentCard.addView(recentHeader)
        recentCard.addView(recentCloudStatusText)
        recentCard.addView(recentCloudListContainer)

        contentCol.addView(heroCard, marginParams(12))
        contentCol.addView(UiFormatters.twoColumnRow(context, c1, c2), marginParams(10))
        contentCol.addView(UiFormatters.twoColumnRow(context, c3, c4), marginParams(12))
        contentCol.addView(controlsCard)

        // Restore persisted latest speed measurement if available
        preferences.loadLatestSpeedMeasurement()?.let { saved ->
            updateLatestSpeedMeasurement(saved.downloadMbps, saved.uploadMbps, saved.measuredAtIso)
        }
    }

    private fun buildKpiCard(title: String, initialValue: String, initialSub: String): Triple<LinearLayout, TextView, TextView> {
        val card = createCard(context, paddingDp = 13)
        val lbl = TextView(context).apply {
            text = title
            textSize = 10f
            letterSpacing = 0.06f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.mutedSlate)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val valTxt = TextView(context).apply {
            text = initialValue
            textSize = 18.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(5), 0, context.dp(2))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val subTxt = TextView(context).apply {
            text = initialSub
            textSize = 11f
            setTextColor(WiFiVisionPalette.bodySlate)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        card.addView(lbl)
        card.addView(valTxt)
        card.addView(subTxt)
        return Triple(card, valTxt, subTxt)
    }

    fun updateActiveWifi(details: WifiDetails, isMonitoring: Boolean) {
        if (isMonitoring) {
            monitorButton.configure(
                label = "Stop Monitoring",
                textColor = WiFiVisionPalette.crimsonText,
                fillColor = WiFiVisionPalette.crimsonBg,
                strokeColor = WiFiVisionPalette.crimsonBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 12.5f
            )
            updatePillStyle(
                context,
                heroMonitorPill,
                label = "LIVE MONITOR: ON",
                textColor = WiFiVisionPalette.champagneGold,
                bgColor = WiFiVisionPalette.heroGlassBg,
                borderColor = WiFiVisionPalette.heroGlassBorder,
                dotColor = WiFiVisionPalette.champagneGold
            )
        } else {
            monitorButton.configure(
                label = "Start Live Monitor",
                textColor = WiFiVisionPalette.emeraldDeep,
                fillColor = WiFiVisionPalette.emeraldTint,
                strokeColor = WiFiVisionPalette.emeraldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 12.5f
            )
            updatePillStyle(
                context,
                heroMonitorPill,
                label = "LIVE MONITOR: OFF",
                textColor = WiFiVisionPalette.heroMutedText,
                bgColor = WiFiVisionPalette.heroGlassBg,
                borderColor = 0x33FFFFFF,
                dotColor = WiFiVisionPalette.heroMutedText
            )
        }

        heroChipsRow.removeAllViews()
        if (!details.connected) {
            heroSsidText.text = "Not Connected"
            activeWifiDetailText.text = "Connection status: Disconnected"
            heroSignalBars.activeBars = 0
            heroRssiText.text = "Unavailable"
            heroQualityText.text = "Disconnected"
            addHeroChip("Status: Disconnected")
            return
        }

        heroSsidText.text = NetworkPresentationUtils.formatSafeConnectedSsid(details)
        heroRssiText.text = details.signalDbm?.let { "$it dBm" } ?: "Unavailable"
        heroSignalBars.activeBars = NetworkPresentationUtils.signalBarCount(details.signalDbm, details.signalQuality)
        heroQualityText.text = details.signalDbm?.let { NetworkPresentationUtils.signalTierLabel(it) } ?: "Connected"

        activeWifiDetailText.text = if (details.ssidUnavailable) {
            "Connection status: Connected (grant location permission to view SSID)"
        } else {
            "Connection status: Connected"
        }

        addHeroChip("Status: Connected")
        addHeroChip(details.band)
        details.frequencyMhz?.let { addHeroChip("$it MHz") }
        if (details.security != "Unknown") addHeroChip(details.security)
    }

    private fun addHeroChip(label: String, goldHighlight: Boolean = false) {
        val chip = createMetadataChip(
            context,
            label = label,
            textColor = if (goldHighlight) WiFiVisionPalette.champagneGold else WiFiVisionPalette.pureWhite,
            bgColor = WiFiVisionPalette.heroGlassBg,
            borderColor = if (goldHighlight) WiFiVisionPalette.heroGlassBorder else 0x38FFFFFF
        )
        heroChipsRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = context.dp(8)
        })
    }

    fun updatePermissionState(permissionManager: WifiPermissionManager) {
        permissionHintText.text = permissionManager.getPermissionStatus()
        if (permissionManager.canScanWifi()) {
            updatePillStyle(
                context,
                permissionBadgePill,
                label = "READY",
                textColor = WiFiVisionPalette.emeraldDeep,
                bgColor = WiFiVisionPalette.emeraldTint,
                borderColor = WiFiVisionPalette.emeraldBorder
            )
        } else {
            updatePillStyle(
                context,
                permissionBadgePill,
                label = "ACTION NEEDED",
                textColor = WiFiVisionPalette.amberText,
                bgColor = WiFiVisionPalette.amberBg,
                borderColor = WiFiVisionPalette.amberBorder
            )
        }
    }

    fun updateScanStatus(message: String) {
        scanStatusText.text = message
    }

    private var latestSpeedMeasuredAtLabel: String? = null
    private var isBackendOffline: Boolean = false

    fun updateConnectedQuality(
        quality: NetworkPresentationUtils.LocalQualityAssessment?,
        details: WifiDetails
    ) {
        if (!details.connected) {
            kpiAvgQuality.text = "Unavailable"
            kpiAvgQualityLabel.text = "Not connected to Wi-Fi"
            return
        }
        if (quality == null) {
            kpiAvgQuality.text = "Not measured"
            kpiAvgQualityLabel.text = "Signal not measured"
            return
        }
        kpiAvgQuality.text = "${quality.score} / 100"
        kpiAvgQualityLabel.text = "${quality.classification} • Connected Wi-Fi"
        heroQualityText.text = quality.classification
    }

    fun updateLatestSpeedMeasurement(
        downloadMbps: Double?,
        uploadMbps: Double?,
        measuredAtIso: String?
    ) {
        if (downloadMbps != null && downloadMbps.isFinite()) {
            kpiNearbyCount.text = UiFormatters.formatMbps(downloadMbps)
            kpiNearbyRange.text = "Actual measured download"
        } else {
            kpiNearbyCount.text = "Not measured"
            kpiNearbyRange.text = "Tap to measure internet speed"
        }

        if (uploadMbps != null && uploadMbps.isFinite()) {
            kpiCloudTotal.text = UiFormatters.formatMbps(uploadMbps)
            kpiCloudAvgSignal.text = "Actual measured upload"
        } else {
            kpiCloudTotal.text = "Not measured"
            kpiCloudAvgSignal.text = "Tap to measure internet speed"
        }

        if (!measuredAtIso.isNullOrBlank()) {
            latestSpeedMeasuredAtLabel = UiFormatters.formatIsoShort(measuredAtIso)
            kpiPerfDown.text = latestSpeedMeasuredAtLabel
        }
    }

    fun updateLocalMetrics(
        nearby: List<WifiNetwork>,
        stats: ScanMergeStats,
        queueCounts: QueueStateCounts,
        lastSyncMs: Long
    ) {
        val lastMeasuredText = when {
            stats.lastScanAtMs != null -> UiFormatters.formatTimeOfDay(stats.lastScanAtMs)
            !latestSpeedMeasuredAtLabel.isNullOrBlank() -> latestSpeedMeasuredAtLabel!!
            else -> "Not measured"
        }
        kpiPerfDown.text = lastMeasuredText

        val syncStateLabel = when {
            queueCounts.uploadingCount > 0 -> "Syncing"
            isBackendOffline -> "Offline"
            queueCounts.failedCount > 0 && queueCounts.queuedCount > 0 -> "Sync Failed"
            queueCounts.queuedCount > 0 -> "Syncing"
            lastSyncMs > 0L || queueCounts.uploadedCount > 0 -> "Synced"
            else -> "Synced"
        }
        kpiPerfMeta.text = "Auto-sync: $syncStateLabel"
        syncStatusText.text = "Cloud synchronization: $syncStateLabel"
    }

    fun updateHealth(result: HealthCheckResult?, baseUrl: String) {
        backendUrlText.text = "Endpoint: $baseUrl"
        isBackendOffline = result != null && !result.reachable
        if (result == null) {
            updatePillStyle(context, backendStatusPill, "CHECKING", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
            backendDetailText.text = "Probing backend service and MongoDB..."
            return
        }
        when {
            result.healthy -> {
                updatePillStyle(context, backendStatusPill, "CONNECTED", WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder, WiFiVisionPalette.jade)
                val svc = result.service ?: "WiFiVision API"
                val db = result.databaseStatus ?: "connected"
                backendDetailText.text = "$svc • Database: $db"
            }
            result.reachable -> {
                updatePillStyle(context, backendStatusPill, "DEGRADED", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder, WiFiVisionPalette.amberText)
                backendDetailText.text = result.message
            }
            else -> {
                updatePillStyle(context, backendStatusPill, "OFFLINE", WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder, WiFiVisionPalette.crimsonText)
                backendDetailText.text = result.message
            }
        }
    }

    fun updateGpsAcquiring() {
        gpsStatusText.text = "Acquiring GPS measurement fix..."
        gpsChipsRow.visibility = View.GONE
        updatePillStyle(context, gpsPrecisionPill, "Acquiring", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
    }

    fun updateGpsSnapshot(snapshot: MeasurementLocationSnapshot) {
        val loc = snapshot.location
        gpsStatusText.text = String.format(Locale.US, "%.6f, %.6f", loc.latitude, loc.longitude)
        val badgeLabel = NetworkPresentationUtils.accuracyBadgeLabel(loc.accuracyMeters)
        val (tc, bg, bc) = when (NetworkPresentationUtils.classifyAccuracyTier(loc.accuracyMeters)) {
            AccuracyTier.HIGH -> Triple(WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
            AccuracyTier.MODERATE -> Triple(WiFiVisionPalette.deepGold, WiFiVisionPalette.softGoldBg, WiFiVisionPalette.goldBorder)
            AccuracyTier.LOW -> Triple(WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
            AccuracyTier.UNKNOWN -> Triple(WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
        }
        updatePillStyle(context, gpsPrecisionPill, badgeLabel, tc, bg, bc)
        gpsChipsRow.removeAllViews()
        addGpsChip("Provider: ${snapshot.provider.uppercase(Locale.US)}")
        val fixChipLabel = if (snapshot.fromLastKnownFallback) {
            val ageSec = ((System.currentTimeMillis() - snapshot.capturedAtMs) / 1000L).coerceAtLeast(0L)
            "Cached Fix (${ageSec}s ago)"
        } else {
            "Live Fix"
        }
        addGpsChip(fixChipLabel)
        addGpsChip(UiFormatters.formatTimeOfDay(snapshot.capturedAtMs))
        gpsChipsRow.visibility = View.VISIBLE
    }

    fun updateGpsUnavailable(reason: String) {
        gpsStatusText.text = "Location unavailable: $reason"
        gpsChipsRow.visibility = View.GONE
        updatePillStyle(context, gpsPrecisionPill, "Unavailable", WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
    }

    private fun addGpsChip(label: String) {
        val chip = createMetadataChip(context, label)
        gpsChipsRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = context.dp(6)
        })
    }

    fun setCloudLoading(loading: Boolean) {
        refreshCloudButton.isEnabled = !loading
        refreshCloudButton.text = if (loading) "Syncing Cloud Dashboard..." else "Refresh Cloud Dashboard"
    }

    private fun renderLatestScanObservations(latestScan: List<WifiNetwork>, scanTimeMs: Long?) {
        recentCloudListContainer.removeAllViews()
        if (latestScan.isEmpty() || scanTimeMs == null) {
            recentCloudStatusText.text = "No networks captured in the latest scan yet. Tap Scan Once to detect nearby Wi-Fi."
            recentCloudStatusText.setTextColor(WiFiVisionPalette.mutedSlate)
            return
        }

        recentCloudStatusText.text = "Latest scan at ${UiFormatters.formatTimeOfDay(scanTimeMs)} • ${latestScan.size} unique network(s) detected (historical records in View Full History)"
        recentCloudStatusText.setTextColor(WiFiVisionPalette.bodySlate)

        latestScan.forEachIndexed { idx, net ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, context.dp(8), 0, context.dp(8))
            }
            val leftCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            leftCol.addView(TextView(context).apply {
                text = net.displaySsid
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(WiFiVisionPalette.darkSlate)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            val freqLabel = if (net.frequencyMhz > 0) "${net.frequencyMhz} MHz" else net.band
            val obsTime = if (net.lastSeenAtMs > 0L) UiFormatters.formatTimeOfDay(net.lastSeenAtMs) else UiFormatters.formatTimeOfDay(scanTimeMs)
            leftCol.addView(TextView(context).apply {
                text = "${net.band} ($freqLabel) • ${net.security} • $obsTime"
                textSize = 11.5f
                setTextColor(WiFiVisionPalette.mutedSlate)
                setPadding(0, context.dp(2), 0, 0)
            })

            val (sigTc, sigBg, sigBc) = UiFormatters.signalColors(net.signalDbm)
            val netQuality = NetworkPresentationUtils.calculateQuality(
                signalDbm = net.signalDbm,
                signalQuality = net.signalQuality,
                band = net.band,
                security = net.security
            )
            val rightCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            rightCol.addView(createPill(context, "${net.signalDbm} dBm", sigTc, sigBg, sigBc))
            rightCol.addView(TextView(context).apply {
                text = "Quality ${netQuality.score}/100 • ${netQuality.classification}"
                textSize = 10.5f
                setTextColor(WiFiVisionPalette.mutedSlate)
                setPadding(0, context.dp(3), 0, 0)
            })

            row.addView(leftCol)
            row.addView(rightCol)
            recentCloudListContainer.addView(row)
            if (idx < latestScan.lastIndex) {
                recentCloudListContainer.addView(View(context).apply {
                    setBackgroundColor(WiFiVisionPalette.hairline)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(1).coerceAtLeast(1)))
            }
        }
    }

    fun updateCloudSummary(
        obsStats: ObservationAnalytics?,
        quality: NetworkQualityResponse?,
        perfStats: PerformanceAnalytics?,
        recentObs: List<CloudObservation>?,
        lastCloudSyncMs: Long,
        errorMessage: String?
    ) {
        // Overview never uses aggregate historical observations or unrelated network averages.
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
