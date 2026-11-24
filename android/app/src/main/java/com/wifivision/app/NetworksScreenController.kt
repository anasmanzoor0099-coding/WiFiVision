package com.wifivision.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class NetworksScreenController(
    private val context: Context,
    private val onScanClick: () -> Unit,
    private val onToggleMonitorClick: () -> Unit,
    private val onMonitorIntervalChanged: (Int) -> Unit,
    private val onUploadClick: () -> Unit,
    private val onClearSessionNetworksClick: () -> Unit,
    private val onRequestPermissionsClick: () -> Unit,
    private val onRefreshCloudClick: () -> Unit,
    private val onLoadMoreCloudClick: (nextPage: Int) -> Unit
) {

    enum class SubMode {
        LIVE_SCANNER,
        CLOUD_EXPLORER
    }

    enum class CloudQualityFilter(val label: String) {
        ALL("All Grades"),
        EXCELLENT("Excellent"),
        GOOD("Good"),
        FAIR("Fair"),
        POOR("Poor")
    }

    enum class CloudSort(val label: String) {
        NEWEST("Newest"),
        STRONGEST("Strongest"),
        WEAKEST("Weakest"),
        QUALITY("Quality")
    }

    val rootView: ScrollView
    private val contentCol: LinearLayout

    // Sub-mode switcher
    private var activeSubMode: SubMode = SubMode.LIVE_SCANNER
    private val liveTabBtn: WiFiActionButton
    private val cloudTabBtn: WiFiActionButton
    private val livePanel: LinearLayout
    private val cloudPanel: LinearLayout

    // Live Scanner state & views
    private var liveNetworks: List<WifiNetwork> = emptyList()
    private var lastScanStats: ScanMergeStats = ScanMergeStats()
    private var liveBandFilter: BandFilter = BandFilter.ALL
    private var liveSearchQuery: String = ""
    private var liveMonitorIntervalSec: Int = AppPreferences.DEFAULT_WIFI_MONITOR_INTERVAL_SEC
    private var isLiveMonitorActive: Boolean = false
    private var lastOutcomeMessage: String? = null
    private var lastOutcomeThrottled: Boolean = false

    val liveRescanBtn: WiFiActionButton
    val liveMonitorBtn: WiFiActionButton
    val liveUploadBtn: WiFiActionButton
    private val liveMonitorStatusPill: TextView
    private val liveClearBtn: TextView
    private val liveStatUniqueVal: TextView
    private val liveStatNewVal: TextView
    private val liveStatUpdatedVal: TextView
    private val liveStatLastScanVal: TextView
    private val liveMonitorIntervalRow: LinearLayout
    private val livePermissionBanner: LinearLayout
    private val livePermissionBannerText: TextView
    private val livePermissionActionBtn: WiFiActionButton
    private val liveSearchInput: EditText
    private val liveBandRow: LinearLayout
    private val liveSummaryText: TextView
    private val liveCardsContainer: LinearLayout

    // Cloud Explorer state & views
    private var cloudObservations: List<CloudObservation> = emptyList()
    private var qualityByObsId: Map<String, NetworkQualityScore> = emptyMap()
    private var cloudTotalCount: Int = 0
    private var cloudCurrentPage: Int = 1
    private var cloudHasMore: Boolean = false
    private var isCloudLoading: Boolean = false
    private var cloudError: String? = null
    private var cloudBandFilter: BandFilter = BandFilter.ALL
    private var cloudQualityFilter: CloudQualityFilter = CloudQualityFilter.ALL
    private var cloudSort: CloudSort = CloudSort.NEWEST
    private var cloudSearchQuery: String = ""

    private val cloudRefreshBtn: WiFiActionButton
    private val cloudSearchInput: EditText
    private val cloudBandRow: LinearLayout
    private val cloudQualityRow: LinearLayout
    private val cloudSortRow: LinearLayout
    private val cloudSummaryText: TextView
    private val cloudErrorBanner: LinearLayout
    private val cloudErrorText: TextView
    private val cloudCardsContainer: LinearLayout
    private val cloudLoadMoreBtn: WiFiActionButton

    init {
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(24))
        }
        rootView = ScrollView(context).apply {
            addView(contentCol)
        }

        // Sub-mode switcher bar
        val modeCard = createCard(context, paddingDp = 6)
        val modeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        liveTabBtn = WiFiActionButton(context).apply {
            setOnClickListener { switchSubMode(SubMode.LIVE_SCANNER) }
        }
        cloudTabBtn = WiFiActionButton(context).apply {
            setOnClickListener { switchSubMode(SubMode.CLOUD_EXPLORER) }
        }
        modeRow.addView(liveTabBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(4) })
        modeRow.addView(cloudTabBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(4) })
        modeCard.addView(modeRow)

        // ---------------------------------------------------------------------
        // PANEL A: LIVE SCANNER
        // ---------------------------------------------------------------------
        livePanel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val liveHeaderCard = createCard(context, paddingDp = 14)
        val liveTitleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        liveTitleRow.addView(
            createSectionTitle(context, "NATIVE WI-FI SCANNER").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        liveMonitorStatusPill = createPill(
            context,
            label = "MONITORING: OFF",
            textColor = WiFiVisionPalette.bodySlate,
            bgColor = WiFiVisionPalette.softIvory,
            borderColor = WiFiVisionPalette.hairline,
            dotColor = WiFiVisionPalette.mutedSlate
        )
        liveTitleRow.addView(liveMonitorStatusPill)

        liveSummaryText = TextView(context).apply {
            text = "Scan Once for one scan • Live Monitoring is OFF by default"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(6), 0, context.dp(8))
        }

        // 4-tile unique network stats strip
        val statsStrip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, context.dp(10))
        }
        fun buildMiniStat(label: String): Pair<LinearLayout, TextView> {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(8), context.dp(7), context.dp(8), context.dp(7))
                background = roundedDrawable(context, WiFiVisionPalette.warmIvory, WiFiVisionPalette.hairline, 10)
            }
            box.addView(TextView(context).apply {
                text = label
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(WiFiVisionPalette.mutedSlate)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            val valueView = TextView(context).apply {
                text = "0"
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(WiFiVisionPalette.darkSlate)
                setPadding(0, context.dp(2), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            box.addView(valueView)
            return box to valueView
        }
        val (uBox, uVal) = buildMiniStat("UNIQUE")
        val (nBox, nVal) = buildMiniStat("NEW")
        val (upBox, upVal) = buildMiniStat("UPDATED")
        val (tBox, tVal) = buildMiniStat("LAST SCAN")
        tVal.text = "Never"
        tVal.textSize = 12f
        liveStatUniqueVal = uVal
        liveStatNewVal = nVal
        liveStatUpdatedVal = upVal
        liveStatLastScanVal = tVal
        statsStrip.addView(uBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(4) })
        statsStrip.addView(nBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(4) })
        statsStrip.addView(upBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(4) })
        statsStrip.addView(tBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))

        // Distinct Scan Once + Live Monitoring + Upload buttons
        val liveBtnRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        liveRescanBtn = WiFiActionButton(context).apply {
            configure(
                label = "Scan Once",
                iconRes = R.drawable.ic_scan_radar,
                textColor = WiFiVisionPalette.pureWhite,
                fillColor = WiFiVisionPalette.emeraldDeep,
                strokeColor = WiFiVisionPalette.emeraldDeep,
                rippleColor = WiFiVisionPalette.rippleLight,
                radiusDp = 10,
                textSizeSp = 12f
            )
            setOnClickListener { onScanClick() }
        }
        liveMonitorBtn = WiFiActionButton(context).apply {
            configure(
                label = "Start Live Monitor",
                textColor = WiFiVisionPalette.emeraldDeep,
                fillColor = WiFiVisionPalette.emeraldTint,
                strokeColor = WiFiVisionPalette.emeraldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 11.5f
            )
            setOnClickListener { onToggleMonitorClick() }
        }
        liveUploadBtn = WiFiActionButton(context).apply {
            configure(
                label = "Upload",
                iconRes = R.drawable.ic_upload_cloud,
                textColor = WiFiVisionPalette.deepGold,
                fillColor = WiFiVisionPalette.softGoldBg,
                strokeColor = WiFiVisionPalette.goldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 12f
            )
            setOnClickListener { onUploadClick() }
        }
        liveBtnRow.addView(liveRescanBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) })
        liveBtnRow.addView(liveMonitorBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.18f).apply { marginEnd = context.dp(6) })
        liveBtnRow.addView(liveUploadBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.95f))

        // Live Monitor interval chips + Clear List link
        val intervalHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(2), 0, context.dp(6))
        }
        intervalHeaderRow.addView(TextView(context).apply {
            text = "Live Monitor interval (OFF by default):"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.bodySlate)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        liveClearBtn = TextView(context).apply {
            text = "Clear List"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.crimsonText)
            setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
            background = rippleRoundedDrawable(context, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder, WiFiVisionPalette.rippleDark, 8)
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener { onClearSessionNetworksClick() }
        }
        intervalHeaderRow.addView(liveClearBtn)

        liveMonitorIntervalRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val intervalScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, context.dp(10))
            addView(liveMonitorIntervalRow)
        }

        liveSearchInput = createStyledInput(context, "Filter SSID, BSSID, or security...").apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    liveSearchQuery = s?.toString().orEmpty().trim()
                    renderLiveCards()
                }
            })
        }

        liveBandRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val liveBandScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, context.dp(10), 0, 0)
            addView(liveBandRow)
        }

        liveHeaderCard.addView(liveTitleRow)
        liveHeaderCard.addView(liveSummaryText)
        liveHeaderCard.addView(statsStrip)
        liveHeaderCard.addView(liveBtnRow, marginParams(10))
        liveHeaderCard.addView(intervalHeaderRow)
        liveHeaderCard.addView(intervalScroll)
        liveHeaderCard.addView(liveSearchInput)
        liveHeaderCard.addView(liveBandScroll)

        // Throttling / permission / cache notice banner
        livePermissionBanner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
            background = roundedDrawable(context, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder, 12)
        }
        livePermissionBannerText = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.amberText)
        }
        livePermissionActionBtn = WiFiActionButton(context).apply {
            configure(
                label = "Grant Permissions",
                textColor = WiFiVisionPalette.pureWhite,
                fillColor = WiFiVisionPalette.amberText,
                strokeColor = WiFiVisionPalette.amberText,
                rippleColor = WiFiVisionPalette.rippleLight,
                radiusDp = 10,
                textSizeSp = 12f
            )
            visibility = View.GONE
            setOnClickListener { onRequestPermissionsClick() }
        }
        livePermissionBanner.addView(livePermissionBannerText)
        livePermissionBanner.addView(livePermissionActionBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(8)
        })

        liveCardsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        livePanel.addView(liveHeaderCard, marginParams(10))
        livePanel.addView(livePermissionBanner, marginParams(10))
        livePanel.addView(liveCardsContainer)

        // ---------------------------------------------------------------------
        // PANEL B: CLOUD EXPLORER
        // ---------------------------------------------------------------------
        cloudPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val cloudHeaderCard = createCard(context, paddingDp = 14)
        val cloudTop = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cloudTop.addView(
            createSectionTitle(context, "CLOUD NETWORK EXPLORER").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        cloudRefreshBtn = WiFiActionButton(context).apply {
            configure(
                label = "Refresh",
                textColor = WiFiVisionPalette.emeraldDeep,
                fillColor = WiFiVisionPalette.emeraldTint,
                strokeColor = WiFiVisionPalette.emeraldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 11.5f
            )
            setOnClickListener { onRefreshCloudClick() }
        }
        cloudTop.addView(cloudRefreshBtn)

        cloudSummaryText = TextView(context).apply {
            text = "Recorded observations synced to your account"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(6), 0, context.dp(10))
        }

        cloudSearchInput = createStyledInput(context, "Search SSID, security, source, or quality...").apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    cloudSearchQuery = s?.toString().orEmpty().trim()
                    renderCloudCards()
                }
            })
        }

        cloudBandRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        cloudQualityRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        cloudSortRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        cloudHeaderCard.addView(cloudTop)
        cloudHeaderCard.addView(cloudSummaryText)
        cloudHeaderCard.addView(cloudSearchInput)
        cloudHeaderCard.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, context.dp(10), 0, context.dp(4))
            addView(cloudBandRow)
        })
        cloudHeaderCard.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, context.dp(4), 0, context.dp(4))
            addView(cloudQualityRow)
        })
        cloudHeaderCard.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, context.dp(4), 0, 0)
            addView(cloudSortRow)
        })

        cloudErrorBanner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
            background = roundedDrawable(context, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder, 12)
        }
        cloudErrorText = TextView(context).apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.crimsonText)
        }
        cloudErrorBanner.addView(cloudErrorText)

        cloudCardsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        cloudLoadMoreBtn = WiFiActionButton(context).apply {
            configure(
                label = "Load More Observations",
                textColor = WiFiVisionPalette.emeraldDeep,
                fillColor = WiFiVisionPalette.emeraldTint,
                strokeColor = WiFiVisionPalette.emeraldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 13f
            )
            visibility = View.GONE
            setOnClickListener { onLoadMoreCloudClick(cloudCurrentPage + 1) }
        }

        cloudPanel.addView(cloudHeaderCard, marginParams(10))
        cloudPanel.addView(cloudErrorBanner, marginParams(10))
        cloudPanel.addView(cloudCardsContainer)
        cloudPanel.addView(cloudLoadMoreBtn, marginParams(8))

        contentCol.addView(modeCard, marginParams(12))
        contentCol.addView(livePanel)
        contentCol.addView(cloudPanel)

        switchSubMode(SubMode.LIVE_SCANNER)
        renderLiveIntervalChips()
        renderLiveCards()
        renderCloudCards()
    }

    fun switchSubMode(mode: SubMode) {
        activeSubMode = mode
        livePanel.visibility = if (mode == SubMode.LIVE_SCANNER) View.VISIBLE else View.GONE
        cloudPanel.visibility = if (mode == SubMode.CLOUD_EXPLORER) View.VISIBLE else View.GONE

        if (mode == SubMode.LIVE_SCANNER) {
            liveTabBtn.configure("Live Scanner", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 12.5f)
            cloudTabBtn.configure("Cloud Explorer", textColor = WiFiVisionPalette.bodySlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12.5f)
        } else {
            liveTabBtn.configure("Live Scanner", textColor = WiFiVisionPalette.bodySlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12.5f)
            cloudTabBtn.configure("Cloud Explorer", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 12.5f)
        }
    }

    fun setDefaultBand(band: BandFilter) {
        liveBandFilter = band
        cloudBandFilter = band
        renderLiveCards()
        renderCloudCards()
    }

    fun updateLivePermissionsAndOutcome(
        permissionManager: WifiPermissionManager,
        outcome: WifiScanOutcome?
    ) {
        if (outcome != null) {
            lastOutcomeMessage = outcome.message
            lastOutcomeThrottled = outcome.throttled
        }

        val missingPerms = !permissionManager.canScanWifi()
        val msg = when {
            missingPerms -> permissionManager.getPermissionStatus()
            lastOutcomeThrottled && lastOutcomeMessage != null -> lastOutcomeMessage!!
            else -> null
        }

        if (msg != null) {
            livePermissionBannerText.text = msg
            livePermissionActionBtn.visibility = if (!permissionManager.hasWifiScanPermissions()) View.VISIBLE else View.GONE
            livePermissionBanner.visibility = View.VISIBLE
        } else {
            livePermissionBanner.visibility = View.GONE
        }
    }

    fun updateLiveControls(
        isScanning: Boolean,
        isMonitoring: Boolean,
        monitorIntervalSec: Int,
        isUploading: Boolean,
        queueCounts: QueueStateCounts
    ) {
        isLiveMonitorActive = isMonitoring
        liveMonitorIntervalSec = monitorIntervalSec

        liveRescanBtn.isEnabled = !isScanning
        liveRescanBtn.text = if (isScanning) "Scanning..." else "Scan Once"

        if (isMonitoring) {
            updatePillStyle(
                context,
                liveMonitorStatusPill,
                label = "MONITORING: ON • ${monitorIntervalSec}s",
                textColor = WiFiVisionPalette.emeraldDeep,
                bgColor = WiFiVisionPalette.emeraldTint,
                borderColor = WiFiVisionPalette.emeraldBorder,
                dotColor = WiFiVisionPalette.jade
            )
            liveMonitorBtn.configure(
                "Stop Monitoring",
                textColor = WiFiVisionPalette.crimsonText,
                fillColor = WiFiVisionPalette.crimsonBg,
                strokeColor = WiFiVisionPalette.crimsonBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 11.5f
            )
        } else {
            updatePillStyle(
                context,
                liveMonitorStatusPill,
                label = "MONITORING: OFF",
                textColor = WiFiVisionPalette.bodySlate,
                bgColor = WiFiVisionPalette.softIvory,
                borderColor = WiFiVisionPalette.hairline,
                dotColor = WiFiVisionPalette.mutedSlate
            )
            liveMonitorBtn.configure(
                "Start Live Monitor",
                textColor = WiFiVisionPalette.emeraldDeep,
                fillColor = WiFiVisionPalette.emeraldTint,
                strokeColor = WiFiVisionPalette.emeraldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 11.5f
            )
        }

        liveUploadBtn.isEnabled = !isUploading && queueCounts.readyToSyncCount > 0
        liveUploadBtn.text = when {
            queueCounts.uploadingCount > 0 ->
                "Uploading (${queueCounts.uploadingCount}/${queueCounts.queuedCount})..."
            queueCounts.readyToSyncCount > 0 -> "Upload (${queueCounts.readyToSyncCount})"
            else -> "Upload"
        }

        renderLiveIntervalChips()
    }

    fun updateLiveNetworks(networks: List<WifiNetwork>, stats: ScanMergeStats) {
        liveNetworks = networks
        lastScanStats = stats
        renderLiveCards()
    }

    private fun renderLiveIntervalChips() {
        liveMonitorIntervalRow.removeAllViews()
        AppPreferences.VALID_WIFI_MONITOR_INTERVALS_SEC.forEach { sec ->
            val chip = createFilterChip("Every ${sec}s", sec == liveMonitorIntervalSec) {
                liveMonitorIntervalSec = sec
                renderLiveIntervalChips()
                onMonitorIntervalChanged(sec)
            }
            liveMonitorIntervalRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    fun setCloudLoading(loading: Boolean) {
        isCloudLoading = loading
        cloudRefreshBtn.isEnabled = !loading
        cloudRefreshBtn.text = if (loading) "Loading..." else "Refresh"
        cloudLoadMoreBtn.isEnabled = !loading
        cloudLoadMoreBtn.text = if (loading) "Loading..." else "Load More Observations"
        if (loading && cloudObservations.isEmpty()) {
            renderCloudCards()
        }
    }

    fun setCloudData(
        observations: List<CloudObservation>,
        totalCount: Int,
        page: Int,
        hasMore: Boolean,
        qualityList: List<NetworkQualityItem>,
        error: String?
    ) {
        cloudObservations = observations
        cloudTotalCount = totalCount
        cloudCurrentPage = page
        cloudHasMore = hasMore
        cloudError = error
        val map = LinkedHashMap<String, NetworkQualityScore>(qualityList.size)
        for (q in qualityList) {
            map[q.observationId] = q.quality
        }
        qualityByObsId = map
        renderCloudCards()
    }

    fun mergeQualityScores(qualityList: List<NetworkQualityItem>) {
        if (qualityList.isEmpty()) return
        val map = LinkedHashMap<String, NetworkQualityScore>(qualityByObsId)
        for (q in qualityList) {
            map[q.observationId] = q.quality
        }
        qualityByObsId = map
        renderCloudCards()
    }

    private fun renderLiveCards() {
        renderBandFilterRow(liveBandRow, liveNetworks.associateByBandCounts(), liveBandFilter) { selected ->
            liveBandFilter = selected
            renderLiveCards()
        }

        liveStatUniqueVal.text = liveNetworks.size.toString()
        liveStatNewVal.text = "+${lastScanStats.newlyDetectedCount}"
        liveStatNewVal.setTextColor(
            if (lastScanStats.newlyDetectedCount > 0) WiFiVisionPalette.emeraldDeep else WiFiVisionPalette.darkSlate
        )
        liveStatUpdatedVal.text = lastScanStats.updatedExistingCount.toString()
        liveStatUpdatedVal.setTextColor(
            if (lastScanStats.updatedExistingCount > 0) WiFiVisionPalette.deepGold else WiFiVisionPalette.darkSlate
        )
        liveStatLastScanVal.text = lastScanStats.lastScanAtMs?.let { UiFormatters.formatTimeOfDay(it) } ?: "Never"
        liveClearBtn.visibility = if (liveNetworks.isNotEmpty()) View.VISIBLE else View.GONE

        val bandFiltered = NetworkPresentationUtils.filterNetworks(liveNetworks, liveBandFilter)
        val q = liveSearchQuery.lowercase(Locale.US)
        val filtered = if (q.isEmpty()) {
            bandFiltered
        } else {
            bandFiltered.filter { net ->
                net.displaySsid.lowercase(Locale.US).contains(q) ||
                    (net.bssid?.lowercase(Locale.US)?.contains(q) == true) ||
                    net.security.lowercase(Locale.US).contains(q) ||
                    net.band.lowercase(Locale.US).contains(q)
            }
        }

        val timeSuffix = lastScanStats.lastScanAtMs?.let { " • Last scan ${UiFormatters.formatTimeOfDay(it)}" } ?: ""
        liveSummaryText.text = when {
            liveNetworks.isEmpty() -> "Tap Scan Once for one scan • Live Monitoring is OFF by default. Nearby network detection does not mean your phone is connected to these networks."
            liveBandFilter == BandFilter.ALL && q.isEmpty() ->
                "${liveNetworks.size} unique nearby network(s)$timeSuffix • Nearby detection does not mean connected"
            else ->
                "Showing ${filtered.size} of ${liveNetworks.size} unique$timeSuffix • Nearby detection does not mean connected"
        }

        liveCardsContainer.removeAllViews()
        if (liveNetworks.isEmpty()) {
            val emptyCard = createCard(context, paddingDp = 18).apply { gravity = Gravity.CENTER_HORIZONTAL }
            emptyCard.addView(TextView(context).apply {
                text = "Tap Scan Once to discover unique nearby Wi-Fi networks"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(WiFiVisionPalette.darkSlate)
                gravity = Gravity.CENTER
            })
            emptyCard.addView(TextView(context).apply {
                text = "Scan Once captures one location-aware scan and preserves unique networks (deduplicated by BSSID or SSID+Freq+Security) without repeating automatically. Enable Live Monitoring only when continuous updates are needed."
                textSize = 12f
                setTextColor(WiFiVisionPalette.bodySlate)
                gravity = Gravity.CENTER
                setPadding(0, context.dp(6), 0, 0)
            })
            liveCardsContainer.addView(emptyCard)
            return
        }

        if (filtered.isEmpty()) {
            val emptyFiltered = createCard(context, paddingDp = 18).apply { gravity = Gravity.CENTER_HORIZONTAL }
            emptyFiltered.addView(TextView(context).apply {
                text = "No nearby networks match your filter"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(WiFiVisionPalette.darkSlate)
            })
            liveCardsContainer.addView(emptyFiltered)
            return
        }

        for (network in filtered) {
            liveCardsContainer.addView(buildLiveNetworkCard(network), marginParams(10))
        }
    }

    private fun buildLiveNetworkCard(network: WifiNetwork): LinearLayout {
        val card = createCard(context, paddingDp = 14)
        val progressPct = NetworkPresentationUtils.signalProgressPercent(network.signalDbm)
        val barCount = NetworkPresentationUtils.signalBarCount(network.signalDbm, network.signalQuality)
        val (sigTc, sigBg, sigBc) = UiFormatters.signalColors(network.signalDbm)

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(SignalBarsView(context).apply {
            activeBars = barCount
            activeColor = sigTc
            inactiveColor = WiFiVisionPalette.hairline
        }, LinearLayout.LayoutParams(context.dp(22), context.dp(16)))
        topRow.addView(TextView(context).apply {
            text = network.displaySsid
            textSize = 15f
            typeface = if (network.ssid == null) Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC) else Typeface.DEFAULT_BOLD
            setTextColor(if (network.ssid == null) WiFiVisionPalette.deepGold else WiFiVisionPalette.darkSlate)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(context.dp(10), 0, context.dp(8), 0)
        })

        val statusBadge = when (network.discoveryStatus) {
            NetworkDiscoveryStatus.NEW -> createPill(context, "NEW", WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
            NetworkDiscoveryStatus.UPDATED -> createPill(context, "UPDATED", WiFiVisionPalette.deepGold, WiFiVisionPalette.softGoldBg, WiFiVisionPalette.goldBorder)
            NetworkDiscoveryStatus.PREVIOUS -> createPill(context, "PREV", WiFiVisionPalette.bodySlate, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline)
        }
        topRow.addView(statusBadge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = context.dp(6)
        })
        topRow.addView(createPill(context, "${network.signalDbm} dBm", sigTc, sigBg, sigBc))

        val meterRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(9), 0, context.dp(9))
        }
        meterRow.addView(SignalStrengthBarView(context).apply {
            progressPercent = progressPct
            fillColor = sigTc
        }, LinearLayout.LayoutParams(0, context.dp(6), 1f))
        meterRow.addView(TextView(context).apply {
            text = "${NetworkPresentationUtils.signalTierLabel(network.signalDbm)} • $progressPct%"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(sigTc)
            setPadding(context.dp(10), 0, 0, 0)
        })

        val chipsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        addChip(chipsRow, network.band)
        addChip(chipsRow, "${network.frequencyMhz} MHz")
        addSecurityChip(chipsRow, network.security)
        val localQuality = NetworkPresentationUtils.calculateQuality(
            signalDbm = network.signalDbm,
            signalQuality = network.signalQuality,
            band = network.band,
            security = network.security
        )
        addChip(chipsRow, "Score ${localQuality.score}/100 (${localQuality.classification})")

        val bssidView = TextView(context).apply {
            text = network.bssid?.let { "BSSID $it" } ?: "BSSID hidden • ID by SSID + Freq + Security"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(WiFiVisionPalette.mutedSlate)
            setPadding(0, context.dp(8), 0, 0)
        }

        card.addView(topRow)
        card.addView(meterRow)
        card.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipsRow)
        })
        card.addView(bssidView)

        if (network.lastSeenAtMs > 0L) {
            val seenPart = if (network.seenCount > 1) " • Seen ${network.seenCount}x" else ""
            val locPart = network.lastLocation?.accuracyMeters?.let { " • GPS ${UiFormatters.formatMeters(it)}" } ?: ""
            card.addView(TextView(context).apply {
                text = "Last seen ${UiFormatters.formatTimeOfDay(network.lastSeenAtMs)}$seenPart$locPart"
                textSize = 10.5f
                setTextColor(WiFiVisionPalette.mutedSlate)
                setPadding(0, context.dp(2), 0, 0)
            })
        }
        return card
    }

    private fun renderCloudCards() {
        val bandCounts = mapOf(
            BandFilter.ALL to cloudObservations.size,
            BandFilter.BAND_2_4 to cloudObservations.count { it.band.equals(BandFilter.BAND_2_4.label, true) },
            BandFilter.BAND_5 to cloudObservations.count { it.band.equals(BandFilter.BAND_5.label, true) },
            BandFilter.BAND_6 to cloudObservations.count { it.band.equals(BandFilter.BAND_6.label, true) }
        )
        renderBandFilterRow(cloudBandRow, bandCounts, cloudBandFilter) { selected ->
            cloudBandFilter = selected
            renderCloudCards()
        }

        renderChipGroup(
            cloudQualityRow,
            CloudQualityFilter.entries,
            { it.label },
            cloudQualityFilter
        ) { selected ->
            cloudQualityFilter = selected
            renderCloudCards()
        }

        renderChipGroup(
            cloudSortRow,
            CloudSort.entries,
            { "Sort: ${it.label}" },
            cloudSort
        ) { selected ->
            cloudSort = selected
            renderCloudCards()
        }

        if (cloudError != null) {
            cloudErrorText.text = cloudError
            cloudErrorBanner.visibility = View.VISIBLE
        } else {
            cloudErrorBanner.visibility = View.GONE
        }

        val q = cloudSearchQuery.lowercase(Locale.US)
        val filtered = cloudObservations.filter { obs ->
            if (cloudBandFilter != BandFilter.ALL && !obs.band.equals(cloudBandFilter.label, true)) {
                return@filter false
            }
            val scoreObj = qualityByObsId[obs.id]
            val grade = scoreObj?.classification ?: ""
            if (cloudQualityFilter != CloudQualityFilter.ALL && !grade.equals(cloudQualityFilter.label, true)) {
                return@filter false
            }
            if (q.isNotEmpty()) {
                val ssidDisplay = obs.ssid.ifBlank { "hidden network" }.lowercase(Locale.US)
                val match = ssidDisplay.contains(q) ||
                    obs.security.lowercase(Locale.US).contains(q) ||
                    obs.band.lowercase(Locale.US).contains(q) ||
                    obs.source.lowercase(Locale.US).contains(q) ||
                    grade.lowercase(Locale.US).contains(q)
                if (!match) return@filter false
            }
            true
        }

        val sorted = when (cloudSort) {
            CloudSort.NEWEST -> filtered
            CloudSort.STRONGEST -> filtered.sortedByDescending { it.signalDbm }
            CloudSort.WEAKEST -> filtered.sortedBy { it.signalDbm }
            CloudSort.QUALITY -> filtered.sortedByDescending { qualityByObsId[it.id]?.score ?: -1 }
        }

        cloudSummaryText.text = if (isCloudLoading && cloudObservations.isEmpty()) {
            "Loading historical cloud observations..."
        } else {
            "History: showing ${sorted.size} of ${cloudObservations.size} loaded ($cloudTotalCount total in workspace)"
        }

        cloudLoadMoreBtn.visibility = if (cloudHasMore) View.VISIBLE else View.GONE

        cloudCardsContainer.removeAllViews()
        if (sorted.isEmpty()) {
            val emptyCard = createCard(context, paddingDp = 18).apply { gravity = Gravity.CENTER_HORIZONTAL }
            emptyCard.addView(TextView(context).apply {
                text = when {
                    isCloudLoading -> "Loading cloud observations..."
                    cloudObservations.isEmpty() -> "No cloud observations synced yet"
                    else -> "No cloud observations match current filters"
                }
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(WiFiVisionPalette.darkSlate)
                gravity = Gravity.CENTER
            })
            emptyCard.addView(TextView(context).apply {
                text = "Tap any observation card when loaded to inspect its full 4-factor quality score breakdown."
                textSize = 12f
                setTextColor(WiFiVisionPalette.bodySlate)
                gravity = Gravity.CENTER
                setPadding(0, context.dp(6), 0, 0)
            })
            cloudCardsContainer.addView(emptyCard, marginParams(10))
            return
        }

        for (obs in sorted) {
            cloudCardsContainer.addView(buildCloudObservationCard(obs), marginParams(10))
        }
    }

    private fun buildCloudObservationCard(obs: CloudObservation): LinearLayout {
        val card = createCard(context, paddingDp = 14)
        val quality = qualityByObsId[obs.id]
        val (sigTc, sigBg, sigBc) = UiFormatters.signalColors(obs.signalDbm)
        val progressPct = NetworkPresentationUtils.signalProgressPercent(obs.signalDbm)

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ssidView = TextView(context).apply {
            text = obs.ssid.trim().ifEmpty { "<Hidden Network>" }
            textSize = 15f
            typeface = if (obs.ssid.isBlank()) Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC) else Typeface.DEFAULT_BOLD
            setTextColor(if (obs.ssid.isBlank()) WiFiVisionPalette.deepGold else WiFiVisionPalette.darkSlate)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = context.dp(8)
            }
        }
        topRow.addView(ssidView)
        topRow.addView(createPill(context, "${obs.signalDbm} dBm", sigTc, sigBg, sigBc))

        val barRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(8), 0, context.dp(8))
        }
        barRow.addView(SignalStrengthBarView(context).apply {
            progressPercent = progressPct
            fillColor = sigTc
        }, LinearLayout.LayoutParams(0, context.dp(6), 1f))
        if (quality != null) {
            val (qTc, qBg, qBc) = UiFormatters.qualityColors(quality.classification)
            barRow.addView(
                createPill(context, "${quality.score} • ${quality.classification}", qTc, qBg, qBc),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = context.dp(10)
                }
            )
        }

        val chipsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        addChip(chipsRow, obs.band)
        if (obs.frequencyMhz > 0) addChip(chipsRow, "${obs.frequencyMhz} MHz")
        addSecurityChip(chipsRow, obs.security)
        addChip(chipsRow, "source: ${obs.source}")
        if (obs.location != null) {
            addChip(chipsRow, "GPS ${UiFormatters.formatMeters(obs.location.accuracyMeters)}")
        }

        val footerText = TextView(context).apply {
            text = "Observed ${UiFormatters.formatIsoDateTime(obs.observedAt)} • Tap for quality breakdown"
            textSize = 11f
            setTextColor(WiFiVisionPalette.mutedSlate)
            setPadding(0, context.dp(8), 0, 0)
        }

        card.addView(topRow)
        card.addView(barRow)
        card.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipsRow)
        })
        card.addView(footerText)

        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { showObservationDetailDialog(obs, quality) }
        return card
    }

    private fun showObservationDetailDialog(obs: CloudObservation, quality: NetworkQualityScore?) {
        val details = buildString {
            appendLine("SSID: ${obs.ssid.trim().ifEmpty { "<Hidden Network>" }}")
            appendLine("Signal: ${obs.signalDbm} dBm (${NetworkPresentationUtils.signalTierLabel(obs.signalDbm)})")
            appendLine("Band / Freq: ${obs.band} • ${obs.frequencyMhz} MHz")
            appendLine("Security: ${obs.security}")
            appendLine("Source: ${obs.source}")
            appendLine("Observed: ${UiFormatters.formatIsoDateTime(obs.observedAt)}")
            appendLine()
            if (obs.location != null) {
                appendLine(String.format(Locale.US, "Device GPS: %.5f, %.5f (%s)", obs.location.latitude, obs.location.longitude, UiFormatters.formatMeters(obs.location.accuracyMeters)))
            } else {
                appendLine("Device GPS: Not recorded")
            }
            appendLine()
            if (quality != null) {
                appendLine("Network Quality Score: ${quality.score}/100 (${quality.classification})")
                appendLine("• Signal Power (70%): ${quality.components.signalScore}/100")
                appendLine("• Signal Level (15%): ${quality.components.signalLevelScore}/100")
                appendLine("• Band Capability (5%): ${quality.components.bandScore}/100")
                appendLine("• Security Posture (10%): ${quality.components.securityScore}/100")
            } else {
                appendLine("Network Quality Score: Included in recent 100 scored observations only.")
            }
        }.trim()

        AlertDialog.Builder(context)
            .setTitle("Network Observation Detail")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun List<WifiNetwork>.associateByBandCounts(): Map<BandFilter, Int> =
        NetworkPresentationUtils.countByBand(this)

    private fun renderBandFilterRow(
        row: LinearLayout,
        counts: Map<BandFilter, Int>,
        selected: BandFilter,
        onSelect: (BandFilter) -> Unit
    ) {
        row.removeAllViews()
        BandFilter.entries.forEach { filter ->
            val cnt = counts[filter] ?: 0
            val isSel = filter == selected
            val chip = createFilterChip("${filter.label} ($cnt)", isSel) { onSelect(filter) }
            row.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    private fun <T> renderChipGroup(
        row: LinearLayout,
        options: List<T>,
        labelOf: (T) -> String,
        selected: T,
        onSelect: (T) -> Unit
    ) {
        row.removeAllViews()
        options.forEach { option ->
            val chip = createFilterChip(labelOf(option), option == selected) { onSelect(option) }
            row.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    private fun createFilterChip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(context.dp(11), context.dp(6), context.dp(11), context.dp(6))
            if (selected) {
                setTextColor(WiFiVisionPalette.pureWhite)
                background = rippleRoundedDrawable(context, WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.rippleLight, 999)
            } else {
                setTextColor(WiFiVisionPalette.bodySlate)
                background = rippleRoundedDrawable(context, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline, WiFiVisionPalette.rippleDark, 999)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun addChip(row: LinearLayout, label: String) {
        row.addView(createMetadataChip(context, label), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = context.dp(6)
        })
    }

    private fun addSecurityChip(row: LinearLayout, security: String) {
        val chip = when {
            NetworkPresentationUtils.isWeakSecurity(security) -> createMetadataChip(context, security, WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
            security.equals("WPA3", true) || security.equals("WPA2", true) -> createMetadataChip(context, security, WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
            else -> createMetadataChip(context, security)
        }
        row.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = context.dp(6)
        })
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
