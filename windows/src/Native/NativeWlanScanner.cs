using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using WiFiVision.Windows.Location;
using WiFiVision.Windows.Models;

namespace WiFiVision.Windows.Native
{
    public interface IWlanScanner
    {
        WifiAdapterInfo GetAdapterStatus();
        WindowsScanOutcome ScanNow(bool includeLocation = true);
    }

    /// <summary>
    /// Native Windows 10/11 Wi-Fi scanner using Win32 wlanapi.dll P/Invoke.
    /// Does not require elevated administrator privileges (requires Windows Location
    /// permission enabled on Windows 11 24H2+ for BSSID/scan access).
    /// </summary>
    public sealed class NativeWlanScanner : IWlanScanner
    {
        private const uint ERROR_SUCCESS = 0;
        private const uint ERROR_ACCESS_DENIED = 5;
        private const uint ERROR_NOT_SUPPORTED = 50;
        private const uint ERROR_SERVICE_NOT_ACTIVE = 1062;
        private const uint ERROR_INVALID_STATE = 5023;
        private const uint ERROR_NDIS_DOT11_POWER_STATE_INVALID = 0x80342002;
        private const uint WLAN_NOTIFICATION_SOURCE_ACM = 0x00000008;
        private const uint WLAN_NOTIFICATION_ACM_SCAN_COMPLETE = 7;
        private const uint WLAN_NOTIFICATION_ACM_SCAN_FAIL = 8;
        private const uint WLAN_AVAILABLE_NETWORK_INCLUDE_ALL_ADHOC_PROFILES = 0x00000001;
        private const uint WLAN_AVAILABLE_NETWORK_INCLUDE_ALL_MANUAL_HIDDEN_PROFILES = 0x00000002;
        private const int WLAN_INTF_OPCODE_CURRENT_CONNECTION = 7;
        private const int DOT11_BSS_ANY = 3;

        private readonly IWindowsLocationProvider _locationProvider;

        public NativeWlanScanner() : this(new WindowsLocationService())
        {
        }

        public NativeWlanScanner(IWindowsLocationProvider locationProvider)
        {
            if (locationProvider == null) throw new ArgumentNullException("locationProvider");
            _locationProvider = locationProvider;
        }

        private delegate void WlanNotificationCallback(ref WLAN_NOTIFICATION_DATA data, IntPtr context);

        [DllImport("wlanapi.dll")]
        private static extern uint WlanOpenHandle(uint dwClientVersion, IntPtr pReserved, out uint pdwNegotiatedVersion, out IntPtr phClientHandle);

        [DllImport("wlanapi.dll")]
        private static extern uint WlanCloseHandle(IntPtr hClientHandle, IntPtr pReserved);

        [DllImport("wlanapi.dll")]
        private static extern uint WlanEnumInterfaces(IntPtr hClientHandle, IntPtr pReserved, out IntPtr ppInterfaceList);

        [DllImport("wlanapi.dll")]
        private static extern uint WlanScan(IntPtr hClientHandle, ref Guid pInterfaceGuid, IntPtr pDot11Ssid, IntPtr pIeData, IntPtr pReserved);

        [DllImport("wlanapi.dll")]
        private static extern uint WlanRegisterNotification(IntPtr hClientHandle, uint dwNotifSource, bool bIgnoreDuplicate, WlanNotificationCallback funcCallback, IntPtr pCallbackContext, IntPtr pReserved, out uint pdwPrevNotifSource);

        [DllImport("wlanapi.dll")]
        private static extern uint WlanGetAvailableNetworkList(IntPtr hClientHandle, ref Guid pInterfaceGuid, uint dwFlags, IntPtr pReserved, out IntPtr ppAvailableNetworkList);

        [DllImport("wlanapi.dll")]
        private static extern uint WlanGetNetworkBssList(IntPtr hClientHandle, ref Guid pInterfaceGuid, IntPtr pDot11Ssid, int dot11BssType, bool bSecurityEnabled, IntPtr pReserved, out IntPtr ppWlanBssList);

