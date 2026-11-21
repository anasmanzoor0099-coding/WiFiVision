export const DEFAULT_WINDOWS_COMPANION_PORT = 48271;
export const LOCAL_COMPANION_TOKEN_HEADER = "X-WiFiVision-Local-Token";

export class WindowsCompanionError extends Error {
  readonly status: number;
  readonly errorCode: string;
  readonly isOffline: boolean;

  constructor(
    message: string,
    options: { status?: number; errorCode?: string; isOffline?: boolean } = {}
  ) {
    super(message);
    this.name = "WindowsCompanionError";
    this.status = options.status ?? 0;
    this.errorCode = options.errorCode ?? "UNKNOWN";
    this.isOffline = Boolean(options.isOffline);
  }
}

export interface WindowsCompanionHealth {
  ok: boolean;
  service: string;
  version: string;
  platform: "windows";
  paired: boolean;
  monitorActive: boolean;
  queuedCount: number;
  uploadedCount: number;
}

export interface WindowsScannedNetwork {
  ssid: string | null;
  displaySsid: string;
  isHiddenSsid: boolean;
  bssid: string | null;
  signalDbm: number;
  linkQualityPercent: number;
  signalQuality: number;
  frequencyMhz: number;
  channel: number | null;
  band: string;
  security: string;
}

export interface WindowsScanOutcome {
  success: boolean;
  errorKind: string;
  statusMessage: string;
  locationMessage: string;
  scannedAtEpochMs: number;
  location: {
    latitude: number;
    longitude: number;
    accuracyMeters?: number | null;
  } | null;
  networks: WindowsScannedNetwork[];
}

export interface WindowsCompanionStatus {
  paired: boolean;
  deviceId?: string | null;
  deviceName?: string | null;
  platform: "windows";
  expiresAt?: string | null;
  backendBaseUrl?: string;
  monitorActive: boolean;
  uploadInProgress: boolean;
  queuedCount: number;
  uploadedCount: number;
  globalPauseRemainingSec?: number | null;
  statusBanner: string;
  adapter?: {
    interfaceGuid?: string;
    description?: string;
    state?: string;
    isRadioReady?: boolean;
    connectedSsid?: string | null;
  } | null;
  lastScan?: WindowsScanOutcome | null;
}

export interface WindowsUploadPreview {
  totalReadyCount: number;
  existingPendingQueueCount: number;
  readyLocalCount: number;
  invalidSkippedCount: number;
  hasLocation: boolean;
  locationAccuracyMeters?: number | null;
  previewLines: string[];
}

export interface WindowsUploadConfirmResponse {
  confirmed: boolean;
  cancelledOrMismatch: boolean;
  attemptedCount: number;
  uploadedCount: number;
  remainingQueuedCount: number;
  failedOrDeferredCount: number;
  requiresReparing: boolean;
  message: string;
  status: WindowsCompanionStatus;
}

export function buildCompanionBaseUrl(
  port: number = DEFAULT_WINDOWS_COMPANION_PORT
): string {
  const safePort = Number.isInteger(port) && port >= 1024 && port <= 65535
    ? port
    : DEFAULT_WINDOWS_COMPANION_PORT;
  return `http://127.0.0.1:${safePort}`;
}

export function formatCompanionOfflineMessage(): string {
  return "Windows Companion is not reachable on http://127.0.0.1:48271. Build and launch windows/WiFiVisionCompanion.exe on your Windows laptop to scan real nearby Wi-Fi networks.";
}

async function companionRequest<T>(
  path: string,
  options: {
    method?: "GET" | "POST";
    token?: string;
    body?: unknown;
    port?: number;
  } = {}
): Promise<T> {
  const baseUrl = buildCompanionBaseUrl(options.port);
  const headers: Record<string, string> = {};
  if (options.token) {
    headers[LOCAL_COMPANION_TOKEN_HEADER] = options.token.trim();
  }
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method: options.method ?? "GET",
      headers,
      mode: "cors",
      credentials: "omit",
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
  } catch {
    throw new WindowsCompanionError(formatCompanionOfflineMessage(), {
      status: 0,
      errorCode: "COMPANION_OFFLINE",
      isOffline: true,
    });
  }

  const text = await response.text();
  let parsed: Record<string, unknown> = {};
  if (text) {
    try {
      parsed = JSON.parse(text) as Record<string, unknown>;
    } catch {
      parsed = {};
    }
  }

  if (!response.ok) {
    const serverMsg =
      typeof parsed.message === "string" && parsed.message.trim()
        ? parsed.message
        : `Companion request failed (HTTP ${response.status}).`;
    const serverCode =
      typeof parsed.error === "string" && parsed.error.trim()
        ? parsed.error
        : `HTTP_${response.status}`;
    throw new WindowsCompanionError(serverMsg, {
      status: response.status,
      errorCode: serverCode,
      isOffline: false,
    });
  }

  return parsed as unknown as T;
}

export function checkCompanionHealth(
  port: number = DEFAULT_WINDOWS_COMPANION_PORT
): Promise<WindowsCompanionHealth> {
  return companionRequest<WindowsCompanionHealth>("/health", {
    method: "GET",
    port,
  });
}

export function fetchCompanionStatus(
  token: string,
  port: number = DEFAULT_WINDOWS_COMPANION_PORT
): Promise<WindowsCompanionStatus> {
  if (!token.trim()) {
    return Promise.reject(
      new Error("Paste the Local API Token from WiFiVisionCompanion.exe first.")
    );
  }
  return companionRequest<WindowsCompanionStatus>("/status", {
    method: "GET",
    token,
    port,
  });
}

export function triggerCompanionScan(
  token: string,
  port: number = DEFAULT_WINDOWS_COMPANION_PORT
): Promise<{ outcome: WindowsScanOutcome; status: WindowsCompanionStatus }> {
  return companionRequest("/scan", {
    method: "POST",
    token,
    body: {},
    port,
  });
}

export function startCompanionMonitor(
  token: string,
  port: number = DEFAULT_WINDOWS_COMPANION_PORT
): Promise<WindowsCompanionStatus> {
  return companionRequest("/monitor/start", {
    method: "POST",
    token,
    body: {},
    port,
  });
}

export function stopCompanionMonitor(
  token: string,
  port: number = DEFAULT_WINDOWS_COMPANION_PORT
): Promise<WindowsCompanionStatus> {
  return companionRequest("/monitor/stop", {
    method: "POST",
    token,
    body: {},
    port,
  });
}

export function fetchCompanionUploadPreview(
  token: string,
  port: number = DEFAULT_WINDOWS_COMPANION_PORT
): Promise<WindowsUploadPreview> {
  return companionRequest("/queue/preview", {
    method: "GET",
    token,
    port,
  });
}

export function confirmCompanionUpload(
  token: string,
  expectedCount: number,
  port: number = DEFAULT_WINDOWS_COMPANION_PORT
): Promise<WindowsUploadConfirmResponse> {
  return companionRequest("/upload/confirm", {
    method: "POST",
    token,
    body: {
      confirm: true,
      expectedCount,
    },
    port,
  });
}
