"use client";

import { useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  Copy,
  Download,
  FileCheck2,
  HelpCircle,
  Info,
  Laptop,
  MapPin,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Terminal,
  Wifi,
} from "lucide-react";

import { ANDROID_RELEASE } from "@/lib/releaseInfo";

const INSTALL_STEPS = [
  {
    step: "01",
    title: "Download the Signed APK",
    detail: `Tap Download Android App above on your Android phone (minimum ${ANDROID_RELEASE.minAndroidVersion}). No WiFiVision account login is needed to download the file.`,
  },
  {
    step: "02",
    title: "Allow Browser / File Manager Install",
    detail:
      'When Android prompts "For your security, your phone currently isn’t allowed to install unknown apps from this source", tap Settings and toggle Allow from this source once.',
  },
  {
    step: "03",
    title: "Confirm Installation",
    detail: `Open ${ANDROID_RELEASE.fileName} from Downloads and tap Install. Android verifies the ${ANDROID_RELEASE.signingScheme} signature before installing ${ANDROID_RELEASE.packageId}.`,
  },
  {
    step: "04",
    title: "Sign In or Pair & Scan",
    detail:
      `Launch WiFiVision v${ANDROID_RELEASE.versionName}, sign in (or register & verify your email) or pair a scoped device credential, grant Wi-Fi and location permissions when prompted, and inspect nearby networks locally.`,
  },
] as const;

const PERMISSIONS = [
  {
    name: "Nearby Wi-Fi Devices & Wi-Fi State",
    androidPerms: "NEARBY_WIFI_DEVICES, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE",
    purpose:
      "Reads nearby 2.4 GHz, 5 GHz, and 6 GHz access points, signal strength (RSSI dBm), frequency, security capabilities, and initiates explicit on-demand scans.",
  },
  {
    name: "Precise Device Location",
    androidPerms: "ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION",
    purpose:
      "Android requires location permission to read Wi-Fi BSSID/SSID scan results and optionally attaches your device GPS coordinates for heatmap mapping. Never guessed when unavailable.",
  },
  {
    name: "Network Access",
    androidPerms: "INTERNET, ACCESS_NETWORK_STATE",
    purpose:
      "Used strictly for HTTPS authentication, performance speed diagnostics, and explicit user-confirmed uploads to the configured WiFiVision backend.",
  },
] as const;

const TROUBLESHOOTING_ITEMS = [
  {
    title: '"App not installed" or signature conflict',
    fix: "If you previously installed an unsigned or debug developer build, uninstall that debug app first before installing the signed production release APK. Existing signed v1.0.1, v1.1.0, v1.1.1, v1.1.2, and v1.1.3 installs upgrade in-place cleanly.",
  },
  {
    title: "Empty Wi-Fi scan list after opening scanner",
    fix: "Ensure Wi-Fi is switched ON and Android System Location Services is enabled in Quick Settings. On Android 9+, grant Precise Location (or Nearby Wi-Fi Devices on Android 13+) for WiFiVision.",
  },
  {
    title: "Scans do not refresh every few seconds",
    fix: "Android 9+ throttles foreground Wi-Fi scan triggers across the OS (4 scans per 2 minutes). Wait briefly or disable Wi-Fi scan throttling inside Android Developer Options for rapid site surveys.",
  },
  {
    title: "Verify download integrity before sideloading",
    fix: `Compare the SHA-256 hash of ${ANDROID_RELEASE.fileName} against ${ANDROID_RELEASE.sha256.slice(0, 16)}... using Get-FileHash (Windows) or sha256sum (macOS/Linux).`,
  },
] as const;

