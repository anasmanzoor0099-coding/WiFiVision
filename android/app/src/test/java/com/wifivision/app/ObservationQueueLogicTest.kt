package com.wifivision.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationQueueLogicTest {

    private fun samplePayload(
        ssid: String = "TestNet",
        bssid: String = "AA:BB:CC:DD:EE:01",
        signalDbm: Int = -60,
        lat: Double = 29.3956,
        lon: Double = 71.6836,
        observedAtMs: Long = 1_700_000_000_000L
    ): ObservationPayload {
        return ObservationPayload(
            ssid = ssid,
            bssid = bssid,
            signalDbm = signalDbm,
            frequencyMhz = 2437,
            band = "2.4 GHz",
            security = "WPA2",
            signalQuality = 3,
            location = ObservationLocation(lat, lon, 10.0),
            observedAtEpochMs = observedAtMs
        )
    }

    @Test
    fun deduplicatesRepeatedObservationsUnlessSignificantChange() {
        val logic = ObservationQueueLogic()
        val t0 = 1_000_000L

        val first = logic.enqueueObservations(listOf(samplePayload()), t0)
        assertEquals(1, first.acceptedCount)

        // Same AP 10 seconds later -> skipped as duplicate
        val second = logic.enqueueObservations(listOf(samplePayload()), t0 + 10_000L)
        assertEquals(0, second.acceptedCount)
        assertEquals(1, second.skippedDuplicateCount)

        // Simulate upload completion so item is no longer pending in queue
        val item = logic.peekNextReady(t0 + 10_000L)
        assertNotNull(item)
        logic.markSuccess(item!!.id, t0 + 11_000L)

        // Same signal after 40s -> still deduplicated within 5m window
        val third = logic.enqueueObservations(listOf(samplePayload(signalDbm = -62)), t0 + 40_000L)
        assertEquals(0, third.acceptedCount)
        assertEquals(1, third.skippedDuplicateCount)

        // Significant RSSI shift (>= 8 dBm) after 45s -> accepted
        val fourth = logic.enqueueObservations(listOf(samplePayload(signalDbm = -72)), t0 + 45_000L)
        assertEquals(1, fourth.acceptedCount)
    }

    @Test
    fun enforcesBoundedCapacityAndBatchCap() {
        val logic = ObservationQueueLogic(maxQueueSize = 5, maxBatchPerScan = 3)
        val batch = (1..6).map { idx ->
            samplePayload(
                ssid = "Net_$idx",
                bssid = "AA:BB:CC:DD:EE:0$idx"
            )
        }
        val firstSummary = logic.enqueueObservations(batch, nowMs = 1_000_000L)
        assertEquals(3, firstSummary.acceptedCount)
        assertEquals(3, firstSummary.skippedBatchCapCount)
        assertEquals(3, logic.size())

        val secondBatch = (7..9).map { idx ->
            samplePayload(
                ssid = "Net_$idx",
                bssid = "AA:BB:CC:DD:EE:0$idx"
            )
        }
        val secondSummary = logic.enqueueObservations(secondBatch, nowMs = 1_001_000L)
        assertEquals(3, secondSummary.acceptedCount)
        assertEquals(1, secondSummary.droppedOldestCount)
        assertEquals(5, logic.size())
    }

    @Test
    fun handlesRateLimitsBackoffRetryLimitsAndPersistence() {
        val logic = ObservationQueueLogic(maxRetryAttempts = 3, minUploadSpacingMs = 1_500L)
        val t0 = 5_000_000L

        logic.enqueueObservations(listOf(samplePayload()), t0)
        val item = logic.peekNextReady(t0)!!

        // 429 pauses queue for Retry-After
        val pauseMs = logic.markRateLimited(retryAfterSeconds = 45L, nowMs = t0)
        assertTrue(pauseMs >= 60_000L) // max(default 60s, Retry-After 45s)
        assertNull(logic.peekNextReady(t0 + 10_000L))
        assertNotNull(logic.peekNextReady(t0 + pauseMs))

        // Transient failure schedules exponential backoff and pauses on max attempts without deleting queued records
        assertTrue(logic.markTransientFailure(item.id, t0 + pauseMs, "Offline"))
        assertEquals(1, logic.size())
        assertEquals(1, logic.failedItemCount())
        assertTrue(logic.markTransientFailure(item.id, t0 + pauseMs + 20_000L, "Timeout"))
        assertFalse(logic.markTransientFailure(item.id, t0 + pauseMs + 50_000L, "Still offline"))
        assertEquals(1, logic.size())
        assertNull(logic.nextWakeDelayMs(t0 + pauseMs + 51_000L))
        assertNull(logic.peekNextReady(t0 + pauseMs + 51_000L))

        // Manual sync / recovery resets retry timers so preserved observations upload
        logic.resetRetryTimersForImmediateSync(t0 + pauseMs + 60_000L)
        assertNotNull(logic.peekNextReady(t0 + pauseMs + 60_000L))

        // Upgrade coarse or missing GPS location on queued item
        val upgraded = logic.upgradeQueuedLocations(ObservationLocation(29.3960, 71.6840, 8.0))
        assertEquals(0, upgraded) // Already had 10.0m high accuracy

        logic.markSuccess(item.id, t0 + pauseMs + 61_000L)
        assertEquals(0, logic.size())
        assertEquals(0, logic.failedItemCount())

        // Server-confirmed deduplication only starts at markSuccess (un-uploaded enqueue never blocks retry after removal)
        val unconfirmed = samplePayload(ssid = "TempNet", bssid = "AA:BB:CC:DD:EE:88", signalDbm = -70)
        assertEquals(1, logic.enqueueObservations(listOf(unconfirmed), t0 + 180_000L).acceptedCount)
        val tempQueued = logic.peekNextReady(t0 + 185_000L)!!
        logic.markClientRejected(tempQueued.id, t0 + 186_000L)
        assertEquals(1, logic.enqueueObservations(listOf(unconfirmed), t0 + 190_000L).acceptedCount)
        val reQueued = logic.peekNextReady(t0 + 195_000L)!!
        logic.markClientRejected(reQueued.id, t0 + 196_000L)

        // Persistence round-trip with coarse location upgrade
        val coarsePayload = ObservationPayload(
            ssid = "CoarseNet",
            bssid = "11:22:33:44:55:66",
            signalDbm = -64,
            frequencyMhz = 2437,
            band = "2.4 GHz",
            security = "WPA2",
            signalQuality = 3,
            location = ObservationLocation(29.3956, 71.6836, 100.0),
            observedAtEpochMs = t0 + 200_000L
        )
        logic.enqueueObservations(listOf(coarsePayload), t0 + 200_000L)
        assertEquals(1, logic.upgradeQueuedLocations(ObservationLocation(29.3959, 71.6839, 12.0)))
        val exported = logic.exportStateJson()
        val restored = ObservationQueueLogic()
        restored.restoreStateJson(exported)
        assertEquals(1, restored.size())
        assertEquals("11:22:33:44:55:66", restored.items().first().payload.bssid)
        assertEquals(12.0, restored.items().first().payload.location?.accuracyMeters)
    }
}
