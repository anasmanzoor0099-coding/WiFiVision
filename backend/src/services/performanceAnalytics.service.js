import PerformanceMeasurement from "../models/PerformanceMeasurement.js";

export const getPerformanceTrends = async (limit = 50, userId = null) => {
    const query = userId ? { userId } : { _id: null };
    const measurements = await PerformanceMeasurement.find(query)
        .select(
            "latencyMs medianLatencyMs jitterMs downloadMbps uploadMbps packetLoss source measuredAt"
        )
        .sort({ measuredAt: -1 })
        .limit(limit)
        .lean();

    // Convert newest-first results into chronological order
    const chronologicalMeasurements = measurements.reverse();

    return {
        count: chronologicalMeasurements.length,

        trends: chronologicalMeasurements.map((item) => ({
            latencyMs: item.latencyMs,

            medianLatencyMs: item.medianLatencyMs ?? null,

            jitterMs: item.jitterMs,

            downloadMbps: item.downloadMbps,

            uploadMbps: item.uploadMbps,

            packetLossPercentage:
                item.packetLoss.packetLossPercentage,

            source: item.source,

            measuredAt: item.measuredAt
        }))
    };
};