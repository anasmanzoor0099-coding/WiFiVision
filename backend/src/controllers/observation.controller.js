import NetworkObservation from "../models/NetworkObservation.js";
import observationSchema from "../validators/observation.validator.js";
import { hashBssid } from "../utils/cryptoSecrets.js";
import {
    buildHeatmapPipeline,
    summarizeAndFormatHeatmapCells
} from "../services/heatmap.service.js";

// Known development/test observations.
// These are excluded from geographic visualizations,
// but are NOT deleted from MongoDB.
const TEST_OBSERVATION_SSIDS = [
    "WiFiVision_Test",
    "WiFiVision_Geo_Test"
];

// Shared filter for real user-owned geographic observations.
// Note: unassigned legacy records (userId: null / missing) are excluded automatically,
// and finite WGS84 coordinate bounds exclude null, NaN, and malformed coordinates.
const VALID_GEO_COORDINATE_FILTER = {
    "location.latitude": {
        $gte: -90,
        $lte: 90
    },
    "location.longitude": {
        $gte: -180,
        $lte: 180
    }
};

const getRealObservationMatch = (userId) => ({
    userId,
    ssid: {
        $nin: TEST_OBSERVATION_SSIDS
    },
    ...VALID_GEO_COORDINATE_FILTER
});

// CREATE OBSERVATION
export const createObservation = async (req, res, next) => {
    try {
        const ownerUserId = req.device?.userId || req.user?._id;
        const ownerDeviceId = req.device?.deviceId || null;

        if (!ownerUserId) {
            return res.status(401).json({
                success: false,
                message: "Authentication or paired device credential required."
            });
        }

        const validation = observationSchema.safeParse(req.body);

        if (!validation.success) {
            return res.status(400).json({
                success: false,
                message: "Invalid observation data.",
                errors: validation.error.issues.map((issue) => ({
                    field: issue.path.join("."),
                    message: issue.message
                }))
            });
        }

        const { bssid: rawBssid, ...safeObservationFields } = validation.data;
        const devicePlatform = req.device?.platform;

        if (
            devicePlatform &&
            safeObservationFields.source &&
            safeObservationFields.source !== devicePlatform
        ) {
            return res.status(403).json({
                success: false,
                message: `Observation source '${safeObservationFields.source}' is not permitted for a ${devicePlatform} device credential.`
            });
        }

        let bssidHash = null;
        if (rawBssid) {
            try {
                bssidHash = hashBssid(rawBssid);
            } catch {
                return res.status(503).json({
                    success: false,
                    message: "Server BSSID hashing configuration unavailable."
                });
            }
        }

        const resolvedSource =
            devicePlatform || safeObservationFields.source || "android";

        const observation = await NetworkObservation.create({
            ...safeObservationFields,
            source: resolvedSource,
            bssidHash,
            userId: ownerUserId,
            deviceId: ownerDeviceId
        });

        return res.status(201).json({
            success: true,
            message: "Network observation saved successfully.",
            observation: {
                id: observation._id,
                ssid: observation.ssid,
                signalDbm: observation.signalDbm,
                frequencyMhz: observation.frequencyMhz,
                band: observation.band,
                security: observation.security,
                signalQuality: observation.signalQuality,
                location: observation.location,
                source: observation.source,
                observedAt: observation.observedAt
            }
        });

    } catch (error) {
        next(error);
    }
};

const DEFAULT_OBSERVATIONS_PAGE = 1;
const DEFAULT_OBSERVATIONS_LIMIT = 100;
const MAX_OBSERVATIONS_LIMIT = 100;
const MAX_OBSERVATIONS_PAGE = 10000;
const VALID_STATS_BANDS = ["2.4 GHz", "5 GHz", "6 GHz", "Unknown"];

function parsePositiveIntParam(rawValue, fallback, minValue, maxValue) {
    if (rawValue === undefined) {
        return { ok: true, value: fallback };
    }
    if (typeof rawValue !== "string" || !/^\d+$/.test(rawValue.trim())) {
        return { ok: false, value: fallback };
    }
    const parsed = Number.parseInt(rawValue.trim(), 10);
    if (!Number.isSafeInteger(parsed) || parsed < minValue || parsed > maxValue) {
        return { ok: false, value: fallback };
    }
    return { ok: true, value: parsed };
}