        [DllImport("wlanapi.dll")]
        private static extern uint WlanQueryInterface(IntPtr hClientHandle, ref Guid pInterfaceGuid, int OpCode, IntPtr pReserved, out uint pdwDataSize, out IntPtr ppData, out int pWlanOpcodeValueType);

        [DllImport("wlanapi.dll")]
        private static extern void WlanFreeMemory(IntPtr pMemory);

        [StructLayout(LayoutKind.Sequential)]
        private struct WLAN_NOTIFICATION_DATA
        {
            public uint NotificationSource;
            public uint NotificationCode;
            public Guid InterfaceGuid;
            public uint dwDataSize;
            public IntPtr pData;
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct WLAN_INTERFACE_INFO
        {
            public Guid InterfaceGuid;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)]
            public string strInterfaceDescription;
            public int isState;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct DOT11_SSID
        {
            public uint uSSIDLength;
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 32)]
            public byte[] ucSSID;
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct WLAN_AVAILABLE_NETWORK
        {
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)]
            public string strProfileName;
            public DOT11_SSID dot11Ssid;
            public int dot11BssType;
            public uint uNumberOfBssids;
            public bool bNetworkConnectable;
            public uint wlanNotConnectableReason;
            public uint uNumberOfPhyTypes;
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 8)]
            public uint[] dot11PhyTypes;
            public bool bMorePhyTypes;
            public uint wlanSignalQuality;
            public bool bSecurityEnabled;
            public int dot11DefaultAuthAlgorithm;
            public int dot11DefaultCipherAlgorithm;
            public uint dwFlags;
            public uint dwReserved;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct WLAN_RATE_SET
        {
            public uint uRateSetLength;
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 126)]
            public ushort[] usRateSet;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct WLAN_BSS_ENTRY
        {
            public DOT11_SSID dot11Ssid;
            public uint uPhyId;
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 6)]
            public byte[] dot11Bssid;
            public int dot11BssType;
            public int dot11BssPhyType;
            public int lRssi;
            public uint uLinkQuality;
            public bool bInRegDomain;
            public ushort usBeaconPeriod;
            public ulong ullTimestamp;
            public ulong ullHostTimestamp;
            public ushort usCapabilityInformation;
            public uint ulChCenterFrequency;
            public WLAN_RATE_SET wlanRateSet;
            public uint ulIeOffset;
            public uint ulIeSize;
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct WLAN_CONNECTION_ATTRIBUTES
        {
            public int isState;
            public int wlanConnectionMode;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)]
            public string strProfileName;
            public DOT11_SSID dot11Ssid;
        }

        public WifiAdapterInfo GetAdapterStatus()
        {
            IntPtr handle = IntPtr.Zero;
            IntPtr ifListPtr = IntPtr.Zero;
            try
            {
                uint neg;
                uint openRes = WlanOpenHandle(2, IntPtr.Zero, out neg, out handle);
                if (openRes != ERROR_SUCCESS)
                {
                    return new WifiAdapterInfo
                    {
                        Description = "WLAN service unavailable",
                        State = openRes == ERROR_SERVICE_NOT_ACTIVE ? "ServiceStopped" : "Error",
                        IsRadioReady = false
                    };
                }

                uint enumRes = WlanEnumInterfaces(handle, IntPtr.Zero, out ifListPtr);
                if (enumRes != ERROR_SUCCESS || ifListPtr == IntPtr.Zero)
                {
                    return new WifiAdapterInfo
                    {
                        Description = "No Wi-Fi adapter detected",
                        State = "MissingAdapter",
                        IsRadioReady = false
                    };
                }

                int count = Marshal.ReadInt32(ifListPtr, 0);
                if (count <= 0)
                {
                    return new WifiAdapterInfo
                    {
                        Description = "No Wi-Fi adapter detected",
                        State = "MissingAdapter",
                        IsRadioReady = false
                    };
                }

                IntPtr entryPtr = new IntPtr(ifListPtr.ToInt64() + 8);
                var info = (WLAN_INTERFACE_INFO)Marshal.PtrToStructure(entryPtr, typeof(WLAN_INTERFACE_INFO));
                string connectedSsid = QueryConnectedSsid(handle, info.InterfaceGuid);

                return new WifiAdapterInfo
                {
                    InterfaceGuid = info.InterfaceGuid.ToString(),
                    Description = info.strInterfaceDescription,
                    State = MapInterfaceState(info.isState),
                    IsRadioReady = info.isState != 0,
                    ConnectedSsid = connectedSsid
                };
            }
            catch
            {
                return new WifiAdapterInfo
                {
                    Description = "Windows WLAN API unavailable",
                    State = "Error",
                    IsRadioReady = false
                };
            }
            finally
            {
                if (ifListPtr != IntPtr.Zero) WlanFreeMemory(ifListPtr);
                if (handle != IntPtr.Zero) WlanCloseHandle(handle, IntPtr.Zero);
            }
        }

        public WindowsScanOutcome ScanNow(bool includeLocation = true)
        {
            long scannedAtMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            IntPtr handle = IntPtr.Zero;
            IntPtr ifListPtr = IntPtr.Zero;

            LocationAcquisitionResult locResult = includeLocation
                ? _locationProvider.TryGetLocation(1500)
                : new LocationAcquisitionResult { Location = null, StatusMessage = "Location skipped" };

            try
            {
                uint neg;
                uint openRes = WlanOpenHandle(2, IntPtr.Zero, out neg, out handle);
                if (openRes != ERROR_SUCCESS)
                {
                    return BuildErrorOutcome(
                        openRes == ERROR_SERVICE_NOT_ACTIVE ? WlanScanErrorKind.ServiceUnavailable : WlanScanErrorKind.UnsupportedHardware,
                        "Windows WLAN AutoConfig service unavailable (error " + openRes + ").",
                        null,
                        locResult,
                        scannedAtMs
                    );
                }

                uint enumRes = WlanEnumInterfaces(handle, IntPtr.Zero, out ifListPtr);
                if (enumRes != ERROR_SUCCESS || ifListPtr == IntPtr.Zero)
                {
                    return BuildErrorOutcome(
                        WlanScanErrorKind.MissingAdapter,
                        "Unable to enumerate Wi-Fi adapters (error " + enumRes + ").",
                        null,
                        locResult,
                        scannedAtMs
                    );
                }

                int count = Marshal.ReadInt32(ifListPtr, 0);
                if (count <= 0)
                {
                    return BuildErrorOutcome(
                        WlanScanErrorKind.MissingAdapter,
                        "No Wi-Fi network adapter was found on this Windows device.",
                        new WifiAdapterInfo { Description = "No Wi-Fi adapter", State = "MissingAdapter", IsRadioReady = false },
                        locResult,
                        scannedAtMs
                    );
                }

                IntPtr entryPtr = new IntPtr(ifListPtr.ToInt64() + 8);
                var iface = (WLAN_INTERFACE_INFO)Marshal.PtrToStructure(entryPtr, typeof(WLAN_INTERFACE_INFO));
                Guid guid = iface.InterfaceGuid;
                var adapterInfo = new WifiAdapterInfo
                {
                    InterfaceGuid = guid.ToString(),
                    Description = iface.strInterfaceDescription,
                    State = MapInterfaceState(iface.isState),
                    IsRadioReady = iface.isState != 0,
                    ConnectedSsid = QueryConnectedSsid(handle, guid)
                };

                // Trigger active scan and wait briefly for WLAN ACM scan completion
                var scanDone = new ManualResetEventSlim(false);
                WlanNotificationCallback cb = (ref WLAN_NOTIFICATION_DATA data, IntPtr ctx) =>
                {
                    if (data.NotificationSource == WLAN_NOTIFICATION_SOURCE_ACM &&
                        data.InterfaceGuid == guid &&
                        (data.NotificationCode == WLAN_NOTIFICATION_ACM_SCAN_COMPLETE ||
                         data.NotificationCode == WLAN_NOTIFICATION_ACM_SCAN_FAIL))
                    {
                        scanDone.Set();
                    }
                };

                uint prevSource;
                bool notifRegistered = WlanRegisterNotification(
                    handle,
                    WLAN_NOTIFICATION_SOURCE_ACM,
                    true,
                    cb,
                    IntPtr.Zero,
                    IntPtr.Zero,
                    out prevSource
                ) == ERROR_SUCCESS;

                uint scanCode = WlanScan(handle, ref guid, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);
                if (scanCode == ERROR_SUCCESS)
                {
                    if (notifRegistered) scanDone.Wait(4000);
                    else Thread.Sleep(1500);
                }
                else
                {
                    if (notifRegistered)
                    {
                        uint ignored;
                        WlanRegisterNotification(handle, 0, true, null, IntPtr.Zero, IntPtr.Zero, out ignored);
                    }
                    if (scanCode == ERROR_ACCESS_DENIED)
                    {
                        return BuildErrorOutcome(
                            WlanScanErrorKind.PermissionDenied,
                            "Windows denied Wi-Fi scan permission. Enable Settings -> Privacy & security -> Location for desktop apps.",
                            adapterInfo,
                            locResult,
                            scannedAtMs
                        );
                    }
                    if (scanCode == ERROR_NDIS_DOT11_POWER_STATE_INVALID || scanCode == ERROR_INVALID_STATE)
                    {
                        return BuildErrorOutcome(
                            WlanScanErrorKind.WifiDisabled,
                            "Wi-Fi radio is turned off or unavailable on the adapter.",
                            adapterInfo,
                            locResult,
                            scannedAtMs
                        );
                    }
                    if (scanCode == ERROR_NOT_SUPPORTED)
                    {
                        return BuildErrorOutcome(
                            WlanScanErrorKind.UnsupportedHardware,
                            "Wi-Fi adapter driver reported WlanScan is unsupported.",
                            adapterInfo,
                            locResult,
                            scannedAtMs
                        );
                    }
                }

                if (notifRegistered)
                {
                    uint ignored;
                    WlanRegisterNotification(handle, 0, true, null, IntPtr.Zero, IntPtr.Zero, out ignored);
                }

                // Read per-SSID security map from available network list
                var securityBySsid = ReadSecurityBySsid(handle, ref guid);

                // Read granular per-BSSID entries from native BSS list
                IntPtr bssPtr;
                uint bssRes = WlanGetNetworkBssList(handle, ref guid, IntPtr.Zero, DOT11_BSS_ANY, false, IntPtr.Zero, out bssPtr);
                if (bssRes != ERROR_SUCCESS)
                {
                    if (bssRes == ERROR_ACCESS_DENIED)
                    {
                        return BuildErrorOutcome(
                            WlanScanErrorKind.PermissionDenied,
                            "Location/Wi-Fi permission denied while reading nearby BSSID scan results.",
                            adapterInfo,
                            locResult,
                            scannedAtMs
                        );
                    }
                    if (bssRes == ERROR_NDIS_DOT11_POWER_STATE_INVALID || bssRes == ERROR_INVALID_STATE)
                    {
                        return BuildErrorOutcome(
                            WlanScanErrorKind.WifiDisabled,
                            "Wi-Fi radio is turned off.",
                            adapterInfo,
                            locResult,
                            scannedAtMs
                        );
                    }
                    return BuildErrorOutcome(
                        WlanScanErrorKind.ScanFailed,
                        "Failed to retrieve nearby Wi-Fi BSS entries (error " + bssRes + ").",
                        adapterInfo,
                        locResult,
                        scannedAtMs
                    );
                }

                var networks = new List<WindowsScanNetwork>();
                try
                {
                    if (bssPtr != IntPtr.Zero)
                    {
                        int bssCount = Marshal.ReadInt32(bssPtr, 4);
                        int entrySize = Marshal.SizeOf(typeof(WLAN_BSS_ENTRY));
                        for (int i = 0; i < bssCount; i++)
                        {
                            IntPtr ep = new IntPtr(bssPtr.ToInt64() + 8 + i * entrySize);
                            var entry = (WLAN_BSS_ENTRY)Marshal.PtrToStructure(ep, typeof(WLAN_BSS_ENTRY));
                            var net = ConvertBssEntry(entry, ep, securityBySsid);
                            if (net != null) networks.Add(net);
                        }
                    }
                }
                finally
                {
                    if (bssPtr != IntPtr.Zero) WlanFreeMemory(bssPtr);
                }

                networks = networks.OrderByDescending(n => n.SignalDbm).ToList();
                string summary = networks.Count > 0
                    ? networks.Count + " nearby Wi-Fi BSSID(s) detected on " + adapterInfo.Description
                    : "Scan completed cleanly: 0 nearby networks found.";

                return new WindowsScanOutcome
                {
                    Success = true,
                    ErrorKind = WlanScanErrorKind.None,
                    StatusMessage = summary,
                    LocationMessage = locResult.StatusMessage,
                    Adapter = adapterInfo,
                    Location = locResult.Location,
                    ScannedAtEpochMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                    Networks = networks
                };
            }
            catch (DllNotFoundException)
            {
                return BuildErrorOutcome(
                    WlanScanErrorKind.UnsupportedHardware,
                    "Native Windows WLAN API (wlanapi.dll) is unavailable on this system.",
                    null,
                    locResult,
                    scannedAtMs
                );
            }
            catch (Exception ex)
            {
                return BuildErrorOutcome(
                    WlanScanErrorKind.ScanFailed,
                    "Windows Wi-Fi scan error: " + ex.Message,
                    null,
                    locResult,
                    scannedAtMs
                );
            }
            finally
            {
                if (ifListPtr != IntPtr.Zero) WlanFreeMemory(ifListPtr);
                if (handle != IntPtr.Zero) WlanCloseHandle(handle, IntPtr.Zero);
            }
        }

        private static Dictionary<string, string> ReadSecurityBySsid(IntPtr handle, ref Guid guid)
        {
            var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            IntPtr availPtr = IntPtr.Zero;
            try
            {
                uint code = WlanGetAvailableNetworkList(
                    handle,
                    ref guid,
                    WLAN_AVAILABLE_NETWORK_INCLUDE_ALL_ADHOC_PROFILES | WLAN_AVAILABLE_NETWORK_INCLUDE_ALL_MANUAL_HIDDEN_PROFILES,
                    IntPtr.Zero,
                    out availPtr
                );
                if (code != ERROR_SUCCESS || availPtr == IntPtr.Zero)
                    return result;

                int count = Marshal.ReadInt32(availPtr, 0);
                int size = Marshal.SizeOf(typeof(WLAN_AVAILABLE_NETWORK));
                for (int i = 0; i < count; i++)
                {
                    IntPtr ep = new IntPtr(availPtr.ToInt64() + 8 + i * size);
                    var net = (WLAN_AVAILABLE_NETWORK)Marshal.PtrToStructure(ep, typeof(WLAN_AVAILABLE_NETWORK));
                    string ssid = DecodeSsid(net.dot11Ssid);
                    if (string.IsNullOrEmpty(ssid)) continue;

                    string mapped = WlanTaxonomy.MapDot11AuthAndCipher(
                        net.dot11DefaultAuthAlgorithm,
                        net.dot11DefaultCipherAlgorithm,
                        net.bSecurityEnabled
                    );

                    if (!result.ContainsKey(ssid) || mapped != "Unknown")
                    {
                        result[ssid] = mapped;
                    }
                }
            }
            catch
            {
                // Ignore available list read failure cleanly
            }
            finally
            {
                if (availPtr != IntPtr.Zero) WlanFreeMemory(availPtr);
            }
            return result;
        }

        private static WindowsScanNetwork ConvertBssEntry(
            WLAN_BSS_ENTRY entry,
            IntPtr entryPtr,
            Dictionary<string, string> securityBySsid)
        {
            int rssi = entry.lRssi;
            if (rssi < -127 || rssi > 0) return null;

            int freqMhz = entry.ulChCenterFrequency > 10000
                ? (int)(entry.ulChCenterFrequency / 1000)
                : (int)entry.ulChCenterFrequency;
            if (freqMhz <= 0) return null;

            string rawSsid = DecodeSsid(entry.dot11Ssid);
            bool hidden = string.IsNullOrWhiteSpace(rawSsid);

            string bssid = null;
            if (entry.dot11Bssid != null && entry.dot11Bssid.Length == 6)
            {
                string formatted = BitConverter.ToString(entry.dot11Bssid).Replace("-", ":").ToUpperInvariant();
                if (formatted != "00:00:00:00:00:00" && formatted != "02:00:00:00:00:00")
                    bssid = formatted;
            }

            string security = ResolveSecurityFromIeOrMap(entry, entryPtr, rawSsid, securityBySsid);
            int linkQuality = (int)Math.Min(100, entry.uLinkQuality);

            return new WindowsScanNetwork
            {
                Ssid = hidden ? null : rawSsid,
                IsHiddenSsid = hidden,
                Bssid = bssid,
                SignalDbm = rssi,
                LinkQualityPercent = linkQuality,
                SignalQuality = WlanTaxonomy.MapLinkQualityToSignalQuality(linkQuality),
                FrequencyMhz = freqMhz,
                Channel = WlanTaxonomy.DeriveChannelFromFrequencyMhz(freqMhz),
                Band = WlanTaxonomy.ClassifyBand(freqMhz),
                Security = security
            };
        }

        private static string ResolveSecurityFromIeOrMap(
            WLAN_BSS_ENTRY entry,
            IntPtr entryPtr,
            string ssid,
            Dictionary<string, string> securityBySsid)
        {
            if (!string.IsNullOrEmpty(ssid) && securityBySsid.ContainsKey(ssid))
            {
                string fromList = securityBySsid[ssid];
                if (fromList != "Unknown") return fromList;
            }

            // Inspect 802.11 Information Elements for RSN (tag 48) / WPA (vendor tag 221) / Privacy bit (0x0010)
            if (entry.ulIeSize > 0 && entry.ulIeSize <= 4096 && entry.ulIeOffset > 0)
            {
                try
                {
                    byte[] ieBytes = new byte[entry.ulIeSize];
                    Marshal.Copy(new IntPtr(entryPtr.ToInt64() + entry.ulIeOffset), ieBytes, 0, (int)entry.ulIeSize);
                    bool hasRsn = false;
                    bool hasWpa3Akm = false;
                    bool hasWpa1 = false;

                    int idx = 0;
                    while (idx + 2 <= ieBytes.Length)
                    {
                        byte id = ieBytes[idx];
                        byte len = ieBytes[idx + 1];
                        if (idx + 2 + len > ieBytes.Length) break;

                        if (id == 48 && len >= 2)
                        {
                            hasRsn = true;
                            if (ContainsWpa3AkmSuite(ieBytes, idx + 2, len))
                                hasWpa3Akm = true;
                        }
                        else if (id == 221 && len >= 4 &&
                                 ieBytes[idx + 2] == 0x00 && ieBytes[idx + 3] == 0x50 &&
                                 ieBytes[idx + 4] == 0xF2 && ieBytes[idx + 5] == 0x01)
                        {
                            hasWpa1 = true;
                        }
                        idx += 2 + len;
                    }

                    if (hasWpa3Akm) return "WPA3";
                    if (hasRsn) return "WPA2";
                    if (hasWpa1) return "WPA";
                    if ((entry.usCapabilityInformation & 0x0010) == 0) return "Open";
                }
                catch
                {
                    // Fallback cleanly without guessing
                }
            }

            return "Unknown";
        }

        private static bool ContainsWpa3AkmSuite(byte[] ieBytes, int offset, int length)
        {
            // Scan RSN IE body cleanly for OUI 00-0F-AC with AKM suite type 8 (SAE), 9 (FT-SAE), 18 (OWE), 24 (SAE-EXT)
            for (int i = offset; i + 4 <= offset + length; i++)
            {
                if (ieBytes[i] == 0x00 && ieBytes[i + 1] == 0x0F && ieBytes[i + 2] == 0xAC)
                {
                    byte type = ieBytes[i + 3];
                    if (type == 8 || type == 9 || type == 12 || type == 24)
                        return true;
                }
            }
            return false;
        }

        private static string QueryConnectedSsid(IntPtr handle, Guid guid)
        {
            uint dataSize;
            IntPtr dataPtr = IntPtr.Zero;
            int valueType;
            try
            {
                uint res = WlanQueryInterface(handle, ref guid, WLAN_INTF_OPCODE_CURRENT_CONNECTION, IntPtr.Zero, out dataSize, out dataPtr, out valueType);
                if (res != ERROR_SUCCESS || dataPtr == IntPtr.Zero || dataSize < Marshal.SizeOf(typeof(WLAN_CONNECTION_ATTRIBUTES)))
                    return null;
                var attrs = (WLAN_CONNECTION_ATTRIBUTES)Marshal.PtrToStructure(dataPtr, typeof(WLAN_CONNECTION_ATTRIBUTES));
                string ssid = DecodeSsid(attrs.dot11Ssid);
                return string.IsNullOrWhiteSpace(ssid) ? null : ssid;
            }
            catch
            {
                return null;
            }
            finally
            {
                if (dataPtr != IntPtr.Zero) WlanFreeMemory(dataPtr);
            }
        }

        private static string DecodeSsid(DOT11_SSID dot11Ssid)
        {
            if (dot11Ssid.ucSSID == null || dot11Ssid.uSSIDLength == 0) return null;
            int len = (int)Math.Min(dot11Ssid.uSSIDLength, 32);
            string decoded = Encoding.UTF8.GetString(dot11Ssid.ucSSID, 0, len).Trim('\0', ' ');
            return string.IsNullOrWhiteSpace(decoded) ? null : decoded;
        }

        private static string MapInterfaceState(int state)
        {
            switch (state)
            {
                case 0: return "NotReady";
                case 1: return "Connected";
                case 2: return "AdHocFormed";
                case 3: return "Disconnecting";
                case 4: return "Disconnected";
                case 5: return "Associating";
                case 6: return "Discovering";
                case 7: return "Authenticating";
                default: return "Unknown";
            }
        }

        private static WindowsScanOutcome BuildErrorOutcome(
            WlanScanErrorKind kind,
            string message,
            WifiAdapterInfo adapter,
            LocationAcquisitionResult locResult,
            long scannedAtMs)
        {
            return new WindowsScanOutcome
            {
                Success = false,
                ErrorKind = kind,
                StatusMessage = message,
                LocationMessage = locResult.StatusMessage,
                Adapter = adapter,
                Location = locResult.Location,
                ScannedAtEpochMs = scannedAtMs,
                Networks = new List<WindowsScanNetwork>()
            };
        }
    }
}
