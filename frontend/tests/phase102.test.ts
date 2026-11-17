import test from "node:test";
import assert from "node:assert/strict";

import {
  ANALYTICS_BAND_OPTIONS,
  computeAnalyticsViewModel,
  getPersistedAnalyticsBand,
  isValidAnalyticsBand,
  persistAnalyticsBand,
} from "../src/lib/analytics";
import { getPopupBandMetrics } from "../src/lib/heatmap";
import type {
  HeatmapPoint,
  NetworkObservation,
  NetworkQualityResponse,
  ObservationAnalytics,
} from "../src/lib/dashboard";

function makeObs(
  id: string,
  band: string,
  signalDbm: number,
  security = "WPA2"
): NetworkObservation {
  return {
    _id: id,
    ssid: `Net_${id}`,
    signalDbm,
    frequencyMhz: band === "5 GHz" ? 5180 : band === "6 GHz" ? 5955 : 2412,
    band,
    security,
    signalQuality: 3,
    source: "windows",
    observedAt: "2026-11-17T10:00:00.000Z",
    createdAt: "2026-11-17T10:00:00.000Z",
    updatedAt: "2026-11-17T10:00:00.000Z",
  };
}

const overallAnalytics: ObservationAnalytics = {
  totalNetworks: 6,
  averageSignal: -62,
  strongestSignal: -45,
  weakestSignal: -85,
  filteredBand: null,
  bands: [
    { _id: "2.4 GHz", count: 3 },
    { _id: "5 GHz", count: 2 },
    { _id: "Unknown", count: 1 },
  ],
  security: [
    { _id: "WPA2", count: 3 },
    { _id: "WPA3", count: 2 },
    { _id: "Open", count: 1 },
  ],
};

const qualityResponse: NetworkQualityResponse = {
  success: true,
  count: 6,
  averageScore: 76,
  networks: [
    {
      observationId: "1",
      ssid: "Net_1",
      observedAt: "2026-11-17T10:00:00.000Z",
      quality: {
        score: 90,
        classification: "Excellent",
        components: {
          signalScore: 100,
          signalLevelScore: 75,
          bandScore: 100,
          securityScore: 90,
        },
        details: { signalDbm: -45, band: "2.4 GHz", security: "WPA2" },
      },
    },
    {
      observationId: "2",
      ssid: "Net_2",
      observedAt: "2026-11-17T10:00:00.000Z",
      quality: {
        score: 48,
        classification: "Poor",
        components: {
          signalScore: 35,
          signalLevelScore: 50,
          bandScore: 100,
          securityScore: 20,
        },
        details: { signalDbm: -85, band: "2.4 GHz", security: "Open" },
      },
    },
    {
      observationId: "3",
      ssid: "Net_3",
      observedAt: "2026-11-17T10:00:00.000Z",
      quality: {
        score: 86,
        classification: "Excellent",
        components: {
          signalScore: 85,
          signalLevelScore: 75,
          bandScore: 100,
          securityScore: 100,
        },
        details: { signalDbm: -50, band: "5 GHz", security: "WPA3" },
      },
    },
    {
      observationId: "4",
      ssid: "Net_4",
      observedAt: "2026-11-17T10:00:00.000Z",
      quality: {
        score: 70,
        classification: "Good",
        components: {
          signalScore: 55,
          signalLevelScore: 75,
          bandScore: 50,
          securityScore: 90,
        },
        details: { signalDbm: -68, band: "Unknown", security: "WPA2" },
      },
    },
  ],
};

const sampleObservations: NetworkObservation[] = [
  makeObs("1", "2.4 GHz", -45, "WPA2"),
  makeObs("2", "2.4 GHz", -85, "Open"),
  makeObs("3", "5 GHz", -50, "WPA3"),
  makeObs("4", "Unknown", -68, "WPA2"),
];

test("Analytics band options cover all five supported filter states cleanly", () => {
  assert.deepEqual(
    [...ANALYTICS_BAND_OPTIONS],
    ["all", "2.4 GHz", "5 GHz", "6 GHz", "Unknown"]
  );
  for (const band of ANALYTICS_BAND_OPTIONS) {
    assert.equal(isValidAnalyticsBand(band), true);
  }
  assert.equal(isValidAnalyticsBand("3.6 GHz"), false);
});

