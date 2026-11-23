package com.wifivision.app

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class AppPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_SETTINGS,
        Context.MODE_PRIVATE
    )

    companion object {
        const val PREFS_SETTINGS = "wifivision_settings"
        const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_DEFAULT_BAND = "default_band"
        private const val KEY_MONITORING_AUTO_REFRESH = "monitoring_auto_refresh"
        private const val KEY_MONITORING_INTERVAL_SEC = "monitoring_interval_sec"
        private const val KEY_WIFI_LIVE_MONITOR_INTERVAL_SEC = "wifi_live_monitor_interval_sec"
        private const val KEY_PERF_HISTORY_LIMIT = "performance_history_limit"
        private const val KEY_LAST_SYNC_EPOCH_MS = "last_sync_epoch_ms"
        private const val KEY_LAST_UPDATE_CHECK_EPOCH_MS = "last_update_check_epoch_ms"
        private const val KEY_LATEST_RELEASE_NAME = "latest_release_name"
        private const val KEY_LATEST_RELEASE_CODE = "latest_release_code"
        const val DEFAULT_WIFI_MONITOR_INTERVAL_SEC = 45
        val VALID_WIFI_MONITOR_INTERVALS_SEC = listOf(30, 45, 60, 120)
    }

    fun loadBaseUrl(): String {
        val saved = prefs.getString(KEY_API_BASE_URL, null)
        if (!saved.isNullOrBlank()) {
            val validation = ApiContractUtils.validateBaseUrl(saved, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)
            if (validation is UrlValidationResult.Valid) {
                return validation.normalizedUrl
            }
        }
        return BuildConfig.DEFAULT_API_BASE_URL
    }

    fun saveBaseUrl(normalizedUrl: String) {
        prefs.edit().putString(KEY_API_BASE_URL, normalizedUrl).apply()
    }

    fun getDefaultBand(): BandFilter {
        val raw = prefs.getString(KEY_DEFAULT_BAND, null) ?: return BandFilter.ALL
        return BandFilter.entries.firstOrNull { it.name == raw || it.label.equals(raw, ignoreCase = true) }
            ?: BandFilter.ALL
    }

    fun setDefaultBand(band: BandFilter) {
        prefs.edit().putString(KEY_DEFAULT_BAND, band.name).apply()
    }

    fun isMonitoringAutoRefresh(): Boolean =
        prefs.getBoolean(KEY_MONITORING_AUTO_REFRESH, false)

    fun setMonitoringAutoRefresh(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_AUTO_REFRESH, enabled).apply()
    }

    fun getMonitoringIntervalSeconds(): Int {
        val raw = prefs.getInt(KEY_MONITORING_INTERVAL_SEC, 30)
        return if (raw == 15 || raw == 30 || raw == 60) raw else 30
    }

    fun setMonitoringIntervalSeconds(seconds: Int) {
        val safe = if (seconds == 15 || seconds == 30 || seconds == 60) seconds else 30
        prefs.edit().putInt(KEY_MONITORING_INTERVAL_SEC, safe).apply()
    }

    fun getWifiMonitorIntervalSeconds(): Int {
        val raw = prefs.getInt(KEY_WIFI_LIVE_MONITOR_INTERVAL_SEC, DEFAULT_WIFI_MONITOR_INTERVAL_SEC)
        return if (raw in VALID_WIFI_MONITOR_INTERVALS_SEC) raw else DEFAULT_WIFI_MONITOR_INTERVAL_SEC
    }

    fun setWifiMonitorIntervalSeconds(seconds: Int) {
        val safe = if (seconds in VALID_WIFI_MONITOR_INTERVALS_SEC) seconds else DEFAULT_WIFI_MONITOR_INTERVAL_SEC
        prefs.edit().putInt(KEY_WIFI_LIVE_MONITOR_INTERVAL_SEC, safe).apply()
    }

    fun getPerformanceHistoryLimit(): Int {
        val raw = prefs.getInt(KEY_PERF_HISTORY_LIMIT, 50)
        return if (raw == 20 || raw == 50 || raw == 100) raw else 50
    }

    fun setPerformanceHistoryLimit(limit: Int) {
        val safe = if (limit == 20 || limit == 50 || limit == 100) limit else 50
        prefs.edit().putInt(KEY_PERF_HISTORY_LIMIT, safe).apply()
    }

    fun getLastSyncEpochMs(): Long = prefs.getLong(KEY_LAST_SYNC_EPOCH_MS, 0L)

    fun setLastSyncEpochMs(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_EPOCH_MS, epochMs).apply()
    }

    fun getLastUpdateCheckEpochMs(): Long = prefs.getLong(KEY_LAST_UPDATE_CHECK_EPOCH_MS, 0L)

    fun setLastUpdateCheckEpochMs(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_EPOCH_MS, epochMs).apply()
    }

    fun getLatestKnownReleaseName(): String? = prefs.getString(KEY_LATEST_RELEASE_NAME, null)

    fun getLatestKnownReleaseCode(): Int = prefs.getInt(KEY_LATEST_RELEASE_CODE, 0)

    fun setLatestKnownRelease(versionName: String, versionCode: Int) {
        prefs.edit()
            .putString(KEY_LATEST_RELEASE_NAME, versionName)
            .putInt(KEY_LATEST_RELEASE_CODE, versionCode)
            .apply()
    }

    fun resetDashboardPreferences() {
        prefs.edit()
            .putString(KEY_DEFAULT_BAND, BandFilter.ALL.name)
            .putBoolean(KEY_MONITORING_AUTO_REFRESH, false)
            .putInt(KEY_MONITORING_INTERVAL_SEC, 30)
            .putInt(KEY_PERF_HISTORY_LIMIT, 50)
            .apply()
    }
}