// GET OBSERVATIONS (Paginated, backward-compatible defaults page=1, limit=100)
export const getObservations = async (req, res, next) => {
    try {
        if (!req.user?._id) {
            return res.status(401).json({
                success: false,
                message: "Authentication required."
            });
        }

        const pageParam = parsePositiveIntParam(
            req.query?.page,
            DEFAULT_OBSERVATIONS_PAGE,
            1,
            MAX_OBSERVATIONS_PAGE
        );
        const limitParam = parsePositiveIntParam(
            req.query?.limit,
            DEFAULT_OBSERVATIONS_LIMIT,
            1,
            MAX_OBSERVATIONS_LIMIT
        );

        if (!pageParam.ok || !limitParam.ok) {
            return res.status(400).json({
                success: false,
                message: "Invalid pagination parameters. 'page' (1-10000) and 'limit' (1-100) must be positive integers."
            });
        }

        const page = pageParam.value;
        const limit = limitParam.value;
        const skip = (page - 1) * limit;
        const ownerQuery = { userId: req.user._id };

        const [observations, totalCount] = await Promise.all([
            NetworkObservation.find(ownerQuery)
                .select("-bssid -bssidHash")
                .sort({ observedAt: -1 })
                .skip(skip)
                .limit(limit)
                .lean(),
            NetworkObservation.countDocuments(ownerQuery)
        ]);

        const hasMore = skip + observations.length < totalCount;

        return res.status(200).json({
            success: true,
            count: observations.length,
            totalCount,
            page,
            limit,
            hasMore,
            observations
        });

    } catch (error) {
        next(error);
    }
};

// GET OBSERVATION ANALYTICS
export const getObservationStats = async (req, res, next) => {
    try {
        if (!req.user?._id) {
            return res.status(401).json({
                success: false,
                message: "Authentication required."
            });
        }

        const { band } = req.query || {};
        if (
            band !== undefined &&
            (typeof band !== "string" || !VALID_STATS_BANDS.includes(band))
        ) {
            return res.status(400).json({
                success: false,
                message: "Invalid frequency band."
            });
        }

        const ownerMatch = { userId: req.user._id };
        if (band) {
            ownerMatch.band = band;
        }

        const [stats] = await NetworkObservation.aggregate([
            { $match: ownerMatch },
            {
                $group: {
                    _id: null,
                    totalNetworks: {
                        $sum: 1
                    },
                    averageSignal: {
                        $avg: "$signalDbm"
                    },
                    strongestSignal: {
                        $max: "$signalDbm"
                    },
                    weakestSignal: {
                        $min: "$signalDbm"
                    }
                }
            }
        ]);

        const bands = await NetworkObservation.aggregate([
            { $match: ownerMatch },
            {
                $group: {
                    _id: "$band",
                    count: {
                        $sum: 1
                    }
                }
            }
        ]);

        const security = await NetworkObservation.aggregate([
            { $match: ownerMatch },
            {
                $group: {
                    _id: "$security",
                    count: {
                        $sum: 1
                    }
                }
            }
        ]);

        return res.status(200).json({
            success: true,
            analytics: {
                filteredBand: band || null,
                totalNetworks: stats?.totalNetworks || 0,
                averageSignal: stats
                    ? Math.round(stats.averageSignal)
                    : null,
                strongestSignal: stats?.strongestSignal ?? null,
                weakestSignal: stats?.weakestSignal ?? null,
                bands,
                security
            }
        });

    } catch (error) {
        next(error);
    }
};

