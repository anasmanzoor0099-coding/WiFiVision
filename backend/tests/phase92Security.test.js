import "dotenv/config";

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import mongoose from "mongoose";
import request from "supertest";

const originalNodeEnv = process.env.NODE_ENV;
const originalJwtSecret = process.env.JWT_SECRET;
const originalUpstashUrl = process.env.UPSTASH_REDIS_REST_URL;
const originalUpstashToken = process.env.UPSTASH_REDIS_REST_TOKEN;
const originalBssidSecret = process.env.BSSID_HMAC_SECRET;
const originalDevicePepper = process.env.DEVICE_TOKEN_PEPPER;

process.env.NODE_ENV = "test";
if (!process.env.JWT_SECRET) {
  process.env.JWT_SECRET = "wifivision_phase92_jwt_secret_minimum_32chars_ok";
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
const { default: PerformanceMeasurement } = await import(
  "../src/models/PerformanceMeasurement.js"
);
const { generateAuthToken } = await import("../src/utils/auth.utils.js");
const { createDistributedLimiter } = await import(
  "../src/utils/rateLimiter.js"
);

const runId = Date.now();
const prefix = `phase92_${runId}`;
let userA;
let userB;
let cookieA;
let cookieB;
let deviceAToken;
let deviceAId;
let obsAId;

function makeDeviceHeaders(token, customNonce) {
  return {
    Authorization: `Bearer ${token}`,
    "X-WiFiVision-Timestamp": String(Date.now()),
    "X-WiFiVision-Nonce": customNonce || crypto.randomBytes(12).toString("hex"),
  };
}

before(async () => {
  await connectDB();

  userA = await User.create({
    name: "User Alpha",
    email: `${prefix}_alpha@example.com`,
    password: "hashed_unused_for_jwt",
    isEmailVerified: true,
    tokenVersion: 0,
  });

  userB = await User.create({
    name: "User Beta",
    email: `${prefix}_beta@example.com`,
    password: "hashed_unused_for_jwt",
    isEmailVerified: true,
    tokenVersion: 0,
  });

  cookieA = `wifivision_token=${generateAuthToken(userA)}`;
  cookieB = `wifivision_token=${generateAuthToken(userB)}`;
});

after(async () => {
  try {
    if (mongoose.connection.readyState === 1) {
      await NetworkObservation.deleteMany({
        userId: { $in: [userA?._id, userB?._id].filter(Boolean) },
      });
      await PerformanceMeasurement.deleteMany({
        userId: { $in: [userA?._id, userB?._id].filter(Boolean) },
      });
      await PairingCode.deleteMany({
        userId: { $in: [userA?._id, userB?._id].filter(Boolean) },
      });
      await DeviceCredential.deleteMany({
        userId: { $in: [userA?._id, userB?._id].filter(Boolean) },
      });
      await User.deleteMany({
        _id: { $in: [userA?._id, userB?._id].filter(Boolean) },
      });
    }
  } finally {
    process.env.NODE_ENV = originalNodeEnv;
    if (originalJwtSecret === undefined) delete process.env.JWT_SECRET;
    else process.env.JWT_SECRET = originalJwtSecret;
    if (originalUpstashUrl === undefined) delete process.env.UPSTASH_REDIS_REST_URL;
    else process.env.UPSTASH_REDIS_REST_URL = originalUpstashUrl;
    if (originalUpstashToken === undefined)
      delete process.env.UPSTASH_REDIS_REST_TOKEN;
    else process.env.UPSTASH_REDIS_REST_TOKEN = originalUpstashToken;
    if (originalBssidSecret === undefined) delete process.env.BSSID_HMAC_SECRET;
    else process.env.BSSID_HMAC_SECRET = originalBssidSecret;
    if (originalDevicePepper === undefined) delete process.env.DEVICE_TOKEN_PEPPER;
    else process.env.DEVICE_TOKEN_PEPPER = originalDevicePepper;
    await mongoose.disconnect();
  }
});

test("6. Missing authentication returns 401 across all protected telemetry & device routes", async () => {
  const routes = [
    ["get", "/api/observations"],
    ["get", "/api/observations/stats"],
    ["get", "/api/observations/heatmap"],
    ["get", "/api/observations/density"],
    ["get", "/api/quality"],
    ["get", "/api/performance/history"],
    ["get", "/api/performance/analytics"],
    ["get", "/api/performance/trends"],
    ["post", "/api/performance"],
    ["post", "/api/observations"],
    ["get", "/api/devices"],
    ["post", "/api/devices/pairing-code"],
    ["delete", `/api/devices/${new mongoose.Types.ObjectId().toString()}`],
  ];

  for (const [method, url] of routes) {
    const res = await request(app)[method](url);
    assert.equal(res.status, 401, `Expected 401 for ${method.toUpperCase()} ${url}`);
  }
});

test("9-11. Pairing codes: 5-min expiry, single-use, and atomic concurrent consumption", async () => {
  // Generate pairing code as User A
  const genRes = await request(app)
    .post("/api/devices/pairing-code")
    .set("Cookie", cookieA);
  assert.equal(genRes.status, 201);
  assert.equal(genRes.body.success, true);
  assert.match(genRes.body.pairingCode, /^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$/);
  assert.equal(genRes.body.expiresInSeconds, 300);

  // Concurrent double pairing attempt with same code: only 1 succeeds
  const [r1, r2] = await Promise.all([
    request(app)
      .post("/api/devices/pair")
      .send({ pairingCode: genRes.body.pairingCode, deviceName: "TECNO KN4 A1" }),
    request(app)
      .post("/api/devices/pair")
      .send({ pairingCode: genRes.body.pairingCode, deviceName: "TECNO KN4 A2" }),
  ]);

  const statuses = [r1.status, r2.status].sort();
  assert.deepEqual(statuses, [201, 401]);
  const winner = r1.status === 201 ? r1 : r2;
  deviceAToken = winner.body.deviceToken;
  deviceAId = winner.body.device.deviceId;
  assert.ok(deviceAToken?.startsWith(`wvd_${deviceAId}_`));
  assert.deepEqual(winner.body.device.scopes, ["observations:write"]);

  // Reuse after consumption rejected
  const reuseRes = await request(app)
    .post("/api/devices/pair")
    .send({ pairingCode: genRes.body.pairingCode });
  assert.equal(reuseRes.status, 401);

  // Expired pairing code rejected
  const genRes2 = await request(app)
    .post("/api/devices/pairing-code")
    .set("Cookie", cookieA);
  assert.equal(genRes2.status, 201);
  await PairingCode.updateMany(
    { userId: userA._id, consumedAt: null },
    { $set: { expiresAt: new Date(Date.now() - 1000) } }
  );
  const expiredPairRes = await request(app)
    .post("/api/devices/pair")
    .send({ pairingCode: genRes2.body.pairingCode });
  assert.equal(expiredPairRes.status, 401);
});

test("7,15,16,17. Device uploads enforce ownership, reject forged userId, HMAC-hash BSSID without persisting raw BSSID, and block replays", async () => {
  const rawBssid = "C4:AD:34:AA:BB:99";
  const nonce = crypto.randomBytes(12).toString("hex");
  const headers = makeDeviceHeaders(deviceAToken, nonce);

  const payload = {
    userId: userB._id.toString(), // forged userId must be ignored
    ssid: `${prefix}_AlphaNet`,
    bssid: rawBssid,
    signalDbm: -53,
    frequencyMhz: 5180,
    band: "5 GHz",
    security: "WPA3",
    signalQuality: 4,
    location: {
      latitude: 31.5204,
      longitude: 74.3587,
      accuracyMeters: 9.5,
    },
    source: "android",
  };

  const uploadRes = await request(app)
    .post("/api/observations")
    .set(headers)
    .send(payload);

  assert.equal(uploadRes.status, 201);
  assert.equal(uploadRes.body.observation.bssid, undefined);
  assert.equal(uploadRes.body.observation.bssidHash, undefined);
  obsAId = uploadRes.body.observation.id;

  // Replay attempt with exact same nonce & timestamp must be rejected
  const replayRes = await request(app)
    .post("/api/observations")
    .set(headers)
    .send(payload);
  assert.equal(replayRes.status, 401);

  // Verify stored DB document has userA ownership, bssidHash set, and raw bssid undefined
  const dbDoc = await NetworkObservation.findById(obsAId)
    .select("+bssid +bssidHash")
    .lean();
  assert.equal(String(dbDoc.userId), String(userA._id));
  assert.equal(String(dbDoc.deviceId), String(deviceAId));
  assert.equal(dbDoc.bssid, undefined);
  assert.match(dbDoc.bssidHash, /^[0-9a-f]{64}$/);
  assert.notEqual(dbDoc.bssidHash, rawBssid);
});

test("13-14. Device credentials cannot read dashboard APIs, administer devices, or trigger performance tests", async () => {
  const blockedEndpoints = [
    ["get", "/api/observations"],
    ["get", "/api/observations/stats"],
    ["get", "/api/observations/heatmap"],
    ["get", "/api/observations/density"],
    ["get", "/api/quality"],
    ["post", "/api/performance"],
    ["get", "/api/performance/history"],
    ["get", "/api/devices"],
    ["post", "/api/devices/pairing-code"],
    ["get", "/api/auth/me"],
  ];

  for (const [method, url] of blockedEndpoints) {
    const res = await request(app)
      [method](url)
      .set(makeDeviceHeaders(deviceAToken));
    assert.equal(res.status, 401, `Device credential must not authorize ${method.toUpperCase()} ${url}`);
  }
});

test("20-21 & 4-5. GET /api/performance returns 405; POST /api/performance requires auth and isolates performance history/analytics", async () => {
  const getPerf = await request(app).get("/api/performance").set("Cookie", cookieA);
  assert.equal(getPerf.status, 405);

  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    const u = String(url);
    if (u.startsWith("https://speed.cloudflare.com/")) {
      if (u.includes("bytes=0")) {
        return new Response(new Uint8Array(0), { status: 200 });
      }
      if (u.includes("__down")) {
        return new Response(new Uint8Array(64 * 1024), { status: 200 });
      }
      if (u.includes("__up")) {
        return new Response("ok", { status: 200 });
      }
    }
    return realFetch(url, init);
  };

  try {
    const postPerfA = await request(app)
      .post("/api/performance")
      .set("Cookie", cookieA)
      .send({ userId: userB._id.toString() }); // forged userId ignored
    assert.equal(postPerfA.status, 201);
    assert.equal(postPerfA.body.success, true);

    const histA = await request(app)
      .get("/api/performance/history")
      .set("Cookie", cookieA);
    assert.equal(histA.status, 200);
    assert.equal(histA.body.count, 1);

    // User B sees 0 performance measurements (no leakage from User A or legacy docs)
    const histB = await request(app)
      .get("/api/performance/history")
      .set("Cookie", cookieB);
    assert.equal(histB.status, 200);
    assert.equal(histB.body.count, 0);

    const analyticsB = await request(app)
      .get("/api/performance/analytics")
      .set("Cookie", cookieB);
    assert.equal(analyticsB.status, 200);
    assert.equal(analyticsB.body.analytics.totalTests, 0);

    const trendsB = await request(app)
      .get("/api/performance/trends")
      .set("Cookie", cookieB);
    assert.equal(trendsB.status, 200);
    assert.equal(trendsB.body.count, 0);
  } finally {
    globalThis.fetch = realFetch;
  }
});

