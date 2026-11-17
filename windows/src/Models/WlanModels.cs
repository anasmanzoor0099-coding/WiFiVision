using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text.RegularExpressions;

namespace WiFiVision.Windows.Models
{
    public sealed class WifiAdapterInfo
    {
        public string InterfaceGuid { get; set; }
        public string Description { get; set; }
        public string State { get; set; } // Connected, Disconnected, Disabled, Unavailable
        public bool IsRadioReady { get; set; }
        public string ConnectedSsid { get; set; }
    }

    public sealed class ObservationLocation
    {
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        public double? AccuracyMeters { get; set; }

        public bool IsValid()
        {
            if (double.IsNaN(Latitude) || double.IsInfinity(Latitude) || Latitude < -90.0 || Latitude > 90.0)
                return false;
            if (double.IsNaN(Longitude) || double.IsInfinity(Longitude) || Longitude < -180.0 || Longitude > 180.0)
                return false;
            if (AccuracyMeters.HasValue && (double.IsNaN(AccuracyMeters.Value) || double.IsInfinity(AccuracyMeters.Value) || AccuracyMeters.Value < 0.0))
                return false;
            return true;
        }
    }

    public sealed class WindowsScanNetwork
    {
        public string Ssid { get; set; } // null when hidden or unavailable
        public bool IsHiddenSsid { get; set; }
        public string Bssid { get; set; } // uppercase AA:BB:CC:DD:EE:FF or null
        public int SignalDbm { get; set; }
        public int LinkQualityPercent { get; set; }
        public int SignalQuality { get; set; } // 0..4
        public int FrequencyMhz { get; set; }
        public int? Channel { get; set; }
        public string Band { get; set; } // 2.4 GHz, 5 GHz, 6 GHz, Unknown
        public string Security { get; set; } // Open, WEP, WPA, WPA2, WPA3, Unknown

        public string DisplaySsid
        {
            get { return string.IsNullOrWhiteSpace(Ssid) ? "Hidden Network" : Ssid; }
        }

        public ObservationPayload ToObservationPayload(ObservationLocation location, long observedAtEpochMs)
        {
            return new ObservationPayload
            {
                Ssid = Ssid,
                Bssid = Bssid,
                SignalDbm = SignalDbm,
                FrequencyMhz = FrequencyMhz,
                Band = Band,
                Security = Security,
                SignalQuality = SignalQuality,
                Location = location,
                Source = ObservationPayload.SourceWindows,
                ObservedAtEpochMs = observedAtEpochMs
            };
        }
    }

    public enum WlanScanErrorKind
    {
        None = 0,
        MissingAdapter = 1,
        WifiDisabled = 2,
        PermissionDenied = 3,
        UnsupportedHardware = 4,
        ServiceUnavailable = 5,
        ScanFailed = 6
    }

    public sealed class WindowsScanOutcome
    {
        public bool Success { get; set; }
        public WlanScanErrorKind ErrorKind { get; set; }
        public string StatusMessage { get; set; }
        public string LocationMessage { get; set; }
        public WifiAdapterInfo Adapter { get; set; }
        public ObservationLocation Location { get; set; }
        public long ScannedAtEpochMs { get; set; }
        public List<WindowsScanNetwork> Networks { get; set; }

        public WindowsScanOutcome()
        {
            Networks = new List<WindowsScanNetwork>();
        }
    }

    public sealed class ObservationPayload
    {
        public const string SourceWindows = "windows";
        private static readonly Regex BssidRegex = new Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$", RegexOptions.Compiled);
        public static readonly HashSet<string> ValidBands = new HashSet<string> { "2.4 GHz", "5 GHz", "6 GHz", "Unknown" };
        public static readonly HashSet<string> ValidSecurity = new HashSet<string> { "Open", "WEP", "WPA", "WPA2", "WPA3", "Unknown" };

        public string Ssid { get; set; }
        public string Bssid { get; set; }
        public int SignalDbm { get; set; }
        public int FrequencyMhz { get; set; }
        public string Band { get; set; }
        public string Security { get; set; }
        public int? SignalQuality { get; set; }
        public ObservationLocation Location { get; set; }
        public string Source { get; set; }
        public long ObservedAtEpochMs { get; set; }

        public ObservationPayload()
        {
            Band = "Unknown";
            Security = "Unknown";
            Source = SourceWindows;
        }

        public string DedupeKey()
        {
            string identity = !string.IsNullOrEmpty(Bssid)
                ? Bssid.ToLowerInvariant()
                : "ssid:" + (string.IsNullOrEmpty(Ssid) ? "hidden" : Ssid.ToLowerInvariant());
            return identity + "|" + Band;
        }

