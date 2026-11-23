package com.wifivision.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.util.Locale

class SettingsScreenController(
    private val context: Context,
    private val preferences: AppPreferences,
    private val onRefreshProfileClick: () -> Unit,
    private val onLogoutClick: () -> Unit,
    private val onLogoutAllClick: () -> Unit,
    private val onReviewUploadClick: () -> Unit,
    private val onQuickPairSelfClick: (deviceName: String?) -> Unit,
    private val onPairWithCodeClick: (code: String, deviceName: String?) -> Unit,
    private val onRotateKeyClick: () -> Unit,
    private val onUnpairClick: () -> Unit,
    private val onRefreshDevicesClick: () -> Unit,
    private val onGeneratePairingCodeClick: (platform: String) -> Unit,
    private val onRevokeDeviceClick: (deviceId: String) -> Unit,
    private val onRunMonitoringClick: () -> Unit,
    private val onMonitoringPrefChanged: () -> Unit,
    private val onApplyBaseUrlClick: (rawUrl: String) -> Unit,
    private val onPreferencesChanged: (BandFilter, Int) -> Unit,
    private val onRequestPermissionsClick: () -> Unit,
    private val onOpenAppSettingsClick: () -> Unit,
    private val onCheckForUpdatesClick: () -> Unit
) {

    val rootView: ScrollView
    private val contentCol: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var pairingCountdownRunnable: Runnable? = null
    private var activePairingExpiresAtMs: Long = 0L

    // Account & Session views
    private val acctNameText: TextView
    private val acctEmailText: TextView
    private val acctIdText: TextView
    private val acctVerifiedPill: TextView
    private val sessionStateText: TextView
    private val refreshProfileBtn: WiFiActionButton
    private val logoutBtn: WiFiActionButton
    private val logoutAllBtn: WiFiActionButton

    // Sync Queue views
    private val syncPendingValue: TextView
    private val syncUploadedValue: TextView
    private val syncBannerBox: LinearLayout
    private val syncBannerText: TextView
    val syncUploadBtn: WiFiActionButton

    // This Device Pairing views
    private val thisDeviceBadgePill: TextView
    private val thisDeviceDetailText: TextView
    private val quickPairBtn: WiFiActionButton
    private val pairingCodeInput: EditText
    private val deviceNameInput: EditText
    private val pairBtn: WiFiActionButton
    private val rotateBtn: WiFiActionButton
    private val unpairBtn: WiFiActionButton

    // Cloud Paired Devices views
    private var selectedCodePlatform: String = "android"
    private val platAndroidBtn: WiFiActionButton
    private val platWindowsBtn: WiFiActionButton
    private val genCodeBtn: WiFiActionButton
    private val refreshDevicesBtn: WiFiActionButton
    private val generatedCodeCard: LinearLayout
    private val generatedCodeValue: TextView
    private val generatedCodeExpiry: TextView
    private val devicesFeedbackText: TextView
    private val devicesListContainer: LinearLayout

    // System Monitoring views
    private val runHealthBtn: WiFiActionButton
    private val autoRefreshCheck: CheckBox
    private val intervalRow: LinearLayout
    private val monBackendValue: TextView
    private val monDbValue: TextView
    private val monAvailValue: TextView
    private val monEndpointList: LinearLayout
    private val monRecentFeed: TextView

    // URL & Preferences views
    val apiUrlInput: EditText
    private val applyUrlBtn: WiFiActionButton
    private val endpointHealthPill: TextView
    private val endpointDetailText: TextView
    private val prefBandRow: LinearLayout
    private val prefLimitRow: LinearLayout

    // Permissions views
    private val permStatusText: TextView
    private val reqPermBtn: WiFiActionButton

    // App Updates views
    private val updateStatusPill: TextView
    private val updateInstalledValue: TextView
    private val updateLatestValue: TextView
    private val updateLastCheckText: TextView
    private val updateDetailText: TextView
    private val checkUpdatesBtn: WiFiActionButton

    init {
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(24))
        }
        rootView = ScrollView(context).apply {
            addView(contentCol)
        }

        // 1. Account & Session Card
        val acctCard = createCard(context, paddingDp = 16)
        val acctHdr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        acctHdr.addView(createSectionTitle(context, "ACCOUNT & SESSION").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        acctVerifiedPill = createPill(context, "Verified", WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
        acctHdr.addView(acctVerifiedPill)

        acctNameText = TextView(context).apply {
            text = "—"
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(8), 0, context.dp(2))
        }
        acctEmailText = TextView(context).apply {
            text = "—"
            textSize = 13f
            setTextColor(WiFiVisionPalette.bodySlate)
        }
        acctIdText = TextView(context).apply {
            text = "Account ID: —"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(WiFiVisionPalette.mutedSlate)
            setPadding(0, context.dp(4), 0, context.dp(8))
        }
        sessionStateText = TextView(context).apply {
            text = "Session: Encrypted in Android Keystore AES-256-GCM"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.jade)
            setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
            background = roundedDrawable(context, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder, 10)
        }
        refreshProfileBtn = WiFiActionButton(context).apply {
            configure("Refresh Profile", textColor = WiFiVisionPalette.darkSlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            setOnClickListener { onRefreshProfileClick() }
        }
        logoutBtn = WiFiActionButton(context).apply {
            configure("Sign Out", textColor = WiFiVisionPalette.crimsonText, fillColor = WiFiVisionPalette.crimsonBg, strokeColor = WiFiVisionPalette.crimsonBorder, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            setOnClickListener { onLogoutClick() }
        }
        logoutAllBtn = WiFiActionButton(context).apply {
            configure("Sign Out All", textColor = WiFiVisionPalette.amberText, fillColor = WiFiVisionPalette.amberBg, strokeColor = WiFiVisionPalette.amberBorder, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            setOnClickListener { confirmLogoutAll() }
        }
        val acctBtnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(10), 0, 0)
            addView(refreshProfileBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f).apply { marginEnd = context.dp(6) })
            addView(logoutAllBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) })
            addView(logoutBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.95f))
        }
        acctCard.addView(acctHdr); acctCard.addView(acctNameText); acctCard.addView(acctEmailText); acctCard.addView(acctIdText); acctCard.addView(sessionStateText); acctCard.addView(acctBtnRow)

        // 2. Local Observation Queue & Sync Card
        val syncCard = createCard(context, paddingDp = 16)
        syncCard.addView(createSectionTitle(context, "OBSERVATION SYNC & QUEUE"))
        val queueRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(10), 0, context.dp(10))
        }
        val pendBox = createMiniStatBox("PENDING QUEUE")
        syncPendingValue = pendBox.second
        val uplBox = createMiniStatBox("UPLOADED TOTAL")
        syncUploadedValue = uplBox.second
        queueRow.addView(pendBox.first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) })
        queueRow.addView(uplBox.first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(6) })

        syncBannerBox = LinearLayout(context).apply {
            setPadding(context.dp(12), context.dp(9), context.dp(12), context.dp(9))
            background = roundedDrawable(context, WiFiVisionPalette.warmIvory, WiFiVisionPalette.hairline, 10)
        }
        syncBannerText = TextView(context).apply {
            text = "All scanned data stays local until you confirm UPLOAD NOW."
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.bodySlate)
        }
        syncBannerBox.addView(syncBannerText)

        syncUploadBtn = WiFiActionButton(context).apply {
            configure("Review & Upload Observations", iconRes = R.drawable.ic_upload_cloud, textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 12, textSizeSp = 13f)
            setOnClickListener { onReviewUploadClick() }
        }
        syncCard.addView(queueRow)
        syncCard.addView(syncBannerBox, marginParams(10))
        syncCard.addView(syncUploadBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 3. This Phone Device Upload Pairing Card (Scoped wvd_... Credential)
        val selfPairCard = createCard(context, paddingDp = 16)
        val selfPairHdr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        selfPairHdr.addView(createSectionTitle(context, "THIS PHONE UPLOAD CREDENTIAL").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        thisDeviceBadgePill = createPill(context, "UNPAIRED", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
        selfPairHdr.addView(thisDeviceBadgePill)

        thisDeviceDetailText = TextView(context).apply {
            textSize = 12f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, context.dp(8), 0, context.dp(10))
        }
        deviceNameInput = createStyledInput(context, "Device label (e.g. ${Build.MODEL})").apply {
            setText(Build.MODEL)
        }
        quickPairBtn = WiFiActionButton(context).apply {
            configure("Quick Pair This Phone (1-Tap)", iconRes = R.drawable.ic_shield_lock, textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 12, textSizeSp = 12.5f)
            setOnClickListener {
                onQuickPairSelfClick(deviceNameInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() })
            }
        }
        pairingCodeInput = createStyledInput(context, "Or enter 8-character pairing code", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        pairBtn = WiFiActionButton(context).apply {
            configure("Pair Code", textColor = WiFiVisionPalette.deepGold, fillColor = WiFiVisionPalette.softGoldBg, strokeColor = WiFiVisionPalette.goldBorder, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            setOnClickListener {
                onPairWithCodeClick(
                    pairingCodeInput.text?.toString().orEmpty(),
                    deviceNameInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
        }
        val codePairRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pairingCodeInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) })
            addView(pairBtn, LinearLayout.LayoutParams(context.dp(100), LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        rotateBtn = WiFiActionButton(context).apply {
            configure("Rotate Key", textColor = WiFiVisionPalette.emeraldDeep, fillColor = WiFiVisionPalette.emeraldTint, strokeColor = WiFiVisionPalette.emeraldBorder, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            setOnClickListener { onRotateKeyClick() }
        }
        unpairBtn = WiFiActionButton(context).apply {
            configure("Unpair Local", textColor = WiFiVisionPalette.crimsonText, fillColor = WiFiVisionPalette.crimsonBg, strokeColor = WiFiVisionPalette.crimsonBorder, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            setOnClickListener { onUnpairClick() }
        }
        val keyRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(8), 0, 0)
            addView(rotateBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) })
            addView(unpairBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(6) })
        }

        selfPairCard.addView(selfPairHdr)
        selfPairCard.addView(thisDeviceDetailText)
        selfPairCard.addView(createFieldLabel(context, "Device Label"), marginBottomParams(4))
        selfPairCard.addView(deviceNameInput, marginParams(10))
        selfPairCard.addView(quickPairBtn, marginParams(10))
        selfPairCard.addView(codePairRow)
        selfPairCard.addView(keyRow)

        // 4. All Workspace Paired Devices & Pairing Code Generator
        val allDevCard = createCard(context, paddingDp = 16)
        val allDevHdr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        allDevHdr.addView(createSectionTitle(context, "ALL PAIRED DEVICES").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        refreshDevicesBtn = WiFiActionButton(context).apply {
            configure("Refresh", textColor = WiFiVisionPalette.emeraldDeep, fillColor = WiFiVisionPalette.emeraldTint, strokeColor = WiFiVisionPalette.emeraldBorder, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 11.5f)
            setOnClickListener { onRefreshDevicesClick() }
        }
        allDevHdr.addView(refreshDevicesBtn)

        val platRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(10), 0, context.dp(8))
        }
        platAndroidBtn = WiFiActionButton(context).apply {
            setOnClickListener { selectCodePlatform("android") }
        }
        platWindowsBtn = WiFiActionButton(context).apply {
            setOnClickListener { selectCodePlatform("windows") }
        }
        platRow.addView(platAndroidBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(6) })
        platRow.addView(platWindowsBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(6) })

        genCodeBtn = WiFiActionButton(context).apply {
            configure("Generate Pairing Code", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.darkSlate, strokeColor = WiFiVisionPalette.darkSlate, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 12.5f)
            setOnClickListener { onGeneratePairingCodeClick(selectedCodePlatform) }
        }

        generatedCodeCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = roundedDrawable(context, WiFiVisionPalette.softGoldBg, WiFiVisionPalette.goldBorder, 12)
        }
        generatedCodeValue = TextView(context).apply {
            textSize = 22f
            letterSpacing = 0.22f
            typeface = Typeface.MONOSPACE
            setTextColor(WiFiVisionPalette.darkSlate)
        }
        generatedCodeExpiry = TextView(context).apply {
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.deepGold)
            setPadding(0, context.dp(4), 0, 0)
        }
        generatedCodeCard.addView(createSectionTitle(context, "SINGLE-USE PAIRING CODE"))
        generatedCodeCard.addView(generatedCodeValue)
        generatedCodeCard.addView(generatedCodeExpiry)

        devicesFeedbackText = TextView(context).apply {
            textSize = 12f
            setTextColor(WiFiVisionPalette.mutedSlate)
            setPadding(0, context.dp(8), 0, context.dp(4))
        }
        devicesListContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        allDevCard.addView(allDevHdr)
        allDevCard.addView(platRow)
        allDevCard.addView(genCodeBtn, marginParams(10))
        allDevCard.addView(generatedCodeCard, marginParams(8))
        allDevCard.addView(devicesFeedbackText)
        allDevCard.addView(devicesListContainer)

        // 5. System Monitoring Card
        val monCard = createCard(context, paddingDp = 16)
        val monHdr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        monHdr.addView(createSectionTitle(context, "SYSTEM MONITORING").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        runHealthBtn = WiFiActionButton(context).apply {
            configure("Run Health Check", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 11.5f)
            setOnClickListener { onRunMonitoringClick() }
        }
        monHdr.addView(runHealthBtn)

        autoRefreshCheck = CheckBox(context).apply {
            text = "Auto-refresh monitoring"
            textSize = 12.5f
            setTextColor(WiFiVisionPalette.darkSlate)
            isChecked = preferences.isMonitoringAutoRefresh()
            setOnCheckedChangeListener { _, checked ->
                preferences.setMonitoringAutoRefresh(checked)
                onMonitoringPrefChanged()
            }
        }
        intervalRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val monKpiRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(10), 0, context.dp(10))
        }
        val m1 = createMiniStatBox("BACKEND API")
        monBackendValue = m1.second
        val m2 = createMiniStatBox("MONGODB DB")
        monDbValue = m2.second
        val m3 = createMiniStatBox("ENDPOINTS")
        monAvailValue = m3.second
        monKpiRow.addView(m1.first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(4) })
        monKpiRow.addView(m2.first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(4); marginEnd = context.dp(4) })
        monKpiRow.addView(m3.first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(4) })

        monEndpointList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        monRecentFeed = TextView(context).apply {
            textSize = 11.5f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, context.dp(8), 0, 0)
        }

        monCard.addView(monHdr)
        monCard.addView(autoRefreshCheck)
        monCard.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(intervalRow) })
        monCard.addView(monKpiRow)
        monCard.addView(monEndpointList)
        monCard.addView(monRecentFeed)

        // 6. Backend URL & Local Preferences Card
        val prefCard = createCard(context, paddingDp = 16)
        val urlHdr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        urlHdr.addView(createSectionTitle(context, "ENDPOINT & APP PREFERENCES").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        endpointHealthPill = createPill(context, "UNKNOWN", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
        urlHdr.addView(endpointHealthPill)

        endpointDetailText = TextView(context).apply {
            text = "Current API: ${preferences.loadBaseUrl()}"
            textSize = 12f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, context.dp(6), 0, context.dp(8))
        }
        apiUrlInput = createStyledInput(context, BuildConfig.DEFAULT_API_BASE_URL, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI).apply {
            setText(preferences.loadBaseUrl())
        }
        applyUrlBtn = WiFiActionButton(context).apply {
            configure("Apply Backend URL", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.darkSlate, strokeColor = WiFiVisionPalette.darkSlate, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 12f)
            setOnClickListener { onApplyBaseUrlClick(apiUrlInput.text?.toString().orEmpty()) }
        }

        prefBandRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        prefLimitRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val resetPrefBtn = WiFiActionButton(context).apply {
            configure("Reset Dashboard Preferences", textColor = WiFiVisionPalette.bodySlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            setOnClickListener {
                preferences.resetDashboardPreferences()
                autoRefreshCheck.isChecked = preferences.isMonitoringAutoRefresh()
                renderIntervalChips()
                renderPrefBandChips()
                renderPrefLimitChips()
                onMonitoringPrefChanged()
                onPreferencesChanged(preferences.getDefaultBand(), preferences.getPerformanceHistoryLimit())
            }
        }

        prefCard.addView(urlHdr)
        prefCard.addView(endpointDetailText)
        prefCard.addView(apiUrlInput, marginParams(8))
        prefCard.addView(applyUrlBtn, marginParams(12))
        prefCard.addView(createFieldLabel(context, "Default Frequency Filter"), marginBottomParams(6))
        prefCard.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(prefBandRow) }, marginParams(10))
        prefCard.addView(createFieldLabel(context, "Performance History Window"), marginBottomParams(6))
        prefCard.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(prefLimitRow) }, marginParams(12))
        prefCard.addView(resetPrefBtn)

        // 7. Wi-Fi & Location Permissions Card
        val permCard = createCard(context, paddingDp = 16)
        permCard.addView(createSectionTitle(context, "PERMISSIONS & HARDWARE GUIDANCE"))
        permStatusText = TextView(context).apply {
            textSize = 12f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, context.dp(8), 0, context.dp(10))
        }
        reqPermBtn = WiFiActionButton(context).apply {
            configure("Request Wi-Fi & GPS Permissions", textColor = WiFiVisionPalette.emeraldDeep, fillColor = WiFiVisionPalette.emeraldTint, strokeColor = WiFiVisionPalette.emeraldBorder, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            setOnClickListener { onRequestPermissionsClick() }
        }
        val sysSettingsBtn = WiFiActionButton(context).apply {
            configure("Open Android App Settings", textColor = WiFiVisionPalette.bodySlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            setOnClickListener { onOpenAppSettingsClick() }
        }
        permCard.addView(permStatusText)
        permCard.addView(reqPermBtn, marginParams(8))
        permCard.addView(sysSettingsBtn)

        // 8. In-App Updates Card
        val updateCard = createCard(context, paddingDp = 16)
        val updateHdr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        updateHdr.addView(
            createSectionTitle(context, "APP UPDATES").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        updateStatusPill = createPill(
            context,
            label = "NOT CHECKED",
            textColor = WiFiVisionPalette.bodySlate,
            bgColor = WiFiVisionPalette.softIvory,
            borderColor = WiFiVisionPalette.hairline,
            dotColor = WiFiVisionPalette.mutedSlate
        )
        updateHdr.addView(updateStatusPill)

        val (instBox, instVal) = createMiniStatBox("INSTALLED VERSION")
        val (latBox, latVal) = createMiniStatBox("LATEST RELEASE")
        updateInstalledValue = instVal
        updateInstalledValue.text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        updateLatestValue = latVal
        updateLatestValue.text = "—"

        updateLastCheckText = TextView(context).apply {
            text = "Last check: Never"
            textSize = 11.5f
            setTextColor(WiFiVisionPalette.mutedSlate)
            setPadding(0, context.dp(8), 0, context.dp(4))
        }
        updateDetailText = TextView(context).apply {
            text = "Automatic checks run on launch against official WiFiVision release metadata."
            textSize = 12f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, 0, 0, context.dp(10))
        }
        checkUpdatesBtn = WiFiActionButton(context).apply {
            configure(
                label = "Check for Updates",
                textColor = WiFiVisionPalette.pureWhite,
                fillColor = WiFiVisionPalette.emeraldDeep,
                strokeColor = WiFiVisionPalette.emeraldDeep,
                rippleColor = WiFiVisionPalette.rippleLight,
                radiusDp = 10,
                textSizeSp = 12.5f
            )
            setOnClickListener { onCheckForUpdatesClick() }
        }
        updateCard.addView(updateHdr, marginParams(10))
        updateCard.addView(UiFormatters.twoColumnRow(context, instBox, latBox, 8))
        updateCard.addView(updateLastCheckText)
        updateCard.addView(updateDetailText)
        updateCard.addView(checkUpdatesBtn)

        contentCol.addView(acctCard, marginParams(12))
        contentCol.addView(syncCard, marginParams(12))
        contentCol.addView(selfPairCard, marginParams(12))
        contentCol.addView(allDevCard, marginParams(12))
        contentCol.addView(monCard, marginParams(12))
        contentCol.addView(prefCard, marginParams(12))
        contentCol.addView(updateCard, marginParams(12))
        contentCol.addView(permCard)

        selectCodePlatform("android")
        renderIntervalChips()
        renderPrefBandChips()
        renderPrefLimitChips()
    }

    private fun createMiniStatBox(title: String): Pair<LinearLayout, TextView> {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(10), context.dp(9), context.dp(10), context.dp(9))
            background = roundedDrawable(context, WiFiVisionPalette.warmIvory, WiFiVisionPalette.hairline, 12)
        }
        box.addView(TextView(context).apply {
            text = title; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.mutedSlate)
        })
        val valueView = TextView(context).apply {
            text = "—"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(4), 0, 0)
        }
        box.addView(valueView)
        return box to valueView
    }

    fun updateUserProfile(user: AuthUser?) {
        if (user == null) {
            acctNameText.text = "Not Signed In"
            acctEmailText.text = "—"
            acctIdText.text = "Account ID: —"
            return
        }
        acctNameText.text = user.name
        acctEmailText.text = user.email
        acctIdText.text = "ID: ${user.id}"
        if (user.isEmailVerified) {
            updatePillStyle(context, acctVerifiedPill, "Verified", WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
        } else {
            updatePillStyle(context, acctVerifiedPill, "Unverified", WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
        }
    }

    fun setProfileLoading(loading: Boolean) {
        refreshProfileBtn.isEnabled = !loading
        refreshProfileBtn.text = if (loading) "Syncing..." else "Refresh Profile"
    }

    fun updateQueueStats(counts: QueueStateCounts, canUpload: Boolean, isUploading: Boolean) {
        syncPendingValue.text = if (counts.uploadingCount > 0) {
            "${counts.queuedCount} queued (${counts.uploadingCount} active)"
        } else {
            "${counts.queuedCount} queued (${counts.readyToSyncCount} ready)"
        }
        syncUploadedValue.text = "${counts.uploadedCount} uploaded • ${counts.failedCount} failed"
        syncUploadBtn.isEnabled = !isUploading && canUpload
        syncUploadBtn.text = when {
            isUploading -> "Uploading (${counts.uploadingCount}/${counts.queuedCount})..."
            counts.readyToSyncCount > 0 -> "Sync Observations Now (${counts.readyToSyncCount})"
            else -> "Sync Observations"
        }
    }

    fun setUploadBanner(message: String, textColor: Int, bgColor: Int, borderColor: Int) {
        syncBannerText.text = message
        syncBannerText.setTextColor(textColor)
        syncBannerBox.background = roundedDrawable(context, bgColor, borderColor, 10)
    }

    fun updateThisDevicePairing(activeCred: PairedDeviceCredential?, storedCred: PairedDeviceCredential?, isBusy: Boolean) {
        when {
            activeCred != null -> {
                updatePillStyle(context, thisDeviceBadgePill, "PAIRED", WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder, WiFiVisionPalette.jade)
                thisDeviceDetailText.text = "Paired as \"${activeCred.deviceName}\" • Protected in Android Keystore AES-256-GCM (scoped observation upload only)."
                rotateBtn.isEnabled = !isBusy
                unpairBtn.isEnabled = !isBusy
            }
            storedCred != null -> {
                updatePillStyle(context, thisDeviceBadgePill, "EXPIRED", WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder, WiFiVisionPalette.crimsonText)
                thisDeviceDetailText.text = "Stored device credential expired. Use Quick Pair or enter a fresh 8-char code."
                rotateBtn.isEnabled = false
                unpairBtn.isEnabled = !isBusy
            }
            else -> {
                updatePillStyle(context, thisDeviceBadgePill, "UNPAIRED", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder, WiFiVisionPalette.amberText)
                thisDeviceDetailText.text = "Tap Quick Pair This Phone or enter an 8-char Android code to authorize private uploads."
                rotateBtn.isEnabled = false
                unpairBtn.isEnabled = false
            }
        }
        quickPairBtn.isEnabled = !isBusy
        pairBtn.isEnabled = !isBusy
    }

    fun clearPairingCodeInput() {
        pairingCodeInput.setText("")
    }

    fun setDevicesLoading(loading: Boolean) {
        refreshDevicesBtn.isEnabled = !loading
        refreshDevicesBtn.text = if (loading) "Loading..." else "Refresh"
        genCodeBtn.isEnabled = !loading
    }

    fun showGeneratedPairingCode(resp: PairingCodeResponse) {
        generatedCodeValue.text = resp.pairingCode
        activePairingExpiresAtMs = try {
            Instant.parse(resp.expiresAt).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis() + resp.expiresInSeconds * 1000L
        }
        generatedCodeCard.visibility = View.VISIBLE
        startPairingCountdown()
    }

    fun updatePairedDevicesList(devices: List<PairedDeviceSummary>, error: String?) {
        devicesListContainer.removeAllViews()
        if (error != null) {
            devicesFeedbackText.text = error
            devicesFeedbackText.setTextColor(WiFiVisionPalette.crimsonText)
            return
        }

        if (devices.isEmpty()) {
            devicesFeedbackText.text = "No companion devices paired to your account yet."
            devicesFeedbackText.setTextColor(WiFiVisionPalette.mutedSlate)
            return
        }

        devicesFeedbackText.text = "${devices.size} device credential(s) in your workspace"
        devicesFeedbackText.setTextColor(WiFiVisionPalette.bodySlate)

        devices.forEachIndexed { idx, dev ->
            val isActive = dev.status.equals("active", ignoreCase = true)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, context.dp(8), 0, context.dp(8))
            }
            val leftCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) }
            }
            leftCol.addView(TextView(context).apply {
                text = "${dev.deviceName} (${dev.platform.replaceFirstChar { it.uppercase() }})"
                textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.darkSlate)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
            leftCol.addView(TextView(context).apply {
                val lastUsed = dev.lastUsedAt?.let { UiFormatters.formatIsoShort(it) } ?: "Never"
                text = "${dev.status.uppercase(Locale.US)} • Last used: $lastUsed"
                textSize = 11f
                setTextColor(if (isActive) WiFiVisionPalette.emeraldDeep else WiFiVisionPalette.crimsonText)
            })
            row.addView(leftCol)

            if (isActive) {
                val revokeBtn = WiFiActionButton(context).apply {
                    minimumHeight = context.dp(34)
                    configure("Revoke", textColor = WiFiVisionPalette.crimsonText, fillColor = WiFiVisionPalette.crimsonBg, strokeColor = WiFiVisionPalette.crimsonBorder, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 8, textSizeSp = 11.5f)
                    setOnClickListener { confirmRevokeDevice(dev) }
                }
                row.addView(revokeBtn, LinearLayout.LayoutParams(context.dp(84), LinearLayout.LayoutParams.WRAP_CONTENT))
            } else {
                row.addView(createPill(context, "REVOKED", WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder))
            }

            devicesListContainer.addView(row)
            if (idx < devices.lastIndex) {
                devicesListContainer.addView(View(context).apply {
                    setBackgroundColor(WiFiVisionPalette.hairline)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(1).coerceAtLeast(1)))
            }
        }
    }

    fun setMonitoringLoading(loading: Boolean) {
        runHealthBtn.isEnabled = !loading
        runHealthBtn.text = if (loading) "Checking..." else "Run Health Check"
    }

    fun updateMonitoringSnapshot(snapshot: SystemMonitoringSnapshot) {
        val h = snapshot.health
        monBackendValue.text = if (h.healthy) "Healthy" else if (h.reachable) "Degraded" else "Offline"
        monBackendValue.setTextColor(if (h.healthy) WiFiVisionPalette.emeraldDeep else WiFiVisionPalette.crimsonText)

        monDbValue.text = h.databaseStatus?.replaceFirstChar { it.uppercase() } ?: "Unknown"
        val dbOk = h.databaseStatus.equals("connected", ignoreCase = true)
        monDbValue.setTextColor(if (dbOk) WiFiVisionPalette.emeraldDeep else WiFiVisionPalette.crimsonText)

        val availCount = snapshot.endpoints.count { it.available }
        monAvailValue.text = "$availCount / ${snapshot.endpoints.size}"

        monEndpointList.removeAllViews()
        snapshot.endpoints.forEach { ep ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
                background = roundedDrawable(context, WiFiVisionPalette.warmIvory, WiFiVisionPalette.hairline, 10)
            }
            val leftCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) }
            }
            leftCol.addView(TextView(context).apply {
                text = "${ep.label} (${ep.endpoint})"
                textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.darkSlate)
            })
            leftCol.addView(TextView(context).apply {
                text = ep.detail
                textSize = 11f; setTextColor(WiFiVisionPalette.bodySlate)
            })
            val pill = if (ep.available) {
                createPill(context, "Available", WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
            } else {
                createPill(context, "Unavailable", WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
            }
            row.addView(leftCol); row.addView(pill)
            monEndpointList.addView(row, marginParams(6))
        }

        val obsTotal = snapshot.observationAnalytics?.totalNetworks?.toString() ?: "Unavailable"
        val perfTotal = snapshot.performanceAnalytics?.totalTests?.toString() ?: "Unavailable"
        monRecentFeed.text = "Stored Wi-Fi scans: $obsTotal • Speed tests: $perfTotal • Checked ${UiFormatters.formatTimeOfDay(snapshot.checkedAtEpochMs)}"
    }

    fun updateEndpointHealth(result: HealthCheckResult?, currentUrl: String) {
        endpointDetailText.text = if (result != null) {
            "${result.message} • $currentUrl"
        } else {
            "Current API: $currentUrl"
        }
        when {
            result == null -> updatePillStyle(context, endpointHealthPill, "CHECKING", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
            result.healthy -> updatePillStyle(context, endpointHealthPill, "CONNECTED", WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder, WiFiVisionPalette.jade)
            result.reachable -> updatePillStyle(context, endpointHealthPill, "DEGRADED", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
            else -> updatePillStyle(context, endpointHealthPill, "OFFLINE", WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
        }
    }

    fun updatePermissionsCard(permissionManager: WifiPermissionManager) {
        permStatusText.text = buildString {
            appendLine("Status: ${permissionManager.getPermissionStatus()}")
            appendLine("Wi-Fi Enabled: ${if (permissionManager.isWifiEnabled()) "Yes" else "No"}")
            appendLine("Location Services: ${if (permissionManager.isLocationEnabled()) "Yes" else "No"}")
            append("Fine Location Granted: ${if (permissionManager.hasFineLocationPermission()) "Yes" else "No"}")
        }
    }

    fun setUpdateCheckLoading(loading: Boolean) {
        checkUpdatesBtn.isEnabled = !loading
        checkUpdatesBtn.text = if (loading) "Checking for Updates..." else "Check for Updates"
        if (loading) {
            updatePillStyle(
                context,
                updateStatusPill,
                label = "CHECKING",
                textColor = WiFiVisionPalette.amberText,
                bgColor = WiFiVisionPalette.amberBg,
                borderColor = WiFiVisionPalette.amberBorder,
                dotColor = WiFiVisionPalette.amberText
            )
            updateDetailText.text = "Contacting official release endpoint..."
        }
    }

    fun updateAppUpdateCard(
        installedVersionName: String,
        installedVersionCode: Int,
        latestVersionName: String?,
        latestVersionCode: Int,
        lastCheckMs: Long,
        updateAvailable: Boolean,
        statusMessage: String,
        isError: Boolean = false
    ) {
        updateInstalledValue.text = "v$installedVersionName ($installedVersionCode)"
        updateLatestValue.text = if (!latestVersionName.isNullOrBlank() && latestVersionCode > 0) {
            "v$latestVersionName ($latestVersionCode)"
        } else {
            "Not checked"
        }
        updateLastCheckText.text = if (lastCheckMs > 0L) {
            "Last check: ${UiFormatters.formatTimeOfDay(lastCheckMs)}"
        } else {
            "Last check: Never"
        }
        updateDetailText.text = statusMessage

        when {
            isError -> {
                updatePillStyle(
                    context,
                    updateStatusPill,
                    label = "CHECK FAILED",
                    textColor = WiFiVisionPalette.crimsonText,
                    bgColor = WiFiVisionPalette.crimsonBg,
                    borderColor = WiFiVisionPalette.crimsonBorder,
                    dotColor = WiFiVisionPalette.crimsonText
                )
            }
            updateAvailable -> {
                updatePillStyle(
                    context,
                    updateStatusPill,
                    label = "UPDATE AVAILABLE",
                    textColor = WiFiVisionPalette.deepGold,
                    bgColor = WiFiVisionPalette.softGoldBg,
                    borderColor = WiFiVisionPalette.goldBorder,
                    dotColor = WiFiVisionPalette.champagneGold
                )
            }
            latestVersionCode > 0 -> {
                updatePillStyle(
                    context,
                    updateStatusPill,
                    label = "UP TO DATE",
                    textColor = WiFiVisionPalette.emeraldDeep,
                    bgColor = WiFiVisionPalette.emeraldTint,
                    borderColor = WiFiVisionPalette.emeraldBorder,
                    dotColor = WiFiVisionPalette.jade
                )
            }
            else -> {
                updatePillStyle(
                    context,
                    updateStatusPill,
                    label = "NOT CHECKED",
                    textColor = WiFiVisionPalette.bodySlate,
                    bgColor = WiFiVisionPalette.softIvory,
                    borderColor = WiFiVisionPalette.hairline,
                    dotColor = WiFiVisionPalette.mutedSlate
                )
            }
        }
    }

    private fun confirmLogoutAll() {
        AlertDialog.Builder(context)
            .setTitle("Sign Out All Sessions?")
            .setMessage("This revokes your active web and mobile authentication tokens across all devices. Paired device upload keys remain untouched until revoked.")
            .setPositiveButton("Sign Out All") { _, _ -> onLogoutAllClick() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRevokeDevice(dev: PairedDeviceSummary) {
        AlertDialog.Builder(context)
            .setTitle("Revoke ${dev.deviceName}?")
            .setMessage("This immediately revokes scoped observation uploads for this ${dev.platform} device credential.")
            .setPositiveButton("Revoke") { _, _ -> onRevokeDeviceClick(dev.deviceId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectCodePlatform(platform: String) {
        selectedCodePlatform = platform
        if (platform == "android") {
            platAndroidBtn.configure("Android Code", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 12f)
            platWindowsBtn.configure("Windows Code", textColor = WiFiVisionPalette.bodySlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
        } else {
            platAndroidBtn.configure("Android Code", textColor = WiFiVisionPalette.bodySlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12f)
            platWindowsBtn.configure("Windows Code", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 12f)
        }
    }

    private fun startPairingCountdown() {
        pairingCountdownRunnable?.let { handler.removeCallbacks(it) }
        val tick = object : Runnable {
            override fun run() {
                val remSec = ((activePairingExpiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                if (remSec > 0L) {
                    val m = remSec / 60L
                    val s = remSec % 60L
                    generatedCodeExpiry.text = String.format(Locale.US, "Expires in %d:%02d • Enter in %s companion app", m, s, selectedCodePlatform)
                    generatedCodeExpiry.setTextColor(WiFiVisionPalette.deepGold)
                    handler.postDelayed(this, 1000L)
                } else {
                    generatedCodeExpiry.text = "Expired — generate a new pairing code"
                    generatedCodeExpiry.setTextColor(WiFiVisionPalette.crimsonText)
                }
            }
        }
        pairingCountdownRunnable = tick
        tick.run()
    }

    private fun renderIntervalChips() {
        intervalRow.removeAllViews()
        val current = preferences.getMonitoringIntervalSeconds()
        listOf(15, 30, 60).forEach { sec ->
            val chip = createPrefChip("Every ${sec}s", sec == current) {
                preferences.setMonitoringIntervalSeconds(sec)
                renderIntervalChips()
                onMonitoringPrefChanged()
            }
            intervalRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    private fun renderPrefBandChips() {
        prefBandRow.removeAllViews()
        val current = preferences.getDefaultBand()
        BandFilter.entries.forEach { band ->
            val chip = createPrefChip(band.label, band == current) {
                preferences.setDefaultBand(band)
                renderPrefBandChips()
                onPreferencesChanged(band, preferences.getPerformanceHistoryLimit())
            }
            prefBandRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    private fun renderPrefLimitChips() {
        prefLimitRow.removeAllViews()
        val current = preferences.getPerformanceHistoryLimit()
        listOf(20, 50, 100).forEach { lim ->
            val chip = createPrefChip("Last $lim", lim == current) {
                preferences.setPerformanceHistoryLimit(lim)
                renderPrefLimitChips()
                onPreferencesChanged(preferences.getDefaultBand(), lim)
            }
            prefLimitRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    private fun createPrefChip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
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

    private fun marginParams(bottomDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = context.dp(bottomDp)
        }
    }

    private fun marginBottomParams(bottomDp: Int): LinearLayout.LayoutParams = marginParams(bottomDp)

    fun cleanup() {
        pairingCountdownRunnable?.let { handler.removeCallbacks(it) }
        pairingCountdownRunnable = null
        handler.removeCallbacksAndMessages(null)
    }
}
