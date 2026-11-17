using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;
using System.Collections.Generic;

namespace WiFiVision.Windows.Security
{
    public sealed class PairedWindowsCredential
    {
        public string DeviceId { get; set; }
        public string DeviceName { get; set; }
        public string Platform { get; set; }
        public string DeviceToken { get; set; }
        public string ExpiresAtIso { get; set; }

        public bool IsExpired(long? nowEpochMs = null)
        {
            long current = nowEpochMs ?? DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            DateTimeOffset parsed;
            if (!DateTimeOffset.TryParse(ExpiresAtIso, out parsed))
                return true;
            return parsed.ToUnixTimeMilliseconds() <= current;
        }
    }

    public interface IDataProtector
    {
        byte[] Protect(byte[] plaintext);
        byte[] Unprotect(byte[] encrypted);
    }

    /// <summary>
    /// Windows DPAPI protector scoped strictly to the current Windows user account.
    /// </summary>
    public sealed class WindowsDpapiProtector : IDataProtector
    {
        private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("WiFiVision.Windows.Credential.v1");

        public byte[] Protect(byte[] plaintext)
        {
            return ProtectedData.Protect(plaintext, Entropy, DataProtectionScope.CurrentUser);
        }

        public byte[] Unprotect(byte[] encrypted)
        {
            return ProtectedData.Unprotect(encrypted, Entropy, DataProtectionScope.CurrentUser);
        }
    }

    public sealed class WindowsCredentialStore
    {
        private static readonly Regex TokenRegex = new Regex("^wvd_[0-9a-fA-F]{24}_[0-9a-fA-F]{64}$", RegexOptions.Compiled);
        private static readonly JavaScriptSerializer Json = new JavaScriptSerializer();

        private readonly string _credentialFilePath;
        private readonly string _settingsFilePath;
        private readonly IDataProtector _protector;
        private readonly object _sync = new object();

        public WindowsCredentialStore()
            : this(GetDefaultAppDataDir(), new WindowsDpapiProtector())
        {
        }

        public WindowsCredentialStore(string appDataDirectory, IDataProtector protector)
        {
            if (string.IsNullOrWhiteSpace(appDataDirectory))
                throw new ArgumentException("AppData directory is required.");
            Directory.CreateDirectory(appDataDirectory);
            _credentialFilePath = Path.Combine(appDataDirectory, "device_cred.dpapi");
            _settingsFilePath = Path.Combine(appDataDirectory, "settings.json");
            if (protector == null) throw new ArgumentNullException("protector");
            _protector = protector;
        }

        public static string GetDefaultAppDataDir()
        {
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(localAppData, "WiFiVisionCompanion");
        }

        public static bool IsValidTokenFormat(string token)
        {
            return !string.IsNullOrWhiteSpace(token) && TokenRegex.IsMatch(token.Trim());
        }

        public static string GenerateSecureRandomHex(int numBytes = 32)
        {
            byte[] bytes = new byte[numBytes];
            using (var rng = new RNGCryptoServiceProvider())
            {
                rng.GetBytes(bytes);
            }
            var sb = new StringBuilder(bytes.Length * 2);
            foreach (byte b in bytes) sb.Append(b.ToString("x2"));
            return sb.ToString();
        }

        public bool SaveCredential(PairedWindowsCredential credential)
        {
            if (credential == null ||
                string.IsNullOrWhiteSpace(credential.DeviceId) ||
                string.IsNullOrWhiteSpace(credential.DeviceName) ||
                !IsValidTokenFormat(credential.DeviceToken))
            {
                return false;
            }

            lock (_sync)
            {
                try
                {
                    var payload = new Dictionary<string, object>
                    {
                        { "deviceId", credential.DeviceId },
                        { "deviceName", credential.DeviceName },
                        { "platform", "windows" },
                        { "deviceToken", credential.DeviceToken },
                        { "expiresAt", credential.ExpiresAtIso }
                    };
                    string json = Json.Serialize(payload);
                    byte[] plainBytes = Encoding.UTF8.GetBytes(json);
                    byte[] encrypted = _protector.Protect(plainBytes);
                    File.WriteAllBytes(_credentialFilePath, encrypted);
                    return true;
                }
                catch
                {
                    return false;
                }
            }
        }

        public PairedWindowsCredential LoadCredential()
        {
            lock (_sync)
            {
                if (!File.Exists(_credentialFilePath))
                    return null;

                try
                {
                    byte[] encrypted = File.ReadAllBytes(_credentialFilePath);
                    byte[] plainBytes = _protector.Unprotect(encrypted);
                    string json = Encoding.UTF8.GetString(plainBytes);
                    var map = Json.Deserialize<Dictionary<string, object>>(json);
                    if (map == null)
                    {
                        ClearCredential();
                        return null;
                    }

                    string deviceId = map.ContainsKey("deviceId") ? map["deviceId"] as string : null;
                    string deviceName = map.ContainsKey("deviceName") ? map["deviceName"] as string : null;
                    string deviceToken = map.ContainsKey("deviceToken") ? map["deviceToken"] as string : null;
                    string expiresAt = map.ContainsKey("expiresAt") ? map["expiresAt"] as string : null;

                    if (string.IsNullOrWhiteSpace(deviceId) ||
                        string.IsNullOrWhiteSpace(deviceName) ||
                        !IsValidTokenFormat(deviceToken) ||
                        string.IsNullOrWhiteSpace(expiresAt))
                    {
                        ClearCredential();
                        return null;
                    }

                    return new PairedWindowsCredential
                    {
                        DeviceId = deviceId,
                        DeviceName = deviceName,
                        Platform = "windows",
                        DeviceToken = deviceToken,
                        ExpiresAtIso = expiresAt
                    };
                }
                catch
                {
                    ClearCredential();
                    return null;
                }
            }
        }

        public PairedWindowsCredential GetActiveCredential(long? nowEpochMs = null)
        {
            var cred = LoadCredential();
            if (cred == null) return null;
            return cred.IsExpired(nowEpochMs) ? null : cred;
        }

        public void ClearCredential()
        {
            lock (_sync)
            {
                try
                {
                    if (File.Exists(_credentialFilePath))
                        File.Delete(_credentialFilePath);
                }
                catch
                {
                    // Ignore file deletion error safely
                }
            }
        }

        public void SaveBackendBaseUrl(string baseUrl)
        {
            lock (_sync)
            {
                try
                {
                    var map = new Dictionary<string, object> { { "apiBaseUrl", baseUrl ?? "" } };
                    File.WriteAllText(_settingsFilePath, Json.Serialize(map), Encoding.UTF8);
                }
                catch
                {
                    // Safe fallback
                }
            }
        }

        public string LoadBackendBaseUrl()
        {
            lock (_sync)
            {
                if (!File.Exists(_settingsFilePath)) return "http://localhost:5000";
                try
                {
                    string raw = File.ReadAllText(_settingsFilePath, Encoding.UTF8);
                    var map = Json.Deserialize<Dictionary<string, object>>(raw);
                    string saved = map != null && map.ContainsKey("apiBaseUrl") ? map["apiBaseUrl"] as string : null;
                    return string.IsNullOrWhiteSpace(saved) ? "http://localhost:5000" : saved.Trim().TrimEnd('/');
                }
                catch
                {
                    return "http://localhost:5000";
                }
            }
        }
    }
}