test("1-3, 8, 22. User B cannot see User A telemetry or unassigned 52 legacy records, even with forged query params", async () => {
  // User A sees their 1 uploaded observation
  const aObs = await request(app)
    .get("/api/observations")
    .set("Cookie", cookieA);
  assert.equal(aObs.status, 200);
  assert.equal(aObs.body.count, 1);
  assert.equal(aObs.body.observations[0].ssid, `${prefix}_AlphaNet`);

  // User B tries forged userId in query string
  const bObs = await request(app)
    .get(`/api/observations?userId=${userA._id.toString()}`)
    .set("Cookie", cookieB);
  assert.equal(bObs.status, 200);
  assert.equal(bObs.body.count, 0);

  const bStats = await request(app)
    .get("/api/observations/stats")
    .set("Cookie", cookieB);
  assert.equal(bStats.status, 200);
  assert.equal(bStats.body.analytics.totalNetworks, 0);

  const bHeatmap = await request(app)
    .get(`/api/observations/heatmap?userId=${userA._id.toString()}`)
    .set("Cookie", cookieB);
  assert.equal(bHeatmap.status, 200);
  assert.equal(bHeatmap.body.count, 0);

  const bDensity = await request(app)
    .get("/api/observations/density")
    .set("Cookie", cookieB);
  assert.equal(bDensity.status, 200);
  assert.equal(bDensity.body.count, 0);

  const bQuality = await request(app)
    .get("/api/quality")
    .set("Cookie", cookieB);
  assert.equal(bQuality.status, 200);
  assert.equal(bQuality.body.count, 0);

  // Also verify unassigned legacy observations remain untouched in MongoDB (still >= 52)
  const legacyCount = await NetworkObservation.countDocuments({
    userId: null,
  });
  assert.ok(legacyCount >= 52, `Expected >= 52 unassigned legacy records, found ${legacyCount}`);
});