        public bool IsValid()
        {
            if (Bssid != null && !BssidRegex.IsMatch(Bssid.Trim())) return false;
            if (SignalDbm < -127 || SignalDbm > 0) return false;
            if (FrequencyMhz <= 0) return false;
            if (!ValidBands.Contains(Band ?? "Unknown")) return false;
            if (!ValidSecurity.Contains(Security ?? "Unknown")) return false;
            if (SignalQuality.HasValue && (SignalQuality.Value < 0 || SignalQuality.Value > 4)) return false;
            if (Location != null && !Location.IsValid()) return false;
            if (Source != SourceWindows) return false;
            if (ObservedAtEpochMs <= 0) return false;
            return true;
        }

        public Dictionary<string, object> ToApiDictionary()
        {
            var dict = new Dictionary<string, object>();
            if (!string.IsNullOrWhiteSpace(Ssid))
                dict["ssid"] = Ssid.Trim().Length > 100 ? Ssid.Trim().Substring(0, 100) : Ssid.Trim();
            if (!string.IsNullOrWhiteSpace(Bssid))
                dict["bssid"] = Bssid.Trim().ToUpperInvariant();
            dict["signalDbm"] = SignalDbm;
            dict["frequencyMhz"] = FrequencyMhz;
            dict["band"] = Band ?? "Unknown";
            dict["security"] = Security ?? "Unknown";
            if (SignalQuality.HasValue)
                dict["signalQuality"] = SignalQuality.Value;
            if (Location != null && Location.IsValid())
            {
                var loc = new Dictionary<string, object>
                {
                    { "latitude", Location.Latitude },
                    { "longitude", Location.Longitude }
                };
                if (Location.AccuracyMeters.HasValue)
                    loc["accuracyMeters"] = Location.AccuracyMeters.Value;
                dict["location"] = loc;
            }
            dict["source"] = SourceWindows;
            dict["observedAt"] = DateTimeOffset.FromUnixTimeMilliseconds(ObservedAtEpochMs).UtcDateTime.ToString("O", CultureInfo.InvariantCulture);
            return dict;
        }
    }

    public static class WlanTaxonomy
    {
        public static string ClassifyBand(int frequencyMhz)
        {
            if (frequencyMhz >= 2400 && frequencyMhz <= 2500) return "2.4 GHz";
            if (frequencyMhz >= 4900 && frequencyMhz <= 5900) return "5 GHz";
            if (frequencyMhz >= 5925 && frequencyMhz <= 7125) return "6 GHz";
            return "Unknown";
        }

        public static int? DeriveChannelFromFrequencyMhz(int frequencyMhz)
        {
            if (frequencyMhz == 2484) return 14;
            if (frequencyMhz >= 2412 && frequencyMhz <= 2472 && (frequencyMhz - 2412) % 5 == 0)
                return ((frequencyMhz - 2412) / 5) + 1;
            if (frequencyMhz >= 5160 && frequencyMhz <= 5885 && (frequencyMhz - 5000) % 5 == 0)
                return (frequencyMhz - 5000) / 5;
            if (frequencyMhz >= 5955 && frequencyMhz <= 7115 && (frequencyMhz - 5950) % 5 == 0)
                return (frequencyMhz - 5950) / 5;
            return null;
        }

        public static int MapLinkQualityToSignalQuality(int linkQualityPercent)
        {
            int clamped = Math.Max(0, Math.Min(100, linkQualityPercent));
            int tier = (int)Math.Round(clamped / 25.0, MidpointRounding.AwayFromZero);
            return Math.Max(0, Math.Min(4, tier));
        }

        public static string MapDot11AuthAndCipher(int authAlgorithm, int cipherAlgorithm, bool securityEnabled)
        {
            if (!securityEnabled && authAlgorithm == 1)
                return "Open";

            switch (authAlgorithm)
            {
                case 1: // DOT11_AUTH_ALGO_80211_OPEN
                    return (cipherAlgorithm == 1 || cipherAlgorithm == 5) && securityEnabled ? "WEP" : "Open";
                case 2: // DOT11_AUTH_ALGO_80211_SHARED_KEY
                    return "WEP";
                case 3: // DOT11_AUTH_ALGO_WPA
                case 4: // DOT11_AUTH_ALGO_WPA_PSK
                case 5: // DOT11_AUTH_ALGO_WPA_NONE
                    return "WPA";
                case 6: // DOT11_AUTH_ALGO_RSNA
                case 7: // DOT11_AUTH_ALGO_RSNA_PSK
                    return "WPA2";
                case 8:  // DOT11_AUTH_ALGO_WPA3_ENT_192
                case 9:  // DOT11_AUTH_ALGO_WPA3_SAE
                case 11: // DOT11_AUTH_ALGO_WPA3_ENT
                    return "WPA3";
                case 10: // DOT11_AUTH_ALGO_OWE
                    return "Open";
                default:
                    if (!securityEnabled) return "Open";
                    if (cipherAlgorithm == 1 || cipherAlgorithm == 5) return "WEP";
                    return "Unknown";
            }
        }
    }
}