test("Analytics selected filter persists in sessionStorage cleanly across interactions", () => {
  const originalWindow = (globalThis as { window?: unknown }).window;
  const sessionStore = new Map<string, string>();
  const localStore = new Map<string, string>([
    [
      "wifivision.localPreferences.v1",
      JSON.stringify({ defaultBand: "2.4 GHz" }),
    ],
  ]);

  (globalThis as { window?: unknown }).window = {
    sessionStorage: {
      getItem: (key: string) => sessionStore.get(key) ?? null,
      setItem: (key: string, value: string) => {
        sessionStore.set(key, value);
      },
    },
    localStorage: {
      getItem: (key: string) => localStore.get(key) ?? null,
      setItem: (key: string, value: string) => {
        localStore.set(key, value);
      },
    },
  };

  try {
    // Falls back to Settings defaultBand before any explicit analytics selection
    assert.equal(getPersistedAnalyticsBand(), "2.4 GHz");

    persistAnalyticsBand("Unknown");
    assert.equal(getPersistedAnalyticsBand(), "Unknown");

    persistAnalyticsBand("5 GHz");
    assert.equal(getPersistedAnalyticsBand(), "5 GHz");

    // Corrupted session value gracefully falls back to configured default
    sessionStore.set("wifivision.analytics.selectedBand.v1", "invalid-band");
    assert.equal(getPersistedAnalyticsBand(), "2.4 GHz");
  } finally {
    if (originalWindow === undefined) {
      delete (globalThis as { window?: unknown }).window;
    } else {
      (globalThis as { window?: unknown }).window = originalWindow;
    }
  }
});

test("computeAnalyticsViewModel handles All, 2.4 GHz, 5 GHz, Unknown, empty 6 GHz, and unavailable stats consistently", () => {
  // 1. All Bands
  const allVm = computeAnalyticsViewModel({
    selectedBand: "all",
    analytics: overallAnalytics,
    overallAnalytics,
    qualityData: qualityResponse,
    observations: sampleObservations,
    obsTotalCount: 6,
    obsHasMore: true,
  });
  assert.equal(allVm.spectrumTotalScans, 6);
  assert.equal(allVm.bandChartData.length, 3);
  assert.equal(allVm.filteredAvgQualityScore, 76);
  assert.equal(allVm.filteredObservations.length, 4);
  assert.deepEqual(allVm.sampleSignalSpan, { weakest: -85, strongest: -45 });
  assert.equal(allVm.isBandEmpty, false);
  assert.equal(allVm.isStatsUnavailable, false);

  // 2. 5 GHz filter keeps Frequency Band Distribution as all-band aggregate while filtering security/quality/signal
  const band5Analytics: ObservationAnalytics = {
    totalNetworks: 2,
    averageSignal: -60,
    strongestSignal: -50,
    weakestSignal: -70,
    filteredBand: "5 GHz",
    bands: [{ _id: "5 GHz", count: 2 }],
    security: [{ _id: "WPA3", count: 2 }],
  };
  const band5Vm = computeAnalyticsViewModel({
    selectedBand: "5 GHz",
    analytics: band5Analytics,
    overallAnalytics,
    qualityData: qualityResponse,
    observations: sampleObservations,
    obsTotalCount: 6,
    obsHasMore: false,
  });
  assert.equal(band5Vm.spectrumTotalScans, 6);
  assert.equal(band5Vm.bandChartData.length, 3);
  assert.equal(
    band5Vm.bandChartData.find((b) => b.name === "5 GHz")?.isSelected,
    true
  );
  assert.equal(
    band5Vm.bandChartData.find((b) => b.name === "2.4 GHz")?.isSelected,
    false
  );
  assert.equal(band5Vm.securityChartData[0]?.name, "WPA3");
  assert.equal(band5Vm.filteredAvgQualityScore, 86);
  assert.equal(band5Vm.filteredObservations.length, 1);
  assert.deepEqual(band5Vm.sampleSignalSpan, { weakest: -50, strongest: -50 });

  // 3. Unknown band filter
  const unknownAnalytics: ObservationAnalytics = {
    totalNetworks: 1,
    averageSignal: -68,
    strongestSignal: -68,
    weakestSignal: -68,
    filteredBand: "Unknown",
    bands: [{ _id: "Unknown", count: 1 }],
    security: [{ _id: "WPA2", count: 1 }],
  };
  const unknownVm = computeAnalyticsViewModel({
    selectedBand: "Unknown",
    analytics: unknownAnalytics,
    overallAnalytics,
    qualityData: qualityResponse,
    observations: sampleObservations,
    obsTotalCount: 6,
    obsHasMore: false,
  });
  assert.equal(unknownVm.filteredAvgQualityScore, 70);
  assert.equal(unknownVm.filteredObservations.length, 1);
  assert.equal(unknownVm.qualityDistribution[0]?.name, "Good");

  // 4. Empty 6 GHz band state preserves overall spectrum aggregate without misleading counts
  const empty6Analytics: ObservationAnalytics = {
    totalNetworks: 0,
    averageSignal: null,
    strongestSignal: null,
    weakestSignal: null,
    filteredBand: "6 GHz",
    bands: [],
    security: [],
  };
  const empty6Vm = computeAnalyticsViewModel({
    selectedBand: "6 GHz",
    analytics: empty6Analytics,
    overallAnalytics,
    qualityData: qualityResponse,
    observations: sampleObservations,
    obsTotalCount: 6,
    obsHasMore: false,
  });
  assert.equal(empty6Vm.isBandEmpty, true);
  assert.equal(empty6Vm.bandChartData.length, 3);
  assert.equal(empty6Vm.securityChartData.length, 0);
  assert.equal(empty6Vm.qualityDistribution.length, 0);
  assert.equal(empty6Vm.signalBuckets.length, 0);
  assert.equal(empty6Vm.filteredAvgQualityScore, null);
  assert.deepEqual(empty6Vm.sampleSignalSpan, { weakest: null, strongest: null });

  // 5. Unavailable stats API marks isStatsUnavailable cleanly
  const unavailableVm = computeAnalyticsViewModel({
    selectedBand: "2.4 GHz",
    analytics: null,
    overallAnalytics,
    qualityData: null,
    observations: sampleObservations,
    obsTotalCount: 6,
    obsHasMore: false,
  });
  assert.equal(unavailableVm.isStatsUnavailable, true);
  assert.equal(unavailableVm.filteredAvgQualityScore, null);
});

