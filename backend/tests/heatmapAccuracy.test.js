import { test } from "node:test";
import assert from "node:assert/strict";

import NetworkObservation from "../src/models/NetworkObservation.js";
import { getHeatmapData } from "../src/controllers/observation.controller.js";
import {
    COARSE_GPS_ACCURACY_METERS,
    buildHeatmapPipeline,
    summarizeAndFormatHeatmapCells
} from "../src/services/heatmap.service.js";

function createMockRes() {
    return {
        statusCode: 200,
        body: null,
        status(code) {
            this.statusCode = code;
            return this;
        },
        json(payload) {
            this.body = payload;
            return this;
        }
    };
}

test("buildHeatmapPipeline includes accuracy metrics and respects optional band filter", () => {
    const pipeline = buildHeatmapPipeline({ band: "5 GHz" });

    assert.equal(pipeline[0].$match.band, "5 GHz");
    const groupStage = pipeline[1].$group;
    assert.ok(groupStage.avgAccuracyMeters, "Expected avgAccuracyMeters accumulator");
    assert.ok(groupStage.accuracySampleCount, "Expected accuracySampleCount accumulator");
    assert.ok(groupStage.missingAccuracyCount, "Expected missingAccuracyCount accumulator");
    assert.ok(groupStage.coarseAccuracyCount, "Expected coarseAccuracyCount accumulator");

    const projectStage = pipeline[2].$project;
    assert.ok(projectStage.avgAccuracyMeters, "Expected avgAccuracyMeters projection");
    assert.equal(projectStage.accuracySampleCount, 1);
    assert.equal(projectStage.missingAccuracyCount, 1);
    assert.equal(projectStage.coarseAccuracyCount, 1);
    assert.equal(COARSE_GPS_ACCURACY_METERS, 50);
});

test("summarizeAndFormatHeatmapCells computes weighted average accuracy without intermediate drift", () => {
    const { heatmap, accuracySummary } = summarizeAndFormatHeatmapCells([
        {
            latitude: 29.375,
            longitude: 71.752,
            totalObservations: 3,
            averageSignal: -62,
            strongestSignal: -52,
            weakestSignal: -70,
            avgAccuracyMeters: 10.15, // (10.1 + 10.2) / 2, 1 missing
            accuracySampleCount: 2,
            missingAccuracyCount: 1,
            coarseAccuracyCount: 0,
            frequencyDistribution: []
        },
        {
            latitude: 29.396,
            longitude: 71.684,
            totalObservations: 2,
            averageSignal: -74,
            strongestSignal: -68,
            weakestSignal: -80,
            avgAccuracyMeters: 62.5, // e.g. 40 + 85
            accuracySampleCount: 2,
            missingAccuracyCount: 0,
            coarseAccuracyCount: 1,
            frequencyDistribution: []
        }
    ]);

    assert.equal(heatmap.length, 2);
    assert.equal(heatmap[0].avgAccuracyMeters, 10.2);
    assert.equal(heatmap[0].accuracySampleCount, 2);
    assert.equal(heatmap[0].missingAccuracyCount, 1);
    assert.equal(heatmap[0].coarseAccuracyCount, 0);

    assert.equal(heatmap[1].avgAccuracyMeters, 62.5);
    assert.equal(heatmap[1].coarseAccuracyCount, 1);

    // Weighted average = (10.15 * 2 + 62.5 * 2) / 4 = 145.3 / 4 = 36.325 -> 36.3
    assert.deepEqual(accuracySummary, {
        averageAccuracyMeters: 36.3,
        withAccuracyCount: 4,
        withoutAccuracyCount: 1,
        coarseAccuracyCount: 1
    });
});

