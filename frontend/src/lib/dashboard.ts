const API_URL = process.env.NEXT_PUBLIC_API_URL?.replace(/\/+$/, "");

export function getConfiguredApiUrl(): string | null {
  return API_URL || null;
}

export interface DashboardResponse<T> {
  success: boolean;
  message?: string;
  analytics?: T;
  count?: number;
  averageScore?: number | null;
  networks?: unknown[];
  heatmap?: unknown[];
  density?: unknown[];
}

export interface NetworkObservation {
  _id: string;
  ssid: string;
  signalDbm: number;
  frequencyMhz: number;
  band: string;
  security: string;
  signalQuality: number;
  location?: {
    latitude: number;
    longitude: number;
    accuracyMeters?: number;
  };
  source: string;
  observedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface ObservationListResponse {
  success: boolean;
  count: number;
  totalCount?: number;
  page?: number;
  limit?: number;
  hasMore?: boolean;
  observations: NetworkObservation[];
}

export interface SystemHealth {
  success: boolean;
  status?: string;
  service?: string;
  database?: string;
  timestamp?: string;
}

export interface ObservationBandStat {
  _id: string | null;
  count: number;
}

export interface ObservationSecurityStat {
  _id: string | null;
  count: number;
}

export interface ObservationAnalytics {
  totalNetworks: number;
  averageSignal: number | null;
  strongestSignal: number | null;
  weakestSignal: number | null;
  filteredBand?: string | null;
  bands?: ObservationBandStat[];
  security?: ObservationSecurityStat[];
}

export interface HeatmapBandDistribution {
  band: string;
  count: number;
  averageSignal?: number | null;
  strongestSignal?: number | null;
  weakestSignal?: number | null;
}

export interface HeatmapPoint {
  latitude: number;
  longitude: number;
  totalObservations: number;
  averageSignal: number;
  strongestSignal: number;
  weakestSignal: number;
  avgAccuracyMeters?: number | null;
  accuracySampleCount?: number;
  missingAccuracyCount?: number;
  coarseAccuracyCount?: number;
  frequencyDistribution: HeatmapBandDistribution[];
}

export interface HeatmapAccuracySummary {
  averageAccuracyMeters: number | null;
  withAccuracyCount: number;
  withoutAccuracyCount: number;
  coarseAccuracyCount: number;
}

export interface HeatmapLocationSummary {
  geolocatedObservations: number;
  nonGeolocatedObservations: number;
  totalObservations: number;
}

export interface HeatmapResponse {
  success: boolean;
  count: number;
  heatmap: HeatmapPoint[];
  accuracySummary?: HeatmapAccuracySummary;
  locationSummary?: HeatmapLocationSummary;
}

export interface NetworkDensityPoint {
  latitude: number;
  longitude: number;
  totalObservations: number;
  uniqueNetworkCount: number;
  averageSignal: number;
  frequencyDistribution: {
    band: string;
    count: number;
  }[];
}

export interface NetworkDensityResponse {
  success: boolean;
  count: number;
  density: NetworkDensityPoint[];
}

export interface NetworkQualityComponents {
  signalScore: number;
  signalLevelScore: number;
  bandScore: number;
  securityScore: number;
}

export interface NetworkQualityDetails {
  signalDbm: number;
  band: string;
  security: string;
}

export interface NetworkQualityResult {
  score: number;
  classification: "Excellent" | "Good" | "Fair" | "Poor" | string;
  components: NetworkQualityComponents;
  details: NetworkQualityDetails;
}

export interface NetworkQualityItem {
  observationId: string;
  ssid: string;
  quality: NetworkQualityResult;
  observedAt: string;
}

export interface NetworkQualityResponse {
  success: boolean;
  count: number;
  averageScore: number | null;
  networks: NetworkQualityItem[];
}

export interface PacketLossSummary {
  totalPackets: number;
  successfulPackets: number;
  lostPackets: number;
  packetLossPercentage: number;
  averageResponseMs: number | null;
  measurementType: string;
}

export interface PerformanceTestResult {
  latencyMs: number;
  medianLatencyMs?: number | null;
  jitterMs: number;
  downloadMbps: number;
  uploadMbps: number;
  measuredAt: string;
  packetLoss: PacketLossSummary;
}

export interface RunPerformanceTestResponse {
  success: boolean;
  message: string;
  savedMeasurementId: string;
  performance: PerformanceTestResult;
}

export interface PerformanceMeasurementRecord {
  _id: string;
  latencyMs: number;
  medianLatencyMs?: number | null;
  jitterMs: number;
  downloadMbps: number;
  uploadMbps: number;
  packetLoss: PacketLossSummary;
  source: string;
  testServer?: string;
  measuredAt: string;
  createdAt?: string;
}

export interface PerformanceHistoryResponse {
  success: boolean;
  count: number;
  history: PerformanceMeasurementRecord[];
}

export interface PerformanceTrendPoint {
  latencyMs: number;
  medianLatencyMs: number | null;
  jitterMs: number;
  downloadMbps: number;
  uploadMbps: number;
  packetLossPercentage: number;
  source: string;
  measuredAt: string;
}

export interface PerformanceTrendsResponse {
  success: boolean;
  count: number;
  trends: PerformanceTrendPoint[];
}

export interface PerformanceAnalytics {
  totalTests: number;
  averageDownloadMbps: number | null;
  averageUploadMbps: number | null;
  averageLatencyMs: number | null;
  averageMedianLatencyMs?: number | null;
  averageJitterMs: number | null;
  averagePacketLossPercentage: number | null;
  fastestDownloadMbps?: number | null;
  slowestDownloadMbps?: number | null;
  fastestUploadMbps?: number | null;
  latestMeasurement?: string | null;
}

export type DevicePlatform = "android" | "windows";

export interface PairedDevice {
  deviceId: string;
  deviceName: string;
  platform?: DevicePlatform;
  scopes: string[];
  status: "active" | "revoked";
  revokedAt?: string | null;
  expiresAt: string;
  lastUsedAt?: string | null;
  createdAt: string;
}

export interface PairingCodeResponse {
  success: boolean;
  pairingCode: string;
  platform?: DevicePlatform | "any";
  expiresAt: string;
  expiresInSeconds: number;
}

export interface DeviceListResponse {
  success: boolean;
  count: number;
  devices: PairedDevice[];
}

export interface RevokeDeviceResponse {
  success: boolean;
  message: string;
  device: PairedDevice;
}

export interface CreateWebObservationPayload {
  ssid?: string;
  bssid?: string;
  signalDbm: number;
  frequencyMhz: number;
  band?: "2.4 GHz" | "5 GHz" | "6 GHz" | "Unknown";
  security?: "Open" | "WEP" | "WPA" | "WPA2" | "WPA3" | "Unknown";
  signalQuality?: number;
  location?: {
    latitude: number;
    longitude: number;
    accuracyMeters?: number;
  };
  source?: "android" | "web";
  observedAt?: string;
}

export interface CreateWebObservationResponse {
  success: boolean;
  message: string;
  observation: {
    id: string;
    ssid: string;
    signalDbm: number;
    frequencyMhz: number;
    band: string;
    security: string;
    signalQuality: number;
    location?: {
      latitude: number;
      longitude: number;
      accuracyMeters?: number;
    };
    source: string;
    observedAt: string;
  };
}

async function dashboardRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  if (!API_URL) {
    throw new Error("Frontend API URL is not configured.");
  }

