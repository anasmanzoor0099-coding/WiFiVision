import "dotenv/config";

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import jwt from "jsonwebtoken";
import mongoose from "mongoose";
import request from "supertest";

const originalNodeEnv = process.env.NODE_ENV;
const originalJwtSecret = process.env.JWT_SECRET;

process.env.NODE_ENV = "test";
if (!process.env.JWT_SECRET) {
  process.env.JWT_SECRET = "wifivision_phase9_security_test_secret_key_32chars";
}

const { default: app } = await import("../app.js");
const { default: connectDB } = await import("../src/config/db.js");
const { default: User } = await import("../src/models/User.js");
const { default: DeviceCredential } = await import(
  "../src/models/DeviceCredential.js"
);
const { default: NetworkObservation } = await import(
  "../src/models/NetworkObservation.js"
);
const { hashToken, generateAuthToken } = await import(
  "../src/utils/auth.utils.js"
);
const { hashDeviceSecret } = await import("../src/utils/cryptoSecrets.js");

const runId = Date.now();
const testEmailPrefix = `wifivision_sec_test_${runId}`;
const testSsidPrefix = `WiFiVision_SecTest_${runId}`;
let verifiedUser;
let verifiedCookie;
let testDevice;
let testDeviceToken;

function deviceHeaders() {
  return {
    Authorization: `Bearer ${testDeviceToken}`,
    "X-WiFiVision-Timestamp": String(Date.now()),
    "X-WiFiVision-Nonce": crypto.randomBytes(12).toString("hex"),
  };
}

before(async () => {
  await connectDB();

  verifiedUser = await User.create({
    name: "Sec Verified User",
    email: `${testEmailPrefix}_verified@example.com`,
    password: "hashed_unused_for_jwt",
    isEmailVerified: true,
  });
  verifiedCookie = `wifivision_token=${generateAuthToken(verifiedUser)}`;

  const rawSecret = crypto.randomBytes(32).toString("hex");
  testDevice = await DeviceCredential.create({
    userId: verifiedUser._id,
    deviceName: "Sec Test Phone",
    secretHash: hashDeviceSecret(rawSecret),
    scopes: ["observations:write"],
    status: "active",
    expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
  });
  testDeviceToken = `wvd_${testDevice._id.toString()}_${rawSecret}`;
});

after(async () => {
  try {
    if (mongoose.connection.readyState === 1) {
      await User.deleteMany({
        email: { $regex: `^${testEmailPrefix}` },
      });
      await DeviceCredential.deleteMany({
        _id: testDevice?._id,
      });
      await NetworkObservation.deleteMany({
        ssid: { $regex: `^(?:${testSsidPrefix}|<script>sec-${runId})` },
      });
    }
  } finally {
    process.env.NODE_ENV = originalNodeEnv;
    if (originalJwtSecret === undefined) {
      delete process.env.JWT_SECRET;
    } else {
      process.env.JWT_SECRET = originalJwtSecret;
    }
    await mongoose.disconnect();
  }
});

