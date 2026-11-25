"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Activity,
  AlertCircle,
  ArrowDownToLine,
  ArrowUpFromLine,
  CheckCircle2,
  Clock3,
  Gauge,
  Loader2,
  Play,
  RefreshCw,
  WifiOff,
  Zap,
} from "lucide-react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import DashboardShell from "@/components/layout/DashboardShell";
import {
  getPerformanceAnalytics,
  getPerformanceHistory,
  getPerformanceTrends,
  runPerformanceTest,
  type PerformanceAnalytics,
  type PerformanceMeasurementRecord,
  type PerformanceTestResult,
  type PerformanceTrendPoint,
} from "@/lib/dashboard";
import {
  getLocalPreferences,
  type PerformanceHistoryLimit,
} from "@/lib/preferences";

function fmt(value: number | null | undefined, digits = 2): string {
  if (typeof value !== "number" || !Number.isFinite(value)) return "—";
  return value.toFixed(digits);
}

function fmtShortTime(iso?: string): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function PerformancePage() {
  const [analytics, setAnalytics] = useState<PerformanceAnalytics | null>(null);
  const [history, setHistory] = useState<PerformanceMeasurementRecord[]>([]);
  const [trends, setTrends] = useState<PerformanceTrendPoint[]>([]);
  const [latestResult, setLatestResult] =
    useState<PerformanceTestResult | null>(null);
  const [historyLimit, setHistoryLimit] = useState<PerformanceHistoryLimit>(
    () => getLocalPreferences().performanceHistoryLimit
  );

  const [loading, setLoading] = useState(true);
  const [runningTest, setRunningTest] = useState(false);
  const testInFlightRef = useRef(false);
  const [error, setError] = useState("");
  const [testError, setTestError] = useState("");
  const [testMessage, setTestMessage] = useState("");

  const fetchPerformanceData = useCallback(
    async (limit = historyLimit) => {
      const [analyticsRes, historyRes, trendsRes] = await Promise.allSettled([
        getPerformanceAnalytics(),
        getPerformanceHistory(limit),
        getPerformanceTrends(limit),
      ]);

      if (analyticsRes.status === "fulfilled") {
        setError("");
        setAnalytics(analyticsRes.value.analytics ?? null);
      } else {
        setAnalytics(null);
        setError(
          analyticsRes.reason instanceof Error
            ? analyticsRes.reason.message
            : "Unable to load performance analytics."
        );
      }

      if (historyRes.status === "fulfilled") {
        setHistory(
          Array.isArray(historyRes.value.history)
            ? historyRes.value.history
            : []
        );
      } else {
        setHistory([]);
      }

      if (trendsRes.status === "fulfilled") {
        setTrends(
          Array.isArray(trendsRes.value.trends) ? trendsRes.value.trends : []
        );
      } else {
        setTrends([]);
      }

      setLoading(false);
    },
    [historyLimit]
  );

  const loadPerformanceData = useCallback(
    async (limit = historyLimit) => {
      setLoading(true);
      setError("");
      await fetchPerformanceData(limit);
    },
    [fetchPerformanceData, historyLimit]
  );

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(() => {
      if (active) {
        return fetchPerformanceData(historyLimit);
      }
    });
    return () => {
      active = false;
    };
  }, [fetchPerformanceData, historyLimit]);

  async function handleRunTest() {
    if (testInFlightRef.current || runningTest) return;

    testInFlightRef.current = true;
    setRunningTest(true);
    setTestError("");
    setTestMessage("");

    try {
      const response = await runPerformanceTest();
      setLatestResult(response.performance);
      setTestMessage(
        response.message || "Performance measured and saved successfully."
      );
      await loadPerformanceData(historyLimit);
    } catch (err) {
      setTestError(
        err instanceof Error
          ? err.message
          : "Unable to complete performance test right now."
      );
    } finally {
      testInFlightRef.current = false;
      setRunningTest(false);
    }
  }

  const chartData = useMemo(
    () =>
      trends.map((item) => ({
        label: fmtShortTime(item.measuredAt),
        downloadMbps: item.downloadMbps,
        uploadMbps: item.uploadMbps,
        latencyMs: item.latencyMs,
        jitterMs: item.jitterMs,
        packetLossPercentage: item.packetLossPercentage,
      })),
    [trends]
  );

  return (
    <DashboardShell>
      <div className="w-full min-w-0 space-y-6">
        {/* Header */}
        <section className="flex flex-col justify-between gap-4 border-b border-[#303640]/[0.09] pb-5 lg:flex-row lg:items-center">
          <div className="min-w-0">
            <div className="mb-1.5 flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full bg-[#C6A15B]" />
              <p className="text-[9px] font-bold uppercase tracking-[0.2em] text-[#9F7C38]">
                Throughput, Latency & Packet Loss
              </p>
            </div>
            <h1 className="text-2xl font-semibold tracking-tight text-[#303640] sm:text-[26px]">
              Performance Analytics
            </h1>
            <p className="mt-1 max-w-2xl text-xs leading-relaxed text-[#5E6572]">
              Measure live download, upload, latency, jitter, and 20-packet
              delivery stability via the backend measurement engine and inspect
              chronological history.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <select
              value={historyLimit}
              onChange={(event) =>
                setHistoryLimit(Number(event.target.value) as PerformanceHistoryLimit)
              }
              disabled={loading || runningTest}
              aria-label="Measurement history window"
              className="h-10 rounded-xl border border-[#303640]/12 bg-white px-3 text-xs font-semibold text-[#303640] outline-none transition focus:border-[#C6A15B] disabled:opacity-50"
            >
              <option value={20}>Last 20 runs</option>
              <option value={50}>Last 50 runs</option>
              <option value={100}>Last 100 runs</option>
            </select>

            <button
              type="button"
              onClick={() => void loadPerformanceData(historyLimit)}
              disabled={loading || runningTest}
              className="inline-flex h-10 items-center gap-2 rounded-xl border border-[#303640]/12 bg-white px-4 text-xs font-semibold text-[#303640] shadow-xs transition hover:border-[#C6A15B] hover:bg-[#ECE9E0]/60 disabled:opacity-50"
            >
              <RefreshCw
                size={13}
                className={`${loading ? "animate-spin" : ""} text-[#9F7C38]`}
              />
              Refresh
            </button>

            <button
              type="button"
              onClick={() => void handleRunTest()}
              disabled={runningTest}
              className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-[#303640] px-4 text-xs font-semibold text-white shadow-xs transition hover:bg-[#3F4754] disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
            >
              {runningTest ? (
                <>
                  <Loader2
                    size={14}
                    className="animate-spin text-[#C6A15B]"
                  />
                  Running Speed & Packet Loss Test...
                </>
              ) : (
                <>
                  <Play size={14} className="text-[#C6A15B]" />
                  Run Speed & Packet Loss Test
                </>
              )}
            </button>
          </div>
        </section>

        {/* Live test progress / result feedback */}
        {runningTest && (
          <div className="flex items-center gap-3 rounded-2xl border border-[#C6A15B]/35 bg-[#F1EFE9] px-4 py-3.5 text-xs text-[#303640]">
            <Loader2 size={16} className="shrink-0 animate-spin text-[#9F7C38]" />
            <span>
              Measuring download throughput, upload throughput, latency, jitter,
              and 20-packet loss from the backend server. Please wait...
            </span>
          </div>
        )}

        {testError && (
          <div
            role="alert"
            className="flex items-center gap-3 rounded-2xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.07] px-4 py-3.5 text-xs font-medium text-[#B83B3B]"
          >
            <AlertCircle size={17} className="shrink-0" />
            <span>{testError}</span>
          </div>
        )}

        {latestResult && (
          <section className="rounded-2xl border border-[#2E7D5B]/30 bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#303640]/[0.08] pb-3">
              <div className="flex items-center gap-2 text-xs font-semibold text-[#2E7D5B]">
                <CheckCircle2 size={16} />
                <span>{testMessage || "Latest measurement recorded"}</span>
              </div>
              <span className="text-[11px] text-[#5E6572]">
                {fmtShortTime(latestResult.measuredAt)}
              </span>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-5">
              <div className="rounded-xl bg-[#F1EFE9] p-3">
                <span className="text-[10px] font-bold uppercase text-[#868D98]">
                  Download
                </span>
                <p className="mt-1 text-lg font-bold text-[#2E7D5B]">
                  {fmt(latestResult.downloadMbps)} Mbps
                </p>
              </div>
              <div className="rounded-xl bg-[#F1EFE9] p-3">
                <span className="text-[10px] font-bold uppercase text-[#868D98]">
                  Upload
                </span>
                <p className="mt-1 text-lg font-bold text-[#303640]">
                  {fmt(latestResult.uploadMbps)} Mbps
                </p>
              </div>
              <div className="rounded-xl bg-[#F1EFE9] p-3">
                <span className="text-[10px] font-bold uppercase text-[#868D98]">
                  Latency
                </span>
                <p className="mt-1 text-lg font-bold text-[#9F7C38]">
                  {fmt(latestResult.latencyMs)} ms
                </p>
              </div>
              <div className="rounded-xl bg-[#F1EFE9] p-3">
                <span className="text-[10px] font-bold uppercase text-[#868D98]">
                  Jitter
                </span>
                <p className="mt-1 text-lg font-bold text-[#303640]">
                  {fmt(latestResult.jitterMs)} ms
                </p>
              </div>
              <div className="rounded-xl bg-[#F1EFE9] p-3">
                <span className="text-[10px] font-bold uppercase text-[#868D98]">
                  Packet Loss
                </span>
                <p className="mt-1 text-lg font-bold text-[#303640]">
                  {fmt(latestResult.packetLoss.packetLossPercentage, 1)}%
                </p>
              </div>
            </div>
          </section>
        )}

        {error && (
          <div
            role="alert"
            className="flex items-center gap-3 rounded-2xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.07] px-4 py-3.5 text-xs font-medium text-[#B83B3B]"
          >
            <AlertCircle size={17} className="shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Latest Measurement KPI cards */}
        <section className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-3">
          {[
            {
              label: "Latest Download",
              value:
                (latestResult ?? history[0])
                  ? `${fmt((latestResult ?? history[0])?.downloadMbps)} Mbps`
                  : "Not measured",
              sub: "How quickly data comes to your device",
              icon: ArrowDownToLine,
              accent: "text-[#2E7D5B] bg-[#2E7D5B]/10",
            },
            {
              label: "Latest Upload",
              value:
                (latestResult ?? history[0])
                  ? `${fmt((latestResult ?? history[0])?.uploadMbps)} Mbps`
                  : "Not measured",
              sub: "How quickly your device sends data",
              icon: ArrowUpFromLine,
              accent: "text-[#303640] bg-[#F1EFE9]",
            },
            {
              label: "Latest Latency",
              value:
                (latestResult ?? history[0])
                  ? `${fmt((latestResult ?? history[0])?.latencyMs)} ms`
                  : "Not measured",
              sub: "Lower is better — server response speed",
              icon: Clock3,
              accent: "text-[#9F7C38] bg-[#C6A15B]/15",
            },
            {
              label: "Latest Jitter",
              value:
                (latestResult ?? history[0])
                  ? `${fmt((latestResult ?? history[0])?.jitterMs)} ms`
                  : "Not measured",
              sub: "Lower is better — response steadiness",
              icon: Activity,
              accent: "text-[#9F7C38] bg-[#C6A15B]/15",
            },
            {
              label: "Packet Loss",
              value:
                (latestResult ?? history[0])
                  ? `${fmt((latestResult ?? history[0])?.packetLoss?.packetLossPercentage)}%`
                  : "Not measured",
              sub: "0% is ideal — connection reliability",
              icon: WifiOff,
              accent: "text-[#B83B3B] bg-[#B83B3B]/10",
            },
            {
              label: "Last Measured",
              value:
                (latestResult ?? history[0])
                  ? fmtShortTime((latestResult ?? history[0])?.measuredAt)
                  : "Not measured",
              sub: `Total tests recorded: ${analytics?.totalTests ?? history.length}`,
              icon: Gauge,
              accent: "text-[#303640] bg-[#F1EFE9]",
            },
          ].map((card) => {
            const Icon = card.icon;
            return (
              <article
                key={card.label}
                className="rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_10px_rgba(48,54,64,0.03)]"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#868D98]">
                    {card.label}
                  </span>
                  <span
                    className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${card.accent}`}
                  >
                    <Icon size={16} />
                  </span>
                </div>
                <p className="mt-3 text-xl font-semibold tabular-nums text-[#303640]">
                  {card.value}
                </p>
                <p className="mt-1 text-[11px] text-[#5E6572]">{card.sub}</p>
              </article>
            );
          })}
        </section>

        {/* Trend charts (Recharts) */}
        <section className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <article className="rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h2 className="text-sm font-semibold text-[#303640]">
                  Throughput Trend
                </h2>
                <p className="text-[11px] text-[#5E6572]">
                  Chronological download & upload speeds (Mbps)
                </p>
              </div>
              <Zap size={16} className="text-[#C6A15B]" />
            </div>

            {chartData.length < 2 ? (
              <div className="flex h-[250px] items-center justify-center rounded-xl bg-[#F1EFE9] text-xs text-[#5E6572]">
                Not enough measurements yet
              </div>
            ) : (
              <div className="h-[260px] w-full min-w-0">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart
                    data={chartData}
                    margin={{ top: 10, right: 14, left: -10, bottom: 4 }}
                  >
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="rgba(48, 54, 64, 0.08)"
                      vertical={false}
                    />
                    <XAxis
                      dataKey="label"
                      tick={{ fontSize: 10, fill: "#5E6572" }}
                      axisLine={{ stroke: "rgba(48, 54, 64, 0.12)" }}
                      tickLine={false}
                      minTickGap={16}
                    />
                    <YAxis
                      width={44}
                      tick={{ fontSize: 10, fill: "#5E6572" }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "#FFFFFF",
                        borderColor: "rgba(48,54,64,0.12)",
                        borderRadius: "12px",
                        fontSize: "12px",
                        color: "#303640",
                      }}
                    />
                    <Legend wrapperStyle={{ fontSize: "11px" }} />
                    <Area
                      type="monotone"
                      dataKey="downloadMbps"
                      name="Download (Mbps)"
                      stroke="#2E7D5B"
                      fill="#2E7D5B"
                      fillOpacity={0.14}
                      strokeWidth={2}
                    />
                    <Area
                      type="monotone"
                      dataKey="uploadMbps"
                      name="Upload (Mbps)"
                      stroke="#C6A15B"
                      fill="#C6A15B"
                      fillOpacity={0.16}
                      strokeWidth={2}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            )}
          </article>

          <article className="rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h2 className="text-sm font-semibold text-[#303640]">
                  Latency, Jitter & Packet Loss
                </h2>
                <p className="text-[11px] text-[#5E6572]">
                  Response stability across saved test runs
                </p>
              </div>
              <Activity size={16} className="text-[#9F7C38]" />
            </div>

            {chartData.length < 2 ? (
              <div className="flex h-[250px] items-center justify-center rounded-xl bg-[#F1EFE9] text-xs text-[#5E6572]">
                Not enough measurements yet
              </div>
            ) : (
              <div className="h-[260px] w-full min-w-0">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart
                    data={chartData}
                    margin={{ top: 10, right: 14, left: -10, bottom: 4 }}
                  >
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="rgba(48, 54, 64, 0.08)"
                      vertical={false}
                    />
                    <XAxis
                      dataKey="label"
                      tick={{ fontSize: 10, fill: "#5E6572" }}
                      axisLine={{ stroke: "rgba(48, 54, 64, 0.12)" }}
                      tickLine={false}
                      minTickGap={16}
                    />
                    <YAxis
                      width={44}
                      tick={{ fontSize: 10, fill: "#5E6572" }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "#FFFFFF",
                        borderColor: "rgba(48,54,64,0.12)",
                        borderRadius: "12px",
                        fontSize: "12px",
                        color: "#303640",
                      }}
                    />
                    <Legend wrapperStyle={{ fontSize: "11px" }} />
                    <Line
                      type="monotone"
                      dataKey="latencyMs"
                      name="Latency (ms)"
                      stroke="#303640"
                      strokeWidth={2}
                      dot={{ r: 3 }}
                    />
                    <Line
                      type="monotone"
                      dataKey="jitterMs"
                      name="Jitter (ms)"
                      stroke="#C6A15B"
                      strokeWidth={2}
                      dot={{ r: 3 }}
                    />
                    <Line
                      type="monotone"
                      dataKey="packetLossPercentage"
                      name="Packet Loss (%)"
                      stroke="#B83B3B"
                      strokeWidth={2}
                      dot={{ r: 3 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
          </article>
        </section>

        {/* Historical Measurements Table */}
        <section className="overflow-hidden rounded-2xl border border-[#303640]/[0.09] bg-white shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
          <div className="flex items-center justify-between border-b border-[#303640]/[0.08] px-5 py-4">
            <div>
              <h2 className="text-sm font-semibold text-[#303640]">
                Measurement History
              </h2>
              <p className="text-[11px] text-[#5E6572]">
                Latest {history.length} saved speed & packet loss tests
              </p>
            </div>
          </div>

          {!loading && history.length === 0 ? (
            <div className="p-10 text-center">
              <Gauge size={26} className="mx-auto text-[#868D98]" />
              <p className="mt-3 text-sm font-semibold text-[#303640]">
                No performance measurements saved yet
              </p>
              <p className="mt-1 text-xs text-[#5E6572]">
                Click &ldquo;Run Speed &amp; Packet Loss Test&rdquo; above to
                record a live measurement.
              </p>
            </div>
          ) : (
            <>
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full min-w-[740px] border-collapse text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#303640]/[0.08] bg-[#F1EFE9]/60 text-[10px] font-bold uppercase tracking-wider text-[#5E6572]">
                      <th className="px-5 py-3.5">Measured At</th>
                      <th className="px-4 py-3.5">Download</th>
                      <th className="px-4 py-3.5">Upload</th>
                      <th className="px-4 py-3.5">Latency</th>
                      <th className="px-4 py-3.5">Jitter</th>
                      <th className="px-4 py-3.5">Packet Loss</th>
                      <th className="px-5 py-3.5">Server / Source</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#303640]/[0.06]">
                    {history.map((item) => (
                      <tr
                        key={item._id}
                        className="transition hover:bg-[#ECE9E0]/45"
                      >
                        <td className="px-5 py-3.5 text-[#303640]">
                          {fmtShortTime(item.measuredAt)}
                        </td>
                        <td className="px-4 py-3.5 font-semibold tabular-nums text-[#2E7D5B]">
                          {fmt(item.downloadMbps)} Mbps
                        </td>
                        <td className="px-4 py-3.5 font-semibold tabular-nums text-[#303640]">
                          {fmt(item.uploadMbps)} Mbps
                        </td>
                        <td className="px-4 py-3.5 font-semibold tabular-nums text-[#9F7C38]">
                          {fmt(item.latencyMs)} ms
                        </td>
                        <td className="px-4 py-3.5 tabular-nums text-[#5E6572]">
                          {fmt(item.jitterMs)} ms
                        </td>
                        <td className="px-4 py-3.5 tabular-nums">
                          <span
                            className={`font-semibold ${
                              (item.packetLoss?.packetLossPercentage ?? 0) > 0
                                ? "text-[#B83B3B]"
                                : "text-[#2E7D5B]"
                            }`}
                          >
                            {fmt(item.packetLoss?.packetLossPercentage, 1)}%
                          </span>
                          <span className="ml-1.5 text-[10px] text-[#868D98]">
                            ({item.packetLoss?.lostPackets ?? 0}/
                            {item.packetLoss?.totalPackets ?? 0} lost)
                          </span>
                        </td>
                        <td className="px-5 py-3.5 text-[#5E6572]">
                          {item.testServer || "Cloudflare"} · {item.source}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="divide-y divide-[#303640]/[0.07] md:hidden">
                {history.map((item) => (
                  <div key={item._id} className="space-y-2.5 p-4 text-xs">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-[#303640]">
                        {fmtShortTime(item.measuredAt)}
                      </span>
                      <span className="rounded-md bg-[#F1EFE9] px-2 py-0.5 text-[10px] font-medium text-[#5E6572]">
                        {item.testServer || "Cloudflare"} · {item.source}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-[11px]">
                      <div className="rounded-lg bg-[#F7F6F2] px-2.5 py-2">
                        <span className="block text-[9px] uppercase text-[#868D98]">
                          Download
                        </span>
                        <strong className="tabular-nums text-[#2E7D5B]">
                          {fmt(item.downloadMbps)} Mbps
                        </strong>
                      </div>
                      <div className="rounded-lg bg-[#F7F6F2] px-2.5 py-2">
                        <span className="block text-[9px] uppercase text-[#868D98]">
                          Upload
                        </span>
                        <strong className="tabular-nums text-[#303640]">
                          {fmt(item.uploadMbps)} Mbps
                        </strong>
                      </div>
                      <div className="rounded-lg bg-[#F7F6F2] px-2.5 py-2">
                        <span className="block text-[9px] uppercase text-[#868D98]">
                          Latency / Jitter
                        </span>
                        <strong className="tabular-nums text-[#9F7C38]">
                          {fmt(item.latencyMs)} ms
                        </strong>{" "}
                        <span className="text-[10px] text-[#5E6572]">
                          ({fmt(item.jitterMs)} ms)
                        </span>
                      </div>
                      <div className="rounded-lg bg-[#F7F6F2] px-2.5 py-2">
                        <span className="block text-[9px] uppercase text-[#868D98]">
                          Packet Loss
                        </span>
                        <strong
                          className={`tabular-nums ${
                            (item.packetLoss?.packetLossPercentage ?? 0) > 0
                              ? "text-[#B83B3B]"
                              : "text-[#2E7D5B]"
                          }`}
                        >
                          {fmt(item.packetLoss?.packetLossPercentage, 1)}%
                        </strong>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </section>
      </div>
    </DashboardShell>
  );
}