  const response = await fetch(`${API_URL}${endpoint}`, {
    method: "GET",
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
    cache: "no-store",
  });

  const data = await response.json().catch(() => null);

  if (!response.ok || !data?.success) {
    throw new Error(
      data?.message || `Dashboard request failed: ${endpoint}`
    );
  }

  return data as T;
}

export function getSystemHealth() {
  return dashboardRequest<SystemHealth>("/health");
}

export function getObservationAnalytics(band?: string) {
  const params = new URLSearchParams();
  if (band && band !== "all") {
    params.set("band", band);
  }
  const query = params.toString();
  return dashboardRequest<DashboardResponse<ObservationAnalytics>>(
    `/observations/stats${query ? `?${query}` : ""}`
  );
}

export function getObservations(options?: { page?: number; limit?: number }) {
  const params = new URLSearchParams();
  if (typeof options?.page === "number" && Number.isFinite(options.page)) {
    params.set("page", String(Math.trunc(options.page)));
  }
  if (typeof options?.limit === "number" && Number.isFinite(options.limit)) {
    params.set("limit", String(Math.trunc(options.limit)));
  }
  const query = params.toString();
  return dashboardRequest<ObservationListResponse>(
    `/observations${query ? `?${query}` : ""}`
  );
}

export function createWebObservation(payload: CreateWebObservationPayload) {
  return dashboardRequest<CreateWebObservationResponse>("/observations", {
    method: "POST",
    body: JSON.stringify({
      source: "web",
      ...payload,
    }),
  });
}

export function getPerformanceAnalytics() {
  return dashboardRequest<DashboardResponse<PerformanceAnalytics>>(
    "/performance/analytics"
  );
}

export function runPerformanceTest() {
  return dashboardRequest<RunPerformanceTestResponse>("/performance", {
    method: "POST",
  });
}

export function getPerformanceHistory(limit = 50) {
  const safeLimit = Math.min(100, Math.max(1, Math.trunc(limit)));
  return dashboardRequest<PerformanceHistoryResponse>(
    `/performance/history?limit=${safeLimit}`
  );
}

export function getPerformanceTrends(limit = 50) {
  const safeLimit = Math.min(100, Math.max(1, Math.trunc(limit)));
  return dashboardRequest<PerformanceTrendsResponse>(
    `/performance/trends?limit=${safeLimit}`
  );
}

export function getNetworkQuality() {
  return dashboardRequest<NetworkQualityResponse>("/quality");
}

export function getHeatmapData(band?: string) {
  const query = band ? `?band=${encodeURIComponent(band)}` : "";
  return dashboardRequest<HeatmapResponse>(`/observations/heatmap${query}`);
}

export function getNetworkDensity() {
  return dashboardRequest<NetworkDensityResponse>("/observations/density");
}

export function listPairedDevices() {
  return dashboardRequest<DeviceListResponse>("/devices");
}

export function generateDevicePairingCode(
  platform: DevicePlatform | "any" = "android"
) {
  return dashboardRequest<PairingCodeResponse>("/devices/pairing-code", {
    method: "POST",
    body: JSON.stringify({ platform }),
  });
}

export function revokePairedDevice(deviceId: string) {
  return dashboardRequest<RevokeDeviceResponse>(
    `/devices/${encodeURIComponent(deviceId)}`,
    {
      method: "DELETE",
    }
  );
}
