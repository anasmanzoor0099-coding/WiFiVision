import "dotenv/config";

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import mongoose from "mongoose";
import request from "supertest";

const originalNodeEnv = process.env.NODE_ENV;
process.env.NODE_ENV = "test";
if (!process.env.JWT_SECRET) {
  process.env.JWT_SECRET = "wifivision_phase2_test_secret_key_change_in_prod";
}

const { default: app } = await import("../app.js");
const { default: connectDB } = await import("../src/config/db.js");
const { default: NetworkObservation } = await import(
  "../src/models/NetworkObservation.js"
);
const { default: User } = await import("../src/models/User.js");
const { generateAuthToken } = await import("../src/utils/auth.utils.js");
const { hashBssid } = await import("../src/utils/cryptoSecrets.js");

const runId = Date.now();
const testSsid = `WiFiVision_Phase2_Test_${runId}`;
const testLat = -79.412;
const testLng = 141.734;
let testUser;
let authCookie;

before(async () => {
  await connectDB();

  testUser = await User.create({
    name: "Phase 2 Heatmap Test User",
    email: `wifivision_phase2_${runId}@example.com`,
    password: "hashed_unused_for_jwt",
    isEmailVerified: true,
  });
  authCookie = `wifivision_token=${generateAuthToken(testUser)}`;

  await NetworkObservation.create([
    {
      userId: testUser._id,
      ssid: testSsid,
      bssidHash: hashBssid("AA:BB:CC:DD:EE:01"),
      signalDbm: -50,
      frequencyMhz: 2437,
      band: "2.4 GHz",
      security: "WPA2",
      signalQuality: 4,
      location: {
        latitude: testLat,
        longitude: testLng,
        accuracyMeters: 8,
      },
      source: "web",
    },
    {
      userId: testUser._id,
      ssid: testSsid,
      bssidHash: hashBssid("AA:BB:CC:DD:EE:02"),
      signalDbm: -60,
      frequencyMhz: 2462,
      band: "2.4 GHz",
      security: "WPA2",
      signalQuality: 4,
      location: {
        latitude: testLat,
        longitude: testLng,
        accuracyMeters: 8,
      },
      source: "web",
    },
    {
      userId: testUser._id,
      ssid: testSsid,
      bssidHash: hashBssid("AA:BB:CC:DD:EE:03"),
      signalDbm: -70,
      frequencyMhz: 5180,
      band: "5 GHz",
      security: "WPA3",
      signalQuality: 3,
      location: {
        latitude: testLat,
        longitude: testLng,
        accuracyMeters: 8,
      },
      source: "web",
    },
  ]);
});

after(async () => {
  try {
    if (mongoose.connection.readyState === 1) {
      await NetworkObservation.deleteMany({ ssid: testSsid });
      if (testUser?._id) {
        await User.deleteMany({ _id: testUser._id });
      }
    }
  } finally {
    process.env.NODE_ENV = originalNodeEnv;
    await mongoose.disconnect();
  }
});

test("CORS allows configured origins and blocks disallowed origins without HTTP 500", async () => {
  const allowedOrigin = "http://localhost:3000";
  const allowedRes = await request(app)
    .get("/")
    .set("Origin", allowedOrigin);

  assert.equal(allowedRes.status, 200);
  assert.equal(
    allowedRes.headers["access-control-allow-origin"],
    allowedOrigin
  );

  const blockedRes = await request(app)
    .get("/")
    .set("Origin", "https://disallowed-origin.example.com");

  assert.notEqual(blockedRes.status, 500);
  assert.equal(blockedRes.headers["access-control-allow-origin"], undefined);

  const blockedPreflight = await request(app)
    .options("/api/auth/me")
    .set("Origin", "https://disallowed-origin.example.com")
    .set("Access-Control-Request-Method", "GET");

  assert.notEqual(blockedPreflight.status, 500);
  assert.equal(
    blockedPreflight.headers["access-control-allow-origin"],
    undefined
  );
});

test("Heatmap API calculates accurate band-specific signal averages", async () => {
  const allBandsRes = await request(app)
    .get("/api/observations/heatmap")
    .set("Cookie", authCookie);
  assert.equal(allBandsRes.status, 200);
  assert.equal(allBandsRes.body.success, true);

  const cell = allBandsRes.body.heatmap.find(
    (entry) => entry.latitude === testLat && entry.longitude === testLng
  );
  assert.ok(cell, "Expected test cell in heatmap response");
  assert.equal(cell.totalObservations, 3);
  assert.equal(cell.averageSignal, -60);

  const dist24 = cell.frequencyDistribution.find((d) => d.band === "2.4 GHz");
  const dist5 = cell.frequencyDistribution.find((d) => d.band === "5 GHz");
  const dist6 = cell.frequencyDistribution.find((d) => d.band === "6 GHz");

  assert.deepEqual(dist24, {
    band: "2.4 GHz",
    count: 2,
    averageSignal: -55,
    strongestSignal: -50,
    weakestSignal: -60,
  });
  assert.deepEqual(dist5, {
    band: "5 GHz",
    count: 1,
    averageSignal: -70,
    strongestSignal: -70,
    weakestSignal: -70,
  });
  assert.deepEqual(dist6, {
    band: "6 GHz",
    count: 0,
    averageSignal: null,
    strongestSignal: null,
    weakestSignal: null,
  });

  const band5Res = await request(app)
    .get("/api/observations/heatmap")
    .set("Cookie", authCookie)
    .query({ band: "5 GHz" });
  assert.equal(band5Res.status, 200);

  const band5Cell = band5Res.body.heatmap.find(
    (entry) => entry.latitude === testLat && entry.longitude === testLng
  );
  assert.ok(band5Cell, "Expected 5 GHz filtered cell in heatmap response");
  assert.equal(band5Cell.totalObservations, 1);
  assert.equal(band5Cell.averageSignal, -70);
});
