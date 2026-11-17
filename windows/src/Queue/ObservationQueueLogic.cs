using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Web.Script.Serialization;
using WiFiVision.Windows.Models;
using WiFiVision.Windows.Security;

namespace WiFiVision.Windows.Queue
{
    public sealed class QueuedObservation
    {
        public string Id { get; set; }
        public ObservationPayload Payload { get; set; }
        public int Attempts { get; set; }
        public long NextAttemptAtMs { get; set; }
        public long EnqueuedAtMs { get; set; }
    }

    public sealed class EnqueueSummary
    {
        public int AcceptedCount { get; set; }
        public int SkippedInvalidCount { get; set; }
        public int SkippedDuplicateCount { get; set; }
        public int SkippedBatchCapCount { get; set; }
        public int DroppedOldestCount { get; set; }
    }

    public sealed class UploadConfirmationPreview
    {
        public int ExistingPendingQueueCount { get; set; }
        public int ReadyLocalCount { get; set; }
        public int InvalidSkippedCount { get; set; }
        public List<string> PreviewLines { get; set; }
        public bool HasLocation { get; set; }
        public double? LocationAccuracyMeters { get; set; }
        public List<ObservationPayload> CandidatePayloads { get; set; }

        public UploadConfirmationPreview()
        {
            PreviewLines = new List<string>();
            CandidatePayloads = new List<ObservationPayload>();
        }
    }

    /// <summary>
    /// Privacy-first queue, duplicate suppression, rate-limit backoff, and explicit
    /// confirmation coordinator matching Android's ObservationQueue + SessionController.
    /// Auto-upload stays OFF by design.
    /// </summary>
    public sealed class ObservationQueueLogic
    {
        public const int DefaultMaxQueueSize = 100;
        public const int DefaultMaxRetryAttempts = 5;
        public const int DefaultMaxBatchPerScan = 10;
        public const long DefaultDedupeWindowMs = 5 * 60000L;
        public const long DefaultMinUploadSpacingMs = 1500L;
        private const long MinChangeIntervalMs = 30000L;
        private const int SignificantRssiDeltaDbm = 8;
        private const double SignificantMoveMeters = 25.0;
        private const long BaseBackoffMs = 5000L;
        private const long MaxBackoffMs = 5 * 60000L;
        private const long DefaultRateLimitPauseMs = 60000L;
        private const long MaxRateLimitPauseMs = 15 * 60000L;

        private sealed class RecentRecord
        {
            public int SignalDbm;
            public double? Latitude;
            public double? Longitude;
            public long RecordedAtMs;
        }

        private readonly List<QueuedObservation> _queue = new List<QueuedObservation>();
        private readonly Dictionary<string, RecentRecord> _recentByKey = new Dictionary<string, RecentRecord>();
        private readonly object _sync = new object();
        private long _nextId = 1;

        public bool AutoUploadEnabled { get { return false; } }
        public long GlobalPauseUntilMs { get; private set; }
        public int ConsecutiveRateLimits { get; private set; }
        public long LastUploadCompletedAtMs { get; private set; }
        public int UploadedCount { get; private set; }

        public int Size
        {
            get { lock (_sync) return _queue.Count; }
        }

        public List<QueuedObservation> ItemsSnapshot()
        {
            lock (_sync) return _queue.ToList();
        }

