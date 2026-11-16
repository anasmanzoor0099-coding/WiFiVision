package com.wifivision.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Evaluates runtime permissions and hardware service states required for
 * Wi-Fi scanning, active Wi-Fi inspection, and GPS measurement acquisition.
 */
class WifiPermissionManager(context: Context) {

    private val appContext = context.applicationContext

    fun hasFineLocationPermission(): Boolean {
        return appContext.checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCoarseLocationPermission(): Boolean {
        return appContext.checkSelfPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasLocationPermission(): Boolean {
        return hasFineLocationPermission()
    }

    fun hasNearbyWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.checkSelfPermission(
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasWifiScanPermissions(): Boolean {
        return hasFineLocationPermission() && hasNearbyWifiPermission()
    }

    fun requiredRuntimePermissions(): Array<String> {
        val missing = mutableListOf<String>()
        if (!hasFineLocationPermission()) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!hasCoarseLocationPermission()) {
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNearbyWifiPermission()) {
            missing.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return missing.toTypedArray()
    }

    @Suppress("DEPRECATION")
    fun isWifiEnabled(): Boolean {
        return try {
            val wifiManager =
                appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    fun isLocationEnabled(): Boolean {
        val locationManager =
            appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun canScanWifi(): Boolean {
        return hasWifiScanPermissions() &&
            isWifiEnabled() &&
            isLocationEnabled()
    }

    fun getPermissionStatus(): String {
        return when {
            !hasFineLocationPermission() && hasCoarseLocationPermission() ->
                "Precise location permission required for GPS and Wi-Fi scanning"

            !hasFineLocationPermission() ->
                "Fine location permission required"

            !hasNearbyWifiPermission() ->
                "Nearby Wi-Fi devices permission required"

            !isWifiEnabled() ->
                "Please enable Wi-Fi"

            !isLocationEnabled() ->
                "Please enable device location"

            else ->
                "Ready to scan"
        }
    }
}
