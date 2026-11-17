import "dotenv/config";

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import mongoose from "mongoose";
import request from "supertest";

const originalNodeEnv = process.env.NODE_ENV;
const originalJwtSecret = process.env.JWT_SECRET;

process.env.NODE_ENV = "test";
if (!process.env.JWT_SECRET) {
  process.env.JWT_SECRET = "wifivision_phase101_jwt_secret_minimum_32chars_ok";
}

const { default: app } = await import("../app.js");
const { default: connectDB } = await import("../src/config/db.js");
const { default: User } = await import("../src/models/User.js");
const { default: NetworkObservation } = await import(
  "../src/models/NetworkObservation.js"
);
const { generateAuthToken } = await import("../src/utils/auth.utils.js");
const { buildHeatmapPipeline } = await import(
  "../src/services/heatmap.service.js"
);

const runId = Date.now();
const prefix = `phase101_${runId}`;
let userA;
let userB;
let cookieA;
let cookieB;

before(async () => {
  await connectDB();

  userA = await User.create({
    name: "Phase101 Alpha",
    email: `${prefix}_alpha@example.com`,
    password: "hashed_unused_for_jwt",
    isEmailVerified: true,
    tokenVersion: 0,
  });

  userB = await User.create({
    name: "Phase101 Beta",
    email: `${prefix}_beta@example.com`,
    password: "hashed_unused_for_jwt",
    isEmailVerified: true,
    tokenVersion: 0,
  });

  cookieA = `wifivision_token=${generateAuthToken(userA)}`;
  cookieB = `wifivision_token=${generateAuthToken(userB)}`;

  const baseTime = new Date("2026-11-17T10:00:00.000Z").getTime();

  await NetworkObservation.insertMany([
    {
      userId: userA._id,
      ssid: `${prefix}_24_A`,
      bssid: "AA:11:22:33:44:01",
      signalDbm: -45,
      frequencyMhz: 2412,
      band: "2.4 GHz",
      security: "WPA2",
      location: { latitude: 29.375, longitude: 71.752, accuracyMeters: 12 },
      source: "windows",
      observedAt: new Date(baseTime + 5000),
    },
    {
      userId: userA._id,
      ssid: `${prefix}_24_B`,
      bssid: "AA:11:22:33:44:02",
      signalDbm: -65,
      frequencyMhz: 2437,
      band: "2.4 GHz",
      security: "WPA2",
      location: { latitude: 29.375, longitude: 71.752, accuracyMeters: 16 },
      source: "android",
      observedAt: new Date(baseTime + 4000),
    },
    {
      userId: userA._id,
      ssid: `${prefix}_24_C`,
      bssid: "AA:11:22:33:44:03",
      signalDbm: -85,
      frequencyMhz: 2462,
      band: "2.4 GHz",
      security: "Open",
      location: { latitude: 29.375, longitude: 71.752, accuracyMeters: 20 },
      source: "windows",
      observedAt: new Date(baseTime + 3000),
    },
    {
      userId: userA._id,
      ssid: `${prefix}_5_A`,
      bssid: "AA:11:22:33:44:04",
      signalDbm: -50,
      frequencyMhz: 5180,
      band: "5 GHz",
      security: "WPA3",
      location: { latitude: 29.375, longitude: 71.752, accuracyMeters: 14 },
      source: "windows",
      observedAt: new Date(baseTime + 2000),
    },
    {
      userId: userA._id,
      ssid: `${prefix}_5_B`,
      bssid: "AA:11:22:33:44:05",
      signalDbm: -70,
      frequencyMhz: 5240,
      band: "5 GHz",
      security: "WPA3",
      location: { latitude: 29.375, longitude: 71.752, accuracyMeters: 18 },
      source: "android",
      observedAt: new Date(baseTime + 1000),
    },
    {
      userId: userB._id,
      ssid: `${prefix}_BETA_ONLY`,
      bssid: "BB:11:22:33:44:01",
      signalDbm: -38,
      frequencyMhz: 5745,
      band: "5 GHz",
      security: "WPA2",
      location: { latitude: 29.375, longitude: 71.752, accuracyMeters: 10 },
      source: "android",
      observedAt: new Date(baseTime + 6000),
    },
  ]);
});

after(async () => {
  try {
    if (mongoose.connection.readyState === 1) {
      await NetworkObservation.deleteMany({
        userId: { $in: [userA?._id, userB?._id].filter(Boolean) },
      });
      await User.deleteMany({
        _id: { $in: [userA?._id, userB?._id].filter(Boolean) },
      });
      await mongoose.disconnect();
    }
  } finally {
    if (originalNodeEnv === undefined) delete process.env.NODE_ENV;
    else process.env.NODE_ENV = originalNodeEnv;
    if (originalJwtSecret === undefined) delete process.env.JWT_SECRET;
    else process.env.JWT_SECRET = originalJwtSecret;
  }
});