// ADVANCED GEOGRAPHIC HEATMAP
export const getHeatmapData = async (req, res, next) => {
    try {
        if (!req.user?._id) {
            return res.status(401).json({
                success: false,
                message: "Authentication required."
            });
        }

        const { band } = req.query;

        const validBands = [
            "2.4 GHz",
            "5 GHz",
            "6 GHz"
        ];

        if (
            band !== undefined &&
            (typeof band !== "string" || !validBands.includes(band))
        ) {
            return res.status(400).json({
                success: false,
                message: "Invalid frequency band."
            });
        }

        const baseScopeMatch = {
            userId: req.user._id,
            ssid: {
                $nin: TEST_OBSERVATION_SSIDS
            }
        };
        const matchStage = getRealObservationMatch(req.user._id);

        if (band) {
            baseScopeMatch.band = band;
            matchStage.band = band;
        }

        const isDbConnected = NetworkObservation.db?.readyState === 1;

        const [rawHeatmap, totalInScope, geolocatedInScope] = await Promise.all([
            NetworkObservation.aggregate(buildHeatmapPipeline(matchStage)),
            isDbConnected
                ? NetworkObservation.countDocuments(baseScopeMatch)
                : Promise.resolve(null),
            isDbConnected
                ? NetworkObservation.countDocuments(matchStage)
                : Promise.resolve(null)
        ]);

        const { heatmap, accuracySummary } = summarizeAndFormatHeatmapCells(rawHeatmap);
        const mappedObsCount = heatmap.reduce(
            (sum, cell) => sum + (cell.totalObservations || 0),
            0
        );
        const geolocatedObservations =
            typeof geolocatedInScope === "number" ? geolocatedInScope : mappedObsCount;
        const totalObservations =
            typeof totalInScope === "number" ? totalInScope : geolocatedObservations;
        const nonGeolocatedObservations = Math.max(
            0,
            totalObservations - geolocatedObservations
        );

        return res.status(200).json({
            success: true,
            count: heatmap.length,
            heatmap,
            accuracySummary,
            locationSummary: {
                geolocatedObservations,
                nonGeolocatedObservations,
                totalObservations
            }
        });

    } catch (error) {
        next(error);
    }
};

// NETWORK DENSITY INTELLIGENCE
export const getNetworkDensity = async (req, res, next) => {
    try {
        if (!req.user?._id) {
            return res.status(401).json({
                success: false,
                message: "Authentication required."
            });
        }

        const densityData = await NetworkObservation.aggregate([
            {
                $match: getRealObservationMatch(req.user._id)
            },
            {
                $group: {
                    _id: {
                        latitude: {
                            $round: [
                                "$location.latitude",
                                3
                            ]
                        },
                        longitude: {
                            $round: [
                                "$location.longitude",
                                3
                            ]
                        }
                    },

                    totalObservations: {
                        $sum: 1
                    },

                    uniqueSsids: {
                        $addToSet: "$ssid"
                    },

                    averageSignal: {
                        $avg: "$signalDbm"
                    },

                    band24: {
                        $sum: {
                            $cond: [
                                {
                                    $eq: [
                                        "$band",
                                        "2.4 GHz"
                                    ]
                                },
                                1,
                                0
                            ]
                        }
                    },

                    band5: {
                        $sum: {
                            $cond: [
                                {
                                    $eq: [
                                        "$band",
                                        "5 GHz"
                                    ]
                                },
                                1,
                                0
                            ]
                        }
                    },

                    band6: {
                        $sum: {
                            $cond: [
                                {
                                    $eq: [
                                        "$band",
                                        "6 GHz"
                                    ]
                                },
                                1,
                                0
                            ]
                        }
                    }
                }
            },
            {
                $project: {
                    _id: 0,

                    latitude: "$_id.latitude",
                    longitude: "$_id.longitude",

                    totalObservations: 1,

                    uniqueNetworkCount: {
                        $size: "$uniqueSsids"
                    },

                    averageSignal: {
                        $round: [
                            "$averageSignal",
                            1
                        ]
                    },

                    frequencyDistribution: [
                        {
                            band: {
                                $literal: "2.4 GHz"
                            },
                            count: "$band24"
                        },
                        {
                            band: {
                                $literal: "5 GHz"
                            },
                            count: "$band5"
                        },
                        {
                            band: {
                                $literal: "6 GHz"
                            },
                            count: "$band6"
                        }
                    ]
                }
            },
            {
                $addFields: {
                    densityLevel: {
                        $switch: {
                            branches: [
                                {
                                    case: {
                                        $lte: [
                                            "$uniqueNetworkCount",
                                            2
                                        ]
                                    },
                                    then: "Low"
                                },
                                {
                                    case: {
                                        $lte: [
                                            "$uniqueNetworkCount",
                                            5
                                        ]
                                    },
                                    then: "Medium"
                                }
                            ],
                            default: "High"
                        }
                    }
                }
            },
            {
                $sort: {
                    uniqueNetworkCount: -1
                }
            },
            {
                $limit: 5000
            }
        ]);

        return res.status(200).json({
            success: true,
            count: densityData.length,
            density: densityData
        });

    } catch (error) {
        next(error);
    }
};