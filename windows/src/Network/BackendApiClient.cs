using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;
using WiFiVision.Windows.Models;
using WiFiVision.Windows.Security;

namespace WiFiVision.Windows.Network
{
    public sealed class HealthStatusResult
    {
        public bool Reachable { get; set; }
        public bool Healthy { get; set; }
        public int? StatusCode { get; set; }
        public string Message { get; set; }
    }

    public sealed class PairingResult
    {
        public bool Success { get; set; }
        public int? StatusCode { get; set; }
        public string Message { get; set; }
        public PairedWindowsCredential Credential { get; set; }
    }

    public sealed class UploadAttemptResult
    {
        public bool Success { get; set; }
        public bool Unauthorized { get; set; }
        public bool RateLimited { get; set; }
        public bool ClientRejected { get; set; }
        public bool TransientFailure { get; set; }
        public int? StatusCode { get; set; }
        public long? RetryAfterSeconds { get; set; }
        public string ErrorCode { get; set; }
        public string Message { get; set; }
    }

    public sealed class BackendApiClient
    {
        private static readonly Regex PairingCodeRegex = new Regex("^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6,8}$", RegexOptions.Compiled);
        private static readonly JavaScriptSerializer Json = new JavaScriptSerializer();

        static BackendApiClient()
        {
            // Enforce TLS 1.2+ on .NET Framework 4.8 HTTPS connections
            ServicePointManager.SecurityProtocol |= SecurityProtocolType.Tls12 | (SecurityProtocolType)12288; // Tls13
        }

        public static string ValidateBaseUrl(string rawUrl, bool allowLocalhostHttp, out string errorReason)
        {
            errorReason = null;
            string trimmed = (rawUrl ?? "").Trim().TrimEnd('/');
            if (string.IsNullOrEmpty(trimmed))
            {
                errorReason = "Backend API base URL cannot be empty.";
                return null;
            }

            Uri uri;
            if (!Uri.TryCreate(trimmed, UriKind.Absolute, out uri))
            {
                errorReason = "Invalid backend URL syntax.";
                return null;
            }

            string scheme = uri.Scheme.ToLowerInvariant();
            if (scheme != "https" && scheme != "http")
            {
                errorReason = "Only https:// (or localhost http:// in development) URLs are supported.";
                return null;
            }

            bool isLoopback = uri.Host.Equals("localhost", StringComparison.OrdinalIgnoreCase) || uri.Host == "127.0.0.1";
            if (scheme == "http" && (!allowLocalhostHttp || !isLoopback))
            {
                errorReason = "HTTPS is required for non-localhost backend URLs.";
                return null;
            }

            if (!string.IsNullOrEmpty(uri.UserInfo))
            {
                errorReason = "URL must not contain embedded credentials.";
                return null;
            }

            if (!string.IsNullOrEmpty(uri.Query) || !string.IsNullOrEmpty(uri.Fragment))
            {
                errorReason = "Base URL must not contain query parameters or fragments.";
                return null;
            }

            return trimmed;
        }

        public static string NormalizePairingCode(string rawCode)
        {
            if (string.IsNullOrWhiteSpace(rawCode)) return null;
            string cleaned = rawCode.Trim().ToUpperInvariant().Replace(" ", "").Replace("-", "");
            return PairingCodeRegex.IsMatch(cleaned) ? cleaned : null;
        }

        public HealthStatusResult CheckHealth(string baseUrl)
        {
            string err;
            string valid = ValidateBaseUrl(baseUrl, true, out err);
            if (valid == null)
            {
                return new HealthStatusResult { Reachable = false, Healthy = false, Message = err };
            }

            try
            {
                var req = (HttpWebRequest)WebRequest.Create(valid + "/api/health");
                req.Method = "GET";
                req.Timeout = 5000;
                req.Accept = "application/json";

                using (var resp = (HttpWebResponse)req.GetResponse())
                {
                    int code = (int)resp.StatusCode;
                    string body = ReadStream(resp.GetResponseStream());
                    var map = ParseJson(body);
                    string db = map != null && map.ContainsKey("database") ? Convert.ToString(map["database"]) : "connected";
                    return new HealthStatusResult
                    {
                        Reachable = true,
                        Healthy = code == 200,
                        StatusCode = code,
                        Message = code == 200 ? "Connected (" + db + ")" : "Backend status HTTP " + code
                    };
                }
            }
            catch (WebException ex)
            {
                var errResp = ex.Response as HttpWebResponse;
                if (errResp != null)
                {
                    return new HealthStatusResult
                    {
                        Reachable = true,
                        Healthy = false,
                        StatusCode = (int)errResp.StatusCode,
                        Message = "Backend degraded (HTTP " + (int)errResp.StatusCode + ")"
                    };
                }
                return new HealthStatusResult { Reachable = false, Healthy = false, Message = "Unreachable: " + ex.Message };
            }
        }

