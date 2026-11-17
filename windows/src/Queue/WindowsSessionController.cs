using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using WiFiVision.Windows.Models;
using WiFiVision.Windows.Native;
using WiFiVision.Windows.Network;
using WiFiVision.Windows.Security;

namespace WiFiVision.Windows.Queue
{
    public sealed class CompanionStateSnapshot
    {
        public bool Paired { get; set; }
        public string DeviceId { get; set; }
        public string DeviceName { get; set; }
        public string Platform { get; set; }
        public string ExpiresAtIso { get; set; }
        public string BackendBaseUrl { get; set; }
        public bool MonitorActive { get; set; }
        public bool UploadInProgress { get; set; }
        public int QueuedCount { get; set; }
        public int UploadedCount { get; set; }
        public long? GlobalPauseRemainingSec { get; set; }
        public WifiAdapterInfo Adapter { get; set; }
        public WindowsScanOutcome LastScan { get; set; }
        public string StatusBanner { get; set; }
    }

    public sealed class UploadExecutionResult
    {
        public bool Confirmed { get; set; }
        public bool CancelledOrMismatch { get; set; }
        public int AttemptedCount { get; set; }
        public int UploadedCount { get; set; }
        public int RemainingQueuedCount { get; set; }
        public int FailedOrDeferredCount { get; set; }
        public bool RequiresReparing { get; set; }
        public string Message { get; set; }
    }

    /// <summary>
    /// Coordinates local WLAN scanning, explicit queue control (AutoUpload OFF),
    /// DPAPI credential persistence, and confirmed uploads for both WinForms UI and loopback HTTP API.
    /// </summary>
    public sealed class WindowsSessionController : IDisposable
    {
        private readonly IWlanScanner _scanner;
        private readonly WindowsCredentialStore _credentialStore;
        private readonly PersistentWindowsQueue _persistentQueue;
        private readonly BackendApiClient _apiClient;
        private readonly object _sync = new object();
        private readonly Timer _monitorTimer;
        private readonly int _monitorIntervalMs;

        private WindowsScanOutcome _lastScan;
        private bool _lastScanQueuedForUpload;
        private bool _monitorActive;
        private bool _uploadInProgress;
        private string _statusBanner = "Ready. Scan locally or pair your Windows device.";

        public event Action StateChanged;

        public WindowsSessionController(
            IWlanScanner scanner = null,
            WindowsCredentialStore credentialStore = null,
            PersistentWindowsQueue persistentQueue = null,
            BackendApiClient apiClient = null,
            int monitorIntervalMs = 20000)
        {
            _scanner = scanner ?? new NativeWlanScanner();
            _credentialStore = credentialStore ?? new WindowsCredentialStore();
            _persistentQueue = persistentQueue ?? new PersistentWindowsQueue();
            _apiClient = apiClient ?? new BackendApiClient();
            _monitorIntervalMs = Math.Max(5000, monitorIntervalMs);
            _monitorTimer = new Timer(OnMonitorTick, null, Timeout.Infinite, Timeout.Infinite);
        }

        public CompanionStateSnapshot GetSnapshot()
        {
            PairedWindowsCredential cred = _credentialStore.GetActiveCredential();
            string backendUrl = _credentialStore.LoadBackendBaseUrl() ?? "http://localhost:5000";
            WifiAdapterInfo adapter = null;
            try
            {
                adapter = _scanner.GetAdapterStatus();
            }
            catch
            {
                // Keep UI/API responsive if adapter status probe throws unexpectedly
            }

            lock (_sync)
            {
                long nowMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                long? pauseSec = _persistentQueue.Logic.GlobalPauseUntilMs > nowMs
                    ? (long?)Math.Ceiling((_persistentQueue.Logic.GlobalPauseUntilMs - nowMs) / 1000.0)
                    : null;

                return new CompanionStateSnapshot
                {
                    Paired = cred != null,
                    DeviceId = cred != null ? cred.DeviceId : null,
                    DeviceName = cred != null ? cred.DeviceName : null,
                    Platform = "windows",
                    ExpiresAtIso = cred != null ? cred.ExpiresAtIso : null,
                    BackendBaseUrl = backendUrl,
                    MonitorActive = _monitorActive,
                    UploadInProgress = _uploadInProgress,
                    QueuedCount = _persistentQueue.Logic.Size,
                    UploadedCount = _persistentQueue.Logic.UploadedCount,
                    GlobalPauseRemainingSec = pauseSec,
                    Adapter = adapter,
                    LastScan = _lastScan,
                    StatusBanner = _statusBanner
                };
            }
        }

        public WindowsScanOutcome ScanOnly(bool includeLocation = true)
        {
            WindowsScanOutcome outcome = _scanner.ScanNow(includeLocation);
            lock (_sync)
            {
                _lastScan = outcome;
                _lastScanQueuedForUpload = false;
                _statusBanner = outcome.Success
                    ? "Local scan complete: " + outcome.Networks.Count + " network(s) found. Nothing uploaded."
                    : outcome.StatusMessage;
            }
            NotifyStateChanged();
            return outcome;
        }

