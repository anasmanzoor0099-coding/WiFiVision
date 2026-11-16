package com.wifivision.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationModelsTest {

    @Test
    fun sanitizeSsid_rejectsPlaceholdersAndTrimsQuotes() {
        assertNull(ObservationPayload.sanitizeSsid(null))
        assertNull(ObservationPayload.sanitizeSsid(""))
        assertNull(ObservationPayload.sanitizeSsid("   "))
        assertNull(ObservationPayload.sanitizeSsid("<unknown ssid>"))
        assertNull(ObservationPayload.sanitizeSsid("SSID unavailable"))
        assertNull(ObservationPayload.sanitizeSsid("Not connected"))
        assertEquals("Campus_WiFi", ObservationPayload.sanitizeSsid("\"Campus_WiFi\""))
    }

    @Test
    fun sanitizeBssid_rejectsPlaceholderAndNormalizesValidMac() {
        assertNull(ObservationPayload.sanitizeBssid(null))
        assertNull(ObservationPayload.sanitizeBssid("02:00:00:00:00:00"))
        assertNull(ObservationPayload.sanitizeBssid("not-a-mac"))
        assertEquals("AA:BB:CC:DD:EE:FF", ObservationPayload.sanitizeBssid("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun parseSecurity_mapsSaeAndRsnCorrectly() {
        assertEquals("WPA3", ObservationPayload.parseSecurity("[WPA2-PSK-CCMP][SAE-CCMP][ESS]"))
        assertEquals("WPA3", ObservationPayload.parseSecurity("[WPA3-SAE]"))
        assertEquals("WPA2", ObservationPayload.parseSecurity("[WPA2-PSK-CCMP][ESS]"))
        assertEquals("WPA2", ObservationPayload.parseSecurity("[RSN-PSK-CCMP]"))
        assertEquals("WPA", ObservationPayload.parseSecurity("[WPA-PSK-TKIP]"))
        assertEquals("WEP", ObservationPayload.parseSecurity("[WEP]"))
        assertEquals("Open", ObservationPayload.parseSecurity("[ESS]"))
        assertEquals("Open", ObservationPayload.parseSecurity("[OWE]"))
        assertEquals("Unknown", ObservationPayload.parseSecurity(""))
        assertEquals("Unknown", ObservationPayload.parseSecurity(null))
    }

    @Test
    fun validation_rejectsInvalidSignalsCoordinatesAndPlaceholders() {
        val valid = ObservationPayload(
            ssid = "OfficeNet",
            bssid = "AA:BB:CC:11:22:33",
            signalDbm = -58,
            frequencyMhz = 5180,
            band = "5 GHz",
            security = "WPA2",
            signalQuality = 4,
            location = ObservationLocation(29.3956, 71.6836, 12.5),
            observedAtEpochMs = 1_700_000_000_000L
        )
        assertTrue(valid.isValid())

        assertFalse(valid.copy(signalDbm = -200).isValid())
        assertFalse(valid.copy(signalDbm = 5).isValid())
        assertFalse(valid.copy(frequencyMhz = 0).isValid())
        assertFalse(valid.copy(band = "10 GHz").isValid())
        assertFalse(valid.copy(security = "WPA4").isValid())
        assertFalse(valid.copy(signalQuality = 5).isValid())
        assertFalse(valid.copy(ssid = "<unknown ssid>").isValid())
        assertFalse(valid.copy(bssid = "02:00:00:00:00:00").isValid())
        assertFalse(valid.copy(location = ObservationLocation(95.0, 71.6)).isValid())
        assertFalse(valid.copy(location = ObservationLocation(29.3, -190.0)).isValid())
        assertFalse(valid.copy(location = ObservationLocation(29.3, 71.6, -2.0)).isValid())
    }

    @Test
    fun toJsonString_omitsNullHiddenFieldsAndFormatsIsoTimestamp() {
        val hiddenPayload = ObservationPayload(
            ssid = null,
            bssid = null,
            signalDbm = -67,
            frequencyMhz = 2437,
            band = "2.4 GHz",
            security = "WPA3",
            signalQuality = 3,
            location = ObservationLocation(29.3956, 71.6836, 8.0),
            observedAtEpochMs = 1_700_000_000_000L
        )
        val json = hiddenPayload.toJsonString()
        val parsed = JsonSupport.parseJson(json) as Map<*, *>

        assertFalse(parsed.containsKey("ssid"))
        assertFalse(parsed.containsKey("bssid"))
        assertEquals(-67L, parsed["signalDbm"])
        assertEquals(2437L, parsed["frequencyMhz"])
        assertEquals("2.4 GHz", parsed["band"])
        assertEquals("WPA3", parsed["security"])
        assertEquals("android", parsed["source"])
        assertEquals("2023-11-14T22:13:20Z", parsed["observedAt"])
        assertTrue(parsed["location"] is Map<*, *>)
    }
}
