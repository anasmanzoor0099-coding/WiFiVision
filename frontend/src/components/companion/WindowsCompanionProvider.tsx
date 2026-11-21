"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";

import {
  checkCompanionHealth,
  confirmCompanionUpload,
  fetchCompanionStatus,
  fetchCompanionUploadPreview,
  formatCompanionOfflineMessage,
  startCompanionMonitor,
  stopCompanionMonitor,
  triggerCompanionScan,
  WindowsCompanionError,
  type WindowsCompanionHealth,
  type WindowsCompanionStatus,
  type WindowsUploadPreview,
} from "@/lib/windowsCompanion";

export type CompanionBusyAction =
  | "detect"
  | "link"
  | "scan"
  | "monitor"
  | "preview"
  | "upload"
  | null;

export type CompanionErrorKind =
  | "offline"
  | "unauthorized"
  | "permission-denied"
  | "scan-failed"
  | "rate-limited"
  | "generic"
  | null;

interface WindowsCompanionContextValue {
  localToken: string;
  setLocalToken: (token: string) => void;
  isLinked: boolean;
  health: WindowsCompanionHealth | null;
  status: WindowsCompanionStatus | null;
  preview: WindowsUploadPreview | null;
  hasChecked: boolean;
  busyAction: CompanionBusyAction;
  feedback: string;
  errorMsg: string;
  errorKind: CompanionErrorKind;
  lastUploadVersion: number;
  lastScanCompletedAt: string | null;
  detectCompanion: () => Promise<void>;
  ensureDetected: () => void;
  linkLocalBridge: (tokenOverride?: string) => Promise<void>;
  disconnectBridge: () => void;
  scanOnly: () => Promise<void>;
  startMonitoring: () => Promise<void>;
  stopMonitoring: () => Promise<void>;
  toggleMonitor: () => Promise<void>;
  loadUploadPreview: () => Promise<void>;
  confirmUploadNow: () => Promise<void>;
  clearPreview: () => void;
  clearMessages: () => void;
}

const WindowsCompanionContext =
  createContext<WindowsCompanionContextValue | null>(null);

const MONITOR_POLL_INTERVAL_MS = 10000;

function classifyCompanionError(err: unknown): {
  kind: Exclude<CompanionErrorKind, null>;
  message: string;
} {
  if (err instanceof WindowsCompanionError) {
    if (err.isOffline || err.status === 0) {
      return { kind: "offline", message: err.message };
    }
    if (err.status === 401 || err.errorCode === "LOCAL_TOKEN_REQUIRED") {
      return { kind: "unauthorized", message: err.message };
    }
    if (err.status === 403) {
      return { kind: "permission-denied", message: err.message };
    }
    if (err.status === 429 || err.errorCode === "RATE_LIMITED") {
      return { kind: "rate-limited", message: err.message };
    }
    if (err.status >= 500) {
      return { kind: "scan-failed", message: err.message };
    }
    return { kind: "generic", message: err.message };
  }

  const message =
    err instanceof Error && err.message.trim()
      ? err.message
      : formatCompanionOfflineMessage();
  const lower = message.toLowerCase();

  if (
    lower.includes("not reachable") ||
    lower.includes("failed to fetch") ||
    lower.includes("127.0.0.1:48271")
  ) {
    return { kind: "offline", message };
  }
  if (lower.includes("token") || lower.includes("unauthorized")) {
    return { kind: "unauthorized", message };
  }
  if (lower.includes("permission") || lower.includes("denied")) {
    return { kind: "permission-denied", message };
  }
  if (lower.includes("too many requests") || lower.includes("rate")) {
    return { kind: "rate-limited", message };
  }
  return { kind: "generic", message };
}

function classifyScanOutcomeError(errorKind?: string): Exclude<CompanionErrorKind, null> {
  if (errorKind === "PermissionDenied") {
    return "permission-denied";
  }
  return "scan-failed";
}

