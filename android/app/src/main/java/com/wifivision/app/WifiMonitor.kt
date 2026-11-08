package com.wifivision.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

data class WifiDetails(
    val connected: Boolean,
    val ssid: String,
    val signalDbm: Int?,
    val frequencyMhz: Int?,
    val linkSpeedMbps: Int?,
    val band: String,
    val internetAvailable: Boolean
)

class WifiMonitor(context: Context) {

    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    fun getCurrentWifiInfo(): WifiDetails {

        val wifiManager =
            appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

        val network = connectivityManager.activeNetwork

        val capabilities =
            connectivityManager.getNetworkCapabilities(network)

        val connectedToWifi =
            capabilities?.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) == true

        val internetAvailable =
            capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            ) == true

        if (!connectedToWifi) {
            return WifiDetails(
                connected = false,
                ssid = "Not connected",
                signalDbm = null,
                frequencyMhz = null,
                linkSpeedMbps = null,
                band = "Unknown",
                internetAvailable = false
            )
        }

        val info = wifiManager.connectionInfo

        val frequency = info.frequency.takeIf { it > 0 }

        val band = when (frequency) {
            in 2400..2500 -> "2.4 GHz"
            in 4900..5900 -> "5 GHz"
            in 5925..7125 -> "6 GHz"
            else -> "Unknown"
        }

        val rawSsid = info.ssid
        val ssid = if (
            rawSsid.isNullOrBlank() ||
            rawSsid == "<unknown ssid>"
        ) {
            "SSID unavailable"
        } else {
            rawSsid.removeSurrounding("\"")
        }

        return WifiDetails(
            connected = true,
            ssid = ssid,
            signalDbm = info.rssi.takeIf { it in -127..0 },
            frequencyMhz = frequency,
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            band = band,
            internetAvailable = internetAvailable
        )
    }
}