import test from "node:test";
import assert from "node:assert/strict";

import {
    calculateLatencyStatistics
} from "../src/utils/latencyStatistics.js";

// TEST 1: Average latency
test("Should calculate average latency correctly", () => {
    const result = calculateLatencyStatistics([
        100, 200, 300, 400
    ]);

    assert.equal(result.averageLatencyMs, 250);
});

// TEST 2: Median latency with an even number of samples
test("Should calculate median latency correctly for even samples", () => {
    const result = calculateLatencyStatistics([
        100, 200, 300, 400
    ]);

    assert.equal(result.medianLatencyMs, 250);
});

// TEST 3: Median latency with an odd number of samples
test("Should calculate median latency correctly for odd samples", () => {
    const result = calculateLatencyStatistics([
        100, 200, 500
    ]);

    assert.equal(result.medianLatencyMs, 200);
});

// TEST 4: Jitter calculation
test("Should calculate average consecutive latency difference", () => {
    const result = calculateLatencyStatistics([
        10, 20, 40
    ]);

    assert.equal(result.jitterMs, 15);
});

// TEST 5: Stable connection
test("Should return zero jitter for stable latency samples", () => {
    const result = calculateLatencyStatistics([
        100, 100, 100, 100
    ]);

    assert.equal(result.jitterMs, 0);
});

// TEST 6: Single latency sample
test("Should handle a single latency sample", () => {
    const result = calculateLatencyStatistics([
        150
    ]);

    assert.equal(result.averageLatencyMs, 150);
    assert.equal(result.medianLatencyMs, 150);
    assert.equal(result.jitterMs, 0);
});

// TEST 7: Empty samples validation
test("Should reject an empty latency sample array", () => {
    assert.throws(
        () => calculateLatencyStatistics([]),
        /at least one sample/
    );
});

// TEST 8: Invalid latency values
test("Should reject negative latency values", () => {
    assert.throws(
        () => calculateLatencyStatistics([100, -20, 200]),
        /non-negative/
    );
});