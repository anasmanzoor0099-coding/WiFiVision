package com.wifivision.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalDbm: Int,
    val frequencyMhz: Int,
    val band: String,
    val security: String,
    val signalQuality: Int
)

class WifiScanner(context: Context) {

    private val appContext = context.applicationContext

    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private fun hasLocationPermission(): Boolean {
        return appContext.checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startScan(): Boolean {
        if (!hasLocationPermission()) return false

        return try {
            wifiManager.startScan()
        } catch (e: SecurityException) {
            false
        }
    }

    fun getAvailableNetworks(): List<WifiNetwork> {
        if (!hasLocationPermission()) return emptyList()

        return try {
            wifiManager.scanResults
                .filter {
                    it.SSID.isNotBlank() &&
                    it.SSID != "<unknown ssid>"
                }
                .sortedByDescending { it.level }
                .map { it.toWifiNetwork() }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun ScanResult.toWifiNetwork(): WifiNetwork {

        val band = when (frequency) {
            in 2400..2500 -> "2.4 GHz"
            in 4900..5900 -> "5 GHz"
            in 5925..7125 -> "6 GHz"
            else -> "Unknown"
        }

        val security = when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            else -> "Open"
        }

        val quality = WifiManager.calculateSignalLevel(level, 5)

        return WifiNetwork(
            ssid = SSID,
            bssid = BSSID,
            signalDbm = level,
            frequencyMhz = frequency,
            band = band,
            security = security,
            signalQuality = quality
        )
    }
}