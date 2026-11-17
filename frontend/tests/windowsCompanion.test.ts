import test from "node:test";
import assert from "node:assert/strict";

import {
  buildCompanionBaseUrl,
  checkCompanionHealth,
  confirmCompanionUpload,
  fetchCompanionStatus,
  fetchCompanionUploadPreview,
  formatCompanionOfflineMessage,
  LOCAL_COMPANION_TOKEN_HEADER,
  triggerCompanionScan,
} from "../src/lib/windowsCompanion";

test("generateDevicePairingCode includes target platform payload when generating Windows code", async () => {
  const savedPublicApi = process.env.NEXT_PUBLIC_API_URL;
  process.env.NEXT_PUBLIC_API_URL = "/api";
  const originalFetch = globalThis.fetch;
  const { generateDevicePairingCode } = await import("../src/lib/dashboard");

  let capturedBody = "";
  globalThis.fetch = async (input: unknown, init: RequestInit = {}) => {
    capturedBody = String(init.body || "");
    assert.equal(String(input), "/api/devices/pairing-code");
    return new Response(
      JSON.stringify({
        success: true,
        pairingCode: "WIN92345",
        platform: "windows",
        expiresAt: new Date(Date.now() + 300_000).toISOString(),
        expiresInSeconds: 300,
      }),
      { status: 201 }
    );
  };

  try {
    const res = await generateDevicePairingCode("windows");
    assert.equal(res.pairingCode, "WIN92345");
    assert.equal(res.platform, "windows");
    assert.deepEqual(JSON.parse(capturedBody), { platform: "windows" });
  } finally {
    globalThis.fetch = originalFetch;
    if (savedPublicApi === undefined) delete process.env.NEXT_PUBLIC_API_URL;
    else process.env.NEXT_PUBLIC_API_URL = savedPublicApi;
  }
});

test("Windows companion local API client sends token header, handles scan/preview/confirm, and surfaces offline guidance", async () => {
  assert.equal(buildCompanionBaseUrl(48271), "http://127.0.0.1:48271");
  assert.equal(buildCompanionBaseUrl(80), "http://127.0.0.1:48271");

  const originalFetch = globalThis.fetch;
  const requests: Array<{ url: string; method: string; token?: string; body?: string }> = [];

  globalThis.fetch = async (input: unknown, init: RequestInit = {}) => {
    const url = String(input);
    const headers = (init.headers || {}) as Record<string, string>;
    requests.push({
      url,
      method: (init.method || "GET").toUpperCase(),
      token: headers[LOCAL_COMPANION_TOKEN_HEADER],
      body: typeof init.body === "string" ? init.body : undefined,
    });

    assert.equal(init.credentials, "omit");

    if (url.endsWith("/health")) {
      return new Response(
        JSON.stringify({
          ok: true,
          service: "wifivision-windows-companion",
          version: "1.0.0",
          platform: "windows",
          paired: true,
          monitorActive: false,
          queuedCount: 0,
          uploadedCount: 0,
        }),
        { status: 200 }
      );
    }

    if (url.endsWith("/status")) {
      return new Response(
        JSON.stringify({
          paired: true,
          deviceName: "WinLaptop",
          platform: "windows",
          monitorActive: false,
          uploadInProgress: false,
          queuedCount: 0,
          uploadedCount: 0,
          statusBanner: "Ready",
        }),
        { status: 200 }
      );
    }

    if (url.endsWith("/scan")) {
      return new Response(
        JSON.stringify({
          outcome: {
            success: true,
            errorKind: "None",
            statusMessage: "OK",
            locationMessage: "Unavailable",
            scannedAtEpochMs: Date.now(),
            location: null,
            networks: [],
          },
          status: {
            paired: true,
            platform: "windows",
            monitorActive: false,
            uploadInProgress: false,
            queuedCount: 0,
            uploadedCount: 0,
            statusBanner: "Scanned locally",
          },
        }),
        { status: 200 }
      );
    }

    if (url.endsWith("/queue/preview")) {
      return new Response(
        JSON.stringify({
          totalReadyCount: 2,
          existingPendingQueueCount: 0,
          readyLocalCount: 2,
          invalidSkippedCount: 0,
          hasLocation: false,
          previewLines: ["OfficeNet (-58 dBm, 5 GHz, WPA2, No location)"],
        }),
        { status: 200 }
      );
    }

    if (url.endsWith("/upload/confirm")) {
      return new Response(
        JSON.stringify({
          confirmed: true,
          cancelledOrMismatch: false,
          attemptedCount: 2,
          uploadedCount: 2,
          remainingQueuedCount: 0,
          failedOrDeferredCount: 0,
          requiresReparing: false,
          message: "Uploaded 2 Windows observation(s).",
          status: {
            paired: true,
            platform: "windows",
            monitorActive: false,
            uploadInProgress: false,
            queuedCount: 0,
            uploadedCount: 2,
            statusBanner: "Uploaded 2",
          },
        }),
        { status: 200 }
      );
    }

    return new Response("{}", { status: 404 });
  };

  try {
    const h = await checkCompanionHealth();
    assert.equal(h.platform, "windows");

    await fetchCompanionStatus("secret-local-token");
    await triggerCompanionScan("secret-local-token");
    const prev = await fetchCompanionUploadPreview("secret-local-token");
    assert.equal(prev.totalReadyCount, 2);

    const conf = await confirmCompanionUpload("secret-local-token", 2);
    assert.equal(conf.uploadedCount, 2);

    assert.equal(requests[1].token, "secret-local-token");
    assert.deepEqual(JSON.parse(requests[4].body || "{}"), {
      confirm: true,
      expectedCount: 2,
    });

    // Offline network rejection surfaces explicit actionable setup instructions
    globalThis.fetch = async () => {
      throw new TypeError("Failed to fetch");
    };
    await assert.rejects(
      () => checkCompanionHealth(),
      new RegExp(formatCompanionOfflineMessage().slice(0, 32))
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