export default function DownloadContent() {
  const [copiedSha, setCopiedSha] = useState(false);

  async function handleCopySha256() {
    try {
      await navigator.clipboard.writeText(ANDROID_RELEASE.sha256);
      setCopiedSha(true);
      window.setTimeout(() => setCopiedSha(false), 2200);
    } catch {
      // Fallback ignored when clipboard API is unavailable.
    }
  }

  return (
    <div className="mx-auto w-full max-w-[1100px] space-y-8 px-4 py-10 sm:px-6 sm:py-14">
      {/* HERO SECTION */}
      <section className="rounded-2xl border border-[#303640]/[0.09] bg-white p-6 shadow-[0_6px_28px_rgba(48,54,64,0.05)] sm:p-8">
        <div className="flex flex-col justify-between gap-6 lg:flex-row lg:items-center">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-[#C6A15B]/35 bg-[#F7F6F2] px-3 py-1 text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#2E7D5B]" />
              Public Release Distribution · No Login Required
            </div>

            <h1 className="mt-4 text-3xl font-semibold tracking-tight text-[#303640] sm:text-4xl">
              Download WiFi<span className="text-[#9F7C38]">Vision</span> for
              Android
            </h1>

            <p className="mt-3 text-sm leading-relaxed text-[#5E6572]">
              Install the signed native Android application (v
              {ANDROID_RELEASE.versionName}) for on-device Wi-Fi scanning,
              interactive OpenStreetMap heatmaps, network quality scoring, and
              explicit observation syncing.
            </p>

            <div className="mt-6 flex flex-wrap items-center gap-3">
              <a
                href={ANDROID_RELEASE.downloadPath}
                download={ANDROID_RELEASE.fileName}
                className="inline-flex h-12 items-center gap-2.5 rounded-xl bg-[#303640] px-6 text-xs font-semibold text-white shadow-sm transition hover:bg-[#3F4754] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B]"
              >
                <Download size={16} className="text-[#C6A15B]" />
                Download Android App (v{ANDROID_RELEASE.versionName} APK)
              </a>

              <Link
                href="/login"
                className="inline-flex h-12 items-center gap-2 rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-5 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#F1EFE9]"
              >
                Sign In to Web Dashboard
              </Link>
            </div>
          </div>

          <div className="w-full rounded-2xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-5 lg:w-[330px] lg:shrink-0">
            <div className="flex items-center gap-2.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#303640] text-[#C6A15B]">
                <Smartphone size={18} />
              </div>
              <div>
                <p className="text-xs font-semibold text-[#303640]">
                  Production Signed Release
                </p>
                <p className="text-[11px] text-[#2E7D5B]">
                  {ANDROID_RELEASE.signingScheme}
                </p>
              </div>
            </div>

            <dl className="mt-4 space-y-2.5 border-t border-[#303640]/[0.08] pt-3.5 text-xs">
              <div className="flex items-center justify-between gap-2">
                <dt className="text-[#5E6572]">Release Version</dt>
                <dd className="font-semibold text-[#303640]">
                  v{ANDROID_RELEASE.versionName} (code{" "}
                  {ANDROID_RELEASE.versionCode})
                </dd>
              </div>
              <div className="flex items-center justify-between gap-2">
                <dt className="text-[#5E6572]">APK File Size</dt>
                <dd className="font-semibold tabular-nums text-[#303640]">
                  {ANDROID_RELEASE.sizeShortLabel}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-2">
                <dt className="text-[#5E6572]">Exact Bytes</dt>
                <dd className="font-mono text-[11px] text-[#303640]">
                  {ANDROID_RELEASE.sizeBytes.toLocaleString()} bytes
                </dd>
              </div>
              <div className="flex items-center justify-between gap-2">
                <dt className="text-[#5E6572]">Min Android OS</dt>
                <dd className="font-semibold text-[#303640]">
                  {ANDROID_RELEASE.minAndroidVersion}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-2">
                <dt className="text-[#5E6572]">Package ID</dt>
                <dd className="font-mono text-[11px] text-[#303640]">
                  {ANDROID_RELEASE.packageId}
                </dd>
              </div>
            </dl>
          </div>
        </div>
      </section>

      {/* SHA-256 INTEGRITY VERIFICATION */}
      <section className="rounded-2xl border border-[#303640]/[0.09] bg-white p-6 shadow-xs">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-[#2E7D5B]/25 bg-[#2E7D5B]/10 text-[#2E7D5B]">
              <FileCheck2 size={18} />
            </div>
            <div>
              <h2 className="text-base font-semibold text-[#303640]">
                SHA-256 Checksum &amp; Signature Verification
              </h2>
              <p className="text-xs text-[#5E6572]">
                Verify that your downloaded{" "}
                <code>{ANDROID_RELEASE.fileName}</code> matches this signed
                build byte-for-byte.
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => void handleCopySha256()}
            className="inline-flex items-center gap-1.5 rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-3.5 py-2 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#F1EFE9]"
          >
            {copiedSha ? (
              <>
                <Check size={13} className="text-[#2E7D5B]" />
                Copied Checksum
              </>
            ) : (
              <>
                <Copy size={13} className="text-[#9F7C38]" />
                Copy SHA-256
              </>
            )}
          </button>
        </div>

        <div className="mt-4 rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
          <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#9F7C38]">
            Verified SHA-256 Digest ({ANDROID_RELEASE.fileName})
          </p>
          <p className="mt-1.5 break-all font-mono text-xs font-semibold text-[#303640]">
            {ANDROID_RELEASE.sha256}
          </p>
          <p className="mt-2.5 text-[11px] text-[#5E6572]">
            Release Signing Certificate:{" "}
            <span className="font-medium text-[#303640]">
              {ANDROID_RELEASE.signerSummary}
            </span>{" "}
            · Cert SHA-256:{" "}
            <code className="break-all rounded bg-white px-1 py-0.5 text-[10px] text-[#303640]">
              {ANDROID_RELEASE.signerCertSha256}
            </code>
          </p>
        </div>
      </section>

      {/* ANDROID OS SCAN AVAILABILITY NOTICE */}
      <section
        role="note"
        aria-label="Android Wi-Fi scan availability notice"
        className="flex items-start gap-3.5 rounded-2xl border border-[#C6A15B]/45 bg-[#FBF9F4] p-5 text-xs text-[#303640]"
      >
        <AlertTriangle size={20} className="mt-0.5 shrink-0 text-[#9F7C38]" />
        <div className="space-y-1.5">
          <h2 className="text-sm font-semibold text-[#303640]">
            Important Notice on Android Wi-Fi Scan Availability &amp; Throttling
          </h2>
          <p className="leading-relaxed text-[#5E6572]">
            Native Wi-Fi scan availability depends on Android runtime
            permissions, System Location Services being turned ON, active Wi-Fi
            radio hardware, and Android OS background/foreground scan
            restrictions. Starting with Android 9 (Pie), the operating system
            limits foreground apps to <strong>4 Wi-Fi scans per 2 minutes</strong>{" "}
            (unless &ldquo;Wi-Fi scan throttling&rdquo; is disabled in Android
            Developer Options). When the OS throttles or blocks a fresh scan,
            WiFiVision clearly displays the status returned by Android rather
            than fabricating mock networks or coordinates.
          </p>
        </div>
      </section>

      {/* INSTALL & UPDATE INSTRUCTIONS */}
      <section className="grid grid-cols-1 gap-6 lg:grid-cols-[1.35fr_1fr]">
        <div className="rounded-2xl border border-[#303640]/[0.09] bg-white p-6 shadow-xs">
          <div className="flex items-center gap-2.5">
            <ShieldCheck size={18} className="text-[#9F7C38]" />
            <h2 className="text-base font-semibold text-[#303640]">
              Android Installation Instructions
            </h2>
          </div>

          <div className="mt-4 space-y-3">
            {INSTALL_STEPS.map((item) => (
              <div
                key={item.step}
                className="flex gap-3.5 rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-3.5"
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-[#303640] text-[11px] font-bold text-[#C6A15B]">
                  {item.step}
                </span>
                <div>
                  <h3 className="text-xs font-semibold text-[#303640]">
                    {item.title}
                  </h3>
                  <p className="mt-1 text-xs leading-relaxed text-[#5E6572]">
                    {item.detail}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="space-y-6">
          <div className="rounded-2xl border border-[#303640]/[0.09] bg-white p-6 shadow-xs">
            <div className="flex items-center gap-2.5">
              <RefreshCw size={17} className="text-[#2E7D5B]" />
              <h2 className="text-base font-semibold text-[#303640]">
                Updating Existing Installs (v1.0.1 – v1.1.3 → v
                {ANDROID_RELEASE.versionName})
              </h2>
            </div>
            <ul className="mt-3.5 space-y-2.5 text-xs leading-relaxed text-[#5E6572]">
              <li className="flex items-start gap-2">
                <CheckCircle2
                  size={14}
                  className="mt-0.5 shrink-0 text-[#2E7D5B]"
                />
                <span>
                  <strong className="text-[#303640]">In-place upgrade:</strong>{" "}
                  If you already run signed release <code>v1.0.1</code> (
                  <code>versionCode 2</code>), <code>v1.1.0</code> (
                  <code>versionCode 3</code>), <code>v1.1.1</code> (
                  <code>versionCode 4</code>), <code>v1.1.2</code> (
                  <code>versionCode 5</code>), or <code>v1.1.3</code> (
                  <code>versionCode 6</code>), installing{" "}
                  <code>v{ANDROID_RELEASE.versionName}</code> (
                  <code>versionCode {ANDROID_RELEASE.versionCode}</code>)
                  upgrades directly and preserves your Android Keystore session
                  and paired device credentials. Built-in in-app update checking
                  and SHA-256 verified installs are included starting in{" "}
                  <code>v1.1.1</code>.
                </span>
              </li>
              <li className="flex items-start gap-2">
                <Info
                  size={14}
                  className="mt-0.5 shrink-0 text-[#9F7C38]"
                />
                <span>
                  <strong className="text-[#303640]">
                    Switching from a dev/debug build:
                  </strong>{" "}
                  If you previously sideloaded a local debug build from Android
                  Studio, uninstall it first so the production release signature
                  can be verified cleanly.
                </span>
              </li>
            </ul>
          </div>

          <div className="rounded-2xl border border-[#303640]/[0.09] bg-white p-6 shadow-xs">
            <div className="flex items-center gap-2.5">
              <HelpCircle size={17} className="text-[#9F7C38]" />
              <h2 className="text-base font-semibold text-[#303640]">
                Troubleshooting
              </h2>
            </div>
            <div className="mt-3.5 space-y-3">
              {TROUBLESHOOTING_ITEMS.map((item) => (
                <div
                  key={item.title}
                  className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-3"
                >
                  <p className="text-xs font-semibold text-[#303640]">
                    {item.title}
                  </p>
                  <p className="mt-1 text-[11px] leading-relaxed text-[#5E6572]">
                    {item.fix}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* PERMISSIONS EXPLAINED */}
      <section className="rounded-2xl border border-[#303640]/[0.09] bg-white p-6 shadow-xs">
        <div className="flex items-center gap-2.5">
          <MapPin size={18} className="text-[#9F7C38]" />
          <div>
            <h2 className="text-base font-semibold text-[#303640]">
              Why WiFiVision Requests Wi-Fi &amp; Location Permissions
            </h2>
            <p className="text-xs text-[#5E6572]">
              Local-first scanning means permissions are used solely on your
              device when you trigger scans. Auto-upload remains OFF by default.
            </p>
          </div>
        </div>

        <div className="mt-4 grid grid-cols-1 gap-3.5 md:grid-cols-3">
          {PERMISSIONS.map((perm) => (
            <div
              key={perm.name}
              className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4"
            >
              <div className="flex items-center gap-2">
                <Wifi size={14} className="text-[#9F7C38]" />
                <h3 className="text-xs font-semibold text-[#303640]">
                  {perm.name}
                </h3>
              </div>
              <p className="mt-1.5 font-mono text-[10px] text-[#9F7C38]">
                {perm.androidPerms}
              </p>
              <p className="mt-2 text-xs leading-relaxed text-[#5E6572]">
                {perm.purpose}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* WINDOWS COMPANION SETUP (HONEST DOCUMENTATION) */}
      <section
        id="windows-companion"
        className="rounded-2xl border border-[#303640]/[0.09] bg-white p-6 shadow-xs"
      >
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#F1EFE9] text-[#9F7C38]">
              <Laptop size={20} />
            </div>
            <div>
              <h2 className="text-base font-semibold text-[#303640]">
                Windows Laptop Scanning — Windows Companion Setup
              </h2>
              <p className="mt-1 max-w-2xl text-xs leading-relaxed text-[#5E6572]">
                Because web browsers block direct access to 802.11 BSSID and
                RSSI hardware APIs, Windows laptop scanning runs through the
                native C# .NET Framework 4.8 companion app (
                <code>windows/WiFiVision.Windows.csproj</code>) exposing a
                hardened local loopback bridge on{" "}
                <code>http://127.0.0.1:48271</code>.{" "}
                <strong className="text-[#303640]">
                  No hosted Windows installer is distributed directly
                </strong>{" "}
                — build and run <code>WiFiVisionCompanion.exe</code> from the
                repository source below.
              </p>
            </div>
          </div>

          <span className="inline-flex items-center gap-1.5 rounded-full border border-[#303640]/15 bg-[#F7F6F2] px-3 py-1 text-[11px] font-semibold text-[#303640]">
            <Terminal size={13} className="text-[#9F7C38]" />
            Build From windows/ Source (v1.1.0)
          </span>
        </div>

        <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-3">
          <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
            <p className="text-[11px] font-bold uppercase tracking-wider text-[#9F7C38]">
              1. Build With Built-In MSBuild
            </p>
            <p className="mt-2 text-xs text-[#5E6572]">
              In PowerShell inside the <code>windows/</code> directory:
            </p>
            <pre className="mt-2 overflow-x-auto rounded-lg border border-[#303640]/10 bg-white p-2.5 font-mono text-[10px] leading-relaxed text-[#303640]">
{`C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\MSBuild.exe WiFiVision.Windows.csproj /p:Configuration=Release /p:Platform=x64`}
            </pre>
          </div>

          <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
            <p className="text-[11px] font-bold uppercase tracking-wider text-[#9F7C38]">
              2. Launch &amp; Pair Device
            </p>
            <p className="mt-2 text-xs leading-relaxed text-[#5E6572]">
              Run <code>.\bin\Release\WiFiVisionCompanion.exe</code>. It uses
              the production backend (
              <code>{ANDROID_RELEASE.productionBackendUrl}</code>) by default and
              displays a one-time <strong>Session Local API Token</strong>. To
              enable uploads, generate a <strong>Windows</strong> pairing code in{" "}
              <Link href="/settings" className="font-semibold text-[#303640] underline">
                Settings
              </Link>{" "}
              (stored with Windows DPAPI).
            </p>
          </div>

          <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
            <p className="text-[11px] font-bold uppercase tracking-wider text-[#9F7C38]">
              3. Connect in Dashboard
            </p>
            <p className="mt-2 text-xs leading-relaxed text-[#5E6572]">
              Sign in and open{" "}
              <Link href="/networks" className="font-semibold text-[#303640] underline">
                Network Explorer (/networks)
              </Link>{" "}
              or Overview (<code>/</code>). Paste the Local API Token (kept in
              memory only), run <strong>Start Scan</strong> or{" "}
              <strong>Start Monitoring</strong>, then confirm{" "}
              <strong>UPLOAD NOW</strong> to sync cloud observations and refresh
              your heatmap.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
