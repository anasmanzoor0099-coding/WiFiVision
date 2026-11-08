import "dotenv/config";

import {
    test,
    before,
    after
} from "node:test";

import assert from "node:assert/strict";
import request from "supertest";
import mongoose from "mongoose";

import app from "../app.js";
import connectDB from "../src/config/db.js";
import NetworkObservation from "../src/models/NetworkObservation.js";

// Unique test identifier
const testId = `WiFiVision_API_Test_${Date.now()}`;

const validObservation = {
    ssid: testId,
    bssid: "AA:BB:CC:DD:EE:FF",
    signalDbm: -55,
    frequencyMhz: 2437,
    band: "2.4 GHz",
    security: "WPA2",
    signalQuality: 4,
    location: {
        latitude: 29.3956,
        longitude: 71.6836,
        accuracyMeters: 10
    },
    source: "web"
};

// CONNECT TO MONGODB BEFORE TESTS
before(async () => {
    await connectDB();
});

// CLEAN UP AFTER TESTS
after(async () => {
    try {
        if (mongoose.connection.readyState === 1) {
            await NetworkObservation.deleteMany({
                ssid: testId
            });
        }
    } finally {
        await mongoose.disconnect();
    }
});

// TEST 1: Create a valid observation
test("Should create and save a valid network observation", async () => {
    const response = await request(app)
        .post("/api/observations")
        .send(validObservation);

    assert.equal(response.status, 201);
    assert.equal(response.body.success, true);
    assert.equal(response.body.observation.ssid, testId);
    assert.equal(response.body.observation.signalDbm, -55);
});

// TEST 2: Reject invalid signal strength
test("Should reject an observation with invalid signal strength", async () => {
    const response = await request(app)
        .post("/api/observations")
        .send({
            ...validObservation,
            signalDbm: -200
        });

    assert.equal(response.status, 400);
    assert.equal(response.body.success, false);
});

// TEST 3: Reject an invalid frequency band
test("Should reject an observation with an invalid frequency band", async () => {
    const response = await request(app)
        .post("/api/observations")
        .send({
            ...validObservation,
            band: "10 GHz"
        });

    assert.equal(response.status, 400);
    assert.equal(response.body.success, false);
});

// TEST 4: Reject invalid geographic coordinates
test("Should reject invalid geographic coordinates", async () => {
    const response = await request(app)
        .post("/api/observations")
        .send({
            ...validObservation,
            location: {
                latitude: 120,
                longitude: 71.6836
            }
        });

    assert.equal(response.status, 400);
    assert.equal(response.body.success, false);
});

// TEST 5: Retrieve observations
test("Should retrieve saved network observations", async () => {
    const response = await request(app)
        .get("/api/observations");

    assert.equal(response.status, 200);
    assert.equal(response.body.success, true);
    assert.ok(Array.isArray(response.body.observations));
});

// TEST 6: Retrieve observation statistics
test("Should return network observation statistics", async () => {
    const response = await request(app)
        .get("/api/observations/stats");

    assert.equal(response.status, 200);
    assert.equal(response.body.success, true);

    assert.ok(
        typeof response.body.analytics.totalNetworks === "number"
    );
});

// TEST 7: Retrieve geographic heatmap
test("Should return geographic heatmap data", async () => {
    const response = await request(app)
        .get("/api/observations/heatmap");

    assert.equal(response.status, 200);
    assert.equal(response.body.success, true);
    assert.ok(Array.isArray(response.body.heatmap));
});

// TEST 8: Reject an invalid heatmap band
test("Should reject an invalid heatmap frequency filter", async () => {
    const response = await request(app)
        .get("/api/observations/heatmap")
        .query({ band: "10 GHz" });

    assert.equal(response.status, 400);
    assert.equal(response.body.success, false);
});

// TEST 9: Retrieve network density
test("Should return geographic network density data", async () => {
    const response = await request(app)
        .get("/api/observations/density");

    assert.equal(response.status, 200);
    assert.equal(response.body.success, true);
    assert.ok(Array.isArray(response.body.density));
});