object UiFormatters {
    private val SHORT_DT_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)
    private val FULL_DT_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss", Locale.US)

    fun formatIsoShort(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            val instant = Instant.parse(iso.trim())
            SHORT_DT_FORMAT.format(instant.atZone(ZoneId.systemDefault()))
        } catch (_: Exception) {
            "—"
        }
    }

    fun formatIsoDateTime(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            val instant = Instant.parse(iso.trim())
            FULL_DT_FORMAT.format(instant.atZone(ZoneId.systemDefault()))
        } catch (_: Exception) {
            "—"
        }
    }

    fun formatTimeOfDay(epochMs: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(epochMs))
    }

    fun formatSignal(dbm: Double?): String {
        if (dbm == null || !dbm.isFinite()) return "—"
        return String.format(Locale.US, "%.1f dBm", dbm)
    }

    fun formatSignalInt(dbm: Int?): String {
        if (dbm == null) return "—"
        return "$dbm dBm"
    }

    fun formatMbps(mbps: Double?): String {
        if (mbps == null || !mbps.isFinite()) return "—"
        return String.format(Locale.US, "%.1f Mbps", mbps)
    }

    fun formatMs(ms: Double?): String {
        if (ms == null || !ms.isFinite()) return "—"
        return String.format(Locale.US, "%.1f ms", ms)
    }

    fun formatPercent(pct: Double?): String {
        if (pct == null || !pct.isFinite()) return "—"
        return String.format(Locale.US, "%.1f%%", pct)
    }

    fun formatMeters(meters: Double?): String {
        if (meters == null || !meters.isFinite() || meters < 0.0) return "Unknown"
        return String.format(Locale.US, "±%.1f m", meters)
    }

    fun classifyScoreLabel(score: Int): String = when {
        score >= 85 -> "Excellent"
        score >= 70 -> "Good"
        score >= 50 -> "Fair"
        else -> "Poor"
    }

    fun qualityColors(classification: String): Triple<Int, Int, Int> {
        return when (classification.lowercase(Locale.US)) {
            "excellent" -> Triple(
                WiFiVisionPalette.emeraldDeep,
                WiFiVisionPalette.emeraldTint,
                WiFiVisionPalette.emeraldBorder
            )
            "good" -> Triple(
                WiFiVisionPalette.jade,
                WiFiVisionPalette.emeraldTint,
                WiFiVisionPalette.emeraldBorder
            )
            "fair" -> Triple(
                WiFiVisionPalette.deepGold,
                WiFiVisionPalette.softGoldBg,
                WiFiVisionPalette.goldBorder
            )
            "poor" -> Triple(
                WiFiVisionPalette.crimsonText,
                WiFiVisionPalette.crimsonBg,
                WiFiVisionPalette.crimsonBorder
            )
            else -> Triple(
                WiFiVisionPalette.bodySlate,
                WiFiVisionPalette.softIvory,
                WiFiVisionPalette.hairline
            )
        }
    }

    fun signalColors(signalDbm: Int): Triple<Int, Int, Int> {
        return when (NetworkPresentationUtils.classifySignalTier(signalDbm)) {
            SignalTier.EXCELLENT -> Triple(
                WiFiVisionPalette.emeraldDeep,
                WiFiVisionPalette.emeraldTint,
                WiFiVisionPalette.emeraldBorder
            )
            SignalTier.GOOD -> Triple(
                WiFiVisionPalette.jade,
                WiFiVisionPalette.emeraldTint,
                WiFiVisionPalette.emeraldBorder
            )
            SignalTier.FAIR -> Triple(
                WiFiVisionPalette.deepGold,
                WiFiVisionPalette.softGoldBg,
                WiFiVisionPalette.goldBorder
            )
            SignalTier.WEAK -> Triple(
                WiFiVisionPalette.crimsonText,
                WiFiVisionPalette.crimsonBg,
                WiFiVisionPalette.crimsonBorder
            )
        }
    }

    fun rssiHeatmapColor(signalDbm: Double): Int {
        return when {
            signalDbm >= -55.0 -> 0xFF2E7D5B.toInt()
            signalDbm >= -67.0 -> 0xFF65A30D.toInt()
            signalDbm >= -75.0 -> 0xFFC6A15B.toInt()
            signalDbm >= -85.0 -> 0xFFD97706.toInt()
            else -> 0xFFB83B3B.toInt()
        }
    }

    fun densityHeatmapColor(count: Int, maxCount: Int): Int {
        if (count <= 2) return 0xFF1E7A5B.toInt()
        val thresholdHigh = maxOf(6, (maxCount * 0.65).toInt())
        return if (count >= thresholdHigh) 0xFFB83B3B.toInt() else 0xFFC6A15B.toInt()
    }

    fun twoColumnRow(
        context: Context,
        left: View,
        right: View,
        gapDp: Int = 10
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = context.dp(gapDp / 2)
            })
            addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = context.dp(gapDp / 2)
            })
        }
    }
}