        public EnqueueSummary EnqueueObservations(List<ObservationPayload> candidates, long nowMs, bool applyBatchCap = true)
        {
            lock (_sync)
            {
                PruneRecent(nowMs);

                int invalidCount = 0;
                int dupCount = 0;
                var accepted = new List<ObservationPayload>();
                var seenKeys = new HashSet<string>();

                if (candidates != null)
                {
                    foreach (var candidate in candidates)
                    {
                        if (candidate == null || !candidate.IsValid())
                        {
                            invalidCount++;
                            continue;
                        }
                        string key = candidate.DedupeKey();
                        if (!seenKeys.Add(key) || IsDuplicateLocked(candidate, key, nowMs))
                        {
                            dupCount++;
                            continue;
                        }
                        accepted.Add(candidate);
                    }
                }

                List<ObservationPayload> toEnqueue = (applyBatchCap && accepted.Count > DefaultMaxBatchPerScan)
                    ? accepted.Take(DefaultMaxBatchPerScan).ToList()
                    : accepted;
                int skippedCap = accepted.Count - toEnqueue.Count;

                int droppedOldest = 0;
                foreach (var payload in toEnqueue)
                {
                    if (_queue.Count >= DefaultMaxQueueSize)
                    {
                        _queue.RemoveAt(0);
                        droppedOldest++;
                    }
                    _queue.Add(new QueuedObservation
                    {
                        Id = "win_obs_" + nowMs + "_" + (_nextId++),
                        Payload = payload,
                        Attempts = 0,
                        NextAttemptAtMs = 0,
                        EnqueuedAtMs = nowMs
                    });
                    string key = payload.DedupeKey();
                    _recentByKey[key] = new RecentRecord
                    {
                        SignalDbm = payload.SignalDbm,
                        Latitude = payload.Location != null ? payload.Location.Latitude : (double?)null,
                        Longitude = payload.Location != null ? payload.Location.Longitude : (double?)null,
                        RecordedAtMs = nowMs
                    };
                }

                return new EnqueueSummary
                {
                    AcceptedCount = toEnqueue.Count,
                    SkippedInvalidCount = invalidCount,
                    SkippedDuplicateCount = dupCount,
                    SkippedBatchCapCount = skippedCap,
                    DroppedOldestCount = droppedOldest
                };
            }
        }

        public QueuedObservation PeekNextReady(long nowMs)
        {
            lock (_sync)
            {
                if (_queue.Count == 0) return null;
                if (nowMs < GlobalPauseUntilMs) return null;
                if (LastUploadCompletedAtMs > 0 && (nowMs - LastUploadCompletedAtMs) < DefaultMinUploadSpacingMs)
                    return null;
                return _queue.FirstOrDefault(item => item.NextAttemptAtMs <= nowMs);
            }
        }

        public long? NextWakeDelayMs(long nowMs)
        {
            lock (_sync)
            {
                if (_queue.Count == 0) return null;
                long earliest = _queue.Min(item => item.NextAttemptAtMs);
                long spacingReady = LastUploadCompletedAtMs > 0 ? LastUploadCompletedAtMs + DefaultMinUploadSpacingMs : 0;
                long readyAt = Math.Max(earliest, Math.Max(GlobalPauseUntilMs, spacingReady));
                return Math.Max(0, readyAt - nowMs);
            }
        }

        public bool MarkSuccess(string id, long nowMs)
        {
            lock (_sync)
            {
                int removed = _queue.RemoveAll(item => item.Id == id);
                if (removed > 0)
                {
                    ConsecutiveRateLimits = 0;
                    GlobalPauseUntilMs = 0;
                    LastUploadCompletedAtMs = nowMs;
                    UploadedCount += removed;
                    return true;
                }
                return false;
            }
        }

        public bool MarkClientRejected(string id, long nowMs)
        {
            lock (_sync)
            {
                LastUploadCompletedAtMs = nowMs;
                return _queue.RemoveAll(item => item.Id == id) > 0;
            }
        }

        public long MarkRateLimited(long? retryAfterSeconds, long nowMs)
        {
            lock (_sync)
            {
                ConsecutiveRateLimits++;
                LastUploadCompletedAtMs = nowMs;
                int shift = Math.Min(ConsecutiveRateLimits - 1, 4);
                long expPause = Math.Min(DefaultRateLimitPauseMs << shift, MaxRateLimitPauseMs);
                long serverPause = (retryAfterSeconds.HasValue && retryAfterSeconds.Value > 0)
                    ? Math.Min(retryAfterSeconds.Value * 1000L, MaxRateLimitPauseMs)
                    : 0L;
                long pauseMs = Math.Max(expPause, serverPause);
                GlobalPauseUntilMs = nowMs + pauseMs;
                return pauseMs;
            }
        }

        public bool MarkTransientFailure(string id, long nowMs)
        {
            lock (_sync)
            {
                LastUploadCompletedAtMs = nowMs;
                int idx = _queue.FindIndex(entry => entry.Id == id);
                if (idx < 0) return false;
                var queued = _queue[idx];
                int nextAttempts = queued.Attempts + 1;
                if (nextAttempts >= DefaultMaxRetryAttempts)
                {
                    _queue.RemoveAt(idx);
                    return false;
                }
                int shift = Math.Min(nextAttempts - 1, 6);
                long backoff = Math.Min(BaseBackoffMs << shift, MaxBackoffMs);
                queued.Attempts = nextAttempts;
                queued.NextAttemptAtMs = nowMs + backoff;
                return true;
            }
        }