        public bool StartMonitoring()
        {
            lock (_sync)
            {
                if (_monitorActive) return true;
                _monitorActive = true;
                _statusBanner = "Monitoring started (local queue only; auto-upload stays OFF).";
            }
            // Capture initial monitor tick cleanly without blocking caller
            _monitorTimer.Change(250, _monitorIntervalMs);
            NotifyStateChanged();
            return true;
        }

        public void StopMonitoring()
        {
            lock (_sync)
            {
                _monitorActive = false;
                _statusBanner = "Monitoring stopped.";
            }
            _monitorTimer.Change(Timeout.Infinite, Timeout.Infinite);
            NotifyStateChanged();
        }

        private void OnMonitorTick(object state)
        {
            bool active;
            lock (_sync) active = _monitorActive;
            if (!active) return;

            WindowsScanOutcome outcome = _scanner.ScanNow(true);
            lock (_sync)
            {
                if (!_monitorActive) return;
                _lastScan = outcome;
                if (outcome.Success && outcome.Networks.Count > 0)
                {
                    var candidates = outcome.Networks
                        .Select(n => n.ToObservationPayload(outcome.Location, outcome.ScannedAtEpochMs))
                        .ToList();
                    EnqueueSummary summary = _persistentQueue.Logic.EnqueueObservations(candidates, outcome.ScannedAtEpochMs, true);
                    _lastScanQueuedForUpload = true;
                    _persistentQueue.Persist();
                    _statusBanner = "Monitor queued +" + summary.AcceptedCount + " observation(s). Waiting for explicit UPLOAD NOW.";
                }
                else if (!outcome.Success)
                {
                    _statusBanner = "Monitor paused on scan notice: " + outcome.StatusMessage;
                }
            }
            NotifyStateChanged();
        }

        public UploadConfirmationPreview BuildUploadPreview()
        {
            lock (_sync)
            {
                var readyFromLastScan = new List<ObservationPayload>();
                int invalidSkipped = 0;

                if (_lastScan != null && _lastScan.Success && !_lastScanQueuedForUpload)
                {
                    foreach (var n in _lastScan.Networks)
                    {
                        var payload = n.ToObservationPayload(_lastScan.Location, _lastScan.ScannedAtEpochMs);
                        if (payload.IsValid())
                        {
                            if (readyFromLastScan.Count < ObservationQueueLogic.DefaultMaxBatchPerScan)
                            {
                                readyFromLastScan.Add(payload);
                            }
                        }
                        else
                        {
                            invalidSkipped++;
                        }
                    }
                }

                var queuedItems = _persistentQueue.Logic.ItemsSnapshot();
                var previewLines = new List<string>();
                foreach (var item in queuedItems.Take(5))
                {
                    previewLines.Add(FormatPreviewLine(item.Payload));
                }
                foreach (var p in readyFromLastScan.Take(Math.Max(0, 6 - previewLines.Count)))
                {
                    previewLines.Add(FormatPreviewLine(p));
                }

                bool hasLoc = _lastScan != null && _lastScan.Location != null
                    || queuedItems.Any(q => q.Payload != null && q.Payload.Location != null);
                double? acc = _lastScan != null && _lastScan.Location != null
                    ? _lastScan.Location.AccuracyMeters
                    : queuedItems.Select(q => q.Payload.Location != null ? q.Payload.Location.AccuracyMeters : null).FirstOrDefault(a => a.HasValue);

                return new UploadConfirmationPreview
                {
                    ExistingPendingQueueCount = queuedItems.Count,
                    ReadyLocalCount = readyFromLastScan.Count,
                    InvalidSkippedCount = invalidSkipped,
                    PreviewLines = previewLines,
                    HasLocation = hasLoc,
                    LocationAccuracyMeters = acc,
                    CandidatePayloads = readyFromLastScan
                };
            }
        }

        public PairingResult PairDevice(string backendUrl, string pairingCode, string deviceName)
        {
            _credentialStore.SaveBackendBaseUrl(backendUrl);
            PairingResult res = _apiClient.PairWindowsDevice(backendUrl, pairingCode, deviceName);
            if (res.Success && res.Credential != null)
            {
                _credentialStore.SaveCredential(res.Credential);
                lock (_sync)
                {
                    _statusBanner = "Paired Windows device: " + res.Credential.DeviceName;
                }
                NotifyStateChanged();
            }
            return res;
        }

        public void ClearPairing()
        {
            _credentialStore.ClearCredential();
            lock (_sync)
            {
                _statusBanner = "Local Windows device credential cleared.";
            }
            NotifyStateChanged();
        }

        public void SaveBackendBaseUrl(string backendUrl)
        {
            _credentialStore.SaveBackendBaseUrl(backendUrl);
            NotifyStateChanged();
        }

