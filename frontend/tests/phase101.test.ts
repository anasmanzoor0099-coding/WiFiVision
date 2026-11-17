import test from "node:test";
import assert from "node:assert/strict";

import { getPointBandExtremes } from "../src/lib/heatmap";
import type { HeatmapPoint } from "../src/lib/dashboard";

test("getPointBandExtremes returns band-specific strongest and weakest RSSI values", () => {
  const point: HeatmapPoint = {
    latitude: 29.375,
    longitude: 71.752,
    totalObservations: 5,
    averageSignal: -63,
    strongestSignal: -45,
    weakestSignal: -85,
    avgAccuracyMeters: 15,
    accuracySampleCount: 5,
    missingAccuracyCount: 0,
    coarseAccuracyCount: 0,
    frequencyDistribution: [
      {
        band: "2.4 GHz",
        count: 3,
        averageSignal: -65,
        strongestSignal: -45,
        weakestSignal: -85,
      },
      {
        band: "5 GHz",
        count: 2,
        averageSignal: -60,
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

  assert.deepEqual(getPointBandExtremes(point, "all"), {
    strongestSignal: -45,
    weakestSignal: -85,
  });
  assert.deepEqual(getPointBandExtremes(point, "2.4 GHz"), {
    strongestSignal: -45,
    weakestSignal: -85,
  });
  assert.deepEqual(getPointBandExtremes(point, "5 GHz"), {
    strongestSignal: -50,
    weakestSignal: -70,
  });
  assert.deepEqual(getPointBandExtremes(point, "6 GHz"), {
    strongestSignal: null,
    weakestSignal: null,
  });
});

test("getPointBandExtremes falls back cleanly when legacy cached band entries lack extremes", () => {
  const legacyPoint: HeatmapPoint = {
    latitude: 29.375,
    longitude: 71.752,
    totalObservations: 2,
    averageSignal: -60,
    strongestSignal: -52,
    weakestSignal: -74,
    frequencyDistribution: [
      { band: "5 GHz", count: 2, averageSignal: -60 },
    ],
  };

  assert.deepEqual(getPointBandExtremes(legacyPoint, "5 GHz"), {
    strongestSignal: -60,
    weakestSignal: -60,
  });
});

test("getObservations and getObservationAnalytics serialize pagination and band parameters cleanly", async () => {
  const savedPublicApi = process.env.NEXT_PUBLIC_API_URL;
  process.env.NEXT_PUBLIC_API_URL = "/api";

  const { getObservationAnalytics, getObservations } = await import(
    "../src/lib/dashboard"
  );

  const requestedUrls: string[] = [];
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (input: unknown) => {
    requestedUrls.push(String(input));
    return new Response(
      JSON.stringify({
        success: true,
        count: 0,
        totalCount: 0,
        page: 1,
        limit: 100,
        hasMore: false,
        observations: [],
        analytics: {
          totalNetworks: 0,
          averageSignal: null,
          strongestSignal: null,
          weakestSignal: null,
          filteredBand: null,
        },
      }),
      { status: 200 }
    );
  };

  try {
    await getObservations();
    await getObservations({ page: 2, limit: 50 });
    await getObservationAnalytics();
    await getObservationAnalytics("all");
    await getObservationAnalytics("5 GHz");

    assert.equal(requestedUrls[0], "/api/observations");
    assert.equal(requestedUrls[1], "/api/observations?page=2&limit=50");
    assert.equal(requestedUrls[2], "/api/observations/stats");
    assert.equal(requestedUrls[3], "/api/observations/stats");
    assert.equal(requestedUrls[4], "/api/observations/stats?band=5+GHz");
  } finally {
    globalThis.fetch = originalFetch;
    if (savedPublicApi === undefined) {
      delete process.env.NEXT_PUBLIC_API_URL;
    } else {
      process.env.NEXT_PUBLIC_API_URL = savedPublicApi;
    }
  }
});
