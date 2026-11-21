"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  Activity,
  AlertCircle,
  ArrowUpRight,
  CheckCircle2,
  Eye,
  EyeOff,
  Laptop,
  Play,
  Radio,
  RefreshCw,
  Search,
  Shield,
  Signal,
  Square,
  Unplug,
  Upload,
  Wifi,
} from "lucide-react";

import {
  useWindowsCompanion,
  type CompanionErrorKind,
} from "@/components/companion/WindowsCompanionProvider";

type BandFilter = "all" | "2.4 GHz" | "5 GHz" | "6 GHz";

interface WindowsCompanionCardProps {
  variant?: "full" | "explorer" | "compact";
}

function getSignalQualityBadge(signalQuality: number): {
  label: string;
  className: string;
} {
  switch (signalQuality) {
    case 4:
      return {
        label: "Excellent",
        className: "border-[#2E7D5B]/30 bg-[#2E7D5B]/10 text-[#2E7D5B]",
      };
    case 3:
      return {
        label: "Good",
        className: "border-lime-600/25 bg-lime-600/10 text-lime-700",
      };
    case 2:
      return {
        label: "Fair",
        className: "border-[#C6A15B]/45 bg-[#C6A15B]/15 text-[#9F7C38]",
      };
    case 1:
      return {
        label: "Weak",
        className: "border-[#B83B3B]/30 bg-[#B83B3B]/10 text-[#B83B3B]",
      };
    default:
      return {
        label: "Minimal",
        className: "border-[#303640]/15 bg-[#F1EFE9] text-[#5E6572]",
      };
  }
}

