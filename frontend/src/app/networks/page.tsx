"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertCircle,
  Clock3,
  Radio,
  RefreshCw,
  Search,
  Shield,
  ShieldCheck,
  Signal,
  Wifi,
} from "lucide-react";

import DashboardShell from "@/components/layout/DashboardShell";
import {
  getNetworkQuality,
  getObservations,
  type NetworkObservation,
  type NetworkQualityItem,
} from "@/lib/dashboard";
import { getLocalPreferences } from "@/lib/preferences";

type BandFilter = "all" | "2.4 GHz" | "5 GHz" | "6 GHz";
type SortOption = "newest" | "strongest" | "weakest" | "quality";

interface EnrichedObservation {
  observation: NetworkObservation;
  displaySsid: string;
  isHiddenSsid: boolean;
  quality: NetworkQualityItem["quality"] | null;
}

function getClassificationStyle(classification?: string): string {
  switch (classification) {
    case "Excellent":
      return "border-[#2E7D5B]/30 bg-[#2E7D5B]/10 text-[#2E7D5B]";
    case "Good":
      return "border-lime-600/25 bg-lime-600/10 text-lime-700";
    case "Fair":
      return "border-[#C6A15B]/40 bg-[#C6A15B]/15 text-[#9F7C38]";
    case "Poor":
      return "border-[#B83B3B]/30 bg-[#B83B3B]/10 text-[#B83B3B]";
    default:
      return "border-[#303640]/15 bg-[#F1EFE9] text-[#5E6572]";
  }
}

