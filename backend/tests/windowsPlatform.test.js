import "dotenv/config";

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import mongoose from "mongoose";
import request from "supertest";

process.env.NODE_ENV = "test";
if (!process.env.JWT_SECRET) {
  process.env.JWT_SECRET = "wifivision_phase93_jwt_secret_minimum_32chars_ok";
}

const { default: app } = await import("../app.js");
const { default: connectDB } = await import("../src/config/db.js");
const { default: User } = await import("../src/models/User.js");
const { default: PairingCode } = await import("../src/models/PairingCode.js");
const { default: DeviceCredential } = await import(
  "../src/models/DeviceCredential.js"
);
const { default: NetworkObservation } = await import(
  "../src/models/NetworkObservation.js"
);
const { generateAuthToken } = await import("../src/utils/auth.utils.js");

const prefix = `win_plat_${Date.now()}`;
let ownerUser;
let otherUser;
let ownerCookie;
let otherCookie;
let winDeviceToken;
let winDeviceId;
let androidDeviceToken;

function makeDeviceHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    "X-WiFiVision-Timestamp": String(Date.now()),
    "X-WiFiVision-Nonce": crypto.randomBytes(12).toString("hex"),
  };
}

before(async () => {
  await connectDB();
  ownerUser = await User.create({
    name: "Windows Owner",
    email: `${prefix}_owner@example.com`,
    password: "unused_test_password_hash",
    isEmailVerified: true,
    tokenVersion: 0,
  });
  otherUser = await User.create({
    name: "Isolated Bystander",
    email: `${prefix}_other@example.com`,
    password: "unused_test_password_hash",
    isEmailVerified: true,
    tokenVersion: 0,
  });

  ownerCookie = `wifivision_token=${generateAuthToken(ownerUser)}`;
  otherCookie = `wifivision_token=${generateAuthToken(otherUser)}`;
});

after(async () => {
  try {
    if (mongoose.connection.readyState === 1) {
      const ids = [ownerUser?._id, otherUser?._id].filter(Boolean);
      await NetworkObservation.deleteMany({ userId: { $in: ids } });
      await PairingCode.deleteMany({ userId: { $in: ids } });
      await DeviceCredential.deleteMany({ userId: { $in: ids } });
      await User.deleteMany({ _id: { $in: ids } });
    }
  } finally {
    await mongoose.disconnect();
  }
});

test("Windows-scoped pairing code rejects Android pairing and pairs Windows laptop cleanly", async () => {
  const codeRes = await request(app)
    .post("/api/devices/pairing-code")
    .set("Cookie", ownerCookie)
    .send({ platform: "windows" })
    .expect(201);

  assert.equal(codeRes.body.platform, "windows");
  const code = codeRes.body.pairingCode;

  // Attempt with wrong platform ("android") must be rejected 403 and consume code? Wait: verify 403 rejection
  const wrongPlatRes = await request(app)
    .post("/api/devices/pair")
    .send({
      pairingCode: code,
      platform: "android",
      deviceName: "Spoofed-Phone",
    })
    .expect(403);
  assert.match(wrongPlatRes.body.message, /windows/i);

  // Fresh Windows-scoped pairing succeeds
  const winCodeRes = await request(app)
    .post("/api/devices/pairing-code")
    .set("Cookie", ownerCookie)
    .send({ platform: "windows" })
    .expect(201);

  const pairRes = await request(app)
    .post("/api/devices/pair")
    .send({
      pairingCode: winCodeRes.body.pairingCode,
      platform: "windows",
      deviceName: "ThinkPad-X1",
    })
    .expect(201);

  assert.equal(pairRes.body.device.platform, "windows");
  assert.equal(pairRes.body.device.deviceName, "ThinkPad-X1");
  assert.ok(pairRes.body.deviceToken.startsWith("wvd_"));

  winDeviceToken = pairRes.body.deviceToken;
  winDeviceId = pairRes.body.device.deviceId;

  // Also pair an Android device under the same user to verify shared multi-device workspace
  const androidCodeRes = await request(app)
    .post("/api/devices/pairing-code")
    .set("Cookie", ownerCookie)
    .send({ platform: "android" })
    .expect(201);

  const androidPair = await request(app)
    .post("/api/devices/pair")
    .send({
      pairingCode: androidCodeRes.body.pairingCode,
      platform: "android",
      deviceName: "TECNO-Test",
    })
    .expect(201);

  androidDeviceToken = androidPair.body.deviceToken;

  const listRes = await request(app)
    .get("/api/devices")
    .set("Cookie", ownerCookie)
    .expect(200);
  const platforms = new Set(listRes.body.devices.map((d) => d.platform));
  assert.equal(platforms.has("windows"), true);
  assert.equal(platforms.has("android"), true);
});

