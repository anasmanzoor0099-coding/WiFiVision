import { performance } from "node:perf_hooks";

import {
    calculateLatencyStatistics
} from "../utils/latencyStatistics.js";

const TIMEOUT_MS = 20000;

const LATENCY_URL =
    "https://speed.cloudflare.com/__down?bytes=0";

const DOWNLOAD_URL =
    "https://speed.cloudflare.com/__down?bytes=1000000";

const UPLOAD_URL =
    "https://speed.cloudflare.com/__up";

// LATENCY MEASUREMENT
const measureLatency = async () => {
    const start = performance.now();

    try {
        const response = await fetch(LATENCY_URL, {
            method: "GET",
            cache: "no-store",
            signal: AbortSignal.timeout(TIMEOUT_MS)
        });

        if (!response.ok) {
            await response.body?.cancel();
            throw new Error(
                `Latency endpoint returned HTTP ${response.status}`
            );
        }

        await response.arrayBuffer();

        return Number(
            (performance.now() - start).toFixed(2)
        );

    } catch (error) {
        throw new Error(
            `Latency measurement failed: ${error.message}`
        );
    }
};

// LATENCY AND JITTER STATISTICS
const measureJitter = async (samples = 10) => {
    // Warm-up request (excluded from measurements)
    await measureLatency();

    const measurements = [];

    for (let i = 0; i < samples; i++) {
        measurements.push(await measureLatency());
    }

    const statistics =
        calculateLatencyStatistics(measurements);

    return {
        ...statistics,
        samples: measurements
    };
};

// DOWNLOAD SPEED MEASUREMENT
const measureDownloadSpeed = async () => {
    const start = performance.now();

    try {
        const response = await fetch(DOWNLOAD_URL, {
            cache: "no-store",
            signal: AbortSignal.timeout(TIMEOUT_MS)
        });

        if (!response.ok || !response.body) {
            throw new Error(
                `Download endpoint returned HTTP ${response.status}`
            );
        }

        const reader = response.body.getReader();
        let totalBytes = 0;

        try {
            while (true) {
                const { done, value } = await reader.read();

                if (done) break;

                totalBytes += value.byteLength;
            }
        } finally {
            reader.releaseLock();
        }

        const durationSeconds =
            (performance.now() - start) / 1000;

        if (durationSeconds <= 0 || totalBytes === 0) {
            throw new Error("No download data received.");
        }

        const downloadMbps =
            (totalBytes * 8) /
            (durationSeconds * 1_000_000);

        return {
            downloadMbps: Number(downloadMbps.toFixed(2)),
            bytesReceived: totalBytes,
            durationSeconds: Number(durationSeconds.toFixed(2))
        };

    } catch (error) {
        throw new Error(
            `Download measurement failed: ${error.message}`
        );
    }
};

// UPLOAD SPEED MEASUREMENT
const measureUploadSpeed = async (sizeMb = 1) => {
    const sizeBytes = sizeMb * 1024 * 1024;
    const payload = new Uint8Array(sizeBytes);

    // Generate non-compressible test data
    for (let offset = 0; offset < payload.length; offset += 65536) {
        const chunk = payload.subarray(
            offset,
            Math.min(offset + 65536, payload.length)
        );

        globalThis.crypto.getRandomValues(chunk);
    }

    const start = performance.now();

    try {
        const response = await fetch(UPLOAD_URL, {
            method: "POST",
            headers: {
                "Content-Type": "application/octet-stream"
            },
            body: payload,
            signal: AbortSignal.timeout(TIMEOUT_MS)
        });

        if (!response.ok) {
            throw new Error(
                `Upload endpoint returned HTTP ${response.status}`
            );
        }

        await response.arrayBuffer();

        const durationSeconds =
            (performance.now() - start) / 1000;

        if (durationSeconds <= 0) {
            throw new Error("Invalid upload duration.");
        }

        const uploadMbps =
            (sizeBytes * 8) /
            (durationSeconds * 1_000_000);

        return {
            uploadMbps: Number(uploadMbps.toFixed(2)),
            bytesSent: sizeBytes,
            durationSeconds: Number(durationSeconds.toFixed(2))
        };

    } catch (error) {
        throw new Error(
            `Upload measurement failed: ${error.message}`
        );
    }
};

// MAIN PERFORMANCE ENGINE
export const measureInternetPerformance = async () => {
    const latencyResult = await measureJitter(10);

    const download = await measureDownloadSpeed();

    const upload = await measureUploadSpeed();

    return {
        latencyMs: latencyResult.averageLatencyMs,
        medianLatencyMs: latencyResult.medianLatencyMs,
        jitterMs: latencyResult.jitterMs,
        jitterSamples: latencyResult.samples,
        downloadMbps: download.downloadMbps,
        uploadMbps: upload.uploadMbps,
        downloadBytes: download.bytesReceived,
        uploadBytes: upload.bytesSent,
        measuredAt: new Date().toISOString()
    };
};