import "dotenv/config";

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import request from "supertest";
import mongoose from "mongoose";

import app from "../app.js";
import connectDB from "../src/config/db.js";
import NetworkObservation from "../src/models/NetworkObservation.js";
import User from "../src/models/User.js";
import DeviceCredential from "../src/models/DeviceCredential.js";
import { generateAuthToken } from "../src/utils/auth.utils.js";
import { hashDeviceSecret } from "../src/utils/cryptoSecrets.js";

if (!process.env.JWT_SECRET) {
    process.env.JWT_SECRET = "wifivision_obs_test_secret_key_32chars_minimum";
}

const runId = Date.now();
const testId = `WiFiVision_API_Test_${runId}`;
let testUser;
let authCookie;
let deviceToken;
let testDeviceId;

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
    source: "android"
};

function makeDeviceHeaders() {
    return {
        Authorization: `Bearer ${deviceToken}`,
        "X-WiFiVision-Timestamp": String(Date.now()),
        "X-WiFiVision-Nonce": crypto.randomBytes(12).toString("hex")
    };
}

before(async () => {
    await connectDB();

    testUser = await User.create({
        name: "Obs Test User",
        email: `wifivision_obs_api_${runId}@example.com`,
        password: "hashed_unused_for_jwt",
        isEmailVerified: true
    });
    authCookie = `wifivision_token=${generateAuthToken(testUser)}`;

    const rawSecret = crypto.randomBytes(32).toString("hex");
    const device = await DeviceCredential.create({
        userId: testUser._id,
        deviceName: "API Test Phone",
        secretHash: hashDeviceSecret(rawSecret),
        scopes: ["observations:write"],
        status: "active",
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
    });
    testDeviceId = device._id;
    deviceToken = `wvd_${device._id.toString()}_${rawSecret}`;
});

after(async () => {
    try {
        if (mongoose.connection.readyState === 1) {
            await NetworkObservation.deleteMany({ ssid: testId });
            if (testDeviceId) {
                await DeviceCredential.deleteMany({ _id: testDeviceId });
            }
            if (testUser?._id) {
                await User.deleteMany({ _id: testUser._id });
            }
        }
    } finally {
        await mongoose.disconnect();
    }
});

// TEST 1: Create a valid observation via paired device credential
test("Should create and save a valid network observation", async () => {
    const response = await request(app)
        .post("/api/observations")
        .set(makeDeviceHeaders())
        .send(validObservation);

    assert.equal(response.status, 201);
    assert.equal(response.body.success, true);
    assert.equal(response.body.observation.ssid, testId);
    assert.equal(response.body.observation.signalDbm, -55);
    assert.equal(response.body.observation.bssid, undefined);
});

// TEST 2: Reject invalid signal strength
test("Should reject an observation with invalid signal strength", async () => {
    const response = await request(app)
        .post("/api/observations")
        .set(makeDeviceHeaders())
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
        .set(makeDeviceHeaders())
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
        .set(makeDeviceHeaders())
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
        .get("/api/observations")
        .set("Cookie", authCookie);

    assert.equal(response.status, 200);
    assert.equal(response.body.success, true);
    assert.ok(Array.isArray(response.body.observations));
    assert.ok(response.body.observations.some((obs) => obs.ssid === testId));
});

// TEST 6: Retrieve observation statistics
test("Should return network observation statistics", async () => {
    const response = await request(app)
        .get("/api/observations/stats")
        .set("Cookie", authCookie);

    assert.equal(response.status, 200);
    assert.equal(response.body.success, true);
    assert.equal(response.body.analytics.totalNetworks, 1);
});

// TEST 7: Retrieve geographic heatmap
test("Should return geographic heatmap data", async () => {
    const response = await request(app)
        .get("/api/observations/heatmap")
        .set("Cookie", authCookie);

    assert.equal(response.status, 200);
    assert.equal(response.body.success, true);
    assert.ok(Array.isArray(response.body.heatmap));
});

// TEST 8: Reject an invalid heatmap band
test("Should reject an invalid heatmap frequency filter", async () => {
    const response = await request(app)
        .get("/api/observations/heatmap")
        .set("Cookie", authCookie)
        .query({ band: "10 GHz" });

    assert.equal(response.status, 400);
    assert.equal(response.body.success, false);
});

// TEST 9: Retrieve network density
test("Should return geographic network density data", async () => {
    const response = await request(app)
        .get("/api/observations/density")
        .set("Cookie", authCookie);

    assert.equal(response.status, 200);
    assert.equal(response.body.success, true);
    assert.ok(Array.isArray(response.body.density));
});
