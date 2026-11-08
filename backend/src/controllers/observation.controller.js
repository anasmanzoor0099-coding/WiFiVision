import NetworkObservation from "../models/NetworkObservation.js";
import observationSchema from "../validators/observation.validator.js";

// CREATE OBSERVATION
export const createObservation = async (req, res, next) => {
    try {
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

        const observation = await NetworkObservation.create(
            validation.data
        );

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

// GET OBSERVATIONS
export const getObservations = async (req, res, next) => {
    try {
        const observations = await NetworkObservation.find()
            .select("-bssid")
            .sort({ observedAt: -1 })
            .limit(100)
            .lean();

        return res.status(200).json({
            success: true,
            count: observations.length,
            observations
        });

    } catch (error) {
        next(error);
    }
};// GET OBSERVATION ANALYTICS
export const getObservationStats = async (req, res, next) => {
    try {
        const [stats] = await NetworkObservation.aggregate([
            {
                $group: {
                    _id: null,
                    totalNetworks: { $sum: 1 },
                    averageSignal: { $avg: "$signalDbm" },
                    strongestSignal: { $max: "$signalDbm" },
                    weakestSignal: { $min: "$signalDbm" }
                }
            }
        ]);

        const bands = await NetworkObservation.aggregate([
            {
                $group: {
                    _id: "$band",
                    count: { $sum: 1 }
                }
            }
        ]);

        const security = await NetworkObservation.aggregate([
            {
                $group: {
                    _id: "$security",
                    count: { $sum: 1 }
                }
            }
        ]);

        return res.status(200).json({
            success: true,
            analytics: {
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
};// ADVANCED GEOGRAPHIC HEATMAP
export const getHeatmapData = async (req, res, next) => {
    try {
        const { band } = req.query;

        const validBands = ["2.4 GHz", "5 GHz", "6 GHz"];

        if (band && !validBands.includes(band)) {
            return res.status(400).json({
                success: false,
                message: "Invalid frequency band."
            });
        }

        const matchStage = {
            "location.latitude": { $exists: true, $ne: null },
            "location.longitude": { $exists: true, $ne: null }
        };

        if (band) {
            matchStage.band = band;
        }

        const heatmap = await NetworkObservation.aggregate([
            {
                $match: matchStage
            },
            {
                $group: {
                    _id: {
                        latitude: {
                            $round: ["$location.latitude", 3]
                        },
                        longitude: {
                            $round: ["$location.longitude", 3]
                        }
                    },
                    totalObservations: { $sum: 1 },
                    averageSignal: { $avg: "$signalDbm" },
                    strongestSignal: { $max: "$signalDbm" },
                    weakestSignal: { $min: "$signalDbm" },

                    band24: {
                        $sum: {
                            $cond: [
                                { $eq: ["$band", "2.4 GHz"] },
                                1,
                                0
                            ]
                        }
                    },

                    band5: {
                        $sum: {
                            $cond: [
                                { $eq: ["$band", "5 GHz"] },
                                1,
                                0
                            ]
                        }
                    },

                    band6: {
                        $sum: {
                            $cond: [
                                { $eq: ["$band", "6 GHz"] },
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

                    averageSignal: {
                        $round: ["$averageSignal", 1]
                    },

                    strongestSignal: 1,
                    weakestSignal: 1,

                    frequencyDistribution: [
                        {
                            band: { $literal: "2.4 GHz" },
                            count: "$band24"
                        },
                        {
                            band: { $literal: "5 GHz" },
                            count: "$band5"
                        },
                        {
                            band: { $literal: "6 GHz" },
                            count: "$band6"
                        }
                    ]
                }
            },
            {
                $sort: {
                    totalObservations: -1
                }
            },
            {
                $limit: 5000
            }
        ]);

        return res.status(200).json({
            success: true,
            count: heatmap.length,
            heatmap
        });

    } catch (error) {
        next(error);
    }
};// NETWORK DENSITY INTELLIGENCE
export const getNetworkDensity = async (req, res, next) => {
    try {
        const densityData = await NetworkObservation.aggregate([
            {
                $match: {
                    "location.latitude": {
                        $exists: true,
                        $ne: null
                    },
                    "location.longitude": {
                        $exists: true,
                        $ne: null
                    }
                }
            },
            {
                $group: {
                    _id: {
                        latitude: {
                            $round: ["$location.latitude", 3]
                        },
                        longitude: {
                            $round: ["$location.longitude", 3]
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
                                { $eq: ["$band", "2.4 GHz"] },
                                1,
                                0
                            ]
                        }
                    },

                    band5: {
                        $sum: {
                            $cond: [
                                { $eq: ["$band", "5 GHz"] },
                                1,
                                0
                            ]
                        }
                    },

                    band6: {
                        $sum: {
                            $cond: [
                                { $eq: ["$band", "6 GHz"] },
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
                        $round: ["$averageSignal", 1]
                    },

                    frequencyDistribution: [
                        {
                            band: { $literal: "2.4 GHz" },
                            count: "$band24"
                        },
                        {
                            band: { $literal: "5 GHz" },
                            count: "$band5"
                        },
                        {
                            band: { $literal: "6 GHz" },
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
                                        $lte: ["$uniqueNetworkCount", 2]
                                    },
                                    then: "Low"
                                },
                                {
                                    case: {
                                        $lte: ["$uniqueNetworkCount", 5]
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