        public UploadExecutionResult ConfirmUploadNow(int? expectedTotalCount = null)
        {
            PairedWindowsCredential cred = _credentialStore.GetActiveCredential();
            if (cred == null)
            {
                return new UploadExecutionResult
                {
                    Confirmed = false,
                    RequiresReparing = true,
                    Message = "Windows device is not paired (or local credential expired). Pair in Settings first."
                };
            }

            string backendUrl = _credentialStore.LoadBackendBaseUrl() ?? "http://localhost:5000";
            string urlError;
            if (BackendApiClient.ValidateBaseUrl(backendUrl, true, out urlError) == null)
            {
                return new UploadExecutionResult
                {
                    Confirmed = false,
                    Message = urlError ?? "Invalid backend API URL."
                };
            }

            lock (_sync)
            {
                if (_uploadInProgress)
                {
                    return new UploadExecutionResult
                    {
                        Confirmed = false,
                        Message = "An upload batch is already in progress."
                    };
                }

                UploadConfirmationPreview preview = BuildUploadPreview();
                int actualTotal = preview.ExistingPendingQueueCount + preview.ReadyLocalCount;
                if (expectedTotalCount.HasValue && expectedTotalCount.Value != actualTotal)
                {
                    return new UploadExecutionResult
                    {
                        Confirmed = false,
                        CancelledOrMismatch = true,
                        RemainingQueuedCount = actualTotal,
                        Message = "Upload count changed before confirmation (" + expectedTotalCount.Value + " -> " + actualTotal + "). Refresh preview and try again."
                    };
                }

                if (preview.CandidatePayloads.Count > 0)
                {
                    long nowMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                    _persistentQueue.Logic.EnqueueObservations(preview.CandidatePayloads, nowMs, false);
                    _lastScanQueuedForUpload = true;
                    _persistentQueue.Persist();
                }

                _uploadInProgress = true;
                _statusBanner = "Uploading confirmed observations...";
            }
            NotifyStateChanged();

            int attempted = 0;
            int uploaded = 0;
            int failed = 0;
            bool abortedAuth = false;
            bool abortedRateLimit = false;
            string finalMsg = "Upload complete.";

            try
            {
                var snapshot = _persistentQueue.Logic.ItemsSnapshot();
                foreach (var item in snapshot)
                {
                    attempted++;
                    long nowMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                    UploadAttemptResult res = _apiClient.UploadObservation(backendUrl, item.Payload, cred.DeviceToken);
                    if (res.Success)
                    {
                        _persistentQueue.Logic.MarkSuccess(item.Id, nowMs);
                        uploaded++;
                    }
                    else if (res.Unauthorized)
                    {
                        _credentialStore.ClearCredential();
                        abortedAuth = true;
                        finalMsg = "Device credential revoked or expired (" + (res.Message ?? "HTTP 401") + "). Local credential cleared; please re-pair.";
                        break;
                    }
                    else if (res.ClientRejected)
                    {
                        _persistentQueue.Logic.MarkClientRejected(item.Id, nowMs);
                        failed++;
                        if (res.StatusCode == 403)
                        {
                            finalMsg = res.Message ?? "Upload forbidden for this platform/device.";
                        }
                    }
                    else if (res.RateLimited)
                    {
                        _persistentQueue.Logic.MarkRateLimited(res.RetryAfterSeconds, nowMs);
                        abortedRateLimit = true;
                        finalMsg = "Rate limited by backend. Paused safely to avoid server overload.";
                        break;
                    }
                    else
                    {
                        _persistentQueue.Logic.MarkTransientFailure(item.Id, nowMs);
                        failed++;
                        finalMsg = res.Message ?? "Transient upload error; remaining observations kept safely queued.";
                    }
                }

                _persistentQueue.Persist();
            }
            finally
            {
                lock (_sync)
                {
                    _uploadInProgress = false;
                    if (uploaded > 0 && !abortedAuth && !abortedRateLimit && failed == 0)
                    {
                        finalMsg = "Uploaded " + uploaded + " Windows observation(s) successfully.";
                    }
                    _statusBanner = finalMsg;
                }
                NotifyStateChanged();
            }

            return new UploadExecutionResult
            {
                Confirmed = true,
                AttemptedCount = attempted,
                UploadedCount = uploaded,
                RemainingQueuedCount = _persistentQueue.Logic.Size,
                FailedOrDeferredCount = failed,
                RequiresReparing = abortedAuth,
                Message = finalMsg
            };
        }

        private static string FormatPreviewLine(ObservationPayload p)
        {
            if (p == null) return "Unknown observation";
            string name = string.IsNullOrWhiteSpace(p.Ssid) ? "Hidden Network" : p.Ssid;
            string loc = p.Location != null
                ? string.Format(System.Globalization.CultureInfo.InvariantCulture, "GPS {0:F4}, {1:F4}", p.Location.Latitude, p.Location.Longitude)
                : "No location";
            return name + " (" + p.SignalDbm + " dBm, " + p.Band + ", " + p.Security + ", " + loc + ")";
        }

        private void NotifyStateChanged()
        {
            var handler = StateChanged;
            if (handler != null) handler();
        }

        public void Dispose()
        {
            _monitorTimer.Dispose();
        }
    }
}
