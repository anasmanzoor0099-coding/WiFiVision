package com.wifivision.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationSessionControllerTest {

    private fun sampleNetwork(
        ssid: String? = "Campus_WiFi",
        bssid: String? = "AA:BB:CC:11:22:33",
        signalDbm: Int = -59
    ): WifiNetwork {
        return WifiNetwork(
            ssid = ssid,
            bssid = bssid,
            signalDbm = signalDbm,
            frequencyMhz = 5180,
            band = "5 GHz",
            security = "WPA3",
            signalQuality = 4
        )
    }

    private fun sampleWifiDetails(connected: Boolean = true): WifiDetails {
        return WifiDetails(
            connected = connected,
            ssid = if (connected) "IUB Guest" else null,
            bssid = if (connected) "78:72:5D:99:AE:5A" else null,
            ssidUnavailable = false,
            signalDbm = if (connected) -68 else null,
            frequencyMhz = if (connected) 2462 else null,
            linkSpeedMbps = if (connected) 65 else null,
            band = if (connected) "2.4 GHz" else "Unknown",
            signalQuality = if (connected) 3 else null,
            internetAvailable = connected
        )
    }

    @Test
    fun appStartup_doesNotDrainPreExistingQueue_andAutoUploadIsOff() {
        val queue = ObservationQueueLogic()
        queue.enqueueObservations(
            listOf(
                ObservationPayload(
                    ssid = "SavedNet",
                    bssid = "AA:BB:CC:DD:EE:01",
                    signalDbm = -62,
                    frequencyMhz = 2437,
                    band = "2.4 GHz",
                    security = "WPA2",
                    signalQuality = 3,
                    location = null,
                    observedAtEpochMs = 1_700_000_000_000L
                )
            ),
            nowMs = 1_000_000L
        )

        val controller = ObservationSessionController(queue)
        assertFalse(controller.autoUploadEnabled)

        val startup = controller.onAppStartup()
        assertFalse(startup.shouldDrainQueue)
        assertEquals(1, startup.pendingQueueCount)
    }

    @Test
    fun scanOnly_updatesLocalState_andNeverEnqueuesOrDrains() {
        val controller = ObservationSessionController()
        controller.isScanInProgress = true
        val loc = ObservationLocation(29.374878, 71.752417, 100.0)
        controller.onGpsUpdated(loc, 100.0, 2_000_000L)

        val result = controller.onScanCompleted(
            networks = listOf(sampleNetwork(), sampleNetwork(ssid = null, bssid = null, signalDbm = -75)),
            activeWifi = sampleWifiDetails(connected = true),
            location = loc,
            scannedAtMs = 2_000_500L
        )

        assertFalse(controller.isScanInProgress)
        assertEquals(2, result.detectedCount)
        assertEquals(0, result.enqueuedCount)
        assertFalse(result.shouldDrainQueue)
        assertEquals(0, controller.queueLogic.size())
        assertEquals(2, controller.lastScanNetworks.size)
        assertEquals(2_000_500L, controller.lastScanTimestampMs)
    }

    @Test
    fun monitoring_updatesConnectionLocally_andNeverEnqueuesOrDrains() {
        val controller = ObservationSessionController()

        val startDecision = controller.onMonitoringStarted(started = true)
        assertTrue(controller.isMonitoringActive)
        assertEquals(0, startDecision.enqueuedCount)
        assertFalse(startDecision.shouldDrainQueue)
        assertEquals(0, controller.queueLogic.size())

        val updateDecision = controller.onMonitorUpdated(sampleWifiDetails(connected = true), 3_000_000L)
        assertEquals(0, updateDecision.enqueuedCount)
        assertFalse(updateDecision.shouldDrainQueue)
        assertEquals(0, controller.queueLogic.size())
        assertEquals("IUB Guest", controller.latestWifiDetails?.ssid)
    }

    @Test
    fun manualUpload_requiresExplicitConfirmationBeforeEnqueueAndDrain() {
        val controller = ObservationSessionController()
        val loc = ObservationLocation(29.374878, 71.752417, 25.0)
        controller.onGpsUpdated(loc, 25.0, 4_000_000L)
        controller.onScanCompleted(
            networks = listOf(sampleNetwork()),
            activeWifi = sampleWifiDetails(connected = true),
            location = loc,
            scannedAtMs = 4_000_500L
        )

        // Preview step does not mutate queue
        val preview = controller.prepareUploadConfirmation(4_001_000L)
        assertNotNull(preview)
        assertEquals(2, preview!!.readyLocalCount)
        assertEquals(0, preview.existingPendingQueueCount)
        assertEquals(0, controller.queueLogic.size())

        // Cancelling confirmation leaves queue empty
        val cancelled = controller.confirmAndEnqueueUpload(confirmed = false, nowMs = 4_001_500L)
        assertFalse(cancelled.confirmed)
        assertFalse(cancelled.shouldDrainQueue)
        assertNull(cancelled.enqueueSummary)
        assertEquals(0, controller.queueLogic.size())

        // Re-preparing and confirming enqueues and permits drain
        assertNotNull(controller.prepareUploadConfirmation(4_002_000L))
        val confirmed = controller.confirmAndEnqueueUpload(confirmed = true, nowMs = 4_002_500L)
        assertTrue(confirmed.confirmed)
        assertTrue(confirmed.shouldDrainQueue)
        assertEquals(2, confirmed.enqueueSummary?.acceptedCount)
        assertEquals(2, controller.queueLogic.size())
    }

    @Test
    fun healthCheck_updatesBannerConsistentlyWithoutDrainingQueue() {
        val queue = ObservationQueueLogic()
        val controller = ObservationSessionController(queue)

        val offline = controller.onHealthCheckCompleted(
            HealthCheckResult(
                reachable = false,
                healthy = false,
                statusCode = null,
                databaseStatus = null,
                message = "Unreachable: Failed to connect"
            )
        )
        assertEquals("OFFLINE", offline.pillLabel)
        assertTrue(offline.bannerText.contains("Unreachable"))
        assertFalse(offline.shouldDrainQueue)

        val healthy = controller.onHealthCheckCompleted(
            HealthCheckResult(
                reachable = true,
                healthy = true,
                statusCode = 200,
                databaseStatus = "connected",
                message = "Connected (connected)"
            )
        )
        assertEquals("CONNECTED", healthy.pillLabel)
        assertEquals("Backend health: Connected (database connected)", healthy.bannerText)
        assertFalse(healthy.shouldDrainQueue)

        val degraded = controller.onHealthCheckCompleted(
            HealthCheckResult(
                reachable = true,
                healthy = false,
                statusCode = 503,
                databaseStatus = "disconnected",
                message = "Backend degraded (database disconnected)"
            )
        )
        assertEquals("DEGRADED", degraded.pillLabel)
        assertTrue(degraded.bannerText.contains("degraded"))
        assertFalse(degraded.shouldDrainQueue)
    }

    @Test
    fun lifecycleCleanup_resetsInProgressScanMonitoringAndConfirmation() {
        val controller = ObservationSessionController()
        controller.isScanInProgress = true
        controller.onMonitoringStarted(started = true)
        controller.onScanCompleted(
            networks = listOf(sampleNetwork()),
            activeWifi = null,
            location = null,
            scannedAtMs = 5_000_000L
        )
        controller.isScanInProgress = true
        assertNotNull(controller.prepareUploadConfirmation(5_001_000L))

        val cleanup = controller.onStopCleanup()
        assertTrue(cleanup.cancelledScan)
        assertTrue(cleanup.stoppedMonitoring)
        assertTrue(cleanup.clearedPendingConfirmation)
        assertFalse(controller.isScanInProgress)
        assertFalse(controller.isMonitoringActive)
        assertNull(controller.pendingUploadConfirmation)
    }

    @Test
    fun activeConnection_prefersFreshMatchingScanSecurity_andNeverGuessesUnknown() {
        // Android 12+ WifiInfo security constant mapping never guesses PSK/EAP
        assertEquals("Open", WifiMonitor.mapWifiInfoSecurityType(0))   // SECURITY_TYPE_OPEN
        assertEquals("WEP", WifiMonitor.mapWifiInfoSecurityType(1))    // SECURITY_TYPE_WEP
        assertEquals("Unknown", WifiMonitor.mapWifiInfoSecurityType(2)) // SECURITY_TYPE_PSK (ambiguous WPA/WPA2)
        assertEquals("Unknown", WifiMonitor.mapWifiInfoSecurityType(3)) // SECURITY_TYPE_EAP
        assertEquals("WPA3", WifiMonitor.mapWifiInfoSecurityType(4))   // SECURITY_TYPE_SAE
        assertEquals("Open", WifiMonitor.mapWifiInfoSecurityType(6))   // SECURITY_TYPE_OWE
        assertEquals("WPA3", WifiMonitor.mapWifiInfoSecurityType(9))   // SECURITY_TYPE_EAP_WPA3_ENTERPRISE
        assertEquals("Unknown", WifiMonitor.mapWifiInfoSecurityType(-1))

        val controller = ObservationSessionController()
        val connectedDetails = WifiDetails(
            connected = true,
            ssid = "IUB Guest",
            bssid = "78:72:5D:99:AE:5A",
            ssidUnavailable = false,
            signalDbm = -55,
            frequencyMhz = 2462,
            linkSpeedMbps = 65,
            band = "2.4 GHz",
            signalQuality = 4,
            internetAvailable = true,
            security = "Unknown"
        )
        val matchingScan = WifiNetwork(
            ssid = "IUB Guest",
            bssid = "78:72:5d:99:ae:5a",
            signalDbm = -57,
            frequencyMhz = 2462,
            band = "2.4 GHz",
            security = "WPA2",
            signalQuality = 4
        )
        val otherApSameSsid = WifiNetwork(
            ssid = "IUB Guest",
            bssid = "AA:BB:CC:DD:EE:99",
            signalDbm = -72,
            frequencyMhz = 2462,
            band = "2.4 GHz",
            security = "WPA2",
            signalQuality = 3
        )

        controller.onScanCompleted(
            networks = listOf(matchingScan, otherApSameSsid),
            activeWifi = connectedDetails,
            location = null,
            scannedAtMs = 10_000_000L
        )

        val preview = controller.prepareUploadConfirmation(10_005_000L)
        assertNotNull(preview)
        // Connected BSSID merged with ScanResult -> 2 distinct BSSIDs (not 3), and connected AP gets WPA2
        assertEquals(2, preview!!.readyLocalCount)
        val connectedPayload = preview.candidatePayloads.first { it.bssid == "78:72:5D:99:AE:5A" }
        assertEquals("WPA2", connectedPayload.security)
        assertEquals(-55, connectedPayload.signalDbm)

        // When scan is stale (> 5 min), do not borrow stale ScanResult security; preserve Unknown
        val staleController = ObservationSessionController()
        staleController.onScanCompleted(
            networks = listOf(matchingScan),
            activeWifi = connectedDetails,
            location = null,
            scannedAtMs = 1_000_000L
        )
        val stalePreview = staleController.prepareUploadConfirmation(1_000_000L + ObservationSessionController.FRESH_SCAN_MATCH_WINDOW_MS + 1_000L)
        val staleConnected = stalePreview!!.candidatePayloads.first { it.bssid == "78:72:5D:99:AE:5A" }
        assertEquals("Unknown", staleConnected.security)
    }

    @Test
    fun redactedSsid_isNeverConvertedToFabricatedHiddenNetwork() {
        val redactedDetails = WifiDetails(
            connected = true,
            ssid = null,
            bssid = "78:72:5D:99:AE:5A",
            ssidUnavailable = true,
            signalDbm = -60,
            frequencyMhz = 2462,
            linkSpeedMbps = 65,
            band = "2.4 GHz",
            signalQuality = 3,
            internetAvailable = true,
            security = "Unknown"
        )

        // Standalone WifiDetails.toObservationPayload must refuse redacted SSID
        assertNull(redactedDetails.toObservationPayload(location = null, observedAtEpochMs = 1_000_000L))

        // Controller without matching scan must also refuse to upload redacted active snapshot
        val controllerWithoutScan = ObservationSessionController()
        controllerWithoutScan.onMonitorUpdated(redactedDetails, 2_000_000L)
        assertNull(controllerWithoutScan.prepareUploadConfirmation(2_001_000L))

        // If a fresh matching BSSID scan establishes the real SSID safely, identity & security resolve cleanly
        val controllerWithScan = ObservationSessionController()
        controllerWithScan.onScanCompleted(
            networks = listOf(
                WifiNetwork(
                    ssid = "IUB Guest",
                    bssid = "78:72:5D:99:AE:5A",
                    signalDbm = -61,
                    frequencyMhz = 2462,
                    band = "2.4 GHz",
                    security = "WPA2",
                    signalQuality = 3
                )
            ),
            activeWifi = redactedDetails,
            location = null,
            scannedAtMs = 3_000_000L
        )
        val resolvedPreview = controllerWithScan.prepareUploadConfirmation(3_001_000L)
        assertNotNull(resolvedPreview)
        assertEquals(1, resolvedPreview!!.readyLocalCount)
        assertEquals("IUB Guest", resolvedPreview.candidatePayloads.first().ssid)
        assertEquals("WPA2", resolvedPreview.candidatePayloads.first().security)
    }

    @Test
    fun enqueueEligibleObservations_queuesScanResultsAndUpgradesCoarseGps() {
        val controller = ObservationSessionController()
        val coarseLoc = ObservationLocation(29.374878, 71.752417, 100.0)
        controller.onGpsUpdated(coarseLoc, 100.0, 4_000_000L)

        controller.onScanCompleted(
            networks = listOf(
                sampleNetwork(ssid = "Lab_5G", bssid = "AA:BB:CC:11:22:33", signalDbm = -58),
                sampleNetwork(ssid = "Lab_2G", bssid = "AA:BB:CC:11:22:44", signalDbm = -67)
            ),
            activeWifi = null,
            location = coarseLoc,
            scannedAtMs = 4_000_500L
        )

        val summary = controller.enqueueEligibleObservations(nowMs = 4_001_000L, applyBatchCap = false)
        assertEquals(2, summary.acceptedCount)
        assertEquals(2, controller.queueLogic.size())
        assertEquals(0, controller.countEligibleLocalObservations(4_001_100L))

        // While queued, duplicate scans at same location do not create duplicate queue rows.
        val dupSummary = controller.enqueueEligibleObservations(nowMs = 4_005_000L, applyBatchCap = false)
        assertEquals(0, dupSummary.acceptedCount)
        assertEquals(2, controller.queueLogic.size())

        // Better live GPS fix upgrades both local scan results and queued items in place.
        val accurateLoc = ObservationLocation(29.374900, 71.752440, 12.0)
        controller.onGpsUpdated(accurateLoc, 12.0, 4_006_000L)
        assertEquals(12.0, controller.lastScanNetworks.first().lastLocation?.accuracyMeters)
        assertEquals(12.0, controller.queueLogic.items().first().payload.location?.accuracyMeters)

        // Confirm upload on server for both queued items.
        val firstQueued = controller.queueLogic.peekNextReady(4_006_500L)!!
        assertTrue(controller.queueLogic.markSuccess(firstQueued.id, 4_007_000L))
        val secondQueued = controller.queueLogic.peekNextReady(4_008_000L)!!
        assertTrue(controller.queueLogic.markSuccess(secondQueued.id, 4_008_500L))
        assertEquals(0, controller.queueLogic.size())
    }
}