function formatTime(value?: string): string {
  if (!value) return "Unknown";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Unknown";
  return date.toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

const PAGE_SIZE = 100;

export default function NetworkExplorerPage() {
  const [observations, setObservations] = useState<NetworkObservation[]>([]);
  const [page, setPage] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [qualityItems, setQualityItems] = useState<NetworkQualityItem[]>([]);
  const [averageScore, setAverageScore] = useState<number | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [bandFilter, setBandFilter] = useState<BandFilter>(
    () => getLocalPreferences().defaultBand
  );
  const [qualityFilter, setQualityFilter] = useState<string>("all");
  const [sortBy, setSortBy] = useState<SortOption>("newest");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchExplorer = useCallback(async () => {
    const [obsRes, qualityRes] = await Promise.allSettled([
      getObservations({ page, limit: PAGE_SIZE }),
      getNetworkQuality(),
    ]);

    const errors: string[] = [];

    if (obsRes.status === "fulfilled") {
      const list = Array.isArray(obsRes.value.observations)
        ? obsRes.value.observations
        : [];
      setObservations(list);
      setTotalCount(
        typeof obsRes.value.totalCount === "number"
          ? obsRes.value.totalCount
          : list.length
      );
      setHasMore(Boolean(obsRes.value.hasMore));
    } else {
      setObservations([]);
      setTotalCount(0);
      setHasMore(false);
      errors.push(
        obsRes.reason instanceof Error
          ? obsRes.reason.message
          : "Unable to load network observations."
      );
    }

    if (qualityRes.status === "fulfilled") {
      setQualityItems(
        Array.isArray(qualityRes.value.networks) ? qualityRes.value.networks : []
      );
      setAverageScore(
        typeof qualityRes.value.averageScore === "number"
          ? qualityRes.value.averageScore
          : null
      );
    } else {
      setQualityItems([]);
      setAverageScore(null);
      errors.push(
        qualityRes.reason instanceof Error
          ? qualityRes.reason.message
          : "Unable to load network quality scores."
      );
    }

    setError(errors.length > 0 ? errors.join(" ") : "");
    setLoading(false);
  }, [page]);

  const loadExplorer = useCallback(() => {
    setLoading(true);
    setError("");
    void fetchExplorer();
  }, [fetchExplorer]);

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(() => {
      if (active) {
        return fetchExplorer();
      }
    });
    return () => {
      active = false;
    };
  }, [fetchExplorer]);

  const qualityById = useMemo(() => {
    const map = new Map<string, NetworkQualityItem["quality"]>();
    qualityItems.forEach((item) => {
      map.set(item.observationId, item.quality);
    });
    return map;
  }, [qualityItems]);

  const enriched = useMemo<EnrichedObservation[]>(() => {
    return observations.map((obs) => {
      const trimmedSsid = typeof obs.ssid === "string" ? obs.ssid.trim() : "";
      const isHiddenSsid =
        trimmedSsid.length === 0 || trimmedSsid.toLowerCase() === "<unknown ssid>";
      return {
        observation: obs,
        displaySsid: isHiddenSsid ? "Hidden Network" : trimmedSsid,
        isHiddenSsid,
        quality: qualityById.get(obs._id) ?? null,
      };
    });
  }, [observations, qualityById]);

  const filtered = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();

    const matches = enriched.filter((entry) => {
      const { observation, displaySsid, quality } = entry;

      if (bandFilter !== "all" && observation.band !== bandFilter) {
        return false;
      }

      if (
        qualityFilter !== "all" &&
        quality?.classification !== qualityFilter
      ) {
        return false;
      }

      if (!query) return true;

      return (
        displaySsid.toLowerCase().includes(query) ||
        (observation.security || "").toLowerCase().includes(query) ||
        (observation.band || "").toLowerCase().includes(query) ||
        (observation.source || "").toLowerCase().includes(query) ||
        (quality?.classification || "").toLowerCase().includes(query)
      );
    });

    return matches.sort((a, b) => {
      switch (sortBy) {
        case "strongest":
          return b.observation.signalDbm - a.observation.signalDbm;
        case "weakest":
          return a.observation.signalDbm - b.observation.signalDbm;
        case "quality":
          return (b.quality?.score ?? -1) - (a.quality?.score ?? -1);
        case "newest":
        default: {
          const ta = new Date(
            a.observation.observedAt || a.observation.createdAt
          ).getTime();
          const tb = new Date(
            b.observation.observedAt || b.observation.createdAt
          ).getTime();
          return (Number.isNaN(tb) ? 0 : tb) - (Number.isNaN(ta) ? 0 : ta);
        }
      }
    });
  }, [bandFilter, enriched, qualityFilter, searchQuery, sortBy]);

  const hasActiveFilters =
    searchQuery.trim().length > 0 ||
    bandFilter !== "all" ||
    qualityFilter !== "all" ||
    sortBy !== "newest";

  function clearFilters() {
    setSearchQuery("");
    setBandFilter("all");
    setQualityFilter("all");
    setSortBy("newest");
  }

  const selectedEntry = useMemo(
    () =>
      filtered.find((item) => item.observation._id === selectedId) ??
      filtered[0] ??
      null,
    [filtered, selectedId]
  );

  return (
    <DashboardShell>
      <div className="w-full min-w-0 space-y-6">
        {/* Page header */}
        <section className="flex flex-col justify-between gap-4 border-b border-[#303640]/[0.09] pb-5 lg:flex-row lg:items-center">
          <div className="min-w-0">
            <div className="mb-1.5 flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full bg-[#C6A15B]" />
              <p className="text-[9px] font-bold uppercase tracking-[0.2em] text-[#9F7C38]">
                Observation & Quality Explorer
              </p>
            </div>
            <h1 className="text-2xl font-semibold tracking-tight text-[#303640] sm:text-[26px]">
              Network Explorer
            </h1>
            <p className="mt-1 max-w-2xl text-xs leading-relaxed text-[#5E6572]">
              Search recorded Wi-Fi scans, inspect signal levels and protocol
              metadata, and evaluate quality breakdowns from GET /api/quality.
            </p>
          </div>

          <button
            type="button"
            onClick={() => void loadExplorer()}
            disabled={loading}
            className="inline-flex h-10 shrink-0 items-center gap-2 self-start rounded-xl bg-[#303640] px-4 text-xs font-semibold text-white shadow-xs transition hover:bg-[#3F4754] disabled:cursor-not-allowed disabled:opacity-60 lg:self-auto"
          >
            <RefreshCw
              size={13}
              className={`${loading ? "animate-spin" : ""} text-[#C6A15B]`}
            />
            Refresh scans
          </button>
        </section>

        {/* Summary KPIs */}
        <section className="grid grid-cols-1 gap-3.5 sm:grid-cols-3">
          <article className="rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#868D98]">
                Observations Loaded
              </span>
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#C6A15B]/15 text-[#9F7C38]">
                <Radio size={17} />
              </span>
            </div>
            <p className="mt-3 text-2xl font-semibold text-[#303640]">
              {totalCount > observations.length
                ? `${observations.length} / ${totalCount}`
                : observations.length}
            </p>
            <p className="mt-1 text-[11px] text-[#5E6572]">
              Page {page} · Showing {filtered.length} matching filter criteria
            </p>
          </article>

          <article className="rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#868D98]">
                Average Quality Score
              </span>
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#2E7D5B]/10 text-[#2E7D5B]">
                <ShieldCheck size={17} />
              </span>
            </div>
            <p className="mt-3 text-2xl font-semibold text-[#303640]">
              {averageScore !== null ? `${averageScore} / 100` : "—"}
            </p>
            <p className="mt-1 text-[11px] text-[#5E6572]">
              Weighted across{" "}
              {totalCount > qualityItems.length
                ? `latest ${qualityItems.length} of ${totalCount} scans`
                : "signal, level, band, and security"}
            </p>
          </article>

          <article className="rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#868D98]">
                Selected Signal Grade
              </span>
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#F1EFE9] text-[#303640]">
                <Signal size={17} />
              </span>
            </div>
            <p className="mt-3 text-2xl font-semibold text-[#303640]">
              {selectedEntry?.quality
                ? `${selectedEntry.quality.score} (${selectedEntry.quality.classification})`
                : "—"}
            </p>
            <p className="mt-1 truncate text-[11px] text-[#5E6572]">
              {selectedEntry ? selectedEntry.displaySsid : "Select a scan row"}
            </p>
          </article>
        </section>

        {/* Search & filter toolbar */}
        <section className="rounded-2xl border border-[#303640]/[0.09] bg-white p-4 shadow-[0_2px_10px_rgba(48,54,64,0.03)]">
          <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
            <div className="relative w-full xl:max-w-sm">
              <Search
                size={15}
                className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#868D98]"
              />
              <input
                type="search"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Search by SSID, security, band, or grade..."
                className="h-10 w-full rounded-xl border border-[#303640]/15 bg-[#F7F6F2]/70 pl-10 pr-3.5 text-xs text-[#303640] placeholder-[#868D98] outline-none transition focus:border-[#C6A15B] focus:bg-white focus:ring-3 focus:ring-[#C6A15B]/15"
              />
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <div className="flex flex-wrap items-center gap-1 rounded-xl border border-[#303640]/10 bg-[#F7F6F2] p-1">
                {(["all", "2.4 GHz", "5 GHz", "6 GHz"] as BandFilter[]).map(
                  (band) => (
                    <button
                      key={band}
                      type="button"
                      aria-pressed={bandFilter === band}
                      onClick={() => setBandFilter(band)}
                      className={`h-8 rounded-lg px-3 text-[11px] font-semibold transition ${
                        bandFilter === band
                          ? "bg-[#303640] text-white shadow-2xs"
                          : "text-[#5E6572] hover:bg-[#ECE9E0] hover:text-[#303640]"
                      }`}
                    >
                      {band === "all" ? "All Bands" : band}
                    </button>
                  )
                )}
              </div>

              <select
                value={qualityFilter}
                onChange={(event) => setQualityFilter(event.target.value)}
                aria-label="Filter by quality grade"
                className="h-10 rounded-xl border border-[#303640]/12 bg-[#F7F6F2] px-3 text-[11px] font-semibold text-[#303640] outline-none transition focus:border-[#C6A15B] focus:bg-white"
              >
                <option value="all">All Grades</option>
                <option value="Excellent">Excellent</option>
                <option value="Good">Good</option>
                <option value="Fair">Fair</option>
                <option value="Poor">Poor</option>
              </select>

              <select
                value={sortBy}
                onChange={(event) => setSortBy(event.target.value as SortOption)}
                aria-label="Sort observations"
                className="h-10 rounded-xl border border-[#303640]/12 bg-[#F7F6F2] px-3 text-[11px] font-semibold text-[#303640] outline-none transition focus:border-[#C6A15B] focus:bg-white"
              >
                <option value="newest">Sort: Newest</option>
                <option value="strongest">Sort: Strongest RSSI</option>
                <option value="weakest">Sort: Weakest RSSI</option>
                <option value="quality">Sort: Quality Score</option>
              </select>

              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={clearFilters}
                  className="h-10 rounded-xl border border-[#303640]/15 bg-white px-3.5 text-[11px] font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#ECE9E0]/60"
                >
                  Clear filters
                </button>
              )}
            </div>
          </div>
        </section>

        {error && (
          <div
            role="alert"
            className="flex items-center gap-3 rounded-2xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.07] px-4 py-3.5 text-xs font-medium text-[#B83B3B]"
          >
            <AlertCircle size={17} className="shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Quality breakdown inspector for selected observation */}
        {selectedEntry?.quality && (
          <section className="rounded-2xl border border-[#C6A15B]/35 bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#303640]/[0.08] pb-4">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#C6A15B]/15 text-[#9F7C38]">
                  <Wifi size={18} />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h2 className="text-sm font-semibold text-[#303640]">
                      {selectedEntry.displaySsid}
                    </h2>
                    {selectedEntry.isHiddenSsid && (
                      <span className="rounded-md bg-[#F1EFE9] px-2 py-0.5 text-[10px] font-semibold text-[#5E6572]">
                        Hidden SSID
                      </span>
                    )}
                  </div>
                  <p className="text-[11px] text-[#5E6572]">
                    Quality component breakdown from GET /api/quality ·{" "}
                    {selectedEntry.observation.signalDbm} dBm ·{" "}
                    {selectedEntry.observation.band} ·{" "}
                    {selectedEntry.observation.security}
                  </p>
                </div>
              </div>

              <span
                className={`rounded-lg border px-3 py-1.5 text-xs font-semibold ${getClassificationStyle(
                  selectedEntry.quality.classification
                )}`}
              >
                Score {selectedEntry.quality.score}/100 ·{" "}
                {selectedEntry.quality.classification}
              </span>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
              {[
                {
                  label: "Signal Strength (70%)",
                  value: selectedEntry.quality.components.signalScore,
                },
                {
                  label: "Signal Level (15%)",
                  value: selectedEntry.quality.components.signalLevelScore,
                },
                {
                  label: "Band Support (5%)",
                  value: selectedEntry.quality.components.bandScore,
                },
                {
                  label: "Security Rating (10%)",
                  value: selectedEntry.quality.components.securityScore,
                },
              ].map((part) => (
                <div
                  key={part.label}
                  className="rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] p-3.5"
                >
                  <p className="text-[10px] font-semibold text-[#5E6572]">
                    {part.label}
                  </p>
                  <p className="mt-1.5 text-lg font-bold text-[#303640]">
                    {part.value}
                    <span className="ml-1 text-[10px] font-normal text-[#868D98]">
                      / 100
                    </span>
                  </p>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Data list: desktop table & mobile cards */}
        <section className="overflow-hidden rounded-2xl border border-[#303640]/[0.09] bg-white shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
          {loading ? (
            <div className="flex items-center justify-center gap-3 p-12 text-xs font-medium text-[#5E6572]">
              <RefreshCw size={16} className="animate-spin text-[#C6A15B]" />
              Loading recorded network observations...
            </div>
          ) : filtered.length === 0 ? (
            <div className="p-10 text-center">
              <Wifi size={26} className="mx-auto text-[#868D98]" />
              <p className="mt-3 text-sm font-semibold text-[#303640]">
                No network observations match your search
              </p>
              <p className="mt-1 text-xs text-[#5E6572]">
                Try clearing filters or record scans from your paired Android or
                Windows companion device.
              </p>
              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={clearFilters}
                  className="mt-4 inline-flex items-center rounded-xl border border-[#C6A15B]/45 bg-[#C6A15B]/15 px-4 py-2 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#C6A15B]/25"
                >
                  Reset filters
                </button>
              )}
            </div>
          ) : (
            <>
              {/* Desktop table */}
              <div className="hidden overflow-x-auto lg:block">
                <table className="w-full border-collapse text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#303640]/[0.08] bg-[#F1EFE9]/60 text-[10px] font-bold uppercase tracking-wider text-[#5E6572]">
                      <th className="px-5 py-3.5">SSID</th>
                      <th className="px-4 py-3.5">Signal</th>
                      <th className="px-4 py-3.5">Frequency / Band</th>
                      <th className="px-4 py-3.5">Security</th>
                      <th className="px-4 py-3.5">Quality Score</th>
                      <th className="px-4 py-3.5">Source</th>
                      <th className="px-5 py-3.5">Observed At</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#303640]/[0.06]">
                    {filtered.map((entry) => {
                      const { observation, displaySsid, isHiddenSsid, quality } =
                        entry;
                      const isSelected =
                        selectedEntry?.observation._id === observation._id;

                      return (
                        <tr
                          key={observation._id}
                          onClick={() => setSelectedId(observation._id)}
                          className={`cursor-pointer transition ${
                            isSelected ? "bg-[#C6A15B]/10" : "hover:bg-[#ECE9E0]/45"
                          }`}
                        >
                          <td className="px-5 py-3.5 font-semibold text-[#303640]">
                            <div className="flex items-center gap-2">
                              <span
                                className={
                                  isHiddenSsid ? "italic text-[#5E6572]" : ""
                                }
                              >
                                {displaySsid}
                              </span>
                            </div>
                          </td>
                          <td className="px-4 py-3.5 font-mono font-semibold tabular-nums text-[#303640]">
                            {observation.signalDbm} dBm
                          </td>
                          <td className="px-4 py-3.5 tabular-nums text-[#5E6572]">
                            {observation.frequencyMhz} MHz ·{" "}
                            <span className="font-semibold text-[#9F7C38]">
                              {observation.band}
                            </span>
                          </td>
                          <td className="px-4 py-3.5 text-[#5E6572]">
                            <span className="inline-flex items-center gap-1.5">
                              <Shield size={12} className="text-[#868D98]" />
                              {observation.security || "Unknown"}
                            </span>
                          </td>
                          <td className="px-4 py-3.5">
                            {quality ? (
                              <span
                                className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[10px] font-semibold ${getClassificationStyle(
                                  quality.classification
                                )}`}
                              >
                                {quality.score} · {quality.classification}
                              </span>
                            ) : (
                              <span className="text-[#868D98]">—</span>
                            )}
                          </td>
                          <td className="px-4 py-3.5 uppercase tracking-wider text-[10px] font-semibold text-[#868D98]">
                            {observation.source || "—"}
                          </td>
                          <td className="px-5 py-3.5 text-[#5E6572]">
                            {formatTime(observation.observedAt)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Mobile cards */}
              <div className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:hidden">
                {filtered.map((entry) => {
                  const { observation, displaySsid, isHiddenSsid, quality } =
                    entry;
                  const isSelected =
                    selectedEntry?.observation._id === observation._id;

                  return (
                    <button
                      key={observation._id}
                      type="button"
                      onClick={() => setSelectedId(observation._id)}
                      className={`w-full rounded-xl border p-4 text-left transition ${
                        isSelected
                          ? "border-[#C6A15B] bg-[#C6A15B]/[0.07]"
                          : "border-[#303640]/[0.09] bg-[#F7F6F2]/60 hover:border-[#C6A15B]/50"
                      }`}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <p
                          className={`truncate text-sm font-semibold ${
                            isHiddenSsid
                              ? "italic text-[#5E6572]"
                              : "text-[#303640]"
                          }`}
                        >
                          {displaySsid}
                        </p>

                        {quality && (
                          <span
                            className={`shrink-0 rounded-md border px-2 py-0.5 text-[10px] font-semibold ${getClassificationStyle(
                              quality.classification
                            )}`}
                          >
                            {quality.score} · {quality.classification}
                          </span>
                        )}
                      </div>

                      <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                        <div className="rounded-lg bg-white px-2.5 py-2">
                          <span className="block text-[9px] uppercase text-[#868D98]">
                            Signal
                          </span>
                          <strong className="text-[#303640]">
                            {observation.signalDbm} dBm
                          </strong>
                        </div>
                        <div className="rounded-lg bg-white px-2.5 py-2">
                          <span className="block text-[9px] uppercase text-[#868D98]">
                            Band
                          </span>
                          <strong className="text-[#9F7C38]">
                            {observation.band} ({observation.frequencyMhz} MHz)
                          </strong>
                        </div>
                      </div>

                      <div className="mt-3 flex items-center justify-between text-[11px] text-[#5E6572]">
                        <span className="inline-flex items-center gap-1">
                          <Shield size={12} />
                          {observation.security || "Unknown"}
                        </span>
                        <span className="inline-flex items-center gap-1">
                          <Clock3 size={12} />
                          {formatTime(observation.observedAt)}
                        </span>
                      </div>
                    </button>
                  );
                })}
              </div>
            </>
          )}

          {(totalCount > PAGE_SIZE || page > 1) && (
            <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-[#303640]/[0.08] pt-4 text-xs text-[#5E6572]">
              <span>
                Page {page}
                {totalCount > 0
                  ? ` of ${Math.max(1, Math.ceil(totalCount / PAGE_SIZE))}`
                  : ""}{" "}
                · {totalCount} total observation(s)
              </span>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={loading || page <= 1}
                  onClick={() => {
                    setLoading(true);
                    setPage((prev) => Math.max(1, prev - 1));
                  }}
                  className="rounded-lg border border-[#303640]/12 bg-white px-3 py-1.5 font-medium text-[#303640] transition hover:bg-[#F7F6F2] disabled:cursor-not-allowed disabled:opacity-45"
                >
                  Previous
                </button>
                <button
                  type="button"
                  disabled={loading || !hasMore}
                  onClick={() => {
                    setLoading(true);
                    setPage((prev) => prev + 1);
                  }}
                  className="rounded-lg border border-[#303640]/12 bg-white px-3 py-1.5 font-medium text-[#303640] transition hover:bg-[#F7F6F2] disabled:cursor-not-allowed disabled:opacity-45"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </section>
      </div>
    </DashboardShell>
  );
}
