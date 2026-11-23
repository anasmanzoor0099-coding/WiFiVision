package com.wifivision.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var permissionManager: WifiPermissionManager
    private lateinit var scanner: WifiScanner
    private lateinit var wifiMonitor: WifiMonitor
    private lateinit var locationProvider: LocationProvider
    private lateinit var observationQueue: ObservationQueue
    private lateinit var sessionController: ObservationSessionController
    private lateinit var credentialStore: DeviceCredentialStore
    private lateinit var sessionStore: SessionCredentialStore
    private lateinit var authSessionManager: AuthSessionManager
    private lateinit var preferences: AppPreferences
    private lateinit var apiClient: BackendApiClient
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var updateDialogController: UpdateDialogController

    private val handler = Handler(Looper.getMainLooper())

    // Top-level containers
    private lateinit var rootFrame: FrameLayout
    private lateinit var workspaceContainer: LinearLayout
    private lateinit var authController: AuthScreenController

    // Workspace chrome
    private lateinit var topBar: LinearLayout
    private lateinit var backendPill: TextView
    private lateinit var userHandleText: TextView
    private lateinit var globalFeedbackContainer: LinearLayout
    private lateinit var globalFeedbackProgress: ProgressBar
    private lateinit var globalFeedbackDot: View
    private lateinit var globalBannerText: TextView
    private lateinit var tabHost: FrameLayout
    private lateinit var bottomNavBar: LinearLayout

    // 5 Parity Screen Controllers
    private lateinit var overviewScreen: OverviewScreenController
    private lateinit var networksScreen: NetworksScreenController
    private lateinit var heatmapScreen: HeatmapScreenController
    private lateinit var analyticsScreen: AnalyticsScreenController
    private lateinit var settingsScreen: SettingsScreenController

    private val navTabs = LinkedHashMap<DashboardTab, NavTabUi>()

    private data class NavTabUi(
        val root: LinearLayout,
        val iconCapsule: LinearLayout,
        val icon: ImageView,
        val label: TextView,
        val countBadge: TextView
    )

    private enum class DashboardTab {
        OVERVIEW,
        NETWORKS,
        HEATMAP,
        ANALYTICS,
        SETTINGS
    }

    private enum class FeedbackTone {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    private enum class PendingPermissionAction {
        NONE,
        SCAN_ONLY,
        GPS_ONLY,
        START_MONITOR,
        HEATMAP_LOCATE
    }

    private var activeTab: DashboardTab = DashboardTab.OVERVIEW
    private var currentBaseUrl: String = ""

    // Local scanning & upload state
    private var isUploading = false
    private var isPairingBusy = false
    private var isHealthCheckBusy = false
    private var isGpsBusy = false
    private var isActivityStarted = false
    private var scanGeneration = 0L
    private var gpsGeneration = 0L
    private var activeScanFromMonitor = false
    private var isWifiLiveMonitoring = false
    private var liveMonitorGeneration = 0L
    private var liveMonitorIntervalSec = AppPreferences.DEFAULT_WIFI_MONITOR_INTERVAL_SEC
    private var lastLiveMonitorThrottled = false
    private var liveMonitorRunnable: Runnable? = null
    private var pendingAction: PendingPermissionAction = PendingPermissionAction.NONE
    private var drainRunnable: Runnable? = null
    private var uploadConfirmDialog: AlertDialog? = null

    // Cloud parity state
    private var isOverviewCloudBusy = false
    private var isCloudExplorerBusy = false
    private var isHeatmapBusy = false
    private var isRfAnalyticsBusy = false
    private var isPerfBusy = false
    private var isSpeedTestBusy = false
    private var isDevicesBusy = false
    private var isMonitoringBusy = false

    private var cloudExplorerLoaded = false
    private var heatmapLoaded = false
    private var rfAnalyticsLoaded = false
    private var perfLoaded = false
    private var devicesLoaded = false
    private var monitoringLoaded = false

    private val loadedCloudObservations = mutableListOf<CloudObservation>()
    private var cachedQualityItems: List<NetworkQualityItem> = emptyList()
    private var cachedObsAnalyticsAll: ObservationAnalytics? = null
    private var cachedPerfAnalytics: PerformanceAnalytics? = null
    private var activeHeatmapBand: BandFilter = BandFilter.ALL
    private var activeRfBand: BandFilter = BandFilter.ALL
    private var activePerfLimit: Int = 50
    private var monitoringAutoRunnable: Runnable? = null

    // In-app update state
    private var isUpdateCheckBusy = false
    private var dismissedUpdateVersionCode: Int? = null
    private var pendingInstallAfterPermission: Pair<File, AppReleaseMetadata>? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val KEY_ACTIVE_TAB = "active_tab_v2"
        private const val KEY_ACTIVE_HEATMAP_BAND = "active_heatmap_band"
        private const val KEY_ACTIVE_RF_BAND = "active_rf_band"
        private const val LIVE_MONITOR_THROTTLED_MIN_INTERVAL_MS = 60_000L
        private const val SCAN_GPS_TIMEOUT_MS = 5_000L
        private const val SCAN_GPS_CACHE_AGE_MS = 20_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_WiFiVision)
        super.onCreate(savedInstanceState)

        preferences = AppPreferences(this)
        liveMonitorIntervalSec = preferences.getWifiMonitorIntervalSeconds()
        activeHeatmapBand = preferences.getDefaultBand()
        activeRfBand = preferences.getDefaultBand()
        activePerfLimit = preferences.getPerformanceHistoryLimit()

        if (savedInstanceState != null) {
            val savedTabName = savedInstanceState.getString(KEY_ACTIVE_TAB)
            activeTab = DashboardTab.entries.firstOrNull { it.name == savedTabName } ?: DashboardTab.OVERVIEW
            savedInstanceState.getString(KEY_ACTIVE_HEATMAP_BAND)?.let { raw ->
                activeHeatmapBand = BandFilter.entries.firstOrNull { it.name == raw } ?: activeHeatmapBand
            }
            savedInstanceState.getString(KEY_ACTIVE_RF_BAND)?.let { raw ->
                activeRfBand = BandFilter.entries.firstOrNull { it.name == raw } ?: activeRfBand
            }
        }

        permissionManager = WifiPermissionManager(this)
        scanner = WifiScanner(this, permissionManager)
        wifiMonitor = WifiMonitor(this, permissionManager)
        locationProvider = LocationProvider(this, permissionManager)
        observationQueue = ObservationQueue(this)
        sessionController = ObservationSessionController(observationQueue.logic)
        credentialStore = DeviceCredentialStore(this)
        sessionStore = SessionCredentialStore(this)
        apiClient = BackendApiClient()
        authSessionManager = AuthSessionManager(apiClient, sessionStore)
        appUpdateManager = AppUpdateManager(this, preferences)
        updateDialogController = UpdateDialogController(
            context = this,
            onUpdateNowClick = { release -> startInAppUpdateDownload(release) },
            onLaterClick = { versionCode ->
                dismissedUpdateVersionCode = versionCode
                pendingInstallAfterPermission = null
            },
            onCancelDownloadClick = { appUpdateManager.cancelDownload() },
            onOpenInstallSettingsClick = {
                try {
                    startActivity(appUpdateManager.createUnknownSourcesSettingsIntent())
                } catch (_: Exception) {
                    setGlobalFeedback("Unable to open Android install permission settings.", FeedbackTone.ERROR)
                }
            }
        )

        currentBaseUrl = preferences.loadBaseUrl()
        buildRootUi()
        seedSettingsUpdateCardFromCache()

        AppUpdateInstallBus.listener = { event ->
            if (isUiAlive()) {
                handleUpdateInstallEvent(event)
            }
        }

        val startup = sessionController.onAppStartup()
        refreshLocalUiState()

        if (startup.pendingQueueCount > 0) {
            setUploadFeedback(
                "${startup.pendingQueueCount} pending observation(s) saved locally. Review and upload when ready.",
                FeedbackTone.INFO
            )
        }

        val activeSession = authSessionManager.getActiveSession()
        if (activeSession != null) {
            enterWorkspace(activeSession)
        } else {
            showAuthenticationScreen()
            if (!authController.showCreatePasswordIfSetupActive()) {
                authController.showSignIn()
            }
        }

        // Check official release metadata automatically on app launch.
        checkForAppUpdates(manual = false)
    }

    override fun onResume() {
        super.onResume()
        val pendingInstall = pendingInstallAfterPermission ?: return
        if (appUpdateManager.canRequestPackageInstalls()) {
            pendingInstallAfterPermission = null
            val (apkFile, release) = pendingInstall
            commitVerifiedUpdateInstall(apkFile, release)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACTIVE_TAB, activeTab.name)
        outState.putString(KEY_ACTIVE_HEATMAP_BAND, activeHeatmapBand.name)
        outState.putString(KEY_ACTIVE_RF_BAND, activeRfBand.name)
    }

    override fun onStart() {
        super.onStart()
        isActivityStarted = true
        networksScreen.updateLiveNetworks(sessionController.lastScanNetworks, sessionController.lastScanStats)
        refreshLocalUiState()
        if (authSessionManager.isSignedIn()) {
            scheduleMonitoringAutoRefresh()
        }
    }

    override fun onStop() {
        isActivityStarted = false
        val hadPendingScan = sessionController.isScanInProgress
        dismissUploadDialog()
        stopLiveMonitoring(feedbackMessage = null)
        cancelActiveScanAndGps(
            cancelledMessage = if (hadPendingScan && !activeScanFromMonitor) "Scan cancelled while app moved to background" else null
        )
        cancelScheduledDrain()
        cancelMonitoringAutoRefresh()
        sessionController.onStopCleanup()
        refreshLocalUiState()
        updateGlobalLoadingIndicator()
        super.onStop()
    }

    override fun onDestroy() {
        isActivityStarted = false
        AppUpdateInstallBus.listener = null
        updateDialogController.dismiss()
        appUpdateManager.shutdown()
        dismissUploadDialog()
        stopLiveMonitoring(feedbackMessage = null)
        cancelActiveScanAndGps(cancelledMessage = null)
        cancelScheduledDrain()
        cancelMonitoringAutoRefresh()
        sessionController.onStopCleanup()
        authController.cleanup()
        settingsScreen.cleanup()
        heatmapScreen.mapView.shutdown()
        apiClient.shutdown()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun isUiAlive(): Boolean = !isFinishing && !isDestroyed

    private fun buildRootUi() {
        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(WiFiVisionPalette.warmIvory)
            fitsSystemWindows = false
        }

        authController = AuthScreenController(
            context = this,
            authSessionManager = authSessionManager,
            preferences = preferences,
            onAuthenticated = { session ->
                if (!isUiAlive()) return@AuthScreenController
                enterWorkspace(session)
            },
            onBaseUrlUpdated = { nextUrl ->
                currentBaseUrl = nextUrl
                settingsScreen.apiUrlInput.setText(nextUrl)
                overviewScreen.updateHealth(sessionController.lastHealthResult, nextUrl)
            }
        )

        workspaceContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(WiFiVisionPalette.warmIvory)
            visibility = View.GONE
        }

        // Top App Bar
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(WiFiVisionPalette.pureWhite)
            elevation = dpF(2f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }

        val brandEmblem = ImageView(this).apply {
            setImageResource(R.drawable.ic_brand_badge)
            contentDescription = "WiFiVision"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val brandCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        brandCol.addView(TextView(this).apply {
            text = "WiFiVision"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.darkSlate)
            maxLines = 1
        })
        userHandleText = TextView(this).apply {
            text = "Network Intelligence"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.jade)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        brandCol.addView(userHandleText)

        backendPill = createPill(
            this,
            label = "CHECKING",
            textColor = WiFiVisionPalette.amberText,
            bgColor = WiFiVisionPalette.amberBg,
            borderColor = WiFiVisionPalette.amberBorder,
            dotColor = WiFiVisionPalette.amberText
        ).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { switchTab(DashboardTab.SETTINGS) }
        }

        topBar.addView(brandEmblem, LinearLayout.LayoutParams(dp(36), dp(36)))
        topBar.addView(brandCol)
        topBar.addView(backendPill)

        val topHairline = View(this).apply { setBackgroundColor(WiFiVisionPalette.hairline) }

        // Global feedback bar
        globalFeedbackContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedDrawable(this@MainActivity, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline, 12)
        }
        globalFeedbackProgress = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(WiFiVisionPalette.emeraldDeep)
        }
        globalFeedbackDot = View(this).apply {
            background = pillDrawable(this@MainActivity, WiFiVisionPalette.jade, WiFiVisionPalette.jade, 0)
        }
        globalBannerText = TextView(this).apply {
            text = "Ready for local & cloud network intelligence"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.bodySlate)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        globalFeedbackContainer.addView(globalFeedbackProgress, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) })
        globalFeedbackContainer.addView(globalFeedbackDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
        globalFeedbackContainer.addView(globalBannerText)

        val feedbackWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(2))
            addView(globalFeedbackContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        // Instantiate all 5 parity screens
        overviewScreen = OverviewScreenController(
            context = this,
            onScanClick = { handleScanOnlyClick() },
            onToggleMonitorClick = { toggleMonitoring() },
            onReviewUploadClick = { handleUploadObservationsClick() },
            onRefreshGpsClick = { handleRefreshGpsClick() },
            onRefreshCloudClick = { refreshOverviewCloudData() },
            onOpenCloudExplorerClick = {
                networksScreen.switchSubMode(NetworksScreenController.SubMode.CLOUD_EXPLORER)
                switchTab(DashboardTab.NETWORKS)
                if (!cloudExplorerLoaded) {
                    refreshCloudExplorer(page = 1, append = false)
                }
            },
            onOpenPerformanceClick = {
                analyticsScreen.switchSubMode(AnalyticsScreenController.SubMode.PERFORMANCE)
                switchTab(DashboardTab.ANALYTICS)
                if (!perfLoaded) {
                    refreshPerformanceData(activePerfLimit)
                }
            },
            onOpenSettingsClick = { switchTab(DashboardTab.SETTINGS) }
        )

        networksScreen = NetworksScreenController(
            context = this,
            onScanClick = { handleScanOnlyClick() },
            onToggleMonitorClick = { toggleMonitoring() },
            onMonitorIntervalChanged = { sec -> handleLiveMonitorIntervalChanged(sec) },
            onUploadClick = { handleUploadObservationsClick() },
            onClearSessionNetworksClick = { handleClearSessionNetworksClick() },
            onRequestPermissionsClick = {
                ensureRuntimePermissions(PendingPermissionAction.SCAN_ONLY)
            },
            onRefreshCloudClick = { refreshCloudExplorer(page = 1, append = false) },
            onLoadMoreCloudClick = { nextPage -> refreshCloudExplorer(page = nextPage, append = true) }
        )
        networksScreen.setDefaultBand(preferences.getDefaultBand())

        heatmapScreen = HeatmapScreenController(
            context = this,
            initialBand = activeHeatmapBand,
            onBandChanged = { nextBand ->
                activeHeatmapBand = nextBand
                refreshHeatmapData(nextBand)
            },
            onRefreshClick = { band -> refreshHeatmapData(band) },
            onLocateMeClick = { handleHeatmapLocateMeClick() }
        )

        analyticsScreen = AnalyticsScreenController(
            context = this,
            initialBand = activeRfBand,
            initialHistoryLimit = activePerfLimit,
            onRfBandChanged = { nextBand ->
                activeRfBand = nextBand
                refreshRfAnalyticsData(nextBand)
            },
            onRefreshRfClick = { band -> refreshRfAnalyticsData(band) },
            onRunSpeedTestClick = { handleRunSpeedTestClick() },
            onRefreshPerfClick = { limit -> refreshPerformanceData(limit) },
            onPerfLimitChanged = { nextLimit ->
                activePerfLimit = nextLimit
                preferences.setPerformanceHistoryLimit(nextLimit)
                refreshPerformanceData(nextLimit)
            }
        )

        settingsScreen = SettingsScreenController(
            context = this,
            preferences = preferences,
            onRefreshProfileClick = { refreshAccountProfile() },
            onLogoutClick = { handleLogout(allSessions = false) },
            onLogoutAllClick = { handleLogout(allSessions = true) },
            onReviewUploadClick = { handleUploadObservationsClick() },
            onQuickPairSelfClick = { name -> handleQuickPairSelf(name) },
            onPairWithCodeClick = { code, name -> handlePairWithCode(code, name) },
            onRotateKeyClick = { handleRotateCredentialClick() },
            onUnpairClick = { handleUnpairClick() },
            onRefreshDevicesClick = { refreshPairedDevices() },
            onGeneratePairingCodeClick = { platform -> handleGeneratePairingCode(platform) },
            onRevokeDeviceClick = { deviceId -> handleRevokeDevice(deviceId) },
            onRunMonitoringClick = { refreshMonitoringSnapshot() },
            onMonitoringPrefChanged = { scheduleMonitoringAutoRefresh() },
            onApplyBaseUrlClick = { raw -> applyApiBaseUrl(raw) },
            onPreferencesChanged = { newBand, newLimit ->
                activeHeatmapBand = newBand
                activeRfBand = newBand
                activePerfLimit = newLimit
                networksScreen.setDefaultBand(newBand)
                heatmapScreen.setSelectedBand(newBand)
                analyticsScreen.setDefaultPreferences(newBand, newLimit)
            },
            onRequestPermissionsClick = {
                ensureRuntimePermissions(PendingPermissionAction.NONE)
            },
            onOpenAppSettingsClick = { openAppSystemSettings() },
            onCheckForUpdatesClick = { checkForAppUpdates(manual = true) }
        )

        tabHost = FrameLayout(this)
        val matchParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        tabHost.addView(overviewScreen.rootView, matchParams)
        tabHost.addView(networksScreen.rootView, matchParams)
        tabHost.addView(heatmapScreen.rootView, matchParams)
        tabHost.addView(analyticsScreen.rootView, matchParams)
        tabHost.addView(settingsScreen.rootView, matchParams)

        val navHairline = View(this).apply { setBackgroundColor(WiFiVisionPalette.hairline) }
        bottomNavBar = buildBottomNavBar()

        workspaceContainer.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        workspaceContainer.addView(topHairline, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1).coerceAtLeast(1)))
        workspaceContainer.addView(feedbackWrapper, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        workspaceContainer.addView(tabHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        workspaceContainer.addView(navHairline, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1).coerceAtLeast(1)))
        workspaceContainer.addView(bottomNavBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        rootFrame.addView(authController.rootView, matchParams)
        rootFrame.addView(workspaceContainer, matchParams)

        installWindowInsetsHandling()
        setContentView(rootFrame)
    }

    private fun buildBottomNavBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(WiFiVisionPalette.pureWhite)
            elevation = dpF(8f)
            setPadding(dp(4), dp(5), dp(4), dp(7))
        }

        val specs = listOf(
            Triple(DashboardTab.OVERVIEW, "Overview", R.drawable.ic_nav_overview),
            Triple(DashboardTab.NETWORKS, "Networks", R.drawable.ic_nav_networks),
            Triple(DashboardTab.HEATMAP, "Heatmap", R.drawable.ic_nav_heatmap),
            Triple(DashboardTab.ANALYTICS, "Analytics", R.drawable.ic_nav_analytics),
            Triple(DashboardTab.SETTINGS, "Settings", R.drawable.ic_nav_settings)
        )
        specs.forEach { (tab, label, iconRes) ->
            val ui = buildNavTabItem(tab, label, iconRes)
            navTabs[tab] = ui
            bar.addView(ui.root, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return bar
    }

    private fun buildNavTabItem(tab: DashboardTab, title: String, iconRes: Int): NavTabUi {
        val itemRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(2), dp(3), dp(2), dp(3))
            background = rippleRoundedDrawable(this@MainActivity, 0x00000000, 0x00000000, WiFiVisionPalette.rippleDark, 12, strokeWidthDp = 0)
            setOnClickListener { switchTab(tab) }
        }

        val iconCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = title
        }
        val countBadge = TextView(this).apply {
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(1), dp(4), dp(1))
            visibility = View.GONE
        }
        iconCapsule.addView(icon, LinearLayout.LayoutParams(dp(19), dp(19)))
        iconCapsule.addView(countBadge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(3)
        })

        val label = TextView(this).apply {
            text = title
            textSize = 10.5f
            setPadding(0, dp(2), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        itemRoot.addView(iconCapsule, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        itemRoot.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return NavTabUi(itemRoot, iconCapsule, icon, label, countBadge)
    }

    private fun installWindowInsetsHandling() {
        rootFrame.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                val authBottom = maxOf(systemBars.bottom, ime.bottom)
                authController.applyWindowInsets(systemBars.top, authBottom)

                topBar.setPadding(dp(16), dp(10) + systemBars.top, dp(16), dp(10))
                val imeVisible = ime.bottom > systemBars.bottom + dp(60)
                if (imeVisible) {
                    bottomNavBar.visibility = View.GONE
                    tabHost.setPadding(0, 0, 0, (ime.bottom - systemBars.bottom).coerceAtLeast(0))
                } else {
                    bottomNavBar.visibility = View.VISIBLE
                    bottomNavBar.setPadding(dp(4), dp(5), dp(4), dp(7) + systemBars.bottom)
                    tabHost.setPadding(0, 0, 0, 0)
                }
            } else {
                @Suppress("DEPRECATION")
                val topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                val rawBottom = insets.systemWindowInsetBottom
                authController.applyWindowInsets(topInset, rawBottom)
                topBar.setPadding(dp(16), dp(10) + topInset, dp(16), dp(10))
                if (rawBottom > dp(100)) {
                    bottomNavBar.visibility = View.GONE
                    tabHost.setPadding(0, 0, 0, rawBottom)
                } else {
                    bottomNavBar.visibility = View.VISIBLE
                    bottomNavBar.setPadding(dp(4), dp(5), dp(4), dp(7) + rawBottom)
                    tabHost.setPadding(0, 0, 0, 0)
                }
            }
            insets
        }
    }

    // =========================================================================
    // Session & Workspace Transitions
    // =========================================================================

    private fun showAuthenticationScreen(bannerMessage: String? = null, isError: Boolean = false) {
        cancelMonitoringAutoRefresh()
        workspaceContainer.visibility = View.GONE
        authController.updateBaseUrlDisplay(currentBaseUrl)
        authController.rootView.visibility = View.VISIBLE
        if (bannerMessage != null) {
            authController.showSignIn(bannerMessage, isError)
        }
        cloudExplorerLoaded = false
        heatmapLoaded = false
        rfAnalyticsLoaded = false
        perfLoaded = false
        devicesLoaded = false
        monitoringLoaded = false
        loadedCloudObservations.clear()
        cachedQualityItems = emptyList()
        cachedObsAnalyticsAll = null
        cachedPerfAnalytics = null
    }

    private fun enterWorkspace(session: UserSessionCredential) {
        authController.rootView.visibility = View.GONE
        workspaceContainer.visibility = View.VISIBLE
        userHandleText.text = session.user.email
        settingsScreen.updateUserProfile(session.user)
        settingsScreen.apiUrlInput.setText(currentBaseUrl)

        refreshLocalUiState()
        switchTab(activeTab)
        checkBackendHealth()
        refreshAccountProfile(silent = true)
        refreshOverviewCloudData()
        scheduleMonitoringAutoRefresh()
    }

    private fun switchTab(tab: DashboardTab) {
        activeTab = tab
        overviewScreen.rootView.visibility = if (tab == DashboardTab.OVERVIEW) View.VISIBLE else View.GONE
        networksScreen.rootView.visibility = if (tab == DashboardTab.NETWORKS) View.VISIBLE else View.GONE
        heatmapScreen.rootView.visibility = if (tab == DashboardTab.HEATMAP) View.VISIBLE else View.GONE
        analyticsScreen.rootView.visibility = if (tab == DashboardTab.ANALYTICS) View.VISIBLE else View.GONE
        settingsScreen.rootView.visibility = if (tab == DashboardTab.SETTINGS) View.VISIBLE else View.GONE

        navTabs.forEach { (key, ui) ->
            val selected = key == tab
            if (selected) {
                ui.iconCapsule.background = pillDrawable(this, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
                ui.icon.setColorFilter(WiFiVisionPalette.emeraldDeep)
                ui.label.setTextColor(WiFiVisionPalette.emeraldDeep)
                ui.label.typeface = Typeface.DEFAULT_BOLD
            } else {
                ui.iconCapsule.background = null
                ui.icon.setColorFilter(WiFiVisionPalette.mutedSlate)
                ui.label.setTextColor(WiFiVisionPalette.mutedSlate)
                ui.label.typeface = Typeface.DEFAULT
            }
        }

        if (!authSessionManager.isSignedIn()) return

        when (tab) {
            DashboardTab.NETWORKS -> if (!cloudExplorerLoaded) refreshCloudExplorer(1, false)
            DashboardTab.HEATMAP -> if (!heatmapLoaded) refreshHeatmapData(activeHeatmapBand)
            DashboardTab.ANALYTICS -> {
                if (!rfAnalyticsLoaded) refreshRfAnalyticsData(activeRfBand)
                if (!perfLoaded) refreshPerformanceData(activePerfLimit)
            }
            DashboardTab.SETTINGS -> {
                if (!devicesLoaded) refreshPairedDevices()
                if (!monitoringLoaded) refreshMonitoringSnapshot()
            }
            DashboardTab.OVERVIEW -> Unit
        }
    }

    private fun requireSessionToken(): String? {
        val active = authSessionManager.getActiveSession()
        if (active == null) {
            handleSessionExpired("Your session has expired. Please sign in again.")
            return null
        }
        return active.sessionToken
    }

    private fun handleSessionExpired(message: String) {
        authSessionManager.onUnauthorizedDetected()
        stopLiveMonitoring(feedbackMessage = null)
        showAuthenticationScreen(message, isError = true)
    }

    private fun handleApiFailure(res: ApiResult<*>): String {
        if (res is ApiResult.Unauthorized) {
            handleSessionExpired(res.message)
        }
        return when (res) {
            is ApiResult.Unauthorized -> res.message
            is ApiResult.Forbidden -> res.message
            is ApiResult.RateLimited -> {
                val extra = res.retryAfterSeconds?.let { " Try again in ${it}s." } ?: ""
                "${res.message}$extra"
            }
            is ApiResult.ClientError -> res.message
            is ApiResult.ServerError -> res.message
            is ApiResult.NetworkError -> res.message
            is ApiResult.Success -> ""
        }
    }

    // =========================================================================
    // Cloud Sync Methods
    // =========================================================================

    private fun refreshAccountProfile(silent: Boolean = false) {
        if (!silent) settingsScreen.setProfileLoading(true)
        authSessionManager.refreshProfile(currentBaseUrl) { result ->
            if (!isUiAlive()) return@refreshProfile
            settingsScreen.setProfileLoading(false)
            when (result) {
                is ApiResult.Success -> {
                    userHandleText.text = result.data.email
                    settingsScreen.updateUserProfile(result.data)
                    if (!silent) setGlobalFeedback("Profile synced for ${result.data.email}", FeedbackTone.SUCCESS)
                }
                is ApiResult.Unauthorized -> handleSessionExpired(result.message)
                else -> if (!silent) setGlobalFeedback(handleApiFailure(result), FeedbackTone.WARNING)
            }
        }
    }

    private fun handleLogout(allSessions: Boolean) {
        stopLiveMonitoring(feedbackMessage = null)
        setGlobalFeedback(if (allSessions) "Revoking all sessions..." else "Signing out...", FeedbackTone.INFO)
        val callback: (ApiResult<LogoutResponse>) -> Unit = { res ->
            if (isUiAlive()) {
                val msg = when (res) {
                    is ApiResult.Success -> res.data.message
                    else -> "Signed out on this device."
                }
                showAuthenticationScreen(msg, isError = false)
            }
        }
        if (allSessions) {
            authSessionManager.logoutAllSessions(currentBaseUrl, callback)
        } else {
            authSessionManager.logout(currentBaseUrl, callback)
        }
    }

    private fun refreshOverviewCloudData() {
        val token = requireSessionToken() ?: return
        isOverviewCloudBusy = true
        overviewScreen.setCloudLoading(true)
        updateGlobalLoadingIndicator()

        var pending = 4
        var latestObsStats: ObservationAnalytics? = cachedObsAnalyticsAll
        var latestQuality: NetworkQualityResponse? = null
        var latestPerfStats: PerformanceAnalytics? = cachedPerfAnalytics
        var latestObsList: List<CloudObservation>? = null
        var firstError: String? = null

        fun finishStep() {
            pending--
            if (pending > 0 || !isUiAlive()) return
            isOverviewCloudBusy = false
            overviewScreen.setCloudLoading(false)
            updateGlobalLoadingIndicator()
            val syncMs = if (firstError == null) System.currentTimeMillis() else 0L
            overviewScreen.updateCloudSummary(
                obsStats = latestObsStats,
                quality = latestQuality,
                perfStats = latestPerfStats,
                recentObs = latestObsList,
                lastCloudSyncMs = syncMs,
                errorMessage = firstError
            )
            refreshLocalUiState()
        }

        apiClient.getObservationStats(currentBaseUrl, token, null) { res ->
            if (!isUiAlive()) return@getObservationStats
            when (res) {
                is ApiResult.Success -> {
                    latestObsStats = res.data
                    cachedObsAnalyticsAll = res.data
                }
                else -> firstError = firstError ?: handleApiFailure(res)
            }
            finishStep()
        }

        apiClient.getNetworkQuality(currentBaseUrl, token) { res ->
            if (!isUiAlive()) return@getNetworkQuality
            when (res) {
                is ApiResult.Success -> {
                    latestQuality = res.data
                    cachedQualityItems = res.data.networks
                    networksScreen.mergeQualityScores(res.data.networks)
                }
                else -> firstError = firstError ?: handleApiFailure(res)
            }
            finishStep()
        }

        apiClient.getPerformanceAnalytics(currentBaseUrl, token) { res ->
            if (!isUiAlive()) return@getPerformanceAnalytics
            when (res) {
                is ApiResult.Success -> {
                    latestPerfStats = res.data
                    cachedPerfAnalytics = res.data
                }
                else -> firstError = firstError ?: handleApiFailure(res)
            }
            finishStep()
        }

        apiClient.getObservations(currentBaseUrl, token, page = 1, limit = 20) { res ->
            if (!isUiAlive()) return@getObservations
            when (res) {
                is ApiResult.Success -> latestObsList = res.data.observations
                else -> firstError = firstError ?: handleApiFailure(res)
            }
            finishStep()
        }
    }

    private fun refreshCloudExplorer(page: Int, append: Boolean) {
        val token = requireSessionToken() ?: return
        if (isCloudExplorerBusy) return
        isCloudExplorerBusy = true
        networksScreen.setCloudLoading(true)
        updateGlobalLoadingIndicator()

        val needQuality = !append || cachedQualityItems.isEmpty()
        var pending = if (needQuality) 2 else 1
        var obsResponse: ObservationListResponse? = null
        var qualityItems = cachedQualityItems
        var errMsg: String? = null

        fun finishStep() {
            pending--
            if (pending > 0 || !isUiAlive()) return
            isCloudExplorerBusy = false
            networksScreen.setCloudLoading(false)
            updateGlobalLoadingIndicator()
            cloudExplorerLoaded = true

            if (obsResponse != null) {
                if (!append) loadedCloudObservations.clear()
                val existingIds = loadedCloudObservations.mapTo(HashSet()) { it.id }
                obsResponse!!.observations.forEach { obs ->
                    if (existingIds.add(obs.id)) {
                        loadedCloudObservations.add(obs)
                    }
                }
                networksScreen.setCloudData(
                    observations = loadedCloudObservations.toList(),
                    totalCount = obsResponse!!.totalCount,
                    page = obsResponse!!.page,
                    hasMore = obsResponse!!.hasMore,
                    qualityList = qualityItems,
                    error = errMsg
                )
            } else {
                networksScreen.setCloudData(
                    observations = loadedCloudObservations.toList(),
                    totalCount = loadedCloudObservations.size,
                    page = (page - 1).coerceAtLeast(1),
                    hasMore = false,
                    qualityList = qualityItems,
                    error = errMsg
                )
            }
        }

        apiClient.getObservations(currentBaseUrl, token, page = page, limit = 100) { res ->
            if (!isUiAlive()) return@getObservations
            when (res) {
                is ApiResult.Success -> obsResponse = res.data
                else -> errMsg = errMsg ?: handleApiFailure(res)
            }
            finishStep()
        }

        if (needQuality) {
            apiClient.getNetworkQuality(currentBaseUrl, token) { res ->
                if (!isUiAlive()) return@getNetworkQuality
                when (res) {
                    is ApiResult.Success -> {
                        qualityItems = res.data.networks
                        cachedQualityItems = res.data.networks
                    }
                    else -> errMsg = errMsg ?: handleApiFailure(res)
                }
                finishStep()
            }
        }
    }

    private fun refreshHeatmapData(band: BandFilter) {
        val token = requireSessionToken() ?: return
        isHeatmapBusy = true
        heatmapScreen.setLoading(true)
        updateGlobalLoadingIndicator()

        val apiBand = if (band == BandFilter.ALL) null else band.label
        var pending = 2
        var heatmapResp: HeatmapResponse? = null
        var densityResp: NetworkDensityResponse? = null
        var errMsg: String? = null

        fun finishStep() {
            pending--
            if (pending > 0 || !isUiAlive()) return
            isHeatmapBusy = false
            heatmapScreen.setLoading(false)
            updateGlobalLoadingIndicator()
            heatmapLoaded = true
            heatmapScreen.setUserLocation(sessionController.latestLocation)
            heatmapScreen.setHeatmapData(heatmapResp, densityResp, errMsg)
        }

        apiClient.getHeatmapData(currentBaseUrl, token, apiBand) { res ->
            if (!isUiAlive()) return@getHeatmapData
            when (res) {
                is ApiResult.Success -> heatmapResp = res.data
                else -> errMsg = errMsg ?: handleApiFailure(res)
            }
            finishStep()
        }

        apiClient.getNetworkDensity(currentBaseUrl, token) { res ->
            if (!isUiAlive()) return@getNetworkDensity
            when (res) {
                is ApiResult.Success -> densityResp = res.data
                else -> errMsg = errMsg ?: handleApiFailure(res)
            }
            finishStep()
        }
    }

    private fun refreshRfAnalyticsData(band: BandFilter) {
        val token = requireSessionToken() ?: return
        isRfAnalyticsBusy = true
        analyticsScreen.setRfLoading(true)
        updateGlobalLoadingIndicator()

        val needScopedStats = band != BandFilter.ALL
        var pending = if (needScopedStats) 4 else 3
        var allStats: ObservationAnalytics? = cachedObsAnalyticsAll
        var scopedStats: ObservationAnalytics? = null
        var qualResp: NetworkQualityResponse? = null
        var obsSample: List<CloudObservation> = emptyList()
        var errMsg: String? = null

        fun finishStep() {
            pending--
            if (pending > 0 || !isUiAlive()) return
            isRfAnalyticsBusy = false
            analyticsScreen.setRfLoading(false)
            updateGlobalLoadingIndicator()
            rfAnalyticsLoaded = true
            analyticsScreen.setRfAnalyticsData(
                allBandStats = allStats,
                scopedStats = scopedStats ?: allStats,
                qualityResponse = qualResp,
                sampleObservations = obsSample,
                error = errMsg
            )
        }

        apiClient.getObservationStats(currentBaseUrl, token, null) { res ->
            if (!isUiAlive()) return@getObservationStats
            when (res) {
                is ApiResult.Success -> {
                    allStats = res.data
                    cachedObsAnalyticsAll = res.data
                }
                else -> errMsg = errMsg ?: handleApiFailure(res)
            }
            finishStep()
        }

        if (needScopedStats) {
            apiClient.getObservationStats(currentBaseUrl, token, band.label) { res ->
                if (!isUiAlive()) return@getObservationStats
                when (res) {
                    is ApiResult.Success -> scopedStats = res.data
                    else -> errMsg = errMsg ?: handleApiFailure(res)
                }
                finishStep()
            }
        }

        apiClient.getNetworkQuality(currentBaseUrl, token) { res ->
            if (!isUiAlive()) return@getNetworkQuality
            when (res) {
                is ApiResult.Success -> {
                    qualResp = res.data
                    cachedQualityItems = res.data.networks
                }
                else -> errMsg = errMsg ?: handleApiFailure(res)
            }
            finishStep()
        }

        apiClient.getObservations(currentBaseUrl, token, page = 1, limit = 100) { res ->
            if (!isUiAlive()) return@getObservations
            when (res) {
                is ApiResult.Success -> obsSample = res.data.observations
                else -> errMsg = errMsg ?: handleApiFailure(res)
            }
            finishStep()
        }
    }

    private fun refreshPerformanceData(limit: Int) {
        val token = requireSessionToken() ?: return
        isPerfBusy = true
        analyticsScreen.setPerfLoading(true)
        updateGlobalLoadingIndicator()

        var pending = 3
        var perfStats: PerformanceAnalytics? = cachedPerfAnalytics
        var historyList: List<PerformanceMeasurementRecord> = emptyList()
        var trendList: List<PerformanceTrendPoint> = emptyList()
        var errMsg: String? = null

        fun finishStep() {
            pending--
            if (pending > 0 || !isUiAlive()) return
            isPerfBusy = false
            analyticsScreen.setPerfLoading(false)
            updateGlobalLoadingIndicator()
            perfLoaded = true
            analyticsScreen.setPerformanceData(perfStats, historyList, trendList, errMsg)
        }

        apiClient.getPerformanceAnalytics(currentBaseUrl, token) { res ->
            if (!isUiAlive()) return@getPerformanceAnalytics
            when (res) {
                is ApiResult.Success -> {
                    perfStats = res.data
                    cachedPerfAnalytics = res.data
                }
                else -> errMsg = errMsg ?: handleApiFailure(res)
            }
            finishStep()
        }

        apiClient.getPerformanceHistory(currentBaseUrl, token, limit) { res ->
            if (!isUiAlive()) return@getPerformanceHistory
            when (res) {
                is ApiResult.Success -> historyList = res.data.history
                else -> errMsg = errMsg ?: handleApiFailure(res)
            }
            finishStep()
        }

        apiClient.getPerformanceTrends(currentBaseUrl, token, limit) { res ->
            if (!isUiAlive()) return@getPerformanceTrends
            when (res) {
                is ApiResult.Success -> trendList = res.data.trends
                else -> errMsg = errMsg ?: handleApiFailure(res)
            }
            finishStep()
        }
    }

    private fun handleRunSpeedTestClick() {
        if (isSpeedTestBusy) return
        val token = requireSessionToken() ?: return
        isSpeedTestBusy = true
        analyticsScreen.setSpeedTestRunning(true)
        updateGlobalLoadingIndicator()
        setGlobalFeedback("Running internet speed & packet loss test...", FeedbackTone.INFO)

        apiClient.runPerformanceTest(currentBaseUrl, token) { result ->
            isSpeedTestBusy = false
            if (!isUiAlive()) return@runPerformanceTest
            analyticsScreen.setSpeedTestRunning(false)
            updateGlobalLoadingIndicator()

            if (result is ApiResult.Unauthorized) {
                handleSessionExpired(result.message)
                return@runPerformanceTest
            }

            analyticsScreen.showSpeedTestResult(result)
            if (result is ApiResult.Success) {
                setGlobalFeedback(
                    String.format(Locale.US, "Speed test saved: %.1f Mbps down / %.1f ms latency", result.data.performance.downloadMbps, result.data.performance.latencyMs),
                    FeedbackTone.SUCCESS
                )
                refreshPerformanceData(activePerfLimit)
                refreshOverviewCloudData()
            } else {
                setGlobalFeedback(handleApiFailure(result), FeedbackTone.ERROR)
            }
        }
    }

    private fun refreshPairedDevices() {
        val token = requireSessionToken() ?: return
        isDevicesBusy = true
        settingsScreen.setDevicesLoading(true)
        updateGlobalLoadingIndicator()

        apiClient.listPairedDevices(currentBaseUrl, token) { res ->
            isDevicesBusy = false
            if (!isUiAlive()) return@listPairedDevices
            settingsScreen.setDevicesLoading(false)
            updateGlobalLoadingIndicator()
            devicesLoaded = true

            when (res) {
                is ApiResult.Success -> settingsScreen.updatePairedDevicesList(res.data.devices, null)
                else -> settingsScreen.updatePairedDevicesList(emptyList(), handleApiFailure(res))
            }
        }
    }

    private fun handleGeneratePairingCode(platform: String) {
        val token = requireSessionToken() ?: return
        isDevicesBusy = true
        settingsScreen.setDevicesLoading(true)
        updateGlobalLoadingIndicator()

        apiClient.generatePairingCode(currentBaseUrl, token, platform) { res ->
            isDevicesBusy = false
            if (!isUiAlive()) return@generatePairingCode
            settingsScreen.setDevicesLoading(false)
            updateGlobalLoadingIndicator()

            when (res) {
                is ApiResult.Success -> {
                    settingsScreen.showGeneratedPairingCode(res.data)
                    setGlobalFeedback("Generated $platform pairing code (${res.data.pairingCode})", FeedbackTone.SUCCESS)
                }
                else -> setGlobalFeedback(handleApiFailure(res), FeedbackTone.ERROR)
            }
        }
    }

    private fun handleRevokeDevice(deviceId: String) {
        val token = requireSessionToken() ?: return
        isDevicesBusy = true
        settingsScreen.setDevicesLoading(true)

        apiClient.revokePairedDevice(currentBaseUrl, token, deviceId) { res ->
            isDevicesBusy = false
            if (!isUiAlive()) return@revokePairedDevice
            settingsScreen.setDevicesLoading(false)

            when (res) {
                is ApiResult.Success -> {
                    setGlobalFeedback(res.data.message, FeedbackTone.SUCCESS)
                    if (credentialStore.getActiveCredential()?.deviceId == deviceId) {
                        credentialStore.clearCredential()
                        refreshLocalUiState()
                    }
                    refreshPairedDevices()
                }
                else -> setGlobalFeedback(handleApiFailure(res), FeedbackTone.ERROR)
            }
        }
    }

    private fun handleQuickPairSelf(deviceName: String?) {
        if (isPairingBusy) return
        val token = requireSessionToken() ?: return
        val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
        if (validation !is UrlValidationResult.Valid) {
            setUploadFeedback((validation as UrlValidationResult.Invalid).reason, FeedbackTone.ERROR)
            return
        }

        isPairingBusy = true
        refreshLocalUiState()
        setUploadFeedback("Generating Android pairing code & pairing device...", FeedbackTone.INFO)

        apiClient.generatePairingCode(validation.normalizedUrl, token, "android") { codeRes ->
            if (!isUiAlive()) return@generatePairingCode
            when (codeRes) {
                is ApiResult.Success -> {
                    val label = deviceName ?: Build.MODEL
                    apiClient.pairDevice(validation.normalizedUrl, codeRes.data.pairingCode, label) { pairRes ->
                        isPairingBusy = false
                        if (!isUiAlive()) return@pairDevice
                        handlePairAttemptOutcome(pairRes)
                    }
                }
                else -> {
                    isPairingBusy = false
                    setUploadFeedback("Quick pair failed: ${handleApiFailure(codeRes)}", FeedbackTone.ERROR)
                    refreshLocalUiState()
                }
            }
        }
    }

    private fun refreshMonitoringSnapshot() {
        val token = authSessionManager.getActiveSession()?.sessionToken ?: ""
        if (isMonitoringBusy) return
        isMonitoringBusy = true
        settingsScreen.setMonitoringLoading(true)

        apiClient.fetchSystemMonitoringSnapshot(currentBaseUrl, token) { snapshot ->
            isMonitoringBusy = false
            if (!isUiAlive()) return@fetchSystemMonitoringSnapshot
            settingsScreen.setMonitoringLoading(false)
            monitoringLoaded = true
            settingsScreen.updateMonitoringSnapshot(snapshot)
            updateHealthUi(snapshot.health)
        }
    }

    private fun scheduleMonitoringAutoRefresh() {
        cancelMonitoringAutoRefresh()
        if (!isActivityStarted || !authSessionManager.isSignedIn() || !preferences.isMonitoringAutoRefresh()) {
            return
        }
        val intervalMs = preferences.getMonitoringIntervalSeconds() * 1000L
        val task = object : Runnable {
            override fun run() {
                if (isActivityStarted && authSessionManager.isSignedIn() && preferences.isMonitoringAutoRefresh()) {
                    refreshMonitoringSnapshot()
                    handler.postDelayed(this, intervalMs)
                }
            }
        }
        monitoringAutoRunnable = task
        handler.postDelayed(task, intervalMs)
    }

    private fun cancelMonitoringAutoRefresh() {
        monitoringAutoRunnable?.let { handler.removeCallbacks(it) }
        monitoringAutoRunnable = null
    }

    // =========================================================================
    // Preserved Local Wi-Fi Scan, GPS, Queue & Device Credential Flow
    // =========================================================================

    private fun refreshLocalUiState() {
        val currentWifi = wifiMonitor.getCurrentWifiInfo()
        val nowMs = System.currentTimeMillis()
        sessionController.onMonitorUpdated(currentWifi, nowMs)

        val pendingCount = observationQueue.logic.size()
        val eligibleUploadCount = sessionController.countEligibleLocalObservations(nowMs)
        val hasCandidates = pendingCount > 0 || eligibleUploadCount > 0

        overviewScreen.updateActiveWifi(currentWifi, isWifiLiveMonitoring)
        overviewScreen.updatePermissionState(permissionManager)
        overviewScreen.updateLocalMetrics(
            nearby = sessionController.lastScanNetworks,
            stats = sessionController.lastScanStats,
            pendingCount = pendingCount,
            uploadedCount = observationQueue.getUploadedCount(),
            eligibleUploadCount = eligibleUploadCount,
            lastSyncMs = preferences.getLastSyncEpochMs()
        )
        overviewScreen.scanButton.isEnabled = !sessionController.isScanInProgress
        overviewScreen.scanButton.text = if (sessionController.isScanInProgress) "Scanning Once..." else "Scan Once"
        overviewScreen.uploadButton.isEnabled = !isUploading && hasCandidates

        networksScreen.updateLivePermissionsAndOutcome(permissionManager, outcome = null)
        networksScreen.updateLiveControls(
            isScanning = sessionController.isScanInProgress,
            isMonitoring = isWifiLiveMonitoring,
            monitorIntervalSec = liveMonitorIntervalSec,
            isUploading = isUploading,
            eligibleUploadCount = eligibleUploadCount,
            pendingQueueCount = pendingCount,
            hasUploadCandidates = hasCandidates
        )

        settingsScreen.updatePermissionsCard(permissionManager)
        settingsScreen.updateThisDevicePairing(
            activeCred = credentialStore.getActiveCredential(),
            storedCred = credentialStore.loadCredential(),
            isBusy = isPairingBusy
        )
        settingsScreen.updateQueueStats(
            pending = pendingCount,
            uploaded = observationQueue.getUploadedCount(),
            canUpload = hasCandidates,
            isUploading = isUploading
        )

        updateNavBadges()
        updateGlobalLoadingIndicator()
    }

    private fun hasUploadCandidates(): Boolean {
        val nowMs = System.currentTimeMillis()
        return observationQueue.logic.size() > 0 ||
            sessionController.countEligibleLocalObservations(nowMs) > 0
    }

    private fun updateNavBadges() {
        val networkCount = sessionController.lastScanNetworks.size
        navTabs[DashboardTab.NETWORKS]?.countBadge?.apply {
            if (networkCount > 0) {
                text = networkCount.toString()
                setTextColor(WiFiVisionPalette.emeraldDeep)
                background = pillDrawable(this@MainActivity, WiFiVisionPalette.pureWhite, WiFiVisionPalette.emeraldBorder)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        val pendingCount = observationQueue.logic.size()
        navTabs[DashboardTab.SETTINGS]?.countBadge?.apply {
            if (pendingCount > 0) {
                text = pendingCount.toString()
                setTextColor(WiFiVisionPalette.deepGold)
                background = pillDrawable(this@MainActivity, WiFiVisionPalette.softGoldBg, WiFiVisionPalette.goldBorder)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
    }

    private fun updateGlobalLoadingIndicator() {
        val busy = sessionController.isScanInProgress ||
            isUploading ||
            isPairingBusy ||
            isHealthCheckBusy ||
            isGpsBusy ||
            isOverviewCloudBusy ||
            isCloudExplorerBusy ||
            isHeatmapBusy ||
            isRfAnalyticsBusy ||
            isPerfBusy ||
            isSpeedTestBusy ||
            isDevicesBusy
        globalFeedbackProgress.visibility = if (busy) View.VISIBLE else View.GONE
        globalFeedbackDot.visibility = if (busy) View.GONE else View.VISIBLE
    }

    private fun setGlobalFeedback(message: String, tone: FeedbackTone) {
        globalBannerText.text = message
        val (tc, bg, bc, dc) = when (tone) {
            FeedbackTone.INFO -> arrayOf(WiFiVisionPalette.bodySlate, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline, WiFiVisionPalette.jade)
            FeedbackTone.SUCCESS -> arrayOf(WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder, WiFiVisionPalette.jade)
            FeedbackTone.WARNING -> arrayOf(WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder, WiFiVisionPalette.amberText)
            FeedbackTone.ERROR -> arrayOf(WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder, WiFiVisionPalette.crimsonText)
        }
        globalBannerText.setTextColor(tc)
        globalFeedbackContainer.background = roundedDrawable(this, bg, bc, 12)
        globalFeedbackDot.background = pillDrawable(this, dc, dc, 0)
        updateGlobalLoadingIndicator()
    }

    private fun setScanFeedback(message: String, tone: FeedbackTone = FeedbackTone.INFO) {
        overviewScreen.updateScanStatus(message)
        setGlobalFeedback(message, tone)
    }

    private fun setUploadFeedback(message: String, tone: FeedbackTone = FeedbackTone.INFO) {
        val (tc, bg, bc) = when (tone) {
            FeedbackTone.INFO -> Triple(WiFiVisionPalette.bodySlate, WiFiVisionPalette.warmIvory, WiFiVisionPalette.hairline)
            FeedbackTone.SUCCESS -> Triple(WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
            FeedbackTone.WARNING -> Triple(WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
            FeedbackTone.ERROR -> Triple(WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
        }
        settingsScreen.setUploadBanner(message, tc, bg, bc)
        setGlobalFeedback(message, tone)
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

        refreshLocalUiState()
        val action = pendingAction
        pendingAction = PendingPermissionAction.NONE

        if (permissionManager.hasWifiScanPermissions()) {
            when (action) {
                PendingPermissionAction.SCAN_ONLY -> executeScanOnce(triggeredByLiveMonitor = false)
                PendingPermissionAction.GPS_ONLY -> fetchGpsMeasurement {}
                PendingPermissionAction.START_MONITOR -> {
                    if (!permissionManager.isWifiEnabled()) {
                        setScanFeedback("Please enable Wi-Fi before starting Live Monitoring", FeedbackTone.WARNING)
                    } else if (!permissionManager.isLocationEnabled()) {
                        setScanFeedback("Please enable device location services for Live Monitoring", FeedbackTone.WARNING)
                    } else {
                        startLiveMonitoring()
                    }
                }
                PendingPermissionAction.HEATMAP_LOCATE -> fetchGpsMeasurement { loc ->
                    if (loc != null) {
                        heatmapScreen.setUserLocation(loc)
                        heatmapScreen.mapView.centerOn(loc.latitude, loc.longitude, 15.5)
                    }
                }
                PendingPermissionAction.NONE -> setGlobalFeedback("Permissions granted.", FeedbackTone.SUCCESS)
            }
        } else {
            setScanFeedback(permissionManager.getPermissionStatus(), FeedbackTone.WARNING)
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_DENIED }) {
                setUploadFeedback(
                    "Permission denied. Grant location & nearby Wi-Fi permissions to capture measurements.",
                    FeedbackTone.WARNING
                )
            }
        }
    }

    private fun handleScanOnlyClick() {
        if (sessionController.isScanInProgress) return
        if (!ensureRuntimePermissions(PendingPermissionAction.SCAN_ONLY)) return
        if (!permissionManager.isWifiEnabled()) {
            setScanFeedback("Please enable Wi-Fi before scanning", FeedbackTone.WARNING)
            return
        }
        if (!permissionManager.isLocationEnabled()) {
            setScanFeedback("Please enable device location services", FeedbackTone.WARNING)
            return
        }
        executeScanOnce(triggeredByLiveMonitor = false)
    }

    private fun handleRefreshGpsClick() {
        if (sessionController.isScanInProgress || isGpsBusy) return
        if (!ensureRuntimePermissions(PendingPermissionAction.GPS_ONLY)) return
        fetchGpsMeasurement {}
    }

    private fun handleHeatmapLocateMeClick() {
        if (isGpsBusy) return
        if (!ensureRuntimePermissions(PendingPermissionAction.HEATMAP_LOCATE)) return
        setGlobalFeedback("Acquiring device GPS location for map...", FeedbackTone.INFO)
        fetchGpsMeasurement { loc ->
            if (!isUiAlive()) return@fetchGpsMeasurement
            if (loc != null) {
                heatmapScreen.setUserLocation(loc)
                heatmapScreen.mapView.centerOn(loc.latitude, loc.longitude, 15.5)
                setGlobalFeedback(
                    String.format(Locale.US, "Centered map on %.5f, %.5f", loc.latitude, loc.longitude),
                    FeedbackTone.SUCCESS
                )
            } else {
                setGlobalFeedback("Device GPS fix unavailable.", FeedbackTone.WARNING)
            }
        }
    }

    private fun toggleMonitoring() {
        if (isWifiLiveMonitoring || wifiMonitor.isMonitoring) {
            stopLiveMonitoring("Live Monitoring stopped")
            return
        }
        if (!ensureRuntimePermissions(PendingPermissionAction.START_MONITOR)) return
        if (!permissionManager.isWifiEnabled()) {
            setScanFeedback("Please enable Wi-Fi before starting Live Monitoring", FeedbackTone.WARNING)
            return
        }
        if (!permissionManager.isLocationEnabled()) {
            setScanFeedback("Please enable device location services for Live Monitoring", FeedbackTone.WARNING)
            return
        }
        startLiveMonitoring()
    }

    private fun handleLiveMonitorIntervalChanged(seconds: Int) {
        liveMonitorIntervalSec = seconds
        preferences.setWifiMonitorIntervalSeconds(seconds)
        if (isWifiLiveMonitoring) {
            val activeToken = liveMonitorGeneration
            scheduleNextLiveMonitorTick(activeToken)
            setScanFeedback("Live Monitoring interval set to Every ${seconds}s", FeedbackTone.INFO)
        } else {
            refreshLocalUiState()
        }
    }

    private fun handleClearSessionNetworksClick() {
        sessionController.clearSessionNetworks()
        networksScreen.updateLiveNetworks(emptyList(), sessionController.lastScanStats)
        refreshLocalUiState()
        setScanFeedback("Cleared local unique network list (cloud history unchanged)", FeedbackTone.INFO)
    }

    private fun startLiveMonitoring() {
        if (isWifiLiveMonitoring) {
            setScanFeedback("Live Monitoring is already ON", FeedbackTone.INFO)
            return
        }

        val loopToken = ++liveMonitorGeneration
        isWifiLiveMonitoring = true
        lastLiveMonitorThrottled = false
        sessionController.onMonitoringStarted(true)

        // Track active Wi-Fi connection changes only — never launch periodic scans from NetworkCallback.
        wifiMonitor.startMonitoring { details ->
            if (!isUiAlive() || !isWifiLiveMonitoring) return@startMonitoring
            sessionController.onMonitorUpdated(details, System.currentTimeMillis())
            refreshLocalUiState()
        }

        refreshLocalUiState()
        setScanFeedback(
            "Live Monitoring ON (every ${liveMonitorIntervalSec}s • auto-upload OFF)",
            FeedbackTone.SUCCESS
        )

        if (!sessionController.isScanInProgress) {
            executeScanOnce(triggeredByLiveMonitor = true, monitorLoopToken = loopToken)
        } else {
            scheduleNextLiveMonitorTick(loopToken)
        }
    }

    private fun stopLiveMonitoring(feedbackMessage: String?) {
        liveMonitorGeneration++
        liveMonitorRunnable?.let { handler.removeCallbacks(it) }
        liveMonitorRunnable = null

        val wasMonitoringScanRunning = activeScanFromMonitor && sessionController.isScanInProgress
        isWifiLiveMonitoring = false
        lastLiveMonitorThrottled = false
        wifiMonitor.stopMonitoring()
        sessionController.isMonitoringActive = false

        if (wasMonitoringScanRunning) {
            cancelActiveScanAndGps(cancelledMessage = null)
        } else {
            refreshLocalUiState()
        }

        if (feedbackMessage != null && isUiAlive()) {
            setScanFeedback(feedbackMessage, FeedbackTone.INFO)
        }
    }

    private fun scheduleNextLiveMonitorTick(loopToken: Long, overrideDelayMs: Long? = null) {
        liveMonitorRunnable?.let { handler.removeCallbacks(it) }
        liveMonitorRunnable = null

        if (!isUiAlive() || !isActivityStarted || !isWifiLiveMonitoring || loopToken != liveMonitorGeneration) {
            return
        }

        val baseDelayMs = liveMonitorIntervalSec * 1000L
        val delayMs = overrideDelayMs ?: if (lastLiveMonitorThrottled) {
            maxOf(baseDelayMs, LIVE_MONITOR_THROTTLED_MIN_INTERVAL_MS)
        } else {
            baseDelayMs
        }

        val task = Runnable {
            liveMonitorRunnable = null
            if (!isUiAlive() || !isActivityStarted || !isWifiLiveMonitoring || loopToken != liveMonitorGeneration) {
                return@Runnable
            }
            if (!permissionManager.canScanWifi()) {
                stopLiveMonitoring("Live Monitoring stopped: ${permissionManager.getPermissionStatus()}")
                return@Runnable
            }
            if (!sessionController.isScanInProgress) {
                executeScanOnce(triggeredByLiveMonitor = true, monitorLoopToken = loopToken)
            } else {
                scheduleNextLiveMonitorTick(loopToken, overrideDelayMs = 5_000L)
            }
        }
        liveMonitorRunnable = task
        handler.postDelayed(task, delayMs)
    }

    private fun fetchLocationForScanIfPermitted(onComplete: (ObservationLocation?) -> Unit) {
        if (!permissionManager.hasFineLocationPermission() || !permissionManager.isLocationEnabled()) {
            onComplete(null)
            return
        }
        fetchGpsMeasurement(
            timeoutMs = SCAN_GPS_TIMEOUT_MS,
            maxCacheAgeMs = SCAN_GPS_CACHE_AGE_MS,
            onComplete = onComplete
        )
    }

    private fun fetchGpsMeasurement(
        timeoutMs: Long = 8_000L,
        maxCacheAgeMs: Long = 45_000L,
        onComplete: (ObservationLocation?) -> Unit
    ) {
        val token = ++gpsGeneration
        isGpsBusy = true
        overviewScreen.updateGpsAcquiring()
        overviewScreen.refreshGpsButton.isEnabled = false
        updateGlobalLoadingIndicator()

        locationProvider.fetchCurrentLocation(timeoutMs = timeoutMs, maxCacheAgeMs = maxCacheAgeMs) { result ->
            if (!isUiAlive() || token != gpsGeneration) return@fetchCurrentLocation
            isGpsBusy = false
            overviewScreen.refreshGpsButton.isEnabled = true
            updateGlobalLoadingIndicator()

            when (result) {
                is LocationFixResult.Available -> {
                    val snapshot = result.snapshot
                    sessionController.onGpsUpdated(snapshot.location, snapshot.location.accuracyMeters, snapshot.capturedAtMs)
                    overviewScreen.updateGpsSnapshot(snapshot)
                    heatmapScreen.setUserLocation(snapshot.location)
                    onComplete(snapshot.location)
                }
                is LocationFixResult.Unavailable -> {
                    sessionController.onGpsUpdated(null, null, System.currentTimeMillis())
                    overviewScreen.updateGpsUnavailable(result.reason)
                    onComplete(null)
                }
            }
        }
    }

    private fun executeScanOnce(
        triggeredByLiveMonitor: Boolean = false,
        monitorLoopToken: Long = liveMonitorGeneration
    ) {
        if (sessionController.isScanInProgress) return
        val token = ++scanGeneration
        activeScanFromMonitor = triggeredByLiveMonitor
        sessionController.isScanInProgress = true
        refreshLocalUiState()
        setScanFeedback(
            if (triggeredByLiveMonitor) "Live Monitoring: acquiring GPS & scanning Wi-Fi..." else "Scan Once: acquiring GPS & scanning nearby Wi-Fi...",
            FeedbackTone.INFO
        )

        fetchLocationForScanIfPermitted { measurementLocation ->
            if (!isUiAlive() || !isActivityStarted || token != scanGeneration) return@fetchLocationForScanIfPermitted
            if (triggeredByLiveMonitor && (!isWifiLiveMonitoring || monitorLoopToken != liveMonitorGeneration)) {
                activeScanFromMonitor = false
                sessionController.isScanInProgress = false
                refreshLocalUiState()
                return@fetchLocationForScanIfPermitted
            }

            scanner.requestScan { outcome ->
                if (!isUiAlive() || token != scanGeneration) return@requestScan
                activeScanFromMonitor = false

                val scannedAtMs = System.currentTimeMillis()
                val activeWifi = wifiMonitor.getCurrentWifiInfo()
                val mergeRes = sessionController.onScanCompleted(
                    networks = outcome.networks,
                    activeWifi = activeWifi,
                    location = measurementLocation,
                    scannedAtMs = scannedAtMs,
                    isFresh = outcome.isFresh,
                    throttled = outcome.throttled
                )
                lastLiveMonitorThrottled = outcome.throttled

                networksScreen.updateLiveNetworks(sessionController.lastScanNetworks, sessionController.lastScanStats)
                networksScreen.updateLivePermissionsAndOutcome(permissionManager, outcome)
                refreshLocalUiState()

                val modeLabel = if (triggeredByLiveMonitor) "Live Monitor" else "Scan Once"
                val locSuffix = if (measurementLocation == null) " • GPS unavailable" else ""
                val statsSummary = "${mergeRes.totalUniqueCount} unique (+${mergeRes.newlyDetectedCount} new, ${mergeRes.updatedExistingCount} updated)"
                val statusBanner = if (outcome.throttled) {
                    "$modeLabel: ${outcome.message} • $statsSummary (${UiFormatters.formatTimeOfDay(scannedAtMs)}$locSuffix)"
                } else {
                    "$modeLabel: $statsSummary (${UiFormatters.formatTimeOfDay(scannedAtMs)}$locSuffix • local only)"
                }
                val tone = when {
                    outcome.throttled -> FeedbackTone.WARNING
                    outcome.networks.isNotEmpty() || outcome.isFresh -> FeedbackTone.SUCCESS
                    else -> FeedbackTone.WARNING
                }
                setScanFeedback(statusBanner, tone)

                if (triggeredByLiveMonitor && isWifiLiveMonitoring && isActivityStarted && monitorLoopToken == liveMonitorGeneration) {
                    scheduleNextLiveMonitorTick(monitorLoopToken)
                }
            }
        }
    }

    private fun cancelActiveScanAndGps(cancelledMessage: String?) {
        scanGeneration++
        gpsGeneration++
        activeScanFromMonitor = false
        scanner.cancelScan()
        locationProvider.cancel()

        val hadPending = sessionController.isScanInProgress
        sessionController.isScanInProgress = false
        isGpsBusy = false
        overviewScreen.refreshGpsButton.isEnabled = true

        if (hadPending && cancelledMessage != null && isUiAlive()) {
            setScanFeedback(cancelledMessage, FeedbackTone.WARNING)
        }
        refreshLocalUiState()
    }

    private fun handleUploadObservationsClick() {
        if (isUploading) {
            setUploadFeedback("Upload already in progress...", FeedbackTone.INFO)
            return
        }
        val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
        if (validation !is UrlValidationResult.Valid) {
            setUploadFeedback((validation as UrlValidationResult.Invalid).reason, FeedbackTone.ERROR)
            switchTab(DashboardTab.SETTINGS)
            return
        }
        if (credentialStore.getActiveCredential() == null) {
            setUploadFeedback("Device pairing required before uploading. Use Quick Pair or enter a pairing code in Settings.", FeedbackTone.WARNING)
            switchTab(DashboardTab.SETTINGS)
            return
        }

        val nowMs = System.currentTimeMillis()
        if (observationQueue.logic.globalPauseUntilMs > nowMs) {
            val remSec = ((observationQueue.logic.globalPauseUntilMs - nowMs) / 1000L).coerceAtLeast(1L)
            setUploadFeedback("Rate-limit cooldown active (${remSec}s remaining).", FeedbackTone.WARNING)
            return
        }

        val preview = sessionController.prepareUploadConfirmation(nowMs)
        if (preview == null) {
            val msg = if (sessionController.lastScanNetworks.isNotEmpty() || sessionController.latestWifiDetails?.connected == true) {
                "All scanned networks are unchanged since recent sync — nothing new to upload."
            } else {
                "No scanned or queued observations ready. Tap Scan Once first."
            }
            setUploadFeedback(msg, FeedbackTone.INFO)
            return
        }
        showUploadConfirmationDialog(validation.normalizedUrl, preview)
    }

    private fun showUploadConfirmationDialog(targetBaseUrl: String, preview: UploadConfirmationPreview) {
        dismissUploadDialog()
        val locSummary = if (preview.hasLocation) {
            val acc = preview.locationAccuracyMeters?.let { String.format(Locale.US, "±%.1f m", it) } ?: "accuracy n/a"
            "Device GPS measurement attached ($acc)"
        } else {
            "GPS unavailable (uploads without coordinates)"
        }

        val msg = buildString {
            appendLine("Target: $targetBaseUrl/api/observations")
            appendLine("New / changed observations: ${preview.readyLocalCount}")
            appendLine("Existing pending queue: ${preview.existingPendingQueueCount}")
            if (preview.skippedDuplicateCount > 0) {
                appendLine("Duplicates / unchanged skipped: ${preview.skippedDuplicateCount}")
            }
            if (preview.invalidSkippedCount > 0) {
                appendLine("Invalid filtered out: ${preview.invalidSkippedCount}")
            }
            appendLine(locSummary)
            appendLine()
            appendLine("Preview:")
            preview.previewLines.forEach { appendLine("• $it") }
            val pool = if (preview.readyLocalCount > 0) preview.readyLocalCount else preview.existingPendingQueueCount
            if (pool > preview.previewLines.size) {
                appendLine("• ...and ${pool - preview.previewLines.size} more")
            }
        }.trim()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Confirm Observation Upload")
            .setMessage(msg)
            .setPositiveButton("UPLOAD NOW") { _, _ ->
                uploadConfirmDialog = null
                val confirmMs = System.currentTimeMillis()
                val outcome = sessionController.confirmAndEnqueueUpload(true, confirmMs)
                outcome.enqueueSummary?.let { summary ->
                    observationQueue.persist()
                    setUploadFeedback(buildEnqueueSummaryMessage(summary), FeedbackTone.INFO)
                }
                refreshLocalUiState()
                if (outcome.shouldDrainQueue && isActivityStarted) {
                    scheduleQueueDrain(0L)
                }
            }
            .setNegativeButton("CANCEL") { _, _ ->
                uploadConfirmDialog = null
                sessionController.confirmAndEnqueueUpload(false, System.currentTimeMillis())
                setUploadFeedback("Upload cancelled. Observations kept local only.", FeedbackTone.INFO)
            }
            .setOnCancelListener {
                uploadConfirmDialog = null
                sessionController.confirmAndEnqueueUpload(false, System.currentTimeMillis())
            }
            .create()

        uploadConfirmDialog = dialog
        dialog.show()
    }

    private fun dismissUploadDialog() {
        uploadConfirmDialog?.let { if (it.isShowing) it.dismiss() }
        uploadConfirmDialog = null
    }

    private fun buildEnqueueSummaryMessage(summary: EnqueueSummary): String {
        val parts = mutableListOf("Queued ${summary.acceptedCount}")
        if (summary.skippedDuplicateCount > 0) parts.add("${summary.skippedDuplicateCount} duplicates skipped")
        if (summary.skippedBatchCapCount > 0) parts.add("${summary.skippedBatchCapCount} rate-cap deferred")
        if (summary.skippedInvalidCount > 0) parts.add("${summary.skippedInvalidCount} invalid ignored")
        if (summary.droppedOldestCount > 0) parts.add("${summary.droppedOldestCount} oldest evicted")
        return parts.joinToString(" • ")
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
            } else if (authSessionManager.isSignedIn()) {
                refreshOverviewCloudData()
                if (cloudExplorerLoaded) refreshCloudExplorer(1, false)
            }
            refreshLocalUiState()
            return
        }

        val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
        if (validation !is UrlValidationResult.Valid) {
            setUploadFeedback((validation as UrlValidationResult.Invalid).reason, FeedbackTone.ERROR)
            return
        }

        val activeCred = credentialStore.getActiveCredential()
        if (activeCred == null) {
            setUploadFeedback("Device credential missing or expired. Re-pair device before uploading.", FeedbackTone.WARNING)
            refreshLocalUiState()
            return
        }

        isUploading = true
        refreshLocalUiState()
        setUploadFeedback("Uploading (${nextItem.payload.ssid ?: "Hidden Network"})...", FeedbackTone.INFO)

        apiClient.uploadObservation(validation.normalizedUrl, nextItem.payload, activeCred.deviceToken) { result ->
            isUploading = false
            val completedMs = System.currentTimeMillis()
            val bannerMessage: String
            var tone = FeedbackTone.INFO
            var stopForRePair = false

            when (result) {
                is UploadAttemptResult.Success -> {
                    observationQueue.logic.markSuccess(nextItem.id, completedMs)
                    val totalUploaded = observationQueue.incrementUploadedCount()
                    observationQueue.persist()
                    preferences.setLastSyncEpochMs(completedMs)
                    tone = FeedbackTone.SUCCESS
                    bannerMessage = "Uploaded observation #$totalUploaded (${result.message})"
                }
                is UploadAttemptResult.Unauthorized -> {
                    credentialStore.clearCredential()
                    stopForRePair = true
                    tone = FeedbackTone.ERROR
                    bannerMessage = "Device upload unauthorized (${result.message}). Queue preserved; re-pair device."
                }
                is UploadAttemptResult.ClientRejected -> {
                    observationQueue.logic.markClientRejected(nextItem.id, completedMs)
                    observationQueue.persist()
                    tone = FeedbackTone.WARNING
                    bannerMessage = "Dropped invalid observation (HTTP ${result.statusCode}): ${result.message}"
                }
                is UploadAttemptResult.RateLimited -> {
                    val pauseMs = observationQueue.logic.markRateLimited(result.retryAfterSeconds, completedMs)
                    observationQueue.persist()
                    tone = FeedbackTone.WARNING
                    bannerMessage = "Rate limit (429). Paused ${pauseMs / 1000L}s before retry."
                }
                is UploadAttemptResult.TransientFailure -> {
                    val kept = observationQueue.logic.markTransientFailure(nextItem.id, completedMs)
                    observationQueue.persist()
                    tone = if (kept) FeedbackTone.WARNING else FeedbackTone.ERROR
                    bannerMessage = if (kept) {
                        "Upload failed (${result.message}); kept in queue for retry."
                    } else {
                        "Dropped observation after max retries (${result.message})."
                    }
                }
            }

            if (!isUiAlive()) return@uploadObservation
            setUploadFeedback(bannerMessage, tone)
            refreshLocalUiState()

            if (isActivityStarted && !stopForRePair) {
                val wakeDelay = observationQueue.logic.nextWakeDelayMs(completedMs)
                if (wakeDelay != null) {
                    scheduleQueueDrain(wakeDelay)
                } else if (observationQueue.logic.size() == 0 && authSessionManager.isSignedIn()) {
                    refreshOverviewCloudData()
                    if (cloudExplorerLoaded) refreshCloudExplorer(1, false)
                    if (heatmapLoaded) refreshHeatmapData(activeHeatmapBand)
                }
            }
        }
    }

    private fun handlePairWithCode(rawCode: String, deviceName: String?) {
        if (isPairingBusy) return
        val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
        if (validation !is UrlValidationResult.Valid) {
            setUploadFeedback((validation as UrlValidationResult.Invalid).reason, FeedbackTone.ERROR)
            return
        }

        isPairingBusy = true
        refreshLocalUiState()
        setUploadFeedback("Pairing device securely with backend...", FeedbackTone.INFO)
        val label = deviceName ?: Build.MODEL

        apiClient.pairDevice(validation.normalizedUrl, rawCode, label) { res ->
            isPairingBusy = false
            if (!isUiAlive()) return@pairDevice
            handlePairAttemptOutcome(res)
        }
    }

    private fun handlePairAttemptOutcome(result: PairingAttemptResult) {
        when (result) {
            is PairingAttemptResult.Success -> {
                val saved = credentialStore.saveCredential(result.credential)
                settingsScreen.clearPairingCodeInput()
                setUploadFeedback(
                    if (saved) "Device paired (${result.credential.deviceName}) via Android Keystore." else "Paired, but Keystore encryption failed.",
                    if (saved) FeedbackTone.SUCCESS else FeedbackTone.WARNING
                )
                if (authSessionManager.isSignedIn()) {
                    refreshPairedDevices()
                }
            }
            is PairingAttemptResult.Rejected -> setUploadFeedback("Pairing rejected: ${result.message}", FeedbackTone.ERROR)
            is PairingAttemptResult.NetworkError -> setUploadFeedback("Pairing failed: ${result.message}", FeedbackTone.ERROR)
        }
        refreshLocalUiState()
    }

    private fun handleRotateCredentialClick() {
        if (isPairingBusy) return
        val activeCred = credentialStore.getActiveCredential() ?: run {
            setUploadFeedback("Pair this phone first before rotating credentials.", FeedbackTone.WARNING)
            return
        }
        val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
        if (validation !is UrlValidationResult.Valid) {
            setUploadFeedback((validation as UrlValidationResult.Invalid).reason, FeedbackTone.ERROR)
            return
        }

        isPairingBusy = true
        refreshLocalUiState()
        setUploadFeedback("Rotating scoped device upload credential...", FeedbackTone.INFO)

        apiClient.rotateDeviceCredential(validation.normalizedUrl, activeCred.deviceToken) { res ->
            isPairingBusy = false
            if (!isUiAlive()) return@rotateDeviceCredential
            when (res) {
                is PairingAttemptResult.Success -> {
                    credentialStore.saveCredential(res.credential)
                    setUploadFeedback("Device credential rotated and encrypted in Android Keystore.", FeedbackTone.SUCCESS)
                }
                is PairingAttemptResult.Rejected -> {
                    if (res.statusCode == 401) credentialStore.clearCredential()
                    setUploadFeedback("Rotation rejected: ${res.message}", FeedbackTone.ERROR)
                }
                is PairingAttemptResult.NetworkError -> setUploadFeedback("Rotation failed: ${res.message}", FeedbackTone.ERROR)
            }
            refreshLocalUiState()
        }
    }

    private fun handleUnpairClick() {
        if (isPairingBusy) return
        credentialStore.clearCredential()
        setUploadFeedback("Local device credential cleared.", FeedbackTone.INFO)
        refreshLocalUiState()
    }

    private fun applyApiBaseUrl(rawUrl: String) {
        when (val validation = ApiContractUtils.validateBaseUrl(rawUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)) {
            is UrlValidationResult.Valid -> {
                currentBaseUrl = validation.normalizedUrl
                preferences.saveBaseUrl(currentBaseUrl)
                settingsScreen.apiUrlInput.setText(currentBaseUrl)
                authController.updateBaseUrlDisplay(currentBaseUrl)
                setUploadFeedback("Backend URL saved: $currentBaseUrl", FeedbackTone.SUCCESS)
                checkBackendHealth()
            }
            is UrlValidationResult.Invalid -> {
                setBackendPill("URL INVALID", WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
                setUploadFeedback(validation.reason, FeedbackTone.ERROR)
            }
        }
    }

    private fun checkBackendHealth() {
        val validation = ApiContractUtils.validateBaseUrl(currentBaseUrl, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
        if (validation !is UrlValidationResult.Valid) {
            setBackendPill("URL INVALID", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
            return
        }
        currentBaseUrl = validation.normalizedUrl
        isHealthCheckBusy = true
        updateHealthUi(null)
        updateGlobalLoadingIndicator()

        apiClient.checkHealth(currentBaseUrl) { result ->
            isHealthCheckBusy = false
            if (!isUiAlive()) return@checkHealth
            sessionController.onHealthCheckCompleted(result)
            updateHealthUi(result)
            updateGlobalLoadingIndicator()
        }
    }

    private fun updateHealthUi(result: HealthCheckResult?) {
        overviewScreen.updateHealth(result, currentBaseUrl)
        settingsScreen.updateEndpointHealth(result, currentBaseUrl)
        when {
            result == null -> setBackendPill("CHECKING", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
            result.healthy -> setBackendPill("CONNECTED", WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
            result.reachable -> setBackendPill("DEGRADED", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
            else -> setBackendPill("OFFLINE", WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
        }
    }

    private fun setBackendPill(label: String, tc: Int, bg: Int, bc: Int) {
        updatePillStyle(this, backendPill, label, tc, bg, bc, dotColor = tc)
    }

    private fun openAppSystemSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        startActivity(intent)
    }

    // =========================================================================
    // Native In-App Update Flow (Release Metadata, HTTPS APK + SHA-256 + PackageInstaller)
    // =========================================================================

    private fun seedSettingsUpdateCardFromCache() {
        val installedCode = appUpdateManager.getInstalledVersionCode()
        val installedName = appUpdateManager.getInstalledVersionName()
        val cachedLatestName = preferences.getLatestKnownReleaseName()
        val cachedLatestCode = preferences.getLatestKnownReleaseCode()
        val lastCheckMs = preferences.getLastUpdateCheckEpochMs()
        val hasNewer = cachedLatestCode > installedCode

        val message = when {
            cachedLatestCode <= 0 -> "Automatic checks run on launch against official WiFiVision release metadata."
            hasNewer -> "WiFiVision v$cachedLatestName (code $cachedLatestCode) is available."
            else -> "WiFiVision v$installedName is up to date."
        }
        settingsScreen.updateAppUpdateCard(
            installedVersionName = installedName,
            installedVersionCode = installedCode,
            latestVersionName = cachedLatestName,
            latestVersionCode = cachedLatestCode,
            lastCheckMs = lastCheckMs,
            updateAvailable = hasNewer,
            statusMessage = message
        )
    }

    private fun checkForAppUpdates(manual: Boolean) {
        if (isUpdateCheckBusy) return
        isUpdateCheckBusy = true
        if (manual) {
            settingsScreen.setUpdateCheckLoading(true)
            setGlobalFeedback("Checking for WiFiVision updates...", FeedbackTone.INFO)
        }

        appUpdateManager.checkForUpdates { result ->
            isUpdateCheckBusy = false
            if (!isUiAlive()) return@checkForUpdates
            settingsScreen.setUpdateCheckLoading(false)

            val nowCheckMs = preferences.getLastUpdateCheckEpochMs()
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    val rel = result.release
                    settingsScreen.updateAppUpdateCard(
                        installedVersionName = result.installedVersionName,
                        installedVersionCode = result.installedVersionCode,
                        latestVersionName = rel.versionName,
                        latestVersionCode = rel.versionCode,
                        lastCheckMs = nowCheckMs,
                        updateAvailable = true,
                        statusMessage = "New release v${rel.versionName} (${AppUpdateManager.formatBytes(rel.fileSize)}) is ready to download."
                    )
                    if (manual || dismissedUpdateVersionCode != rel.versionCode) {
                        updateDialogController.showUpdatePrompt(
                            release = rel,
                            installedVersionName = result.installedVersionName,
                            installedVersionCode = result.installedVersionCode,
                            belowMinimumSupported = result.belowMinimumSupported
                        )
                    }
                    if (manual) {
                        setGlobalFeedback("Update available: WiFiVision v${rel.versionName}", FeedbackTone.INFO)
                    }
                }

                is UpdateCheckResult.UpToDate -> {
                    val rel = result.release
                    settingsScreen.updateAppUpdateCard(
                        installedVersionName = result.installedVersionName,
                        installedVersionCode = result.installedVersionCode,
                        latestVersionName = rel.versionName,
                        latestVersionCode = rel.versionCode,
                        lastCheckMs = nowCheckMs,
                        updateAvailable = false,
                        statusMessage = "WiFiVision v${result.installedVersionName} is already up to date."
                    )
                    if (manual) {
                        setGlobalFeedback(
                            "WiFiVision v${result.installedVersionName} is up to date.",
                            FeedbackTone.SUCCESS
                        )
                    }
                }

                is UpdateCheckResult.Error -> {
                    val cachedName = preferences.getLatestKnownReleaseName()
                    val cachedCode = preferences.getLatestKnownReleaseCode()
                    val installedCode = appUpdateManager.getInstalledVersionCode()
                    val installedName = appUpdateManager.getInstalledVersionName()
                    settingsScreen.updateAppUpdateCard(
                        installedVersionName = installedName,
                        installedVersionCode = installedCode,
                        latestVersionName = cachedName,
                        latestVersionCode = cachedCode,
                        lastCheckMs = nowCheckMs,
                        updateAvailable = cachedCode > installedCode,
                        statusMessage = result.message,
                        isError = true
                    )
                    if (manual) {
                        setGlobalFeedback(result.message, FeedbackTone.WARNING)
                    }
                }
            }
        }
    }

    private fun startInAppUpdateDownload(release: AppReleaseMetadata) {
        pendingInstallAfterPermission = null
        appUpdateManager.downloadUpdate(release) { state ->
            if (!isUiAlive()) return@downloadUpdate
            updateDialogController.onDownloadStateChanged(state)

            when (state) {
                is UpdateDownloadState.ReadyToInstall -> {
                    if (appUpdateManager.canRequestPackageInstalls()) {
                        commitVerifiedUpdateInstall(state.apkFile, state.release)
                    } else {
                        pendingInstallAfterPermission = state.apkFile to state.release
                        updateDialogController.showPermissionGuidance(state.release)
                        setGlobalFeedback(
                            "Grant 'Install unknown apps' permission to complete updating WiFiVision.",
                            FeedbackTone.WARNING
                        )
                    }
                }

                is UpdateDownloadState.Cancelled -> {
                    setGlobalFeedback("Update download cancelled.", FeedbackTone.INFO)
                }

                is UpdateDownloadState.Failed -> {
                    setGlobalFeedback(state.message, FeedbackTone.ERROR)
                }

                else -> Unit
            }
        }
    }

    private fun commitVerifiedUpdateInstall(apkFile: File, release: AppReleaseMetadata) {
        appUpdateManager.commitPackageInstall(apkFile, release) { errMsg ->
            if (!isUiAlive()) return@commitPackageInstall
            updateDialogController.onDownloadStateChanged(UpdateDownloadState.Failed(errMsg))
            setGlobalFeedback(errMsg, FeedbackTone.ERROR)
        }
    }

    private fun handleUpdateInstallEvent(event: UpdateInstallEvent) {
        updateDialogController.onInstallEvent(event)
        when (event) {
            is UpdateInstallEvent.PendingUserAction -> {
                try {
                    startActivity(event.confirmIntent)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(event.confirmIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (ex: Exception) {
                        val msg = "Unable to open Android system installer (${ex.message ?: "error"})."
                        updateDialogController.onInstallEvent(UpdateInstallEvent.Failed(msg))
                        setGlobalFeedback(msg, FeedbackTone.ERROR)
                    }
                }
            }

            is UpdateInstallEvent.Succeeded -> {
                setGlobalFeedback("WiFiVision update installed successfully.", FeedbackTone.SUCCESS)
                seedSettingsUpdateCardFromCache()
            }

            is UpdateInstallEvent.Cancelled -> {
                setGlobalFeedback("App update installation cancelled.", FeedbackTone.INFO)
            }

            is UpdateInstallEvent.Failed -> {
                setGlobalFeedback(event.message, FeedbackTone.ERROR)
            }
        }
    }
}
