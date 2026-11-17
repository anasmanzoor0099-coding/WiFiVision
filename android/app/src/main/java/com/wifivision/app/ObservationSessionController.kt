package com.wifivision.app

data class StartupDecision(
    val shouldDrainQueue: Boolean,
    val pendingQueueCount: Int
)

data class ScanLocalResult(
    val detectedCount: Int,
    val scannedAtMs: Long,
    val shouldDrainQueue: Boolean,
    val enqueuedCount: Int
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
    val candidatePayloads: List<ObservationPayload>
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
 * Pure-Kotlin state coordinator enforcing WiFiVision's local-first privacy rule:
 * scanning, GPS acquisition, monitoring, and health checks never enqueue or
 * upload observations automatically. Uploads require explicit confirmation.
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
    var lastScanNetworks: List<WifiNetwork> = emptyList()
        private set
    var lastScanTimestampMs: Long? = null
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
        scannedAtMs: Long
    ): ScanLocalResult {
        isScanInProgress = false
        lastScanNetworks = networks
        lastScanTimestampMs = scannedAtMs
        if (activeWifi != null) {
            latestWifiDetails = activeWifi
        }
        if (location != null) {
            latestLocation = location
        }
        return ScanLocalResult(
            detectedCount = networks.size,
            scannedAtMs = scannedAtMs,
            shouldDrainQueue = false,
            enqueuedCount = 0
        )
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

    fun prepareUploadConfirmation(nowMs: Long): UploadConfirmationPreview? {
        val observedAtMs = lastScanTimestampMs ?: nowMs
        val rawCandidates = mutableListOf<ObservationPayload>()

        buildActiveWifiPayload(nowMs, observedAtMs)?.let {
            rawCandidates.add(it)
        }
        lastScanNetworks.forEach { network ->
            rawCandidates.add(network.toObservationPayload(latestLocation, observedAtMs))
        }

        val validCandidates = mutableListOf<ObservationPayload>()
        var invalidCount = 0
        val seenKeys = HashSet<String>()
        for (candidate in rawCandidates) {
            if (!candidate.isValid()) {
                invalidCount++
                continue
            }
            if (seenKeys.add(candidate.dedupeKey())) {
                validCandidates.add(candidate)
            }
        }

        val existingPending = queueLogic.size()
        if (validCandidates.isEmpty() && existingPending == 0) {
            pendingUploadConfirmation = null
            return null
        }

        val previewItems = if (validCandidates.isNotEmpty()) {
            validCandidates.take(6).map { formatPreviewLine(it) }
        } else {
            queueLogic.items().take(6).map { formatPreviewLine(it.payload) }
        }

        val preview = UploadConfirmationPreview(
            existingPendingQueueCount = existingPending,
            readyLocalCount = validCandidates.size,
            invalidSkippedCount = invalidCount,
            previewLines = previewItems,
            hasLocation = latestLocation != null,
            locationAccuracyMeters = latestLocationAccuracyMeters,
            candidatePayloads = validCandidates
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
