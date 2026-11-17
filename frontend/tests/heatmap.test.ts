import test from "node:test";
import assert from "node:assert/strict";

import {
  DENSITY_COLORS,
  computeHeatmapAccuracySummary,
  computeHeatmapBandSummary,
  getDensityLevel,
  getDensityVisuals,
  getLocationPrecisionInfo,
  getPointBandCount,
  getPointBandSignal,
} from "../src/lib/heatmap";
import type { HeatmapPoint, NetworkDensityPoint } from "../src/lib/dashboard";

test("density tier classification distinguishes Low, Medium, and High counts", () => {
  assert.equal(getDensityLevel(1), "Low");
  assert.equal(getDensityLevel(5), "Low");
  assert.equal(getDensityLevel(6), "Medium");
  assert.equal(getDensityLevel(20), "Medium");
  assert.equal(getDensityLevel(21), "High");
  assert.equal(getDensityLevel(50), "High");
});

test("density color, marker radius, and glow intensity scale strictly with density across zooms", () => {
  const low = getDensityVisuals(2, 50, 13);
  const medium = getDensityVisuals(12, 50, 13);
  const high = getDensityVisuals(50, 50, 13);

  assert.equal(low.color, DENSITY_COLORS.Low);
  assert.equal(medium.color, DENSITY_COLORS.Medium);
  assert.equal(high.color, DENSITY_COLORS.High);
  assert.notEqual(low.color, medium.color);
  assert.notEqual(medium.color, high.color);

  assert.ok(low.markerRadius < medium.markerRadius);
  assert.ok(medium.markerRadius < high.markerRadius);
  assert.ok(low.glowDiameterPx < medium.glowDiameterPx);
  assert.ok(medium.glowDiameterPx < high.glowDiameterPx);
  assert.ok(low.glowOpacity < medium.glowOpacity);
  assert.ok(medium.glowOpacity < high.glowOpacity);

  // Zoom-out and zoom-in remain readable and preserve ordering
  const zoomedOut = getDensityVisuals(50, 50, 9);
  const zoomedIn = getDensityVisuals(50, 50, 17);
  assert.ok(zoomedOut.markerRadius >= 5 && zoomedOut.markerRadius < high.markerRadius);
  assert.ok(zoomedIn.markerRadius > high.markerRadius && zoomedIn.markerRadius <= 18);
});

test("GPS accuracy precision classification handles High, Moderate, Low, and Unknown cleanly", () => {
  const high = getLocationPrecisionInfo(15.6);
  assert.equal(high.level, "High");
  assert.equal(high.label, "High location precision");
  assert.equal(high.formattedMeters, "±15.6 m");

  const boundary20 = getLocationPrecisionInfo(20);
  assert.equal(boundary20.level, "High");

  const moderate = getLocationPrecisionInfo(34.2);
  assert.equal(moderate.level, "Moderate");
  assert.equal(moderate.label, "Moderate location precision");
  assert.equal(moderate.formattedMeters, "±34.2 m");

  const boundary50 = getLocationPrecisionInfo(50);
  assert.equal(boundary50.level, "Moderate");

  const coarse = getLocationPrecisionInfo(78.4);
  assert.equal(coarse.level, "Low");
  assert.equal(coarse.label, "Low location precision");
  assert.equal(coarse.formattedMeters, "±78.4 m");

  assert.equal(getLocationPrecisionInfo(null).label, "Unknown");
  assert.equal(getLocationPrecisionInfo(null).formattedMeters, "Unknown");
  assert.equal(getLocationPrecisionInfo(undefined).label, "Unknown");
  assert.equal(getLocationPrecisionInfo(Number.NaN).label, "Unknown");
  assert.equal(getLocationPrecisionInfo(-5).label, "Unknown");
});

test("band-filtered KPIs and GPS accuracy summary remain accurate under band selection", () => {
  const points: HeatmapPoint[] = [
    {
      latitude: 29.375,
      longitude: 71.752,
      totalObservations: 10,
      averageSignal: -68,
      strongestSignal: -52,
      weakestSignal: -84,
      avgAccuracyMeters: 15.6,
      accuracySampleCount: 10,
      missingAccuracyCount: 0,
      coarseAccuracyCount: 0,
      frequencyDistribution: [
        { band: "2.4 GHz", count: 8, averageSignal: -64.5 },
        { band: "5 GHz", count: 2, averageSignal: -82 },
        { band: "6 GHz", count: 0, averageSignal: null },
      ],
    },
    {
      latitude: 29.396,
      longitude: 71.684,
      totalObservations: 4,
      averageSignal: -74,
      strongestSignal: -70,
      weakestSignal: -78,
      avgAccuracyMeters: null,
      accuracySampleCount: 0,
      missingAccuracyCount: 4,
      coarseAccuracyCount: 0,
      frequencyDistribution: [
        { band: "2.4 GHz", count: 4, averageSignal: -74 },
        { band: "5 GHz", count: 0, averageSignal: null },
        { band: "6 GHz", count: 0, averageSignal: null },
      ],
    },
  ];

  const density: NetworkDensityPoint[] = [
    {
      latitude: 29.375,
      longitude: 71.752,
      totalObservations: 10,
      uniqueNetworkCount: 7,
      averageSignal: -68,
      frequencyDistribution: [
        { band: "2.4 GHz", count: 8 },
        { band: "5 GHz", count: 2 },
        { band: "6 GHz", count: 0 },
      ],
    },
    {
      latitude: 29.396,
      longitude: 71.684,
      totalObservations: 4,
      uniqueNetworkCount: 12,
      averageSignal: -74,
      frequencyDistribution: [
        { band: "2.4 GHz", count: 4 },
        { band: "5 GHz", count: 0 },
        { band: "6 GHz", count: 0 },
      ],
    },
  ];

  assert.equal(getPointBandCount(points[0], "5 GHz"), 2);
  assert.equal(getPointBandSignal(points[0], "5 GHz"), -82);

  // Selecting 5 GHz excludes cell (29.396, 71.684) from both KPI and Peak Cell Density
  const summary5g = computeHeatmapBandSummary(points, density, "5 GHz");
  assert.deepEqual(summary5g, {
    locationsCount: 1,
    totalObservations: 2,
    weightedAverageSignal: -82,
    uniqueNetworksPeak: 7,
  });

  // Fallback accuracy summary when apiSummary is omitted
  const accFallback = computeHeatmapAccuracySummary(points, "all");
  assert.deepEqual(accFallback, {
    averageAccuracyMeters: 15.6,
    withAccuracyCount: 10,
    withoutAccuracyCount: 4,
    coarseAccuracyCount: 0,
  });

  // API-provided summary is respected directly when present
  const accApi = computeHeatmapAccuracySummary(points, "5 GHz", {
    averageAccuracyMeters: 15.6,
    withAccuracyCount: 2,
    withoutAccuracyCount: 0,
    coarseAccuracyCount: 0,
  });
  assert.deepEqual(accApi, {
    averageAccuracyMeters: 15.6,
    withAccuracyCount: 2,
    withoutAccuracyCount: 0,
    coarseAccuracyCount: 0,
  });
});
