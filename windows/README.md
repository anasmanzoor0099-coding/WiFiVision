# WiFiVision Windows Companion Scanner

Native Windows laptop Wi-Fi scanner using the Windows Native Wifi API (`wlanapi.dll`), Windows User-Scoped DPAPI credential storage (`ProtectedData`), and a hardened local loopback bridge (`http://127.0.0.1:48271`).

## Features

- **Genuine 802.11 Hardware Scanning**: Queries `WlanOpenHandle`, `WlanEnumInterfaces`, `WlanScan`, `WlanGetNetworkBssList`, and `WlanGetAvailableNetworkList` for SSID, BSSID, RSSI (dBm), link quality, center frequency (MHz), derived channel, band (`2.4 GHz`, `5 GHz`, `6 GHz`), and security (`Open`, `WEP`, `WPA`, `WPA2`, `WPA3`, `Unknown`).
- **Privacy-First Workflows**:
  - `SCAN ONLY`: Displays nearby networks locally with **zero uploads**.
  - `START MONITOR / STOP MONITOR`: Queues deduplicated local observations in DPAPI-encrypted queue state while keeping auto-upload **OFF**.
  - `UPLOAD OBSERVATIONS -> UPLOAD NOW`: Displays an explicit confirmation summary (count, SSIDs, location status) before uploading to `POST /api/observations` with `source: "windows"`.
- **Honest Location Reporting**: Requests Windows Location (`GeoCoordinateWatcher`) only on explicit scan; attaches `null` coordinates when location is disabled or unavailable rather than fabricating defaults.
- **Hardened Loopback Bridge (`127.0.0.1:48271`)**: Validates `Host`, strict `Origin` allowlist, per-launch `X-WiFiVision-Local-Token`, 64 KB body cap (`413`), and sliding-window rate limits (`429`).

## Build & Run (Built-in .NET Framework 4.8 MSBuild)

```powershell
cd C:\Users\user\Desktop\WiFiVision\windows
C:\Windows\Microsoft.NET\Framework64\v4.0.30319\MSBuild.exe WiFiVision.Windows.csproj /p:Configuration=Release /p:Platform=x64
.\bin\Release\WiFiVisionCompanion.exe
```

Run tests + live read-only hardware scan smoke test:

```powershell
C:\Windows\Microsoft.NET\Framework64\v4.0.30319\MSBuild.exe WiFiVision.Windows.Tests.csproj /p:Configuration=Release /p:Platform=x64
.\bin\Tests\WiFiVision.Windows.Tests.exe --live-scan
```
