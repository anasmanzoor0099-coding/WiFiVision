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

        // Transient failure schedules exponential backoff and drops on max attempts
        assertTrue(logic.markTransientFailure(item.id, t0 + pauseMs))
        assertEquals(1, logic.size())
        assertTrue(logic.markTransientFailure(item.id, t0 + pauseMs + 20_000L))
        assertFalse(logic.markTransientFailure(item.id, t0 + pauseMs + 50_000L))
        assertEquals(0, logic.size())

        // Persistence round-trip
        logic.enqueueObservations(listOf(samplePayload(bssid = "11:22:33:44:55:66")), t0 + 200_000L)
        val exported = logic.exportStateJson()
        val restored = ObservationQueueLogic()
        restored.restoreStateJson(exported)
        assertEquals(1, restored.size())
        assertEquals("11:22:33:44:55:66", restored.items().first().payload.bssid)
    }
}
