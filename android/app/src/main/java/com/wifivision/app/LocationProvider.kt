package com.wifivision.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
        private const val FRESH_CACHE_MAX_AGE_MS = 20_000L
        private const val LIVE_SNAPSHOT_MAX_AGE_MS = 30_000L
        private const val GOOD_ACCURACY_METERS = 35.0
        private const val FALLBACK_CACHE_MAX_AGE_MS = 5 * 60_000L
        private const val DEFAULT_TIMEOUT_MS = 8_000L
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxCacheAgeMs: Long = FRESH_CACHE_MAX_AGE_MS,
        forceFresh: Boolean = false,
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
        if (!forceFresh && maxCacheAgeMs > 0L) {
            val recentFresh = bestLastKnownLocation(now, maxCacheAgeMs)
            val acc = recentFresh?.location?.accuracyMeters
            if (recentFresh != null && acc != null && acc <= GOOD_ACCURACY_METERS) {
                onResult(LocationFixResult.Available(recentFresh))
                return
            }
        }

        val enabledProviders = availableProviders()
        if (enabledProviders.isEmpty()) {
            onResult(LocationFixResult.Unavailable("No location providers enabled"))
            return
        }

        var completed = false
        var bestLiveSnapshot: MeasurementLocationSnapshot? = null
        val canAwaitPrecisionProvider = enabledProviders.contains(LocationManager.GPS_PROVIDER) ||
            enabledProviders.contains(LocationManager.FUSED_PROVIDER)

        val completeWith: (LocationFixResult) -> Unit = { result ->
            if (!completed) {
                completed = true
                cancel()
                onResult(result)
            }
        }

        val handleLiveSnapshot: (MeasurementLocationSnapshot) -> Unit = { snapshot ->
            if (!completed) {
                val curBest = bestLiveSnapshot
                if (curBest == null || isBetterSnapshot(snapshot, curBest)) {
                    bestLiveSnapshot = snapshot
                }
                val acc = snapshot.location.accuracyMeters
                val isAccurate = acc != null && acc <= GOOD_ACCURACY_METERS
                if ((isAccurate && !snapshot.fromLastKnownFallback) || !canAwaitPrecisionProvider) {
                    completeWith(LocationFixResult.Available(bestLiveSnapshot ?: snapshot))
                }
            }
        }

        val timeoutTask = Runnable {
            val fallback = bestLastKnownLocation(System.currentTimeMillis(), FALLBACK_CACHE_MAX_AGE_MS)
            val chosen = pickBestOnTimeout(bestLiveSnapshot, fallback)
            if (chosen != null) {
                completeWith(LocationFixResult.Available(chosen))
            } else {
                completeWith(LocationFixResult.Unavailable("GPS fix timed out; location unavailable"))
            }
        }
        timeoutRunnable = timeoutTask
        mainHandler.postDelayed(timeoutTask, timeoutMs)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cancellation = CancellationSignal()
            activeCancellation = cancellation

            for (provider in enabledProviders) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val request = LocationRequest.Builder(0L)
                            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                            .setMaxUpdates(1)
                            .build()
                        locationManager.getCurrentLocation(
                            provider,
                            request,
                            cancellation,
                            mainExecutor
                        ) { location ->
                            val snapshot = location?.toValidatedSnapshot(fromFallback = false)
                            if (snapshot != null) {
                                handleLiveSnapshot(snapshot)
                            }
                        }
                    } else {
                        locationManager.getCurrentLocation(
                            provider,
                            cancellation,
                            mainExecutor
                        ) { location ->
                            val snapshot = location?.toValidatedSnapshot(fromFallback = false)
                            if (snapshot != null) {
                                handleLiveSnapshot(snapshot)
                            }
                        }
                    }
                } catch (_: SecurityException) {
                    completeWith(LocationFixResult.Unavailable("Location permission denied"))
                    return
                } catch (_: Exception) {
                    requestLegacySingleFix(provider, completeWith, handleLiveSnapshot)
                }
            }
        } else {
            for (provider in enabledProviders) {
                requestLegacySingleFix(provider, completeWith, handleLiveSnapshot)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun requestLegacySingleFix(
        provider: String,
        onError: (LocationFixResult) -> Unit,
        onSnapshot: (MeasurementLocationSnapshot) -> Unit
    ) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val snapshot = location.toValidatedSnapshot(fromFallback = false)
                if (snapshot != null) {
                    onSnapshot(snapshot)
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
            onError(LocationFixResult.Unavailable("Location permission denied"))
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

        val candidates = mutableListOf<MeasurementLocationSnapshot>()
        for (provider in availableProviders()) {
            try {
                locationManager.getLastKnownLocation(provider)?.let { raw ->
                    val ageMs = locationAgeMs(raw, nowMs)
                    if (ageMs in 0L..maxAgeMs) {
                        raw.toValidatedSnapshot(fromFallback = true, nowWallMs = nowMs)?.let {
                            candidates.add(it)
                        }
                    }
                }
            } catch (_: SecurityException) {
                return null
            } catch (_: Exception) {
                // Ignore provider read error
            }
        }

        return candidates.minWithOrNull(
            compareBy<MeasurementLocationSnapshot> { it.location.accuracyMeters ?: Double.MAX_VALUE }
                .thenByDescending { it.capturedAtMs }
        )
    }

    private fun isBetterSnapshot(
        candidate: MeasurementLocationSnapshot,
        current: MeasurementLocationSnapshot
    ): Boolean {
        if (current.fromLastKnownFallback && !candidate.fromLastKnownFallback) {
            val cAcc = candidate.location.accuracyMeters
            val curAcc = current.location.accuracyMeters
            if (cAcc == null || curAcc == null || cAcc <= curAcc + 15.0) {
                return true
            }
        }
        val candAcc = candidate.location.accuracyMeters ?: Double.MAX_VALUE
        val curAcc = current.location.accuracyMeters ?: Double.MAX_VALUE
        if (candAcc < curAcc - 2.0) return true
        if (curAcc < candAcc - 2.0) return false
        return candidate.capturedAtMs > current.capturedAtMs
    }

    private fun pickBestOnTimeout(
        live: MeasurementLocationSnapshot?,
        fallback: MeasurementLocationSnapshot?
    ): MeasurementLocationSnapshot? {
        if (live == null) return fallback
        if (fallback == null) return live
        val liveAcc = live.location.accuracyMeters ?: Double.MAX_VALUE
        val fallbackAcc = fallback.location.accuracyMeters ?: Double.MAX_VALUE
        // Prefer the fresh live fix unless cached fallback is materially sharper and recent (< 2 min).
        val fallbackAgeMs = System.currentTimeMillis() - fallback.capturedAtMs
        return if (fallbackAcc + 15.0 < liveAcc && fallbackAgeMs in 0L..120_000L) {
            fallback
        } else {
            live
        }
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

    private fun locationAgeMs(location: Location, nowWallMs: Long): Long {
        if (location.elapsedRealtimeNanos > 0L) {
            val deltaNs = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            if (deltaNs >= 0L) {
                return deltaNs / 1_000_000L
            }
        }
        return if (location.time > 0L) (nowWallMs - location.time).coerceAtLeast(0L) else 0L
    }

    private fun Location.toValidatedSnapshot(
        fromFallback: Boolean,
        nowWallMs: Long = System.currentTimeMillis()
    ): MeasurementLocationSnapshot? {
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

        val ageMs = locationAgeMs(this, nowWallMs)
        val isCached = fromFallback || ageMs > LIVE_SNAPSHOT_MAX_AGE_MS
        val capturedTime = (nowWallMs - ageMs).coerceAtLeast(1L)
        return MeasurementLocationSnapshot(
            location = dto,
            provider = provider ?: "unknown",
            capturedAtMs = capturedTime,
            fromLastKnownFallback = isCached
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