test("summarizeAndFormatHeatmapCells preserves null when accuracy data is missing", () => {
    const { heatmap, accuracySummary } = summarizeAndFormatHeatmapCells([
        {
            latitude: 29.38,
            longitude: 71.75,
            totalObservations: 4,
            averageSignal: -75,
            strongestSignal: -70,
            weakestSignal: -80,
            avgAccuracyMeters: null,
            accuracySampleCount: 0,
            missingAccuracyCount: 4,
            coarseAccuracyCount: 0,
            frequencyDistribution: []
        }
    ]);

    assert.equal(heatmap[0].avgAccuracyMeters, null);
    assert.equal(heatmap[0].accuracySampleCount, 0);
    assert.equal(heatmap[0].missingAccuracyCount, 4);
    assert.equal(heatmap[0].coarseAccuracyCount, 0);

    assert.deepEqual(accuracySummary, {
        averageAccuracyMeters: null,
        withAccuracyCount: 0,
        withoutAccuracyCount: 4,
        coarseAccuracyCount: 0
    });
});

test("summarizeAndFormatHeatmapCells handles empty cells / zero observations cleanly", () => {
    const result = summarizeAndFormatHeatmapCells([]);

    assert.deepEqual(result, {
        heatmap: [],
        accuracySummary: {
            averageAccuracyMeters: null,
            withAccuracyCount: 0,
            withoutAccuracyCount: 0,
            coarseAccuracyCount: 0
        }
    });
});

test("getHeatmapData returns additive accuracySummary with mocked aggregate and band filter", async () => {
    const originalAggregate = NetworkObservation.aggregate;
    let capturedPipeline = null;

    NetworkObservation.aggregate = async (pipeline) => {
        capturedPipeline = pipeline;
        return [
            {
                latitude: 29.375,
                longitude: 71.752,
                totalObservations: 5,
                averageSignal: -82,
                strongestSignal: -80,
                weakestSignal: -84,
                avgAccuracyMeters: 15.583,
                accuracySampleCount: 5,
                missingAccuracyCount: 0,
                coarseAccuracyCount: 0,
                frequencyDistribution: [
                    { band: "2.4 GHz", count: 0, averageSignal: null },
                    { band: "5 GHz", count: 5, averageSignal: -82 },
                    { band: "6 GHz", count: 0, averageSignal: null }
                ]
            }
        ];
    };

    try {
        const mockUserId = "65f000000000000000000001";
        const req = { query: { band: "5 GHz" }, user: { _id: mockUserId } };
        const res = createMockRes();
        let nextError = null;

        await getHeatmapData(req, res, (err) => {
            nextError = err;
        });

        assert.equal(nextError, null);
        assert.equal(res.statusCode, 200);
        assert.equal(capturedPipeline[0].$match.userId, mockUserId);
        assert.equal(capturedPipeline[0].$match.band, "5 GHz");
        assert.equal(res.body.success, true);
        assert.equal(res.body.count, 1);
        assert.equal(res.body.heatmap[0].avgAccuracyMeters, 15.6);
        assert.deepEqual(res.body.accuracySummary, {
            averageAccuracyMeters: 15.6,
            withAccuracyCount: 5,
            withoutAccuracyCount: 0,
            coarseAccuracyCount: 0
        });
    } finally {
        NetworkObservation.aggregate = originalAggregate;
    }
});

test("getHeatmapData handles zero matching observations for a band filter", async () => {
    const originalAggregate = NetworkObservation.aggregate;

    NetworkObservation.aggregate = async () => [];

    try {
        const req = { query: { band: "6 GHz" }, user: { _id: "65f000000000000000000001" } };
        const res = createMockRes();
        await getHeatmapData(req, res, (err) => {
            throw err;
        });

        assert.equal(res.statusCode, 200);
        assert.deepEqual(res.body, {
            success: true,
            count: 0,
            heatmap: [],
            accuracySummary: {
                averageAccuracyMeters: null,
                withAccuracyCount: 0,
                withoutAccuracyCount: 0,
                coarseAccuracyCount: 0
            },
            locationSummary: {
                geolocatedObservations: 0,
                nonGeolocatedObservations: 0,
                totalObservations: 0
            }
        });
    } finally {
        NetworkObservation.aggregate = originalAggregate;
    }
});
