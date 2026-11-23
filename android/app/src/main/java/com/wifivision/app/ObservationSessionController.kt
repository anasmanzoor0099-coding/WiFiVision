package com.wifivision.app

data class StartupDecision(
    val shouldDrainQueue: Boolean,
    val pendingQueueCount: Int
)

data class ScanMergeStats(
    val totalUniqueCount: Int = 0,
    val newlyDetectedCount: Int = 0,
    val updatedExistingCount: Int = 0,
    val lastScanAtMs: Long? = null,
    val isFresh: Boolean = false,
    val throttled: Boolean = false
)

data class ScanLocalResult(
    val detectedCount: Int,
    val scannedAtMs: Long,
    val shouldDrainQueue: Boolean,
    val enqueuedCount: Int,
    val newlyDetectedCount: Int = 0,
    val updatedExistingCount: Int = 0,
    val totalUniqueCount: Int = detectedCount
)

data class MonitorLocalResult(
    val updatedAtMs: Long,
    val shouldDrainQueue: Boolean,
    val enqueuedCount: Int
)

data class HealthUiUpdate(
    val pillLabel: String,
    val bannerText: String,
    val isHealthy: Boolean,
    val isReachable: Boolean,
    val shouldDrainQueue: Boolean
)

data class UploadConfirmationPreview(
    val existingPendingQueueCount: Int,
    val readyLocalCount: Int,
    val invalidSkippedCount: Int,
    val previewLines: List<String>,
    val hasLocation: Boolean,
    val locationAccuracyMeters: Double?,
    val candidatePayloads: List<ObservationPayload>,
    val skippedDuplicateCount: Int = 0
)

data class UploadConfirmationOutcome(
    val confirmed: Boolean,
    val enqueueSummary: EnqueueSummary?,
    val shouldDrainQueue: Boolean,
    val pendingQueueCount: Int
)

data class LifecycleCleanupResult(
    val cancelledScan: Boolean,
    val stoppedMonitoring: Boolean,
    val clearedPendingConfirmation: Boolean
)

/**
 * Pure-Kotlin state coordinator enforcing WiFiVision's local-first privacy rule,
 * unique network deduplication (by BSSID or SSID+Frequency+Security fallback),
 * and duplicate-free observation queuing.
 */