test("Rejects unauthenticated, invalid, alg-none, wrong-secret, and expired JWTs", async () => {
  const unauthMe = await request(app).get("/api/auth/me");
  assert.equal(unauthMe.status, 401);

  const unauthLogout = await request(app).post("/api/auth/logout");
  assert.equal(unauthLogout.status, 401);

  const wrongSecretToken = jwt.sign(
    { sub: new mongoose.Types.ObjectId().toString(), email: "a@example.com" },
    "wrong_secret_key_that_must_not_validate_123456",
    { issuer: "wifivision", audience: "wifivision-users", algorithm: "HS256" }
  );
  const wrongSecretRes = await request(app)
    .get("/api/auth/me")
    .set("Cookie", `wifivision_token=${wrongSecretToken}`);
  assert.equal(wrongSecretRes.status, 401);

  const headerB64 = Buffer.from(
    JSON.stringify({ alg: "none", typ: "JWT" })
  ).toString("base64url");
  const payloadB64 = Buffer.from(
    JSON.stringify({
      sub: new mongoose.Types.ObjectId().toString(),
      email: "a@example.com",
      iss: "wifivision",
      aud: "wifivision-users",
    })
  ).toString("base64url");
  const algNoneToken = `${headerB64}.${payloadB64}.`;
  const algNoneRes = await request(app)
    .get("/api/auth/me")
    .set("Cookie", `wifivision_token=${algNoneToken}`);
  assert.equal(algNoneRes.status, 401);

  const expiredToken = jwt.sign(
    { sub: new mongoose.Types.ObjectId().toString(), email: "a@example.com" },
    process.env.JWT_SECRET,
    {
      expiresIn: -30,
      issuer: "wifivision",
      audience: "wifivision-users",
      algorithm: "HS256",
    }
  );
  const expiredRes = await request(app)
    .get("/api/auth/me")
    .set("Cookie", `wifivision_token=${expiredToken}`);
  assert.equal(expiredRes.status, 401);
});

test("Rejects unauthorized unverified user JWT with 403", async () => {
  const unverified = await User.create({
    name: "Sec Unverified",
    email: `${testEmailPrefix}_unverified@example.com`,
    isEmailVerified: false,
  });

  const token = generateAuthToken(unverified);
  const res = await request(app)
    .get("/api/auth/me")
    .set("Cookie", `wifivision_token=${token}`);
  assert.equal(res.status, 403);
});

test("Enforces production JWT_SECRET minimum length", () => {
  const savedEnv = process.env.NODE_ENV;
  const savedSecret = process.env.JWT_SECRET;
  try {
    process.env.NODE_ENV = "production";
    process.env.JWT_SECRET = "too_short_secret";
    assert.throws(
      () =>
        generateAuthToken({
          _id: new mongoose.Types.ObjectId(),
          email: "prod@example.com",
        }),
      /at least 32 characters/
    );
  } finally {
    process.env.NODE_ENV = savedEnv;
    process.env.JWT_SECRET = savedSecret;
  }
});

test("Blocks NoSQL operator injection, dotted keys, prototype keys, and query array pollution", async () => {
  const loginInjection = await request(app)
    .post("/api/auth/login")
    .send({ email: { $ne: "" }, password: { $ne: "" } });
  assert.equal(loginInjection.status, 400);
  assert.equal(loginInjection.body.success, false);

  const obsDotted = await request(app)
    .post("/api/observations")
    .set(deviceHeaders())
    .send({
      ssid: `${testSsidPrefix}_dot`,
      signalDbm: -60,
      frequencyMhz: 2412,
      "location.latitude": 10,
    });
  assert.equal(obsDotted.status, 400);

  const queryInjection = await request(app)
    .get("/api/observations/heatmap?band[$ne]=5%20GHz")
    .set("Cookie", verifiedCookie);
  assert.equal(queryInjection.status, 400);

  const arrayPollution = await request(app)
    .get("/api/observations/heatmap?band=5%20GHz&band=2.4%20GHz")
    .set("Cookie", verifiedCookie);
  assert.equal(arrayPollution.status, 400);

  const protoPayload = JSON.parse(
    '{"signalDbm":-55,"frequencyMhz":2437,"__proto__":{"polluted":true}}'
  );
  const protoRes = await request(app)
    .post("/api/observations")
    .set(deviceHeaders())
    .send(protoPayload);
  assert.equal(protoRes.status, 400);
});