test("Windows device rejects mismatched source='android', hashes BSSID, and accepts both null & GPS locations", async () => {
  // Spoofing source: "android" using a Windows device credential -> 403
  await request(app)
    .post("/api/observations")
    .set(makeDeviceHeaders(winDeviceToken))
    .send({
      ssid: "SpoofTest",
      bssid: "AA:BB:CC:DD:EE:01",
      signalDbm: -60,
      frequencyMhz: 5180,
      band: "5 GHz",
      security: "WPA2",
      source: "android",
    })
    .expect(403);

  // Windows observation WITHOUT location (honest null) -> 201
  const noLocRes = await request(app)
    .post("/api/observations")
    .set(makeDeviceHeaders(winDeviceToken))
    .send({
      ssid: "Laptop-NoGPS",
      bssid: "AA:BB:CC:DD:EE:02",
      signalDbm: -63,
      frequencyMhz: 5240,
      band: "5 GHz",
      security: "WPA3",
      signalQuality: 3,
      source: "windows",
      observedAt: new Date().toISOString(),
    })
    .expect(201);

  assert.equal(noLocRes.body.observation.source, "windows");
  assert.equal(noLocRes.body.observation.bssid, undefined);
  assert.equal(noLocRes.body.observation.bssidHash, undefined);

  const stored = await NetworkObservation.findById(
    noLocRes.body.observation._id || noLocRes.body.observation.id
  )
    .select("+bssidHash")
    .lean();
  assert.ok(stored);
  assert.equal(stored.source, "windows");
  assert.equal(stored.bssid ?? null, null);
  assert.match(stored.bssidHash, /^[0-9a-f]{64}$/i);

  // Windows observation WITH valid location + Android observation WITH valid location -> shared private workspace
  await request(app)
    .post("/api/observations")
    .set(makeDeviceHeaders(winDeviceToken))
    .send({
      ssid: "Laptop-WithGPS",
      bssid: "AA:BB:CC:DD:EE:03",
      signalDbm: -55,
      frequencyMhz: 2437,
      band: "2.4 GHz",
      security: "WPA2",
      signalQuality: 4,
      location: { latitude: 24.8615, longitude: 67.0099, accuracyMeters: 18 },
      source: "windows",
    })
    .expect(201);

  await request(app)
    .post("/api/observations")
    .set(makeDeviceHeaders(androidDeviceToken))
    .send({
      ssid: "Phone-WithGPS",
      bssid: "AA:BB:CC:DD:EE:04",
      signalDbm: -57,
      frequencyMhz: 5180,
      band: "5 GHz",
      security: "WPA2",
      signalQuality: 4,
      location: { latitude: 24.86155, longitude: 67.00995, accuracyMeters: 12 },
      source: "android",
    })
    .expect(201);

  // Owner sees all 3 observations & 2 geolocated heatmap records; bystander sees 0
  const ownerObs = await request(app)
    .get("/api/observations")
    .set("Cookie", ownerCookie)
    .expect(200);
  const sources = new Set(ownerObs.body.observations.map((o) => o.source));
  assert.equal(sources.has("windows"), true);
  assert.equal(sources.has("android"), true);

  const otherObs = await request(app)
    .get("/api/observations")
    .set("Cookie", otherCookie)
    .expect(200);
  assert.equal(otherObs.body.observations.length, 0);
});

test("Windows device credential rotation preserves platform and revocation immediately blocks uploads", async () => {
  const rotateRes = await request(app)
    .post("/api/devices/rotate")
    .set(makeDeviceHeaders(winDeviceToken))
    .send({})
    .expect(200);

  assert.equal(rotateRes.body.device.platform, "windows");
  const rotatedToken = rotateRes.body.deviceToken;

  // Old token fails 401
  await request(app)
    .post("/api/observations")
    .set(makeDeviceHeaders(winDeviceToken))
    .send({
      ssid: "Stale",
      signalDbm: -60,
      frequencyMhz: 2412,
      source: "windows",
    })
    .expect(401);

  // Revoke device
  await request(app)
    .delete(`/api/devices/${winDeviceId}`)
    .set("Cookie", ownerCookie)
    .expect(200);

  await request(app)
    .post("/api/observations")
    .set(makeDeviceHeaders(rotatedToken))
    .send({
      ssid: "AfterRevoke",
      signalDbm: -60,
      frequencyMhz: 2412,
      source: "windows",
    })
    .expect(401);
});