test("GET /api/observations returns explicit pagination metadata and strict per-user isolation", async () => {
  const defaultRes = await request(app)
    .get("/api/observations")
    .set("Cookie", cookieA);

  assert.equal(defaultRes.status, 200);
  assert.equal(defaultRes.body.success, true);
  assert.equal(defaultRes.body.page, 1);
  assert.equal(defaultRes.body.limit, 100);
  assert.equal(defaultRes.body.totalCount, 5);
  assert.equal(defaultRes.body.count, 5);
  assert.equal(defaultRes.body.hasMore, false);
  assert.equal(defaultRes.body.observations.length, 5);
  for (const obs of defaultRes.body.observations) {
    assert.equal(String(obs.userId), String(userA._id));
    assert.equal("bssid" in obs, false);
    assert.equal("bssidHash" in obs, false);
    assert.notEqual(obs.ssid, `${prefix}_BETA_ONLY`);
  }

  // Paginate with limit=2 across all 3 pages and an empty page 4
  const seenIds = new Set();

  const p1 = await request(app)
    .get("/api/observations?page=1&limit=2")
    .set("Cookie", cookieA);
  assert.equal(p1.status, 200);
  assert.equal(p1.body.page, 1);
  assert.equal(p1.body.limit, 2);
  assert.equal(p1.body.count, 2);
  assert.equal(p1.body.totalCount, 5);
  assert.equal(p1.body.hasMore, true);
  p1.body.observations.forEach((o) => seenIds.add(String(o._id)));

  const p2 = await request(app)
    .get("/api/observations?page=2&limit=2")
    .set("Cookie", cookieA);
  assert.equal(p2.status, 200);
  assert.equal(p2.body.page, 2);
  assert.equal(p2.body.limit, 2);
  assert.equal(p2.body.count, 2);
  assert.equal(p2.body.totalCount, 5);
  assert.equal(p2.body.hasMore, true);
  p2.body.observations.forEach((o) => seenIds.add(String(o._id)));

  const p3 = await request(app)
    .get("/api/observations?page=3&limit=2")
    .set("Cookie", cookieA);
  assert.equal(p3.status, 200);
  assert.equal(p3.body.page, 3);
  assert.equal(p3.body.limit, 2);
  assert.equal(p3.body.count, 1);
  assert.equal(p3.body.totalCount, 5);
  assert.equal(p3.body.hasMore, false);
  p3.body.observations.forEach((o) => seenIds.add(String(o._id)));

  assert.equal(seenIds.size, 5);

  const p4 = await request(app)
    .get("/api/observations?page=4&limit=2")
    .set("Cookie", cookieA);
  assert.equal(p4.status, 200);
  assert.equal(p4.body.count, 0);
  assert.equal(p4.body.totalCount, 5);
  assert.equal(p4.body.hasMore, false);

  // User B only sees User B's single record (no User A or legacy leakage)
  const bRes = await request(app)
    .get("/api/observations")
    .set("Cookie", cookieB);
  assert.equal(bRes.status, 200);
  assert.equal(bRes.body.totalCount, 1);
  assert.equal(bRes.body.observations[0].ssid, `${prefix}_BETA_ONLY`);
});

test("GET /api/observations rejects invalid or out-of-range pagination query params with 400", async () => {
  const invalidUrls = [
    "/api/observations?page=0",
    "/api/observations?page=-1",
    "/api/observations?page=abc",
    "/api/observations?limit=0",
    "/api/observations?limit=101",
    "/api/observations?limit=xyz",
    "/api/observations?page=1&page=2",
  ];

  for (const url of invalidUrls) {
    const res = await request(app).get(url).set("Cookie", cookieA);
    assert.equal(res.status, 400, `Expected 400 for ${url}, got ${res.status}`);
  }
});