export function WindowsCompanionProvider({
  children,
}: {
  children: ReactNode;
}) {
  // Per-launch Local API Token is stored strictly in React memory (never Web Storage or logs).
  const [localToken, setLocalTokenState] = useState("");
  const [health, setHealth] = useState<WindowsCompanionHealth | null>(null);
  const [status, setStatus] = useState<WindowsCompanionStatus | null>(null);
  const [preview, setPreview] = useState<WindowsUploadPreview | null>(null);
  const [hasChecked, setHasChecked] = useState(false);
  const [busyAction, setBusyAction] = useState<CompanionBusyAction>(null);
  const [feedback, setFeedback] = useState("");
  const [errorMsg, setErrorMsg] = useState("");
  const [errorKind, setErrorKind] = useState<CompanionErrorKind>(null);
  const [lastUploadVersion, setLastUploadVersion] = useState(0);
  const [lastScanCompletedAt, setLastScanCompletedAt] = useState<string | null>(
    null
  );

  const hasCheckedRef = useRef(false);
  const tokenRef = useRef("");

  const setLocalToken = useCallback((token: string) => {
    tokenRef.current = token;
    setLocalTokenState(token);
  }, []);

  const clearPreview = useCallback(() => {
    setPreview(null);
  }, []);

  const clearMessages = useCallback(() => {
    setFeedback("");
    setErrorMsg("");
    setErrorKind(null);
  }, []);

  const applyError = useCallback((err: unknown) => {
    const classified = classifyCompanionError(err);
    setErrorKind(classified.kind);
    setErrorMsg(classified.message);

    if (classified.kind === "offline") {
      setHealth(null);
      setStatus(null);
      setPreview(null);
    } else if (classified.kind === "unauthorized") {
      setStatus(null);
      setPreview(null);
    }
  }, []);

  const detectCompanion = useCallback(async () => {
    hasCheckedRef.current = true;
    setHasChecked(true);
    setBusyAction("detect");
    setErrorMsg("");
    setErrorKind(null);
    setFeedback("");

    try {
      const nextHealth = await checkCompanionHealth();
      setHealth(nextHealth);
      const activeToken = tokenRef.current.trim();
      if (activeToken) {
        try {
          const nextStatus = await fetchCompanionStatus(activeToken);
          setStatus(nextStatus);
          setFeedback(
            `Connected to Windows Companion v${nextHealth.version} (${
              nextStatus.paired
                ? `Paired: ${nextStatus.deviceName ?? "Windows"}`
                : "Not paired yet"
            }).`
          );
        } catch (statusErr) {
          applyError(statusErr);
        }
      } else {
        setFeedback(
          `Windows Companion v${nextHealth.version} detected on http://127.0.0.1:48271. Paste its Local API Token to unlock live laptop scanning.`
        );
      }
    } catch (err) {
      applyError(err);
    } finally {
      setBusyAction(null);
    }
  }, [applyError]);

  const ensureDetected = useCallback(() => {
    if (hasCheckedRef.current) return;
    hasCheckedRef.current = true;
    void Promise.resolve().then(() => detectCompanion());
  }, [detectCompanion]);

  const linkLocalBridge = useCallback(
    async (tokenOverride?: string) => {
      const candidate = (
        tokenOverride !== undefined ? tokenOverride : tokenRef.current
      ).trim();

      if (tokenOverride !== undefined) {
        setLocalToken(tokenOverride);
      }

      if (!candidate) {
        setErrorKind("unauthorized");
        setErrorMsg(
          "Paste the one-time Session Local API Token shown inside WiFiVisionCompanion.exe."
        );
        return;
      }

      setBusyAction("link");
      setErrorMsg("");
      setErrorKind(null);
      setFeedback("");

      try {
        const nextHealth = await checkCompanionHealth();
        setHealth(nextHealth);
        const nextStatus = await fetchCompanionStatus(candidate);
        setStatus(nextStatus);
        setPreview(null);
        setFeedback(
          `Local bridge linked (v${nextHealth.version}) for ${
            nextStatus.adapter?.description ?? "Windows Wi-Fi adapter"
          }. Token kept in memory only.`
        );
      } catch (err) {
        applyError(err);
      } finally {
        setBusyAction(null);
      }
    },
    [applyError, setLocalToken]
  );

  const disconnectBridge = useCallback(() => {
    setLocalToken("");
    setStatus(null);
    setPreview(null);
    setErrorMsg("");
    setErrorKind(null);
    setFeedback(
      "Disconnected local companion bridge from browser memory. Launch WiFiVisionCompanion.exe or paste a session token again whenever needed."
    );
  }, [setLocalToken]);

  const scanOnly = useCallback(async () => {
    const activeToken = tokenRef.current.trim();
    if (!activeToken) {
      setErrorKind("unauthorized");
      setErrorMsg(
        "Link the companion first with the Session Local API Token from WiFiVisionCompanion.exe."
      );
      return;
    }

    setBusyAction("scan");
    setErrorMsg("");
    setErrorKind(null);
    setFeedback("");

    try {
      const res = await triggerCompanionScan(activeToken);
      setStatus(res.status);
      setPreview(null);

      if (res.outcome.success) {
        setLastScanCompletedAt(
          res.outcome.scannedAtEpochMs
            ? new Date(res.outcome.scannedAtEpochMs).toISOString()
            : new Date().toISOString()
        );
        setFeedback(
          `SCAN ONLY complete: ${res.outcome.networks.length} nearby Wi-Fi network(s) captured locally. Nothing uploaded.`
        );
      } else {
        setErrorKind(classifyScanOutcomeError(res.outcome.errorKind));
        setErrorMsg(
          res.outcome.statusMessage ||
            "Windows WLAN scan did not complete. Verify Wi-Fi is enabled and Windows Location Services / WLAN permissions are allowed."
        );
      }
    } catch (err) {
      applyError(err);
    } finally {
      setBusyAction(null);
    }
  }, [applyError]);

  const startMonitoring = useCallback(async () => {
    const activeToken = tokenRef.current.trim();
    if (!activeToken) {
      setErrorKind("unauthorized");
      setErrorMsg("Link the companion bridge first before starting monitoring.");
      return;
    }

    setBusyAction("monitor");
    setErrorMsg("");
    setErrorKind(null);
    setFeedback("");

    try {
      const updated = await startCompanionMonitor(activeToken);
      setStatus(updated);
      setFeedback(
        updated.statusBanner ||
          "Local laptop monitoring started (auto-upload remains OFF)."
      );
    } catch (err) {
      applyError(err);
    } finally {
      setBusyAction(null);
    }
  }, [applyError]);

  const stopMonitoring = useCallback(async () => {
    const activeToken = tokenRef.current.trim();
    if (!activeToken) return;

    setBusyAction("monitor");
    setErrorMsg("");
    setErrorKind(null);
    setFeedback("");

    try {
      const updated = await stopCompanionMonitor(activeToken);
      setStatus(updated);
      setFeedback(updated.statusBanner || "Local laptop monitoring stopped.");
    } catch (err) {
      applyError(err);
    } finally {
      setBusyAction(null);
    }
  }, [applyError]);

  const toggleMonitor = useCallback(async () => {
    if (!status) return;
    if (status.monitorActive) {
      await stopMonitoring();
    } else {
      await startMonitoring();
    }
  }, [startMonitoring, status, stopMonitoring]);

  const loadUploadPreview = useCallback(async () => {
    const activeToken = tokenRef.current.trim();
    if (!activeToken) {
      setErrorKind("unauthorized");
      setErrorMsg("Link the companion bridge first to preview queued uploads.");
      return;
    }

    setBusyAction("preview");
    setErrorMsg("");
    setErrorKind(null);
    setFeedback("");

    try {
      const nextPreview = await fetchCompanionUploadPreview(activeToken);
      setPreview(nextPreview);
      if (nextPreview.totalReadyCount === 0) {
        setFeedback(
          "No scanned or queued local observations ready yet. Run Start Scan first."
        );
      }
    } catch (err) {
      applyError(err);
    } finally {
      setBusyAction(null);
    }
  }, [applyError]);

  const confirmUploadNow = useCallback(async () => {
    const activeToken = tokenRef.current.trim();
    if (!activeToken || !preview || preview.totalReadyCount <= 0) return;

    setBusyAction("upload");
    setErrorMsg("");
    setErrorKind(null);
    setFeedback("");

    try {
      const result = await confirmCompanionUpload(
        activeToken,
        preview.totalReadyCount
      );
      setStatus(result.status);
      setPreview(null);

      if (result.uploadedCount > 0) {
        setLastUploadVersion((v) => v + 1);
      }

      if (result.requiresReparing) {
        setErrorKind("permission-denied");
        setErrorMsg(
          result.message ||
            "Device credentials require re-pairing. Generate a Windows pairing code in Settings."
        );
      } else {
        setFeedback(result.message);
      }
    } catch (err) {
      applyError(err);
    } finally {
      setBusyAction(null);
    }
  }, [applyError, preview]);

  // Keep status & local scans fresh while monitoring is active.
  useEffect(() => {
    if (!status?.monitorActive || !localToken.trim()) return;

    const timer = window.setInterval(() => {
      const activeToken = tokenRef.current.trim();
      if (!activeToken) return;
      void fetchCompanionStatus(activeToken)
        .then((nextStatus) => {
          setStatus(nextStatus);
        })
        .catch((err) => {
          applyError(err);
        });
    }, MONITOR_POLL_INTERVAL_MS);

    return () => window.clearInterval(timer);
  }, [applyError, localToken, status?.monitorActive]);

  const isLinked = Boolean(localToken.trim() && status);

  return (
    <WindowsCompanionContext.Provider
      value={{
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
        lastUploadVersion,
        lastScanCompletedAt,
        detectCompanion,
        ensureDetected,
        linkLocalBridge,
        disconnectBridge,
        scanOnly,
        startMonitoring,
        stopMonitoring,
        toggleMonitor,
        loadUploadPreview,
        confirmUploadNow,
        clearPreview,
        clearMessages,
      }}
    >
      {children}
    </WindowsCompanionContext.Provider>
  );
}

export function useWindowsCompanion(): WindowsCompanionContextValue {
  const ctx = useContext(WindowsCompanionContext);
  if (!ctx) {
    throw new Error(
      "useWindowsCompanion must be used inside a WindowsCompanionProvider."
    );
  }
  return ctx;
}
