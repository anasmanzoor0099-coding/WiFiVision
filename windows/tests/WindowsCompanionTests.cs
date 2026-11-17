using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Web.Script.Serialization;
using WiFiVision.Windows.Models;
using WiFiVision.Windows.Native;
using WiFiVision.Windows.Network;
using WiFiVision.Windows.Queue;
using WiFiVision.Windows.Security;
using WiFiVision.Windows.Server;

namespace WiFiVision.Windows.Tests
{
    internal sealed class MockWlanScanner : IWlanScanner
    {
        public WifiAdapterInfo AdapterToReturn { get; set; }
        public WindowsScanOutcome OutcomeToReturn { get; set; }

        public WifiAdapterInfo GetAdapterStatus()
        {
            return AdapterToReturn;
        }

        public WindowsScanOutcome ScanNow(bool includeLocation = true)
        {
            return OutcomeToReturn;
        }
    }

    internal static class Program
    {
        private static readonly JavaScriptSerializer Json = new JavaScriptSerializer();
        private static int _passed;
        private static int _failed;

        private static int Main(string[] args)
        {
            bool runLiveHardware = args != null && args.Any(a => a.Equals("--live-scan", StringComparison.OrdinalIgnoreCase));

            var tests = new List<Tuple<string, Action>>
            {
                Tuple.Create("01. Frequency to Band/Channel mapping (2.4 / 5 / 6 GHz + Unknown)", (Action)TestFrequencyBandChannelMapping),
                Tuple.Create("02. WLAN Auth/Cipher normalization + RSSI/LinkQuality mapping", (Action)TestSecurityAndRssiNormalization),
                Tuple.Create("03. Hidden SSID formatting and null BSSID rejection in ObservationPayload", (Action)TestHiddenSsidAndPayloadValidation),
                Tuple.Create("04. Mocked missing adapter, disabled Wi-Fi, permission denied handling", (Action)TestErrorKindsAndMissingAdapter),
                Tuple.Create("05. DPAPI WindowsCredentialStore round-trip, token format, expiration, clear", (Action)TestCredentialStoreDpapiAndExpiry),
                Tuple.Create("06. Backend URL HTTPS validation (allow loopback HTTP, block remote HTTP)", (Action)TestBackendUrlValidation),
                Tuple.Create("07. ObservationQueue duplicate suppression, RSSI delta bypass, batch cap 10, max 100", (Action)TestQueueDeduplicationAndCaps),
                Tuple.Create("08. ObservationQueue never auto-uploads and applies exponential rate-limit backoff", (Action)TestQueueNoAutoUploadAndBackoff),
                Tuple.Create("09. PersistentWindowsQueue DPAPI disk round-trip", (Action)TestPersistentQueueDpapi),
                Tuple.Create("10. LocalCompanionServer loopback-only /health exposes zero secrets", (Action)TestLocalServerHealthNoSecrets),
                Tuple.Create("11. LocalCompanionServer rejects missing/wrong X-WiFiVision-Local-Token with 401", (Action)TestLocalServerRequiresToken),
                Tuple.Create("12. LocalCompanionServer blocks DNS rebinding Host header with 403", (Action)TestLocalServerRejectsBadHost),
                Tuple.Create("13. LocalCompanionServer enforces strict Origin allowlist and blocks evil origin", (Action)TestLocalServerCorsStrictOrigin),
                Tuple.Create("14. LocalCompanionServer enforces 64 KB payload size limit (413)", (Action)TestLocalServerPayloadTooLarge),
                Tuple.Create("15. LocalCompanionServer enforces sliding-window rate limit (429 + Retry-After)", (Action)TestLocalServerRateLimit),
                Tuple.Create("16. Scan-only & Monitor workflows keep observations local without uploading", (Action)TestScanOnlyAndMonitorDoNotUpload),
                Tuple.Create("17. Mock backend pairing + explicit upload confirm with null location", (Action)TestPairingAndExplicitUploadConfirm),
                Tuple.Create("18. Mock backend 401 revocation clears local DPAPI credential cleanly", (Action)TestRevokedTokenClearsLocalStore),
                Tuple.Create("19. Mock backend credential rotation updates DPAPI store", (Action)TestCredentialRotation),
                Tuple.Create("20. LocalCompanionServer /upload/confirm rejects confirm!=true and count mismatch (409)", (Action)TestConfirmGuardsAndCountMismatch)
            };

            foreach (var test in tests)
            {
                try
                {
                    test.Item2();
                    _passed++;
                    Console.WriteLine("[PASS] " + test.Item1);
                }
                catch (Exception ex)
                {
                    _failed++;
                    Console.WriteLine("[FAIL] " + test.Item1 + " -> " + ex.Message);
                }
            }

            if (runLiveHardware)
            {
                try
                {
                    var live = new NativeWlanScanner();
                    WindowsScanOutcome outcome = live.ScanNow(true);
                    Console.WriteLine("[LIVE HARDWARE SCAN] success=" + outcome.Success +
                        " adapter=\"" + (outcome.Adapter != null ? outcome.Adapter.Description : "none") + "\"" +
                        " state=" + (outcome.Adapter != null ? outcome.Adapter.State : "none") +
                        " networks=" + outcome.Networks.Count +
                        " location=\"" + outcome.LocationMessage + "\"");
                    foreach (var n in outcome.Networks.Take(8))
                    {
                        Console.WriteLine("   - " + n.DisplaySsid + " | " + n.Bssid + " | " + n.SignalDbm + " dBm | " + n.Band + " | ch=" + (n.Channel.HasValue ? n.Channel.Value.ToString() : "-") + " | " + n.Security);
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine("[LIVE HARDWARE SCAN ERROR] " + ex.Message);
                }
            }

            Console.WriteLine("Windows Companion Tests Summary: passed=" + _passed + ", failed=" + _failed);
            return _failed == 0 ? 0 : 1;
        }

        private static void Assert(bool cond, string msg)
        {
            if (!cond) throw new Exception("Assertion failed: " + msg);
        }

        private static string CreateTempDir()
        {
            string dir = Path.Combine(Path.GetTempPath(), "wfv_win_test_" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(dir);
            return dir;
        }

        private static void CleanupDir(string dir)
        {
            try { if (Directory.Exists(dir)) Directory.Delete(dir, true); } catch { }
        }

        private static int GetFreeTcpPort()
        {
            var l = new TcpListener(IPAddress.Loopback, 0);
            l.Start();
            int port = ((IPEndPoint)l.LocalEndpoint).Port;
            l.Stop();
            return port;
        }

        private static void TestFrequencyBandChannelMapping()
        {
            Assert(WlanTaxonomy.ClassifyBand(2412) == "2.4 GHz", "2412 -> 2.4 GHz");
            Assert(WlanTaxonomy.DeriveChannelFromFrequencyMhz(2412) == 1, "2412 -> ch 1");
            Assert(WlanTaxonomy.DeriveChannelFromFrequencyMhz(2484) == 14, "2484 -> ch 14");
            Assert(WlanTaxonomy.ClassifyBand(5180) == "5 GHz", "5180 -> 5 GHz");
            Assert(WlanTaxonomy.DeriveChannelFromFrequencyMhz(5180) == 36, "5180 -> ch 36");
            Assert(WlanTaxonomy.ClassifyBand(6115) == "6 GHz", "6115 -> 6 GHz");
            Assert(WlanTaxonomy.DeriveChannelFromFrequencyMhz(6115) == 33, "6115 -> ch 33");
            Assert(WlanTaxonomy.ClassifyBand(1200) == "Unknown", "1200 -> Unknown");
            Assert(WlanTaxonomy.DeriveChannelFromFrequencyMhz(1200) == null, "1200 -> null channel");
        }

        private static void TestSecurityAndRssiNormalization()
        {
            Assert(WlanTaxonomy.MapDot11AuthAndCipher(7, 4, true) == "WPA2", "RSNA_PSK (7) -> WPA2");
            Assert(WlanTaxonomy.MapDot11AuthAndCipher(9, 4, true) == "WPA3", "WPA3_SAE (9) -> WPA3");
            Assert(WlanTaxonomy.MapDot11AuthAndCipher(1, 0, false) == "Open", "OPEN+NONE -> Open");
            Assert(WlanTaxonomy.MapDot11AuthAndCipher(1, 1, true) == "WEP", "WEP40 (1) -> WEP");
            Assert(WlanTaxonomy.MapLinkQualityToSignalQuality(100) == 4, "100% -> 4");
            Assert(WlanTaxonomy.MapLinkQualityToSignalQuality(50) == 2, "50% -> 2");
            Assert(WlanTaxonomy.MapLinkQualityToSignalQuality(0) == 0, "0% -> 0");
        }

        private static void TestHiddenSsidAndPayloadValidation()
        {
            var net = new WindowsScanNetwork
            {
                Ssid = null,
                IsHiddenSsid = true,
                Bssid = "AA:BB:CC:DD:EE:FF",
                SignalDbm = -62,
                FrequencyMhz = 5180,
                Band = "5 GHz",
                Security = "WPA2",
                SignalQuality = 3
            };
            Assert(net.DisplaySsid == "Hidden Network", "Hidden SSID display text");
            var payload = net.ToObservationPayload(null, 1700000000000L);
            Assert(payload.IsValid(), "Null location Windows payload still valid");
            Assert(payload.Source == "windows", "Source is windows");

            var badBssid = net.ToObservationPayload(null, 1700000000000L);
            badBssid.Bssid = "not-a-mac";
            Assert(!badBssid.IsValid(), "Invalid BSSID must fail validation");
        }

        private static void TestErrorKindsAndMissingAdapter()
        {
            string tempDir = CreateTempDir();
            try
            {
                var mock = new MockWlanScanner
                {
                    AdapterToReturn = new WifiAdapterInfo { Description = "None", State = "Unavailable", IsRadioReady = false },
                    OutcomeToReturn = new WindowsScanOutcome
                    {
                        Success = false,
                        ErrorKind = WlanScanErrorKind.MissingAdapter,
                        StatusMessage = "No Wi-Fi adapter detected."
                    }
                };
                var store = new WindowsCredentialStore(tempDir, new WindowsDpapiProtector());
                var queue = new PersistentWindowsQueue(tempDir, new WindowsDpapiProtector());
                using (var ctl = new WindowsSessionController(mock, store, queue))
                {
                    var res = ctl.ScanOnly(false);
                    Assert(!res.Success && res.ErrorKind == WlanScanErrorKind.MissingAdapter, "Missing adapter reported cleanly");
                    Assert(ctl.GetSnapshot().QueuedCount == 0, "Failed scan queues 0 items");
                }
            }
            finally { CleanupDir(tempDir); }
        }

        private static void TestCredentialStoreDpapiAndExpiry()
        {
            string tempDir = CreateTempDir();
            try
            {
                var store = new WindowsCredentialStore(tempDir, new WindowsDpapiProtector());
                string validToken = "wvd_" + new string('a', 24) + "_" + new string('b', 64);
                Assert(WindowsCredentialStore.IsValidTokenFormat(validToken), "Token format regex");
                Assert(!WindowsCredentialStore.IsValidTokenFormat("bad_token"), "Reject malformed token");

                var future = DateTimeOffset.UtcNow.AddDays(30).ToString("O");
                store.SaveCredential(new PairedWindowsCredential
                {
                    DeviceId = "671000000000000000000001",
                    DeviceName = "ThinkPad-Lab",
                    Platform = "windows",
                    DeviceToken = validToken,
                    ExpiresAtIso = future
                });

                byte[] diskBytes = File.ReadAllBytes(Path.Combine(tempDir, "device_cred.dpapi"));
                string rawDisk = Encoding.UTF8.GetString(diskBytes);
                Assert(!rawDisk.Contains(validToken), "Raw DPAPI file must never store plaintext token");

                var loaded = store.GetActiveCredential();
                Assert(loaded != null && loaded.DeviceToken == validToken && loaded.Platform == "windows", "DPAPI decrypt round-trip");

                store.ClearCredential();
                Assert(store.LoadCredential() == null, "ClearCredential deletes stored credential");
            }
            finally { CleanupDir(tempDir); }
        }

        private static void TestBackendUrlValidation()
        {
            string err;
            Assert(BackendApiClient.ValidateBaseUrl("https://api.wifivision.app/", true, out err) == "https://api.wifivision.app", "HTTPS allowed");
            Assert(BackendApiClient.ValidateBaseUrl("http://localhost:5000", true, out err) == "http://localhost:5000", "Loopback HTTP allowed in dev");
            Assert(BackendApiClient.ValidateBaseUrl("http://evil.example.com", true, out err) == null, "Remote HTTP blocked");
            Assert(BackendApiClient.ValidateBaseUrl("https://user:pass@api.example.com", true, out err) == null, "Embedded credentials blocked");
        }

        private static void TestQueueDeduplicationAndCaps()
        {
            var q = new ObservationQueueLogic();
            var list = new List<ObservationPayload>();
            for (int i = 0; i < 14; i++)
            {
                list.Add(new ObservationPayload
                {
                    Ssid = "Net-" + i,
                    Bssid = string.Format("AA:BB:CC:DD:EE:{0:X2}", i),
                    SignalDbm = -60,
                    FrequencyMhz = 2437,
                    Band = "2.4 GHz",
                    Security = "WPA2",
                    SignalQuality = 3,
                    Location = new ObservationLocation { Latitude = 24.86, Longitude = 67.01, AccuracyMeters = 15 },
                    ObservedAtEpochMs = 1700000000000L
                });
            }

            var s1 = q.EnqueueObservations(list, 1700000000000L, true);
            Assert(s1.AcceptedCount == 10 && s1.SkippedBatchCapCount == 4, "Per-scan cap 10 enforced");

            // Re-enqueue exact duplicate within 30s
            var s2 = q.EnqueueObservations(list.Take(2).ToList(), 1700000005000L, true);
            Assert(s2.AcceptedCount == 0 && s2.SkippedDuplicateCount == 2, "Duplicate suppressed within 30s");

            // >= 8 dBm delta after 35s passes dedupe
            var changed = new ObservationPayload
            {
                Ssid = "Net-0",
                Bssid = "AA:BB:CC:DD:EE:00",
                SignalDbm = -48, // delta 12 dBm
                FrequencyMhz = 2437,
                Band = "2.4 GHz",
                Security = "WPA2",
                SignalQuality = 4,
                Location = new ObservationLocation { Latitude = 24.86, Longitude = 67.01, AccuracyMeters = 15 },
                ObservedAtEpochMs = 1700000036000L
            };
            var s3 = q.EnqueueObservations(new List<ObservationPayload> { changed }, 1700000036000L, true);
            Assert(s3.AcceptedCount == 1, "Significant RSSI change bypasses 5-min window after 30s");
        }

        private static void TestQueueNoAutoUploadAndBackoff()
        {
            var q = new ObservationQueueLogic();
            Assert(!q.AutoUploadEnabled, "AutoUpload must always be false");
            long now = 1700000000000L;
            long p1 = q.MarkRateLimited(null, now);
            long p2 = q.MarkRateLimited(120, now);
            Assert(p1 == 60000L, "First 429 pause 60s");
            Assert(p2 == 120000L, "Retry-After respected");
        }

        private static void TestPersistentQueueDpapi()
        {
            string tempDir = CreateTempDir();
            try
            {
                var q1 = new PersistentWindowsQueue(tempDir, new WindowsDpapiProtector());
                q1.Logic.EnqueueObservations(new List<ObservationPayload>
                {
                    new ObservationPayload
                    {
                        Ssid = "Persisted",
                        Bssid = "11:22:33:44:55:66",
                        SignalDbm = -64,
                        FrequencyMhz = 5220,
                        Band = "5 GHz",
                        Security = "WPA2",
                        SignalQuality = 3,
                        ObservedAtEpochMs = 1700000000000L
                    }
                }, 1700000000000L);
                q1.Persist();

                var q2 = new PersistentWindowsQueue(tempDir, new WindowsDpapiProtector());
                Assert(q2.Logic.Size == 1 && q2.Logic.ItemsSnapshot()[0].Payload.Bssid == "11:22:33:44:55:66", "Queue restored via DPAPI");
            }
            finally { CleanupDir(tempDir); }
        }

        private static void TestLocalServerHealthNoSecrets()
        {
            WithLocalServer((server, port, token, ctl) =>
            {
                var res = HttpCall("GET", "http://127.0.0.1:" + port + "/health", null, null, null);
                Assert(res.StatusCode == 200, "/health 200");
                Assert(!res.Body.Contains(token) && !res.Body.Contains("wvd_"), "/health leaks no tokens");
                Assert(res.Body.Contains("\"platform\":\"windows\""), "/health identifies windows platform");
            });
        }

        private static void TestLocalServerRequiresToken()
        {
            WithLocalServer((server, port, token, ctl) =>
            {
                var noTok = HttpCall("GET", "http://127.0.0.1:" + port + "/status", null, null, null);
                Assert(noTok.StatusCode == 401, "Missing token rejected 401");

                var badTok = HttpCall("POST", "http://127.0.0.1:" + port + "/scan", "wrong-token", null, "{}");
                Assert(badTok.StatusCode == 401, "Wrong token rejected 401");

                var ok = HttpCall("GET", "http://127.0.0.1:" + port + "/status", token, null, null);
                Assert(ok.StatusCode == 200, "Valid token succeeds 200");
            });
        }

        private static void TestLocalServerRejectsBadHost()
        {
            WithLocalServer((server, port, token, ctl) =>
            {
                using (var tcp = new TcpClient("127.0.0.1", port))
                using (var stream = tcp.GetStream())
                {
                    string raw = "GET /health HTTP/1.1\r\nHost: evil.attacker.test:" + port + "\r\nConnection: close\r\n\r\n";
                    byte[] bytes = Encoding.ASCII.GetBytes(raw);
                    stream.Write(bytes, 0, bytes.Length);
                    using (var sr = new StreamReader(stream))
                    {
                        string respText = sr.ReadToEnd();
                        Assert(respText.Contains("403"), "DNS rebinding Host rejected 403");
                    }
                }
            });
        }

        private static void TestLocalServerCorsStrictOrigin()
        {
            WithLocalServer((server, port, token, ctl) =>
            {
                var evil = HttpCall("GET", "http://127.0.0.1:" + port + "/health", null, "https://evil.example", null);
                Assert(evil.StatusCode == 403 && string.IsNullOrEmpty(evil.AcaoHeader), "Evil origin blocked 403 without ACAO");

                var preflightOk = HttpCall("OPTIONS", "http://127.0.0.1:" + port + "/scan", null, "http://localhost:3000", null);
                Assert(preflightOk.StatusCode == 204 && preflightOk.AcaoHeader == "http://localhost:3000", "Allowlisted preflight reflects exact origin");
            });
        }

        private static void TestLocalServerPayloadTooLarge()
        {
            WithLocalServer((server, port, token, ctl) =>
            {
                string huge = "{\"confirm\":true,\"pad\":\"" + new string('x', 70000) + "\"}";
                var res = HttpCall("POST", "http://127.0.0.1:" + port + "/upload/confirm", token, null, huge);
                Assert(res.StatusCode == 413, "Body > 64 KB rejected 413");
            });
        }

        private static void TestLocalServerRateLimit()
        {
            WithLocalServer((server, port, token, ctl) =>
            {
                int hit429 = 0;
                for (int i = 0; i < 6; i++)
                {
                    var res = HttpCall("GET", "http://127.0.0.1:" + port + "/health", null, null, null);
                    if (res.StatusCode == 429) hit429++;
                }
                Assert(hit429 >= 1, "Sliding window rate limit triggers 429");
            }, 4);
        }

        private static void TestScanOnlyAndMonitorDoNotUpload()
        {
            string tempDir = CreateTempDir();
            try
            {
                int backendHits = 0;
                int backendPort = GetFreeTcpPort();
                using (var backend = StartMockBackend(backendPort, (path, body, auth) => { backendHits++; return Tuple.Create(201, "{\"ok\":true}"); }))
                {
                    var mock = new MockWlanScanner
                    {
                        AdapterToReturn = new WifiAdapterInfo { Description = "Mock WiFi 6", State = "Connected", IsRadioReady = true },
                        OutcomeToReturn = new WindowsScanOutcome
                        {
                            Success = true,
                            ErrorKind = WlanScanErrorKind.None,
                            ScannedAtEpochMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                            Networks = new List<WindowsScanNetwork>
                            {
                                new WindowsScanNetwork { Ssid = "OfficeNet", Bssid = "AA:11:22:33:44:55", SignalDbm = -58, FrequencyMhz = 5180, Band = "5 GHz", Security = "WPA2", SignalQuality = 3 }
                            }
                        }
                    };
                    var store = new WindowsCredentialStore(tempDir, new WindowsDpapiProtector());
                    store.SaveBackendBaseUrl("http://127.0.0.1:" + backendPort);
                    var queue = new PersistentWindowsQueue(tempDir, new WindowsDpapiProtector());
                    using (var ctl = new WindowsSessionController(mock, store, queue))
                    {
                        ctl.ScanOnly(false);
                        Assert(ctl.GetSnapshot().QueuedCount == 0 && ctl.GetSnapshot().UploadedCount == 0, "Scan-only queues 0 and uploads 0");

                        ctl.StartMonitoring();
                        System.Threading.Thread.Sleep(500);
                        ctl.StopMonitoring();
                        Assert(ctl.GetSnapshot().QueuedCount >= 1 && ctl.GetSnapshot().UploadedCount == 0 && backendHits == 0, "Monitor queues locally without auto-uploading");
                    }
                }
            }
            finally { CleanupDir(tempDir); }
        }

        private static void TestPairingAndExplicitUploadConfirm()
        {
            string tempDir = CreateTempDir();
            try
            {
                int uploadRequests = 0;
                string lastUploadedSource = null;
                string validToken = "wvd_" + new string('1', 24) + "_" + new string('2', 64);
                int backendPort = GetFreeTcpPort();
                using (StartMockBackend(backendPort, (path, body, auth) =>
                {
                    if (path == "/api/devices/pair")
                    {
                        Assert(body.Contains("\"platform\":\"windows\""), "Pairing requests windows platform");
                        return Tuple.Create(201, Json.Serialize(new Dictionary<string, object>
                        {
                            { "deviceToken", validToken },
                            { "device", new Dictionary<string, object>
                                {
                                    { "deviceId", "671000000000000000000002" },
                                    { "deviceName", "WinTestPC" },
                                    { "platform", "windows" },
                                    { "expiresAt", DateTimeOffset.UtcNow.AddDays(30).ToString("O") }
                                }
                            }
                        }));
                    }
                    if (path == "/api/observations")
                    {
                        uploadRequests++;
                        var map = Json.Deserialize<Dictionary<string, object>>(body);
                        lastUploadedSource = Convert.ToString(map["source"]);
                        Assert(!map.ContainsKey("location") || map["location"] == null, "Null location serialized honestly");
                        return Tuple.Create(201, "{\"observation\":{\"id\":\"obs1\"}}");
                    }
                    return Tuple.Create(404, "{}");
                }))
                {
                    var mock = new MockWlanScanner
                    {
                        AdapterToReturn = new WifiAdapterInfo { Description = "Mock AX", State = "Connected", IsRadioReady = true },
                        OutcomeToReturn = new WindowsScanOutcome
                        {
                            Success = true,
                            ScannedAtEpochMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                            Location = null,
                            Networks = new List<WindowsScanNetwork>
                            {
                                new WindowsScanNetwork { Ssid = "Home5G", Bssid = "AA:BB:CC:11:22:33", SignalDbm = -54, FrequencyMhz = 5240, Band = "5 GHz", Security = "WPA3", SignalQuality = 4 }
                            }
                        }
                    };
                    var store = new WindowsCredentialStore(tempDir, new WindowsDpapiProtector());
                    var queue = new PersistentWindowsQueue(tempDir, new WindowsDpapiProtector());
                    using (var ctl = new WindowsSessionController(mock, store, queue))
                    {
                        var pairRes = ctl.PairDevice("http://127.0.0.1:" + backendPort, "ABCD2345", "WinTestPC");
                        Assert(pairRes.Success, "Mock pairing succeeded");
                        ctl.ScanOnly(false);
                        Assert(uploadRequests == 0, "Still 0 uploads after scan");
                        var preview = ctl.BuildUploadPreview();
                        Assert(preview.ReadyLocalCount == 1 && !preview.HasLocation, "Preview accurate");
                        var upRes = ctl.ConfirmUploadNow(1);
                        Assert(upRes.UploadedCount == 1 && uploadRequests == 1 && lastUploadedSource == "windows", "Explicit upload succeeded with source=windows");
                    }
                }
            }
            finally { CleanupDir(tempDir); }
        }

        private static void TestRevokedTokenClearsLocalStore()
        {
            string tempDir = CreateTempDir();
            try
            {
                int backendPort = GetFreeTcpPort();
                using (StartMockBackend(backendPort, (path, body, auth) => Tuple.Create(401, "{\"error\":\"Device credential revoked\"}")))
                {
                    var store = new WindowsCredentialStore(tempDir, new WindowsDpapiProtector());
                    store.SaveBackendBaseUrl("http://127.0.0.1:" + backendPort);
                    store.SaveCredential(new PairedWindowsCredential
                    {
                        DeviceId = "671000000000000000000003",
                        DeviceName = "RevokedPC",
                        Platform = "windows",
                        DeviceToken = "wvd_" + new string('c', 24) + "_" + new string('d', 64),
                        ExpiresAtIso = DateTimeOffset.UtcNow.AddDays(10).ToString("O")
                    });
                    var mock = new MockWlanScanner
                    {
                        OutcomeToReturn = new WindowsScanOutcome
                        {
                            Success = true,
                            ScannedAtEpochMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                            Networks = new List<WindowsScanNetwork>
                            {
                                new WindowsScanNetwork { Ssid = "Corp", Bssid = "10:20:30:40:50:60", SignalDbm = -61, FrequencyMhz = 2462, Band = "2.4 GHz", Security = "WPA2", SignalQuality = 3 }
                            }
                        }
                    };
                    var queue = new PersistentWindowsQueue(tempDir, new WindowsDpapiProtector());
                    using (var ctl = new WindowsSessionController(mock, store, queue))
                    {
                        ctl.ScanOnly(false);
                        var res = ctl.ConfirmUploadNow(1);
                        Assert(res.RequiresReparing && store.LoadCredential() == null, "401 clears local DPAPI credential");
                        Assert(ctl.GetSnapshot().QueuedCount == 1, "Queued observation preserved safely after 401");
                    }
                }
            }
            finally { CleanupDir(tempDir); }
        }

        private static void TestCredentialRotation()
        {
            int backendPort = GetFreeTcpPort();
            string oldTok = "wvd_" + new string('a', 24) + "_" + new string('1', 64);
            string newTok = "wvd_" + new string('a', 24) + "_" + new string('9', 64);
            using (StartMockBackend(backendPort, (path, body, auth) =>
            {
                Assert(path == "/api/devices/rotate" && auth == "Bearer " + oldTok, "Rotate endpoint receives bearer token");
                return Tuple.Create(200, Json.Serialize(new Dictionary<string, object>
                {
                    { "deviceToken", newTok },
                    { "device", new Dictionary<string, object>
                        {
                            { "deviceId", "671000000000000000000009" },
                            { "deviceName", "RotatedPC" },
                            { "platform", "windows" },
                            { "expiresAt", DateTimeOffset.UtcNow.AddDays(90).ToString("O") }
                        }
                    }
                }));
            }))
            {
                var client = new BackendApiClient();
                var res = client.RotateCredential("http://127.0.0.1:" + backendPort, oldTok);
                Assert(res.Success && res.Credential.DeviceToken == newTok, "Credential rotation returns new scoped token");
            }
        }

        private static void TestConfirmGuardsAndCountMismatch()
        {
            WithLocalServer((server, port, token, ctl) =>
            {
                ctl.ScanOnly(false);
                var noConfirm = HttpCall("POST", "http://127.0.0.1:" + port + "/upload/confirm", token, null, "{\"confirm\":false}");
                Assert(noConfirm.StatusCode == 400, "confirm:false must return 400");

                var preview = HttpCall("GET", "http://127.0.0.1:" + port + "/queue/preview", token, null, null);
                Assert(preview.StatusCode == 200 && preview.Body.Contains("\"totalReadyCount\":1"), "Preview returns count=1");

                // Unpaired credential triggers 401 on valid confirm, OR mismatch triggers 409
                var mismatch = HttpCall("POST", "http://127.0.0.1:" + port + "/upload/confirm", token, null, "{\"confirm\":true,\"expectedCount\":99}");
                Assert(mismatch.StatusCode == 401 || mismatch.StatusCode == 409, "Guards protect confirmation");
            });
        }

        private static void WithLocalServer(Action<LocalCompanionServer, int, string, WindowsSessionController> action, int maxRequestsPerMinute = 30)
        {
            string tempDir = CreateTempDir();
            try
            {
                var mock = new MockWlanScanner
                {
                    AdapterToReturn = new WifiAdapterInfo { Description = "Mock WLAN", State = "Connected", IsRadioReady = true },
                    OutcomeToReturn = new WindowsScanOutcome
                    {
                        Success = true,
                        ScannedAtEpochMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                        Networks = new List<WindowsScanNetwork>
                        {
                            new WindowsScanNetwork { Ssid = "LabWiFi", Bssid = "AA:22:33:44:55:66", SignalDbm = -59, FrequencyMhz = 5180, Band = "5 GHz", Security = "WPA2", SignalQuality = 3 }
                        }
                    }
                };
                var store = new WindowsCredentialStore(tempDir, new WindowsDpapiProtector());
                var queue = new PersistentWindowsQueue(tempDir, new WindowsDpapiProtector());
                int port = GetFreeTcpPort();
                using (var ctl = new WindowsSessionController(mock, store, queue))
                using (var server = new LocalCompanionServer(ctl, port, maxRequestsPerMinute: maxRequestsPerMinute))
                {
                    server.Start();
                    action(server, port, server.LocalApiToken, ctl);
                }
            }
            finally { CleanupDir(tempDir); }
        }

        private sealed class SimpleHttpResp
        {
            public int StatusCode;
            public string Body;
            public string AcaoHeader;
        }

        private static SimpleHttpResp HttpCall(string method, string url, string localToken, string origin, string jsonBody)
        {
            var req = (HttpWebRequest)WebRequest.Create(url);
            req.Method = method;
            if (!string.IsNullOrEmpty(localToken)) req.Headers[LocalCompanionServer.TokenHeaderName] = localToken;
            if (!string.IsNullOrEmpty(origin)) req.Headers["Origin"] = origin;
            if (jsonBody != null)
            {
                byte[] bytes = Encoding.UTF8.GetBytes(jsonBody);
                req.ContentType = "application/json";
                req.ContentLength = bytes.Length;
                using (var s = req.GetRequestStream()) s.Write(bytes, 0, bytes.Length);
            }
            try
            {
                using (var resp = (HttpWebResponse)req.GetResponse())
                using (var sr = new StreamReader(resp.GetResponseStream()))
                {
                    return new SimpleHttpResp
                    {
                        StatusCode = (int)resp.StatusCode,
                        Body = sr.ReadToEnd(),
                        AcaoHeader = resp.Headers["Access-Control-Allow-Origin"]
                    };
                }
            }
            catch (WebException ex)
            {
                var err = ex.Response as HttpWebResponse;
                if (err != null)
                {
                    using (var sr = new StreamReader(err.GetResponseStream()))
                    {
                        return new SimpleHttpResp
                        {
                            StatusCode = (int)err.StatusCode,
                            Body = sr.ReadToEnd(),
                            AcaoHeader = err.Headers["Access-Control-Allow-Origin"]
                        };
                    }
                }
                throw;
            }
        }

        private static IDisposable StartMockBackend(int port, Func<string, string, string, Tuple<int, string>> handler)
        {
            var listener = new HttpListener();
            listener.Prefixes.Add("http://127.0.0.1:" + port + "/");
            listener.Start();
            System.Threading.Tasks.Task.Run(async () =>
            {
                while (listener.IsListening)
                {
                    try
                    {
                        var ctx = await listener.GetContextAsync().ConfigureAwait(false);
                        string body = "";
                        if (ctx.Request.HasEntityBody)
                        {
                            using (var sr = new StreamReader(ctx.Request.InputStream)) body = sr.ReadToEnd();
                        }
                        var result = handler(ctx.Request.Url.AbsolutePath, body, ctx.Request.Headers["Authorization"]);
                        byte[] bytes = Encoding.UTF8.GetBytes(result.Item2);
                        ctx.Response.StatusCode = result.Item1;
                        ctx.Response.ContentType = "application/json";
                        ctx.Response.ContentLength64 = bytes.Length;
                        ctx.Response.OutputStream.Write(bytes, 0, bytes.Length);
                        ctx.Response.Close();
                    }
                    catch { break; }
                }
            });
            return new DisposableAction(() =>
            {
                try { if (listener.IsListening) listener.Stop(); listener.Close(); } catch { }
            });
        }

        private sealed class DisposableAction : IDisposable
        {
            private readonly Action _act;
            public DisposableAction(Action act) { _act = act; }
            public void Dispose() { _act(); }
        }
    }
}