function formatScanTime(epochMs?: number | null): string {
  if (!epochMs || !Number.isFinite(epochMs)) return "No scan run yet";
  const date = new Date(epochMs);
  if (Number.isNaN(date.getTime())) return "No scan run yet";
  return date.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function getErrorGuidance(kind: CompanionErrorKind): string {
  switch (kind) {
    case "offline":
      return "Start WiFiVisionCompanion.exe on this Windows laptop (from windows/bin/Release/WiFiVisionCompanion.exe) and verify it listens on http://127.0.0.1:48271. If your browser prompts to allow Private Network Access on HTTPS, click Allow.";
    case "unauthorized":
      return "Copy the current one-time Session Local API Token displayed inside the WiFiVision Windows Companion window and click Link Local Bridge.";
    case "permission-denied":
      return "Ensure Windows Location Services are enabled for desktop apps, Wi-Fi is turned ON, and WLAN AutoConfig (WlanSvc) is running without administrator blocks.";
    case "scan-failed":
      return "Verify your laptop has an active Wi-Fi adapter powered on in Windows Settings, then click Start Scan again.";
    case "rate-limited":
      return "The local companion bridge enforces a 30 requests/minute safety rate limit. Wait a few seconds before retrying.";
    default:
      return "Confirm WiFiVisionCompanion.exe is running locally on http://127.0.0.1:48271 and paste its active session Local API Token.";
  }
}

export default function WindowsCompanionCard({
  variant = "full",
}: WindowsCompanionCardProps) {
  const {
    localToken,
    setLocalToken,
    isLinked,
    health,
    status,
    preview,
    hasChecked,
    busyAction,
    feedback,
    errorMsg,
    errorKind,
    detectCompanion,
    ensureDetected,
    linkLocalBridge,
    disconnectBridge,
    scanOnly,
    startMonitoring,
    stopMonitoring,
    loadUploadPreview,
    confirmUploadNow,
    clearPreview,
  } = useWindowsCompanion();

  const [showToken, setShowToken] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [bandFilter, setBandFilter] = useState<BandFilter>("all");
  const [showAllNetworks, setShowAllNetworks] = useState(true);

  useEffect(() => {
    ensureDetected();
  }, [ensureDetected]);

  const networks = useMemo(
    () => status?.lastScan?.networks ?? [],
    [status?.lastScan?.networks]
  );

  const filteredNetworks = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    return networks.filter((net) => {
      if (bandFilter !== "all" && net.band !== bandFilter) {
        return false;
      }
      if (!query) return true;
      return (
        net.displaySsid.toLowerCase().includes(query) ||
        (net.bssid ?? "").toLowerCase().includes(query) ||
        net.band.toLowerCase().includes(query) ||
        net.security.toLowerCase().includes(query) ||
        (net.channel ? String(net.channel).includes(query) : false)
      );
    });
  }, [bandFilter, networks, searchQuery]);

  const visibleNetworks = useMemo(() => {
    if (showAllNetworks) return filteredNetworks;
    return filteredNetworks.slice(0, 20);
  }, [filteredNetworks, showAllNetworks]);

  const tokenPresent = localToken.trim().length > 0;
  const companionOnline = Boolean(health?.ok || status);

  const connectionBadge = isLinked
    ? {
        label: `Connected · v${health?.version ?? "1.1.0"}`,
        className: "border-[#2E7D5B]/30 bg-[#2E7D5B]/10 text-[#2E7D5B]",
        dotClass: "bg-[#2E7D5B]",
      }
    : companionOnline
      ? {
          label: `Detected · v${health?.version ?? "1.1.0"} (Token needed)`,
          className: "border-[#C6A15B]/45 bg-[#C6A15B]/15 text-[#9F7C38]",
          dotClass: "bg-[#C6A15B]",
        }
      : {
          label: hasChecked ? "Offline / Not detected" : "Checking local bridge...",
          className: "border-[#303640]/15 bg-[#F1EFE9] text-[#5E6572]",
          dotClass: "bg-[#868D98]",
        };

  if (variant === "compact") {
    return (
      <section
        aria-label="Laptop Wi-Fi scanning status"
        className="rounded-2xl border border-[#303640]/[0.09] bg-white p-4 shadow-[0_2px_12px_rgba(48,54,64,0.04)] sm:p-5"
      >
        <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-center">
          <div className="flex min-w-0 items-start gap-3.5">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#F1EFE9] text-[#9F7C38]">
              <Laptop size={18} />
            </div>

            <div className="min-w-0 space-y-1">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-[13px] font-semibold text-[#303640]">
                  Laptop Wi-Fi Scanning
                </h2>
                <span
                  className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[10px] font-semibold ${connectionBadge.className}`}
                >
                  <span
                    className={`h-1.5 w-1.5 rounded-full ${connectionBadge.dotClass}`}
                  />
                  {connectionBadge.label}
                </span>
                {status && (
                  <span
                    className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-semibold ${
                      status.monitorActive
                        ? "border-[#2E7D5B]/30 bg-[#2E7D5B]/10 text-[#2E7D5B]"
                        : "border-[#303640]/12 bg-[#F7F6F2] text-[#5E6572]"
                    }`}
                  >
                    {status.monitorActive ? "Monitoring Active" : "Monitor Idle"}
                  </span>
                )}
              </div>

              <p className="text-[11px] leading-relaxed text-[#5E6572]">
                {status
                  ? `${networks.length} local network${networks.length === 1 ? "" : "s"} scanned (${formatScanTime(status.lastScan?.scannedAtEpochMs)}) · ${status.queuedCount} queued (local only until confirmed)`
                  : companionOnline
                    ? "Windows Companion detected on 127.0.0.1:48271. Paste your session token to start laptop scanning."
                    : "Connect WiFiVisionCompanion.exe on Windows (127.0.0.1:48271) to scan nearby Wi-Fi networks directly from this laptop."}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {isLinked && status ? (
              <>
                <button
                  type="button"
                  onClick={() => void scanOnly()}
                  disabled={busyAction !== null}
                  className="inline-flex h-9 items-center gap-1.5 rounded-xl bg-[#303640] px-3.5 text-xs font-semibold text-white transition hover:bg-[#3F4754] disabled:opacity-50"
                >
                  <Radio size={13} className="text-[#C6A15B]" />
                  {busyAction === "scan" ? "Scanning..." : "Start Scan"}
                </button>

                <button
                  type="button"
                  onClick={() =>
                    void (status.monitorActive
                      ? stopMonitoring()
                      : startMonitoring())
                  }
                  disabled={busyAction !== null}
                  className="inline-flex h-9 items-center gap-1.5 rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-3 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] disabled:opacity-50"
                >
                  {status.monitorActive ? (
                    <>
                      <Square size={12} className="text-[#B83B3B]" />
                      Stop Monitoring
                    </>
                  ) : (
                    <>
                      <Play size={12} className="text-[#2E7D5B]" />
                      Start Monitoring
                    </>
                  )}
                </button>

                <button
                  type="button"
                  onClick={() => void loadUploadPreview()}
                  disabled={busyAction !== null}
                  className="inline-flex h-9 items-center gap-1.5 rounded-xl border border-[#C6A15B]/50 bg-[#FBF9F4] px-3 text-xs font-semibold text-[#303640] transition hover:bg-[#F1EFE9] disabled:opacity-50"
                >
                  <Upload size={12} className="text-[#9F7C38]" />
                  Upload ({status.queuedCount + networks.length > 0 ? `${status.queuedCount}q` : "0"})
                </button>
              </>
            ) : (
              <button
                type="button"
                onClick={() => void detectCompanion()}
                disabled={busyAction !== null}
                className="inline-flex h-9 items-center gap-1.5 rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-3 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] disabled:opacity-50"
              >
                <RefreshCw
                  size={12}
                  className={
                    busyAction === "detect"
                      ? "animate-spin text-[#9F7C38]"
                      : "text-[#9F7C38]"
                  }
                />
                {busyAction === "detect" ? "Checking..." : "Detect Companion"}
              </button>
            )}

            <Link
              href="/networks#laptop-scanner"
              className="inline-flex h-9 items-center gap-1.5 rounded-xl border border-[#C6A15B]/40 bg-[#C6A15B]/12 px-3 text-xs font-semibold text-[#303640] transition hover:bg-[#C6A15B]/20"
            >
              Open Live Scanner
              <ArrowUpRight size={13} className="text-[#9F7C38]" />
            </Link>
          </div>
        </div>

        {/* Inline quick token link when detected but not yet linked */}
        {!isLinked && companionOnline && (
          <div className="mt-3.5 flex flex-col gap-2 rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-3 sm:flex-row sm:items-center">
            <input
              type={showToken ? "text" : "password"}
              value={localToken}
              onChange={(e) => setLocalToken(e.target.value)}
              placeholder="Paste Session Local API Token from WiFiVisionCompanion.exe (in-memory only)"
              autoComplete="off"
              className="h-9 flex-1 rounded-lg border border-[#303640]/15 bg-white px-3 font-mono text-xs text-[#303640] focus:border-[#C6A15B] focus:outline-none"
            />
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setShowToken((prev) => !prev)}
                aria-label={showToken ? "Hide local token" : "Show local token"}
                className="inline-flex h-9 items-center justify-center rounded-lg border border-[#303640]/15 bg-white px-2.5 text-[#5E6572] hover:text-[#303640]"
              >
                {showToken ? <EyeOff size={14} /> : <Eye size={14} />}
              </button>
              <button
                type="button"
                onClick={() => void linkLocalBridge()}
                disabled={!tokenPresent || busyAction !== null}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-[#303640] px-3.5 text-xs font-semibold text-white transition hover:bg-[#3F4754] disabled:opacity-50"
              >
                <Shield size={13} className="text-[#C6A15B]" />
                {busyAction === "link" ? "Linking..." : "Link Bridge"}
              </button>
            </div>
          </div>
        )}

        {feedback && (
          <div
            role="status"
            className="mt-3 flex items-center gap-2 rounded-xl border border-[#2E7D5B]/25 bg-[#2E7D5B]/[0.07] px-3 py-2 text-[11px] font-medium text-[#2E7D5B]"
          >
            <CheckCircle2 size={14} className="shrink-0" />
            <span>{feedback}</span>
          </div>
        )}

        {errorMsg && (
          <div
            role="alert"
            className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.06] px-3 py-2 text-[11px] text-[#B83B3B]"
          >
            <span className="flex items-center gap-2 font-medium">
              <AlertCircle size={14} className="shrink-0" />
              {errorMsg}
            </span>
            <Link
              href="/download#windows-companion"
              className="font-semibold text-[#303640] underline hover:text-[#9F7C38]"
            >
              Windows Companion Setup
            </Link>
          </div>
        )}

        {preview && preview.totalReadyCount > 0 && (
          <div className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#C6A15B]/55 bg-[#FBF9F4] px-3.5 py-3">
            <div className="text-xs text-[#303640]">
              <span className="font-semibold text-[#9F7C38]">
                Confirm upload:
              </span>{" "}
              {preview.totalReadyCount} local observation
              {preview.totalReadyCount === 1 ? "" : "s"} ready (
              {preview.hasLocation
                ? "GPS attached"
                : "No GPS — uploads null location"}
              ).
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => void confirmUploadNow()}
                disabled={busyAction === "upload"}
                className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-[#C6A15B] px-3 text-xs font-semibold text-[#1E232B] hover:bg-[#B48E49] disabled:opacity-50"
              >
                <Upload size={12} />
                {busyAction === "upload" ? "Uploading..." : "UPLOAD NOW"}
              </button>
              <button
                type="button"
                onClick={clearPreview}
                className="h-8 rounded-lg border border-[#303640]/15 bg-white px-2.5 text-xs font-semibold text-[#303640]"
              >
                Cancel
              </button>
            </div>
          </div>
        )}
      </section>
    );
  }

  const isExplorerVariant = variant === "explorer";

  return (
    <section
      className="rounded-2xl border border-[#303640]/10 bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)] sm:p-6"
      aria-label="Live laptop Wi-Fi scanner"
    >
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#F1EFE9] text-[#9F7C38]">
            <Laptop className="h-5 w-5" />
          </div>
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-base font-semibold text-[#303640]">
                {isExplorerVariant
                  ? "Live Laptop Wi-Fi Scanner"
                  : "Windows Native Wi-Fi Companion"}
              </h2>
              <span
                className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[10px] font-semibold ${connectionBadge.className}`}
              >
                <span
                  className={`h-1.5 w-1.5 rounded-full ${connectionBadge.dotClass}`}
                />
                {connectionBadge.label}
              </span>
              <span className="inline-flex items-center rounded-full border border-[#303640]/15 bg-[#F7F6F2] px-2.5 py-0.5 text-[10px] font-semibold text-[#5E6572]">
                Local Only · Not Cloud Data
              </span>
            </div>
            <p className="text-xs text-[#5E6572]">
              Secure loopback bridge at{" "}
              <code className="rounded bg-[#F7F6F2] px-1 py-0.5 text-[#303640]">
                http://127.0.0.1:48271
              </code>{" "}
              (origin-checked, rate-limited, per-launch session token).
              Local scans stay on your laptop until you confirm{" "}
              <strong>UPLOAD NOW</strong>.
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => void detectCompanion()}
            disabled={busyAction !== null}
            className="inline-flex items-center gap-1.5 rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-3.5 py-2 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#F1EFE9] disabled:opacity-50"
          >
            <RefreshCw
              size={13}
              className={
                busyAction === "detect"
                  ? "animate-spin text-[#9F7C38]"
                  : "text-[#9F7C38]"
              }
            />
            {busyAction === "detect" ? "Checking..." : "Detect Companion"}
          </button>

          {isLinked && (
            <button
              type="button"
              onClick={disconnectBridge}
              disabled={busyAction !== null}
              className="inline-flex items-center gap-1.5 rounded-xl border border-[#303640]/15 bg-white px-3 py-2 text-xs font-semibold text-[#5E6572] transition hover:border-[#B83B3B]/40 hover:text-[#B83B3B] disabled:opacity-50"
            >
              <Unplug size={13} />
              Disconnect
            </button>
          )}
        </div>
      </div>

      {/* In-Memory Local API Token Bar */}
      <div className="mt-5 grid grid-cols-1 gap-3 rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4 md:grid-cols-[1fr_auto]">
        <div>
          <label
            htmlFor={`windows-local-token-${variant}`}
            className="block text-[11px] font-semibold uppercase tracking-wider text-[#9F7C38]"
          >
            Session Local API Token (X-WiFiVision-Local-Token)
          </label>
          <div className="mt-1.5 flex items-center gap-2">
            <input
              id={`windows-local-token-${variant}`}
              type={showToken ? "text" : "password"}
              value={localToken}
              onChange={(e) => setLocalToken(e.target.value)}
              placeholder="Paste token from WiFiVisionCompanion.exe (stored in memory across dashboard navigation)"
              autoComplete="off"
              className="w-full rounded-lg border border-[#303640]/15 bg-white px-3 py-2 font-mono text-xs text-[#303640] focus:border-[#C6A15B] focus:outline-none"
            />
            <button
              type="button"
              onClick={() => setShowToken((prev) => !prev)}
              aria-label={showToken ? "Hide local token" : "Show local token"}
              className="rounded-lg border border-[#303640]/15 bg-white p-2 text-[#5E6572] hover:text-[#303640]"
            >
              {showToken ? <EyeOff size={14} /> : <Eye size={14} />}
            </button>
          </div>
        </div>

        <div className="flex items-end">
          <button
            type="button"
            onClick={() => void linkLocalBridge()}
            disabled={!tokenPresent || busyAction !== null}
            className="inline-flex w-full items-center justify-center gap-1.5 rounded-xl bg-[#303640] px-4 py-2 text-xs font-semibold text-white transition hover:bg-[#3E4654] disabled:opacity-50 md:w-auto"
          >
            <Shield size={13} className="text-[#C6A15B]" />
            {busyAction === "link"
              ? "Linking..."
              : isLinked
                ? "Re-Verify Bridge"
                : "Link Local Bridge"}
          </button>
        </div>
      </div>

      {/* Feedback & Contextual Error Alerts */}
      {feedback && (
        <div
          role="status"
          className="mt-4 flex items-center gap-2 rounded-xl border border-[#2E7D5B]/25 bg-[#2E7D5B]/[0.07] px-3.5 py-2.5 text-xs font-medium text-[#2E7D5B]"
        >
          <CheckCircle2 size={15} className="shrink-0" />
          <span>{feedback}</span>
        </div>
      )}

      {errorMsg && (
        <div
          role="alert"
          className="mt-4 flex items-start gap-2.5 rounded-xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.06] px-3.5 py-3 text-xs text-[#B83B3B]"
        >
          <AlertCircle size={16} className="mt-0.5 shrink-0" />
          <div className="space-y-1">
            <p className="font-semibold">{errorMsg}</p>
            <p className="text-[11px] leading-relaxed text-[#5E6572]">
              {getErrorGuidance(errorKind)}{" "}
              <Link
                href="/download#windows-companion"
                className="font-semibold text-[#303640] underline hover:text-[#9F7C38]"
              >
                View Windows Companion setup guide
              </Link>
              {!isExplorerVariant ? null : (
                <>
                  {" · "}
                  <Link
                    href="/settings"
                    className="font-semibold text-[#303640] underline hover:text-[#9F7C38]"
                  >
                    Manage Pairing in Settings
                  </Link>
                </>
              )}
            </p>
          </div>
        </div>
      )}

      {/* Offline / Not Linked Guidance */}
      {!status && !errorMsg && (
        <div className="mt-4 rounded-xl border border-dashed border-[#303640]/15 bg-[#F7F6F2] p-4 text-xs text-[#5E6572]">
          <p className="font-semibold text-[#303640]">
            {companionOnline
              ? "Windows Companion detected — enter its session Local API Token above"
              : "Windows Companion not connected yet"}
          </p>
          <p className="mt-1 leading-relaxed">
            Web browsers restrict raw 802.11 BSSID/RSSI hardware access. Run{" "}
            <code className="rounded bg-white px-1 py-0.5 text-[#303640]">
              WiFiVisionCompanion.exe
            </code>{" "}
            on your Windows laptop (built from{" "}
            <code className="rounded bg-white px-1 py-0.5 text-[#303640]">
              windows/
            </code>
            ), pair it if you want to upload observations later, and paste its
            per-launch token above.{" "}
            <Link
              href="/download#windows-companion"
              className="font-semibold text-[#303640] underline hover:text-[#9F7C38]"
            >
              Windows Companion build &amp; launch instructions
            </Link>
            .
          </p>
        </div>
      )}

      {/* Active Bridge Status & Scanner Controls */}
      {status && (
        <div className="mt-5 space-y-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-3.5">
              <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#9F7C38]">
                Local Scan Results
              </p>
              <p className="mt-1 text-2xl font-bold tabular-nums text-[#303640]">
                {networks.length}
              </p>
              <p className="truncate text-[11px] text-[#5E6572]">
                {status.adapter?.description ?? "WLAN adapter"} (
                {status.adapter?.state ?? "Unknown"})
              </p>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-3.5">
              <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#9F7C38]">
                Scan &amp; Monitor Status
              </p>
              <p className="mt-1 text-sm font-bold text-[#303640]">
                {status.monitorActive ? "Monitoring Active" : "Manual Scan Mode"}
              </p>
              <p className="mt-1 text-[11px] text-[#5E6572]">
                Last scan: {formatScanTime(status.lastScan?.scannedAtEpochMs)}
              </p>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-3.5">
              <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#9F7C38]">
                Queued Locally
              </p>
              <p className="mt-1 text-2xl font-bold tabular-nums text-[#303640]">
                {status.queuedCount}
              </p>
              <p className="text-[11px] text-[#5E6572]">
                Auto-upload OFF · DPAPI encrypted
              </p>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-3.5">
              <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#9F7C38]">
                Cloud Synced
              </p>
              <p className="mt-1 text-2xl font-bold tabular-nums text-[#303640]">
                {status.uploadedCount}
              </p>
              <p className="truncate text-[11px] text-[#5E6572]">
                {status.paired
                  ? `Paired: ${status.deviceName ?? "Windows"}`
                  : "Unpaired — pair in Settings"}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <button
              type="button"
              onClick={() => void scanOnly()}
              disabled={busyAction !== null}
              className="inline-flex items-center gap-1.5 rounded-xl bg-[#303640] px-4 py-2 text-xs font-semibold text-white transition hover:bg-[#3E4654] disabled:opacity-50"
            >
              <Radio size={13} className="text-[#C6A15B]" />
              {busyAction === "scan" ? "Scanning WLAN..." : "Start Scan"}
            </button>

            {status.monitorActive ? (
              <button
                type="button"
                onClick={() => void stopMonitoring()}
                disabled={busyAction !== null}
                className="inline-flex items-center gap-1.5 rounded-xl border border-[#B83B3B]/35 bg-[#B83B3B]/[0.06] px-3.5 py-2 text-xs font-semibold text-[#B83B3B] transition hover:bg-[#B83B3B]/12 disabled:opacity-50"
              >
                <Square size={12} />
                {busyAction === "monitor" ? "Updating..." : "Stop Monitoring"}
              </button>
            ) : (
              <button
                type="button"
                onClick={() => void startMonitoring()}
                disabled={busyAction !== null}
                className="inline-flex items-center gap-1.5 rounded-xl border border-[#2E7D5B]/35 bg-[#2E7D5B]/[0.07] px-3.5 py-2 text-xs font-semibold text-[#2E7D5B] transition hover:bg-[#2E7D5B]/15 disabled:opacity-50"
              >
                <Play size={12} />
                {busyAction === "monitor" ? "Starting..." : "Start Monitoring"}
              </button>
            )}

            <button
              type="button"
              onClick={() => void loadUploadPreview()}
              disabled={busyAction !== null}
              className="inline-flex items-center gap-1.5 rounded-xl border border-[#C6A15B]/55 bg-[#FBF9F4] px-3.5 py-2 text-xs font-semibold text-[#303640] transition hover:bg-[#F1EFE9] disabled:opacity-50"
            >
              <Activity size={13} className="text-[#9F7C38]" />
              Preview &amp; Upload
            </button>
          </div>

          {status.lastScan && (
            <p className="text-[11px] text-[#5E6572]">
              Location status:{" "}
              <span className="font-medium text-[#303640]">
                {status.lastScan.locationMessage}
              </span>
            </p>
          )}

          {/* Explicit Upload Confirmation Dialog */}
          {preview && preview.totalReadyCount > 0 && (
            <div className="rounded-xl border border-[#C6A15B]/55 bg-[#FBF9F4] p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-bold uppercase tracking-wider text-[#9F7C38]">
                    Explicit Upload Confirmation ({preview.totalReadyCount}{" "}
                    Observation{preview.totalReadyCount === 1 ? "" : "s"})
                  </p>
                  <p className="mt-1 text-xs text-[#5E6572]">
                    Queued: {preview.existingPendingQueueCount} · Latest Scan
                    Ready: {preview.readyLocalCount} · Location:{" "}
                    {preview.hasLocation
                      ? `Attached${preview.locationAccuracyMeters ? ` (±${Math.round(preview.locationAccuracyMeters)} m)` : ""}`
                      : "Unavailable (uploads null location — never guessed)"}
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => void confirmUploadNow()}
                    disabled={busyAction === "upload"}
                    className="inline-flex items-center gap-1.5 rounded-xl bg-[#C6A15B] px-4 py-2 text-xs font-semibold text-[#1E232B] transition hover:bg-[#B48E49] disabled:opacity-50"
                  >
                    <Upload size={13} />
                    {busyAction === "upload" ? "Uploading..." : "UPLOAD NOW"}
                  </button>
                  <button
                    type="button"
                    onClick={clearPreview}
                    className="rounded-xl border border-[#303640]/15 bg-white px-3 py-2 text-xs font-semibold text-[#303640]"
                  >
                    Cancel
                  </button>
                </div>
              </div>

              {preview.previewLines.length > 0 && (
                <ul className="mt-3 space-y-1 font-mono text-[11px] text-[#303640]">
                  {preview.previewLines.map((line, i) => (
                    <li key={i} className="truncate">
                      • {line}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {/* Scanned Networks Toolbar + Full Results Table (No 15-Network Cap) */}
          {networks.length > 0 && (
            <div className="space-y-3 pt-1">
              <div className="flex flex-col gap-2.5 rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-3 lg:flex-row lg:items-center lg:justify-between">
                <div className="relative w-full lg:max-w-xs">
                  <Search
                    size={14}
                    className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#868D98]"
                  />
                  <input
                    type="search"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Filter local scan by SSID, BSSID, channel, security..."
                    className="h-9 w-full rounded-lg border border-[#303640]/15 bg-white pl-8 pr-3 text-xs text-[#303640] placeholder-[#868D98] focus:border-[#C6A15B] focus:outline-none"
                  />
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <div className="flex flex-wrap items-center gap-1 rounded-lg border border-[#303640]/10 bg-white p-1">
                    {(["all", "2.4 GHz", "5 GHz", "6 GHz"] as BandFilter[]).map(
                      (band) => (
                        <button
                          key={band}
                          type="button"
                          aria-pressed={bandFilter === band}
                          onClick={() => setBandFilter(band)}
                          className={`rounded-md px-2.5 py-1 text-[11px] font-semibold transition ${
                            bandFilter === band
                              ? "bg-[#303640] text-white"
                              : "text-[#5E6572] hover:bg-[#ECE9E0] hover:text-[#303640]"
                          }`}
                        >
                          {band === "all" ? "All Bands" : band}
                        </button>
                      )
                    )}
                  </div>

                  <button
                    type="button"
                    aria-pressed={showAllNetworks}
                    onClick={() => setShowAllNetworks((prev) => !prev)}
                    className={`rounded-lg border px-2.5 py-1.5 text-[11px] font-semibold transition ${
                      showAllNetworks
                        ? "border-[#C6A15B]/50 bg-[#C6A15B]/15 text-[#303640]"
                        : "border-[#303640]/15 bg-white text-[#5E6572] hover:border-[#C6A15B] hover:text-[#303640]"
                    }`}
                  >
                    {showAllNetworks
                      ? `Showing all (${filteredNetworks.length})`
                      : `Show all (${filteredNetworks.length})`}
                  </button>
                </div>
              </div>

              {filteredNetworks.length === 0 ? (
                <div className="rounded-xl border border-dashed border-[#303640]/15 bg-[#F7F6F2] p-5 text-center text-xs text-[#5E6572]">
                  No locally scanned networks match &ldquo;{searchQuery || bandFilter}&rdquo;.
                </div>
              ) : (
                <>
                  <div className="flex items-center justify-between text-[11px] text-[#5E6572]">
                    <span>
                      Showing{" "}
                      <strong className="font-semibold text-[#303640]">
                        {visibleNetworks.length}
                      </strong>{" "}
                      of{" "}
                      <strong className="font-semibold text-[#303640]">
                        {filteredNetworks.length}
                      </strong>{" "}
                      matching local network{filteredNetworks.length === 1 ? "" : "s"}
                    </span>
                    <span className="font-medium text-[#9F7C38]">
                      Local hardware scan (not yet uploaded)
                    </span>
                  </div>

                  {/* Desktop Table */}
                  <div className="hidden overflow-x-auto rounded-xl border border-[#303640]/[0.08] md:block">
                    <table className="w-full min-w-[700px] text-left text-xs">
                      <thead className="bg-[#F7F6F2] text-[10px] font-bold uppercase tracking-wider text-[#5E6572]">
                        <tr>
                          <th className="px-3.5 py-2.5">SSID</th>
                          <th className="px-3 py-2.5">BSSID</th>
                          <th className="px-3 py-2.5">Signal Strength</th>
                          <th className="px-3 py-2.5">Frequency / Channel</th>
                          <th className="px-3 py-2.5">Band</th>
                          <th className="px-3.5 py-2.5">Security</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[#303640]/[0.06] bg-white">
                        {visibleNetworks.map((net, idx) => {
                          const quality = getSignalQualityBadge(
                            net.signalQuality
                          );
                          return (
                            <tr
                              key={`${net.bssid ?? "hidden"}-${net.frequencyMhz}-${idx}`}
                              className="transition hover:bg-[#F7F6F2]/75"
                            >
                              <td className="px-3.5 py-2.5 font-medium text-[#303640]">
                                <span className="inline-flex items-center gap-1.5">
                                  <Wifi size={12} className="shrink-0 text-[#C6A15B]" />
                                  <span>{net.displaySsid}</span>
                                  {net.isHiddenSsid && (
                                    <span className="rounded bg-[#F1EFE9] px-1.5 py-0.5 text-[9px] font-semibold text-[#868D98]">
                                      Hidden
                                    </span>
                                  )}
                                </span>
                              </td>
                              <td className="px-3 py-2.5 font-mono text-[11px] text-[#5E6572]">
                                {net.bssid ?? "—"}
                              </td>
                              <td className="px-3 py-2.5">
                                <div className="flex items-center gap-2 tabular-nums">
                                  <span className="font-semibold text-[#303640]">
                                    {net.signalDbm} dBm
                                  </span>
                                  <span
                                    className={`rounded-md border px-1.5 py-0.5 text-[10px] font-semibold ${quality.className}`}
                                  >
                                    {quality.label} ({net.linkQualityPercent}%)
                                  </span>
                                </div>
                              </td>
                              <td className="px-3 py-2.5 tabular-nums text-[#5E6572]">
                                {net.frequencyMhz > 0
                                  ? `${net.frequencyMhz} MHz`
                                  : "—"}
                                {net.channel ? ` · Ch ${net.channel}` : ""}
                              </td>
                              <td className="px-3 py-2.5 font-medium text-[#303640]">
                                {net.band}
                              </td>
                              <td className="px-3.5 py-2.5 text-[#5E6572]">
                                {net.security}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>

                  {/* Mobile Cards */}
                  <div className="divide-y divide-[#303640]/[0.07] rounded-xl border border-[#303640]/[0.08] md:hidden">
                    {visibleNetworks.map((net, idx) => {
                      const quality = getSignalQualityBadge(net.signalQuality);
                      return (
                        <div
                          key={`${net.bssid ?? "hidden"}-${net.frequencyMhz}-${idx}`}
                          className="space-y-2 p-3.5 text-xs"
                        >
                          <div className="flex items-center justify-between gap-2">
                            <span className="inline-flex items-center gap-1.5 font-semibold text-[#303640]">
                              <Wifi size={12} className="text-[#C6A15B]" />
                              {net.displaySsid}
                            </span>
                            <span
                              className={`rounded-md border px-2 py-0.5 text-[10px] font-semibold ${quality.className}`}
                            >
                              {net.signalDbm} dBm · {quality.label}
                            </span>
                          </div>
                          <div className="flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-[#5E6572]">
                            <span className="font-mono">{net.bssid ?? "—"}</span>
                            <span>
                              <Signal size={11} className="mr-1 inline" />
                              {net.linkQualityPercent}%
                            </span>
                            <span>{net.band}</span>
                            <span>
                              {net.frequencyMhz > 0
                                ? `${net.frequencyMhz} MHz`
                                : "—"}
                              {net.channel ? ` · Ch ${net.channel}` : ""}
                            </span>
                            <span>{net.security}</span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      )}
    </section>
  );
}