test("12. Device rotation works and revocation immediately blocks uploads; User B cannot revoke User A device", async () => {
  // Rotate device token as paired device
  const rotRes = await request(app)
    .post("/api/devices/rotate")
    .set(makeDeviceHeaders(deviceAToken));
  assert.equal(rotRes.status, 200);
  const rotatedToken = rotRes.body.deviceToken;
  assert.notEqual(rotatedToken, deviceAToken);

  // Old secret no longer valid
  const staleRes = await request(app)
    .post("/api/observations")
    .set(makeDeviceHeaders(deviceAToken))
    .send({ signalDbm: -60, frequencyMhz: 2437 });
  assert.equal(staleRes.status, 401);

  // User B cannot list or revoke User A's device
  const listB = await request(app).get("/api/devices").set("Cookie", cookieB);
  assert.equal(listB.status, 200);
  assert.equal(listB.body.count, 0);

  const crossRevoke = await request(app)
    .delete(`/api/devices/${deviceAId}`)
    .set("Cookie", cookieB);
  assert.equal(crossRevoke.status, 404);

  // User A revokes device
  const revokeA = await request(app)
    .delete(`/api/devices/${deviceAId}`)
    .set("Cookie", cookieA);
  assert.equal(revokeA.status, 200);
  assert.equal(revokeA.body.device.status, "revoked");

  // Upload immediately blocked after revocation
  const postRevokeUpload = await request(app)
    .post("/api/observations")
    .set(makeDeviceHeaders(rotatedToken))
    .send({ signalDbm: -60, frequencyMhz: 2437, source: "android" });
  assert.equal(postRevokeUpload.status, 401);
});

