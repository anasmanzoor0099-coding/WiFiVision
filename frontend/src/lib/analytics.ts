import type {
  NetworkObservation,
  NetworkQualityItem,
  NetworkQualityResponse,
  ObservationAnalytics,
} from "./dashboard";
import { type BandPreference, getLocalPreferences } from "./preferences";

export type AnalyticsBandFilter = BandPreference | "Unknown";

export const ANALYTICS_BAND_OPTIONS: readonly AnalyticsBandFilter[] = [
  "all",
  "2.4 GHz",
  "5 GHz",
  "6 GHz",
  "Unknown",
] as const;

const ANALYTICS_BAND_STORAGE_KEY = "wifivision.analytics.selectedBand.v1";

export const ANALYTICS_BAND_COLORS: Record<string, string> = {
  "2.4 GHz": "#C6A15B",
  "5 GHz": "#2E7D5B",
  "6 GHz": "#3B6FB6",
  Unknown: "#868D98",
};

export const ANALYTICS_SECURITY_COLORS = [
  "#303640",
  "#C6A15B",
  "#2E7D5B",
  "#B67B28",
  "#B83B3B",
  "#868D98",
] as const;

export const ANALYTICS_QUALITY_COLORS: Record<string, string> = {
  Excellent: "#2E7D5B",
  Good: "#4E8E72",
  Fair: "#C6A15B",
  Poor: "#B83B3B",
};

export function isValidAnalyticsBand(
  value: unknown
): value is AnalyticsBandFilter {
  return (
    value === "all" ||
    value === "2.4 GHz" ||
    value === "5 GHz" ||
    value === "6 GHz" ||
    value === "Unknown"
  );
}

export function getPersistedAnalyticsBand(): AnalyticsBandFilter {
  const defaultPref = getLocalPreferences().defaultBand;
  if (typeof window === "undefined") {
    return defaultPref;
  }

  try {
    const raw = window.sessionStorage.getItem(ANALYTICS_BAND_STORAGE_KEY);
    if (isValidAnalyticsBand(raw)) {
      return raw;
    }
  } catch {
    // Ignore storage restriction and fall back to configured default preference
  }

  return defaultPref;
}

export function persistAnalyticsBand(band: AnalyticsBandFilter): void {
  if (typeof window === "undefined" || !isValidAnalyticsBand(band)) {
    return;
  }

  try {
    window.sessionStorage.setItem(ANALYTICS_BAND_STORAGE_KEY, band);
  } catch {
    // Ignore storage unavailability
  }
}

export interface AnalyticsViewModelInput {
  selectedBand: AnalyticsBandFilter;
  analytics: ObservationAnalytics | null;
  overallAnalytics: ObservationAnalytics | null;
  qualityData: NetworkQualityResponse | null;
  observations: NetworkObservation[];
  obsTotalCount: number | null;
  obsHasMore: boolean;
}

