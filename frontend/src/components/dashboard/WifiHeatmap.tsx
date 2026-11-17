"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  MapPin,
  RefreshCw,
} from "lucide-react";

import {
  getHeatmapData,
  getObservations,
  type HeatmapAccuracySummary,
  type HeatmapLocationSummary,
  type NetworkObservation,
} from "@/lib/dashboard";
import {
  computeGeographicCenter,
  computeObservationLocationCounts,
} from "@/lib/heatmap";
import { getLocalPreferences } from "@/lib/preferences";

import type { BandFilter, HeatPoint } from "./WifiHeatmapMap";

const WifiHeatmapMap = dynamic(
  () => import("./WifiHeatmapMap"),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-[480px] w-full items-center justify-center rounded-xl bg-[#F1EFE9]">
        <div className="flex items-center gap-3 text-xs text-[#5E6572]">
          <RefreshCw size={15} className="animate-spin text-[#C6A15B]" />
          Preparing interactive map...
        </div>
      </div>
    ),
  }
);

export default function WifiHeatmap() {
  const [selectedBand, setSelectedBand] = useState<BandFilter>(
    () => getLocalPreferences().defaultBand
  );
  const [points, setPoints] = useState<HeatPoint[]>([]);
  const [accuracySummary, setAccuracySummary] =
    useState<HeatmapAccuracySummary | null>(null);
  const [locationSummary, setLocationSummary] =
    useState<HeatmapLocationSummary | null>(null);
  const [observations, setObservations] = useState<NetworkObservation[]>([]);
  const [observationsTotalCount, setObservationsTotalCount] =
    useState<number | null>(null);
  const hasLoadedObservationsRef = useRef(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchHeatmap = useCallback(
    async (band: BandFilter, forceObservations = false) => {
      const shouldFetchObservations =
        forceObservations || !hasLoadedObservationsRef.current;

      const [heatmapResult, observationsResult] = await Promise.allSettled([
        getHeatmapData(band === "all" ? undefined : band),
        shouldFetchObservations
          ? getObservations({ page: 1, limit: 100 })
          : Promise.resolve(null),
      ]);

      if (heatmapResult.status === "fulfilled") {
        setError("");
        const rawData = Array.isArray(heatmapResult.value.heatmap)
          ? heatmapResult.value.heatmap
          : [];

        const validPoints: HeatPoint[] = rawData.flatMap((item) => {
          if (!item || typeof item !== "object") return [];

          const value = item as unknown as Record<string, unknown>;

          if (
            typeof value.latitude !== "number" ||
            typeof value.longitude !== "number" ||
            typeof value.averageSignal !== "number" ||
            !Number.isFinite(value.latitude) ||
            !Number.isFinite(value.longitude) ||
            !Number.isFinite(value.averageSignal) ||
            Math.abs(value.latitude) > 90 ||
            Math.abs(value.longitude) > 180
          ) {
            return [];
          }

          return [
            {
              latitude: value.latitude,
              longitude: value.longitude,
              averageSignal: value.averageSignal,
              strongestSignal:
                typeof value.strongestSignal === "number"
                  ? value.strongestSignal
                  : value.averageSignal,
              weakestSignal:
                typeof value.weakestSignal === "number"
                  ? value.weakestSignal
                  : value.averageSignal,
              totalObservations:
                typeof value.totalObservations === "number"
                  ? value.totalObservations
                  : 0,
              avgAccuracyMeters:
                typeof value.avgAccuracyMeters === "number" &&
                Number.isFinite(value.avgAccuracyMeters)
                  ? value.avgAccuracyMeters
                  : null,
              accuracySampleCount:
                typeof value.accuracySampleCount === "number" &&
                Number.isFinite(value.accuracySampleCount)
                  ? value.accuracySampleCount
                  : undefined,
              missingAccuracyCount:
                typeof value.missingAccuracyCount === "number" &&
                Number.isFinite(value.missingAccuracyCount)
                  ? value.missingAccuracyCount
                  : undefined,
              coarseAccuracyCount:
                typeof value.coarseAccuracyCount === "number" &&
                Number.isFinite(value.coarseAccuracyCount)
                  ? value.coarseAccuracyCount
                  : undefined,
              frequencyDistribution: Array.isArray(value.frequencyDistribution)
                ? (value.frequencyDistribution as HeatPoint["frequencyDistribution"])
                : [],
            },
          ];
        });

        setPoints(validPoints);
        setAccuracySummary(heatmapResult.value.accuracySummary ?? null);
        setLocationSummary(heatmapResult.value.locationSummary ?? null);
      } else {
        setAccuracySummary(null);
        setLocationSummary(null);
        setError(
          heatmapResult.reason instanceof Error
            ? heatmapResult.reason.message
            : "Unable to load heatmap data."
        );
      }

      if (shouldFetchObservations) {
        if (
          observationsResult.status === "fulfilled" &&
          observationsResult.value
        ) {
          hasLoadedObservationsRef.current = true;
          const records = observationsResult.value.observations;
          setObservations(Array.isArray(records) ? records : []);
          setObservationsTotalCount(
            typeof observationsResult.value.totalCount === "number"
              ? observationsResult.value.totalCount
              : Array.isArray(records)
                ? records.length
                : 0
          );
        } else if (observationsResult.status === "rejected") {
          setObservations([]);
          setObservationsTotalCount(null);
          console.error(
            "Unable to load individual Wi-Fi observations:",
            observationsResult.reason
          );
        }
      }

      setLoading(false);
    },
    []
  );

  const loadHeatmap = useCallback(
    (band: BandFilter) => {
      setLoading(true);
      setError("");
      void fetchHeatmap(band, true);
    },
    [fetchHeatmap]
  );

  const handleBandChange = useCallback((band: BandFilter) => {
    setLoading(true);
    setError("");
    setSelectedBand(band);
  }, []);

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(() => {
      if (active) {
        return fetchHeatmap(selectedBand, false);
      }
    });
    return () => {
      active = false;
    };
  }, [fetchHeatmap, selectedBand]);

  const mapCenter = useMemo(
    () => computeGeographicCenter(points, selectedBand),
    [points, selectedBand]
  );

  const locationCounts = useMemo(
    () =>
      computeObservationLocationCounts(
        observations,
        selectedBand,
        locationSummary
      ),
    [observations, selectedBand, locationSummary]
  );

  return (
    <div className="w-full min-w-0 space-y-3">

      {/* COMPACT EMBEDDED MAP SUMMARY & REFRESH */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-1 py-0.5">
        <div className="flex flex-wrap items-center gap-2 text-[11px] text-[#5E6572]">
          <span className="inline-flex items-center gap-1.5 rounded-lg border border-[#303640]/10 bg-[#F7F6F2] px-2.5 py-1 font-medium text-[#303640]">
            <MapPin size={12} className="text-[#9F7C38]" />
            {points.length} mapped cell{points.length === 1 ? "" : "s"}
          </span>
          <span className="inline-flex items-center gap-1.5 rounded-lg border border-[#303640]/10 bg-[#F7F6F2] px-2.5 py-1 font-medium text-[#5E6572]">
            {locationCounts.geolocatedObservations} geolocated
            {locationCounts.nonGeolocatedObservations > 0
              ? ` · ${locationCounts.nonGeolocatedObservations} without GPS`
              : ""}
          </span>
        </div>

        <button
          type="button"
          onClick={() => void loadHeatmap(selectedBand)}
          disabled={loading}
          className="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-xl border border-[#303640]/12 bg-white px-3 text-[11px] font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#ECE9E0]/60 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <RefreshCw
            size={12}
            className={`${loading ? "animate-spin" : ""} text-[#9F7C38]`}
          />
          Refresh map
        </button>
      </div>

      {/* ERROR */}
      {error && (
        <div className="rounded-xl border border-[#B83B3B]/25 bg-[#B83B3B]/[0.06] px-4 py-3 text-xs leading-relaxed text-[#B83B3B]">
          {error}
        </div>
      )}

      {/* MAP */}
      <div className="relative w-full min-w-0">

        <WifiHeatmapMap
          points={points}
          center={mapCenter}
          observations={observations}
          observationsTotalCount={observationsTotalCount}
          selectedBand={selectedBand}
          onBandChange={handleBandChange}
          accuracySummary={accuracySummary}
          locationSummary={locationSummary}
          compact
        />

        {loading && (
          <div className="absolute inset-0 z-[1000] flex items-center justify-center rounded-xl bg-[#F7F6F2]/75 backdrop-blur-[2px]">
            <div className="flex items-center gap-3 rounded-xl border border-[#303640]/10 bg-white px-4 py-3 text-xs font-medium text-[#303640] shadow-lg">
              <RefreshCw
                size={15}
                className="animate-spin text-[#C6A15B]"
              />
              Loading network observations...
            </div>
          </div>
        )}

      </div>

      {/* MAP FOOTNOTE */}
      <div className="flex flex-wrap items-center justify-between gap-2 px-1 pt-1">
        <p className="text-[9px] text-[#868D98]">
          Map data © OpenStreetMap contributors
        </p>

        <p className="text-[9px] text-[#868D98]">
          Signal strength measured in dBm
        </p>
      </div>

    </div>
  );
}