        private bool IsDuplicateLocked(ObservationPayload candidate, string key, long nowMs)
        {
            if (_queue.Any(q => q.Payload.DedupeKey() == key))
            {
                RecentRecord rec;
                if (!_recentByKey.TryGetValue(key, out rec) || (nowMs - rec.RecordedAtMs) < MinChangeIntervalMs)
                    return true;
            }

            RecentRecord prev;
            if (!_recentByKey.TryGetValue(key, out prev)) return false;

            long ageMs = nowMs - prev.RecordedAtMs;
            if (ageMs >= DefaultDedupeWindowMs) return false;
            if (ageMs < MinChangeIntervalMs) return true;

            if (Math.Abs(candidate.SignalDbm - prev.SignalDbm) >= SignificantRssiDeltaDbm)
                return false;

            if (prev.Latitude.HasValue && prev.Longitude.HasValue && candidate.Location != null)
            {
                double dist = HaversineMeters(prev.Latitude.Value, prev.Longitude.Value, candidate.Location.Latitude, candidate.Location.Longitude);
                if (dist >= SignificantMoveMeters) return false;
            }

            return true;
        }

        private void PruneRecent(long nowMs)
        {
            var expired = _recentByKey.Where(kv => nowMs - kv.Value.RecordedAtMs >= DefaultDedupeWindowMs).Select(kv => kv.Key).ToList();
            foreach (var k in expired) _recentByKey.Remove(k);
        }

