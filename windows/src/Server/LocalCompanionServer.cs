using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using WiFiVision.Windows.Models;
using WiFiVision.Windows.Queue;
using WiFiVision.Windows.Security;

namespace WiFiVision.Windows.Server
{
    /// <summary>
    /// Security-hardened local loopback HTTP API (http://127.0.0.1:<port>) so the authenticated
    /// WiFiVision web dashboard running in the user's browser can trigger local WLAN scans,
    /// inspect queued observations, and confirm explicit uploads without exposing secrets or wide CORS.
    /// </summary>
    public sealed class LocalCompanionServer : IDisposable
    {
        public const int DefaultPort = 48271;
        public const int MaxRequestBodyBytes = 65536; // 64 KB cap
        public const int DefaultRateLimitPerMinute = 30;
        public const string TokenHeaderName = "X-WiFiVision-Local-Token";

        private static readonly JavaScriptSerializer Json = new JavaScriptSerializer();

        private readonly WindowsSessionController _controller;
        private readonly int _port;
        private readonly string _localApiToken;
        private readonly HashSet<string> _allowedOrigins;
        private readonly int _maxRequestsPerMinute;
        private readonly object _rateSync = new object();
        private readonly Queue<long> _requestTimestampsMs = new Queue<long>();

        private HttpListener _listener;
        private CancellationTokenSource _cts;
        private Task _acceptLoop;

        public int Port { get { return _port; } }
        public string BaseAddress { get { return "http://127.0.0.1:" + _port; } }
        public string LocalApiToken { get { return _localApiToken; } }
        public bool IsListening { get { return _listener != null && _listener.IsListening; } }

        public LocalCompanionServer(
            WindowsSessionController controller,
            int port = DefaultPort,
            string localApiToken = null,
            IEnumerable<string> additionalAllowedOrigins = null,
            int maxRequestsPerMinute = DefaultRateLimitPerMinute)
        {
            if (controller == null) throw new ArgumentNullException("controller");
            _controller = controller;
            if (port < 1024 || port > 65535)
                throw new ArgumentOutOfRangeException("port", "Port must be an unprivileged port between 1024 and 65535.");
            _port = port;
            _localApiToken = string.IsNullOrWhiteSpace(localApiToken)
                ? WindowsCredentialStore.GenerateSecureRandomHex(24)
                : localApiToken.Trim();
            _maxRequestsPerMinute = Math.Max(1, maxRequestsPerMinute);
            _allowedOrigins = BuildAllowedOrigins(additionalAllowedOrigins);
        }

        private static HashSet<string> BuildAllowedOrigins(IEnumerable<string> extraOrigins)
        {
            var set = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
            {
                "http://localhost:3000",
                "http://127.0.0.1:3000"
            };

            string envOrigins = Environment.GetEnvironmentVariable("WIFIVISION_ALLOWED_ORIGINS");
            if (!string.IsNullOrWhiteSpace(envOrigins))
            {
                foreach (var item in envOrigins.Split(new[] { ',', ';' }, StringSplitOptions.RemoveEmptyEntries))
                {
                    AddValidOrigin(set, item);
                }
            }

            if (extraOrigins != null)
            {
                foreach (var item in extraOrigins)
                {
                    AddValidOrigin(set, item);
                }
            }

            return set;
        }

        private static void AddValidOrigin(HashSet<string> target, string rawOrigin)
        {
            string trimmed = (rawOrigin ?? "").Trim().TrimEnd('/');
            if (trimmed == "*" || string.IsNullOrEmpty(trimmed)) return;
            Uri uri;
            if (!Uri.TryCreate(trimmed, UriKind.Absolute, out uri)) return;
            if (uri.Scheme != "http" && uri.Scheme != "https") return;
            target.Add(uri.GetLeftPart(UriPartial.Authority));
        }

