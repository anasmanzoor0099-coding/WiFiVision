package com.wifivision.app

import java.util.Locale
import kotlin.math.roundToInt

enum class BandFilter(val label: String) {
    ALL("All"),
    BAND_2_4("2.4 GHz"),
    BAND_5("5 GHz"),
    BAND_6("6 GHz")
}

enum class SignalTier {
    EXCELLENT,
    GOOD,
    FAIR,
    WEAK
}

enum class AccuracyTier {
    HIGH,
    MODERATE,
    LOW,
    UNKNOWN
}

/**
 * Pure-Kotlin signal, GPS precision, and band-filtering helpers shared between
 * the Android UI layer and JVM unit tests.
 */
object NetworkPresentationUtils {

    fun classifySignalTier(signalDbm: Int): SignalTier {
        return when {
            signalDbm >= -58 -> SignalTier.EXCELLENT
            signalDbm >= -68 -> SignalTier.GOOD
            signalDbm >= -78 -> SignalTier.FAIR
            else -> SignalTier.WEAK
        }
    }

    fun signalTierLabel(signalDbm: Int): String {
        return when (classifySignalTier(signalDbm)) {
            SignalTier.EXCELLENT -> "Excellent"
            SignalTier.GOOD -> "Good"
            SignalTier.FAIR -> "Fair"
            SignalTier.WEAK -> "Weak"
        }
    }

    fun signalProgressPercent(signalDbm: Int): Int {
        val clamped = signalDbm.coerceIn(-100, -35)
        val ratio = (clamped + 100) / 65.0
        return (5.0 + ratio * 95.0).roundToInt().coerceIn(5, 100)
    }

    fun signalBarCount(signalDbm: Int?, qualityHint: Int? = null): Int {
        if (qualityHint != null && qualityHint in 1..4) {
            return qualityHint
        }
        val rssi = signalDbm ?: return 0
        return when {
            rssi >= -55 -> 4
            rssi >= -67 -> 3
            rssi >= -78 -> 2
            rssi >= -90 -> 1
            else -> 0
        }
    }

    fun classifyAccuracyTier(accuracyMeters: Double?): AccuracyTier {
        if (accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters < 0.0) {
            return AccuracyTier.UNKNOWN
        }
        return when {
            accuracyMeters <= 15.0 -> AccuracyTier.HIGH
            accuracyMeters <= 40.0 -> AccuracyTier.MODERATE
            else -> AccuracyTier.LOW
        }
    }

    fun accuracyBadgeLabel(accuracyMeters: Double?): String {
        val tier = classifyAccuracyTier(accuracyMeters)
        val tierName = when (tier) {
            AccuracyTier.HIGH -> "High"
            AccuracyTier.MODERATE -> "Moderate"
            AccuracyTier.LOW -> "Low"
            AccuracyTier.UNKNOWN -> return "No Fix"
        }
        return String.format(Locale.US, "±%.1f m • %s", accuracyMeters, tierName)
    }

    fun filterNetworks(networks: List<WifiNetwork>, filter: BandFilter): List<WifiNetwork> {
        if (filter == BandFilter.ALL) {
            return networks
        }
        return networks.filter { it.band.equals(filter.label, ignoreCase = true) }
    }

    fun countByBand(networks: List<WifiNetwork>): Map<BandFilter, Int> {
        val counts = LinkedHashMap<BandFilter, Int>(BandFilter.entries.size)
        counts[BandFilter.ALL] = networks.size
        counts[BandFilter.BAND_2_4] = networks.count { it.band.equals(BandFilter.BAND_2_4.label, ignoreCase = true) }
        counts[BandFilter.BAND_5] = networks.count { it.band.equals(BandFilter.BAND_5.label, ignoreCase = true) }
        counts[BandFilter.BAND_6] = networks.count { it.band.equals(BandFilter.BAND_6.label, ignoreCase = true) }
        return counts
    }

    fun isWeakSecurity(security: String): Boolean {
        return security.equals("Open", ignoreCase = true) ||
            security.equals("WEP", ignoreCase = true)
    }
}
