package com.wifivision.app

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var permissionManager: WifiPermissionManager
    private lateinit var scanner: WifiScanner
    private lateinit var wifiMonitor: WifiMonitor
    private lateinit var locationProvider: LocationProvider
    private lateinit var observationQueue: ObservationQueue
    private lateinit var sessionController: ObservationSessionController
    private lateinit var credentialStore: DeviceCredentialStore
    private lateinit var apiClient: BackendApiClient

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var backendPill: TextView
    private lateinit var statusText: TextView
    private lateinit var permissionHintText: TextView
    private lateinit var privacyModeText: TextView
    private lateinit var scanButton: Button
    private lateinit var monitorButton: Button
    private lateinit var uploadButton: Button

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

    private lateinit var pairingBadgeText: TextView
    private lateinit var pairingDetailText: TextView
    private lateinit var pairingCodeInput: EditText
    private lateinit var deviceNameInput: EditText
    private lateinit var pairDeviceButton: Button
    private lateinit var rotateKeyButton: Button
    private lateinit var unpairDeviceButton: Button

    private lateinit var networksHeaderText: TextView
    private lateinit var networkContainer: LinearLayout

    private var currentBaseUrl: String = ""
    private var isUploading = false
    private var isPairingBusy = false
    private var isActivityStarted = false
    private var scanGeneration = 0L
    private var gpsGeneration = 0L
    private var lastMonitorAutoScanMs = 0L
    private var pendingAction: PendingPermissionAction = PendingPermissionAction.NONE
    private var drainRunnable: Runnable? = null
    private var uploadConfirmDialog: AlertDialog? = null

    private enum class PendingPermissionAction {
        NONE,
        SCAN_ONLY,
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
        private const val MONITOR_PERIODIC_SCAN_MIN_INTERVAL_MS = 60_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = WifiPermissionManager(this)
        scanner = WifiScanner(this, permissionManager)
        wifiMonitor = WifiMonitor(this, permissionManager)
        locationProvider = LocationProvider(this, permissionManager)
        observationQueue = ObservationQueue(this)
        sessionController = ObservationSessionController(observationQueue.logic)
        credentialStore = DeviceCredentialStore(this)
        apiClient = BackendApiClient()

        currentBaseUrl = loadSavedBaseUrl()
        buildDashboard()

        val startup = sessionController.onAppStartup()
        refreshStaticState()
        if (startup.pendingQueueCount > 0) {
            uploadBannerText.text =
                "${startup.pendingQueueCount} pending observation(s) saved locally. Tap UPLOAD OBSERVATIONS when ready."
        }
        checkBackendHealth()
    }

    override fun onStart() {
        super.onStart()
        isActivityStarted = true
        refreshStaticState()
    }

    override fun onStop() {
        isActivityStarted = false
        val hadPendingScan = sessionController.isScanInProgress
        dismissUploadDialog()
        cancelActiveScanAndGps(
            cancelledMessage = if (hadPendingScan) "Scan cancelled while app moved to background" else null
        )
        cancelScheduledDrain()
        if (wifiMonitor.isMonitoring) {
            wifiMonitor.stopMonitoring()
        }
        sessionController.onStopCleanup()
        updateMonitorButtonState()
        super.onStop()
    }

    override fun onDestroy() {
        isActivityStarted = false
        dismissUploadDialog()
        cancelActiveScanAndGps(cancelledMessage = null)
        cancelScheduledDrain()
        wifiMonitor.stopMonitoring()
        sessionController.onStopCleanup()
        apiClient.shutdown()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun isUiAlive(): Boolean = !isFinishing && !isDestroyed

    private fun dismissUploadDialog() {
        uploadConfirmDialog?.let { dialog ->
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
        uploadConfirmDialog = null
    }

    private fun cancelActiveScanAndGps(cancelledMessage: String?) {
        scanGeneration++
        gpsGeneration++
        scanner.cancelScan()
        locationProvider.cancel()

        val hadPendingScan = sessionController.isScanInProgress
        sessionController.isScanInProgress = false
        scanButton.isEnabled = true
        refreshGpsButton.isEnabled = true

        if (hadPendingScan && cancelledMessage != null && isUiAlive()) {
            statusText.text = cancelledMessage
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun formatTimeOfDay(epochMs: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(epochMs))
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

        // 1. Local Scan, Monitoring & Explicit Upload Card
        val controlCard = createCard()
        statusText = TextView(this).apply {
            text = "Ready for local Wi-Fi & GPS scan"
            textSize = 15f
            setTextColor(darkSlate)
            typeface = Typeface.DEFAULT_BOLD
        }
        permissionHintText = TextView(this).apply {
            textSize = 12f
            setTextColor(mutedSlate)
            setPadding(0, dp(4), 0, dp(2))
        }
        privacyModeText = TextView(this).apply {
            text = "Local-first mode • Auto-upload OFF • Confirmation required"
            textSize = 11f
            setTextColor(deepGold)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        }

        scanButton = Button(this).apply {
            text = "SCAN ONLY"
            setTextColor(pureWhite)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedDrawable(champagneGold, champagneGold, 10)
            isAllCaps = true
            setOnClickListener { handleScanOnlyClick() }
        }

        val secondaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        monitorButton = Button(this).apply {
            text = "START MONITOR"
            setTextColor(darkSlate)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedDrawable(softGoldBg, champagneGold, 10)
            setOnClickListener { toggleMonitoring() }
        }

        uploadButton = Button(this).apply {
            text = "UPLOAD OBSERVATIONS"
            setTextColor(darkSlate)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedDrawable(warmIvory, sandBorder, 10)
            setOnClickListener { handleUploadObservationsClick() }
        }

        secondaryRow.addView(
            monitorButton,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) }
        )
        secondaryRow.addView(
            uploadButton,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        controlCard.addView(statusText)
        controlCard.addView(permissionHintText)
        controlCard.addView(privacyModeText)
        controlCard.addView(
            scanButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply {
                bottomMargin = dp(8)
            }
        )
        controlCard.addView(secondaryRow)
        content.addView(controlCard, cardMarginParams())

        // 2. Active Wi-Fi Connection Card (Local WifiMonitor)
        val activeCard = createCard()
        activeCard.addView(sectionLabel("ACTIVE WI-FI CONNECTION (LOCAL)"))
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
        gpsHeader.addView(refreshGpsButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)))

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
        backendCard.addView(sectionLabel("BACKEND & EXPLICIT UPLOAD QUEUE"))

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
            LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(8) }
        )
        urlButtonsRow.addView(
            testHealthButton,
            LinearLayout.LayoutParams(0, dp(38), 1f)
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
            text = "No pending upload."
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

        // 4b. Device Pairing Card (Scoped Credential + Android Keystore)
        val pairingCard = createCard()
        val pairingHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pairingTitle = sectionLabel("DEVICE PAIRING (PRIVATE ACCOUNT)").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        pairingBadgeText = TextView(this).apply {
            text = "UNPAIRED"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setTextColor(amberText)
            background = pillDrawable(amberBg, sandBorder)
        }
        pairingHeader.addView(pairingTitle)
        pairingHeader.addView(pairingBadgeText)

        pairingDetailText = TextView(this).apply {
            textSize = 12f
            setTextColor(bodySlate)
            setPadding(0, dp(6), 0, dp(6))
            text = "Generate an 8-character pairing code in web Settings -> Paired Android Devices."
        }

        pairingCodeInput = EditText(this).apply {
            hint = "8-character pairing code"
            textSize = 13f
            setTextColor(darkSlate)
            setHintTextColor(mutedSlate)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            isSingleLine = true
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedDrawable(warmIvory, sandBorder, 8)
        }

        deviceNameInput = EditText(this).apply {
            hint = "Device name (e.g. ${android.os.Build.MODEL})"
            textSize = 13f
            setTextColor(darkSlate)
            setHintTextColor(mutedSlate)
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedDrawable(warmIvory, sandBorder, 8)
        }

        val pairingButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        pairDeviceButton = Button(this).apply {
            text = "PAIR DEVICE"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(pureWhite)
            background = roundedDrawable(champagneGold, champagneGold, 8)
            setOnClickListener { handlePairDeviceClick() }
        }
        rotateKeyButton = Button(this).apply {
            text = "ROTATE"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkSlate)
            background = roundedDrawable(softGoldBg, champagneGold, 8)
            setOnClickListener { handleRotateCredentialClick() }
        }
        unpairDeviceButton = Button(this).apply {
            text = "UNPAIR"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(crimsonText)
            background = roundedDrawable(warmIvory, sandBorder, 8)
            setOnClickListener { handleUnpairClick() }
        }

        pairingButtonsRow.addView(
            pairDeviceButton,
            LinearLayout.LayoutParams(0, dp(38), 1.4f).apply { marginEnd = dp(6) }
        )
        pairingButtonsRow.addView(
            rotateKeyButton,
            LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) }
        )
        pairingButtonsRow.addView(
            unpairDeviceButton,
            LinearLayout.LayoutParams(0, dp(38), 0.9f)
        )

        pairingCard.addView(pairingHeader)
        pairingCard.addView(pairingDetailText)
        pairingCard.addView(
            pairingCodeInput,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
        )
        pairingCard.addView(
            deviceNameInput,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        )
        pairingCard.addView(pairingButtonsRow)
        content.addView(pairingCard, cardMarginParams())

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
        val currentWifi = wifiMonitor.getCurrentWifiInfo()
        sessionController.onMonitorUpdated(currentWifi, System.currentTimeMillis())
        updateActiveWifiCard(currentWifi)
        updateQueueStats()
        updateMonitorButtonState()
        updatePairingCard()
    }

    private fun updatePairingCard() {
        val activeCred = credentialStore.getActiveCredential()
        val storedCred = credentialStore.loadCredential()
        when {
            activeCred != null -> {
                pairingBadgeText.text = "PAIRED"
                pairingBadgeText.setTextColor(emeraldText)
                pairingBadgeText.background = pillDrawable(emeraldBg, sandBorder)
                pairingDetailText.text =
                    "Paired as \"${activeCred.deviceName}\" • Keystore AES-GCM active (scoped upload only)"
                rotateKeyButton.isEnabled = !isPairingBusy
                unpairDeviceButton.isEnabled = !isPairingBusy
            }
            storedCred != null -> {
                pairingBadgeText.text = "EXPIRED"
                pairingBadgeText.setTextColor(crimsonText)
                pairingBadgeText.background = pillDrawable(crimsonBg, sandBorder)
                pairingDetailText.text =
                    "Stored device credential expired. Enter a fresh pairing code from web Settings."
                rotateKeyButton.isEnabled = false
                unpairDeviceButton.isEnabled = !isPairingBusy
            }
            else -> {
                pairingBadgeText.text = "UNPAIRED"
                pairingBadgeText.setTextColor(amberText)
                pairingBadgeText.background = pillDrawable(amberBg, sandBorder)
                pairingDetailText.text =
                    "Generate an 8-character pairing code in web Settings -> Paired Android Devices."
                rotateKeyButton.isEnabled = false
                unpairDeviceButton.isEnabled = false
            }
        }
        pairDeviceButton.isEnabled = !isPairingBusy
    }

    private fun handlePairDeviceClick() {
        if (isPairingBusy) return
        val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
        if (validation !is UrlValidationResult.Valid) {
            uploadBannerText.text = (validation as UrlValidationResult.Invalid).reason
            return
        }

        val codeRaw = pairingCodeInput.text?.toString().orEmpty()
        val nameRaw = deviceNameInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: android.os.Build.MODEL

        isPairingBusy = true
        updatePairingCard()
        pairingDetailText.text = "Pairing device securely..."

        apiClient.pairDevice(validation.normalizedUrl, codeRaw, nameRaw) { result ->
            isPairingBusy = false
            if (!isUiAlive()) return@pairDevice

            when (result) {
                is PairingAttemptResult.Success -> {
                    val saved = credentialStore.saveCredential(result.credential)
                    pairingCodeInput.setText("")
                    if (saved) {
                        uploadBannerText.text =
                            "Device paired (${result.credential.deviceName}) and protected via Android Keystore."
                    } else {
                        uploadBannerText.text =
                            "Device paired, but local Keystore encryption failed."
                    }
                }
                is PairingAttemptResult.Rejected -> {
                    uploadBannerText.text = "Pairing rejected: ${result.message}"
                }
                is PairingAttemptResult.NetworkError -> {
                    uploadBannerText.text = "Pairing failed: ${result.message}"
                }
            }
            updatePairingCard()
        }
    }

    private fun handleRotateCredentialClick() {
        if (isPairingBusy) return
        val activeCred = credentialStore.getActiveCredential() ?: run {
            uploadBannerText.text = "Pair this device first before rotating credentials."
            updatePairingCard()
            return
        }
        val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
        if (validation !is UrlValidationResult.Valid) {
            uploadBannerText.text = (validation as UrlValidationResult.Invalid).reason
            return
        }

        isPairingBusy = true
        updatePairingCard()
        apiClient.rotateDeviceCredential(validation.normalizedUrl, activeCred.deviceToken) { result ->
            isPairingBusy = false
            if (!isUiAlive()) return@rotateDeviceCredential

            when (result) {
                is PairingAttemptResult.Success -> {
                    credentialStore.saveCredential(result.credential)
                    uploadBannerText.text = "Device credential rotated and encrypted in Android Keystore."
                }
                is PairingAttemptResult.Rejected -> {
                    if (result.statusCode == 401) {
                        credentialStore.clearCredential()
                    }
                    uploadBannerText.text = "Rotation rejected: ${result.message}"
                }
                is PairingAttemptResult.NetworkError -> {
                    uploadBannerText.text = "Rotation failed: ${result.message}"
                }
            }
            updatePairingCard()
        }
    }

    private fun handleUnpairClick() {
        if (isPairingBusy) return
        credentialStore.clearCredential()
        uploadBannerText.text = "Local device credential cleared. Generate a new code in Settings to re-pair."
        updatePairingCard()
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
        val currentWifi = wifiMonitor.getCurrentWifiInfo()
        sessionController.onMonitorUpdated(currentWifi, System.currentTimeMillis())
        updateActiveWifiCard(currentWifi)

        val action = pendingAction
        pendingAction = PendingPermissionAction.NONE

        if (permissionManager.hasWifiScanPermissions()) {
            when (action) {
                PendingPermissionAction.SCAN_ONLY -> executeScanOnly()
                PendingPermissionAction.GPS_ONLY -> fetchGpsMeasurement(onComplete = {})
                PendingPermissionAction.START_MONITOR -> startWifiMonitoring()
                PendingPermissionAction.NONE -> Unit
            }
        } else {
            val deniedMessage = permissionManager.getPermissionStatus()
            statusText.text = deniedMessage
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_DENIED }) {
                uploadBannerText.text =
                    "Permission denied. Grant location & nearby Wi-Fi permissions to collect local measurements."
            }
        }
    }

    private fun handleScanOnlyClick() {
        if (sessionController.isScanInProgress) return
        if (!ensureRuntimePermissions(PendingPermissionAction.SCAN_ONLY)) return

        if (!permissionManager.isWifiEnabled()) {
            statusText.text = "Please enable Wi-Fi before scanning"
            return
        }
        if (!permissionManager.isLocationEnabled()) {
            statusText.text = "Please enable device location services"
            return
        }

        executeScanOnly()
    }

    private fun handleRefreshGpsClick() {
        if (sessionController.isScanInProgress || !refreshGpsButton.isEnabled) return
        if (!ensureRuntimePermissions(PendingPermissionAction.GPS_ONLY)) return
        fetchGpsMeasurement(onComplete = {})
    }

    private fun toggleMonitoring() {
        if (wifiMonitor.isMonitoring) {
            wifiMonitor.stopMonitoring()
            sessionController.isMonitoringActive = false
            updateMonitorButtonState()
            updateActiveWifiCard(wifiMonitor.getCurrentWifiInfo())
            statusText.text = "Local Wi-Fi monitoring stopped"
            return
        }

        if (!ensureRuntimePermissions(PendingPermissionAction.START_MONITOR)) return
        startWifiMonitoring()
    }

    private fun startWifiMonitoring() {
        val started = wifiMonitor.startMonitoring { details ->
            if (!isUiAlive() || !wifiMonitor.isMonitoring) return@startMonitoring
            val nowMs = System.currentTimeMillis()
            // Local-only update: never enqueues or uploads observations
            sessionController.onMonitorUpdated(details, nowMs)
            updateActiveWifiCard(details)

            // Periodically refresh local scan results while respecting Android's throttling window
            if (
                isActivityStarted &&
                permissionManager.canScanWifi() &&
                !sessionController.isScanInProgress &&
                (nowMs - lastMonitorAutoScanMs) >= MONITOR_PERIODIC_SCAN_MIN_INTERVAL_MS
            ) {
                lastMonitorAutoScanMs = nowMs
                runLocalMonitorScan(nowMs)
            }
        }
        sessionController.onMonitoringStarted(started)
        updateMonitorButtonState()
        statusText.text = if (started) {
            "Local Wi-Fi monitoring active (no auto-upload)"
        } else {
            "Monitoring is already active"
        }
    }

    private fun runLocalMonitorScan(triggeredAtMs: Long) {
        val token = ++scanGeneration
        sessionController.isScanInProgress = true
        scanner.requestScan { outcome ->
            if (!isUiAlive() || token != scanGeneration) return@requestScan
            val completedMs = System.currentTimeMillis().coerceAtLeast(triggeredAtMs)
            val activeWifi = wifiMonitor.getCurrentWifiInfo()
            sessionController.onScanCompleted(
                networks = outcome.networks,
                activeWifi = activeWifi,
                location = sessionController.latestLocation,
                scannedAtMs = completedMs
            )
            renderNetworks(outcome.networks)
            updateActiveWifiCard(activeWifi)
            updateUploadButtonState()
            statusText.text = "${outcome.message} (${formatTimeOfDay(completedMs)} • local only)"
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
                    val snapshot = result.snapshot
                    val loc = snapshot.location
                    sessionController.onGpsUpdated(
                        location = loc,
                        accuracyMeters = loc.accuracyMeters,
                        capturedAtMs = snapshot.capturedAtMs
                    )
                    val accStr = loc.accuracyMeters?.let { String.format(Locale.US, "±%.1f m", it) } ?: "accuracy n/a"
                    val mode = if (snapshot.fromLastKnownFallback) "cached" else "live"
                    val timeStr = formatTimeOfDay(snapshot.capturedAtMs)
                    gpsStatusText.text = String.format(
                        Locale.US,
                        "%.6f, %.6f  (%s • %s • %s • %s)",
                        loc.latitude,
                        loc.longitude,
                        accStr,
                        snapshot.provider,
                        mode,
                        timeStr
                    )
                    onComplete(loc)
                }
                is LocationFixResult.Unavailable -> {
                    sessionController.onGpsUpdated(
                        location = null,
                        accuracyMeters = null,
                        capturedAtMs = System.currentTimeMillis()
                    )
                    gpsStatusText.text = "Location unavailable: ${result.reason}"
                    onComplete(null)
                }
            }
        }
    }

    private fun executeScanOnly() {
        if (sessionController.isScanInProgress) return
        val token = ++scanGeneration
        sessionController.isScanInProgress = true
        scanButton.isEnabled = false
        statusText.text = "Acquiring GPS location & scanning Wi-Fi locally..."
        permissionHintText.text = permissionManager.getPermissionStatus()

        fetchGpsMeasurement { measurementLocation ->
            if (!isUiAlive() || !isActivityStarted || token != scanGeneration) {
                return@fetchGpsMeasurement
            }

            scanner.requestScan { outcome ->
                if (!isUiAlive() || token != scanGeneration) return@requestScan
                scanButton.isEnabled = true

                val scannedAtMs = System.currentTimeMillis()
                lastMonitorAutoScanMs = scannedAtMs
                val activeWifi = wifiMonitor.getCurrentWifiInfo()
                sessionController.onScanCompleted(
                    networks = outcome.networks,
                    activeWifi = activeWifi,
                    location = measurementLocation,
                    scannedAtMs = scannedAtMs
                )

                renderNetworks(outcome.networks)
                updateActiveWifiCard(activeWifi)
                updateQueueStats()

                val locationSuffix = if (measurementLocation == null) {
                    " • GPS unavailable"
                } else {
                    ""
                }
                statusText.text = "${outcome.message} (${formatTimeOfDay(scannedAtMs)}$locationSuffix • local only)"
            }
        }
    }

    private fun handleUploadObservationsClick() {
        if (isUploading) {
            uploadBannerText.text = "Upload already in progress..."
            return
        }

        val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
        if (validation !is UrlValidationResult.Valid) {
            setBackendPill("URL INVALID", crimsonText, crimsonBg)
            uploadBannerText.text = (validation as UrlValidationResult.Invalid).reason
            return
        }

        if (credentialStore.getActiveCredential() == null) {
            uploadBannerText.text =
                "Device pairing required before uploading. Generate a pairing code in web Settings."
            updatePairingCard()
            return
        }

        val nowMs = System.currentTimeMillis()
        if (observationQueue.logic.globalPauseUntilMs > nowMs) {
            val remainingSec = ((observationQueue.logic.globalPauseUntilMs - nowMs) / 1000L).coerceAtLeast(1L)
            uploadBannerText.text = "Rate-limit cooldown active (${remainingSec}s remaining) to respect backend limits."
            return
        }

        val preview = sessionController.prepareUploadConfirmation(nowMs)
        if (preview == null) {
            uploadBannerText.text = "No scanned or queued observations ready. Tap SCAN ONLY first."
            return
        }

        showUploadConfirmationDialog(validation.normalizedUrl, preview)
    }

    private fun showUploadConfirmationDialog(
        targetBaseUrl: String,
        preview: UploadConfirmationPreview
    ) {
        dismissUploadDialog()

        val locationSummary = if (preview.hasLocation) {
            val acc = preview.locationAccuracyMeters?.let { String.format(Locale.US, "±%.1f m", it) } ?: "accuracy n/a"
            "Device GPS measurement attached ($acc)"
        } else {
            "GPS unavailable (will upload without coordinates)"
        }

        val message = buildString {
            appendLine("Target: $targetBaseUrl/api/observations")
            appendLine("New local observations: ${preview.readyLocalCount}")
            appendLine("Existing pending queue: ${preview.existingPendingQueueCount}")
            if (preview.invalidSkippedCount > 0) {
                appendLine("Invalid filtered out: ${preview.invalidSkippedCount}")
            }
            appendLine(locationSummary)
            appendLine()
            appendLine("Preview:")
            preview.previewLines.forEach { line ->
                appendLine("• $line")
            }
            val totalPreviewPool = if (preview.readyLocalCount > 0) {
                preview.readyLocalCount
            } else {
                preview.existingPendingQueueCount
            }
            if (totalPreviewPool > preview.previewLines.size) {
                appendLine("• ...and ${totalPreviewPool - preview.previewLines.size} more")
            }
        }.trim()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Confirm Observation Upload")
            .setMessage(message)
            .setPositiveButton("UPLOAD NOW") { _, _ ->
                uploadConfirmDialog = null
                val confirmMs = System.currentTimeMillis()
                val outcome = sessionController.confirmAndEnqueueUpload(true, confirmMs)
                outcome.enqueueSummary?.let { summary ->
                    observationQueue.persist()
                    uploadBannerText.text = buildEnqueueSummaryMessage(summary)
                }
                updateQueueStats()
                if (outcome.shouldDrainQueue && isActivityStarted) {
                    scheduleQueueDrain(0L)
                }
            }
            .setNegativeButton("CANCEL") { _, _ ->
                uploadConfirmDialog = null
                sessionController.confirmAndEnqueueUpload(false, System.currentTimeMillis())
                uploadBannerText.text = "Upload cancelled. Observations kept local only."
            }
            .setOnCancelListener {
                uploadConfirmDialog = null
                sessionController.confirmAndEnqueueUpload(false, System.currentTimeMillis())
            }
            .create()

        uploadConfirmDialog = dialog
        dialog.show()
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
        uploadBannerText.text = "Checking backend health..."
        apiClient.checkHealth(currentBaseUrl) { result ->
            if (!isUiAlive()) return@checkHealth
            val uiUpdate = sessionController.onHealthCheckCompleted(result)
            when {
                uiUpdate.isHealthy -> setBackendPill(uiUpdate.pillLabel, emeraldText, emeraldBg)
                uiUpdate.isReachable -> setBackendPill(uiUpdate.pillLabel, amberText, amberBg)
                else -> setBackendPill(uiUpdate.pillLabel, crimsonText, crimsonBg)
            }
            uploadBannerText.text = uiUpdate.bannerText
        }
    }

    private fun setBackendPill(label: String, textColor: Int, bgColor: Int) {
        backendPill.text = label
        backendPill.setTextColor(textColor)
        backendPill.background = pillDrawable(bgColor, sandBorder)
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

        val activeCred = credentialStore.getActiveCredential()
        if (activeCred == null) {
            uploadBannerText.text =
                "Device credential missing or expired. Re-pair device before uploading."
            updatePairingCard()
            updateQueueStats()
            return
        }

        isUploading = true
        updateUploadButtonState()
        uploadBannerText.text = "Uploading observation (${nextItem.payload.ssid ?: "Hidden Network"})..."

        apiClient.uploadObservation(validation.normalizedUrl, nextItem.payload, activeCred.deviceToken) { result ->
            isUploading = false
            val completedMs = System.currentTimeMillis()

            val bannerMessage: String
            var markOffline = false
            var markConnected = false
            var stopDrainForRePair = false

            when (result) {
                is UploadAttemptResult.Success -> {
                    observationQueue.logic.markSuccess(nextItem.id, completedMs)
                    val totalUploaded = observationQueue.incrementUploadedCount()
                    observationQueue.persist()
                    markConnected = true
                    bannerMessage = "Uploaded observation #$totalUploaded (${result.message})"
                }
                is UploadAttemptResult.Unauthorized -> {
                    credentialStore.clearCredential()
                    stopDrainForRePair = true
                    bannerMessage = "Upload unauthorized (${result.message}). Queue preserved; please re-pair device."
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

            if (markConnected) {
                setBackendPill("CONNECTED", emeraldText, emeraldBg)
            } else if (markOffline) {
                setBackendPill("OFFLINE", crimsonText, crimsonBg)
            }
            if (stopDrainForRePair) {
                updatePairingCard()
            }
            uploadBannerText.text = bannerMessage
            updateQueueStats()

            if (isActivityStarted && !stopDrainForRePair) {
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
        queueStatusText.text =
            "Pending Queue: $pending / ${ObservationQueueLogic.DEFAULT_MAX_QUEUE_SIZE}  •  Uploaded: $uploaded"
        updateUploadButtonState()
    }

    private fun updateUploadButtonState() {
        val hasLocalOrPending =
            observationQueue.logic.size() > 0 ||
                sessionController.lastScanNetworks.isNotEmpty() ||
                sessionController.latestWifiDetails?.connected == true
        uploadButton.isEnabled = !isUploading && hasLocalOrPending
    }

    private fun updateActiveWifiCard(details: WifiDetails) {
        val monitorTag = if (wifiMonitor.isMonitoring) "Monitor: ACTIVE (local)" else "Monitor: Stopped"
        if (!details.connected) {
            activeWifiStatusText.text = "Not connected to Wi-Fi  •  $monitorTag"
            activeWifiDetailText.text = "Connect to Wi-Fi or tap SCAN ONLY to inspect nearby networks locally."
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
                    text = "No networks scanned yet. Tap SCAN ONLY to inspect nearby Wi-Fi networks locally."
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
