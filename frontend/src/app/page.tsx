"use client";

import { useCallback, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";

import {
  Activity,
  ArrowDownToLine,
  ArrowUpFromLine,
  Clock3,
  Crosshair,
  Gauge,
  RadioTower,
  RefreshCw,
  Signal,
  Zap,
  CheckCircle2,
  CircleDashed,
  AlertCircle,
  Wifi,
  ShieldCheck,
  ArrowUpRight,
  Download,
  Radio,
} from "lucide-react";

import DashboardShell from "@/components/layout/DashboardShell";
import WindowsCompanionCard from "@/components/settings/WindowsCompanionCard";
import { useWindowsCompanion } from "@/components/companion/WindowsCompanionProvider";
import { ANDROID_RELEASE } from "@/lib/releaseInfo";

import {
  getSystemHealth,
  getObservationAnalytics,
  getPerformanceAnalytics,
  getNetworkQuality,
  type ObservationAnalytics,
  type PerformanceAnalytics,
} from "@/lib/dashboard";

const WifiHeatmap = dynamic(
  () => import("@/components/dashboard/WifiHeatmap"),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-[480px] w-full animate-pulse items-center justify-center rounded-xl border border-[#303640]/[0.09] bg-[#F1EFE9]">
        <div className="flex items-center gap-3 text-xs text-[#5E6572]">
          <CircleDashed size={17} className="animate-spin text-[#C6A15B]" />
          Loading network intelligence map...
        </div>
      </div>
    ),
  }
);

type ConnectionStatus = "checking" | "online" | "offline";

interface DashboardData {
  observations: ObservationAnalytics | null;
  performance: PerformanceAnalytics | null;
  qualityScore: number | null;
  backend: ConnectionStatus;
  database: ConnectionStatus;
}

const initialData: DashboardData = {
  observations: null,
  performance: null,
  qualityScore: null,
  backend: "checking",
  database: "checking",
};

function formatValue(
  value: number | null | undefined,
  decimals = 2
): string {
  if (
    value === null ||
    value === undefined ||
    !Number.isFinite(value)
  ) {
    return "—";
  }

  return value.toFixed(decimals);
}

function StatusIndicator({
  status,
}: {
  status: ConnectionStatus;
}) {
  if (status === "checking") {
    return (
      <span className="flex items-center gap-1.5 text-[10px] font-semibold text-[#B67B28]">
        <CircleDashed size={12} className="animate-spin" />
        Checking
      </span>
    );
  }

  if (status === "online") {
    return (
      <span className="flex items-center gap-1.5 text-[10px] font-semibold text-[#2E7D5B]">
        <CheckCircle2 size={12} />
        Connected
      </span>
    );
  }

  return (
    <span className="flex items-center gap-1.5 text-[10px] font-semibold text-[#B83B3B]">
      <AlertCircle size={12} />
      Unavailable
    </span>
  );
}

function SectionHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description?: string;
}) {
  return (
    <div className="min-w-0">
      <p className="mb-1 text-[9px] font-bold uppercase tracking-[0.19em] text-[#9F7C38]">
        {eyebrow}
      </p>

      <h2 className="text-[15px] font-semibold tracking-tight text-[#303640]">
        {title}
      </h2>

      {description && (
        <p className="mt-1 text-[11px] leading-relaxed text-[#5E6572]">
          {description}
        </p>
      )}
    </div>
  );
}

const panel =
  "rounded-2xl border border-[#303640]/[0.09] bg-white shadow-[0_2px_12px_rgba(48,54,64,0.04)]";

