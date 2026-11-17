"use client";

import { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  Database,
  Gauge,
  RefreshCw,
  Server,
  Wifi,
  XCircle,
} from "lucide-react";

import DashboardShell from "@/components/layout/DashboardShell";
import {
  getConfiguredApiUrl,
  getNetworkQuality,
  getObservationAnalytics,
  getObservations,
  getPerformanceAnalytics,
  getPerformanceHistory,
  getSystemHealth,
  type NetworkObservation,
  type ObservationAnalytics,
  type PerformanceAnalytics,
  type PerformanceMeasurementRecord,
  type SystemHealth,
} from "@/lib/dashboard";
import {
  getLocalPreferences,
  type MonitoringIntervalSeconds,
} from "@/lib/preferences";

type EndpointAvailability = {
  id: string;
  label: string;
  endpoint: string;
  status: "available" | "unavailable" | "checking";
  detail: string;
};

function formatDateTime(value?: string | null): string {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "—";
  return parsed.toLocaleString();
}

export default function MonitoringPage() {
  const [health, setHealth] = useState<SystemHealth | null>(null);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [observations, setObservations] = useState<NetworkObservation[]>([]);
  const [perfHistory, setPerfHistory] = useState<PerformanceMeasurementRecord[]>([]);
  const [obsStats, setObsStats] = useState<ObservationAnalytics | null>(null);
  const [perfStats, setPerfStats] = useState<PerformanceAnalytics | null>(null);
  const [endpoints, setEndpoints] = useState<EndpointAvailability[]>([
    {
      id: "health",
      label: "Core API Health",
      endpoint: "GET /api/health",
      status: "checking",
      detail: "Checking service status...",
    },
    {
      id: "observations",
      label: "Observations Feed",
      endpoint: "GET /api/observations",
      status: "checking",
      detail: "Checking observation telemetry...",
    },
    {
      id: "quality",
      label: "Quality Scoring Engine",
      endpoint: "GET /api/quality",
      status: "checking",
      detail: "Checking network scoring endpoint...",
    },
    {
      id: "performance",
      label: "Performance History",
      endpoint: "GET /api/performance/history",
      status: "checking",
      detail: "Checking speed/latency records...",
    },
  ]);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(
    () => getLocalPreferences().monitoringAutoRefresh
  );
  const [intervalSeconds, setIntervalSeconds] =
    useState<MonitoringIntervalSeconds>(
      () => getLocalPreferences().monitoringIntervalSeconds
    );
  const [lastUpdated, setLastUpdated] = useState<string | null>(null);

  const apiUrl = getConfiguredApiUrl();

  const fetchHealthCheck = useCallback(async () => {
    const [
      healthRes,
      obsRes,
      qualityRes,
      perfHistRes,
      obsStatsRes,
      perfStatsRes,
    ] = await Promise.allSettled([
      getSystemHealth(),
      getObservations(),
      getNetworkQuality(),
      getPerformanceHistory(10),
      getObservationAnalytics(),
      getPerformanceAnalytics(),
    ]);

    if (healthRes.status === "fulfilled") {
      setHealth(healthRes.value);
      setHealthError(null);
    } else {
      setHealth(null);
      setHealthError(
        healthRes.reason instanceof Error
          ? healthRes.reason.message
          : "Health endpoint unavailable."
      );
    }

    if (obsRes.status === "fulfilled") {
      setObservations((obsRes.value.observations ?? []).slice(0, 8));
    } else {
      setObservations([]);
    }

    if (perfHistRes.status === "fulfilled") {
      setPerfHistory((perfHistRes.value.history ?? []).slice(0, 8));
    } else {
      setPerfHistory([]);
    }

    setObsStats(
      obsStatsRes.status === "fulfilled"
        ? (obsStatsRes.value.analytics ?? null)
        : null
    );
    setPerfStats(
      perfStatsRes.status === "fulfilled"
        ? (perfStatsRes.value.analytics ?? null)
        : null
    );

    setEndpoints([
      {
        id: "health",
        label: "Core API Health",
        endpoint: "GET /api/health",
        status:
          healthRes.status === "fulfilled" && healthRes.value.success
            ? "available"
            : "unavailable",
        detail:
          healthRes.status === "fulfilled"
            ? `${healthRes.value.service ?? "WiFiVision"} · DB: ${
                healthRes.value.database ?? "unknown"
              }`
            : healthRes.reason instanceof Error
              ? healthRes.reason.message
              : "Health check failed",
      },
      {
        id: "observations",
        label: "Observations Feed",
        endpoint: "GET /api/observations",
        status: obsRes.status === "fulfilled" ? "available" : "unavailable",
        detail:
          obsRes.status === "fulfilled"
            ? `${obsRes.value.count} observation(s) returned`
            : obsRes.reason instanceof Error
              ? obsRes.reason.message
              : "Endpoint unavailable",
      },
      {
        id: "quality",
        label: "Quality Scoring Engine",
        endpoint: "GET /api/quality",
        status: qualityRes.status === "fulfilled" ? "available" : "unavailable",
        detail:
          qualityRes.status === "fulfilled"
            ? `${qualityRes.value.count} scored network(s)`
            : qualityRes.reason instanceof Error
              ? qualityRes.reason.message
              : "Endpoint unavailable",
      },
      {
        id: "performance",
        label: "Performance History",
        endpoint: "GET /api/performance/history",
        status: perfHistRes.status === "fulfilled" ? "available" : "unavailable",
        detail:
          perfHistRes.status === "fulfilled"
            ? `${perfHistRes.value.count} recent test(s)`
            : perfHistRes.reason instanceof Error
              ? perfHistRes.reason.message
              : "Endpoint unavailable",
      },
    ]);

    setLastUpdated(new Date().toISOString());
    setLoading(false);
    setRefreshing(false);
  }, []);

  const runHealthCheck = useCallback(
    (isRefresh = false) => {
      if (isRefresh) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      void fetchHealthCheck();
    },
    [fetchHealthCheck]
  );

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(() => {
      if (active) {
        return fetchHealthCheck();
      }
    });
    return () => {
      active = false;
    };
  }, [fetchHealthCheck]);

  useEffect(() => {
    if (!autoRefresh) return undefined;
    const intervalId = window.setInterval(() => {
      void runHealthCheck(true);
    }, intervalSeconds * 1000);
    return () => {
      window.clearInterval(intervalId);
    };
  }, [autoRefresh, intervalSeconds, runHealthCheck]);

  const backendHealthy =
    health !== null &&
    health.success &&
    (health.status === "healthy" || health.status === "ok");
  const dbStatusRaw = health?.database?.toLowerCase();
  const dbConnected = dbStatusRaw === "connected";
  const dbUnavailable = health === null || !dbStatusRaw;

  const availableEndpointCount = endpoints.filter(
    (item) => item.status === "available"
  ).length;

  const cardClass =
    "rounded-2xl border border-[#303640]/[0.09] bg-white shadow-[0_2px_12px_rgba(48,54,64,0.04)]";

  return (
    <DashboardShell>
      <div className="space-y-6">
        <div className="flex flex-col gap-4 border-b border-[#303640]/[0.08] pb-5 lg:flex-row lg:items-end lg:justify-between">
          <div className="min-w-0">
            <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-[#9F7C38]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#C6A15B]" />
              Operational Telemetry
            </div>
            <h1 className="mt-1.5 text-2xl font-semibold tracking-tight text-[#303640] sm:text-[26px]">
              System Monitoring
            </h1>
            <p className="mt-1 text-sm text-[#5E6572]">
              Live verification of backend API reachability, database state, and
              recent Wi-Fi or speed-test telemetry activity.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <label className="inline-flex h-10 cursor-pointer items-center gap-2.5 rounded-xl border border-[#303640]/12 bg-white px-3.5 text-xs font-medium text-[#303640] shadow-[0_1px_2px_rgba(48,54,64,0.03)]">
              <input
                type="checkbox"
                checked={autoRefresh}
                onChange={(e) => setAutoRefresh(e.target.checked)}
                className="h-4 w-4 rounded border-[#303640]/30 accent-[#C6A15B]"
              />
              Auto-refresh
            </label>

            <select
              value={intervalSeconds}
              onChange={(e) =>
                setIntervalSeconds(
                  Number(e.target.value) as MonitoringIntervalSeconds
                )
              }
              disabled={!autoRefresh}
              aria-label="Auto-refresh interval"
              className="h-10 rounded-xl border border-[#303640]/12 bg-white px-3 text-xs font-medium text-[#303640] shadow-[0_1px_2px_rgba(48,54,64,0.03)] outline-none focus:border-[#C6A15B] disabled:opacity-50"
            >
              <option value={15}>Every 15s</option>
              <option value={30}>Every 30s</option>
              <option value={60}>Every 60s</option>
            </select>

            <button
              type="button"
              onClick={() => void runHealthCheck(true)}
              disabled={loading || refreshing}
              className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-[#303640] px-4 text-sm font-semibold text-white shadow-xs transition hover:bg-[#3F4754] disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
            >
              <RefreshCw
                className={`h-4 w-4 text-[#C6A15B] ${refreshing ? "animate-spin" : ""}`}
              />
              {refreshing ? "Checking..." : "Run Health Check"}
            </button>
          </div>
        </div>

        {healthError && (
          <div className="flex items-start gap-3 rounded-2xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.06] px-4 py-3 text-sm text-[#B83B3B]">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <div>
              <p className="font-medium">Health endpoint reported an issue</p>
              <p className="mt-0.5 text-xs opacity-90">{healthError}</p>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {/* Backend Service Status */}
          <div className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-[#868D98]">
                Backend Service
              </span>
              <Server className="h-4 w-4 text-[#9F7C38]" />
            </div>
            <div className="mt-3 flex items-center gap-2.5">
              {loading ? (
                <span className="text-lg font-semibold text-[#5E6572]">
                  Checking...
                </span>
              ) : backendHealthy ? (
                <>
                  <CheckCircle2 className="h-5 w-5 text-[#2E7D5B]" />
                  <span className="text-lg font-semibold text-[#2E7D5B]">
                    Healthy
                  </span>
                </>
              ) : (
                <>
                  <XCircle className="h-5 w-5 text-[#B83B3B]" />
                  <span className="text-lg font-semibold text-[#B83B3B]">
                    Unavailable
                  </span>
                </>
              )}
            </div>
            <p className="mt-2 text-xs text-[#5E6572]">
              Service: {health?.service ?? "Not reported"}
            </p>
            <p className="mt-0.5 truncate text-xs text-[#868D98]">
              API Base: {apiUrl ?? "NEXT_PUBLIC_API_URL not set"}
            </p>
          </div>

          {/* Database Status */}
          <div className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-[#868D98]">
                MongoDB Database
              </span>
              <Database className="h-4 w-4 text-[#9F7C38]" />
            </div>
            <div className="mt-3 flex items-center gap-2.5">
              {loading ? (
                <span className="text-lg font-semibold text-[#5E6572]">
                  Checking...
                </span>
              ) : dbConnected ? (
                <>
                  <CheckCircle2 className="h-5 w-5 text-[#2E7D5B]" />
                  <span className="text-lg font-semibold text-[#2E7D5B]">
                    Connected
                  </span>
                </>
              ) : dbUnavailable ? (
                <>
                  <AlertTriangle className="h-5 w-5 text-[#B67B28]" />
                  <span className="text-lg font-semibold text-[#B67B28]">
                    Status Unavailable
                  </span>
                </>
              ) : (
                <>
                  <XCircle className="h-5 w-5 text-[#B83B3B]" />
                  <span className="text-lg font-semibold capitalize text-[#B83B3B]">
                    {health?.database}
                  </span>
                </>
              )}
            </div>
            <p className="mt-2 text-xs text-[#5E6572]">
              Reported state: {health?.database ?? "Unavailable"}
            </p>
            <p className="mt-0.5 text-xs text-[#868D98]">
              Server time: {formatDateTime(health?.timestamp)}
            </p>
          </div>

          {/* API Availability Summary */}
          <div className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-[#868D98]">
                API Availability
              </span>
              <Clock className="h-4 w-4 text-[#9F7C38]" />
            </div>
            <div className="mt-3 flex items-baseline gap-2">
              <span className="text-2xl font-semibold tabular-nums text-[#303640]">
                {loading ? "—" : `${availableEndpointCount} / ${endpoints.length}`}
              </span>
              <span className="text-xs font-medium text-[#5E6572]">
                endpoints responding
              </span>
            </div>
            <p className="mt-2 text-xs text-[#5E6572]">
              Total stored scans: {obsStats?.totalNetworks ?? "Unavailable"} ·
              Speed tests: {perfStats?.totalTests ?? "Unavailable"}
            </p>
            <p className="mt-0.5 text-xs text-[#868D98]">
              Last verified: {formatDateTime(lastUpdated)}
            </p>
          </div>
        </div>

        {/* Endpoint Availability Matrix */}
        <section className={`${cardClass} p-5`}>
          <h2 className="text-base font-semibold text-[#303640]">
            Endpoint Availability Checks
          </h2>
          <p className="mt-0.5 text-xs text-[#5E6572]">
            Each endpoint is verified independently; unavailable endpoints are never
            treated as healthy.
          </p>

          <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2">
            {endpoints.map((ep) => (
              <div
                key={ep.id}
                className="flex items-center justify-between gap-3 rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] px-4 py-3"
              >
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span className="text-sm font-semibold text-[#303640]">
                      {ep.label}
                    </span>
                    <code className="shrink-0 whitespace-nowrap rounded-md border border-[#303640]/10 bg-white px-2 py-0.5 text-[11px] font-medium text-[#5E6572]">
                      {ep.endpoint}
                    </code>
                  </div>
                  <p className="mt-1 truncate text-xs text-[#5E6572]">
                    {ep.detail}
                  </p>
                </div>

                {ep.status === "checking" ? (
                  <span className="shrink-0 rounded-full bg-[#F1EFE9] px-2.5 py-1 text-[11px] font-medium text-[#5E6572]">
                    Checking
                  </span>
                ) : ep.status === "available" ? (
                  <span className="shrink-0 rounded-full border border-[#2E7D5B]/25 bg-[#2E7D5B]/10 px-2.5 py-1 text-[11px] font-semibold text-[#2E7D5B]">
                    Available
                  </span>
                ) : (
                  <span className="shrink-0 rounded-full border border-[#B83B3B]/25 bg-[#B83B3B]/10 px-2.5 py-1 text-[11px] font-semibold text-[#B83B3B]">
                    Unavailable
                  </span>
                )}
              </div>
            ))}
          </div>
        </section>

        {/* Recent Telemetry Activity */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <section className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-semibold text-[#303640]">
                  Recent Wi-Fi Observations
                </h2>
                <p className="text-xs text-[#5E6572]">
                  Latest telemetry ingested from Android, Windows, or Web sensors
                </p>
              </div>
              <Wifi className="h-4 w-4 text-[#9F7C38]" />
            </div>

            {loading ? (
              <div className="mt-4 h-52 animate-pulse rounded-xl bg-[#F1EFE9]" />
            ) : observations.length === 0 ? (
              <div className="mt-4 flex h-48 items-center justify-center rounded-xl border border-dashed border-[#303640]/15 bg-[#F7F6F2] px-4 text-center text-sm text-[#5E6572]">
                No recent Wi-Fi observations found or endpoint unavailable.
              </div>
            ) : (
              <div className="mt-4 divide-y divide-[#303640]/[0.07]">
                {observations.map((obs) => (
                  <div
                    key={obs._id}
                    className="flex items-center justify-between gap-3 py-2.5 text-sm"
                  >
                    <div className="min-w-0">
                      <p className="truncate font-medium text-[#303640]">
                        {obs.ssid?.trim() ? obs.ssid : "<Hidden Network>"}
                      </p>
                      <p className="text-xs text-[#5E6572]">
                        {obs.band} · {obs.security} · source: {obs.source}
                      </p>
                    </div>
                    <div className="shrink-0 text-right">
                      <p className="font-semibold tabular-nums text-[#303640]">
                        {obs.signalDbm} dBm
                      </p>
                      <p className="text-[11px] text-[#868D98]">
                        {formatDateTime(obs.observedAt || obs.createdAt)}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-semibold text-[#303640]">
                  Recent Performance Activity
                </h2>
                <p className="text-xs text-[#5E6572]">
                  Latest recorded throughput, latency, and packet-loss probes
                </p>
              </div>
              <Gauge className="h-4 w-4 text-[#9F7C38]" />
            </div>

            {loading ? (
              <div className="mt-4 h-52 animate-pulse rounded-xl bg-[#F1EFE9]" />
            ) : perfHistory.length === 0 ? (
              <div className="mt-4 flex h-48 items-center justify-center rounded-xl border border-dashed border-[#303640]/15 bg-[#F7F6F2] px-4 text-center text-sm text-[#5E6572]">
                No performance measurements recorded yet or endpoint unavailable.
              </div>
            ) : (
              <div className="mt-4 divide-y divide-[#303640]/[0.07]">
                {perfHistory.map((item) => (
                  <div
                    key={item._id}
                    className="flex items-center justify-between gap-3 py-2.5 text-sm"
                  >
                    <div className="min-w-0">
                      <p className="font-medium tabular-nums text-[#303640]">
                        {item.downloadMbps.toFixed(1)} Mbps down ·{" "}
                        {item.uploadMbps.toFixed(1)} Mbps up
                      </p>
                      <p className="text-xs text-[#5E6572]">
                        Latency {item.latencyMs.toFixed(1)} ms · Jitter{" "}
                        {item.jitterMs.toFixed(1)} ms · Loss{" "}
                        {item.packetLoss.packetLossPercentage.toFixed(1)}%
                      </p>
                    </div>
                    <div className="shrink-0 text-right">
                      <span className="inline-flex rounded-full bg-[#F1EFE9] px-2 py-0.5 text-[11px] font-medium text-[#303640]">
                        {item.source}
                      </span>
                      <p className="mt-1 text-[11px] text-[#868D98]">
                        {formatDateTime(item.measuredAt || item.createdAt)}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </DashboardShell>
  );
}