        public PairingResult PairWindowsDevice(string baseUrl, string rawPairingCode, string deviceName)
        {
            string err;
            string validUrl = ValidateBaseUrl(baseUrl, true, out err);
            if (validUrl == null)
                return new PairingResult { Success = false, Message = err };

            string code = NormalizePairingCode(rawPairingCode);
            if (code == null)
            {
                return new PairingResult
                {
                    Success = false,
                    StatusCode = 400,
                    Message = "Enter the 6-8 character pairing code generated in web Settings."
                };
            }

            string cleanName = string.IsNullOrWhiteSpace(deviceName) ? Environment.MachineName : deviceName.Trim();
            if (cleanName.Length > 60) cleanName = cleanName.Substring(0, 60);

            string jsonBody = Json.Serialize(new Dictionary<string, object>
            {
                { "pairingCode", code },
                { "platform", "windows" },
                { "deviceName", cleanName }
            });

            return ExecutePairingCall(validUrl + "/api/devices/pair", jsonBody, null);
        }

        public PairingResult RotateCredential(string baseUrl, string currentDeviceToken)
        {
            string err;
            string validUrl = ValidateBaseUrl(baseUrl, true, out err);
            if (validUrl == null)
                return new PairingResult { Success = false, Message = err };

            if (!WindowsCredentialStore.IsValidTokenFormat(currentDeviceToken))
                return new PairingResult { Success = false, StatusCode = 401, Message = "No valid local device credential." };

            return ExecutePairingCall(validUrl + "/api/devices/rotate", "{}", currentDeviceToken);
        }

        public UploadAttemptResult UploadObservation(string baseUrl, ObservationPayload payload, string deviceToken)
        {
            string err;
            string validUrl = ValidateBaseUrl(baseUrl, true, out err);
            if (validUrl == null)
                return new UploadAttemptResult { TransientFailure = true, Message = err };

            if (payload == null || !payload.IsValid())
                return new UploadAttemptResult { ClientRejected = true, StatusCode = 400, Message = "Invalid observation payload." };

            if (!WindowsCredentialStore.IsValidTokenFormat(deviceToken))
                return new UploadAttemptResult { Unauthorized = true, StatusCode = 401, Message = "Device not paired." };

            string jsonBody = Json.Serialize(payload.ToApiDictionary());
            return ExecuteUploadCall(validUrl + "/api/observations", jsonBody, deviceToken);
        }

        private static PairingResult ExecutePairingCall(string endpointUrl, string jsonBody, string deviceToken)
        {
            try
            {
                var req = CreatePostRequest(endpointUrl, jsonBody, deviceToken);
                using (var resp = (HttpWebResponse)req.GetResponse())
                {
                    return ParsePairingSuccess((int)resp.StatusCode, ReadStream(resp.GetResponseStream()));
                }
            }
            catch (WebException ex)
            {
                var errResp = ex.Response as HttpWebResponse;
                if (errResp != null)
                {
                    int code = (int)errResp.StatusCode;
                    string msg = ExtractMessage(ReadStream(errResp.GetResponseStream()), "HTTP " + code);
                    return new PairingResult { Success = false, StatusCode = code, Message = msg };
                }
                return new PairingResult { Success = false, Message = ex.Message };
            }
        }

