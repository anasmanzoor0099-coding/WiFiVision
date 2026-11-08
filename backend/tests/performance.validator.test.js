import test from "node:test";
import assert from "node:assert/strict";

import performanceSchema from "../src/validators/performance.validator.js";

const validPerformanceData = {
    latencyMs: 120,
    medianLatencyMs: 115,
    jitterMs: 8.5,
    downloadMbps: 50,
    uploadMbps: 20,

    packetLoss: {
        totalPackets: 20,
        successfulPackets: 19,
        lostPackets: 1,
        packetLossPercentage: 5,
        averageResponseMs: 125,
        measurementType: "HTTP application-level request success"
    },

    source: "backend",
    testServer: "Cloudflare",
    measuredAt: "2026-11-07T12:00:00.000Z"
};

// TEST 1: Valid performance data
test("Should accept valid performance data", () => {
    const result = performanceSchema.safeParse(
        validPerformanceData
    );

    assert.equal(result.success, true);
});

// TEST 2: Reject negative latency
test("Should reject negative latency", () => {
    const result = performanceSchema.safeParse({
        ...validPerformanceData,
        latencyMs: -10
    });

    assert.equal(result.success, false);
});

// TEST 3: Reject negative download speed
test("Should reject negative download speed", () => {
    const result = performanceSchema.safeParse({
        ...validPerformanceData,
        downloadMbps: -5
    });

    assert.equal(result.success, false);
});

// TEST 4: Reject invalid packet totals
test("Should reject incorrect packet totals", () => {
    const result = performanceSchema.safeParse({
        ...validPerformanceData,
        packetLoss: {
            ...validPerformanceData.packetLoss,
            successfulPackets: 18
        }
    });

    assert.equal(result.success, false);
});

// TEST 5: Reject incorrect packet loss percentage
test("Should reject incorrect packet loss percentage", () => {
    const result = performanceSchema.safeParse({
        ...validPerformanceData,
        packetLoss: {
            ...validPerformanceData.packetLoss,
            packetLossPercentage: 25
        }
    });

    assert.equal(result.success, false);
});

// TEST 6: Reject invalid measurement source
test("Should reject an unknown measurement source", () => {
    const result = performanceSchema.safeParse({
        ...validPerformanceData,
        source: "unknown"
    });

    assert.equal(result.success, false);
});

// TEST 7: Reject non-finite latency
test("Should reject non-finite latency values", () => {
    const result = performanceSchema.safeParse({
        ...validPerformanceData,
        latencyMs: NaN
    });

    assert.equal(result.success, false);
});

// TEST 8: Accept zero packet loss
test("Should accept zero packet loss", () => {
    const result = performanceSchema.safeParse({
        ...validPerformanceData,
        packetLoss: {
            ...validPerformanceData.packetLoss,
            successfulPackets: 20,
            lostPackets: 0,
            packetLossPercentage: 0
        }
    });

    assert.equal(result.success, true);
});