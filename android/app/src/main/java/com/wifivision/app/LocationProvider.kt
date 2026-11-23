package com.wifivision.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

data class MeasurementLocationSnapshot(
    val location: ObservationLocation,
    val provider: String,
    val capturedAtMs: Long,
    val fromLastKnownFallback: Boolean
)

sealed class LocationFixResult {
    data class Available(val snapshot: MeasurementLocationSnapshot) : LocationFixResult()
    data class Unavailable(val reason: String) : LocationFixResult()
}

/**
 * Acquires genuine Android device GPS/network measurement coordinates using
 * platform LocationManager with explicit timeout, accuracy capture, and
 * strict range validation. Never fabricates coordinates.
 */
class LocationProvider(
    context: Context,
    private val permissionManager: WifiPermissionManager
) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    private var activeCancellation: CancellationSignal? = null
    private var activeListeners: MutableList<LocationListener> = mutableListOf()
    private var timeoutRunnable: Runnable? = null

    companion object {
        private const val FRESH_CACHE_MAX_AGE_MS = 45_000L
        private const val FALLBACK_CACHE_MAX_AGE_MS = 5 * 60_000L
        private const val DEFAULT_TIMEOUT_MS = 8_000L
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxCacheAgeMs: Long = FRESH_CACHE_MAX_AGE_MS,
        onResult: (LocationFixResult) -> Unit
    ) {
        cancel()

        if (!permissionManager.hasFineLocationPermission()) {
            onResult(LocationFixResult.Unavailable("Fine location permission not granted"))
            return
        }

        if (!permissionManager.isLocationEnabled()) {
            onResult(LocationFixResult.Unavailable("Device location services are disabled"))
            return
        }

        val now = System.currentTimeMillis()
        val recentFresh = bestLastKnownLocation(now, maxCacheAgeMs)
        if (recentFresh != null) {
            onResult(LocationFixResult.Available(recentFresh))
            return
        }

        val enabledProviders = availableProviders()
        if (enabledProviders.isEmpty()) {
            onResult(LocationFixResult.Unavailable("No location providers enabled"))
            return
        }

        var completed = false
        val completeWith: (LocationFixResult) -> Unit = { result ->
            if (!completed) {
                completed = true
                cancel()
                onResult(result)
            }
        }

        val timeoutTask = Runnable {
            val fallback = bestLastKnownLocation(System.currentTimeMillis(), FALLBACK_CACHE_MAX_AGE_MS)
            if (fallback != null) {
                completeWith(LocationFixResult.Available(fallback))
            } else {
                completeWith(LocationFixResult.Unavailable("GPS fix timed out; location unavailable"))
            }
        }
        timeoutRunnable = timeoutTask
        mainHandler.postDelayed(timeoutTask, timeoutMs)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cancellation = CancellationSignal()
            activeCancellation = cancellation
            val primaryProvider = enabledProviders.first()
            try {
                locationManager.getCurrentLocation(
                    primaryProvider,
                    cancellation,
                    mainExecutor
                ) { location ->
                    val snapshot = location?.toValidatedSnapshot(fromFallback = false)
                    if (snapshot != null) {
                        completeWith(LocationFixResult.Available(snapshot))
                    }
                }
            } catch (_: SecurityException) {
                completeWith(LocationFixResult.Unavailable("Location permission denied"))
                return
            } catch (_: Exception) {
                // Allow timeout fallback or secondary providers below
            }

            // Also request from a secondary enabled provider if GPS is slow indoors
            if (enabledProviders.size > 1) {
                requestLegacySingleFix(enabledProviders[1], completeWith)
            }
        } else {
            for (provider in enabledProviders) {
                requestLegacySingleFix(provider, completeWith)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun requestLegacySingleFix(
        provider: String,
        onFix: (LocationFixResult) -> Unit
    ) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val snapshot = location.toValidatedSnapshot(fromFallback = false)
                if (snapshot != null) {
                    onFix(LocationFixResult.Available(snapshot))
                }
            }

            @Suppress("OVERRIDE_DEPRECATION")
            @Deprecated("Deprecated in Android SDK")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        activeListeners.add(listener)
        try {
            locationManager.requestLocationUpdates(
                provider,
                0L,
                0f,
                listener,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            onFix(LocationFixResult.Unavailable("Location permission denied"))
        } catch (_: Exception) {
            // Provider may fail to register; timeout handles fallback honestly
        }
    }

    @SuppressLint("MissingPermission")
    fun bestLastKnownLocation(
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = FALLBACK_CACHE_MAX_AGE_MS
    ): MeasurementLocationSnapshot? {
        if (!permissionManager.hasFineLocationPermission()) return null

        val candidates = mutableListOf<Location>()
        for (provider in availableProviders()) {
            try {
                locationManager.getLastKnownLocation(provider)?.let { candidates.add(it) }
            } catch (_: SecurityException) {
                return null
            } catch (_: Exception) {
                // Ignore provider read error
            }
        }

        return candidates
            .filter { loc ->
                loc.time > 0L &&
                    (nowMs - loc.time) in 0L..maxAgeMs &&
                    loc.toValidatedSnapshot(fromFallback = true) != null
            }
            .minWithOrNull(
                compareBy<Location> { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
                    .thenByDescending { it.time }
            )
            ?.toValidatedSnapshot(fromFallback = true)
    }

    private fun availableProviders(): List<String> {
        val ordered = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return ordered.filter { provider ->
            try {
                locationManager.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun Location.toValidatedSnapshot(fromFallback: Boolean): MeasurementLocationSnapshot? {
        val accuracy = if (hasAccuracy() && accuracy.isFinite() && accuracy >= 0f) {
            accuracy.toDouble()
        } else {
            null
        }
        val dto = ObservationLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy
        )
        if (!dto.isValid()) return null

        val capturedTime = if (time > 0L) time else System.currentTimeMillis()
        return MeasurementLocationSnapshot(
            location = dto,
            provider = provider ?: "unknown",
            capturedAtMs = capturedTime,
            fromLastKnownFallback = fromFallback
        )
    }

    fun cancel() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null

        activeCancellation?.cancel()
        activeCancellation = null

        for (listener in activeListeners) {
            try {
                locationManager.removeUpdates(listener)
            } catch (_: Exception) {
                // Safe cleanup
            }
        }
        activeListeners.clear()
    }
}
