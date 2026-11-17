"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Activity,
  AlertCircle,
  Crosshair,
  Info,
  Layers,
  MapPin,
  Radio,
  RefreshCw,
  Signal,
} from "lucide-react";

import DashboardShell from "@/components/layout/DashboardShell";
import {
  getHeatmapData,
  getNetworkDensity,
  getObservations,
  type HeatmapAccuracySummary,
  type HeatmapLocationSummary,
  type HeatmapPoint,
  type NetworkDensityPoint,
  type NetworkObservation,
} from "@/lib/dashboard";
import {
  type BandFilter,
  computeGeographicCenter,
  computeHeatmapAccuracySummary,
  computeHeatmapBandSummary,
  computeObservationLocationCounts,
  getLocationPrecisionInfo,
} from "@/lib/heatmap";
import { getLocalPreferences } from "@/lib/preferences";

const WifiHeatmapMap = dynamic(
  () => import("@/components/dashboard/WifiHeatmapMap"),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-[480px] w-full items-center justify-center rounded-xl border border-[#303640]/[0.09] bg-[#F1EFE9]">
        <div className="flex items-center gap-3 text-xs font-medium text-[#5E6572]">
          <RefreshCw size={16} className="animate-spin text-[#C6A15B]" />
          Loading interactive Wi-Fi heatmap...
        </div>
      </div>
    ),
  }
);

type BandOption = BandFilter;

const BAND_OPTIONS: BandOption[] = ["all", "2.4 GHz", "5 GHz", "6 GHz"];

function formatSignal(value: number | null | undefined): string {
  if (typeof value !== "number" || !Number.isFinite(value)) return "—";
  return `${value.toFixed(1)} dBm`;
}

