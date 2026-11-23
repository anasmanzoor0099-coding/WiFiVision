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
            setPadding(0, context.dp(8), 0, context.dp(8))
            addView(monitorButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) })
            addView(uploadButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(6) })
        }

        controlsCard.addView(ctrlHeader)
        controlsCard.addView(permissionHintText)
        controlsCard.addView(scanStatusText)
        controlsCard.addView(syncStatusText)
        controlsCard.addView(scanButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        controlsCard.addView(secondaryRow)
        controlsCard.addView(refreshCloudButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 3. 6-Card Real Intelligence KPI Grid
        val (c1, v1, s1) = buildKpiCard("UNIQUE NEARBY", "0", "Tap Scan Once")
        kpiNearbyCount = v1
        kpiNearbyRange = s1

        val (c2, v2, s2) = buildKpiCard("CLOUD OBSERVATIONS", "—", "Avg RSSI: —")
        kpiCloudTotal = v2
        kpiCloudAvgSignal = s2

        val (c3, v3, s3) = buildKpiCard("AVG NETWORK QUALITY", "—", "Awaiting scored observations")
        kpiAvgQuality = v3
        kpiAvgQualityLabel = s3

        val (c4, v4, s4) = buildKpiCard("SPEED & LATENCY", "—", "Tap to open Performance")
        kpiPerfDown = v4
        kpiPerfMeta = s4
        c4.isClickable = true
        c4.setOnClickListener { onOpenPerformanceClick() }

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
            createSectionTitle(context, "RECENT OBSERVATIONS").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        val openExplorerBtn = WiFiActionButton(context).apply {
            configure(
                label = "Open Explorer",
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
            text = "Loading cloud observations..."
            textSize = 12f
            setTextColor(WiFiVisionPalette.mutedSlate)
            setPadding(0, context.dp(6), 0, context.dp(8))
        }
        recentCloudListContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        recentCard.addView(recentHeader)
        recentCard.addView(recentCloudStatusText)
        recentCard.addView(recentCloudListContainer)

        contentCol.addView(heroCard, marginParams(12))
        contentCol.addView(controlsCard, marginParams(12))
        contentCol.addView(UiFormatters.twoColumnRow(context, c1, c2), marginParams(10))
        contentCol.addView(UiFormatters.twoColumnRow(context, c3, c4), marginParams(10))
        contentCol.addView(UiFormatters.twoColumnRow(context, c5, c6), marginParams(12))
        contentCol.addView(backendCard, marginParams(12))
        contentCol.addView(gpsCard, marginParams(12))
        contentCol.addView(recentCard)
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
            heroSsidText.text = "Not Connected to Wi-Fi"
            activeWifiDetailText.text = "Connect to Wi-Fi or tap Scan Once to inspect unique local signals."
            heroSignalBars.activeBars = 0
            heroRssiText.text = "-- dBm"
            heroQualityText.text = "Disconnected"
            addHeroChip("Adapter Disconnected")
            addHeroChip(if (isMonitoring) "Live Monitor ON" else "Live Monitor OFF")
            return
        }

        heroSsidText.text = details.displaySsid
        heroRssiText.text = details.signalDbm?.let { "$it dBm" } ?: "-- dBm"
        heroSignalBars.activeBars = NetworkPresentationUtils.signalBarCount(details.signalDbm, details.signalQuality)
        heroQualityText.text = details.signalDbm?.let { NetworkPresentationUtils.signalTierLabel(it) } ?: "Connected"

        val bssidSuffix = details.bssid?.let { " • $it" } ?: ""
        activeWifiDetailText.text = if (details.ssidUnavailable) {
            "Grant precise location permission to reveal SSID$bssidSuffix"
        } else {
            "Active wireless interface telemetry$bssidSuffix"
        }

        addHeroChip(details.band)
        details.frequencyMhz?.let { addHeroChip("$it MHz") }
        details.linkSpeedMbps?.let { addHeroChip("$it Mbps") }
        if (details.security != "Unknown") addHeroChip(details.security)
        addHeroChip(
            if (details.internetAvailable) "Internet Validated" else "No Internet Validation",
            goldHighlight = !details.internetAvailable
        )
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

    fun updateLocalMetrics(
        nearby: List<WifiNetwork>,
        stats: ScanMergeStats,
        pendingCount: Int,
        uploadedCount: Int,
        eligibleUploadCount: Int,
        lastSyncMs: Long
    ) {
        kpiNearbyCount.text = nearby.size.toString()
        kpiNearbyRange.text = when {
            stats.lastScanAtMs != null && nearby.isNotEmpty() ->
                "+${stats.newlyDetectedCount} new • ${stats.updatedExistingCount} upd • ${UiFormatters.formatTimeOfDay(stats.lastScanAtMs)}"
            stats.lastScanAtMs != null ->
                "Last scan ${UiFormatters.formatTimeOfDay(stats.lastScanAtMs)}"
            else -> "Tap Scan Once"
        }

        kpiSyncQueue.text = "$pendingCount queued"
        kpiSyncQueueSub.text = "$uploadedCount uploaded • $eligibleUploadCount new ready"
        val scanLabel = stats.lastScanAtMs?.let { "Last scan: ${UiFormatters.formatTimeOfDay(it)}" } ?: "No scan yet"
        val syncLabel = if (lastSyncMs > 0L) "Last sync: ${UiFormatters.formatTimeOfDay(lastSyncMs)}" else "Not synced yet"
        syncStatusText.text = "$scanLabel • $syncLabel"

        val badgeTotal = eligibleUploadCount + pendingCount
        uploadButton.text = if (badgeTotal > 0) "Upload ($badgeTotal)" else "Upload"
    }

    fun updateHealth(result: HealthCheckResult?, baseUrl: String) {
        backendUrlText.text = "Endpoint: $baseUrl"
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
        addGpsChip(if (snapshot.fromLastKnownFallback) "Cached Fix" else "Live Fix")
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

    fun updateCloudSummary(
        obsStats: ObservationAnalytics?,
        quality: NetworkQualityResponse?,
        perfStats: PerformanceAnalytics?,
        recentObs: List<CloudObservation>?,
        lastCloudSyncMs: Long,
        errorMessage: String?
    ) {
        if (lastCloudSyncMs > 0L) {
            preferences.setLastSyncEpochMs(lastCloudSyncMs)
        }

        if (obsStats != null) {
            kpiCloudTotal.text = obsStats.totalNetworks.toString()
            kpiCloudAvgSignal.text = "Avg RSSI: ${UiFormatters.formatSignal(obsStats.averageSignal)}"
            if (obsStats.strongestSignal != null && obsStats.weakestSignal != null) {
                kpiSignalExtremes.text = "${obsStats.strongestSignal} / ${obsStats.weakestSignal}"
                kpiSignalExtremesSub.text = "Cloud strongest / weakest dBm"
            } else {
                kpiSignalExtremes.text = "— / —"
                kpiSignalExtremesSub.text = "No cloud signal samples yet"
            }
        }

        if (quality != null) {
            val avg = quality.averageScore
            if (avg != null) {
                val grade = UiFormatters.classifyScoreLabel(avg)
                kpiAvgQuality.text = "$avg / 100"
                kpiAvgQualityLabel.text = "$grade (${quality.count} scored)"
            } else {
                kpiAvgQuality.text = "—"
                kpiAvgQualityLabel.text = "No scored networks yet"
            }
        }

        if (perfStats != null) {
            if (perfStats.totalTests > 0 && perfStats.averageDownloadMbps != null) {
                kpiPerfDown.text = UiFormatters.formatMbps(perfStats.averageDownloadMbps)
                kpiPerfMeta.text = "Latency ${UiFormatters.formatMs(perfStats.averageLatencyMs)} • ${perfStats.totalTests} tests"
            } else {
                kpiPerfDown.text = "No Tests"
                kpiPerfMeta.text = "Tap to run speed test"
            }
        }

        recentCloudListContainer.removeAllViews()
        if (errorMessage != null && recentObs.isNullOrEmpty()) {
            recentCloudStatusText.text = errorMessage
            recentCloudStatusText.setTextColor(WiFiVisionPalette.crimsonText)
            return
        }

        val list = recentObs.orEmpty().take(6)
        if (list.isEmpty()) {
            recentCloudStatusText.text = "No cloud observations recorded yet. Scan & upload from this phone or Windows Companion."
            recentCloudStatusText.setTextColor(WiFiVisionPalette.mutedSlate)
            return
        }

        recentCloudStatusText.text = if (lastCloudSyncMs > 0L) {
            "Showing latest ${list.size} cloud observations • Synced ${UiFormatters.formatTimeOfDay(lastCloudSyncMs)}"
        } else {
            "Showing latest ${list.size} cloud observations"
        }
        recentCloudStatusText.setTextColor(WiFiVisionPalette.bodySlate)

        list.forEachIndexed { idx, obs ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, context.dp(8), 0, context.dp(8))
            }
            val leftCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = context.dp(8)
                }
            }
            val displayName = obs.ssid.trim().ifEmpty { "<Hidden Network>" }
            leftCol.addView(TextView(context).apply {
                text = displayName
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (obs.ssid.isBlank()) WiFiVisionPalette.deepGold else WiFiVisionPalette.darkSlate)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            leftCol.addView(TextView(context).apply {
                text = "${obs.band} • ${obs.security} • source: ${obs.source}"
                textSize = 11.5f
                setTextColor(WiFiVisionPalette.bodySlate)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })

            val rightCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            val (tc, bg, bc) = UiFormatters.signalColors(obs.signalDbm)
            rightCol.addView(createPill(context, "${obs.signalDbm} dBm", tc, bg, bc))
            rightCol.addView(TextView(context).apply {
                text = UiFormatters.formatIsoShort(obs.observedAt)
                textSize = 10.5f
                setTextColor(WiFiVisionPalette.mutedSlate)
                setPadding(0, context.dp(3), 0, 0)
            })

            row.addView(leftCol)
            row.addView(rightCol)
            recentCloudListContainer.addView(row)
            if (idx < list.lastIndex) {
                recentCloudListContainer.addView(View(context).apply {
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
