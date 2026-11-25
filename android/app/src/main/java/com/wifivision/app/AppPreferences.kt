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
        private const val KEY_PAIRED_CREDENTIAL_OWNER_USER_ID = "paired_credential_owner_user_id"
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

    fun getPairedCredentialOwnerUserId(): String? =
        prefs.getString(KEY_PAIRED_CREDENTIAL_OWNER_USER_ID, null)?.takeIf { it.isNotBlank() }

    fun setPairedCredentialOwnerUserId(userId: String?) {
        val clean = userId?.trim()?.takeIf { it.isNotEmpty() }
        if (clean == null) {
            prefs.edit().remove(KEY_PAIRED_CREDENTIAL_OWNER_USER_ID).apply()
        } else {
            prefs.edit().putString(KEY_PAIRED_CREDENTIAL_OWNER_USER_ID, clean).apply()
        }
    }

    fun resetDashboardPreferences() {
        prefs.edit()
            .putString(KEY_DEFAULT_BAND, BandFilter.ALL.name)
            .putBoolean(KEY_MONITORING_AUTO_REFRESH, false)
            .putInt(KEY_MONITORING_INTERVAL_SEC, 30)
            .putInt(KEY_PERF_HISTORY_LIMIT, 50)
            .apply()
    }

    data class PersistedScanSession(
        val networks: List<WifiNetwork>,
        val stats: ScanMergeStats
    )

    fun saveLatestScanSession(networks: List<WifiNetwork>, stats: ScanMergeStats) {
        val root = org.json.JSONObject()
        val statsObj = org.json.JSONObject().apply {
            put("totalUniqueCount", stats.totalUniqueCount)
            put("newlyDetectedCount", stats.newlyDetectedCount)
            put("updatedExistingCount", stats.updatedExistingCount)
            if (stats.lastScanAtMs != null) put("lastScanAtMs", stats.lastScanAtMs) else put("lastScanAtMs", org.json.JSONObject.NULL)
            if (stats.sessionId != null) put("sessionId", stats.sessionId) else put("sessionId", org.json.JSONObject.NULL)
            put("isFresh", stats.isFresh)
            put("throttled", stats.throttled)
        }
        root.put("stats", statsObj)

        val arr = org.json.JSONArray()
        for (net in networks) {
            val item = org.json.JSONObject().apply {
                if (net.ssid != null) put("ssid", net.ssid) else put("ssid", org.json.JSONObject.NULL)
                if (net.bssid != null) put("bssid", net.bssid) else put("bssid", org.json.JSONObject.NULL)
                put("signalDbm", net.signalDbm)
                put("frequencyMhz", net.frequencyMhz)
                put("band", net.band)
                put("security", net.security)
                put("signalQuality", net.signalQuality)
                put("lastSeenAtMs", net.lastSeenAtMs)
                put("seenCount", net.seenCount)
                put("discoveryStatus", net.discoveryStatus.name)
                if (net.lastLocation != null) {
                    val locObj = org.json.JSONObject().apply {
                        put("latitude", net.lastLocation.latitude)
                        put("longitude", net.lastLocation.longitude)
                        if (net.lastLocation.accuracyMeters != null) {
                            put("accuracyMeters", net.lastLocation.accuracyMeters)
                        } else {
                            put("accuracyMeters", org.json.JSONObject.NULL)
                        }
                    }
                    put("lastLocation", locObj)
                }
            }
            arr.put(item)
        }
        root.put("networks", arr)
        prefs.edit().putString("latest_scan_session_json", root.toString()).apply()
    }

    fun loadLatestScanSession(): PersistedScanSession? {
        val raw = prefs.getString("latest_scan_session_json", null) ?: return null
        return try {
            val root = org.json.JSONObject(raw)
            val statsObj = root.optJSONObject("stats")
            val lastScanAtMs = if (statsObj != null && !statsObj.isNull("lastScanAtMs")) {
                statsObj.optLong("lastScanAtMs")
            } else {
                null
            }
            val arr = root.optJSONArray("networks") ?: org.json.JSONArray()
            val networks = ArrayList<WifiNetwork>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val ssid = if (obj.isNull("ssid")) null else obj.optString("ssid").takeIf { it.isNotBlank() }
                val bssid = if (obj.isNull("bssid")) null else obj.optString("bssid").takeIf { it.isNotBlank() }
                val locObj = obj.optJSONObject("lastLocation")
                val loc = if (locObj != null) {
                    val acc = if (locObj.isNull("accuracyMeters")) null else locObj.optDouble("accuracyMeters")
                    ObservationLocation(
                        latitude = locObj.optDouble("latitude"),
                        longitude = locObj.optDouble("longitude"),
                        accuracyMeters = acc
                    )
                } else {
                    null
                }
                val statusName = obj.optString("discoveryStatus", NetworkDiscoveryStatus.NEW.name)
                val status = NetworkDiscoveryStatus.entries.firstOrNull { it.name == statusName }
                    ?: NetworkDiscoveryStatus.NEW
                networks.add(
                    WifiNetwork(
                        ssid = ssid,
                        bssid = bssid,
                        signalDbm = obj.optInt("signalDbm"),
                        frequencyMhz = obj.optInt("frequencyMhz"),
                        band = obj.optString("band", "Unknown"),
                        security = obj.optString("security", "Unknown"),
                        signalQuality = obj.optInt("signalQuality", 0),
                        lastSeenAtMs = obj.optLong("lastSeenAtMs", lastScanAtMs ?: 0L),
                        seenCount = obj.optInt("seenCount", 1),
                        discoveryStatus = status,
                        lastLocation = loc
                    )
                )
            }
            val stats = if (statsObj != null) {
                val sid = if (statsObj.isNull("sessionId")) {
                    lastScanAtMs?.let { "scan_$it" }
                } else {
                    statsObj.optString("sessionId").takeIf { it.isNotBlank() } ?: lastScanAtMs?.let { "scan_$it" }
                }
                ScanMergeStats(
                    totalUniqueCount = statsObj.optInt("totalUniqueCount", networks.size),
                    newlyDetectedCount = statsObj.optInt("newlyDetectedCount", networks.size),
                    updatedExistingCount = statsObj.optInt("updatedExistingCount", 0),
                    lastScanAtMs = lastScanAtMs,
                    isFresh = statsObj.optBoolean("isFresh", true),
                    throttled = statsObj.optBoolean("throttled", false),
                    sessionId = sid
                )
            } else {
                ScanMergeStats(
                    totalUniqueCount = networks.size,
                    lastScanAtMs = lastScanAtMs,
                    sessionId = lastScanAtMs?.let { "scan_$it" }
                )
            }
            PersistedScanSession(networks = networks, stats = stats)
        } catch (_: Exception) {
            null
        }
    }

    fun clearLatestScanSession() {
        prefs.edit().remove("latest_scan_session_json").apply()
    }

    data class PersistedSpeedMeasurement(
        val downloadMbps: Double,
        val uploadMbps: Double,
        val measuredAtIso: String
    )

    fun saveLatestSpeedMeasurement(downloadMbps: Double, uploadMbps: Double, measuredAtIso: String) {
        val obj = org.json.JSONObject().apply {
            put("downloadMbps", downloadMbps)
            put("uploadMbps", uploadMbps)
            put("measuredAtIso", measuredAtIso)
        }
        prefs.edit().putString("latest_speed_measurement_json", obj.toString()).apply()
    }

    fun loadLatestSpeedMeasurement(): PersistedSpeedMeasurement? {
        val raw = prefs.getString("latest_speed_measurement_json", null) ?: return null
        return try {
            val obj = org.json.JSONObject(raw)
            val down = obj.optDouble("downloadMbps", Double.NaN)
            val up = obj.optDouble("uploadMbps", Double.NaN)
            val iso = obj.optString("measuredAtIso", "")
            if (!down.isFinite() || !up.isFinite() || iso.isBlank()) return null
            PersistedSpeedMeasurement(downloadMbps = down, uploadMbps = up, measuredAtIso = iso)
        } catch (_: Exception) {
            null
        }
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
