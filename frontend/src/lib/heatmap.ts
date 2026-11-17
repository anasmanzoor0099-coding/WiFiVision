import type {
  HeatmapAccuracySummary,
  HeatmapLocationSummary,
  HeatmapPoint,
  NetworkDensityPoint,
  NetworkObservation,
} from "./dashboard";

export type BandFilter = "all" | "2.4 GHz" | "5 GHz" | "6 GHz";

export type DensityLevel = "Low" | "Medium" | "High";

export type LocationPrecisionLevel = "High" | "Moderate" | "Low" | "Unknown";

export type SignalTier =
  | "Excellent"
  | "Good"
  | "Fair"
  | "Weak"
  | "Very Weak"
  | "Unavailable";

export interface SignalClassification {
  tier: SignalTier;
  label: SignalTier;
  rangeLabel: string;
  color: string;
  badgeClass: string;
}

/**
 * Canonical 5-tier RSSI classification used identically by map markers,
 * thermal glow overlays, popups, legends, and network cards:
 *   - >= -60 dBm : Excellent (#2E7D5B)
 *   - -61 to -70 : Good (#65A30D)
 *   - -71 to -80 : Fair (#C6A15B)
 *   - -81 to -90 : Weak (#D97706)
 *   - < -90 dBm  : Very Weak (#B83B3B)
 */
export const SIGNAL_CLASSIFICATIONS: readonly SignalClassification[] = [
  {
    tier: "Excellent",
    label: "Excellent",
    rangeLabel: "≥ -60 dBm",
    color: "#2E7D5B",
    badgeClass: "border-[#2E7D5B]/25 bg-[#2E7D5B]/10 text-[#2E7D5B]",
  },
  {
    tier: "Good",
    label: "Good",
    rangeLabel: "-61 to -70 dBm",
    color: "#65A30D",
    badgeClass: "border-lime-600/25 bg-lime-600/10 text-lime-700",
  },
  {
    tier: "Fair",
    label: "Fair",
    rangeLabel: "-71 to -80 dBm",
    color: "#C6A15B",
    badgeClass: "border-[#C6A15B]/35 bg-[#C6A15B]/12 text-[#9F7C38]",
  },
  {
    tier: "Weak",
    label: "Weak",
    rangeLabel: "-81 to -90 dBm",
    color: "#D97706",
    badgeClass: "border-amber-600/25 bg-amber-600/10 text-amber-700",
  },
  {
    tier: "Very Weak",
    label: "Very Weak",
    rangeLabel: "< -90 dBm",
    color: "#B83B3B",
    badgeClass: "border-[#B83B3B]/25 bg-[#B83B3B]/10 text-[#B83B3B]",
  },
] as const;

export const UNAVAILABLE_SIGNAL_CLASSIFICATION: SignalClassification = {
  tier: "Unavailable",
  label: "Unavailable",
  rangeLabel: "No signal",
  color: "#868D98",
  badgeClass: "border-[#303640]/15 bg-[#F1EFE9] text-[#5E6572]",
};

export function getSignalClassification(
  signal: number | null | undefined
): SignalClassification {
  if (typeof signal !== "number" || !Number.isFinite(signal)) {
    return UNAVAILABLE_SIGNAL_CLASSIFICATION;
  }
  if (signal >= -60) return SIGNAL_CLASSIFICATIONS[0];
  if (signal >= -70) return SIGNAL_CLASSIFICATIONS[1];
  if (signal >= -80) return SIGNAL_CLASSIFICATIONS[2];
  if (signal >= -90) return SIGNAL_CLASSIFICATIONS[3];
  return SIGNAL_CLASSIFICATIONS[4];
}

export const TEST_OBSERVATION_SSIDS = new Set([
  "wifivision_test",
  "wifivision_geo_test",
]);

export function isTestObservationSsid(ssid: string | null | undefined): boolean {
  if (typeof ssid !== "string") return false;
  const trimmed = ssid.trim().toLowerCase();
  return TEST_OBSERVATION_SSIDS.has(trimmed) || trimmed.startsWith("wifivision_");
}

export function formatObservationSsid(rawSsid: string | null | undefined): {
  displaySsid: string;
  isHidden: boolean;
  groupKey: string;
} {
  const trimmed = typeof rawSsid === "string" ? rawSsid.trim() : "";
  if (!trimmed || trimmed.toLowerCase() === "hidden network") {
    return {
      displaySsid: "Hidden Network",
      isHidden: true,
      groupKey: "__hidden_network__",
    };
  }
  return {
    displaySsid: trimmed,
    isHidden: false,
    groupKey: trimmed.toLowerCase(),
  };
}