test("getPopupBandMetrics handles All Bands, 2.4/5/6 GHz, null/NaN fields, and legacy cached frequencyDistribution without crashing", () => {
  const fullPoint: HeatmapPoint = {
    latitude: 29.375,
    longitude: 71.752,
    totalObservations: 5,
    averageSignal: -63.44,
    strongestSignal: -45,
    weakestSignal: -85,
    frequencyDistribution: [
      {
        band: "2.4 GHz",
        count: 3,
        averageSignal: -65.24,
        strongestSignal: -45,
        weakestSignal: -85,
      },
      {
        band: "5 GHz",
        count: 2,
        averageSignal: -60.75,
        strongestSignal: -50,
        weakestSignal: -70,
      },
      {
        band: "6 GHz",
        count: 0,
        averageSignal: null,
        strongestSignal: null,
        weakestSignal: null,
      },
    ],
  };

  assert.deepEqual(getPopupBandMetrics(fullPoint, "all"), {
    bandLabel: "all",
    averageSignal: -63.4,
    strongestSignal: -45,
    weakestSignal: -85,
    sampleCount: 5,
    totalObservations: 5,
  });
  assert.deepEqual(getPopupBandMetrics(fullPoint, "2.4 GHz"), {
    bandLabel: "2.4 GHz",
    averageSignal: -65.2,
    strongestSignal: -45,
    weakestSignal: -85,
    sampleCount: 3,
    totalObservations: 5,
  });
  assert.deepEqual(getPopupBandMetrics(fullPoint, "5 GHz"), {
    bandLabel: "5 GHz",
    averageSignal: -60.7,
    strongestSignal: -50,
    weakestSignal: -70,
    sampleCount: 2,
    totalObservations: 5,
  });
  assert.deepEqual(getPopupBandMetrics(fullPoint, "6 GHz"), {
    bandLabel: "6 GHz",
    averageSignal: null,
    strongestSignal: null,
    weakestSignal: null,
    sampleCount: 0,
    totalObservations: 5,
  });

  // Older cached point with missing extremes + NaN average falls back safely without returning NaN or 0
  const degeneratePoint: HeatmapPoint = {
    latitude: 29.375,
    longitude: 71.752,
    totalObservations: 4,
    averageSignal: Number.NaN as unknown as number,
    strongestSignal: Number.NaN as unknown as number,
    weakestSignal: null as unknown as number,
    frequencyDistribution: [
      {
        band: "5 GHz",
        count: 2,
        averageSignal: -61.36,
      },
      {
        band: "2.4 GHz",
        count: 2,
        averageSignal: null,
      },
    ],
  };

  assert.deepEqual(getPopupBandMetrics(degeneratePoint, "all"), {
    bandLabel: "all",
    averageSignal: null,
    strongestSignal: null,
    weakestSignal: null,
    sampleCount: 4,
    totalObservations: 4,
  });
  assert.deepEqual(getPopupBandMetrics(degeneratePoint, "5 GHz"), {
    bandLabel: "5 GHz",
    averageSignal: -61.4,
    strongestSignal: -61.36,
    weakestSignal: -61.36,
    sampleCount: 2,
    totalObservations: 4,
  });
  assert.deepEqual(getPopupBandMetrics(degeneratePoint, "2.4 GHz"), {
    bandLabel: "2.4 GHz",
    averageSignal: null,
    strongestSignal: null,
    weakestSignal: null,
    sampleCount: 2,
    totalObservations: 4,
  });
});
