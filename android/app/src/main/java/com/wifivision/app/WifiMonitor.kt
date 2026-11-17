package com.wifivision.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

data class WifiDetails(
    val connected: Boolean,
    val ssid: String?,
    val bssid: String?,
    val ssidUnavailable: Boolean,
    val signalDbm: Int?,
    val frequencyMhz: Int?,
    val linkSpeedMbps: Int?,
    val band: String,
    val signalQuality: Int?,
    val internetAvailable: Boolean,
    val security: String = "Unknown"
) {
    val displaySsid: String
        get() = when {
            !connected -> "Not connected"
            ssid != null -> ssid
            ssidUnavailable -> "SSID unavailable (check location permission)"
            else -> "Hidden Network"
        }

    fun toObservationPayload(
        location: ObservationLocation?,
        observedAtEpochMs: Long,
        resolvedSsid: String? = ssid,
        resolvedSecurity: String? = null
    ): ObservationPayload? {
        if (!connected) return null
        val finalSsid = ObservationPayload.sanitizeSsid(resolvedSsid)
        // Do not turn permission-redacted SSIDs into fabricated Hidden Network observations.
        if (ssidUnavailable && finalSsid == null) return null

        val rssi = signalDbm?.takeIf { it in -127..0 } ?: return null
        val freq = frequencyMhz?.takeIf { it > 0 } ?: return null
        val finalSecurity = resolvedSecurity
            ?.takeIf { it in ObservationPayload.VALID_SECURITY && it != "Unknown" }
            ?: security.takeIf { it in ObservationPayload.VALID_SECURITY }
            ?: "Unknown"

        return ObservationPayload(
            ssid = finalSsid,
            bssid = bssid,
            signalDbm = rssi,
            frequencyMhz = freq,
            band = band,
            security = finalSecurity,
            signalQuality = signalQuality,
            location = location,
            source = ObservationPayload.SOURCE_ANDROID,
            observedAtEpochMs = observedAtEpochMs
        )
    }
}

/**
 * Monitors active Wi-Fi connection state via ConnectivityManager.NetworkCallback
 * and an optional foreground refresh interval, with duplicate-job prevention
 * and clean lifecycle teardown.
 */
class WifiMonitor(
    context: Context,
    private val permissionManager: WifiPermissionManager = WifiPermissionManager(context)
) {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var periodicRunnable: Runnable? = null
    private var listener: ((WifiDetails) -> Unit)? = null

    var isMonitoring: Boolean = false
        private set

    companion object {
        const val DEFAULT_MONITOR_INTERVAL_MS = 30_000L

        /**
         * Maps Android 12+ (API 31+) WifiInfo.currentSecurityType constants without
         * guessing between WPA and WPA2 when the platform constant is ambiguous.
         */
        fun mapWifiInfoSecurityType(securityType: Int): String {
            return when (securityType) {
                WifiInfo.SECURITY_TYPE_OPEN,
                WifiInfo.SECURITY_TYPE_OWE -> "Open"

                WifiInfo.SECURITY_TYPE_WEP -> "WEP"

                WifiInfo.SECURITY_TYPE_SAE,
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3"

                else -> "Unknown"
            }
        }
    }

    @Synchronized
    fun startMonitoring(
        intervalMs: Long = DEFAULT_MONITOR_INTERVAL_MS,
        onUpdate: (WifiDetails) -> Unit
    ): Boolean {
        if (isMonitoring) {
            return false
        }

        isMonitoring = true
        listener = onUpdate

        val callback = createNetworkCallback()
        networkCallback = callback

        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // Periodic refresh still operates safely if callback registration fails
        }

        emitUpdate()

        val runnable = object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                emitUpdate()
                mainHandler.postDelayed(this, intervalMs)
            }
        }
        periodicRunnable = runnable
        mainHandler.postDelayed(runnable, intervalMs)
        return true
    }

    @Synchronized
    fun stopMonitoring() {
        if (!isMonitoring && networkCallback == null && periodicRunnable == null) {
            return
        }
        isMonitoring = false
        listener = null

        periodicRunnable?.let { mainHandler.removeCallbacks(it) }
        periodicRunnable = null

        networkCallback?.let { cb ->
            try {
                connectivityManager.unregisterNetworkCallback(cb)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
        networkCallback = null
    }

    private fun createNetworkCallback(): ConnectivityManager.NetworkCallback {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onAvailable(network: Network) = emitUpdate()
                override fun onLost(network: Network) = emitUpdate()
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) = emitUpdate(networkCapabilities)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = emitUpdate()
                override fun onLost(network: Network) = emitUpdate()
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) = emitUpdate(networkCapabilities)
            }
        }
    }

    private fun emitUpdate(knownCapabilities: NetworkCapabilities? = null) {
        val currentListener = listener ?: return
        val details = getCurrentWifiInfo(knownCapabilities)
        mainHandler.post {
            if (isMonitoring) {
                currentListener(details)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun getCurrentWifiInfo(knownCapabilities: NetworkCapabilities? = null): WifiDetails {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = knownCapabilities
            ?: connectivityManager.getNetworkCapabilities(activeNetwork)

        val connectedToWifi =
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val internetAvailable =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        if (!connectedToWifi) {
            return WifiDetails(
                connected = false,
                ssid = null,
                bssid = null,
                ssidUnavailable = false,
                signalDbm = null,
                frequencyMhz = null,
                linkSpeedMbps = null,
                band = "Unknown",
                signalQuality = null,
                internetAvailable = false
            )
        }

        val transportWifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? WifiInfo
        } else {
            null
        }
        val legacyInfo = wifiManager.connectionInfo
        val info = if (
            transportWifiInfo != null &&
            ObservationPayload.sanitizeSsid(transportWifiInfo.ssid) != null
        ) {
            transportWifiInfo
        } else {
            legacyInfo ?: transportWifiInfo
        }

        val frequency = info?.frequency?.takeIf { it > 0 }
        val band = if (frequency != null) {
            ObservationPayload.classifyBand(frequency)
        } else {
            "Unknown"
        }

        val rssi = info?.rssi?.takeIf { it in -127..0 }
        val signalQuality = if (rssi != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wifiManager.calculateSignalLevel(rssi).coerceIn(0, 4)
            } else {
                WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4)
            }
        } else {
            null
        }

        val rawSsid = info?.ssid
        val sanitizedSsid = ObservationPayload.sanitizeSsid(rawSsid)
        val ssidUnavailable = sanitizedSsid == null && (
            !permissionManager.hasWifiScanPermissions() ||
                rawSsid.isNullOrBlank() ||
                rawSsid.equals("<unknown ssid>", ignoreCase = true)
            )
        val sanitizedBssid = ObservationPayload.sanitizeBssid(info?.bssid)
        val security = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val rawSecurityType = try {
                info?.currentSecurityType ?: WifiInfo.SECURITY_TYPE_UNKNOWN
            } catch (_: Throwable) {
                WifiInfo.SECURITY_TYPE_UNKNOWN
            }
            mapWifiInfoSecurityType(rawSecurityType)
        } else {
            "Unknown"
        }

        return WifiDetails(
            connected = true,
            ssid = sanitizedSsid,
            bssid = sanitizedBssid,
            ssidUnavailable = ssidUnavailable,
            signalDbm = rssi,
            frequencyMhz = frequency,
            linkSpeedMbps = info?.linkSpeed?.takeIf { it > 0 },
            band = band,
            signalQuality = signalQuality,
            internetAvailable = internetAvailable,
            security = security
        )
    }
}
