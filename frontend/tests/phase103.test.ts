import test from "node:test";
import assert from "node:assert/strict";

import {
  SIGNAL_CLASSIFICATIONS,
  UNAVAILABLE_SIGNAL_CLASSIFICATION,
  buildNetworkSummaries,
  computeGeographicCenter,
  computeHeatmapBandSummary,
  computeObservationLocationCounts,
  formatObservationSsid,
  getPopupBandMetrics,
  getSignalClassification,
  isValidCoordinate,
} from "../src/lib/heatmap";
import type { HeatmapPoint, NetworkObservation } from "../src/lib/dashboard";

function makeObservation(
  id: string,
  overrides: Partial<NetworkObservation> = {}
): NetworkObservation {
  return {
    _id: id,
    ssid: `Net_${id}`,
    signalDbm: -62,
    frequencyMhz: 2412,
    band: "2.4 GHz",
    security: "WPA2",
    signalQuality: 3,
    source: "windows",
    observedAt: "2026-11-17T12:00:00.000Z",
    createdAt: "2026-11-17T12:00:00.000Z",
    updatedAt: "2026-11-17T12:00:00.000Z",
    ...overrides,
  };
}

test("Signal classification covers all 5 legend tiers consistently plus Unavailable", () => {
  assert.equal(SIGNAL_CLASSIFICATIONS.length, 5);

  const testCases: Array<{
    signal: number | null | undefined;
    tier: string;
    color: string;
  }> = [
    { signal: -50, tier: "Excellent", color: "#2E7D5B" },
    { signal: -60, tier: "Excellent", color: "#2E7D5B" },
    { signal: -65, tier: "Good", color: "#65A30D" },
    { signal: -70, tier: "Good", color: "#65A30D" },
    { signal: -75, tier: "Fair", color: "#C6A15B" },
    { signal: -80, tier: "Fair", color: "#C6A15B" },
    { signal: -85, tier: "Weak", color: "#D97706" },
    { signal: -90, tier: "Weak", color: "#D97706" },
    { signal: -95, tier: "Very Weak", color: "#B83B3B" },
    { signal: null, tier: "Unavailable", color: "#868D98" },
    { signal: undefined, tier: "Unavailable", color: "#868D98" },
    { signal: Number.NaN, tier: "Unavailable", color: "#868D98" },
  ];

  for (const tc of testCases) {
    const res = getSignalClassification(tc.signal);
    assert.equal(res.tier, tc.tier);
    assert.equal(res.label, tc.tier);
    assert.equal(res.color, tc.color);
    if (tc.tier === "Unavailable") {
      assert.deepEqual(res, UNAVAILABLE_SIGNAL_CLASSIFICATION);
    } else {
      assert.ok(
        SIGNAL_CLASSIFICATIONS.some(
          (legendEntry) =>
            legendEntry.tier === res.tier && legendEntry.color === res.color
        ),
        `Legend must contain classification ${res.tier}`
      );
    }
  }
});

test("isValidCoordinate and computeGeographicCenter validate bounds, handle empty maps without hardcoded fallback, and compute bounding midpoint", () => {
  assert.equal(isValidCoordinate(29.375, 71.752), true);
  assert.equal(isValidCoordinate(-90, 180), true);
  assert.equal(isValidCoordinate(90, -180), true);

  assert.equal(isValidCoordinate(null, 71.752), false);
  assert.equal(isValidCoordinate(29.375, undefined), false);
  assert.equal(isValidCoordinate(Number.NaN, 71.752), false);
  assert.equal(isValidCoordinate(29.375, Number.POSITIVE_INFINITY), false);
  assert.equal(isValidCoordinate(91, 0), false);
  assert.equal(isValidCoordinate(0, -181), false);
  assert.equal(isValidCoordinate("29.375", "71.752"), false);

  // Empty map returns null (never hardcoded Bahawalpur coordinates [29.396, 71.684])
  assert.equal(computeGeographicCenter([]), null);

  // All invalid coordinates returns null
  assert.equal(
    computeGeographicCenter([
      {
        latitude: Number.NaN,
        longitude: 71.7,
        totalObservations: 2,
        frequencyDistribution: [],
      },
      {
        latitude: 120,
        longitude: 71.7,
        totalObservations: 2,
        frequencyDistribution: [],
      },
    ]),
    null
  );

  // Computes bounding midpoint respecting band filter
  const points: HeatmapPoint[] = [
    {
      latitude: 10,
      longitude: 20,
      totalObservations: 2,
      averageSignal: -60,
      strongestSignal: -55,
      weakestSignal: -65,
      frequencyDistribution: [
        { band: "2.4 GHz", count: 2, averageSignal: -60 },
        { band: "5 GHz", count: 0, averageSignal: null },
      ],
    },
    {
      latitude: 30,
      longitude: 60,
      totalObservations: 3,
      averageSignal: -70,
      strongestSignal: -65,
      weakestSignal: -75,
      frequencyDistribution: [
        { band: "2.4 GHz", count: 1, averageSignal: -70 },
        { band: "5 GHz", count: 2, averageSignal: -70 },
      ],
    },
  ];

  assert.deepEqual(computeGeographicCenter(points, "all"), [20, 40]);
  assert.deepEqual(computeGeographicCenter(points, "5 GHz"), [30, 60]);
  assert.equal(computeGeographicCenter(points, "6 GHz"), null);
});

