export const calculateLatencyStatistics = (samples) => {

    if (!Array.isArray(samples) || samples.length === 0) {
        throw new Error(
            "at least one sample is required."
        );
    }

    if (
        samples.some(
            value =>
                typeof value !== "number" ||
                !Number.isFinite(value) ||
                value < 0
        )
    ) {
        throw new Error(
            "Latency values must be finite and non-negative."
        );
    }

    // Average latency
    const averageLatency =
        samples.reduce((sum, value) => sum + value, 0) /
        samples.length;

    // Median latency
    const sortedSamples = [...samples].sort(
        (a, b) => a - b
    );

    const middle = Math.floor(sortedSamples.length / 2);

    const medianLatency =
        sortedSamples.length % 2 === 0
            ? (
                sortedSamples[middle - 1] +
                sortedSamples[middle]
            ) / 2
            : sortedSamples[middle];

    // Average absolute difference between consecutive samples
    const differences = [];

    for (let i = 1; i < samples.length; i++) {
        differences.push(
            Math.abs(samples[i] - samples[i - 1])
        );
    }

    const jitter = differences.length
        ? differences.reduce((sum, value) => sum + value, 0) /
          differences.length
        : 0;

    return {
        averageLatencyMs: Number(averageLatency.toFixed(2)),
        medianLatencyMs: Number(medianLatency.toFixed(2)),
        jitterMs: Number(jitter.toFixed(2))
    };
};