        private static double HaversineMeters(double lat1, double lon1, double lat2, double lon2)
        {
            const double R = 6371000.0;
            double dLat = (lat2 - lat1) * Math.PI / 180.0;
            double dLon = (lon2 - lon1) * Math.PI / 180.0;
            double a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2) +
                       Math.Cos(lat1 * Math.PI / 180.0) * Math.Cos(lat2 * Math.PI / 180.0) *
                       Math.Sin(dLon / 2) * Math.Sin(dLon / 2);
            double c = 2 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1 - a));
            return R * c;
        }

        public string ExportStateJson()
        {
            lock (_sync)
            {
                var serializer = new JavaScriptSerializer();
                var list = _queue.Select(q => new Dictionary<string, object>
                {
                    { "id", q.Id },
                    { "attempts", q.Attempts },
                    { "nextAttemptAtMs", q.NextAttemptAtMs },
                    { "enqueuedAtMs", q.EnqueuedAtMs },
                    { "payload", q.Payload.ToApiDictionary() }
                }).ToList();

                return serializer.Serialize(new Dictionary<string, object>
                {
                    { "globalPauseUntilMs", GlobalPauseUntilMs },
                    { "uploadedCount", UploadedCount },
                    { "items", list }
                });
            }
        }

        public void RestoreStateJson(string rawJson)
        {
            lock (_sync)
            {
                _queue.Clear();
                if (string.IsNullOrWhiteSpace(rawJson)) return;
                try
                {
                    var serializer = new JavaScriptSerializer();
                    var root = serializer.Deserialize<Dictionary<string, object>>(rawJson);
                    if (root == null) return;
                    if (root.ContainsKey("globalPauseUntilMs"))
                        GlobalPauseUntilMs = Convert.ToInt64(root["globalPauseUntilMs"]);
                    if (root.ContainsKey("uploadedCount"))
                        UploadedCount = Convert.ToInt32(root["uploadedCount"]);

                    var items = root.ContainsKey("items") ? root["items"] as System.Collections.ArrayList : null;
                    if (items == null) return;
                    foreach (var obj in items)
                    {
                        if (_queue.Count >= DefaultMaxQueueSize) break;
                        var map = obj as Dictionary<string, object>;
                        if (map == null) continue;
                        var pMap = map.ContainsKey("payload") ? map["payload"] as Dictionary<string, object> : null;
                        var payload = ParsePayloadMap(pMap);
                        if (payload == null || !payload.IsValid()) continue;
                        _queue.Add(new QueuedObservation
                        {
                            Id = map.ContainsKey("id") ? Convert.ToString(map["id"]) : ("win_obs_" + (_nextId++)),
                            Attempts = map.ContainsKey("attempts") ? Convert.ToInt32(map["attempts"]) : 0,
                            NextAttemptAtMs = map.ContainsKey("nextAttemptAtMs") ? Convert.ToInt64(map["nextAttemptAtMs"]) : 0,
                            EnqueuedAtMs = map.ContainsKey("enqueuedAtMs") ? Convert.ToInt64(map["enqueuedAtMs"]) : DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                            Payload = payload
                        });
                    }
                }
                catch
                {
                    _queue.Clear();
                }
            }
        }

        private static ObservationPayload ParsePayloadMap(Dictionary<string, object> map)
        {
            if (map == null) return null;
            try
            {
                int signalDbm = Convert.ToInt32(map["signalDbm"]);
                int freq = Convert.ToInt32(map["frequencyMhz"]);
                string band = map.ContainsKey("band") ? Convert.ToString(map["band"]) : "Unknown";
                string security = map.ContainsKey("security") ? Convert.ToString(map["security"]) : "Unknown";
                int? quality = map.ContainsKey("signalQuality") && map["signalQuality"] != null ? Convert.ToInt32(map["signalQuality"]) : (int?)null;
                string ssid = map.ContainsKey("ssid") ? Convert.ToString(map["ssid"]) : null;
                string bssid = map.ContainsKey("bssid") ? Convert.ToString(map["bssid"]) : null;
                long observedMs = DateTimeOffset.Parse(Convert.ToString(map["observedAt"])).ToUnixTimeMilliseconds();

                ObservationLocation loc = null;
                object rawLocObj;
                if (map.TryGetValue("location", out rawLocObj))
                {
                    var locMap = rawLocObj as Dictionary<string, object>;
                    if (locMap != null)
                    {
                        loc = new ObservationLocation
                        {
                            Latitude = Convert.ToDouble(locMap["latitude"]),
                            Longitude = Convert.ToDouble(locMap["longitude"]),
                            AccuracyMeters = locMap.ContainsKey("accuracyMeters") && locMap["accuracyMeters"] != null
                                ? Convert.ToDouble(locMap["accuracyMeters"])
                                : (double?)null
                        };
                    }
                }

                return new ObservationPayload
                {
                    Ssid = ssid,
                    Bssid = bssid,
                    SignalDbm = signalDbm,
                    FrequencyMhz = freq,
                    Band = band,
                    Security = security,
                    SignalQuality = quality,
                    Location = loc,
                    Source = ObservationPayload.SourceWindows,
                    ObservedAtEpochMs = observedMs
                };
            }
            catch
            {
                return null;
            }
        }
    }

    public sealed class PersistentWindowsQueue
    {
        private readonly string _queueFilePath;
        private readonly IDataProtector _protector;
        private readonly ObservationQueueLogic _logic = new ObservationQueueLogic();
        public ObservationQueueLogic Logic { get { return _logic; } }

        public PersistentWindowsQueue()
            : this(WindowsCredentialStore.GetDefaultAppDataDir(), new WindowsDpapiProtector())
        {
        }

        public PersistentWindowsQueue(string appDataDirectory, IDataProtector protector)
        {
            Directory.CreateDirectory(appDataDirectory);
            _queueFilePath = Path.Combine(appDataDirectory, "queue_state.dpapi");
            if (protector == null) throw new ArgumentNullException("protector");
            _protector = protector;
            Load();
        }

        public void Persist()
        {
            try
            {
                byte[] plain = Encoding.UTF8.GetBytes(Logic.ExportStateJson());
                File.WriteAllBytes(_queueFilePath, _protector.Protect(plain));
            }
            catch
            {
                // Non-fatal persistence safeguard
            }
        }

        private void Load()
        {
            if (!File.Exists(_queueFilePath)) return;
            try
            {
                byte[] encrypted = File.ReadAllBytes(_queueFilePath);
                string raw = Encoding.UTF8.GetString(_protector.Unprotect(encrypted));
                Logic.RestoreStateJson(raw);
            }
            catch
            {
                // Corrupted queue file discarded safely
            }
        }
    }
}
