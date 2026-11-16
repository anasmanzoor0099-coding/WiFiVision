package com.wifivision.app

import android.content.Context
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class QueuedObservation(
    val id: String,
    val payload: ObservationPayload,
    val attempts: Int = 0,
    val nextAttemptAtMs: Long = 0L,
    val enqueuedAtMs: Long
)

data class RecentObservationRecord(
    val key: String,
    val signalDbm: Int,
    val latitude: Double?,
    val longitude: Double?,
    val recordedAtMs: Long
)

data class EnqueueSummary(
    val acceptedCount: Int,
    val skippedInvalidCount: Int,
    val skippedDuplicateCount: Int,
    val skippedBatchCapCount: Int,
    val droppedOldestCount: Int
)

/**
 * Pure-Kotlin queue, deduplication, retry, and rate-limit backoff engine
 * designed to stay within WiFiVision's 60 req / 15 min observation rate limit.
 */
class ObservationQueueLogic(
    private val maxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE,
    private val maxRetryAttempts: Int = DEFAULT_MAX_RETRY_ATTEMPTS,
    private val maxBatchPerScan: Int = DEFAULT_MAX_BATCH_PER_SCAN,
    private val dedupeWindowMs: Long = DEFAULT_DEDUPE_WINDOW_MS,
    private val minUploadSpacingMs: Long = DEFAULT_MIN_UPLOAD_SPACING_MS
) {
    companion object {
        const val DEFAULT_MAX_QUEUE_SIZE = 100
        const val DEFAULT_MAX_RETRY_ATTEMPTS = 5
        const val DEFAULT_MAX_BATCH_PER_SCAN = 10
        const val DEFAULT_DEDUPE_WINDOW_MS = 5 * 60_000L
        const val DEFAULT_MIN_UPLOAD_SPACING_MS = 1_500L
        private const val MIN_CHANGE_INTERVAL_MS = 30_000L
        private const val SIGNIFICANT_RSSI_DELTA_DBM = 8
        private const val SIGNIFICANT_MOVE_METERS = 25.0
        private const val BASE_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 5 * 60_000L
        private const val DEFAULT_RATE_LIMIT_PAUSE_MS = 60_000L
        private const val MAX_RATE_LIMIT_PAUSE_MS = 15 * 60_000L
    }

    private val queue = ArrayList<QueuedObservation>()
    private val recentByKey = LinkedHashMap<String, RecentObservationRecord>()
    private var nextIdCounter = 1L

    var globalPauseUntilMs: Long = 0L
        private set
    var consecutiveRateLimits: Int = 0
        private set
    var lastUploadCompletedAtMs: Long = 0L
        private set

    fun size(): Int = queue.size

    fun items(): List<QueuedObservation> = queue.toList()

    fun clear() {
        queue.clear()
        recentByKey.clear()
        globalPauseUntilMs = 0L
        consecutiveRateLimits = 0
        lastUploadCompletedAtMs = 0L
    }

    fun enqueueObservations(
        candidates: List<ObservationPayload>,
        nowMs: Long,
        applyBatchCap: Boolean = true
    ): EnqueueSummary {
        pruneRecent(nowMs)

        var invalidCount = 0
        var duplicateCount = 0
        val acceptedCandidates = mutableListOf<ObservationPayload>()

        // Deduplicate within the incoming batch as well as against recent history
        val seenKeysInBatch = HashSet<String>()
        for (candidate in candidates) {
            if (!candidate.isValid()) {
                invalidCount++
                continue
            }
            val key = candidate.dedupeKey()
            if (!seenKeysInBatch.add(key)) {
                duplicateCount++
                continue
            }
            if (isDuplicate(candidate, key, nowMs)) {
                duplicateCount++
                continue
            }
            acceptedCandidates.add(candidate)
        }

        val toEnqueue = if (applyBatchCap && acceptedCandidates.size > maxBatchPerScan) {
            acceptedCandidates.take(maxBatchPerScan)
        } else {
            acceptedCandidates
        }
        val skippedCap = acceptedCandidates.size - toEnqueue.size

        var droppedOldest = 0
        for (payload in toEnqueue) {
            if (queue.size >= maxQueueSize) {
                queue.removeAt(0)
                droppedOldest++
            }
            val id = "obs_${nowMs}_${nextIdCounter++}"
            queue.add(
                QueuedObservation(
                    id = id,
                    payload = payload,
                    attempts = 0,
                    nextAttemptAtMs = 0L,
                    enqueuedAtMs = nowMs
                )
            )
            val key = payload.dedupeKey()
            recentByKey[key] = RecentObservationRecord(
                key = key,
                signalDbm = payload.signalDbm,
                latitude = payload.location?.latitude,
                longitude = payload.location?.longitude,
                recordedAtMs = nowMs
            )
        }

        return EnqueueSummary(
            acceptedCount = toEnqueue.size,
            skippedInvalidCount = invalidCount,
            skippedDuplicateCount = duplicateCount,
            skippedBatchCapCount = skippedCap,
            droppedOldestCount = droppedOldest
        )
    }

    fun peekNextReady(nowMs: Long): QueuedObservation? {
        if (queue.isEmpty()) return null
        if (nowMs < globalPauseUntilMs) return null
        if (lastUploadCompletedAtMs > 0L && (nowMs - lastUploadCompletedAtMs) < minUploadSpacingMs) {
            return null
        }
        return queue.firstOrNull { it.nextAttemptAtMs <= nowMs }
    }

    fun nextWakeDelayMs(nowMs: Long): Long? {
        if (queue.isEmpty()) return null
        val earliestItemReady = queue.minOf { it.nextAttemptAtMs }
        val spacingReady = if (lastUploadCompletedAtMs > 0L) {
            lastUploadCompletedAtMs + minUploadSpacingMs
        } else {
            0L
        }
        val readyAt = maxOf(earliestItemReady, globalPauseUntilMs, spacingReady)
        return (readyAt - nowMs).coerceAtLeast(0L)
    }

    fun markSuccess(id: String, nowMs: Long): Boolean {
        val removed = queue.removeAll { it.id == id }
        if (removed) {
            consecutiveRateLimits = 0
            globalPauseUntilMs = 0L
            lastUploadCompletedAtMs = nowMs
        }
        return removed
    }

    fun markClientRejected(id: String, nowMs: Long): Boolean {
        lastUploadCompletedAtMs = nowMs
        return queue.removeAll { it.id == id }
    }

    fun markRateLimited(retryAfterSeconds: Long?, nowMs: Long): Long {
        consecutiveRateLimits++
        lastUploadCompletedAtMs = nowMs
        val exponentialPause = min(
            DEFAULT_RATE_LIMIT_PAUSE_MS shl (consecutiveRateLimits - 1).coerceAtMost(4),
            MAX_RATE_LIMIT_PAUSE_MS
        )
        val serverPause = if (retryAfterSeconds != null && retryAfterSeconds > 0L) {
            min(retryAfterSeconds * 1000L, MAX_RATE_LIMIT_PAUSE_MS)
        } else {
            0L
        }
        val pauseMs = maxOf(exponentialPause, serverPause)
        globalPauseUntilMs = nowMs + pauseMs
        return pauseMs
    }

    /**
     * Returns true if kept in queue for retry, false if dropped after reaching max attempts.
     */
    fun markTransientFailure(id: String, nowMs: Long): Boolean {
        lastUploadCompletedAtMs = nowMs
        val index = queue.indexOfFirst { it.id == id }
        if (index < 0) return false
        val current = queue[index]
        val nextAttempts = current.attempts + 1
        if (nextAttempts >= maxRetryAttempts) {
            queue.removeAt(index)
            return false
        }
        val backoff = min(
            BASE_BACKOFF_MS shl (nextAttempts - 1).coerceAtMost(6),
            MAX_BACKOFF_MS
        )
        queue[index] = current.copy(
            attempts = nextAttempts,
            nextAttemptAtMs = nowMs + backoff
        )
        return true
    }

    private fun isDuplicate(candidate: ObservationPayload, key: String, nowMs: Long): Boolean {
        val queuedMatch = queue.any { it.payload.dedupeKey() == key }
        if (queuedMatch) {
            // Only replace/allow if significant change AND existing isn't brand new
            val recent = recentByKey[key]
            if (recent == null || (nowMs - recent.recordedAtMs) < MIN_CHANGE_INTERVAL_MS) {
                return true
            }
        }

        val previous = recentByKey[key] ?: return false
        val ageMs = nowMs - previous.recordedAtMs
        if (ageMs >= dedupeWindowMs) return false
        if (ageMs < MIN_CHANGE_INTERVAL_MS) return true

        val rssiChanged = abs(candidate.signalDbm - previous.signalDbm) >= SIGNIFICANT_RSSI_DELTA_DBM
        if (rssiChanged) return false

        val prevLat = previous.latitude
        val prevLon = previous.longitude
        val curLoc = candidate.location
        if (prevLat != null && prevLon != null && curLoc != null) {
            val movedMeters = haversineMeters(prevLat, prevLon, curLoc.latitude, curLoc.longitude)
            if (movedMeters >= SIGNIFICANT_MOVE_METERS) return false
        }

        return true
    }

    private fun pruneRecent(nowMs: Long) {
        val iterator = recentByKey.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMs - entry.value.recordedAtMs >= dedupeWindowMs) {
                iterator.remove()
            }
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    fun exportStateJson(): String {
        val itemsJson = queue.joinToString(",") { item ->
            "{" +
                "\"id\":${JsonSupport.quote(item.id)}," +
                "\"attempts\":${item.attempts}," +
                "\"nextAttemptAtMs\":${item.nextAttemptAtMs}," +
                "\"enqueuedAtMs\":${item.enqueuedAtMs}," +
                "\"payload\":${item.payload.toJsonString()}" +
                "}"
        }
        return "{\"globalPauseUntilMs\":$globalPauseUntilMs,\"items\":[$itemsJson]}"
    }

    @Suppress("UNCHECKED_CAST")
    fun restoreStateJson(rawJson: String) {
        queue.clear()
        if (rawJson.isBlank()) return
        try {
            val root = JsonSupport.parseJson(rawJson) as? Map<String, Any?> ?: return
            globalPauseUntilMs = (root["globalPauseUntilMs"] as? Number)?.toLong() ?: 0L
            val itemsList = root["items"] as? List<Any?> ?: return
            for (entry in itemsList) {
                if (queue.size >= maxQueueSize) break
                val obj = entry as? Map<String, Any?> ?: continue
                val id = obj["id"] as? String ?: continue
                val attempts = (obj["attempts"] as? Number)?.toInt() ?: 0
                val nextAttempt = (obj["nextAttemptAtMs"] as? Number)?.toLong() ?: 0L
                val enqueuedAt = (obj["enqueuedAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
                val payloadMap = obj["payload"] as? Map<String, Any?> ?: continue
                val payload = parsePayloadMap(payloadMap) ?: continue
                if (payload.isValid()) {
                    queue.add(
                        QueuedObservation(
                            id = id,
                            payload = payload,
                            attempts = attempts,
                            nextAttemptAtMs = nextAttempt,
                            enqueuedAtMs = enqueuedAt
                        )
                    )
                }
            }
        } catch (_: Exception) {
            queue.clear()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePayloadMap(map: Map<String, Any?>): ObservationPayload? {
        val signalDbm = (map["signalDbm"] as? Number)?.toInt() ?: return null
        val frequencyMhz = (map["frequencyMhz"] as? Number)?.toInt() ?: return null
        val band = map["band"] as? String ?: "Unknown"
        val security = map["security"] as? String ?: "Unknown"
        val signalQuality = (map["signalQuality"] as? Number)?.toInt()
        val ssid = ObservationPayload.sanitizeSsid(map["ssid"] as? String)
        val bssid = ObservationPayload.sanitizeBssid(map["bssid"] as? String)
        val source = map["source"] as? String ?: ObservationPayload.SOURCE_ANDROID
        val observedIso = map["observedAt"] as? String
        val observedEpochMs = if (observedIso != null) {
            try {
                java.time.Instant.parse(observedIso).toEpochMilli()
            } catch (_: Exception) {
                return null
            }
        } else {
            return null
        }

        val locMap = map["location"] as? Map<String, Any?>
        val location = if (locMap != null) {
            val lat = (locMap["latitude"] as? Number)?.toDouble()
            val lon = (locMap["longitude"] as? Number)?.toDouble()
            val acc = (locMap["accuracyMeters"] as? Number)?.toDouble()
            if (lat != null && lon != null) {
                ObservationLocation(lat, lon, acc)
            } else {
                null
            }
        } else {
            null
        }

        return ObservationPayload(
            ssid = ssid,
            bssid = bssid,
            signalDbm = signalDbm,
            frequencyMhz = frequencyMhz,
            band = band,
            security = security,
            signalQuality = signalQuality,
            location = location,
            source = source,
            observedAtEpochMs = observedEpochMs
        )
    }
}

/**
 * SharedPreferences-backed persistence wrapper for ObservationQueueLogic.
 */
class ObservationQueue(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val logic = ObservationQueueLogic()

    companion object {
        private const val PREFS_NAME = "wifivision_observation_queue"
        private const val KEY_STATE = "queue_state_v1"
        private const val KEY_UPLOADED_COUNT = "uploaded_count_v1"
    }

    init {
        val saved = prefs.getString(KEY_STATE, "") ?: ""
        if (saved.isNotBlank()) {
            logic.restoreStateJson(saved)
        }
    }

    @Synchronized
    fun persist() {
        prefs.edit().putString(KEY_STATE, logic.exportStateJson()).apply()
    }

    @Synchronized
    fun getUploadedCount(): Int = prefs.getInt(KEY_UPLOADED_COUNT, 0)

    @Synchronized
    fun incrementUploadedCount(): Int {
        val updated = getUploadedCount() + 1
        prefs.edit().putInt(KEY_UPLOADED_COUNT, updated).apply()
        return updated
    }
}
