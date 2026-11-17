"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Radio,
  RefreshCw,
  ShieldCheck,
  Signal,
  Sparkles,
  Wifi,
} from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import DashboardShell from "@/components/layout/DashboardShell";
import {
  ANALYTICS_BAND_OPTIONS,
  type AnalyticsBandFilter,
  computeAnalyticsViewModel,
  getPersistedAnalyticsBand,
  persistAnalyticsBand,
} from "@/lib/analytics";
import {
  getNetworkQuality,
  getObservationAnalytics,
  getObservations,
  type NetworkObservation,
  type NetworkQualityResponse,
  type ObservationAnalytics,
} from "@/lib/dashboard";

function formatSignal(val: number | null | undefined): string {
  if (val === null || val === undefined || Number.isNaN(val)) return "—";
  return `${Math.round(val)} dBm`;
}

function formatScore(val: number | null | undefined): string {
  if (val === null || val === undefined || Number.isNaN(val)) return "—";
  return `${Math.round(val)} / 100`;
}

export default function AnalyticsPage() {
  const [analytics, setAnalytics] = useState<ObservationAnalytics | null>(null);
  const [overallAnalytics, setOverallAnalytics] =
    useState<ObservationAnalytics | null>(null);
  const [qualityData, setQualityData] = useState<NetworkQualityResponse | null>(null);
  const [observations, setObservations] = useState<NetworkObservation[]>([]);
  const [obsTotalCount, setObsTotalCount] = useState<number | null>(null);
  const [obsHasMore, setObsHasMore] = useState(false);
  const [selectedBand, setSelectedBand] = useState<AnalyticsBandFilter>(() =>
    getPersistedAnalyticsBand()
  );
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [partialWarnings, setPartialWarnings] = useState<string[]>([]);

  const activeBand: AnalyticsBandFilter = selectedBand;

  const handleBandSelect = useCallback((band: AnalyticsBandFilter) => {
    persistAnalyticsBand(band);
    setLoading(true);
    setSelectedBand(band);
  }, []);

  const fetchData = useCallback(async (band: AnalyticsBandFilter) => {
    try {
      const requests: [
        ReturnType<typeof getObservationAnalytics>,
        ReturnType<typeof getNetworkQuality>,
        ReturnType<typeof getObservations>,
        ReturnType<typeof getObservationAnalytics> | null,
      ] = [
        getObservationAnalytics(band === "all" ? undefined : band),
        getNetworkQuality(),
        getObservations({ page: 1, limit: 100 }),
        band === "all" ? null : getObservationAnalytics(),
      ];

      const [statsRes, qualityRes, obsRes, overallRes] =
        await Promise.allSettled([
          requests[0],
          requests[1],
          requests[2],
          requests[3] ?? Promise.resolve(null),
        ]);

      const warnings: string[] = [];

      if (statsRes.status === "fulfilled") {
        const nextAnalytics = statsRes.value.analytics ?? null;
        setAnalytics(nextAnalytics);
        if (band === "all") {
          setOverallAnalytics(nextAnalytics);
        }
      } else {
        setAnalytics(null);
        warnings.push("Observation statistics could not be loaded.");
      }

      if (
        band !== "all" &&
        overallRes.status === "fulfilled" &&
        overallRes.value
      ) {
        setOverallAnalytics(overallRes.value.analytics ?? null);
      }

      if (qualityRes.status === "fulfilled") {
        setQualityData(qualityRes.value);
      } else {
        setQualityData(null);
        warnings.push("Network quality scores could not be loaded.");
      }

      if (obsRes.status === "fulfilled") {
        const list = obsRes.value.observations ?? [];
        setObservations(list);
        setObsTotalCount(
          typeof obsRes.value.totalCount === "number"
            ? obsRes.value.totalCount
            : list.length
        );
        setObsHasMore(Boolean(obsRes.value.hasMore));
      } else {
        setObservations([]);
        setObsTotalCount(null);
        setObsHasMore(false);
        warnings.push("Observation records could not be loaded.");
      }

      if (
        statsRes.status === "rejected" &&
        qualityRes.status === "rejected" &&
        obsRes.status === "rejected"
      ) {
        setError("Unable to load Wi-Fi analytics from the backend API.");
        setPartialWarnings([]);
      } else {
        setError(null);
        setPartialWarnings(warnings);
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load network analytics."
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  const loadData = useCallback(
    (isRefresh = false) => {
      if (isRefresh) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);
      setPartialWarnings([]);
      void fetchData(selectedBand);
    },
    [fetchData, selectedBand]
  );

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(() => {
      if (active) {
        return fetchData(selectedBand);
      }
    });
    return () => {
      active = false;
    };
  }, [fetchData, selectedBand]);

  const {
    bandChartData,
    spectrumTotalScans,
    securityChartData,
    filteredQualityNetworks,
    filteredObservations,
    qualityDistribution,
    qualityComponentAverages,
    signalBuckets,
    filteredAvgQualityScore,
    sampleSignalSpan,
    bandScopeLabel,
    totalKnownObservations,
    qualitySampleCount,
    qualityIsPartialSample,
    obsIsPartialSample,
    isStatsUnavailable,
    isBandEmpty,
  } = useMemo(
    () =>
      computeAnalyticsViewModel({
        selectedBand: activeBand,
        analytics,
        overallAnalytics,
        qualityData,
        observations,
        obsTotalCount,
        obsHasMore,
      }),
    [
      activeBand,
      analytics,
      overallAnalytics,
      qualityData,
      observations,
      obsTotalCount,
      obsHasMore,
    ]
  );

  const cardClass =
    "rounded-2xl border border-[#303640]/[0.09] bg-white shadow-[0_2px_12px_rgba(48,54,64,0.04)]";

  return (
    <DashboardShell>
      <div className="space-y-6">
        <div className="flex flex-col gap-4 border-b border-[#303640]/[0.08] pb-5 lg:flex-row lg:items-end lg:justify-between">
          <div className="min-w-0">
            <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-[#9F7C38]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#C6A15B]" />
              Spectrum &amp; Security Telemetry
            </div>
            <h1 className="mt-1.5 text-2xl font-semibold tracking-tight text-[#303640] sm:text-[26px]">
              Network Analytics
            </h1>
            <p className="mt-1 text-sm text-[#5E6572]">
              Live breakdown of observed frequency bands, encryption protocols,
              signal-strength distribution, and computed quality classifications.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div
              role="group"
              aria-label="Frequency band filter"
              className="inline-flex min-h-10 flex-wrap items-center gap-1 rounded-xl border border-[#303640]/12 bg-white p-1 shadow-[0_1px_2px_rgba(48,54,64,0.03)]"
            >
              {ANALYTICS_BAND_OPTIONS.map((band) => (
                <button
                  key={band}
                  type="button"
                  suppressHydrationWarning
                  aria-pressed={activeBand === band}
                  onClick={() => handleBandSelect(band)}
                  className={`rounded-lg px-2.5 py-1.5 text-xs font-semibold transition ${
                    activeBand === band
                      ? "bg-[#303640] text-white shadow-xs"
                      : "text-[#5E6572] hover:bg-[#F1EFE9] hover:text-[#303640]"
                  }`}
                >
                  {band === "all" ? "All Bands" : band}
                </button>
              ))}
            </div>

            <button
              type="button"
              onClick={() => void loadData(true)}
              disabled={loading || refreshing}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-[#303640] px-4 text-sm font-semibold text-white shadow-xs transition hover:bg-[#3F4754] disabled:cursor-not-allowed disabled:opacity-60"
            >
              <RefreshCw
                className={`h-4 w-4 text-[#C6A15B] ${refreshing ? "animate-spin" : ""}`}
              />
              {refreshing ? "Refreshing..." : "Refresh Data"}
            </button>
          </div>
        </div>

        {error && (
          <div className="rounded-2xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.06] px-4 py-3 text-sm text-[#B83B3B]">
            {error}
          </div>
        )}

        {partialWarnings.length > 0 && !error && (
          <div className="rounded-2xl border border-[#B67B28]/30 bg-[#B67B28]/[0.07] px-4 py-3 text-sm text-[#8E5D1A]">
            {partialWarnings.join(" ")}
          </div>
        )}

        {isBandEmpty && !loading && !error && (
          <div className="rounded-2xl border border-[#303640]/12 bg-[#F7F6F2] px-4 py-3 text-sm text-[#5E6572]">
            No recorded scans matched{" "}
            <span className="font-semibold text-[#303640]">{activeBand}</span>.
            Band-filtered KPIs and charts below remain empty while the overall
            spectrum aggregate shows all {spectrumTotalScans} total scans.
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <div className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wider text-[#868D98]">
              <span>Observed Networks</span>
              <Wifi className="h-4 w-4 text-[#9F7C38]" />
            </div>
            <p className="mt-2.5 text-2xl font-semibold tabular-nums text-[#303640]">
              {loading || !analytics ? "—" : analytics.totalNetworks}
            </p>
            <p className="mt-1 text-xs text-[#5E6572]">
              {isStatsUnavailable
                ? `Stats unavailable (${bandScopeLabel})`
                : activeBand === "all"
                  ? "All recorded scans"
                  : `All ${activeBand} scans`}
            </p>
          </div>

          <div className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wider text-[#868D98]">
              <span>Average Signal</span>
              <Signal className="h-4 w-4 text-[#9F7C38]" />
            </div>
            <p className="mt-2.5 text-2xl font-semibold tabular-nums text-[#303640]">
              {loading || !analytics ? "—" : formatSignal(analytics.averageSignal)}
            </p>
            <p className="mt-1 text-xs text-[#5E6572]">
              {isStatsUnavailable
                ? `Stats unavailable (${bandScopeLabel})`
                : `Mean RSSI (${bandScopeLabel})`}
            </p>
          </div>

          <div className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wider text-[#868D98]">
              <span>Strongest Signal</span>
              <Radio className="h-4 w-4 text-[#2E7D5B]" />
            </div>
            <p className="mt-2.5 text-2xl font-semibold tabular-nums text-[#2E7D5B]">
              {loading || !analytics
                ? "—"
                : formatSignal(analytics.strongestSignal)}
            </p>
            <p className="mt-1 text-xs text-[#5E6572]">
              {isStatsUnavailable
                ? `Stats unavailable (${bandScopeLabel})`
                : `Peak RSSI (${bandScopeLabel})`}
            </p>
          </div>

          <div className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wider text-[#868D98]">
              <span>Weakest Signal</span>
              <Radio className="h-4 w-4 text-[#B83B3B]" />
            </div>
            <p className="mt-2.5 text-2xl font-semibold tabular-nums text-[#B83B3B]">
              {loading || !analytics ? "—" : formatSignal(analytics.weakestSignal)}
            </p>
            <p className="mt-1 text-xs text-[#5E6572]">
              {isStatsUnavailable
                ? `Stats unavailable (${bandScopeLabel})`
                : `Lowest RSSI (${bandScopeLabel})`}
            </p>
          </div>

          <div className={`${cardClass} p-5 sm:col-span-2 lg:col-span-1`}>
            <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wider text-[#868D98]">
              <span>Avg Quality Score</span>
              <Sparkles className="h-4 w-4 text-[#9F7C38]" />
            </div>
            <p className="mt-2.5 text-2xl font-semibold tabular-nums text-[#303640]">
              {loading ? "—" : formatScore(filteredAvgQualityScore)}
            </p>
            <p className="mt-1 text-xs text-[#5E6572]">
              {qualityIsPartialSample
                ? `Latest ${qualitySampleCount} of ${totalKnownObservations} (${bandScopeLabel})`
                : `${filteredQualityNetworks.length} scored (${bandScopeLabel})`}
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          {/* Band Distribution (Always all-band spectrum aggregate) */}
          <section className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-base font-semibold text-[#303640]">
                    Frequency Band Distribution
                  </h2>
                  <span className="rounded-md bg-[#F1EFE9] px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-[#9F7C38]">
                    All-Band Aggregate
                  </span>
                </div>
                <p className="mt-0.5 text-xs text-[#5E6572]">
                  All-band spectrum breakdown ({spectrumTotalScans} total scans ·
                  unaffected by band filter)
                </p>
              </div>
              <Radio className="h-4 w-4 shrink-0 text-[#9F7C38]" />
            </div>

            {loading ? (
              <div className="mt-6 h-64 animate-pulse rounded-xl bg-[#F1EFE9]" />
            ) : bandChartData.length === 0 ? (
              <div className="mt-6 flex h-64 items-center justify-center rounded-xl border border-dashed border-[#303640]/15 bg-[#F7F6F2] px-4 text-center text-sm text-[#5E6572]">
                No frequency band records have been collected yet.
              </div>
            ) : (
              <div className="mt-5 grid grid-cols-1 items-center gap-4 sm:grid-cols-5">
                <div className="h-56 sm:col-span-3">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={bandChartData}
                        dataKey="count"
                        nameKey="name"
                        innerRadius={50}
                        outerRadius={82}
                        paddingAngle={3}
                      >
                        {bandChartData.map((entry) => (
                          <Cell
                            key={entry.name}
                            fill={entry.color}
                            fillOpacity={
                              activeBand === "all" || entry.isSelected ? 1 : 0.35
                            }
                          />
                        ))}
                      </Pie>
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#FFFFFF",
                          borderColor: "rgba(48,54,64,0.12)",
                          borderRadius: "12px",
                          color: "#303640",
                          fontSize: "12px",
                        }}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                </div>

                <div className="space-y-2.5 sm:col-span-2">
                  {bandChartData.map((item) => (
                    <div
                      key={item.name}
                      className={`flex items-center justify-between rounded-xl border px-3 py-2 text-xs transition ${
                        item.isSelected
                          ? "border-[#C6A15B]/60 bg-[#FBF9F4] ring-1 ring-[#C6A15B]/30"
                          : "border-[#303640]/[0.07] bg-[#F7F6F2]"
                      }`}
                    >
                      <span className="flex items-center gap-2 font-medium text-[#303640]">
                        <span
                          className="h-2.5 w-2.5 rounded-full"
                          style={{ backgroundColor: item.color }}
                        />
                        {item.name}
                      </span>
                      <span className="tabular-nums text-[#5E6572]">
                        {item.count} ({item.percentage}%)
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </section>

          {/* Security Protocol Distribution */}
          <section className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-[#303640]">
                  Security Protocol Distribution
                </h2>
                <p className="text-xs text-[#5E6572]">
                  Complete aggregate of encryption suites ({bandScopeLabel},{" "}
                  {analytics?.totalNetworks ?? 0} scans)
                </p>
              </div>
              <ShieldCheck className="h-4 w-4 shrink-0 text-[#9F7C38]" />
            </div>

            {loading ? (
              <div className="mt-6 h-64 animate-pulse rounded-xl bg-[#F1EFE9]" />
            ) : securityChartData.length === 0 ? (
              <div className="mt-6 flex h-64 items-center justify-center rounded-xl border border-dashed border-[#303640]/15 bg-[#F7F6F2] px-4 text-center text-sm text-[#5E6572]">
                {activeBand === "all"
                  ? "No security protocol statistics available yet."
                  : `No security protocol records found for ${activeBand}.`}
              </div>
            ) : (
              <div className="mt-5 h-56">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={securityChartData}
                    margin={{ top: 10, right: 12, left: -12, bottom: 0 }}
                  >
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="rgba(48,54,64,0.08)"
                      vertical={false}
                    />
                    <XAxis
                      dataKey="name"
                      tick={{ fill: "#5E6572", fontSize: 11 }}
                      axisLine={{ stroke: "rgba(48,54,64,0.12)" }}
                      tickLine={false}
                    />
                    <YAxis
                      allowDecimals={false}
                      tick={{ fill: "#5E6572", fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "#FFFFFF",
                        borderColor: "rgba(48,54,64,0.12)",
                        borderRadius: "12px",
                        color: "#303640",
                        fontSize: "12px",
                      }}
                    />
                    <Bar dataKey="count" name="Networks" radius={[8, 8, 0, 0]}>
                      {securityChartData.map((entry) => (
                        <Cell key={entry.name} fill={entry.color} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>

          {/* Network Quality Distribution */}
          <section className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-[#303640]">
                  Network Quality Classification
                </h2>
                <p className="text-xs text-[#5E6572]">
                  Score distribution ({bandScopeLabel}) from{" "}
                  {qualityIsPartialSample
                    ? `latest ${qualitySampleCount} of ${totalKnownObservations}`
                    : `${qualitySampleCount}`}{" "}
                  scans via GET /api/quality
                </p>
              </div>
              <Sparkles className="h-4 w-4 shrink-0 text-[#9F7C38]" />
            </div>

            {loading ? (
              <div className="mt-6 h-64 animate-pulse rounded-xl bg-[#F1EFE9]" />
            ) : qualityDistribution.length === 0 ? (
              <div className="mt-6 flex h-64 items-center justify-center rounded-xl border border-dashed border-[#303640]/15 bg-[#F7F6F2] px-4 text-center text-sm text-[#5E6572]">
                {activeBand === "all"
                  ? "No scored network observations available yet."
                  : `No scored ${activeBand} observations found in latest ${qualitySampleCount} scans.`}
              </div>
            ) : (
              <div className="mt-5 space-y-5">
                <div className="h-52">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={qualityDistribution}
                      margin={{ top: 10, right: 12, left: -12, bottom: 0 }}
                    >
                      <CartesianGrid
                        strokeDasharray="3 3"
                        stroke="rgba(48,54,64,0.08)"
                        vertical={false}
                      />
                      <XAxis
                        dataKey="name"
                        tick={{ fill: "#5E6572", fontSize: 12 }}
                        axisLine={{ stroke: "rgba(48,54,64,0.12)" }}
                        tickLine={false}
                      />
                      <YAxis
                        allowDecimals={false}
                        tick={{ fill: "#5E6572", fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                      />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#FFFFFF",
                          borderColor: "rgba(48,54,64,0.12)",
                          borderRadius: "12px",
                          color: "#303640",
                          fontSize: "12px",
                        }}
                      />
                      <Bar dataKey="count" name="Networks" radius={[8, 8, 0, 0]}>
                        {qualityDistribution.map((entry) => (
                          <Cell key={entry.name} fill={entry.color} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>

                {qualityComponentAverages && (
                  <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
                    {qualityComponentAverages.map((comp) => (
                      <div
                        key={comp.metric}
                        className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-2.5 text-center"
                      >
                        <p className="text-[11px] font-medium text-[#5E6572]">
                          {comp.metric}
                        </p>
                        <p className="mt-1 text-sm font-semibold tabular-nums text-[#303640]">
                          {comp.avg} / {comp.max}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </section>

          {/* Signal Strength Breakdown */}
          <section className={`${cardClass} p-5`}>
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-semibold text-[#303640]">
                  Signal Strength Distribution
                </h2>
                <p className="text-xs text-[#5E6572]">
                  RSSI buckets ({bandScopeLabel}) from{" "}
                  {obsIsPartialSample
                    ? `latest ${observations.length} of ${totalKnownObservations}`
                    : `all ${observations.length}`}{" "}
                  scans
                </p>
              </div>
              <Signal className="h-4 w-4 text-[#9F7C38]" />
            </div>

            {loading ? (
              <div className="mt-6 h-64 animate-pulse rounded-xl bg-[#F1EFE9]" />
            ) : signalBuckets.length === 0 ? (
              <div className="mt-6 flex h-64 items-center justify-center rounded-xl border border-dashed border-[#303640]/15 bg-[#F7F6F2] px-4 text-center text-sm text-[#5E6572]">
                {activeBand === "all"
                  ? "No observation scans available to compute signal distribution."
                  : `No ${activeBand} scans found in latest ${observations.length} loaded observations.`}
              </div>
            ) : (
              <div className="mt-5 space-y-4">
                <div className="h-52">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={signalBuckets}
                      layout="vertical"
                      margin={{ top: 4, right: 16, left: 0, bottom: 4 }}
                    >
                      <CartesianGrid
                        strokeDasharray="3 3"
                        stroke="rgba(48,54,64,0.08)"
                        horizontal={false}
                      />
                      <XAxis
                        type="number"
                        allowDecimals={false}
                        tick={{ fill: "#5E6572", fontSize: 11 }}
                        axisLine={{ stroke: "rgba(48,54,64,0.12)" }}
                        tickLine={false}
                      />
                      <YAxis
                        type="category"
                        dataKey="label"
                        width={126}
                        tick={{ fill: "#303640", fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                      />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#FFFFFF",
                          borderColor: "rgba(48,54,64,0.12)",
                          borderRadius: "12px",
                          color: "#303640",
                          fontSize: "12px",
                        }}
                      />
                      <Bar dataKey="count" name="Observations" radius={[0, 8, 8, 0]}>
                        {signalBuckets.map((entry) => (
                          <Cell key={entry.label} fill={entry.color} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>

                <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9]/80 px-3.5 py-2.5 text-xs text-[#5E6572]">
                  Sample RSSI span ({bandScopeLabel}):{" "}
                  <span className="font-semibold text-[#303640]">
                    {formatSignal(sampleSignalSpan.weakest)}
                  </span>{" "}
                  to{" "}
                  <span className="font-semibold text-[#303640]">
                    {formatSignal(sampleSignalSpan.strongest)}
                  </span>{" "}
                  · Showing{" "}
                  <span className="font-semibold text-[#303640]">
                    {filteredObservations.length}
                  </span>{" "}
                  {activeBand === "all" ? "" : `${activeBand} `}
                  record(s) from{" "}
                  {obsIsPartialSample
                    ? `latest ${observations.length} of ${totalKnownObservations} total`
                    : `all ${totalKnownObservations}`}.
                </div>
              </div>
            )}
          </section>
        </div>
      </div>
    </DashboardShell>
  );
}
