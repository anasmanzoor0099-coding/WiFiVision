import test from "node:test";
import assert from "node:assert/strict";

import {
    calculateNetworkQuality
} from "../src/services/networkQuality.service.js";

// TEST 1: Excellent network quality
test("Should calculate excellent network quality correctly", () => {
    const result = calculateNetworkQuality({
        signalDbm: -45,
        signalQuality: 4,
        band: "5 GHz",
        security: "WPA3"
    });

    assert.equal(result.score, 100);
    assert.equal(result.classification, "Excellent");
});

// TEST 2: Poor network quality
test("Should classify a weak network as Poor", () => {
    const result = calculateNetworkQuality({
        signalDbm: -95,
        signalQuality: 0,
        band: "Unknown",
        security: "Open"
    });

    assert.equal(result.score, 8);
    assert.equal(result.classification, "Poor");
});

// TEST 3: Missing signal quality
test("Should handle missing signal quality safely", () => {
    const result = calculateNetworkQuality({
        signalDbm: -65,
        band: "Unknown",
        security: "Unknown"
    });

    assert.equal(result.components.signalLevelScore, 50);
    assert.equal(result.score, 64);
    assert.equal(result.classification, "Fair");
});

// TEST 4: WPA3 security score
test("Should assign the correct WPA3 security score", () => {
    const result = calculateNetworkQuality({
        signalDbm: -50,
        signalQuality: 4,
        band: "6 GHz",
        security: "WPA3"
    });

    assert.equal(result.components.securityScore, 100);
});

// TEST 5: WPA2 security score
test("Should assign the correct WPA2 security score", () => {
    const result = calculateNetworkQuality({
        signalDbm: -55,
        signalQuality: 4,
        band: "2.4 GHz",
        security: "WPA2"
    });

    assert.equal(result.components.securityScore, 90);
});

// TEST 6: Unknown band handling
test("Should handle an unknown frequency band", () => {
    const result = calculateNetworkQuality({
        signalDbm: -60,
        signalQuality: 3,
        band: "Unknown",
        security: "WPA2"
    });

    assert.equal(result.components.bandScore, 50);
});

// TEST 7: Verify all score components exist
test("Should return all network quality components", () => {
    const result = calculateNetworkQuality({
        signalDbm: -60,
        signalQuality: 3,
        band: "5 GHz",
        security: "WPA2"
    });

    assert.ok(result.components);
    assert.ok(result.details);

    assert.equal(typeof result.score, "number");
    assert.equal(typeof result.classification, "string");

    assert.ok("signalScore" in result.components);
    assert.ok("signalLevelScore" in result.components);
    assert.ok("bandScore" in result.components);
    assert.ok("securityScore" in result.components);
});