export default function DashboardPage() {
  const { lastUploadVersion } = useWindowsCompanion();
  const [data, setData] = useState<DashboardData>(initialData);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const fetchDashboard = useCallback(async () => {
    const [
      healthResult,
      observationResult,
      performanceResult,
      qualityResult,
    ] = await Promise.allSettled([
      getSystemHealth(),
      getObservationAnalytics(),
      getPerformanceAnalytics(),
      getNetworkQuality(),
    ]);

    const failures: string[] = [];

    const nextData: DashboardData = {
      observations: null,
      performance: null,
      qualityScore: null,
      backend: "offline",
      database: "offline",
    };

    if (healthResult.status === "fulfilled") {
      const health = healthResult.value;

      nextData.backend =
        health.success &&
        (health.status === "healthy" || health.status === "ok")
          ? "online"
          : "offline";

      nextData.database =
        health.database === "connected" ? "online" : "offline";
    } else {
      failures.push("health status");
    }

    if (observationResult.status === "fulfilled") {
      nextData.observations = observationResult.value.analytics ?? null;
    } else {
      failures.push("observation statistics");
    }

    if (performanceResult.status === "fulfilled") {
      nextData.performance = performanceResult.value.analytics ?? null;
    } else {
      failures.push("performance analytics");
    }

    if (qualityResult.status === "fulfilled") {
      nextData.qualityScore = qualityResult.value.averageScore ?? null;
    } else {
      failures.push("network quality");
    }

    setErrorMessage(
      failures.length > 0
        ? `Unable to load ${failures.join(", ")} from the backend API.`
        : null
    );

    setData(nextData);
    setLastUpdated(new Date());
    setLoading(false);
  }, []);

  const loadDashboard = useCallback(() => {
    setLoading(true);
    setErrorMessage(null);
    void fetchDashboard();
  }, [fetchDashboard]);

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(() => {
      if (active) {
        return fetchDashboard();
      }
    });
    return () => {
      active = false;
    };
  }, [fetchDashboard]);

  useEffect(() => {
    if (lastUploadVersion <= 0) return;
    void fetchDashboard();
  }, [fetchDashboard, lastUploadVersion]);

  const metrics = [
    {
      title: "NETWORK OBSERVATIONS",
      href: "/networks",
      value: data.observations
        ? String(data.observations.totalNetworks)
        : "—",
      unit: "Recorded observations",
      icon: RadioTower,
      color: "text-[#9F7C38]",
      iconBg: "bg-[#C6A15B]/15",
      accent: "bg-[#C6A15B]",
    },
    {
      title: "AVERAGE SIGNAL",
      href: "/analytics",
      value:
        data.observations?.averageSignal !== null &&
        data.observations?.averageSignal !== undefined
          ? `${formatValue(data.observations.averageSignal, 0)} dBm`
          : "—",
      unit: "Mean recorded RSSI",
      icon: Signal,
      color: "text-[#2E7D5B]",
      iconBg: "bg-[#2E7D5B]/10",
      accent: "bg-[#2E7D5B]",
    },
    {
      title: "PERFORMANCE TESTS",
      href: "/performance",
      value: data.performance
        ? String(data.performance.totalTests)
        : "—",
      unit: "Saved measurements",
      icon: Gauge,
      color: "text-[#303640]",
      iconBg: "bg-[#F1EFE9]",
      accent: "bg-[#B89B64]",
    },
    {
      title: "PEAK SIGNAL",
      href: "/analytics",
      value: data.observations?.strongestSignal !== null &&
        data.observations?.strongestSignal !== undefined
        ? `${formatValue(data.observations.strongestSignal, 0)} dBm`
        : "—",
      unit: "Strongest recorded RSSI",
      icon: Crosshair,
      color: "text-[#B83B3B]",
      iconBg: "bg-[#B83B3B]/10",
      accent: "bg-[#B83B3B]",
    },
  ];

  const performanceItems = [
    {
      label: "Download",
      value: formatValue(data.performance?.averageDownloadMbps),
      unit: "Mbps",
      icon: ArrowDownToLine,
      color: "text-[#2E7D5B]",
    },
    {
      label: "Upload",
      value: formatValue(data.performance?.averageUploadMbps),
      unit: "Mbps",
      icon: ArrowUpFromLine,
      color: "text-[#303640]",
    },
    {
      label: "Latency",
      value: formatValue(data.performance?.averageLatencyMs),
      unit: "ms",
      icon: Clock3,
      color: "text-[#9F7C38]",
    },
  ];

  const hasPerformanceTests =
    (data.performance?.totalTests ?? 0) > 0;

  const systemOnline =
    data.backend === "online" &&
    data.database === "online";

  return (
    <DashboardShell>
      <div className="w-full min-w-0 space-y-6">

        {/* PAGE HEADER */}
        <section className="flex flex-col justify-between gap-4 border-b border-[#303640]/[0.09] pb-5 lg:flex-row lg:items-center">
          <div className="min-w-0">
            <div className="mb-1.5 flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full bg-[#C6A15B]" />

              <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
                Network Intelligence Center
              </p>
            </div>

            <h1 className="text-2xl font-semibold tracking-tight text-[#303640] sm:text-[26px]">
              Network Overview
            </h1>

            <p className="mt-1 max-w-2xl text-xs leading-relaxed text-[#5E6572]">
              Monitor Wi-Fi signals, inspect nearby networks, and
              analyze wireless performance.
            </p>
          </div>

          <div className="flex flex-wrap items-start gap-2.5 sm:gap-3">
            <Link
              href="/monitoring"
              className="inline-flex h-10 items-center gap-2 rounded-xl border border-[#303640]/12 bg-white px-4 text-xs font-semibold text-[#303640] shadow-xs transition hover:border-[#C6A15B] hover:bg-[#ECE9E0]/60"
            >
              <Activity size={14} className="text-[#9F7C38]" />
              System monitoring
            </Link>

            <button
              type="button"
              onClick={() => void loadDashboard()}
              disabled={loading}
              className="inline-flex h-10 items-center gap-2 rounded-xl bg-[#303640] px-4 text-xs font-semibold text-white shadow-xs transition hover:bg-[#3F4754] disabled:cursor-not-allowed disabled:opacity-60"
            >
              <RefreshCw
                size={13}
                className={`${loading ? "animate-spin" : ""} text-[#C6A15B]`}
              />
              Refresh data
            </button>

            <div className="flex w-full flex-col sm:w-auto sm:items-end">
              <a
                href={ANDROID_RELEASE.downloadPath}
                download={ANDROID_RELEASE.fileName}
                className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-xl border border-[#C6A15B]/60 bg-gradient-to-r from-[#2E7D5B] to-[#1F6A4F] px-4 text-xs font-semibold text-white shadow-[0_6px_18px_rgba(46,125,91,0.22)] transition hover:border-[#C6A15B] hover:from-[#266B4D] hover:to-[#195941] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B] sm:w-auto"
              >
                <Download size={14} className="shrink-0 text-[#E6CD8F]" />
                Download WiFiVision
              </a>
              <span className="mt-1.5 text-[10px] font-medium tracking-wide text-[#5E6572]">
                Android 8.0+ · Version 1.1.0
              </span>
            </div>
          </div>
        </section>

        {errorMessage && (
          <div
            role="alert"
            className="flex items-center gap-2.5 rounded-2xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.06] px-4 py-3 text-xs font-medium text-[#B83B3B]"
          >
            <AlertCircle size={16} className="shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        {/* LAPTOP SCANNING STATUS & QUICK ACTIONS */}
        <WindowsCompanionCard variant="compact" />

        {/* NETWORK METRICS */}
        <section>
          <div className="mb-4 flex items-end justify-between gap-3">
            <SectionHeading
              eyebrow="Live overview"
              title="Network statistics"
            />

            <span className="shrink-0 text-[10px] font-medium text-[#868D98]">
              {loading ? "Updating..." : "Latest available data"}
            </span>
          </div>

          <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
            {metrics.map((metric) => {
              const Icon = metric.icon;

              return (
                <Link
                  key={metric.title}
                  href={metric.href}
                  className={`${panel} group relative flex min-h-[126px] min-w-0 flex-col justify-between overflow-hidden p-5 transition duration-200 hover:border-[#C6A15B]/55 hover:shadow-[0_6px_20px_rgba(48,54,64,0.06)]`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <p className="min-w-0 pt-0.5 text-[10px] font-bold leading-4 tracking-[0.14em] text-[#868D98] group-hover:text-[#5E6572]">
                      {metric.title}
                    </p>

                    <div
                      className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${metric.iconBg} ${metric.color}`}
                    >
                      <Icon size={17} strokeWidth={1.8} />
                    </div>
                  </div>

                  <div className="mt-3">
                    <h3 className="text-[26px] font-semibold leading-none tracking-tight tabular-nums text-[#303640]">
                      {metric.value}
                    </h3>

                    <p className="mt-2.5 flex items-center justify-between text-[11px] leading-4 text-[#5E6572]">
                      <span>{metric.unit}</span>
                      <ArrowUpRight
                        size={13}
                        className="text-[#868D98] transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-[#9F7C38]"
                      />
                    </p>
                  </div>

                  <div
                    className={`absolute bottom-0 left-5 h-[2.5px] w-10 rounded-full ${metric.accent}`}
                  />
                </Link>
              );
            })}
          </div>
        </section>

        {/* SIGNAL QUALITY */}
        <section>
          <SectionHeading
            eyebrow="Signal intelligence"
            title="Network quality"
          />

          <div className="mt-4 grid grid-cols-1 gap-3.5 lg:grid-cols-2">
            <Link
              href="/networks"
              className={`${panel} group flex min-w-0 items-center justify-between gap-4 p-5 transition hover:border-[#C6A15B]/55`}
            >
              <div className="flex min-w-0 items-center gap-3.5">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/25 bg-[#C6A15B]/12 text-[#9F7C38]">
                  <ShieldCheck size={20} />
                </div>

                <div className="min-w-0">
                  <p className="text-[12px] font-semibold text-[#303640] group-hover:text-[#9F7C38]">
                    Average network quality
                  </p>

                  <p className="mt-1 text-[10px] leading-relaxed text-[#5E6572]">
                    Based on recorded signal observations · View breakdown
                  </p>
                </div>
              </div>

              <div className="shrink-0 text-right">
                <p className="text-2xl font-semibold text-[#9F7C38]">
                  {data.qualityScore !== null
                    ? formatValue(data.qualityScore, 0)
                    : "—"}
                </p>

                <p className="mt-1 text-[9px] font-medium text-[#868D98]">
                  Out of 100
                </p>
              </div>
            </Link>

            <article className={`${panel} flex min-w-0 items-center justify-between gap-4 p-5`}>
              <div className="flex min-w-0 items-center gap-3.5">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-[#B83B3B]/20 bg-[#B83B3B]/10 text-[#B83B3B]">
                  <Signal size={20} />
                </div>

                <div className="min-w-0">
                  <p className="text-[12px] font-semibold text-[#303640]">
                    Signal extremes
                  </p>

                  <p className="mt-1 text-[10px] leading-relaxed text-[#5E6572]">
                    Strongest and weakest recorded
                  </p>
                </div>
              </div>

              <div className="shrink-0 space-y-2 text-right">
                <p className="text-[11px] font-semibold text-[#2E7D5B]">
                  {data.observations?.strongestSignal !== null &&
                  data.observations?.strongestSignal !== undefined &&
                  Number.isFinite(data.observations.strongestSignal)
                    ? `${formatValue(data.observations.strongestSignal, 0)} dBm`
                    : "—"}
                  <span className="ml-1.5 text-[9px] font-normal text-[#868D98]">
                    Best
                  </span>
                </p>

                <p className="text-[11px] font-semibold text-[#B83B3B]">
                  {data.observations?.weakestSignal !== null &&
                  data.observations?.weakestSignal !== undefined &&
                  Number.isFinite(data.observations.weakestSignal)
                    ? `${formatValue(data.observations.weakestSignal, 0)} dBm`
                    : "—"}
                  <span className="ml-1.5 text-[9px] font-normal text-[#868D98]">
                    Weakest
                  </span>
                </p>
              </div>
            </article>
          </div>
        </section>

        {/* WI-FI HEATMAP */}
        <section className={`${panel} min-w-0 overflow-hidden`}>
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#303640]/[0.08] bg-[#F1EFE9]/60 px-5 py-4 sm:px-6">
            <div className="flex min-w-0 items-center gap-3.5">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/30 bg-white text-[#9F7C38]">
                <Crosshair size={18} />
              </div>

              <div className="min-w-0">
                <p className="text-[12.5px] font-semibold text-[#303640]">
                  Wi-Fi Signal Heatmap
                </p>

                <p className="mt-0.5 text-[10px] leading-relaxed text-[#5E6572]">
                  Geographic view of recorded scan signals
                </p>
              </div>
            </div>

            <Link
              href="/heatmap"
              className="inline-flex shrink-0 items-center gap-2 rounded-lg border border-[#2E7D5B]/25 bg-[#2E7D5B]/[0.08] px-3 py-1.5 text-[10px] font-semibold text-[#2E7D5B] transition hover:border-[#2E7D5B]/50 hover:bg-[#2E7D5B]/15"
            >
              <span className="h-1.5 w-1.5 rounded-full bg-[#2E7D5B]" />
              Open Heatmap Explorer
              <ArrowUpRight size={12} />
            </Link>
          </div>

          <div className="min-w-0 p-3 sm:p-4">
            <WifiHeatmap />
          </div>
        </section>

        {/* PERFORMANCE AND SYSTEM */}
        <section className="grid grid-cols-1 gap-3.5 xl:grid-cols-5">
          <article className={`${panel} min-w-0 p-5 sm:p-6 xl:col-span-3`}>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-3.5">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/25 bg-[#C6A15B]/12 text-[#9F7C38]">
                  <Zap size={18} />
                </div>

                <div>
                  <h2 className="text-[13px] font-semibold text-[#303640]">
                    Internet performance
                  </h2>

                  <p className="mt-0.5 text-[10px] text-[#5E6572]">
                    Saved performance measurements
                  </p>
                </div>
              </div>

              <Link
                href="/performance"
                className="inline-flex items-center gap-1.5 rounded-lg border border-[#303640]/12 bg-[#F7F6F2] px-2.5 py-1.5 text-[10px] font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:text-[#9F7C38]"
              >
                Run Speed Test
                <ArrowUpRight size={13} />
              </Link>
            </div>

            <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
              {performanceItems.map((item) => {
                const Icon = item.icon;

                return (
                  <div
                    key={item.label}
                    className="min-w-0 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] p-4"
                  >
                    <div className="flex items-center gap-2">
                      <Icon size={14} className={item.color} />

                      <p className="text-[10px] font-semibold text-[#5E6572]">
                        {item.label}
                      </p>
                    </div>

                    <div className="mt-3.5 flex flex-wrap items-baseline gap-1.5">
                      <span className="text-[22px] font-semibold leading-none text-[#303640]">
                        {item.value}
                      </span>

                      <span className="text-[9px] font-medium text-[#868D98]">
                        {item.unit}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="mt-4 flex flex-wrap justify-between gap-2 border-t border-[#303640]/[0.08] pt-4">
              <p className="text-[10px] text-[#5E6572]">
                {hasPerformanceTests
                  ? `${data.performance?.totalTests} saved measurements`
                  : "No saved performance measurements"}
              </p>

              {hasPerformanceTests && (
                <p className="text-[10px] text-[#5E6572]">
                  Packet loss:{" "}
                  <span className="font-semibold text-[#303640]">
                    {formatValue(
                      data.performance?.averagePacketLossPercentage
                    )}
                    %
                  </span>
                </p>
              )}
            </div>
          </article>

          <article className={`${panel} min-w-0 p-5 sm:p-6 xl:col-span-2`}>
            <div className="flex flex-wrap items-center gap-3.5">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[#2E7D5B]/20 bg-[#2E7D5B]/10 text-[#2E7D5B]">
                <Activity size={18} />
              </div>

              <div>
                <h2 className="text-[13px] font-semibold text-[#303640]">
                  System status
                </h2>

                <p className="mt-0.5 text-[10px] text-[#5E6572]">
                  Platform connectivity
                </p>
              </div>

              <Link
                href="/monitoring"
                className={`ml-auto inline-flex items-center gap-1.5 rounded-lg border border-[#303640]/10 bg-[#F7F6F2] px-2.5 py-1 text-[10px] font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:text-[#9F7C38]`}
              >
                <span
                  className={`h-2 w-2 shrink-0 rounded-full ${
                    systemOnline
                      ? "bg-[#2E7D5B] shadow-[0_0_8px_rgba(46,125,91,0.5)]"
                      : "bg-[#B67B28]"
                  }`}
                />
                Details
              </Link>
            </div>

            <div className="mt-5 space-y-2.5">
              <div className="flex items-center justify-between gap-3 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] px-3.5 py-3">
                <span className="flex items-center gap-2.5 text-[11px] font-medium text-[#303640]">
                  <Wifi size={14} className="text-[#868D98]" />
                  Frontend
                </span>

                <span className="text-[10px] font-semibold text-[#2E7D5B]">
                  Running
                </span>
              </div>

              <div className="flex items-center justify-between gap-3 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] px-3.5 py-3">
                <span className="flex items-center gap-2.5 text-[11px] font-medium text-[#303640]">
                  <Radio size={14} className="text-[#868D98]" />
                  Backend API
                </span>

                <StatusIndicator status={data.backend} />
              </div>

              <div className="flex items-center justify-between gap-3 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] px-3.5 py-3">
                <span className="flex items-center gap-2.5 text-[11px] font-medium text-[#303640]">
                  <ShieldCheck size={14} className="text-[#868D98]" />
                  Database
                </span>

                <StatusIndicator status={data.database} />
              </div>
            </div>

            <p className="mt-4 text-[10px] leading-relaxed text-[#5E6572]">
              {systemOnline
                ? "All connected services are responding normally."
                : loading
                  ? "Checking service connectivity..."
                  : "One or more services are currently unavailable."}
            </p>
          </article>
        </section>

        {/* FOOTER */}
        <footer className="flex flex-col justify-between gap-2 border-t border-[#303640]/[0.08] pt-4 sm:flex-row sm:items-center">
          <p className="text-[10px] text-[#868D98]">
            WiFiVision Network Intelligence Platform
          </p>

          <div className="flex flex-wrap items-center gap-3">
            {lastUpdated && (
              <p className="text-[10px] text-[#868D98]">
                Last updated: {lastUpdated.toLocaleTimeString()}
              </p>
            )}

            <span className="text-[10px] font-semibold text-[#9F7C38]">
              V1.1.0
            </span>
          </div>
        </footer>

      </div>
    </DashboardShell>
  );
}