export function computeAnalyticsViewModel({
  selectedBand,
  analytics,
  overallAnalytics,
  qualityData,
  observations,
  obsTotalCount,
  obsHasMore,
}: AnalyticsViewModelInput) {
  // Frequency Band Distribution is always an all-band spectrum aggregate so
  // selecting one band never collapses it into a 100% single-slice donut.
  const spectrumSource =
    overallAnalytics ?? (selectedBand === "all" ? analytics : null);

  const bandChartData =
    spectrumSource?.bands && spectrumSource.bands.length > 0
      ? (() => {
          const total = spectrumSource.bands.reduce(
            (sum, item) => sum + item.count,
            0
          );
          return spectrumSource.bands
            .map((item) => {
              const name = item._id || "Unknown";
              return {
                name,
                count: item.count,
                percentage: total > 0 ? Math.round((item.count / total) * 100) : 0,
                color: ANALYTICS_BAND_COLORS[name] ?? "#868D98",
                isSelected: selectedBand !== "all" && name === selectedBand,
              };
            })
            .sort((a, b) => b.count - a.count);
        })()
      : [];

  const spectrumTotalScans =
    spectrumSource?.totalNetworks ??
    bandChartData.reduce((sum, item) => sum + item.count, 0);

  const securityChartData =
    analytics?.security && analytics.security.length > 0
      ? (() => {
          const total = analytics.security.reduce(
            (sum, item) => sum + item.count,
            0
          );
          return analytics.security
            .map((item, idx) => {
              const name = item._id || "Unknown";
              return {
                name,
                count: item.count,
                percentage: total > 0 ? Math.round((item.count / total) * 100) : 0,
                color:
                  ANALYTICS_SECURITY_COLORS[
                    idx % ANALYTICS_SECURITY_COLORS.length
                  ],
              };
            })
            .sort((a, b) => b.count - a.count);
        })()
      : [];

  const filteredQualityNetworks: NetworkQualityItem[] = (() => {
    const items = qualityData?.networks ?? [];
    if (selectedBand === "all") return items;
    return items.filter(
      (item) => (item.quality.details?.band || "Unknown") === selectedBand
    );
  })();

  const filteredObservations =
    selectedBand === "all"
      ? observations
      : observations.filter((obs) => (obs.band || "Unknown") === selectedBand);

  const qualityDistribution = (() => {
    if (filteredQualityNetworks.length === 0) return [];
    const counts = new Map<string, number>();
    for (const item of filteredQualityNetworks) {
      const cls = item.quality.classification || "Unknown";
      counts.set(cls, (counts.get(cls) ?? 0) + 1);
    }
    const order = ["Excellent", "Good", "Fair", "Poor"];
    const orderedKeys = Array.from(counts.keys()).sort((a, b) => {
      const ia = order.indexOf(a);
      const ib = order.indexOf(b);
      if (ia === -1 && ib === -1) return a.localeCompare(b);
      if (ia === -1) return 1;
      if (ib === -1) return -1;
      return ia - ib;
    });
    const total = filteredQualityNetworks.length;
    return orderedKeys.map((name) => {
      const count = counts.get(name) ?? 0;
      return {
        name,
        count,
        percentage: total > 0 ? Math.round((count / total) * 100) : 0,
        color: ANALYTICS_QUALITY_COLORS[name] ?? "#868D98",
      };
    });
  })();

  const qualityComponentAverages = (() => {
    if (filteredQualityNetworks.length === 0) return null;
    const len = filteredQualityNetworks.length;
    let signalSum = 0;
    let levelSum = 0;
    let bandSum = 0;
    let secSum = 0;
    for (const item of filteredQualityNetworks) {
      signalSum += item.quality.components.signalScore;
      levelSum += item.quality.components.signalLevelScore;
      bandSum += item.quality.components.bandScore;
      secSum += item.quality.components.securityScore;
    }
    return [
      { metric: "Signal RSSI (70%)", avg: Math.round(signalSum / len), max: 100 },
      { metric: "Signal Level (15%)", avg: Math.round(levelSum / len), max: 100 },
      { metric: "Band Score (5%)", avg: Math.round(bandSum / len), max: 100 },
      { metric: "Security (10%)", avg: Math.round(secSum / len), max: 100 },
    ];
  })();

  const signalBuckets = (() => {
    if (filteredObservations.length === 0) return [];
    const buckets = [
      { label: "Strong (>= -50 dBm)", count: 0, color: "#2E7D5B" },
      { label: "Good (-51 to -65 dBm)", count: 0, color: "#4E8E72" },
      { label: "Fair (-66 to -75 dBm)", count: 0, color: "#C6A15B" },
      { label: "Weak (< -75 dBm)", count: 0, color: "#B83B3B" },
    ];
    for (const obs of filteredObservations) {
      if (obs.signalDbm >= -50) buckets[0].count += 1;
      else if (obs.signalDbm >= -65) buckets[1].count += 1;
      else if (obs.signalDbm >= -75) buckets[2].count += 1;
      else buckets[3].count += 1;
    }
    return buckets;
  })();

  const filteredAvgQualityScore = (() => {
    if (selectedBand === "all") {
      return qualityData?.averageScore ?? null;
    }
    if (filteredQualityNetworks.length === 0) return null;
    const sum = filteredQualityNetworks.reduce(
      (acc, item) => acc + item.quality.score,
      0
    );
    return sum / filteredQualityNetworks.length;
  })();

  const sampleSignalSpan = (() => {
    if (filteredObservations.length === 0) {
      return { weakest: null as number | null, strongest: null as number | null };
    }
    let weakest = Infinity;
    let strongest = -Infinity;
    for (const obs of filteredObservations) {
      if (typeof obs.signalDbm === "number" && Number.isFinite(obs.signalDbm)) {
        if (obs.signalDbm < weakest) weakest = obs.signalDbm;
        if (obs.signalDbm > strongest) strongest = obs.signalDbm;
      }
    }
    return {
      weakest: Number.isFinite(weakest) ? weakest : null,
      strongest: Number.isFinite(strongest) ? strongest : null,
    };
  })();

  const bandScopeLabel = selectedBand === "all" ? "all bands" : selectedBand;
  const totalKnownObservations = obsTotalCount ?? observations.length;
  const qualitySampleCount =
    qualityData?.count ?? (qualityData?.networks?.length ?? 0);
  const qualityIsPartialSample =
    obsTotalCount !== null && qualitySampleCount < obsTotalCount;
  const obsIsPartialSample =
    obsHasMore && totalKnownObservations > observations.length;

  const isStatsUnavailable = analytics === null;
  const isBandEmpty =
    analytics !== null &&
    analytics.totalNetworks === 0 &&
    selectedBand !== "all";

  return {
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
  };
}