test("Logout-all increments tokenVersion and immediately revokes existing user JWTs", async () => {
  const logoutAllRes = await request(app)
    .post("/api/auth/logout-all")
    .set("Cookie", cookieB);
  assert.equal(logoutAllRes.status, 200);

  const postRevokeMe = await request(app)
    .get("/api/auth/me")
    .set("Cookie", cookieB);
  assert.equal(postRevokeMe.status, 401);
});

test("18-19. Distributed Upstash Redis limits share state across limiter instances and fail closed in production outage", async () => {
  // Simulate shared Redis store across two separate createDistributedLimiter instances
  const sharedRedisCounts = new Map();
  const mockUpstashFetch = async (_url, init) => {
    const commands = JSON.parse(init.body);
    const results = commands.map(([cmd, key]) => {
      if (cmd === "INCR") {
        const next = (sharedRedisCounts.get(key) || 0) + 1;
        sharedRedisCounts.set(key, next);
        return { result: next };
      }
      if (cmd === "PTTL") {
        return { result: 60000 };
      }
      if (cmd === "PEXPIRE") {
        return { result: 1 };
      }
      return { result: null };
    });
    return new Response(JSON.stringify(results), { status: 200 });
  };

  process.env.UPSTASH_REDIS_REST_URL = "https://mock-upstash.example.com";
  process.env.UPSTASH_REDIS_REST_TOKEN = "mock-token";

  const limiter1 = createDistributedLimiter({
    name: `shared-rl-${runId}`,
    windowMs: 60_000,
    limit: 2,
    fetcher: mockUpstashFetch,
  });
  const limiter2 = createDistributedLimiter({
    name: `shared-rl-${runId}`,
    windowMs: 60_000,
    limit: 2,
    fetcher: mockUpstashFetch,
  });

  const makeMockRes = () => {
    const resObj = {
      statusCode: 200,
      headers: {},
      body: null,
      setHeader(k, v) {
        this.headers[k] = v;
      },
      status(code) {
        this.statusCode = code;
        return this;
      },
      json(data) {
        this.body = data;
        return this;
      },
    };
    return resObj;
  };

  let nextCalls = 0;
  await limiter1({ ip: "1.2.3.4" }, makeMockRes(), () => {
    nextCalls++;
  });
  await limiter2({ ip: "1.2.3.4" }, makeMockRes(), () => {
    nextCalls++;
  });
  const thirdRes = makeMockRes();
  await limiter1({ ip: "1.2.3.4" }, thirdRes, () => {
    nextCalls++;
  });

  assert.equal(nextCalls, 2);
  assert.equal(thirdRes.statusCode, 429);

  // Production outage or missing Redis config must fail closed with 503
  process.env.NODE_ENV = "production";
  const outageLimiter = createDistributedLimiter({
    name: `outage-rl-${runId}`,
    windowMs: 60_000,
    limit: 5,
    fetcher: async () => {
      throw new Error("Redis connection refused");
    },
  });
  const outageRes = makeMockRes();
  let passedOnOutage = false;
  await outageLimiter({ ip: "1.2.3.4" }, outageRes, () => {
    passedOnOutage = true;
  });
  assert.equal(passedOnOutage, false);
  assert.equal(outageRes.statusCode, 503);

  delete process.env.UPSTASH_REDIS_REST_URL;
  delete process.env.UPSTASH_REDIS_REST_TOKEN;
  const missingConfigRes = makeMockRes();
  let passedMissing = false;
  await outageLimiter({ ip: "1.2.3.4" }, missingConfigRes, () => {
    passedMissing = true;
  });
  assert.equal(passedMissing, false);
  assert.equal(missingConfigRes.statusCode, 503);
});