export function isValidCoordinate(
  latitude: unknown,
  longitude: unknown
): boolean {
  return (
    typeof latitude === "number" &&
    typeof longitude === "number" &&
    Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= -90 &&
    latitude <= 90 &&
    longitude >= -180 &&
    longitude <= 180
  );
}

export function computeGeographicCenter(
  points: Pick<
    HeatmapPoint,
    "latitude" | "longitude" | "totalObservations" | "frequencyDistribution"
  >[],
  band: BandFilter = "all"
): [number, number] | null {
  let minLat = Infinity;
  let maxLat = -Infinity;
  let minLng = Infinity;
  let maxLng = -Infinity;
  let validCount = 0;

  for (const point of points) {
    if (!isValidCoordinate(point.latitude, point.longitude)) continue;
    if (band !== "all" && getPointBandCount(point, band) <= 0) continue;

    validCount += 1;
    if (point.latitude < minLat) minLat = point.latitude;
    if (point.latitude > maxLat) maxLat = point.latitude;
    if (point.longitude < minLng) minLng = point.longitude;
    if (point.longitude > maxLng) maxLng = point.longitude;
  }

  if (validCount === 0) {
    return null;
  }

  return [(minLat + maxLat) / 2, (minLng + maxLng) / 2];
}

export function normalizeObservationBand(
  observation: Pick<NetworkObservation, "band" | "frequencyMhz">
): string {
  if (observation.band) return observation.band;

  const frequency = observation.frequencyMhz;
  if (typeof frequency === "number" && Number.isFinite(frequency)) {
    if (frequency >= 2400 && frequency < 2500) return "2.4 GHz";
    if (frequency >= 4900 && frequency < 5900) return "5 GHz";
    if (frequency >= 5925 && frequency <= 7125) return "6 GHz";
  }

  return "Unknown";
}

export function computeObservationLocationCounts(
  observations: Pick<NetworkObservation, "ssid" | "band" | "frequencyMhz" | "location">[],
  selectedBand: BandFilter,
  apiSummary?: HeatmapLocationSummary | null
): HeatmapLocationSummary {
  if (
    apiSummary &&
    typeof apiSummary.geolocatedObservations === "number" &&
    typeof apiSummary.nonGeolocatedObservations === "number" &&
    Number.isFinite(apiSummary.geolocatedObservations) &&
    Number.isFinite(apiSummary.nonGeolocatedObservations)
  ) {
    const geolocatedObservations = Math.max(
      0,
      Math.trunc(apiSummary.geolocatedObservations)
    );
    const nonGeolocatedObservations = Math.max(
      0,
      Math.trunc(apiSummary.nonGeolocatedObservations)
    );
    return {
      geolocatedObservations,
      nonGeolocatedObservations,
      totalObservations: geolocatedObservations + nonGeolocatedObservations,
    };
  }

  let geolocatedObservations = 0;
  let nonGeolocatedObservations = 0;

  for (const obs of observations) {
    if (isTestObservationSsid(obs.ssid)) continue;
    if (
      selectedBand !== "all" &&
      normalizeObservationBand(obs) !== selectedBand
    ) {
      continue;
    }

    if (isValidCoordinate(obs.location?.latitude, obs.location?.longitude)) {
      geolocatedObservations += 1;
    } else {
      nonGeolocatedObservations += 1;
    }
  }

  return {
    geolocatedObservations,
    nonGeolocatedObservations,
    totalObservations: geolocatedObservations + nonGeolocatedObservations,
  };
}

export interface NetworkSummary {
  groupKey: string;
  ssid: string;
  isHidden: boolean;
  strongestSignal: number;
  weakestSignal: number;
  observations: number;
  bands: string[];
  security: string;
  latestObservation: string;
}

