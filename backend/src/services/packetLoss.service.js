import { performance } from "node:perf_hooks";

const TEST_URL = "https://speed.cloudflare.com/__down?bytes=0";
const DEFAULT_PACKETS = 20;
const TIMEOUT_MS = 10000;

const sendTestPacket = async () => {
    const start = performance.now();

    try {
        const response = await fetch(TEST_URL, {
            method: "GET",
            cache: "no-store",
            signal: AbortSignal.timeout(TIMEOUT_MS)
        });

        if (!response.ok) {
            await response.body?.cancel();

            return {
                success: false,
                durationMs: null,
                error: `HTTP ${response.status}`
            };
        }

        await response.body?.cancel();

        return {
            success: true,
            durationMs: Number(
                (performance.now() - start).toFixed(2)
            ),
            error: null
        };

    } catch (error) {
        return {
            success: false,
            durationMs: null,
            error: error.message
        };
    }
};

export const measurePacketLoss = async (
    packetCount = DEFAULT_PACKETS
) => {
    if (
        !Number.isInteger(packetCount) ||
        packetCount < 1 ||
        packetCount > 100
    ) {
        throw new Error(
            "Packet count must be an integer between 1 and 100."
        );
    }

    const results = [];

    for (let i = 0; i < packetCount; i++) {
        results.push(await sendTestPacket());
    }

    const successfulPackets = results.filter(
        packet => packet.success
    ).length;

    const lostPackets = packetCount - successfulPackets;

    const packetLossPercentage = Number(
        ((lostPackets / packetCount) * 100).toFixed(2)
    );

    const successfulTimes = results
        .filter(packet => packet.success)
        .map(packet => packet.durationMs);

    const averageResponseMs = successfulTimes.length
        ? Number(
            (
                successfulTimes.reduce(
                    (sum, value) => sum + value,
                    0
                ) / successfulTimes.length
            ).toFixed(2)
        )
        : null;

    return {
        totalPackets: packetCount,
        successfulPackets,
        lostPackets,
        packetLossPercentage,
        averageResponseMs,
        measurementType: "HTTP application-level request success",
        measuredAt: new Date().toISOString()
    };
};