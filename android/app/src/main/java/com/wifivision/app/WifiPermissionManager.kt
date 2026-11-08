package com.wifivision.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build

class WifiPermissionManager(context: Context) {

    private val appContext = context.applicationContext

    fun hasLocationPermission(): Boolean {
        return appContext.checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNearbyWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            appContext.checkSelfPermission(
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isWifiEnabled(): Boolean {
        val wifiManager =
            appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        return wifiManager.isWifiEnabled
    }

    fun isLocationEnabled(): Boolean {
        val locationManager =
            appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return try {
            locationManager.isLocationEnabled
        } catch (e: Exception) {
            false
        }
    }

    fun canScanWifi(): Boolean {
        return hasLocationPermission() &&
                isWifiEnabled() &&
                isLocationEnabled()
    }

    fun getPermissionStatus(): String {
        return when {
            !hasLocationPermission() ->
                "Location permission required"

            !isWifiEnabled() ->
                "Please enable Wi-Fi"

            !isLocationEnabled() ->
                "Please enable device location"

            else ->
                "Ready to scan"
        }
    }
}