export function buildNetworkSummaries(
  observations: NetworkObservation[]
): NetworkSummary[] {
  const grouped = new Map<string, NetworkSummary>();

  for (const observation of observations) {
    if (
      typeof observation.signalDbm !== "number" ||
      !Number.isFinite(observation.signalDbm)
    ) {
      continue;
    }

    if (isTestObservationSsid(observation.ssid)) {
      continue;
    }

    const { displaySsid, isHidden, groupKey } = formatObservationSsid(
      observation.ssid
    );
    const band = normalizeObservationBand(observation);
    const observedDate =
      observation.observedAt || observation.createdAt || "";

    const existing = grouped.get(groupKey);

    if (!existing) {
      grouped.set(groupKey, {
        groupKey,
        ssid: displaySsid,
        isHidden,
        strongestSignal: observation.signalDbm,
        weakestSignal: observation.signalDbm,
        observations: 1,
        bands: [band],
        security: observation.security || "Unknown",
        latestObservation: observedDate,
      });
      continue;
    }

    existing.strongestSignal = Math.max(
      existing.strongestSignal,
      observation.signalDbm
    );
    existing.weakestSignal = Math.min(
      existing.weakestSignal,
      observation.signalDbm
    );
    existing.observations += 1;

    if (!existing.bands.includes(band)) {
      existing.bands.push(band);
    }

    if (
      new Date(observedDate).getTime() >
      new Date(existing.latestObservation).getTime()
    ) {
      existing.latestObservation = observedDate;
      existing.security = observation.security || "Unknown";
    }
  }

  return Array.from(grouped.values()).sort(
    (a, b) => b.strongestSignal - a.strongestSignal
  );
}

export const DENSITY_THRESHOLDS = {
  lowMax: 5,
  mediumMax: 20,
} as const;

export const DENSITY_COLORS: Record<DensityLevel, string> = {
  Low: "#64748B",
  Medium: "#6366F1",
  High: "#3730A3",
};

export interface DensityVisuals {
  level: DensityLevel;
  color: string;
  markerRadius: number;
  glowDiameterPx: number;
  glowOpacity: number;
}

export interface LocationPrecisionInfo {
  level: LocationPrecisionLevel;
  label: string;
  shortLabel: string;
  formattedMeters: string;
  badgeClass: string;
  textClass: string;
}

