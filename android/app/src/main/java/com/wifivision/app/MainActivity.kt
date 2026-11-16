package com.wifivision.app

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var permissionManager: WifiPermissionManager
    private lateinit var scanner: WifiScanner
    private lateinit var wifiMonitor: WifiMonitor
    private lateinit var locationProvider: LocationProvider
    private lateinit var observationQueue: ObservationQueue
    private lateinit var apiClient: BackendApiClient

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var backendPill: TextView
    private lateinit var statusText: TextView
    private lateinit var permissionHintText: TextView
    private lateinit var scanButton: Button
    private lateinit var monitorButton: Button
    private lateinit var retryQueueButton: Button

    private lateinit var activeWifiStatusText: TextView
    private lateinit var activeWifiDetailText: TextView

    private lateinit var gpsStatusText: TextView
    private lateinit var gpsNoteText: TextView
    private lateinit var refreshGpsButton: Button

    private lateinit var apiUrlInput: EditText
    private lateinit var applyUrlButton: Button
    private lateinit var testHealthButton: Button
    private lateinit var queueStatusText: TextView
    private lateinit var uploadBannerText: TextView

    private lateinit var networksHeaderText: TextView
    private lateinit var networkContainer: LinearLayout

    private var currentBaseUrl: String = ""
    private var latestLocation: MeasurementLocationSnapshot? = null
    private var latestWifiDetails: WifiDetails? = null
    private var lastScanNetworks: List<WifiNetwork> = emptyList()
    private var isScanInProgress = false
    private var isUploading = false
    private var isActivityStarted = false
    private var scanGeneration = 0L
    private var gpsGeneration = 0L
    private var pendingAction: PendingPermissionAction = PendingPermissionAction.NONE
    private var drainRunnable: Runnable? = null

    private enum class PendingPermissionAction {
        NONE,
        SCAN_AND_UPLOAD,
        GPS_ONLY,
        START_MONITOR
    }

    // Approved WiFiVision Companion Palette: Warm Ivory × Pure White × Dark Slate × Champagne Gold
    private val warmIvory = Color.rgb(247, 243, 235)
    private val pureWhite = Color.WHITE
    private val sandBorder = Color.rgb(229, 222, 201)
    private val darkSlate = Color.rgb(30, 41, 59)
    private val bodySlate = Color.rgb(71, 85, 105)
    private val mutedSlate = Color.rgb(100, 116, 139)
    private val champagneGold = Color.rgb(194, 155, 75)
    private val deepGold = Color.rgb(148, 112, 42)
    private val softGoldBg = Color.rgb(248, 240, 221)
    private val emeraldText = Color.rgb(22, 101, 52)
    private val emeraldBg = Color.rgb(220, 252, 231)
    private val amberText = Color.rgb(146, 64, 14)
    private val amberBg = Color.rgb(254, 243, 199)
    private val crimsonText = Color.rgb(153, 27, 27)
    private val crimsonBg = Color.rgb(254, 226, 226)

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_SETTINGS = "wifivision_settings"
        private const val KEY_API_BASE_URL = "api_base_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = WifiPermissionManager(this)
        scanner = WifiScanner(this, permissionManager)
        wifiMonitor = WifiMonitor(this, permissionManager)
        locationProvider = LocationProvider(this, permissionManager)
        observationQueue = ObservationQueue(this)
        apiClient = BackendApiClient()

        currentBaseUrl = loadSavedBaseUrl()
        buildDashboard()
        refreshStaticState()
        checkBackendHealth()
    }

    override fun onStart() {
        super.onStart()
        isActivityStarted = true
        refreshStaticState()
    }

    override fun onStop() {
        isActivityStarted = false
        cancelActiveScanAndGps(cancelledMessage = if (isScanInProgress) "Scan cancelled while app moved to background" else null)
        cancelScheduledDrain()
        if (wifiMonitor.isMonitoring) {
            wifiMonitor.stopMonitoring()
            updateMonitorButtonState()
        }
        super.onStop()
    }

    override fun onDestroy() {
        isActivityStarted = false
        cancelActiveScanAndGps(cancelledMessage = null)
        cancelScheduledDrain()
        wifiMonitor.stopMonitoring()
        apiClient.shutdown()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun isUiAlive(): Boolean = !isFinishing && !isDestroyed

    private fun cancelActiveScanAndGps(cancelledMessage: String?) {
        scanGeneration++
        gpsGeneration++
        scanner.cancelScan()
        locationProvider.cancel()

        val hadPendingScan = isScanInProgress
        isScanInProgress = false
        scanButton.isEnabled = true
        refreshGpsButton.isEnabled = true

        if (hadPendingScan && cancelledMessage != null && isUiAlive()) {
            statusText.text = cancelledMessage
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(warmIvory)
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }

        // Top header bar
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(this).apply {
            text = "WiFiVision"
            textSize = 24f
            setTextColor(darkSlate)
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            text = "ANDROID COMPANION"
            textSize = 11f
            letterSpacing = 0.16f
            setTextColor(deepGold)
            typeface = Typeface.DEFAULT_BOLD
        }

        titleCol.addView(title)
        titleCol.addView(subtitle)

        backendPill = TextView(this).apply {
            text = "CHECKING"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setTextColor(amberText)
            background = pillDrawable(amberBg, sandBorder)
        }

        headerRow.addView(titleCol)
        headerRow.addView(backendPill)
        root.addView(headerRow)

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(16))
        }

        // 1. Scan & Monitoring Controls Card
        val controlCard = createCard()
        statusText = TextView(this).apply {
            text = "Ready to collect Wi-Fi & GPS observations"
            textSize = 15f
            setTextColor(darkSlate)
            typeface = Typeface.DEFAULT_BOLD
        }
        permissionHintText = TextView(this).apply {
            textSize = 12f
            setTextColor(mutedSlate)
            setPadding(0, dp(4), 0, dp(12))
        }

        scanButton = Button(this).apply {
            text = "SCAN & UPLOAD OBSERVATIONS"
            setTextColor(pureWhite)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedDrawable(champagneGold, champagneGold, 10)
            isAllCaps = true
            setOnClickListener { handleScanAndUploadClick() }
        }

        val secondaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        monitorButton = Button(this).apply {
            text = "START MONITOR"
            setTextColor(darkSlate)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedDrawable(softGoldBg, champagneGold, 10)
            setOnClickListener { toggleMonitoring() }
        }

        retryQueueButton = Button(this).apply {
            text = "UPLOAD QUEUE"
            setTextColor(darkSlate)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedDrawable(warmIvory, sandBorder, 10)
            setOnClickListener { triggerQueueDrain(manual = true) }
        }

        secondaryRow.addView(
            monitorButton,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
        )
        secondaryRow.addView(
            retryQueueButton,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        controlCard.addView(statusText)
        controlCard.addView(permissionHintText)
        controlCard.addView(
            scanButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply {
                bottomMargin = dp(8)
            }
        )
        controlCard.addView(secondaryRow)
        content.addView(controlCard, cardMarginParams())

        // 2. Active Wi-Fi Connection Card (WifiMonitor)
        val activeCard = createCard()
        activeCard.addView(sectionLabel("ACTIVE WI-FI CONNECTION"))
        activeWifiStatusText = TextView(this).apply {
            text = "Checking connection..."
            textSize = 15f
            setTextColor(darkSlate)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(2))
        }
        activeWifiDetailText = TextView(this).apply {
            textSize = 12f
            setTextColor(bodySlate)
        }
        activeCard.addView(activeWifiStatusText)
        activeCard.addView(activeWifiDetailText)
        content.addView(activeCard, cardMarginParams())

        // 3. GPS Measurement Location Card
        val gpsCard = createCard()
        val gpsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val gpsTitle = sectionLabel("GPS MEASUREMENT LOCATION").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        refreshGpsButton = Button(this).apply {
            text = "REFRESH GPS"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(deepGold)
            background = roundedDrawable(softGoldBg, champagneGold, 8)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { handleRefreshGpsClick() }
        }
        gpsHeader.addView(gpsTitle)
        gpsHeader.addView(refreshGpsButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)))

        gpsStatusText = TextView(this).apply {
            text = "GPS fix not yet acquired"
            textSize = 13f
            setTextColor(darkSlate)
            setPadding(0, dp(6), 0, dp(4))
        }
        gpsNoteText = TextView(this).apply {
            text = "Coordinates reflect where this device measured, not the physical router location."
            textSize = 11f
            setTextColor(mutedSlate)
        }
        gpsCard.addView(gpsHeader)
        gpsCard.addView(gpsStatusText)
        gpsCard.addView(gpsNoteText)
        content.addView(gpsCard, cardMarginParams())

        // 4. Backend & Upload Queue Card
        val backendCard = createCard()
        backendCard.addView(sectionLabel("BACKEND & UPLOAD QUEUE"))

        apiUrlInput = EditText(this).apply {
            setText(currentBaseUrl)
            hint = if (BuildConfig.ALLOW_CLEARTEXT_DEV_URL) "http://10.0.2.2:5000" else "https://api.example.com"
            textSize = 13f
            setTextColor(darkSlate)
            setHintTextColor(mutedSlate)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedDrawable(warmIvory, sandBorder, 8)
        }

        val urlButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        applyUrlButton = Button(this).apply {
            text = "SAVE URL"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkSlate)
            background = roundedDrawable(softGoldBg, champagneGold, 8)
            setOnClickListener { applyApiBaseUrl() }
        }
        testHealthButton = Button(this).apply {
            text = "CHECK HEALTH"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkSlate)
            background = roundedDrawable(warmIvory, sandBorder, 8)
            setOnClickListener { checkBackendHealth() }
        }
        urlButtonsRow.addView(
            applyUrlButton,
            LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(8) }
        )
        urlButtonsRow.addView(
            testHealthButton,
            LinearLayout.LayoutParams(0, dp(36), 1f)
        )

        queueStatusText = TextView(this).apply {
            textSize = 13f
            setTextColor(darkSlate)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        }
        uploadBannerText = TextView(this).apply {
            textSize = 12f
            setTextColor(bodySlate)
            text = "No upload errors."
        }

        backendCard.addView(
            apiUrlInput,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
        )
        backendCard.addView(
            urlButtonsRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        backendCard.addView(queueStatusText)
        backendCard.addView(uploadBannerText)
        content.addView(backendCard, cardMarginParams())

        // 5. Detected Networks List
        networksHeaderText = TextView(this).apply {
            text = "DETECTED NETWORKS (0)"
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(deepGold)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(6), dp(2), dp(6))
        }
        content.addView(networksHeaderText)

        networkContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(networkContainer)

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedDrawable(pureWhite, sandBorder, 12)
        }
    }

    private fun cardMarginParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10)
        }
    }

    private fun sectionLabel(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 11f
            letterSpacing = 0.1f
            setTextColor(deepGold)
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun roundedDrawable(fillColor: Int, strokeColor: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun pillDrawable(fillColor: Int, strokeColor: Int): GradientDrawable {
        return roundedDrawable(fillColor, strokeColor, 999)
    }

    private fun loadSavedBaseUrl(): String {
        val prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_API_BASE_URL, null)
        if (!saved.isNullOrBlank()) {
            val validation = ApiContractUtils.validateBaseUrl(saved, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
            if (validation is UrlValidationResult.Valid) {
                return validation.normalizedUrl
            }
        }
        return BuildConfig.DEFAULT_API_BASE_URL
    }

    private fun refreshStaticState() {
        permissionHintText.text = permissionManager.getPermissionStatus()
        updateActiveWifiCard(wifiMonitor.getCurrentWifiInfo())
        updateQueueStats()
        updateMonitorButtonState()
    }

    private fun updateMonitorButtonState() {
        if (wifiMonitor.isMonitoring) {
            monitorButton.text = "STOP MONITOR"
            monitorButton.setTextColor(crimsonText)
            monitorButton.background = roundedDrawable(crimsonBg, crimsonText, 10)
        } else {
            monitorButton.text = "START MONITOR"
            monitorButton.setTextColor(darkSlate)
            monitorButton.background = roundedDrawable(softGoldBg, champagneGold, 10)
        }
    }

    private fun ensureRuntimePermissions(action: PendingPermissionAction): Boolean {
        val missing = permissionManager.requiredRuntimePermissions()
        if (missing.isEmpty() && permissionManager.hasWifiScanPermissions()) {
            return true
        }
        pendingAction = action
        val toRequest = if (missing.isNotEmpty()) {
            missing
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissions(toRequest, PERMISSION_REQUEST_CODE)
        return false
    }

    @Deprecated("Use the platform permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST_CODE) return

        permissionHintText.text = permissionManager.getPermissionStatus()
        updateActiveWifiCard(wifiMonitor.getCurrentWifiInfo())

        val action = pendingAction
        pendingAction = PendingPermissionAction.NONE

        if (permissionManager.hasWifiScanPermissions()) {
            when (action) {
                PendingPermissionAction.SCAN_AND_UPLOAD -> executeScanAndUpload()
                PendingPermissionAction.GPS_ONLY -> fetchGpsMeasurement(onComplete = {})
                PendingPermissionAction.START_MONITOR -> startWifiMonitoring()
                PendingPermissionAction.NONE -> Unit
            }
        } else {
            val deniedMessage = permissionManager.getPermissionStatus()
            statusText.text = deniedMessage
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_DENIED }) {
                uploadBannerText.text = "Permission denied. Grant location & nearby Wi-Fi permissions to collect real measurements."
            }
        }
    }

    private fun handleScanAndUploadClick() {
        if (isScanInProgress) return
        if (!ensureRuntimePermissions(PendingPermissionAction.SCAN_AND_UPLOAD)) return

        if (!permissionManager.isWifiEnabled()) {
            statusText.text = "Please enable Wi-Fi before scanning"
            return
        }
        if (!permissionManager.isLocationEnabled()) {
            statusText.text = "Please enable device location services"
            return
        }

        executeScanAndUpload()
    }

    private fun handleRefreshGpsClick() {
        if (isScanInProgress || !refreshGpsButton.isEnabled) return
        if (!ensureRuntimePermissions(PendingPermissionAction.GPS_ONLY)) return
        fetchGpsMeasurement(onComplete = {})
    }

    private fun toggleMonitoring() {
        if (wifiMonitor.isMonitoring) {
            wifiMonitor.stopMonitoring()
            updateMonitorButtonState()
            statusText.text = "Active Wi-Fi monitoring stopped"
            return
        }

        if (!ensureRuntimePermissions(PendingPermissionAction.START_MONITOR)) return
        startWifiMonitoring()
    }

    private fun startWifiMonitoring() {
        val started = wifiMonitor.startMonitoring { details ->
            if (!isUiAlive() || !wifiMonitor.isMonitoring) return@startMonitoring
            latestWifiDetails = details
            updateActiveWifiCard(details)
            if (details.connected) {
                val loc = latestLocation?.location
                val payload = details.toObservationPayload(loc, System.currentTimeMillis())
                if (payload != null) {
                    val summary = observationQueue.logic.enqueueObservations(
                        listOf(payload),
                        nowMs = System.currentTimeMillis(),
                        applyBatchCap = false
                    )
                    if (summary.acceptedCount > 0) {
                        observationQueue.persist()
                        updateQueueStats()
                        if (isActivityStarted) {
                            scheduleQueueDrain(0L)
                        }
                    }
                }
            }
        }
        updateMonitorButtonState()
        statusText.text = if (started) {
            "Active Wi-Fi monitoring started"
        } else {
            "Monitoring is already active"
        }
    }

    private fun fetchGpsMeasurement(onComplete: (ObservationLocation?) -> Unit) {
        val token = ++gpsGeneration
        gpsStatusText.text = "Acquiring GPS measurement fix..."
        refreshGpsButton.isEnabled = false

        locationProvider.fetchCurrentLocation { result ->
            if (!isUiAlive() || token != gpsGeneration) return@fetchCurrentLocation
            refreshGpsButton.isEnabled = true
            when (result) {
                is LocationFixResult.Available -> {
                    latestLocation = result.snapshot
                    val loc = result.snapshot.location
                    val accStr = loc.accuracyMeters?.let { String.format(Locale.US, "±%.1f m", it) } ?: "accuracy n/a"
                    val mode = if (result.snapshot.fromLastKnownFallback) "cached" else "live"
                    gpsStatusText.text = String.format(
                        Locale.US,
                        "%.6f, %.6f  (%s • %s • %s)",
                        loc.latitude,
                        loc.longitude,
                        accStr,
                        result.snapshot.provider,
                        mode
                    )
                    onComplete(loc)
                }
                is LocationFixResult.Unavailable -> {
                    latestLocation = null
                    gpsStatusText.text = "Location unavailable: ${result.reason}"
                    onComplete(null)
                }
            }
        }
    }

    private fun executeScanAndUpload() {
        if (isScanInProgress) return
        val token = ++scanGeneration
        isScanInProgress = true
        scanButton.isEnabled = false
        statusText.text = "Acquiring GPS location & scanning Wi-Fi..."
        permissionHintText.text = permissionManager.getPermissionStatus()

        fetchGpsMeasurement { measurementLocation ->
            if (!isUiAlive() || !isActivityStarted || token != scanGeneration) {
                return@fetchGpsMeasurement
            }

            scanner.requestScan { outcome ->
                if (!isUiAlive() || token != scanGeneration) return@requestScan
                isScanInProgress = false
                scanButton.isEnabled = true

                val nowMs = System.currentTimeMillis()
                lastScanNetworks = outcome.networks
                renderNetworks(outcome.networks)

                val activeWifi = wifiMonitor.getCurrentWifiInfo()
                latestWifiDetails = activeWifi
                updateActiveWifiCard(activeWifi)

                val candidates = mutableListOf<ObservationPayload>()
                activeWifi.toObservationPayload(measurementLocation, nowMs)?.let {
                    candidates.add(it)
                }
                outcome.networks.forEach { network ->
                    candidates.add(network.toObservationPayload(measurementLocation, nowMs))
                }

                val enqueueSummary = observationQueue.logic.enqueueObservations(candidates, nowMs)
                observationQueue.persist()
                updateQueueStats()

                val locationSuffix = if (measurementLocation == null) {
                    " (GPS unavailable; observations queued without coordinates)"
                } else {
                    ""
                }
                statusText.text = "${outcome.message}$locationSuffix"
                uploadBannerText.text = buildEnqueueSummaryMessage(enqueueSummary)

                if (isActivityStarted && observationQueue.logic.size() > 0) {
                    scheduleQueueDrain(0L)
                }
            }
        }
    }

    private fun buildEnqueueSummaryMessage(summary: EnqueueSummary): String {
        val parts = mutableListOf<String>()
        parts.add("Queued ${summary.acceptedCount}")
        if (summary.skippedDuplicateCount > 0) {
            parts.add("${summary.skippedDuplicateCount} duplicates skipped")
        }
        if (summary.skippedBatchCapCount > 0) {
            parts.add("${summary.skippedBatchCapCount} rate-cap deferred")
        }
        if (summary.skippedInvalidCount > 0) {
            parts.add("${summary.skippedInvalidCount} invalid ignored")
        }
        if (summary.droppedOldestCount > 0) {
            parts.add("${summary.droppedOldestCount} oldest evicted")
        }
        return parts.joinToString(" • ")
    }

    private fun applyApiBaseUrl() {
        val raw = apiUrlInput.text?.toString().orEmpty()
        when (val validation = ApiContractUtils.validateBaseUrl(raw, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)) {
            is UrlValidationResult.Valid -> {
                currentBaseUrl = validation.normalizedUrl
                apiUrlInput.setText(currentBaseUrl)
                getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_API_BASE_URL, currentBaseUrl)
                    .apply()
                uploadBannerText.text = "Backend URL saved: $currentBaseUrl"
                checkBackendHealth()
            }
            is UrlValidationResult.Invalid -> {
                setBackendPill("URL INVALID", crimsonText, crimsonBg)
                uploadBannerText.text = validation.reason
            }
        }
    }

    private fun checkBackendHealth() {
        when (val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)) {
            is UrlValidationResult.Invalid -> {
                setBackendPill("URL REQUIRED", amberText, amberBg)
                uploadBannerText.text = validation.reason
                return
            }
            is UrlValidationResult.Valid -> currentBaseUrl = validation.normalizedUrl
        }

        setBackendPill("CHECKING", amberText, amberBg)
        apiClient.checkHealth(currentBaseUrl) { result ->
            if (!isUiAlive()) return@checkHealth
            when {
                result.healthy -> {
                    setBackendPill("CONNECTED", emeraldText, emeraldBg)
                    if (isActivityStarted && observationQueue.logic.size() > 0 && !isUploading) {
                        scheduleQueueDrain(0L)
                    }
                }
                result.reachable -> {
                    setBackendPill("DEGRADED", amberText, amberBg)
                }
                else -> {
                    setBackendPill("OFFLINE", crimsonText, crimsonBg)
                }
            }
            if (!result.healthy) {
                uploadBannerText.text = "Backend health: ${result.message}"
            }
        }
    }

    private fun setBackendPill(label: String, textColor: Int, bgColor: Int) {
        backendPill.text = label
        backendPill.setTextColor(textColor)
        backendPill.background = pillDrawable(bgColor, sandBorder)
    }

    private fun triggerQueueDrain(manual: Boolean) {
        if (observationQueue.logic.size() == 0) {
            uploadBannerText.text = "Queue is empty; nothing pending upload."
            return
        }
        when (val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)) {
            is UrlValidationResult.Invalid -> {
                uploadBannerText.text = validation.reason
                return
            }
            is UrlValidationResult.Valid -> currentBaseUrl = validation.normalizedUrl
        }

        if (manual && observationQueue.logic.globalPauseUntilMs > System.currentTimeMillis()) {
            val remainingSec = ((observationQueue.logic.globalPauseUntilMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(1L)
            uploadBannerText.text = "Rate-limit cooldown active (${remainingSec}s remaining) to respect backend limits."
            return
        }
        scheduleQueueDrain(0L)
    }

    private fun scheduleQueueDrain(delayMs: Long) {
        cancelScheduledDrain()
        val task = Runnable { drainNextQueuedObservation() }
        drainRunnable = task
        handler.postDelayed(task, delayMs)
    }

    private fun cancelScheduledDrain() {
        drainRunnable?.let { handler.removeCallbacks(it) }
        drainRunnable = null
    }

    private fun drainNextQueuedObservation() {
        if (isUploading || !isUiAlive() || !isActivityStarted) return
        val nowMs = System.currentTimeMillis()
        val nextItem = observationQueue.logic.peekNextReady(nowMs)
        if (nextItem == null) {
            val wakeDelay = observationQueue.logic.nextWakeDelayMs(nowMs)
            if (wakeDelay != null) {
                scheduleQueueDrain(wakeDelay)
            }
            updateQueueStats()
            return
        }

        val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
        if (validation !is UrlValidationResult.Valid) {
            uploadBannerText.text = (validation as UrlValidationResult.Invalid).reason
            return
        }

        isUploading = true
        retryQueueButton.isEnabled = false
        uploadBannerText.text = "Uploading observation (${nextItem.payload.ssid ?: "Hidden Network"})..."

        apiClient.uploadObservation(validation.normalizedUrl, nextItem.payload) { result ->
            isUploading = false
            val completedMs = System.currentTimeMillis()

            val bannerMessage: String
            var markOffline = false
            var markConnected = false

            when (result) {
                is UploadAttemptResult.Success -> {
                    observationQueue.logic.markSuccess(nextItem.id, completedMs)
                    val totalUploaded = observationQueue.incrementUploadedCount()
                    observationQueue.persist()
                    markConnected = true
                    bannerMessage = "Uploaded observation #$totalUploaded (${result.message})"
                }
                is UploadAttemptResult.ClientRejected -> {
                    observationQueue.logic.markClientRejected(nextItem.id, completedMs)
                    observationQueue.persist()
                    bannerMessage = "Dropped invalid observation (HTTP ${result.statusCode}): ${result.message}"
                }
                is UploadAttemptResult.RateLimited -> {
                    val pauseMs = observationQueue.logic.markRateLimited(result.retryAfterSeconds, completedMs)
                    observationQueue.persist()
                    bannerMessage = "Backend rate limit (429). Paused ${pauseMs / 1000L}s before retry."
                }
                is UploadAttemptResult.TransientFailure -> {
                    val kept = observationQueue.logic.markTransientFailure(nextItem.id, completedMs)
                    observationQueue.persist()
                    if (result.statusCode == null) {
                        markOffline = true
                    }
                    bannerMessage = if (kept) {
                        "Upload failed (${result.message}); kept in queue for backoff retry."
                    } else {
                        "Dropped observation after max retries (${result.message})."
                    }
                }
            }

            if (!isUiAlive()) return@uploadObservation

            retryQueueButton.isEnabled = true
            if (markConnected) {
                setBackendPill("CONNECTED", emeraldText, emeraldBg)
            } else if (markOffline) {
                setBackendPill("OFFLINE", crimsonText, crimsonBg)
            }
            uploadBannerText.text = bannerMessage
            updateQueueStats()

            if (isActivityStarted) {
                val wakeDelay = observationQueue.logic.nextWakeDelayMs(completedMs)
                if (wakeDelay != null) {
                    scheduleQueueDrain(wakeDelay)
                }
            }
        }
    }

    private fun updateQueueStats() {
        val pending = observationQueue.logic.size()
        val uploaded = observationQueue.getUploadedCount()
        queueStatusText.text = "Pending Queue: $pending / ${ObservationQueueLogic.DEFAULT_MAX_QUEUE_SIZE}  •  Uploaded: $uploaded"
        retryQueueButton.isEnabled = !isUploading && pending > 0
    }

    private fun updateActiveWifiCard(details: WifiDetails) {
        val monitorTag = if (wifiMonitor.isMonitoring) "Monitor: ACTIVE" else "Monitor: Stopped"
        if (!details.connected) {
            activeWifiStatusText.text = "Not connected to Wi-Fi  •  $monitorTag"
            activeWifiDetailText.text = "Connect to a Wi-Fi network or run Nearby Scan to capture observations."
            return
        }

        activeWifiStatusText.text = "${details.displaySsid}  •  $monitorTag"
        val rssiStr = details.signalDbm?.let { "$it dBm" } ?: "signal n/a"
        val freqStr = details.frequencyMhz?.let { "$it MHz" } ?: "freq n/a"
        val speedStr = details.linkSpeedMbps?.let { "$it Mbps" } ?: "speed n/a"
        val netStr = if (details.internetAvailable) "Internet validated" else "No validated internet"
        activeWifiDetailText.text = "$rssiStr  •  ${details.band} ($freqStr)  •  $speedStr  •  $netStr"
    }

    private fun renderNetworks(networks: List<WifiNetwork>) {
        networksHeaderText.text = "DETECTED NETWORKS (${networks.size})"
        networkContainer.removeAllViews()

        if (networks.isEmpty()) {
            val emptyCard = createCard()
            emptyCard.addView(
                TextView(this).apply {
                    text = "No networks detected yet. Tap SCAN & UPLOAD OBSERVATIONS to scan."
                    textSize = 13f
                    setTextColor(mutedSlate)
                }
            )
            networkContainer.addView(emptyCard, cardMarginParams())
            return
        }

        for (network in networks) {
            addNetworkCard(network)
        }
    }

    private fun addNetworkCard(network: WifiNetwork) {
        val card = createCard()

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val name = TextView(this).apply {
            text = network.displaySsid
            textSize = 15f
            setTextColor(darkSlate)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val signalBadge = TextView(this).apply {
            text = "${network.signalDbm} dBm"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(deepGold)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = pillDrawable(softGoldBg, champagneGold)
        }

        topRow.addView(name)
        topRow.addView(signalBadge)

        val bssidLabel = network.bssid ?: "BSSID unavailable"
        val details = TextView(this).apply {
            text = "${network.band}  •  ${network.frequencyMhz} MHz  •  ${network.security}\n" +
                "Quality: ${network.signalQuality}/4  •  $bssidLabel"
            textSize = 12f
            setTextColor(bodySlate)
            setPadding(0, dp(6), 0, 0)
        }

        card.addView(topRow)
        card.addView(details)
        networkContainer.addView(card, cardMarginParams())
    }
}