test("computeObservationLocationCounts accurately separates geolocated vs non-geolocated observations without double-counting", () => {
  const mixedObservations: NetworkObservation[] = [
    makeObservation("1", {
      band: "2.4 GHz",
      location: { latitude: 31.5, longitude: 74.3, accuracyMeters: 15 },
    }),
    makeObservation("2", {
      band: "2.4 GHz",
      location: undefined,
    }),
    makeObservation("3", {
      band: "5 GHz",
      location: { latitude: Number.NaN, longitude: 74.3 },
    }),
    makeObservation("4", {
      band: "5 GHz",
      location: { latitude: 31.6, longitude: 74.4 },
    }),
    // Test SSIDs must be excluded
    makeObservation("5", {
      ssid: "WiFiVision_Test",
      band: "2.4 GHz",
      location: { latitude: 31.5, longitude: 74.3 },
    }),
  ];

  assert.deepEqual(
    computeObservationLocationCounts(mixedObservations, "all"),
    {
      geolocatedObservations: 2,
      nonGeolocatedObservations: 2,
      totalObservations: 4,
    }
  );

  assert.deepEqual(
    computeObservationLocationCounts(mixedObservations, "2.4 GHz"),
    {
      geolocatedObservations: 1,
      nonGeolocatedObservations: 1,
      totalObservations: 2,
    }
  );

  assert.deepEqual(
    computeObservationLocationCounts(mixedObservations, "6 GHz"),
    {
      geolocatedObservations: 0,
      nonGeolocatedObservations: 0,
      totalObservations: 0,
    }
  );

  // API locationSummary takes precedence when provided (reflects all DB observations)
  assert.deepEqual(
    computeObservationLocationCounts(mixedObservations, "all", {
      geolocatedObservations: 120,
      nonGeolocatedObservations: 35,
      totalObservations: 155,
    }),
    {
      geolocatedObservations: 120,
      nonGeolocatedObservations: 35,
      totalObservations: 155,
    }
  );
});

test("Hidden and empty SSIDs are labeled clearly as Hidden Network without being dropped or leaking BSSIDs", () => {
  assert.deepEqual(formatObservationSsid(""), {
    displaySsid: "Hidden Network",
    isHidden: true,
    groupKey: "__hidden_network__",
  });
  assert.deepEqual(formatObservationSsid("   "), {
    displaySsid: "Hidden Network",
    isHidden: true,
    groupKey: "__hidden_network__",
  });
  assert.deepEqual(formatObservationSsid(null), {
    displaySsid: "Hidden Network",
    isHidden: true,
    groupKey: "__hidden_network__",
  });
  assert.deepEqual(formatObservationSsid("Office_WiFi"), {
    displaySsid: "Office_WiFi",
    isHidden: false,
    groupKey: "office_wifi",
  });

  const summaries = buildNetworkSummaries([
    makeObservation("h1", { ssid: "", signalDbm: -64, band: "2.4 GHz" }),
    makeObservation("h2", { ssid: "   ", signalDbm: -72, band: "5 GHz" }),
    makeObservation("h3", { ssid: "Hidden Network", signalDbm: -58, band: "5 GHz" }),
    makeObservation("n1", { ssid: "Office_WiFi", signalDbm: -52, band: "5 GHz" }),
    makeObservation("t1", { ssid: "WiFiVision_Geo_Test", signalDbm: -40 }),
  ]);

  assert.equal(summaries.length, 2);
  const hidden = summaries.find((s) => s.groupKey === "__hidden_network__");
  assert.ok(hidden, "Hidden network group must be preserved");
  assert.equal(hidden.ssid, "Hidden Network");
  assert.equal(hidden.isHidden, true);
  assert.equal(hidden.observations, 3);
  assert.equal(hidden.strongestSignal, -58);
  assert.equal(hidden.weakestSignal, -72);
  assert.deepEqual(hidden.bands, ["2.4 GHz", "5 GHz"]);
  assert.equal("bssid" in hidden, false);
  assert.equal("bssidHash" in hidden, false);
});

test("Band-filtered summary and popup metrics remain consistent with legacy cached entries", () => {
  const points: HeatmapPoint[] = [
    {
      latitude: 29.375,
      longitude: 71.752,
      totalObservations: 3,
      averageSignal: -62,
      strongestSignal: -52,
      weakestSignal: -74,
      frequencyDistribution: [
        { band: "2.4 GHz", count: 2, averageSignal: -60 }, // legacy cached (no extremes)
        { band: "5 GHz", count: 1, averageSignal: -66, strongestSignal: -66, weakestSignal: -66 },
      ],
    },
  ];

  const summary24 = computeHeatmapBandSummary(points, [], "2.4 GHz");
  assert.equal(summary24.locationsCount, 1);
  assert.equal(summary24.totalObservations, 2);
  assert.equal(summary24.weightedAverageSignal, -60);

  const popup24 = getPopupBandMetrics(points[0], "2.4 GHz");
  assert.deepEqual(popup24, {
    bandLabel: "2.4 GHz",
    averageSignal: -60,
    strongestSignal: -60,
    weakestSignal: -60,
    sampleCount: 2,
    totalObservations: 3,
  });
});
