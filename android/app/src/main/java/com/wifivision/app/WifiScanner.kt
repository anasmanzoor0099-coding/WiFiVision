package com.wifivision.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

enum class NetworkDiscoveryStatus {
    NEW,
    UPDATED,
    PREVIOUS
}

data class WifiNetwork(
    val ssid: String?,
    val bssid: String?,
    val signalDbm: Int,
    val frequencyMhz: Int,
    val band: String,
    val security: String,
    val signalQuality: Int,
    val firstSeenAtMs: Long = 0L,
    val lastSeenAtMs: Long = 0L,
    val seenCount: Int = 1,
    val lastLocation: ObservationLocation? = null,
    val discoveryStatus: NetworkDiscoveryStatus = NetworkDiscoveryStatus.NEW
) {
    val displaySsid: String
        get() = ssid ?: "Hidden Network"

    fun identityKey(): String {
        return ObservationPayload.buildNetworkIdentityKey(bssid, ssid, frequencyMhz, security)
    }

    fun toObservationPayload(
        location: ObservationLocation?,
        observedAtEpochMs: Long,
        scanSessionId: String? = null,
        isConnected: Boolean = false
    ): ObservationPayload {
        return ObservationPayload(
            ssid = ssid,
            bssid = bssid,
            signalDbm = signalDbm,
            frequencyMhz = frequencyMhz,
            band = band,
            security = security,
            signalQuality = signalQuality,
            location = location,
            source = ObservationPayload.SOURCE_ANDROID,
            observedAtEpochMs = observedAtEpochMs,
            scanSessionId = scanSessionId,
            isConnected = isConnected
        )
    }
}

data class WifiScanOutcome(
    val networks: List<WifiNetwork>,
    val isFresh: Boolean,
    val throttled: Boolean,
    val message: String
)

/**
 * Event-driven Wi-Fi scanner using SCAN_RESULTS_AVAILABLE_ACTION with
 * explicit handling for Android scan throttling, Wi-Fi disabled states,
 * hidden SSIDs, and BSSID sanitization.
 */
class WifiScanner(
    context: Context,
    private val permissionManager: WifiPermissionManager = WifiPermissionManager(context)
) {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeReceiver: BroadcastReceiver? = null
    private var timeoutRunnable: Runnable? = null
    private var isScanning = false

    companion object {
        private const val SCAN_TIMEOUT_MS = 6_000L
    }

    @Suppress("DEPRECATION")
    fun requestScan(onResult: (WifiScanOutcome) -> Unit) {
        if (isScanning) {
            return
        }

        if (!permissionManager.hasWifiScanPermissions()) {
            onResult(
                WifiScanOutcome(
                    networks = emptyList(),
                    isFresh = false,
                    throttled = false,
                    message = permissionManager.getPermissionStatus()
                )
            )
            return
        }

        if (!permissionManager.isWifiEnabled()) {
            onResult(
                WifiScanOutcome(
                    networks = emptyList(),
                    isFresh = false,
                    throttled = false,
                    message = "Wi-Fi is disabled"
                )
            )
            return
        }

        if (!permissionManager.isLocationEnabled()) {
            onResult(
                WifiScanOutcome(
                    networks = emptyList(),
                    isFresh = false,
                    throttled = false,
                    message = "Device location services are disabled"
                )
            )
            return
        }

        cancelScan()
        isScanning = true

        var completed = false
        val finish: (WifiScanOutcome) -> Unit = { outcome ->
            if (!completed) {
                completed = true
                cancelScan()
                onResult(outcome)
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                val networks = getAvailableNetworks()
                val message = when {
                    updated && networks.isNotEmpty() ->
                        "${networks.size} networks detected"
                    updated ->
                        "Scan completed: no nearby networks found"
                    networks.isNotEmpty() ->
                        "Scan throttled by Android; showing ${networks.size} cached networks"
                    else ->
                        "Scan throttled by Android and no cached results are available"
                }
                finish(
                    WifiScanOutcome(
                        networks = networks,
                        isFresh = updated,
                        throttled = !updated,
                        message = message
                    )
                )
            }
        }
        activeReceiver = receiver

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }

        val timeoutTask = Runnable {
            val cached = getAvailableNetworks()
            val message = if (cached.isNotEmpty()) {
                "Scan timed out; showing ${cached.size} cached networks"
            } else {
                "Scan timed out with no results"
            }
            finish(
                WifiScanOutcome(
                    networks = cached,
                    isFresh = false,
                    throttled = true,
                    message = message
                )
            )
        }
        timeoutRunnable = timeoutTask
        mainHandler.postDelayed(timeoutTask, SCAN_TIMEOUT_MS)

        val started = try {
            wifiManager.startScan()
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }

        if (!started) {
            val cached = getAvailableNetworks()
            val message = if (cached.isNotEmpty()) {
                "Android throttled active scan; showing ${cached.size} cached networks"
            } else {
                "Scan throttled by Android. Wait briefly before scanning again."
            }
            finish(
                WifiScanOutcome(
                    networks = cached,
                    isFresh = false,
                    throttled = true,
                    message = message
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun getAvailableNetworks(): List<WifiNetwork> {
        if (!permissionManager.hasWifiScanPermissions()) return emptyList()

        return try {
            val rawList = wifiManager.scanResults.mapNotNull { it.toWifiNetworkOrNull() }
            val uniqueMap = LinkedHashMap<String, WifiNetwork>(rawList.size)
            for (candidate in rawList) {
                val key = candidate.identityKey()
                val existing = uniqueMap[key]
                if (existing == null) {
                    uniqueMap[key] = candidate
                } else {
                    val mergedSsid = existing.ssid ?: candidate.ssid
                    val mergedSecurity = if (existing.security != "Unknown") existing.security else candidate.security
                    val winner = if (candidate.signalDbm >= existing.signalDbm) candidate else existing
                    uniqueMap[key] = winner.copy(
                        ssid = mergedSsid,
                        security = mergedSecurity
                    )
                }
            }
            uniqueMap.values.sortedByDescending { it.signalDbm }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun ScanResult.toWifiNetworkOrNull(): WifiNetwork? {
        if (level !in -127..0 || frequency <= 0) return null

        val rawSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wifiSsid?.toString() ?: SSID
        } else {
            SSID
        }
        val sanitizedSsid = ObservationPayload.sanitizeSsid(rawSsid)
        val sanitizedBssid = ObservationPayload.sanitizeBssid(BSSID)
        val band = ObservationPayload.classifyBand(frequency)
        val security = ObservationPayload.parseSecurity(capabilities)

        val quality = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiManager.calculateSignalLevel(level).coerceIn(0, 4)
        } else {
            WifiManager.calculateSignalLevel(level, 5).coerceIn(0, 4)
        }

        return WifiNetwork(
            ssid = sanitizedSsid,
            bssid = sanitizedBssid,
            signalDbm = level,
            frequencyMhz = frequency,
            band = band,
            security = security,
            signalQuality = quality
        )
    }

    fun cancelScan() {
        isScanning = false
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null

        activeReceiver?.let { receiver ->
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
        activeReceiver = null
    }
}
