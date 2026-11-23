package com.wifivision.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPresentationUtilsTest {

    @Test
    fun classifySignalTier_thresholdsMapCorrectly() {
        assertEquals(SignalTier.EXCELLENT, NetworkPresentationUtils.classifySignalTier(-45))
        assertEquals(SignalTier.EXCELLENT, NetworkPresentationUtils.classifySignalTier(-58))
        assertEquals(SignalTier.GOOD, NetworkPresentationUtils.classifySignalTier(-59))
        assertEquals(SignalTier.GOOD, NetworkPresentationUtils.classifySignalTier(-68))
        assertEquals(SignalTier.FAIR, NetworkPresentationUtils.classifySignalTier(-69))
        assertEquals(SignalTier.FAIR, NetworkPresentationUtils.classifySignalTier(-78))
        assertEquals(SignalTier.WEAK, NetworkPresentationUtils.classifySignalTier(-79))
        assertEquals(SignalTier.WEAK, NetworkPresentationUtils.classifySignalTier(-96))

        assertEquals("Excellent", NetworkPresentationUtils.signalTierLabel(-50))
        assertEquals("Good", NetworkPresentationUtils.signalTierLabel(-64))
        assertEquals("Fair", NetworkPresentationUtils.signalTierLabel(-74))
        assertEquals("Weak", NetworkPresentationUtils.signalTierLabel(-88))
    }

    @Test
    fun signalProgressPercent_clampsAndScalesBounds() {
        assertEquals(5, NetworkPresentationUtils.signalProgressPercent(-115))
        assertEquals(5, NetworkPresentationUtils.signalProgressPercent(-100))
        assertEquals(100, NetworkPresentationUtils.signalProgressPercent(-35))
        assertEquals(100, NetworkPresentationUtils.signalProgressPercent(-20))

        val mid = NetworkPresentationUtils.signalProgressPercent(-67)
        assertTrue("Midpoint percentage should fall cleanly in 40..70, got $mid", mid in 40..70)
    }

    @Test
    fun signalBarCount_usesQualityHintOrRssiThresholds() {
        assertEquals(3, NetworkPresentationUtils.signalBarCount(-85, qualityHint = 3))
        assertEquals(4, NetworkPresentationUtils.signalBarCount(-52, qualityHint = null))
        assertEquals(3, NetworkPresentationUtils.signalBarCount(-65, qualityHint = null))
        assertEquals(2, NetworkPresentationUtils.signalBarCount(-75, qualityHint = 0))
        assertEquals(1, NetworkPresentationUtils.signalBarCount(-88, qualityHint = null))
        assertEquals(0, NetworkPresentationUtils.signalBarCount(-95, qualityHint = null))
        assertEquals(0, NetworkPresentationUtils.signalBarCount(null, qualityHint = null))
    }

    @Test
    fun classifyAccuracyTier_andBadgeLabel_formatPrecisionCorrectly() {
        assertEquals(AccuracyTier.HIGH, NetworkPresentationUtils.classifyAccuracyTier(8.5))
        assertEquals("±8.5 m • High", NetworkPresentationUtils.accuracyBadgeLabel(8.5))

        assertEquals(AccuracyTier.MODERATE, NetworkPresentationUtils.classifyAccuracyTier(28.0))
        assertEquals("±28.0 m • Moderate", NetworkPresentationUtils.accuracyBadgeLabel(28.0))

        assertEquals(AccuracyTier.LOW, NetworkPresentationUtils.classifyAccuracyTier(75.2))
        assertEquals("±75.2 m • Low", NetworkPresentationUtils.accuracyBadgeLabel(75.2))

        assertEquals(AccuracyTier.UNKNOWN, NetworkPresentationUtils.classifyAccuracyTier(null))
        assertEquals(AccuracyTier.UNKNOWN, NetworkPresentationUtils.classifyAccuracyTier(-5.0))
        assertEquals(AccuracyTier.UNKNOWN, NetworkPresentationUtils.classifyAccuracyTier(Double.NaN))
        assertEquals("No Fix", NetworkPresentationUtils.accuracyBadgeLabel(null))
    }

    @Test
    fun filterNetworks_andCountByBand_groupBandsAccurately() {
        val sample = listOf(
            WifiNetwork("Studio-5G", "AA:BB:CC:DD:EE:01", -52, 5180, "5 GHz", "WPA3", 4),
            WifiNetwork("Studio-2G", "AA:BB:CC:DD:EE:02", -63, 2437, "2.4 GHz", "WPA2", 3),
            WifiNetwork("Guest-5G", "AA:BB:CC:DD:EE:03", -71, 5745, "5 GHz", "Open", 2),
            WifiNetwork(null, "AA:BB:CC:DD:EE:04", -66, 6115, "6 GHz", "WPA3", 3)
        )

        val counts = NetworkPresentationUtils.countByBand(sample)
        assertEquals(4, counts[BandFilter.ALL])
        assertEquals(1, counts[BandFilter.BAND_2_4])
        assertEquals(2, counts[BandFilter.BAND_5])
        assertEquals(1, counts[BandFilter.BAND_6])

        assertEquals(4, NetworkPresentationUtils.filterNetworks(sample, BandFilter.ALL).size)
        assertEquals(listOf("Studio-2G"), NetworkPresentationUtils.filterNetworks(sample, BandFilter.BAND_2_4).map { it.ssid })
        assertEquals(2, NetworkPresentationUtils.filterNetworks(sample, BandFilter.BAND_5).size)
        assertEquals(1, NetworkPresentationUtils.filterNetworks(sample, BandFilter.BAND_6).size)
    }

    @Test
    fun isWeakSecurity_flagsOpenAndWepOnly() {
        assertTrue(NetworkPresentationUtils.isWeakSecurity("Open"))
        assertTrue(NetworkPresentationUtils.isWeakSecurity("WEP"))
        assertFalse(NetworkPresentationUtils.isWeakSecurity("WPA2"))
        assertFalse(NetworkPresentationUtils.isWeakSecurity("WPA3"))
        assertFalse(NetworkPresentationUtils.isWeakSecurity("Unknown"))
    }
}