class ObservationSessionController(
    val queueLogic: ObservationQueueLogic = ObservationQueueLogic()
) {
    companion object {
        const val FRESH_SCAN_MATCH_WINDOW_MS = 5 * 60_000L
    }

    var autoUploadEnabled: Boolean = false
        private set

    var latestLocation: ObservationLocation? = null
        private set
    var latestLocationAccuracyMeters: Double? = null
        private set
    var latestLocationTimestampMs: Long? = null
        private set

    var latestWifiDetails: WifiDetails? = null
        private set

    private val uniqueNetworksByKey = LinkedHashMap<String, WifiNetwork>()

    var lastScanNetworks: List<WifiNetwork> = emptyList()
        private set
    var lastScanTimestampMs: Long? = null
        private set
    var lastScanStats: ScanMergeStats = ScanMergeStats()
        private set
    var lastHealthResult: HealthCheckResult? = null
        private set

    var pendingUploadConfirmation: UploadConfirmationPreview? = null
        private set

    var isScanInProgress: Boolean = false
    var isMonitoringActive: Boolean = false

    fun onAppStartup(): StartupDecision {
        return StartupDecision(
            shouldDrainQueue = false,
            pendingQueueCount = queueLogic.size()
        )
    }

    fun onGpsUpdated(
        location: ObservationLocation?,
        accuracyMeters: Double?,
        capturedAtMs: Long
    ) {
        latestLocation = location
        latestLocationAccuracyMeters = accuracyMeters
        latestLocationTimestampMs = if (location != null) capturedAtMs else null
    }

    fun onScanCompleted(
        networks: List<WifiNetwork>,
        activeWifi: WifiDetails?,
        location: ObservationLocation?,
        scannedAtMs: Long,
        isFresh: Boolean = true,
        throttled: Boolean = false
    ): ScanLocalResult {
        isScanInProgress = false
        if (activeWifi != null) {
            latestWifiDetails = activeWifi
        }
        if (location != null) {
            latestLocation = location
            latestLocationAccuracyMeters = location.accuracyMeters
            latestLocationTimestampMs = scannedAtMs
        }

        // Preserve previously captured unique networks and last successful timestamp if scan failed with empty non-fresh results.
        if (!isFresh && networks.isEmpty()) {
            lastScanStats = ScanMergeStats(
                totalUniqueCount = lastScanNetworks.size,
                newlyDetectedCount = 0,
                updatedExistingCount = 0,
                lastScanAtMs = lastScanTimestampMs,
                isFresh = false,
                throttled = throttled
            )
            return ScanLocalResult(
                detectedCount = 0,
                scannedAtMs = lastScanTimestampMs ?: scannedAtMs,
                shouldDrainQueue = false,
                enqueuedCount = 0,
                newlyDetectedCount = 0,
                updatedExistingCount = 0,
                totalUniqueCount = lastScanNetworks.size
            )
        }

        // Demote existing entries before merging the latest scan batch.
        for ((key, prev) in uniqueNetworksByKey.entries) {
            uniqueNetworksByKey[key] = prev.copy(discoveryStatus = NetworkDiscoveryStatus.PREVIOUS)
        }

        var newlyDetected = 0
        var updatedExisting = 0
        val batchKeysSeen = HashSet<String>()

        for (rawNet in networks) {
            val key = rawNet.identityKey()
            val existing = uniqueNetworksByKey[key]
            val locToAttach = location ?: existing?.lastLocation ?: latestLocation
            if (existing == null) {
                uniqueNetworksByKey[key] = rawNet.copy(
                    firstSeenAtMs = scannedAtMs,
                    lastSeenAtMs = scannedAtMs,
                    seenCount = 1,
                    lastLocation = locToAttach,
                    discoveryStatus = NetworkDiscoveryStatus.NEW
                )
                batchKeysSeen.add(key)
                newlyDetected++
            } else {
                val mergedSsid = rawNet.ssid ?: existing.ssid
                val mergedSecurity = if (rawNet.security != "Unknown") rawNet.security else existing.security
                val isRepeatInBatch = !batchKeysSeen.add(key)
                val nextSignal = if (isRepeatInBatch) maxOf(existing.signalDbm, rawNet.signalDbm) else rawNet.signalDbm
                val nextSeenCount = if (isRepeatInBatch) existing.seenCount else existing.seenCount + 1
                if (!isRepeatInBatch && existing.discoveryStatus != NetworkDiscoveryStatus.NEW) {
                    updatedExisting++
                }
                uniqueNetworksByKey[key] = existing.copy(
                    ssid = mergedSsid,
                    bssid = rawNet.bssid ?: existing.bssid,
                    signalDbm = nextSignal,
                    frequencyMhz = rawNet.frequencyMhz,
                    band = rawNet.band,
                    security = mergedSecurity,
                    signalQuality = if (nextSignal == rawNet.signalDbm) rawNet.signalQuality else existing.signalQuality,
                    lastSeenAtMs = scannedAtMs,
                    seenCount = nextSeenCount,
                    lastLocation = locToAttach,
                    discoveryStatus = if (existing.discoveryStatus == NetworkDiscoveryStatus.NEW) {
                        NetworkDiscoveryStatus.NEW
                    } else {
                        NetworkDiscoveryStatus.UPDATED
                    }
                )
            }
        }

        lastScanTimestampMs = scannedAtMs
        lastScanNetworks = uniqueNetworksByKey.values.sortedWith(
            compareByDescending<WifiNetwork> { it.discoveryStatus != NetworkDiscoveryStatus.PREVIOUS }
                .thenByDescending { it.lastSeenAtMs }
                .thenByDescending { it.signalDbm }
        )
        lastScanStats = ScanMergeStats(
            totalUniqueCount = lastScanNetworks.size,
            newlyDetectedCount = newlyDetected,
            updatedExistingCount = updatedExisting,
            lastScanAtMs = lastScanTimestampMs,
            isFresh = isFresh,
            throttled = throttled
        )

        return ScanLocalResult(
            detectedCount = networks.size,
            scannedAtMs = scannedAtMs,
            shouldDrainQueue = false,
            enqueuedCount = 0,
            newlyDetectedCount = newlyDetected,
            updatedExistingCount = updatedExisting,
            totalUniqueCount = lastScanNetworks.size
        )
    }

    fun clearSessionNetworks() {
        uniqueNetworksByKey.clear()
        lastScanNetworks = emptyList()
        lastScanTimestampMs = null
        lastScanStats = ScanMergeStats()
        pendingUploadConfirmation = null
    }

    fun onMonitoringStarted(started: Boolean): MonitorLocalResult {
        if (started) {
            isMonitoringActive = true
        }
        return MonitorLocalResult(
            updatedAtMs = System.currentTimeMillis(),
            shouldDrainQueue = false,
            enqueuedCount = 0
        )
    }

    fun onMonitorUpdated(details: WifiDetails, updatedAtMs: Long): MonitorLocalResult {
        latestWifiDetails = details
        return MonitorLocalResult(
            updatedAtMs = updatedAtMs,
            shouldDrainQueue = false,
            enqueuedCount = 0
        )
    }

    fun onHealthCheckCompleted(result: HealthCheckResult): HealthUiUpdate {
        lastHealthResult = result
        val pillLabel = when {
            result.healthy -> "CONNECTED"
            result.reachable -> "DEGRADED"
            else -> "OFFLINE"
        }
        val bannerText = formatHealthBanner(result)
        return HealthUiUpdate(
            pillLabel = pillLabel,
            bannerText = bannerText,
            isHealthy = result.healthy,
            isReachable = result.reachable,
            shouldDrainQueue = false
        )
    }

    fun formatHealthBanner(result: HealthCheckResult): String {
        return if (result.healthy) {
            val db = result.databaseStatus ?: "connected"
            "Backend health: Connected (database $db)"
        } else {
            "Backend health: ${result.message}"
        }
    }

    fun countEligibleLocalObservations(nowMs: Long): Int {
        val rawCandidates = collectCandidatePayloads(nowMs)
        if (rawCandidates.isEmpty()) return 0
        return queueLogic.filterNewEligibleCandidates(rawCandidates, nowMs).eligible.size
    }

    fun prepareUploadConfirmation(nowMs: Long): UploadConfirmationPreview? {
        val rawCandidates = collectCandidatePayloads(nowMs)
        val filterResult = queueLogic.filterNewEligibleCandidates(rawCandidates, nowMs)
        val existingPending = queueLogic.size()

        if (filterResult.eligible.isEmpty() && existingPending == 0) {
            pendingUploadConfirmation = null
            return null
        }

        val previewItems = if (filterResult.eligible.isNotEmpty()) {
            filterResult.eligible.take(6).map { formatPreviewLine(it) }
        } else {
            queueLogic.items().take(6).map { formatPreviewLine(it.payload) }
        }

        val preview = UploadConfirmationPreview(
            existingPendingQueueCount = existingPending,
            readyLocalCount = filterResult.eligible.size,
            invalidSkippedCount = filterResult.skippedInvalidCount,
            previewLines = previewItems,
            hasLocation = latestLocation != null,
            locationAccuracyMeters = latestLocationAccuracyMeters,
            candidatePayloads = filterResult.eligible,
            skippedDuplicateCount = filterResult.skippedDuplicateCount
        )
        pendingUploadConfirmation = preview
        return preview
    }

    fun confirmAndEnqueueUpload(confirmed: Boolean, nowMs: Long): UploadConfirmationOutcome {
        val pending = pendingUploadConfirmation
        pendingUploadConfirmation = null

        if (!confirmed || pending == null) {
            return UploadConfirmationOutcome(
                confirmed = false,
                enqueueSummary = null,
                shouldDrainQueue = false,
                pendingQueueCount = queueLogic.size()
            )
        }

        val summary = if (pending.candidatePayloads.isNotEmpty()) {
            queueLogic.enqueueObservations(pending.candidatePayloads, nowMs)
        } else {
            null
        }

        return UploadConfirmationOutcome(
            confirmed = true,
            enqueueSummary = summary,
            shouldDrainQueue = queueLogic.size() > 0,
            pendingQueueCount = queueLogic.size()
        )
    }

    fun onStopCleanup(): LifecycleCleanupResult {
        val hadScan = isScanInProgress
        val hadMonitor = isMonitoringActive
        val hadConfirm = pendingUploadConfirmation != null

        isScanInProgress = false
        isMonitoringActive = false
        pendingUploadConfirmation = null

        return LifecycleCleanupResult(
            cancelledScan = hadScan,
            stoppedMonitoring = hadMonitor,
            clearedPendingConfirmation = hadConfirm
        )
    }

    private fun collectCandidatePayloads(nowMs: Long): List<ObservationPayload> {
        val observedAtMs = lastScanTimestampMs ?: nowMs
        val rawCandidates = mutableListOf<ObservationPayload>()

        buildActiveWifiPayload(nowMs, observedAtMs)?.let {
            rawCandidates.add(it)
        }
        val pool = if (uniqueNetworksByKey.isNotEmpty()) {
            uniqueNetworksByKey.values.toList()
        } else {
            lastScanNetworks
        }
        pool.forEach { network ->
            val obsLoc = network.lastLocation ?: latestLocation
            val obsTs = network.lastSeenAtMs.takeIf { it > 0L } ?: observedAtMs
            rawCandidates.add(network.toObservationPayload(obsLoc, obsTs))
        }
        return rawCandidates
    }

    private fun buildActiveWifiPayload(nowMs: Long, observedAtMs: Long): ObservationPayload? {
        val details = latestWifiDetails ?: return null
        if (!details.connected) return null

        val isScanFresh = lastScanTimestampMs != null &&
            (nowMs - lastScanTimestampMs!!) in 0L..FRESH_SCAN_MATCH_WINDOW_MS

        val matchingScan = if (isScanFresh && details.bssid != null) {
            lastScanNetworks.firstOrNull { network ->
                network.bssid != null && network.bssid.equals(details.bssid, ignoreCase = true)
            }
        } else {
            null
        }

        val resolvedSsid = details.ssid ?: matchingScan?.ssid
        if (details.ssidUnavailable && resolvedSsid == null) {
            return null
        }

        val resolvedSecurity = when {
            matchingScan != null && matchingScan.security != "Unknown" -> matchingScan.security
            details.security != "Unknown" -> details.security
            else -> "Unknown"
        }

        return details.toObservationPayload(
            location = latestLocation,
            observedAtEpochMs = observedAtMs,
            resolvedSsid = resolvedSsid,
            resolvedSecurity = resolvedSecurity
        )
    }

    private fun formatPreviewLine(payload: ObservationPayload): String {
        val name = payload.ssid ?: "Hidden Network"
        return "$name  •  ${payload.signalDbm} dBm  •  ${payload.band}  •  ${payload.security}"
    }
}
