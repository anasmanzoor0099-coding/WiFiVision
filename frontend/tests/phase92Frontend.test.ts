import test from "node:test";
import assert from "node:assert/strict";

import {
  formatPairingCountdown,
  getRemainingPairingSeconds,
  isPairingCodeExpired,
} from "../src/lib/devicePairing";

test("Pairing code expiry timer accurately reports active countdown and expired state", () => {
  const now = 1_700_000_000_000;
  const activeExpiry = new Date(now + 125_000).toISOString();
  const expiredExpiry = new Date(now - 5_000).toISOString();

  assert.equal(getRemainingPairingSeconds(activeExpiry, now), 125);
  assert.equal(isPairingCodeExpired(activeExpiry, now), false);
  assert.equal(formatPairingCountdown(125), "2:05");

  assert.equal(getRemainingPairingSeconds(expiredExpiry, now), 0);
  assert.equal(isPairingCodeExpired(expiredExpiry, now), true);
  assert.equal(formatPairingCountdown(0), "0:00");
});

test("Same-origin NEXT_PUBLIC_API_URL=/api routes pairing, device listing, revocation, and POST performance requests with credentials", async () => {
  const savedPublicApi = process.env.NEXT_PUBLIC_API_URL;
  process.env.NEXT_PUBLIC_API_URL = "/api";

  const calls: Array<{ url: string; method: string; credentials?: RequestCredentials }> = [];
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (input: unknown, init: RequestInit = {}) => {
    const url = String(input);
    const method = (init.method || "GET").toUpperCase();
    calls.push({ url, method, credentials: init.credentials });

    if (url === "/api/devices/pairing-code" && method === "POST") {
      return new Response(
        JSON.stringify({
          success: true,
          pairingCode: "ABCD2345",
          expiresAt: new Date(Date.now() + 300_000).toISOString(),
          expiresInSeconds: 300,
        }),
        { status: 201 }
      );
    }

    if (url === "/api/devices" && method === "GET") {
      return new Response(
        JSON.stringify({
          success: true,
          count: 1,
          devices: [
            {
              deviceId: "dev_123",
              deviceName: "TECNO KN4",
              scopes: ["observations:write"],
              status: "active",
              expiresAt: new Date(Date.now() + 86_400_000).toISOString(),
              lastUsedAt: null,
              createdAt: new Date().toISOString(),
            },
          ],
        }),
        { status: 200 }
      );
    }

    if (url === "/api/devices/dev_123" && method === "DELETE") {
      return new Response(
        JSON.stringify({
          success: true,
          message: "Device revoked immediately.",
          device: {
            deviceId: "dev_123",
            deviceName: "TECNO KN4",
            scopes: ["observations:write"],
            status: "revoked",
            revokedAt: new Date().toISOString(),
            expiresAt: new Date().toISOString(),
            createdAt: new Date().toISOString(),
          },
        }),
        { status: 200 }
      );
    }

    if (url === "/api/performance" && method === "POST") {
      return new Response(
        JSON.stringify({
          success: true,
          message: "OK",
          savedMeasurementId: "perf_1",
          performance: {
            latencyMs: 25,
            medianLatencyMs: 24,
            jitterMs: 3,
            downloadMbps: 55,
            uploadMbps: 18,
            measuredAt: new Date().toISOString(),
            packetLoss: {
              totalPackets: 20,
              successfulPackets: 20,
              lostPackets: 0,
              packetLossPercentage: 0,
              averageResponseMs: 25,
              measurementType: "HTTP application-level request success",
            },
          },
        }),
        { status: 201 }
      );
    }

    return new Response(JSON.stringify({ success: false }), { status: 401 });
  };

  try {
    const dashboard = await import("../src/lib/dashboard");
    const auth = await import("../src/lib/auth");

    const codeRes = await dashboard.generateDevicePairingCode();
    assert.equal(codeRes.pairingCode, "ABCD2345");

    const listRes = await dashboard.listPairedDevices();
    assert.equal(listRes.count, 1);
    assert.equal(listRes.devices[0].deviceName, "TECNO KN4");

    const revRes = await dashboard.revokePairedDevice("dev_123");
    assert.equal(revRes.device.status, "revoked");

    const perfRes = await dashboard.runPerformanceTest();
    assert.equal(perfRes.savedMeasurementId, "perf_1");

    await assert.rejects(() => auth.getCurrentUser());

    assert.deepEqual(
      calls.map((c) => `${c.method} ${c.url}`),
      [
        "POST /api/devices/pairing-code",
        "GET /api/devices",
        "DELETE /api/devices/dev_123",
        "POST /api/performance",
        "GET /api/auth/me",
      ]
    );
    assert.ok(calls.every((c) => c.credentials === "include"));
  } finally {
    globalThis.fetch = originalFetch;
    if (savedPublicApi === undefined) delete process.env.NEXT_PUBLIC_API_URL;
    else process.env.NEXT_PUBLIC_API_URL = savedPublicApi;
  }
});

test("Next.js config exposes same-origin /api/:path* rewrite when BACKEND_ORIGIN is set", async () => {
  const savedBackendOrigin = process.env.BACKEND_ORIGIN;
  process.env.BACKEND_ORIGIN = "https://wifivision-api.example.com/";

  try {
    const mod = await import("../next.config");
    const cfg = mod.default;
    const rewrites = await cfg.rewrites?.();
    assert.deepEqual(rewrites, [
      {
        source: "/api/:path*",
        destination: "https://wifivision-api.example.com/api/:path*",
      },
    ]);
  } finally {
    if (savedBackendOrigin === undefined) delete process.env.BACKEND_ORIGIN;
    else process.env.BACKEND_ORIGIN = savedBackendOrigin;
  }
});