        public void Start()
        {
            if (_listener != null) return;
            _listener = new HttpListener();
            // Bind strictly to IPv4 loopback only — NEVER 0.0.0.0, +, or *
            _listener.Prefixes.Add("http://127.0.0.1:" + _port + "/");
            _listener.Start();
            _cts = new CancellationTokenSource();
            _acceptLoop = Task.Run(() => AcceptLoopAsync(_cts.Token));
        }

        public void Stop()
        {
            try
            {
                if (_cts != null) _cts.Cancel();
            }
            catch { }

            try
            {
                if (_listener != null)
                {
                    if (_listener.IsListening) _listener.Stop();
                    _listener.Close();
                }
            }
            catch { }
            finally
            {
                _listener = null;
            }
        }

        private async Task AcceptLoopAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested && _listener != null && _listener.IsListening)
            {
                HttpListenerContext ctx = null;
                try
                {
                    ctx = await _listener.GetContextAsync().ConfigureAwait(false);
                }
                catch
                {
                    if (ct.IsCancellationRequested) break;
                    continue;
                }

                // Process each request asynchronously without blocking listener accept loop
                var captured = ctx;
                var _ = Task.Run(() => HandleContextSafe(captured));
            }
        }

        private void HandleContextSafe(HttpListenerContext ctx)
        {
            try
            {
                HandleContext(ctx);
            }
            catch
            {
                try
                {
                    WriteJson(ctx.Response, 500, null, new Dictionary<string, object>
                    {
                        { "error", "INTERNAL_ERROR" },
                        { "message", "Unexpected companion server error." }
                    });
                }
                catch { }
            }
        }

        private void HandleContext(HttpListenerContext ctx)
        {
            HttpListenerRequest req = ctx.Request;
            HttpListenerResponse resp = ctx.Response;

            // 1. Validate Host header against DNS rebinding
            string host = (req.Headers["Host"] ?? "").Trim();
            string expectedIpHost = "127.0.0.1:" + _port;
            string expectedLocalHost = "localhost:" + _port;
            if (!host.Equals(expectedIpHost, StringComparison.OrdinalIgnoreCase) &&
                !host.Equals(expectedLocalHost, StringComparison.OrdinalIgnoreCase))
            {
                WriteJson(resp, 403, null, new Dictionary<string, object>
                {
                    { "error", "INVALID_HOST" },
                    { "message", "Rejected request with unrecognized Host header." }
                });
                return;
            }

            // 2. Validate Origin header strictly against allowlist
            string origin = (req.Headers["Origin"] ?? "").Trim().TrimEnd('/');
            string approvedCorsOrigin = null;
            if (!string.IsNullOrEmpty(origin))
            {
                if (!_allowedOrigins.Contains(origin))
                {
                    WriteJson(resp, 403, null, new Dictionary<string, object>
                    {
                        { "error", "ORIGIN_NOT_ALLOWED" },
                        { "message", "Origin is not authorized to access WiFiVision Windows Companion." }
                    });
                    return;
                }
                approvedCorsOrigin = origin;
            }

            // 3. Handle CORS preflight without token (browser preflight strips custom header values)
            if (req.HttpMethod.Equals("OPTIONS", StringComparison.OrdinalIgnoreCase))
            {
                ApplySecurityHeaders(resp, approvedCorsOrigin);
                if (approvedCorsOrigin != null)
                {
                    resp.AddHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                    resp.AddHeader("Access-Control-Allow-Headers", "Content-Type, " + TokenHeaderName);
                    resp.AddHeader("Access-Control-Max-Age", "300");
                }
                resp.StatusCode = 204;
                resp.Close();
                return;
            }

            // 4. Sliding window rate limit (covers all endpoints including /health)
            int retryAfterSec;
            if (!TryConsumeRateLimit(out retryAfterSec))
            {
                resp.AddHeader("Retry-After", retryAfterSec.ToString());
                WriteJson(resp, 429, approvedCorsOrigin, new Dictionary<string, object>
                {
                    { "error", "RATE_LIMITED" },
                    { "message", "Too many requests to local companion API. Wait " + retryAfterSec + "s." }
                });
                return;
            }

            string path = (req.Url != null ? req.Url.AbsolutePath : "/").TrimEnd('/');
            if (string.IsNullOrEmpty(path)) path = "/";
            string method = req.HttpMethod.ToUpperInvariant();

            // 5. Minimal public health endpoint (never returns BSSIDs, tokens, or pairing secrets)
            if (method == "GET" && path == "/health")
            {
                var snap = _controller.GetSnapshot();
                WriteJson(resp, 200, approvedCorsOrigin, new Dictionary<string, object>
                {
                    { "ok", true },
                    { "service", "wifivision-windows-companion" },
                    { "version", "1.0.0" },
                    { "platform", "windows" },
                    { "paired", snap.Paired },
                    { "monitorActive", snap.MonitorActive },
                    { "queuedCount", snap.QueuedCount },
                    { "uploadedCount", snap.UploadedCount }
                });
                return;
            }

            // 6. All operational endpoints require per-launch local API token (constant-time comparison)
            string providedToken = (req.Headers[TokenHeaderName] ?? "").Trim();
            if (!FixedTimeEquals(providedToken, _localApiToken))
            {
                WriteJson(resp, 401, approvedCorsOrigin, new Dictionary<string, object>
                {
                    { "error", "LOCAL_TOKEN_REQUIRED" },
                    { "message", "Missing or invalid " + TokenHeaderName + " header. Copy the session token from the WiFiVision Windows Companion window." }
                });
                return;
            }

            // 7. Enforce request body limit (64 KB) on POST/PUT
            string rawBody = "";
            if (req.HasEntityBody)
            {
                if (req.ContentLength64 > MaxRequestBodyBytes)
                {
                    WriteJson(resp, 413, approvedCorsOrigin, new Dictionary<string, object>
                    {
                        { "error", "PAYLOAD_TOO_LARGE" },
                        { "message", "Request body exceeds 64 KB safety limit." }
                    });
                    return;
                }

                bool tooLarge;
                rawBody = ReadBoundedBody(req.InputStream, MaxRequestBodyBytes, out tooLarge);
                if (tooLarge)
                {
                    WriteJson(resp, 413, approvedCorsOrigin, new Dictionary<string, object>
                    {
                        { "error", "PAYLOAD_TOO_LARGE" },
                        { "message", "Request body exceeds 64 KB safety limit." }
                    });
                    return;
                }
            }

            // 8. Route authenticated companion actions
            if (method == "GET" && path == "/status")
            {
                WriteJson(resp, 200, approvedCorsOrigin, SerializeSnapshot(_controller.GetSnapshot()));
                return;
            }

            if (method == "POST" && path == "/scan")
            {
                WindowsScanOutcome outcome = _controller.ScanOnly(true);
                var snap = _controller.GetSnapshot();
                WriteJson(resp, 200, approvedCorsOrigin, new Dictionary<string, object>
                {
                    { "outcome", SerializeScanOutcome(outcome) },
                    { "status", SerializeSnapshot(snap) }
                });
                return;
            }

            if (method == "POST" && path == "/monitor/start")
            {
                _controller.StartMonitoring();
                WriteJson(resp, 200, approvedCorsOrigin, SerializeSnapshot(_controller.GetSnapshot()));
                return;
            }

            if (method == "POST" && path == "/monitor/stop")
            {
                _controller.StopMonitoring();
                WriteJson(resp, 200, approvedCorsOrigin, SerializeSnapshot(_controller.GetSnapshot()));
                return;
            }

            if (method == "GET" && path == "/queue/preview")
            {
                UploadConfirmationPreview preview = _controller.BuildUploadPreview();
                int totalReady = preview.ExistingPendingQueueCount + preview.ReadyLocalCount;
                WriteJson(resp, 200, approvedCorsOrigin, new Dictionary<string, object>
                {
                    { "totalReadyCount", totalReady },
                    { "existingPendingQueueCount", preview.ExistingPendingQueueCount },
                    { "readyLocalCount", preview.ReadyLocalCount },
                    { "invalidSkippedCount", preview.InvalidSkippedCount },
                    { "hasLocation", preview.HasLocation },
                    { "locationAccuracyMeters", preview.LocationAccuracyMeters },
                    { "previewLines", preview.PreviewLines }
                });
                return;
            }

            if (method == "POST" && path == "/upload/confirm")
            {
                var bodyMap = ParseBodyMap(rawBody);
                bool confirm = bodyMap != null && bodyMap.ContainsKey("confirm") && Convert.ToBoolean(bodyMap["confirm"]);
                if (!confirm)
                {
                    WriteJson(resp, 400, approvedCorsOrigin, new Dictionary<string, object>
                    {
                        { "error", "CONFIRMATION_REQUIRED" },
                        { "message", "Explicit { \"confirm\": true } body is required before uploading observations." }
                    });
                    return;
                }

                int? expectedCount = null;
                if (bodyMap.ContainsKey("expectedCount") && bodyMap["expectedCount"] != null)
                {
                    expectedCount = Convert.ToInt32(bodyMap["expectedCount"]);
                }

                UploadExecutionResult result = _controller.ConfirmUploadNow(expectedCount);
                int status = result.RequiresReparing ? 401 : (result.CancelledOrMismatch ? 409 : 200);
                WriteJson(resp, status, approvedCorsOrigin, new Dictionary<string, object>
                {
                    { "confirmed", result.Confirmed },
                    { "cancelledOrMismatch", result.CancelledOrMismatch },
                    { "attemptedCount", result.AttemptedCount },
                    { "uploadedCount", result.UploadedCount },
                    { "remainingQueuedCount", result.RemainingQueuedCount },
                    { "failedOrDeferredCount", result.FailedOrDeferredCount },
                    { "requiresReparing", result.RequiresReparing },
                    { "message", result.Message },
                    { "status", SerializeSnapshot(_controller.GetSnapshot()) }
                });
                return;
            }

            WriteJson(resp, 404, approvedCorsOrigin, new Dictionary<string, object>
            {
                { "error", "NOT_FOUND" },
                { "message", "Unknown local companion endpoint." }
            });
        }

        private bool TryConsumeRateLimit(out int retryAfterSec)
        {
            long nowMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            long cutoffMs = nowMs - 60000L;
            lock (_rateSync)
            {
                while (_requestTimestampsMs.Count > 0 && _requestTimestampsMs.Peek() <= cutoffMs)
                {
                    _requestTimestampsMs.Dequeue();
                }

                if (_requestTimestampsMs.Count >= _maxRequestsPerMinute)
                {
                    long oldest = _requestTimestampsMs.Peek();
                    retryAfterSec = Math.Max(1, (int)Math.Ceiling((oldest + 60000L - nowMs) / 1000.0));
                    return false;
                }

                _requestTimestampsMs.Enqueue(nowMs);
                retryAfterSec = 0;
                return true;
            }
        }

        private static bool FixedTimeEquals(string a, string b)
        {
            byte[] ba = Encoding.UTF8.GetBytes(a ?? "");
            byte[] bb = Encoding.UTF8.GetBytes(b ?? "");
            int diff = ba.Length ^ bb.Length;
            int min = Math.Min(ba.Length, bb.Length);
            for (int i = 0; i < min; i++)
            {
                diff |= ba[i] ^ bb[i];
            }
            return diff == 0;
        }

        private static string ReadBoundedBody(Stream stream, int maxBytes, out bool overflow)
        {
            overflow = false;
            using (var ms = new MemoryStream())
            {
                byte[] buf = new byte[4096];
                int read;
                while ((read = stream.Read(buf, 0, buf.Length)) > 0)
                {
                    if (ms.Length + read > maxBytes)
                    {
                        overflow = true;
                        return null;
                    }
                    ms.Write(buf, 0, read);
                }
                return Encoding.UTF8.GetString(ms.ToArray());
            }
        }

        private static Dictionary<string, object> ParseBodyMap(string raw)
        {
            if (string.IsNullOrWhiteSpace(raw)) return null;
            try
            {
                return Json.Deserialize<Dictionary<string, object>>(raw);
            }
            catch
            {
                return null;
            }
        }

        private static Dictionary<string, object> SerializeSnapshot(CompanionStateSnapshot s)
        {
            return new Dictionary<string, object>
            {
                { "paired", s.Paired },
                { "deviceId", s.DeviceId },
                { "deviceName", s.DeviceName },
                { "platform", s.Platform },
                { "expiresAt", s.ExpiresAtIso },
                { "backendBaseUrl", s.BackendBaseUrl },
                { "monitorActive", s.MonitorActive },
                { "uploadInProgress", s.UploadInProgress },
                { "queuedCount", s.QueuedCount },
                { "uploadedCount", s.UploadedCount },
                { "globalPauseRemainingSec", s.GlobalPauseRemainingSec },
                { "statusBanner", s.StatusBanner },
                { "adapter", s.Adapter == null ? null : new Dictionary<string, object>
                    {
                        { "interfaceGuid", s.Adapter.InterfaceGuid },
                        { "description", s.Adapter.Description },
                        { "state", s.Adapter.State },
                        { "isRadioReady", s.Adapter.IsRadioReady },
                        { "connectedSsid", s.Adapter.ConnectedSsid }
                    }
                },
                { "lastScan", s.LastScan == null ? null : SerializeScanOutcome(s.LastScan) }
            };
        }

        private static Dictionary<string, object> SerializeScanOutcome(WindowsScanOutcome o)
        {
            return new Dictionary<string, object>
            {
                { "success", o.Success },
                { "errorKind", o.ErrorKind.ToString() },
                { "statusMessage", o.StatusMessage },
                { "locationMessage", o.LocationMessage },
                { "scannedAtEpochMs", o.ScannedAtEpochMs },
                { "location", o.Location == null ? null : new Dictionary<string, object>
                    {
                        { "latitude", o.Location.Latitude },
                        { "longitude", o.Location.Longitude },
                        { "accuracyMeters", o.Location.AccuracyMeters }
                    }
                },
                { "networks", o.Networks.Select(n => new Dictionary<string, object>
                    {
                        { "ssid", n.Ssid },
                        { "displaySsid", n.DisplaySsid },
                        { "isHiddenSsid", n.IsHiddenSsid },
                        { "bssid", n.Bssid },
                        { "signalDbm", n.SignalDbm },
                        { "linkQualityPercent", n.LinkQualityPercent },
                        { "signalQuality", n.SignalQuality },
                        { "frequencyMhz", n.FrequencyMhz },
                        { "channel", n.Channel },
                        { "band", n.Band },
                        { "security", n.Security }
                    }).ToList()
                }
            };
        }

        private static void ApplySecurityHeaders(HttpListenerResponse resp, string approvedCorsOrigin)
        {
            resp.Headers["Cache-Control"] = "no-store";
            resp.Headers["X-Content-Type-Options"] = "nosniff";
            resp.Headers["X-Frame-Options"] = "DENY";
            if (!string.IsNullOrEmpty(approvedCorsOrigin))
            {
                resp.Headers["Access-Control-Allow-Origin"] = approvedCorsOrigin;
                resp.Headers["Vary"] = "Origin";
            }
        }

        private static void WriteJson(HttpListenerResponse resp, int statusCode, string approvedCorsOrigin, object payload)
        {
            ApplySecurityHeaders(resp, approvedCorsOrigin);
            byte[] bytes = Encoding.UTF8.GetBytes(Json.Serialize(payload));
            resp.StatusCode = statusCode;
            resp.ContentType = "application/json; charset=utf-8";
            resp.ContentEncoding = Encoding.UTF8;
            resp.ContentLength64 = bytes.Length;
            resp.OutputStream.Write(bytes, 0, bytes.Length);
            resp.OutputStream.Flush();
            resp.Close();
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