test("GET /api/observations/stats supports accurate optional band filtering and rejects invalid band", async () => {
  const allRes = await request(app)
    .get("/api/observations/stats")
    .set("Cookie", cookieA);
  assert.equal(allRes.status, 200);
  assert.equal(allRes.body.analytics.totalNetworks, 5);
  assert.equal(allRes.body.analytics.filteredBand, null);
  assert.equal(allRes.body.analytics.strongestSignal, -45);
  assert.equal(allRes.body.analytics.weakestSignal, -85);
  assert.equal(allRes.body.analytics.averageSignal, -63);

  const band5Res = await request(app)
    .get("/api/observations/stats?band=5%20GHz")
    .set("Cookie", cookieA);
  assert.equal(band5Res.status, 200);
  assert.equal(band5Res.body.analytics.filteredBand, "5 GHz");
  assert.equal(band5Res.body.analytics.totalNetworks, 2);
  assert.equal(band5Res.body.analytics.strongestSignal, -50);
  assert.equal(band5Res.body.analytics.weakestSignal, -70);
  assert.equal(band5Res.body.analytics.averageSignal, -60);
  assert.equal(band5Res.body.analytics.bands.length, 1);
  assert.equal(band5Res.body.analytics.bands[0]._id, "5 GHz");
  assert.equal(band5Res.body.analytics.security.length, 1);
  assert.equal(band5Res.body.analytics.security[0]._id, "WPA3");
  assert.equal(band5Res.body.analytics.security[0].count, 2);

  const band24Res = await request(app)
    .get("/api/observations/stats?band=2.4%20GHz")
    .set("Cookie", cookieA);
  assert.equal(band24Res.status, 200);
  assert.equal(band24Res.body.analytics.filteredBand, "2.4 GHz");
  assert.equal(band24Res.body.analytics.totalNetworks, 3);
  assert.equal(band24Res.body.analytics.strongestSignal, -45);
  assert.equal(band24Res.body.analytics.weakestSignal, -85);
  assert.equal(band24Res.body.analytics.averageSignal, -65);

  const empty6Res = await request(app)
    .get("/api/observations/stats?band=6%20GHz")
    .set("Cookie", cookieA);
  assert.equal(empty6Res.status, 200);
  assert.equal(empty6Res.body.analytics.filteredBand, "6 GHz");
  assert.equal(empty6Res.body.analytics.totalNetworks, 0);
  assert.equal(empty6Res.body.analytics.averageSignal, null);
  assert.equal(empty6Res.body.analytics.strongestSignal, null);
  assert.equal(empty6Res.body.analytics.weakestSignal, null);

  const emptyUnknownRes = await request(app)
    .get("/api/observations/stats?band=Unknown")
    .set("Cookie", cookieA);
  assert.equal(emptyUnknownRes.status, 200);
  assert.equal(emptyUnknownRes.body.analytics.filteredBand, "Unknown");
  assert.equal(emptyUnknownRes.body.analytics.totalNetworks, 0);
  assert.equal(emptyUnknownRes.body.analytics.averageSignal, null);
  assert.equal(emptyUnknownRes.body.analytics.strongestSignal, null);
  assert.equal(emptyUnknownRes.body.analytics.weakestSignal, null);

  const badBand = await request(app)
    .get("/api/observations/stats?band=2.5GHz")
    .set("Cookie", cookieA);
  assert.equal(badBand.status, 400);

  const dupBand = await request(app)
    .get("/api/observations/stats?band=5%20GHz&band=2.4%20GHz")
    .set("Cookie", cookieA);
  assert.equal(dupBand.status, 400);
});

test("GET /api/observations/heatmap returns band-specific strongestSignal and weakestSignal inside frequencyDistribution", async () => {
  const pipeline = buildHeatmapPipeline({ userId: userA._id });
  assert.ok(pipeline[1].$group.maxBand24);
  assert.ok(pipeline[1].$group.minBand24);
  assert.ok(pipeline[1].$group.maxBand5);
  assert.ok(pipeline[1].$group.minBand5);

  const res = await request(app)
    .get("/api/observations/heatmap")
    .set("Cookie", cookieA);

  assert.equal(res.status, 200);
  assert.equal(res.body.heatmap.length, 1);

  const point = res.body.heatmap[0];
  assert.equal(point.totalObservations, 5);
  assert.equal(point.strongestSignal, -45);
  assert.equal(point.weakestSignal, -85);

  const band24 = point.frequencyDistribution.find((b) => b.band === "2.4 GHz");
  const band5 = point.frequencyDistribution.find((b) => b.band === "5 GHz");
  const band6 = point.frequencyDistribution.find((b) => b.band === "6 GHz");

  assert.equal(band24.count, 3);
  assert.equal(band24.strongestSignal, -45);
  assert.equal(band24.weakestSignal, -85);

  assert.equal(band5.count, 2);
  assert.equal(band5.strongestSignal, -50);
  assert.equal(band5.weakestSignal, -70);

  assert.equal(band6.count, 0);
  assert.equal(band6.strongestSignal, null);
  assert.equal(band6.weakestSignal, null);
  assert.deepEqual(res.body.locationSummary, {
    geolocatedObservations: 5,
    nonGeolocatedObservations: 0,
    totalObservations: 5,
  });
});

test("GET /api/observations/heatmap returns accurate non-overlapping locationSummary for geolocated vs non-geolocated observations", async () => {
  await NetworkObservation.create({
    userId: userB._id,
    ssid: "",
    signalDbm: -66,
    frequencyMhz: 2412,
    band: "2.4 GHz",
    security: "WPA2",
    location: null,
    source: "windows",
  });

  const allBRes = await request(app)
    .get("/api/observations/heatmap")
    .set("Cookie", cookieB);
  assert.equal(allBRes.status, 200);
  assert.equal(allBRes.body.heatmap.length, 1);
  assert.deepEqual(allBRes.body.locationSummary, {
    geolocatedObservations: 1,
    nonGeolocatedObservations: 1,
    totalObservations: 2,
  });

  const band24BRes = await request(app)
    .get("/api/observations/heatmap?band=2.4%20GHz")
    .set("Cookie", cookieB);
  assert.equal(band24BRes.status, 200);
  assert.equal(band24BRes.body.heatmap.length, 0);
  assert.deepEqual(band24BRes.body.locationSummary, {
    geolocatedObservations: 0,
    nonGeolocatedObservations: 1,
    totalObservations: 1,
  });
});