export default function HeatmapExplorerPage() {
  const [selectedBand, setSelectedBand] = useState<BandOption>(
    () => getLocalPreferences().defaultBand
  );
  const [points, setPoints] = useState<HeatmapPoint[]>([]);
  const [apiAccuracySummary, setApiAccuracySummary] =
    useState<HeatmapAccuracySummary | null>(null);
  const [apiLocationSummary, setApiLocationSummary] =
    useState<HeatmapLocationSummary | null>(null);
  const [density, setDensity] = useState<NetworkDensityPoint[]>([]);
  const [observations, setObservations] = useState<NetworkObservation[]>([]);
  const [observationsTotalCount, setObservationsTotalCount] =
    useState<number | null>(null);
  const [showAllCells, setShowAllCells] = useState(false);
  const hasLoadedSupportDataRef = useRef(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchHeatmapExplorer = useCallback(
    async (band: BandOption, forceSupportData = false) => {
      const shouldFetchSupport =
        forceSupportData || !hasLoadedSupportDataRef.current;

      const [heatmapRes, densityRes, observationsRes] =
        await Promise.allSettled([
          getHeatmapData(band === "all" ? undefined : band),
          shouldFetchSupport ? getNetworkDensity() : Promise.resolve(null),
          shouldFetchSupport
            ? getObservations({ page: 1, limit: 100 })
            : Promise.resolve(null),
        ]);

      if (heatmapRes.status === "fulfilled") {
        setError("");
        setPoints(
          Array.isArray(heatmapRes.value.heatmap) ? heatmapRes.value.heatmap : []
        );
        setApiAccuracySummary(heatmapRes.value.accuracySummary ?? null);
        setApiLocationSummary(heatmapRes.value.locationSummary ?? null);
      } else {
        setPoints([]);
        setApiAccuracySummary(null);
        setApiLocationSummary(null);
        setError(
          heatmapRes.reason instanceof Error
            ? heatmapRes.reason.message
            : "Unable to load geographic heatmap observations."
        );
      }

      if (shouldFetchSupport) {
        if (densityRes.status === "fulfilled" && densityRes.value) {
          setDensity(
            Array.isArray(densityRes.value.density)
              ? densityRes.value.density
              : []
          );
        } else if (densityRes.status === "rejected") {
          setDensity([]);
        }

        if (observationsRes.status === "fulfilled" && observationsRes.value) {
          hasLoadedSupportDataRef.current = true;
          const list = Array.isArray(observationsRes.value.observations)
            ? observationsRes.value.observations
            : [];
          setObservations(list);
          setObservationsTotalCount(
            typeof observationsRes.value.totalCount === "number"
              ? observationsRes.value.totalCount
              : list.length
          );
        } else if (observationsRes.status === "rejected") {
          setObservations([]);
          setObservationsTotalCount(null);
        }
      }

      setLoading(false);
    },
    []
  );

  const loadHeatmapExplorer = useCallback(
    (band: BandOption) => {
      setLoading(true);
      setError("");
      void fetchHeatmapExplorer(band, true);
    },
    [fetchHeatmapExplorer]
  );

  const handleBandSelect = useCallback((band: BandOption) => {
    setLoading(true);
    setError("");
    setSelectedBand(band);
  }, []);

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(() => {
      if (active) {
        return fetchHeatmapExplorer(selectedBand, false);
      }
    });
    return () => {
      active = false;
    };
  }, [fetchHeatmapExplorer, selectedBand]);

  const mapCenter = useMemo(
    () => computeGeographicCenter(points, selectedBand),
    [points, selectedBand]
  );

  const bandSummary = useMemo(
    () => computeHeatmapBandSummary(points, density, selectedBand),
    [density, points, selectedBand]
  );

  const locationCounts = useMemo(
    () =>
      computeObservationLocationCounts(
        observations,
        selectedBand,
        apiLocationSummary
      ),
    [observations, selectedBand, apiLocationSummary]
  );

  const displayedPoints = useMemo(
    () => (showAllCells ? points : points.slice(0, 25)),
    [points, showAllCells]
  );

  const accuracySummary = useMemo(
    () => computeHeatmapAccuracySummary(points, selectedBand, apiAccuracySummary),
    [points, selectedBand, apiAccuracySummary]
  );

  const overallPrecision = useMemo(
    () => getLocationPrecisionInfo(accuracySummary.averageAccuracyMeters),
    [accuracySummary.averageAccuracyMeters]
  );

  const densityMap = useMemo(() => {
    const lookup = new Map<string, NetworkDensityPoint>();
    density.forEach((cell) => {
      lookup.set(`${cell.latitude},${cell.longitude}`, cell);
    });
    return lookup;
  }, [density]);

  return (
    <DashboardShell>
      <div className="w-full min-w-0 space-y-6">
        {/* Header */}
        <section className="flex flex-col justify-between gap-4 border-b border-[#303640]/[0.09] pb-5 lg:flex-row lg:items-center">
          <div className="min-w-0">
            <div className="mb-1.5 flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full bg-[#C6A15B]" />
              <p className="text-[9px] font-bold uppercase tracking-[0.2em] text-[#9F7C38]">
                Spatial Coverage Explorer
              </p>
            </div>
            <h1 className="text-2xl font-semibold tracking-tight text-[#303640] sm:text-[26px]">
              Wi-Fi Signal Heatmap
            </h1>
            <p className="mt-1 max-w-2xl text-xs leading-relaxed text-[#5E6572]">
              Inspect recorded Wi-Fi signal strength and network density across
              GPS observation coordinates reported by scanning devices.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <div className="flex flex-wrap items-center gap-1 rounded-xl border border-[#303640]/10 bg-white p-1 shadow-2xs">
              {BAND_OPTIONS.map((band) => (
                <button
                  key={band}
                  type="button"
                  aria-pressed={selectedBand === band}
                  onClick={() => handleBandSelect(band)}
                  disabled={loading}
                  className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                    selectedBand === band
                      ? "bg-[#303640] text-white"
                      : "text-[#5E6572] hover:bg-[#ECE9E0]/60 hover:text-[#303640]"
                  }`}
                >
                  {band === "all" ? "All Bands" : band}
                </button>
              ))}
            </div>

            <button
              type="button"
              onClick={() => void loadHeatmapExplorer(selectedBand)}
              disabled={loading}
              className="inline-flex h-10 items-center gap-2 rounded-xl bg-[#303640] px-4 text-xs font-semibold text-white shadow-xs transition hover:bg-[#3F4754] disabled:cursor-not-allowed disabled:opacity-60"
            >
              <RefreshCw
                size={13}
                className={`${loading ? "animate-spin" : ""} text-[#C6A15B]`}
              />
              Refresh
            </button>
          </div>
        </section>

        {/* Honest measurement disclaimer */}
        <div className="flex items-start gap-3 rounded-2xl border border-[#C6A15B]/30 bg-[#F1EFE9] px-4 py-3 text-xs text-[#303640]">
          <Info size={16} className="mt-0.5 shrink-0 text-[#9F7C38]" />
          <p className="leading-relaxed text-[#5E6572]">
            Coordinates reflect the{" "}
            <strong className="font-semibold text-[#303640]">
              device GPS observation location
            </strong>{" "}
            at the time of scanning, not the physical location of routers or
            unmeasured dead-zone boundaries.
          </p>
        </div>

        {/* Summary metrics */}
        <section className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
          <article className="rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#868D98]">
                Mapped Cells
              </span>
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#C6A15B]/15 text-[#9F7C38]">
                <MapPin size={17} />
              </span>
            </div>
            <p className="mt-3 text-2xl font-semibold text-[#303640]">
              {bandSummary.locationsCount}
            </p>
            <p className="mt-1 text-[11px] text-[#5E6572]">
              Filter: {selectedBand === "all" ? "All frequencies" : selectedBand}
            </p>
          </article>

          <article className="rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#868D98]">
                GPS Observations
              </span>
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#F1EFE9] text-[#303640]">
                <Radio size={17} />
              </span>
            </div>
            <p className="mt-3 text-2xl font-semibold text-[#303640]">
              {locationCounts.geolocatedObservations}
            </p>
            <p className="mt-1 text-[11px] text-[#5E6572]">
              {locationCounts.nonGeolocatedObservations > 0
                ? `${locationCounts.nonGeolocatedObservations} excluded without GPS (${locationCounts.totalObservations} total)`
                : "Valid GPS scan samples"}
            </p>
          </article>

          <article className="rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#868D98]">
                Band Avg Signal
              </span>
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#2E7D5B]/10 text-[#2E7D5B]">
                <Signal size={17} />
              </span>
            </div>
            <p className="mt-3 text-2xl font-semibold text-[#303640]">
              {formatSignal(bandSummary.weightedAverageSignal)}
            </p>
            <p className="mt-1 text-[11px] text-[#5E6572]">
              Weighted across {selectedBand === "all" ? "all bands" : selectedBand}
            </p>
          </article>

          <article className="rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#868D98]">
                Peak Cell Density
              </span>
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#C6A15B]/15 text-[#9F7C38]">
                <Layers size={17} />
              </span>
            </div>
            <p className="mt-3 text-2xl font-semibold text-[#303640]">
              {bandSummary.uniqueNetworksPeak}
            </p>
            <p className="mt-1 text-[11px] text-[#5E6572]">
              Max unique SSIDs in one cell
            </p>
          </article>
        </section>

        {/* Compact GPS Accuracy Summary */}
        <section className="rounded-2xl border border-[#303640]/[0.09] bg-white p-4 shadow-[0_2px_12px_rgba(48,54,64,0.04)] sm:p-5">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#303640]/[0.08] pb-3">
            <div className="flex items-center gap-2.5">
              <div className="flex h-8 w-8 items-center justify-center rounded-xl bg-[#F1EFE9] text-[#9F7C38]">
                <Crosshair size={16} />
              </div>
              <div>
                <h2 className="text-xs font-semibold text-[#303640]">
                  GPS Accuracy Summary
                </h2>
                <p className="text-[11px] text-[#5E6572]">
                  Estimated device location uncertainty across{" "}
                  {selectedBand === "all" ? "all bands" : selectedBand} mapped
                  observations
                </p>
              </div>
            </div>

            <span
              className={`rounded-lg border px-2.5 py-1 text-[11px] font-semibold ${overallPrecision.badgeClass}`}
            >
              {overallPrecision.label}
            </span>
          </div>

          <div className="mt-3.5 grid grid-cols-2 gap-3 lg:grid-cols-4">
            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] px-3.5 py-2.5">
              <p className="text-[10px] font-bold uppercase tracking-wider text-[#868D98]">
                Avg GPS Accuracy
              </p>
              <p className="mt-1 text-lg font-semibold tabular-nums text-[#303640]">
                {overallPrecision.formattedMeters}
              </p>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] px-3.5 py-2.5">
              <p className="text-[10px] font-bold uppercase tracking-wider text-[#868D98]">
                With Accuracy
              </p>
              <p className="mt-1 text-lg font-semibold tabular-nums text-[#2E7D5B]">
                {accuracySummary.withAccuracyCount}
              </p>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] px-3.5 py-2.5">
              <p className="text-[10px] font-bold uppercase tracking-wider text-[#868D98]">
                Without Accuracy
              </p>
              <p className="mt-1 text-lg font-semibold tabular-nums text-[#5E6572]">
                {accuracySummary.withoutAccuracyCount}
              </p>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] px-3.5 py-2.5">
              <p className="text-[10px] font-bold uppercase tracking-wider text-[#868D98]">
                Coarse (&gt; 50 m)
              </p>
              <p className="mt-1 text-lg font-semibold tabular-nums text-[#B83B3B]">
                {accuracySummary.coarseAccuracyCount}
              </p>
            </div>
          </div>

          <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-[11px] text-[#5E6572]">
            <span>
              Precision tiers: ≤ 20 m High · 20–50 m Moderate · &gt; 50 m Low
              (coarse) · Missing: Unknown
            </span>
            <span>
              GPS accuracy is an estimated radius, not guaranteed positional
              error or router location.
            </span>
          </div>
        </section>

        {/* Non-geolocated visibility banner */}
        {!loading && !error && locationCounts.nonGeolocatedObservations > 0 && (
          <div className="flex items-start gap-3 rounded-2xl border border-[#303640]/12 bg-[#F7F6F2] px-4 py-3 text-xs text-[#5E6572]">
            <AlertCircle size={16} className="mt-0.5 shrink-0 text-[#9F7C38]" />
            <p className="leading-relaxed">
              <strong className="font-semibold text-[#303640]">
                {locationCounts.nonGeolocatedObservations} of{" "}
                {locationCounts.totalObservations}
              </strong>{" "}
              observation(s) in this filter do not have valid latitude/longitude
              coordinates (for example, desktop scans with Location Services off)
              and cannot be plotted on the map.{" "}
              <strong className="font-semibold text-[#303640]">
                {locationCounts.geolocatedObservations}
              </strong>{" "}
              geolocated observation(s) are included below.
            </p>
          </div>
        )}

        {/* Error banner */}
        {error && (
          <div
            role="alert"
            className="flex items-center gap-3 rounded-2xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.07] px-4 py-3.5 text-xs font-medium text-[#B83B3B]"
          >
            <AlertCircle size={17} className="shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Map section */}
        <section className="overflow-hidden rounded-2xl border border-[#303640]/[0.09] bg-white shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#303640]/[0.08] bg-[#F1EFE9]/60 px-5 py-3.5">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl border border-[#C6A15B]/30 bg-white text-[#9F7C38]">
                <Crosshair size={17} />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-[#303640]">
                  Interactive Geographic Map
                </h2>
                <p className="text-[11px] text-[#5E6572]">
                  Showing{" "}
                  {selectedBand === "all"
                    ? "all supported frequency bands"
                    : `${selectedBand} filtered observations`}
                </p>
              </div>
            </div>

            {loading && (
              <span className="inline-flex items-center gap-2 text-xs font-medium text-[#9F7C38]">
                <RefreshCw size={13} className="animate-spin" />
                Updating coordinates...
              </span>
            )}
          </div>

          <div className="p-3 sm:p-4">
            <WifiHeatmapMap
              points={points}
              center={mapCenter}
              observations={observations}
              observationsTotalCount={observationsTotalCount}
              selectedBand={selectedBand}
              onBandChange={setSelectedBand}
              accuracySummary={accuracySummary}
              locationSummary={apiLocationSummary}
              showBandFilter={false}
            />
          </div>
        </section>

        {/* Per-cell density & band-specific signal breakdown */}
        <section className="overflow-hidden rounded-2xl border border-[#303640]/[0.09] bg-white shadow-[0_2px_12px_rgba(48,54,64,0.04)]">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#303640]/[0.08] px-5 py-4">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#F1EFE9] text-[#303640]">
                <Activity size={17} />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-[#303640]">
                  Cell Signal & Density Breakdown
                </h2>
                <p className="text-[11px] text-[#5E6572]">
                  Band-specific average dBm and unique SSID counts per coordinate
                  cell
                </p>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-lg bg-[#F1EFE9] px-2.5 py-1 text-[11px] font-semibold text-[#5E6572]">
                {points.length > 25
                  ? `Showing ${displayedPoints.length} of ${points.length} cells`
                  : `${points.length} cells`}
              </span>
              {points.length > 25 && (
                <button
                  type="button"
                  onClick={() => setShowAllCells((prev) => !prev)}
                  className="rounded-lg border border-[#303640]/12 bg-white px-2.5 py-1 text-[11px] font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#F7F6F2]"
                >
                  {showAllCells ? "Show top 25" : `Show all ${points.length}`}
                </button>
              )}
            </div>
          </div>

          {!loading && points.length === 0 ? (
            <div className="p-8 text-center">
              <MapPin size={24} className="mx-auto text-[#868D98]" />
              <p className="mt-3 text-sm font-semibold text-[#303640]">
                No geographic cells match this filter
              </p>
              <p className="mt-1 text-xs text-[#5E6572]">
                Switch frequency filter or upload GPS-tagged scans from your
                paired Android or Windows companion app.
              </p>
            </div>
          ) : (
            <>
              <div className="hidden w-full max-w-full overflow-x-auto md:block">
                <table className="w-full min-w-[780px] border-collapse text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#303640]/[0.08] bg-[#F1EFE9]/60 text-[10px] font-bold uppercase tracking-wider text-[#5E6572]">
                      <th className="px-5 py-3">Observation Coordinate</th>
                      <th className="px-4 py-3">Samples</th>
                      <th className="px-4 py-3">Unique SSIDs</th>
                      <th className="px-4 py-3">Avg GPS Accuracy</th>
                      <th className="px-4 py-3">Cell Avg</th>
                      <th className="px-4 py-3">2.4 GHz Avg</th>
                      <th className="px-4 py-3">5 GHz Avg</th>
                      <th className="px-5 py-3">6 GHz Avg</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#303640]/[0.06]">
                    {displayedPoints.map((point) => {
                      const cellKey = `${point.latitude},${point.longitude}`;
                      const densityCell = densityMap.get(cellKey);
                      const precision = getLocationPrecisionInfo(
                        point.avgAccuracyMeters
                      );
                      const b24 = point.frequencyDistribution?.find(
                        (b) => b.band === "2.4 GHz"
                      );
                      const b5 = point.frequencyDistribution?.find(
                        (b) => b.band === "5 GHz"
                      );
                      const b6 = point.frequencyDistribution?.find(
                        (b) => b.band === "6 GHz"
                      );

                      return (
                        <tr
                          key={cellKey}
                          className="transition hover:bg-[#F7F6F2]"
                        >
                          <td className="px-5 py-3.5 font-mono text-[11px] font-medium text-[#303640]">
                            {point.latitude.toFixed(3)},{" "}
                            {point.longitude.toFixed(3)}
                          </td>
                          <td className="px-4 py-3.5 font-semibold tabular-nums text-[#303640]">
                            {point.totalObservations}
                          </td>
                          <td className="px-4 py-3.5 tabular-nums text-[#5E6572]">
                            {densityCell ? densityCell.uniqueNetworkCount : "—"}
                          </td>
                          <td className="px-4 py-3.5">
                            <span
                              className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[10px] font-semibold tabular-nums ${precision.badgeClass}`}
                              title={precision.label}
                            >
                              {precision.formattedMeters} · {precision.shortLabel}
                            </span>
                          </td>
                          <td className="px-4 py-3.5 font-semibold tabular-nums text-[#9F7C38]">
                            {formatSignal(point.averageSignal)}
                          </td>
                          <td className="px-4 py-3.5 tabular-nums text-[#5E6572]">
                            {b24 && b24.count > 0
                              ? `${formatSignal(b24.averageSignal)} (${b24.count})`
                              : "—"}
                          </td>
                          <td className="px-4 py-3.5 tabular-nums text-[#5E6572]">
                            {b5 && b5.count > 0
                              ? `${formatSignal(b5.averageSignal)} (${b5.count})`
                              : "—"}
                          </td>
                          <td className="px-5 py-3.5 tabular-nums text-[#5E6572]">
                            {b6 && b6.count > 0
                              ? `${formatSignal(b6.averageSignal)} (${b6.count})`
                              : "—"}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <div className="divide-y divide-[#303640]/[0.07] md:hidden">
                {displayedPoints.map((point) => {
                  const cellKey = `${point.latitude},${point.longitude}`;
                  const densityCell = densityMap.get(cellKey);
                  const precision = getLocationPrecisionInfo(
                    point.avgAccuracyMeters
                  );
                  const b24 = point.frequencyDistribution?.find(
                    (b) => b.band === "2.4 GHz"
                  );
                  const b5 = point.frequencyDistribution?.find(
                    (b) => b.band === "5 GHz"
                  );
                  const b6 = point.frequencyDistribution?.find(
                    (b) => b.band === "6 GHz"
                  );

                  return (
                    <div key={cellKey} className="space-y-2.5 p-4 text-xs">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-mono text-[11px] font-semibold text-[#303640]">
                          {point.latitude.toFixed(3)}, {point.longitude.toFixed(3)}
                        </span>
                        <span className="rounded-md bg-[#C6A15B]/15 px-2 py-0.5 text-[11px] font-semibold tabular-nums text-[#9F7C38]">
                          {formatSignal(point.averageSignal)}
                        </span>
                      </div>

                      <div className="grid grid-cols-2 gap-2 text-[11px] text-[#5E6572]">
                        <div>
                          Samples:{" "}
                          <span className="font-semibold text-[#303640]">
                            {point.totalObservations}
                          </span>
                        </div>
                        <div>
                          Unique SSIDs:{" "}
                          <span className="font-semibold text-[#303640]">
                            {densityCell ? densityCell.uniqueNetworkCount : "—"}
                          </span>
                        </div>
                        <div className="col-span-2 flex items-center gap-2">
                          <span>GPS Accuracy:</span>
                          <span
                            className={`rounded-md border px-1.5 py-0.5 text-[10px] font-semibold tabular-nums ${precision.badgeClass}`}
                          >
                            {precision.formattedMeters} · {precision.shortLabel}
                          </span>
                        </div>
                      </div>

                      <div className="flex flex-wrap gap-1.5 text-[10px]">
                        <span className="rounded-md bg-[#F1EFE9] px-2 py-1 text-[#5E6572]">
                          2.4G:{" "}
                          {b24 && b24.count > 0
                            ? `${formatSignal(b24.averageSignal)} (${b24.count})`
                            : "—"}
                        </span>
                        <span className="rounded-md bg-[#F1EFE9] px-2 py-1 text-[#5E6572]">
                          5G:{" "}
                          {b5 && b5.count > 0
                            ? `${formatSignal(b5.averageSignal)} (${b5.count})`
                            : "—"}
                        </span>
                        <span className="rounded-md bg-[#F1EFE9] px-2 py-1 text-[#5E6572]">
                          6G:{" "}
                          {b6 && b6.count > 0
                            ? `${formatSignal(b6.averageSignal)} (${b6.count})`
                            : "—"}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </section>
      </div>
    </DashboardShell>
  );
}
