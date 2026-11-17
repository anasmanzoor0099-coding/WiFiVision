"use client";

import { useState } from "react";
import {
  Activity,
  AlertCircle,
  CheckCircle2,
  Eye,
  EyeOff,
  Laptop,
  Play,
  Radio,
  RefreshCw,
  Shield,
  Square,
  Upload,
  Wifi,
} from "lucide-react";

import {
  checkCompanionHealth,
  confirmCompanionUpload,
  fetchCompanionStatus,
  fetchCompanionUploadPreview,
  startCompanionMonitor,
  stopCompanionMonitor,
  triggerCompanionScan,
  type WindowsCompanionHealth,
  type WindowsCompanionStatus,
  type WindowsUploadPreview,
} from "@/lib/windowsCompanion";

export default function WindowsCompanionCard() {
  // Local API session token lives strictly in component memory — never written to Web Storage.
  const [localToken, setLocalToken] = useState("");
  const [showToken, setShowToken] = useState(false);

  const [health, setHealth] = useState<WindowsCompanionHealth | null>(null);
  const [status, setStatus] = useState<WindowsCompanionStatus | null>(null);
  const [preview, setPreview] = useState<WindowsUploadPreview | null>(null);

  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  async function handleDetectCompanion() {
    setBusyAction("detect");
    setErrorMsg(null);
    setFeedback(null);
    try {
      const h = await checkCompanionHealth();
      setHealth(h);
      if (localToken.trim()) {
        const s = await fetchCompanionStatus(localToken);
        setStatus(s);
        setFeedback(
          `Connected to local Windows Companion (${s.paired ? `Paired: ${s.deviceName ?? "Windows"}` : "Not paired yet"}).`
        );
      } else {
        setFeedback(
          "Windows Companion detected on http://127.0.0.1:48271. Paste its Local API Token below to unlock live scanning."
        );
      }
    } catch (err) {
      setHealth(null);
      setStatus(null);
      setErrorMsg(
        err instanceof Error ? err.message : "Windows Companion not detected."
      );
    } finally {
      setBusyAction(null);
    }
  }

  async function handleConnectStatus() {
    if (!localToken.trim()) {
      setErrorMsg(
        "Paste the session Local API Token shown inside WiFiVisionCompanion.exe."
      );
      return;
    }
    setBusyAction("connect");
    setErrorMsg(null);
    setFeedback(null);
    try {
      const s = await fetchCompanionStatus(localToken);
      setStatus(s);
      setPreview(null);
      setFeedback(
        `Local bridge authorized for ${s.adapter?.description ?? "Windows Wi-Fi adapter"}.`
      );
    } catch (err) {
      setErrorMsg(
        err instanceof Error ? err.message : "Failed to authorize local bridge."
      );
    } finally {
      setBusyAction(null);
    }
  }

  async function handleScanOnly() {
    setBusyAction("scan");
    setErrorMsg(null);
    setFeedback(null);
    try {
      const res = await triggerCompanionScan(localToken);
      setStatus(res.status);
      setPreview(null);
      setFeedback(
        res.outcome.success
          ? `SCAN ONLY complete: ${res.outcome.networks.length} nearby Wi-Fi network(s) captured locally. Nothing uploaded.`
          : res.outcome.statusMessage
      );
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Local scan failed.");
    } finally {
      setBusyAction(null);
    }
  }

  async function handleToggleMonitor() {
    if (!status) return;
    setBusyAction("monitor");
    setErrorMsg(null);
    setFeedback(null);
    try {
      const updated = status.monitorActive
        ? await stopCompanionMonitor(localToken)
        : await startCompanionMonitor(localToken);
      setStatus(updated);
      setFeedback(updated.statusBanner);
    } catch (err) {
      setErrorMsg(
        err instanceof Error ? err.message : "Failed to toggle monitoring."
      );
    } finally {
      setBusyAction(null);
    }
  }

  async function handleLoadPreview() {
    setBusyAction("preview");
    setErrorMsg(null);
    setFeedback(null);
    try {
      const nextPreview = await fetchCompanionUploadPreview(localToken);
      setPreview(nextPreview);
      if (nextPreview.totalReadyCount === 0) {
        setFeedback("No scanned or queued Windows observations ready yet. Run SCAN ONLY first.");
      }
    } catch (err) {
      setErrorMsg(
        err instanceof Error ? err.message : "Unable to load upload preview."
      );
    } finally {
      setBusyAction(null);
    }
  }

  async function handleConfirmUploadNow() {
    if (!preview || preview.totalReadyCount <= 0) return;
    setBusyAction("upload");
    setErrorMsg(null);
    setFeedback(null);
    try {
      const result = await confirmCompanionUpload(
        localToken,
        preview.totalReadyCount
      );
      setStatus(result.status);
      setPreview(null);
      setFeedback(result.message);
    } catch (err) {
      setErrorMsg(
        err instanceof Error ? err.message : "Observation upload failed."
      );
    } finally {
      setBusyAction(null);
    }
  }

  const networks = status?.lastScan?.networks ?? [];
  const tokenPresent = localToken.trim().length > 0;

  return (
    <section className="rounded-2xl border border-[#303640]/10 bg-white p-6 shadow-xs">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#F1EFE9] text-[#9F7C38]">
            <Laptop className="h-5 w-5" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-[#303640]">
              Windows Native Wi-Fi Companion
            </h2>
            <p className="text-xs text-[#5E6572]">
              Connect to the loopback companion agent on{" "}
              <code className="rounded bg-[#F7F6F2] px-1 py-0.5 text-[#303640]">
                http://127.0.0.1:48271
              </code>{" "}
              for genuine Windows laptop 802.11 scanning with manual upload
              confirmation.
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => void handleDetectCompanion()}
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
      </div>

      {/* Connection & Per-Launch Token Bar */}
      <div className="mt-5 grid grid-cols-1 gap-3 rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4 md:grid-cols-[1fr_auto]">
        <div>
          <label
            htmlFor="windows-local-token"
            className="block text-[11px] font-semibold uppercase tracking-wider text-[#9F7C38]"
          >
            Session Local API Token (X-WiFiVision-Local-Token)
          </label>
          <div className="mt-1.5 flex items-center gap-2">
            <input
              id="windows-local-token"
              type={showToken ? "text" : "password"}
              value={localToken}
              onChange={(e) => setLocalToken(e.target.value)}
              placeholder="Paste token from WiFiVisionCompanion.exe (kept in memory only)"
              autoComplete="off"
              className="w-full rounded-lg border border-[#303640]/15 bg-white px-3 py-2 text-xs font-mono text-[#303640] focus:border-[#C6A15B] focus:outline-none"
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
            onClick={() => void handleConnectStatus()}
            disabled={!tokenPresent || busyAction !== null}
            className="inline-flex w-full items-center justify-center gap-1.5 rounded-xl bg-[#303640] px-4 py-2 text-xs font-semibold text-white transition hover:bg-[#3E4654] disabled:opacity-50 md:w-auto"
          >
            <Shield size={13} className="text-[#C6A15B]" />
            {busyAction === "connect" ? "Linking..." : "Link Local Bridge"}
          </button>
        </div>
      </div>

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
          className="mt-4 flex items-start gap-2 rounded-xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.06] px-3.5 py-2.5 text-xs font-medium text-[#B83B3B]"
        >
          <AlertCircle size={15} className="mt-0.5 shrink-0" />
          <div>
            <p>{errorMsg}</p>
            <p className="mt-1 text-[11px] font-normal text-[#5E6572]">
              Start the desktop binary on this Windows laptop via{" "}
              <code className="rounded bg-white px-1 py-0.5 text-[#303640]">
                windows/WiFiVisionCompanion.exe
              </code>
              , copy its one-time session Local API Token, and click{" "}
              <strong>Link Local Bridge</strong>.
            </p>
          </div>
        </div>
      )}

      {/* Offline Guidance */}
      {!status && !health && !errorMsg && (
        <div className="mt-4 rounded-xl border border-dashed border-[#303640]/15 bg-[#F7F6F2] p-4 text-xs text-[#5E6572]">
          <p className="font-semibold text-[#303640]">
            Windows Companion disconnected or not checked yet
          </p>
          <p className="mt-1 leading-relaxed">
            Browsers cannot directly access nearby Wi-Fi BSSID/RSSI hardware for
            security reasons. Launch{" "}
            <code className="rounded bg-white px-1 py-0.5 text-[#303640]">
              WiFiVisionCompanion.exe
            </code>{" "}
            from <code className="rounded bg-white px-1 py-0.5 text-[#303640]">windows/</code>{" "}
            using MSBuild (.NET Framework 4.8), pair it with a{" "}
            <strong>Windows</strong> pairing code above, then click{" "}
            <strong>Detect Companion</strong>.
          </p>
        </div>
      )}

      {/* Active Controls & Metrics */}
      {status && (
        <div className="mt-5 space-y-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-3.5">
              <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#9F7C38]">
                Local Scan Results
              </p>
              <p className="mt-1 text-2xl font-bold tabular-nums text-[#303640]">
                {networks.length}
              </p>
              <p className="text-[11px] text-[#5E6572]">
                {status.adapter?.description ?? "WLAN adapter"} (
                {status.adapter?.state ?? "Unknown"})
              </p>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-3.5">
              <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#9F7C38]">
                Queued (Unsent)
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
                Uploaded Count
              </p>
              <p className="mt-1 text-2xl font-bold tabular-nums text-[#303640]">
                {status.uploadedCount}
              </p>
              <p className="text-[11px] text-[#5E6572]">
                {status.paired
                  ? `Paired as ${status.deviceName ?? "Windows"}`
                  : "Unpaired — pair above before uploading"}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <button
              type="button"
              onClick={() => void handleScanOnly()}
              disabled={busyAction !== null}
              className="inline-flex items-center gap-1.5 rounded-xl bg-[#303640] px-4 py-2 text-xs font-semibold text-white transition hover:bg-[#3E4654] disabled:opacity-50"
            >
              <Radio size={13} className="text-[#C6A15B]" />
              {busyAction === "scan" ? "Scanning WLAN..." : "SCAN ONLY"}
            </button>

            <button
              type="button"
              onClick={() => void handleToggleMonitor()}
              disabled={busyAction !== null}
              className="inline-flex items-center gap-1.5 rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-3.5 py-2 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#F1EFE9] disabled:opacity-50"
            >
              {status.monitorActive ? (
                <>
                  <Square size={12} className="text-[#B83B3B]" />
                  STOP MONITOR
                </>
              ) : (
                <>
                  <Play size={12} className="text-[#2E7D5B]" />
                  START MONITOR
                </>
              )}
            </button>

            <button
              type="button"
              onClick={() => void handleLoadPreview()}
              disabled={busyAction !== null}
              className="inline-flex items-center gap-1.5 rounded-xl border border-[#C6A15B]/55 bg-[#FBF9F4] px-3.5 py-2 text-xs font-semibold text-[#303640] transition hover:bg-[#F1EFE9] disabled:opacity-50"
            >
              <Activity size={13} className="text-[#9F7C38]" />
              Preview & Upload
            </button>
          </div>

          {status.lastScan && (
            <p className="text-[11px] text-[#5E6572]">
              Location status: {status.lastScan.locationMessage}
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
                    onClick={() => void handleConfirmUploadNow()}
                    disabled={busyAction === "upload"}
                    className="inline-flex items-center gap-1.5 rounded-xl bg-[#C6A15B] px-4 py-2 text-xs font-semibold text-[#1E232B] transition hover:bg-[#B48E49] disabled:opacity-50"
                  >
                    <Upload size={13} />
                    {busyAction === "upload" ? "Uploading..." : "UPLOAD NOW"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setPreview(null)}
                    className="rounded-xl border border-[#303640]/15 bg-white px-3 py-2 text-xs font-semibold text-[#303640]"
                  >
                    Cancel
                  </button>
                </div>
              </div>

              {preview.previewLines.length > 0 && (
                <ul className="mt-3 space-y-1 text-[11px] font-mono text-[#303640]">
                  {preview.previewLines.map((line, i) => (
                    <li key={i} className="truncate">
                      • {line}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {/* Live Scanned Networks Table */}
          {networks.length > 0 && (
            <div className="overflow-x-auto rounded-xl border border-[#303640]/[0.08]">
              <table className="w-full text-left text-xs">
                <thead className="bg-[#F7F6F2] text-[10px] font-bold uppercase tracking-wider text-[#5E6572]">
                  <tr>
                    <th className="px-3 py-2.5">SSID</th>
                    <th className="px-3 py-2.5">BSSID</th>
                    <th className="px-3 py-2.5">Signal</th>
                    <th className="px-3 py-2.5">Band / Ch</th>
                    <th className="px-3 py-2.5">Security</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#303640]/[0.06] bg-white">
                  {networks.slice(0, 15).map((net, idx) => (
                    <tr key={`${net.bssid ?? "hidden"}-${idx}`}>
                      <td className="px-3 py-2 font-medium text-[#303640]">
                        <span className="inline-flex items-center gap-1.5">
                          <Wifi size={12} className="text-[#C6A15B]" />
                          {net.displaySsid}
                        </span>
                      </td>
                      <td className="px-3 py-2 font-mono text-[11px] text-[#5E6572]">
                        {net.bssid ?? "—"}
                      </td>
                      <td className="px-3 py-2 tabular-nums text-[#303640]">
                        {net.signalDbm} dBm
                      </td>
                      <td className="px-3 py-2 text-[#5E6572]">
                        {net.band}
                        {net.channel ? ` (ch ${net.channel})` : ""}
                      </td>
                      <td className="px-3 py-2 text-[#5E6572]">
                        {net.security}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