        private static UploadAttemptResult ExecuteUploadCall(string endpointUrl, string jsonBody, string deviceToken)
        {
            try
            {
                var req = CreatePostRequest(endpointUrl, jsonBody, deviceToken);
                using (var resp = (HttpWebResponse)req.GetResponse())
                {
                    int code = (int)resp.StatusCode;
                    string msg = ExtractMessage(ReadStream(resp.GetResponseStream()), "Saved");
                    return new UploadAttemptResult { Success = true, StatusCode = code, Message = msg };
                }
            }
            catch (WebException ex)
            {
                var errResp = ex.Response as HttpWebResponse;
                if (errResp != null)
                {
                    int code = (int)errResp.StatusCode;
                    string raw = ReadStream(errResp.GetResponseStream());
                    var map = ParseJson(raw);
                    string msg = map != null && map.ContainsKey("message") ? Convert.ToString(map["message"]) : "HTTP " + code;
                    string errCode = map != null && map.ContainsKey("code") ? Convert.ToString(map["code"]) : null;

                    if (code == 401)
                        return new UploadAttemptResult { Unauthorized = true, StatusCode = 401, ErrorCode = errCode, Message = msg };
                    if (code == 429)
                    {
                        long? retryAfter = null;
                        long parsedRetry;
                        if (long.TryParse(errResp.Headers["Retry-After"], out parsedRetry) && parsedRetry > 0)
                            retryAfter = parsedRetry;
                        return new UploadAttemptResult { RateLimited = true, StatusCode = 429, RetryAfterSeconds = retryAfter, Message = msg };
                    }
                    if (code >= 400 && code < 500)
                        return new UploadAttemptResult { ClientRejected = true, StatusCode = code, Message = msg };
                    return new UploadAttemptResult { TransientFailure = true, StatusCode = code, Message = msg };
                }
                return new UploadAttemptResult { TransientFailure = true, Message = ex.Message };
            }
        }

        private static HttpWebRequest CreatePostRequest(string url, string jsonBody, string deviceToken)
        {
            byte[] bodyBytes = Encoding.UTF8.GetBytes(jsonBody);
            var req = (HttpWebRequest)WebRequest.Create(url);
            req.Method = "POST";
            req.Timeout = 10000;
            req.ContentType = "application/json; charset=utf-8";
            req.Accept = "application/json";
            req.ContentLength = bodyBytes.Length;

            if (!string.IsNullOrEmpty(deviceToken))
            {
                req.Headers["Authorization"] = "Bearer " + deviceToken;
                req.Headers["X-WiFiVision-Timestamp"] = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString();
                req.Headers["X-WiFiVision-Nonce"] = WindowsCredentialStore.GenerateSecureRandomHex(16);
            }

            using (var stream = req.GetRequestStream())
            {
                stream.Write(bodyBytes, 0, bodyBytes.Length);
            }
            return req;
        }

        private static PairingResult ParsePairingSuccess(int statusCode, string body)
        {
            var map = ParseJson(body);
            if (map == null || !map.ContainsKey("deviceToken"))
                return new PairingResult { Success = false, StatusCode = statusCode, Message = "Invalid pairing response." };

            string token = Convert.ToString(map["deviceToken"]);
            var deviceMap = map.ContainsKey("device") ? map["device"] as Dictionary<string, object> : null;
            string deviceId = deviceMap != null && deviceMap.ContainsKey("deviceId") ? Convert.ToString(deviceMap["deviceId"]) : null;
            string deviceName = deviceMap != null && deviceMap.ContainsKey("deviceName") ? Convert.ToString(deviceMap["deviceName"]) : "Windows Companion";
            string expiresAt = deviceMap != null && deviceMap.ContainsKey("expiresAt") ? Convert.ToString(deviceMap["expiresAt"]) : null;

            if (!WindowsCredentialStore.IsValidTokenFormat(token) || string.IsNullOrWhiteSpace(deviceId) || string.IsNullOrWhiteSpace(expiresAt))
                return new PairingResult { Success = false, StatusCode = statusCode, Message = "Malformed device credential returned by server." };

            return new PairingResult
            {
                Success = true,
                StatusCode = statusCode,
                Message = ExtractMessage(body, "Paired successfully."),
                Credential = new PairedWindowsCredential
                {
                    DeviceId = deviceId,
                    DeviceName = deviceName,
                    Platform = "windows",
                    DeviceToken = token,
                    ExpiresAtIso = expiresAt
                }
            };
        }

        private static string ReadStream(Stream stream)
        {
            if (stream == null) return "";
            using (var reader = new StreamReader(stream, Encoding.UTF8))
            {
                return reader.ReadToEnd();
            }
        }

        private static Dictionary<string, object> ParseJson(string raw)
        {
            if (string.IsNullOrWhiteSpace(raw)) return null;
            try { return Json.Deserialize<Dictionary<string, object>>(raw); }
            catch { return null; }
        }

        private static string ExtractMessage(string raw, string fallback)
        {
            var map = ParseJson(raw);
            if (map != null && map.ContainsKey("message") && map["message"] != null)
                return Convert.ToString(map["message"]);
            return fallback;
        }
    }
}
