import {
    measureInternetPerformance
} from "../services/internetPerformance.service.js";

import {
    measurePacketLoss
} from "../services/packetLoss.service.js";

import {
    getPerformanceTrends
} from "../services/performanceAnalytics.service.js";

import PerformanceMeasurement from "../models/PerformanceMeasurement.js";

import performanceSchema from "../validators/performance.validator.js";

// RUN AND SAVE A PERFORMANCE TEST
export const getInternetPerformance = async (req, res, next) => {
    try {
        const performanceData = await measureInternetPerformance();

        const packetLossData = await measurePacketLoss(20);

        // Prepare measurement data
        const measurementData = {
            latencyMs: performanceData.latencyMs,

            medianLatencyMs: performanceData.medianLatencyMs,

            jitterMs: performanceData.jitterMs,

            downloadMbps: performanceData.downloadMbps,

            uploadMbps: performanceData.uploadMbps,

            packetLoss: {
                totalPackets: packetLossData.totalPackets,
                successfulPackets: packetLossData.successfulPackets,
                lostPackets: packetLossData.lostPackets,
                packetLossPercentage:
                    packetLossData.packetLossPercentage,
                averageResponseMs:
                    packetLossData.averageResponseMs,
                measurementType:
                    packetLossData.measurementType
            },

            source: "backend",
            testServer: "Cloudflare",
            measuredAt: performanceData.measuredAt
        };

        // Validate before saving to MongoDB
        const validatedMeasurement =
            performanceSchema.parse(measurementData);

        const savedMeasurement =
            await PerformanceMeasurement.create({
                ...validatedMeasurement,
                userId: req.user._id
            });

        return res.status(201).json({
            success: true,

            message: "Performance measured and saved successfully.",

            savedMeasurementId: savedMeasurement._id,

            performance: {
                ...performanceData,
                packetLoss: packetLossData
            }
        });

    } catch (error) {
        next(error);
    }
};

// GET PERFORMANCE HISTORY
export const getPerformanceHistory = async (req, res, next) => {
    try {
        const requestedLimit = Number(req.query.limit) || 50;

        if (
            !Number.isInteger(requestedLimit) ||
            requestedLimit < 1 ||
            requestedLimit > 100
        ) {
            return res.status(400).json({
                success: false,
                message: "Limit must be between 1 and 100."
            });
        }

        const measurements = await PerformanceMeasurement.find({
            userId: req.user._id
        })
            .sort({ measuredAt: -1 })
            .limit(requestedLimit)
            .lean();

        return res.status(200).json({
            success: true,
            count: measurements.length,
            history: measurements
        });

    } catch (error) {
        next(error);
    }
};

// GET PERFORMANCE ANALYTICS
export const getPerformanceAnalytics = async (req, res, next) => {
    try {
        const [analytics] = await PerformanceMeasurement.aggregate([
            {
                $match: { userId: req.user._id }
            },
            {
                $group: {
                    _id: null,

                    totalTests: { $sum: 1 },

                    averageDownloadMbps: {
                        $avg: "$downloadMbps"
                    },

                    averageUploadMbps: {
                        $avg: "$uploadMbps"
                    },

                    averageLatencyMs: {
                        $avg: "$latencyMs"
                    },

                    averageMedianLatencyMs: {
                        $avg: "$medianLatencyMs"
                    },

                    averageJitterMs: {
                        $avg: "$jitterMs"
                    },

                    averagePacketLossPercentage: {
                        $avg: "$packetLoss.packetLossPercentage"
                    },

                    fastestDownloadMbps: {
                        $max: "$downloadMbps"
                    },

                    slowestDownloadMbps: {
                        $min: "$downloadMbps"
                    },

                    fastestUploadMbps: {
                        $max: "$uploadMbps"
                    },

                    latestMeasurement: {
                        $max: "$measuredAt"
                    }
                }
            }
        ]);

        const round = (value) =>
            value === null || value === undefined
                ? null
                : Number(value.toFixed(2));

        return res.status(200).json({
            success: true,

            analytics: analytics
                ? {
                    totalTests: analytics.totalTests,

                    averageDownloadMbps:
                        round(analytics.averageDownloadMbps),

                    averageUploadMbps:
                        round(analytics.averageUploadMbps),

                    averageLatencyMs:
                        round(analytics.averageLatencyMs),

                    averageMedianLatencyMs:
                        round(analytics.averageMedianLatencyMs),

                    averageJitterMs:
                        round(analytics.averageJitterMs),

                    averagePacketLossPercentage:
                        round(analytics.averagePacketLossPercentage),

                    fastestDownloadMbps:
                        round(analytics.fastestDownloadMbps),

                    slowestDownloadMbps:
                        round(analytics.slowestDownloadMbps),

                    fastestUploadMbps:
                        round(analytics.fastestUploadMbps),

                    latestMeasurement:
                        analytics.latestMeasurement
                }
                : {
                    totalTests: 0,
                    message: "No performance measurements available."
                }
        });

    } catch (error) {
        next(error);
    }
};

// GET CHRONOLOGICAL PERFORMANCE TRENDS
export const getPerformanceTrendsController = async (req, res, next) => {
    try {
        const requestedLimit = Number(req.query.limit) || 50;

        if (
            !Number.isInteger(requestedLimit) ||
            requestedLimit < 1 ||
            requestedLimit > 100
        ) {
            return res.status(400).json({
                success: false,
                message: "Limit must be between 1 and 100."
            });
        }

        const result = await getPerformanceTrends(requestedLimit, req.user._id);

        return res.status(200).json({
            success: true,
            ...result
        });

    } catch (error) {
        next(error);
    }
};