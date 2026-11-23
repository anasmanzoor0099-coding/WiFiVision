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
    val frequencyMhz: Int = 0,
    val band: String = "Unknown",
    val security: String = "Unknown",
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Double? = null,
    val recordedAtMs: Long
)

enum class ObservationDecisionReason {
    NEW_DISCOVERY,
    SIGNIFICANT_SIGNAL_CHANGE,
    LOCATION_CHANGED,
    LOCATION_ACQUIRED,
    METADATA_CHANGED,
    PERIODIC_REFRESH,
    DUPLICATE_IN_QUEUE,
    DUPLICATE_UNCHANGED,
    DUPLICATE_TOO_SOON
}

data class ObservationFilterResult(
    val eligible: List<ObservationPayload>,
    val skippedDuplicateCount: Int,
    val skippedInvalidCount: Int
)

data class EnqueueSummary(
    val acceptedCount: Int,
    val skippedInvalidCount: Int,
    val skippedDuplicateCount: Int,
    val skippedBatchCapCount: Int,
    val droppedOldestCount: Int
)

/**
 * Pure-Kotlin queue, persistent history deduplication, retry, and rate-limit
 * backoff engine designed to prevent duplicate cloud observations while staying
 * within WiFiVision's 60 req / 15 min observation rate limit.
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
        const val DEFAULT_DEDUPE_WINDOW_MS = 30 * 60_000L
        const val DEFAULT_MIN_UPLOAD_SPACING_MS = 1_500L
        private const val MIN_OBSERVATION_INTERVAL_MS = 60_000L
        private const val LOCATION_ACQUIRED_MIN_INTERVAL_MS = 30_000L
        private const val SIGNIFICANT_RSSI_DELTA_DBM = 8
        private const val SIGNIFICANT_MOVE_METERS = 25.0
        private const val MAX_ACCURACY_GUARD_METERS = 60.0
        private const val MAX_RECENT_RECORDS = 500
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

    fun recentHistoryCount(): Int = recentByKey.size

    fun clear() {
        queue.clear()
        recentByKey.clear()
        globalPauseUntilMs = 0L
        consecutiveRateLimits = 0
        lastUploadCompletedAtMs = 0L
    }

    fun filterNewEligibleCandidates(
        candidates: List<ObservationPayload>,
        nowMs: Long
    ): ObservationFilterResult {
        pruneRecent(nowMs)

        var invalidCount = 0
        var duplicateCount = 0
        val eligible = mutableListOf<ObservationPayload>()
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
            if (!shouldAcceptReason(evaluateCandidate(candidate, nowMs))) {
                duplicateCount++
                continue
            }
            eligible.add(candidate)
        }

        return ObservationFilterResult(
            eligible = eligible,
            skippedDuplicateCount = duplicateCount,
            skippedInvalidCount = invalidCount
        )
    }

    fun enqueueObservations(
        candidates: List<ObservationPayload>,
        nowMs: Long,
        applyBatchCap: Boolean = true
    ): EnqueueSummary {
        val filtered = filterNewEligibleCandidates(candidates, nowMs)
        val toEnqueue = if (applyBatchCap && filtered.eligible.size > maxBatchPerScan) {
            filtered.eligible.take(maxBatchPerScan)
        } else {
            filtered.eligible
        }
        val skippedCap = filtered.eligible.size - toEnqueue.size

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
            recordRecent(payload, nowMs)
        }

        return EnqueueSummary(
            acceptedCount = toEnqueue.size,
            skippedInvalidCount = filtered.skippedInvalidCount,
            skippedDuplicateCount = filtered.skippedDuplicateCount,
            skippedBatchCapCount = skippedCap,
            droppedOldestCount = droppedOldest
        )
    }

    fun evaluateCandidate(
        candidate: ObservationPayload,
        nowMs: Long
    ): ObservationDecisionReason {
        val key = candidate.dedupeKey()

        // Block identical/stationary observation if an equivalent record is already pending in offline queue.
        val queuedMatches = queue.filter { it.payload.dedupeKey() == key }
        if (queuedMatches.isNotEmpty()) {
            val movedFromAllQueued = candidate.location != null && queuedMatches.all { queued ->
                hasMovedSignificantly(
                    prevLat = queued.payload.location?.latitude,
                    prevLon = queued.payload.location?.longitude,
                    prevAcc = queued.payload.location?.accuracyMeters,
                    candidate = candidate
                )
            }
            if (!movedFromAllQueued) {
                return ObservationDecisionReason.DUPLICATE_IN_QUEUE
            }
            return ObservationDecisionReason.LOCATION_CHANGED
        }

        val previous = recentByKey[key] ?: return ObservationDecisionReason.NEW_DISCOVERY

        // Always record genuinely distinct geographic locations separately.
        if (hasMovedSignificantly(
                prevLat = previous.latitude,
                prevLon = previous.longitude,
                prevAcc = previous.accuracyMeters,
                candidate = candidate
            )
        ) {
            return ObservationDecisionReason.LOCATION_CHANGED
        }

        val ageMs = (nowMs - previous.recordedAtMs).coerceAtLeast(0L)

        // If prior stationary scan lacked GPS and valid GPS was just acquired, allow upgrade after short guard.
        if (previous.latitude == null && candidate.location != null && ageMs >= LOCATION_ACQUIRED_MIN_INTERVAL_MS) {
            return ObservationDecisionReason.LOCATION_ACQUIRED
        }

        // Prevent insignificant rapid fluctuations within minimum interval.
        if (ageMs < MIN_OBSERVATION_INTERVAL_MS) {
            return ObservationDecisionReason.DUPLICATE_TOO_SOON
        }

        if (abs(candidate.signalDbm - previous.signalDbm) >= SIGNIFICANT_RSSI_DELTA_DBM) {
            return ObservationDecisionReason.SIGNIFICANT_SIGNAL_CHANGE
        }

        val freqChanged = previous.frequencyMhz > 0 && candidate.frequencyMhz != previous.frequencyMhz
        val secChanged = previous.security != "Unknown" &&
            candidate.security != "Unknown" &&
            !candidate.security.equals(previous.security, ignoreCase = true)
        if (freqChanged || secChanged) {
            return ObservationDecisionReason.METADATA_CHANGED
        }

        if (ageMs >= dedupeWindowMs) {
            return ObservationDecisionReason.PERIODIC_REFRESH
        }

        return ObservationDecisionReason.DUPLICATE_UNCHANGED
    }

    private fun shouldAcceptReason(reason: ObservationDecisionReason): Boolean {
        return when (reason) {
            ObservationDecisionReason.NEW_DISCOVERY,
            ObservationDecisionReason.SIGNIFICANT_SIGNAL_CHANGE,
            ObservationDecisionReason.LOCATION_CHANGED,
            ObservationDecisionReason.LOCATION_ACQUIRED,
            ObservationDecisionReason.METADATA_CHANGED,
            ObservationDecisionReason.PERIODIC_REFRESH -> true

            ObservationDecisionReason.DUPLICATE_IN_QUEUE,
            ObservationDecisionReason.DUPLICATE_UNCHANGED,
            ObservationDecisionReason.DUPLICATE_TOO_SOON -> false
        }
    }

    private fun hasMovedSignificantly(
        prevLat: Double?,
        prevLon: Double?,
        prevAcc: Double?,
        candidate: ObservationPayload
    ): Boolean {
        val curLoc = candidate.location ?: return false
        if (prevLat == null || prevLon == null) return false

        val movedMeters = haversineMeters(prevLat, prevLon, curLoc.latitude, curLoc.longitude)
        val worstAccuracy = maxOf(prevAcc ?: 0.0, curLoc.accuracyMeters ?: 0.0)
        val accuracyGuard = min(worstAccuracy, MAX_ACCURACY_GUARD_METERS)
        val effectiveMoveThreshold = maxOf(SIGNIFICANT_MOVE_METERS, accuracyGuard)
        return movedMeters >= effectiveMoveThreshold
    }

    private fun recordRecent(payload: ObservationPayload, nowMs: Long) {
        val key = payload.dedupeKey()
        recentByKey.remove(key)
        recentByKey[key] = RecentObservationRecord(
            key = key,
            signalDbm = payload.signalDbm,
            frequencyMhz = payload.frequencyMhz,
            band = payload.band,
            security = payload.security,
            latitude = payload.location?.latitude,
            longitude = payload.location?.longitude,
            accuracyMeters = payload.location?.accuracyMeters,
            recordedAtMs = nowMs
        )
        while (recentByKey.size > MAX_RECENT_RECORDS) {
            val eldestKey = recentByKey.keys.firstOrNull() ?: break
            recentByKey.remove(eldestKey)
        }
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
        val matched = queue.firstOrNull { it.id == id }
        val removed = queue.removeAll { it.id == id }
        if (removed) {
            matched?.let { recordRecent(it.payload, nowMs) }
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
        val historyJson = recentByKey.values.joinToString(",") { rec ->
            val fields = mutableListOf(
                "\"key\":${JsonSupport.quote(rec.key)}",
                "\"signalDbm\":${rec.signalDbm}",
                "\"frequencyMhz\":${rec.frequencyMhz}",
                "\"band\":${JsonSupport.quote(rec.band)}",
                "\"security\":${JsonSupport.quote(rec.security)}",
                "\"recordedAtMs\":${rec.recordedAtMs}"
            )
            if (rec.latitude != null && rec.longitude != null) {
                fields.add("\"latitude\":${rec.latitude}")
                fields.add("\"longitude\":${rec.longitude}")
            }
            if (rec.accuracyMeters != null) {
                fields.add("\"accuracyMeters\":${rec.accuracyMeters}")
            }
            "{${fields.joinToString(",")}}"
        }
        return "{\"globalPauseUntilMs\":$globalPauseUntilMs,\"items\":[$itemsJson],\"recentHistory\":[$historyJson]}"
    }

    @Suppress("UNCHECKED_CAST")
    fun restoreStateJson(rawJson: String) {
        queue.clear()
        recentByKey.clear()
        if (rawJson.isBlank()) return
        try {
            val root = JsonSupport.parseJson(rawJson) as? Map<String, Any?> ?: return
            globalPauseUntilMs = (root["globalPauseUntilMs"] as? Number)?.toLong() ?: 0L
            val itemsList = root["items"] as? List<Any?>
            itemsList?.forEach { entry ->
                if (queue.size >= maxQueueSize) return@forEach
                val obj = entry as? Map<String, Any?> ?: return@forEach
                val id = obj["id"] as? String ?: return@forEach
                val attempts = (obj["attempts"] as? Number)?.toInt() ?: 0
                val nextAttempt = (obj["nextAttemptAtMs"] as? Number)?.toLong() ?: 0L
                val enqueuedAt = (obj["enqueuedAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
                val payloadMap = obj["payload"] as? Map<String, Any?> ?: return@forEach
                val payload = parsePayloadMap(payloadMap) ?: return@forEach
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

            val historyList = root["recentHistory"] as? List<Any?>
            historyList?.forEach { entry ->
                if (recentByKey.size >= MAX_RECENT_RECORDS) return@forEach
                val obj = entry as? Map<String, Any?> ?: return@forEach
                val key = obj["key"] as? String ?: return@forEach
                val signalDbm = (obj["signalDbm"] as? Number)?.toInt() ?: return@forEach
                val recordedAtMs = (obj["recordedAtMs"] as? Number)?.toLong() ?: return@forEach
                val freq = (obj["frequencyMhz"] as? Number)?.toInt() ?: 0
                val band = obj["band"] as? String ?: "Unknown"
                val sec = obj["security"] as? String ?: "Unknown"
                val lat = (obj["latitude"] as? Number)?.toDouble()
                val lon = (obj["longitude"] as? Number)?.toDouble()
                val acc = (obj["accuracyMeters"] as? Number)?.toDouble()
                recentByKey[key] = RecentObservationRecord(
                    key = key,
                    signalDbm = signalDbm,
                    frequencyMhz = freq,
                    band = band,
                    security = sec,
                    latitude = lat,
                    longitude = lon,
                    accuracyMeters = acc,
                    recordedAtMs = recordedAtMs
                )
            }
            pruneRecent(System.currentTimeMillis())
        } catch (_: Exception) {
            queue.clear()
            recentByKey.clear()
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