test("Validates observation payloads, rejects invalid MAC/control chars, and omits bssid from API responses", async () => {
  const badMac = await request(app)
    .post("/api/observations")
    .set(deviceHeaders())
    .send({
      ssid: `${testSsidPrefix}_badmac`,
      bssid: "NOT_A_MAC_ADDRESS",
      signalDbm: -55,
      frequencyMhz: 2437,
    });
  assert.equal(badMac.status, 400);

  const badControl = await request(app)
    .post("/api/observations")
    .set(deviceHeaders())
    .send({
      ssid: "Bad\x00Control",
      signalDbm: -55,
      frequencyMhz: 2437,
    });
  assert.equal(badControl.status, 400);

  const androidCompatible = await request(app)
    .post("/api/observations")
    .set(deviceHeaders())
    .send({
      ssid: `${testSsidPrefix}_android`,
      bssid: "AA:11:BB:22:CC:33",
      signalDbm: -62,
      frequencyMhz: 5180,
      band: "5 GHz",
      security: "WPA3",
      signalQuality: 4,
      location: {
        latitude: 29.3956,
        longitude: 71.6836,
        accuracyMeters: 12.4,
      },
      source: "android",
    });
  assert.equal(androidCompatible.status, 201);
  assert.equal(androidCompatible.body.observation.bssid, undefined);

  const xssSsid = `<script>sec-${runId}</script>`;
  const xssPost = await request(app)
    .post("/api/observations")
    .set(deviceHeaders())
    .send({
      ssid: xssSsid,
      bssid: "AA:11:BB:22:CC:44",
      signalDbm: -68,
      frequencyMhz: 2437,
      band: "2.4 GHz",
      security: "WPA2",
      source: "android",
    });
  assert.equal(xssPost.status, 201);
  assert.match(xssPost.headers["content-type"] || "", /application\/json/);

  const feedRes = await request(app)
    .get("/api/observations")
    .set("Cookie", verifiedCookie);
  assert.equal(feedRes.status, 200);
  const matched = feedRes.body.observations.find(
    (item) => item.ssid === `${testSsidPrefix}_android`
  );
  assert.ok(matched);
  assert.equal(matched.bssid, undefined);
});

test("Rejects oversized JSON payloads cleanly with 413", async () => {
  const oversized = JSON.stringify({ data: "X".repeat(1_100_000) });
  const res = await request(app)
    .post("/api/observations")
    .set("Content-Type", "application/json")
    .send(oversized);
  assert.equal(res.status, 413);
});

test("Locks out OTP after 5 failed verification attempts", async () => {
  const email = `${testEmailPrefix}_otplock@example.com`;
  await User.create({
    name: "OTP Lockout Test",
    email,
    isEmailVerified: false,
    emailVerificationToken: hashToken("918273"),
    emailVerificationExpires: new Date(Date.now() + 10 * 60 * 1000),
    emailVerificationAttempts: 0,
  });

  for (let i = 0; i < 4; i++) {
    const wrongRes = await request(app)
      .post("/api/auth/register/verify")
      .send({ email, code: "000001" });
    assert.equal(wrongRes.status, 400);
  }

  const lockRes = await request(app)
    .post("/api/auth/register/verify")
    .send({ email, code: "000001" });
  assert.equal(lockRes.status, 429);

  const postLockRes = await request(app)
    .post("/api/auth/register/verify")
    .send({ email, code: "918273" });
  assert.equal(postLockRes.status, 400);
});

test("Enforces CORS origin allowlist and exposes RateLimit headers with 429 protection", async () => {
  const blocked = await request(app)
    .get("/api/observations")
    .set("Origin", "https://attacker.invalid");
  assert.equal(blocked.headers["access-control-allow-origin"], undefined);

  let lastStatus = 200;
  let sawRateLimitHeader = false;
  for (let i = 0; i < 7; i++) {
    const res = await request(app)
      .post("/api/auth/resend-code")
      .send({ email: `${testEmailPrefix}_ratelimit@example.com` });
    if (res.headers["ratelimit"] || res.headers["ratelimit-limit"]) {
      sawRateLimitHeader = true;
    }
    lastStatus = res.status;
  }

  assert.equal(sawRateLimitHeader, true);
  assert.equal(lastStatus, 429);
});