export interface BandSummary {
  locationsCount: number;
  totalObservations: number;
  weightedAverageSignal: number | null;
  uniqueNetworksPeak: number;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function getPointBandCount(
  point: Pick<HeatmapPoint, "totalObservations" | "frequencyDistribution">,
  band: BandFilter
): number {
  if (band === "all") return point.totalObservations;

  return (
    point.frequencyDistribution?.find((item) => item.band === band)?.count ?? 0
  );
}

export interface PopupBandMetrics {
  bandLabel: string;
  averageSignal: number | null;
  strongestSignal: number | null;
  weakestSignal: number | null;
  sampleCount: number;
  totalObservations: number;
}

function toFiniteNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function getPointBandExtremes(
  point: Pick<
    HeatmapPoint,
    "averageSignal" | "strongestSignal" | "weakestSignal" | "frequencyDistribution"
  >,
  band: BandFilter
): { strongestSignal: number | null; weakestSignal: number | null } {
  const pointAvg = toFiniteNumber(point.averageSignal);

  if (band === "all") {
    return {
      strongestSignal: toFiniteNumber(point.strongestSignal) ?? pointAvg,
      weakestSignal: toFiniteNumber(point.weakestSignal) ?? pointAvg,
    };
  }

  const bandEntry = point.frequencyDistribution?.find(
    (item) => item.band === band
  );
  if (!bandEntry || bandEntry.count <= 0) {
    return { strongestSignal: null, weakestSignal: null };
  }

  const fallbackSignal =
    toFiniteNumber(bandEntry.averageSignal) ?? pointAvg;

  return {
    strongestSignal:
      toFiniteNumber(bandEntry.strongestSignal) ?? fallbackSignal,
    weakestSignal:
      toFiniteNumber(bandEntry.weakestSignal) ?? fallbackSignal,
  };
}

export function getPopupBandMetrics(
  point: Pick<
    HeatmapPoint,
    | "totalObservations"
    | "averageSignal"
    | "strongestSignal"
    | "weakestSignal"
    | "frequencyDistribution"
  >,
  band: BandFilter
): PopupBandMetrics {
  const rawSignal = getPointBandSignal(point, band);
  const averageSignal =
    rawSignal !== null ? Math.round(rawSignal * 10) / 10 : null;
  const { strongestSignal, weakestSignal } = getPointBandExtremes(point, band);
  const sampleCount = getPointBandCount(point, band);

  return {
    bandLabel: band === "all" ? "all" : band,
    averageSignal,
    strongestSignal,
    weakestSignal,
    sampleCount,
    totalObservations: point.totalObservations,
  };
}

export function getPointBandSignal(
  point: Pick<HeatmapPoint, "averageSignal" | "frequencyDistribution">,
  band: BandFilter
): number | null {
  if (band === "all") {
    return typeof point.averageSignal === "number" &&
      Number.isFinite(point.averageSignal)
      ? point.averageSignal
      : null;
  }

  const bandEntry = point.frequencyDistribution?.find(
    (item) => item.band === band
  );
  if (!bandEntry || bandEntry.count <= 0) return null;

  if (
    typeof bandEntry.averageSignal === "number" &&
    Number.isFinite(bandEntry.averageSignal)
  ) {
    return bandEntry.averageSignal;
  }

  return typeof point.averageSignal === "number" &&
    Number.isFinite(point.averageSignal)
    ? point.averageSignal
    : null;
}

export function getDensityLevel(observationCount: number): DensityLevel {
  if (observationCount <= DENSITY_THRESHOLDS.lowMax) return "Low";
  if (observationCount <= DENSITY_THRESHOLDS.mediumMax) return "Medium";
  return "High";
}

export function getDensityVisuals(
  observationCount: number,
  maxVisibleCount: number,
  zoom = 13
): DensityVisuals {
  const safeCount = Math.max(0, observationCount);
  const safeMax = Math.max(safeCount, maxVisibleCount, 1);
  const level = getDensityLevel(safeCount);
  const color = DENSITY_COLORS[level];

  const normalizedRatio =
    safeMax > 1 ? clamp((safeCount - 1) / (safeMax - 1), 0, 1) : 0;

  const markerZoomFactor = clamp(0.82 + (zoom - 13) * 0.08, 0.72, 1.3);
  const glowZoomFactor = clamp(0.75 + (zoom - 13) * 0.08, 0.65, 1.35);

  const tierBaseRadius =
    level === "Low" ? 6 : level === "Medium" ? 9.5 : 13;
  const tierBaseGlow =
    level === "Low" ? 140 : level === "Medium" ? 215 : 290;
  const glowOpacity =
    level === "Low" ? 0.36 : level === "Medium" ? 0.54 : 0.72;

  const markerRadius = clamp(
    Math.round((tierBaseRadius + normalizedRatio * 2) * markerZoomFactor * 10) /
      10,
    5,
    18
  );

  const glowDiameterPx = Math.round(
    clamp((tierBaseGlow + normalizedRatio * 30) * glowZoomFactor, 90, 390)
  );

  return {
    level,
    color,
    markerRadius,
    glowDiameterPx,
    glowOpacity,
  };
}

export function getSignalMarkerRadius(zoom = 13): number {
  const zoomFactor = clamp(0.82 + (zoom - 13) * 0.08, 0.72, 1.3);
  return clamp(Math.round(7 * zoomFactor * 10) / 10, 5, 11);
}

export function getSignalGlowDiameter(zoom = 13): number {
  const zoomFactor = clamp(0.78 + (zoom - 13) * 0.08, 0.65, 1.35);
  return Math.round(clamp(240 * zoomFactor, 150, 340));
}

export function getLocationPrecisionInfo(
  accuracyMeters: number | null | undefined
): LocationPrecisionInfo {
  if (
    typeof accuracyMeters !== "number" ||
    !Number.isFinite(accuracyMeters) ||
    accuracyMeters < 0
  ) {
    return {
      level: "Unknown",
      label: "Unknown",
      shortLabel: "Unknown",
      formattedMeters: "Unknown",
      badgeClass: "border-[#303640]/15 bg-[#F1EFE9] text-[#5E6572]",
      textClass: "text-[#5E6572]",
    };
  }

  const formattedMeters = `±${accuracyMeters.toFixed(1)} m`;

  if (accuracyMeters <= 20) {
    return {
      level: "High",
      label: "High location precision",
      shortLabel: "High precision",
      formattedMeters,
      badgeClass: "border-[#2E7D5B]/25 bg-[#2E7D5B]/10 text-[#2E7D5B]",
      textClass: "text-[#2E7D5B]",
    };
  }

  if (accuracyMeters <= 50) {
    return {
      level: "Moderate",
      label: "Moderate location precision",
      shortLabel: "Moderate precision",
      formattedMeters,
      badgeClass: "border-[#C6A15B]/35 bg-[#C6A15B]/12 text-[#9F7C38]",
      textClass: "text-[#9F7C38]",
    };
  }

  return {
    level: "Low",
    label: "Low location precision",
    shortLabel: "Low precision (coarse)",
    formattedMeters,
    badgeClass: "border-[#B83B3B]/25 bg-[#B83B3B]/10 text-[#B83B3B]",
    textClass: "text-[#B83B3B]",
  };
}

export function computeHeatmapBandSummary(
  points: HeatmapPoint[],
  density: NetworkDensityPoint[],
  selectedBand: BandFilter
): BandSummary {
  let locationsCount = 0;
  let totalObs = 0;
  let weightedSignal = 0;
  let weightedObs = 0;
  let uniqueNetworksPeak = 0;
  const activeCellKeys = new Set<string>();

  points.forEach((point) => {
    const count = getPointBandCount(point, selectedBand);
    if (count <= 0) return;

    locationsCount += 1;
    totalObs += count;
    activeCellKeys.add(`${point.latitude},${point.longitude}`);

    const signal = getPointBandSignal(point, selectedBand);
    if (signal !== null) {
      weightedSignal += signal * count;
      weightedObs += count;
    }
  });

  density.forEach((cell) => {
    const cellKey = `${cell.latitude},${cell.longitude}`;
    if (selectedBand !== "all" && !activeCellKeys.has(cellKey)) {
      return;
    }
    if (cell.uniqueNetworkCount > uniqueNetworksPeak) {
      uniqueNetworksPeak = cell.uniqueNetworkCount;
    }
  });

  return {
    locationsCount,
    totalObservations: totalObs,
    weightedAverageSignal:
      weightedObs > 0
        ? Math.round((weightedSignal / weightedObs) * 10) / 10
        : null,
    uniqueNetworksPeak,
  };
}

export function computeHeatmapAccuracySummary(
  points: HeatmapPoint[],
  selectedBand: BandFilter,
  apiSummary?: HeatmapAccuracySummary | null
): HeatmapAccuracySummary {
  if (
    apiSummary &&
    typeof apiSummary.withAccuracyCount === "number" &&
    typeof apiSummary.withoutAccuracyCount === "number" &&
    typeof apiSummary.coarseAccuracyCount === "number"
  ) {
    return apiSummary;
  }

  let weightedAccuracySum = 0;
  let withAccuracyCount = 0;
  let withoutAccuracyCount = 0;
  let coarseAccuracyCount = 0;

  points.forEach((point) => {
    const visibleCount = getPointBandCount(point, selectedBand);
    if (visibleCount <= 0) return;

    const hasValidAvg =
      typeof point.avgAccuracyMeters === "number" &&
      Number.isFinite(point.avgAccuracyMeters) &&
      point.avgAccuracyMeters >= 0;

    const hasExplicitCounts =
      typeof point.accuracySampleCount === "number" &&
      Number.isFinite(point.accuracySampleCount);

    if (hasExplicitCounts) {
      const sampleCount = Math.max(0, Math.trunc(point.accuracySampleCount!));
      const missingCount =
        typeof point.missingAccuracyCount === "number" &&
        Number.isFinite(point.missingAccuracyCount)
          ? Math.max(0, Math.trunc(point.missingAccuracyCount))
          : Math.max(0, visibleCount - sampleCount);
      const coarseCount =
        typeof point.coarseAccuracyCount === "number" &&
        Number.isFinite(point.coarseAccuracyCount)
          ? Math.max(0, Math.trunc(point.coarseAccuracyCount))
          : 0;

      if (hasValidAvg && sampleCount > 0) {
        weightedAccuracySum += point.avgAccuracyMeters! * sampleCount;
        withAccuracyCount += sampleCount;
      }
      withoutAccuracyCount += missingCount;
      coarseAccuracyCount += coarseCount;
      return;
    }

    if (hasValidAvg) {
      weightedAccuracySum += point.avgAccuracyMeters! * visibleCount;
      withAccuracyCount += visibleCount;
      if (point.avgAccuracyMeters! > 50) {
        coarseAccuracyCount += visibleCount;
      }
    } else {
      withoutAccuracyCount += visibleCount;
    }
  });

  return {
    averageAccuracyMeters:
      withAccuracyCount > 0
        ? Math.round((weightedAccuracySum / withAccuracyCount) * 10) / 10
        : null,
    withAccuracyCount,
    withoutAccuracyCount,
    coarseAccuracyCount,
  };
}
