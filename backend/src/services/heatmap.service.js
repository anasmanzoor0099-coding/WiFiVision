export const COARSE_GPS_ACCURACY_METERS = 50;

const hasValidAccuracyExpression = {
    $and: [
        { $isNumber: "$location.accuracyMeters" },
        { $gte: ["$location.accuracyMeters", 0] }
    ]
};

function roundOneDecimal(value) {
    return Math.round(value * 10) / 10;
}

/**
 * Builds the MongoDB aggregation pipeline for geographic Wi-Fi heatmap cells
 * and additive GPS accuracy metrics within each 0.001-degree cell.
 */
export function buildHeatmapPipeline(matchStage) {
    return [
        {
            $match: matchStage
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

                averageSignal: {
                    $avg: "$signalDbm"
                },

                strongestSignal: {
                    $max: "$signalDbm"
                },

                weakestSignal: {
                    $min: "$signalDbm"
                },

                avgAccuracyMeters: {
                    $avg: {
                        $cond: [
                            hasValidAccuracyExpression,
                            "$location.accuracyMeters",
                            "$$REMOVE"
                        ]
                    }
                },

                accuracySampleCount: {
                    $sum: {
                        $cond: [
                            hasValidAccuracyExpression,
                            1,
                            0
                        ]
                    }
                },

                missingAccuracyCount: {
                    $sum: {
                        $cond: [
                            hasValidAccuracyExpression,
                            0,
                            1
                        ]
                    }
                },

                coarseAccuracyCount: {
                    $sum: {
                        $cond: [
                            {
                                $and: [
                                    hasValidAccuracyExpression,
                                    {
                                        $gt: [
                                            "$location.accuracyMeters",
                                            COARSE_GPS_ACCURACY_METERS
                                        ]
                                    }
                                ]
                            },
                            1,
                            0
                        ]
                    }
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

                avgBand24: {
                    $avg: {
                        $cond: [
                            {
                                $eq: [
                                    "$band",
                                    "2.4 GHz"
                                ]
                            },
                            "$signalDbm",
                            "$$REMOVE"
                        ]
                    }
                },

                maxBand24: {
                    $max: {
                        $cond: [
                            { $eq: ["$band", "2.4 GHz"] },
                            "$signalDbm",
                            "$$REMOVE"
                        ]
                    }
                },

                minBand24: {
                    $min: {
                        $cond: [
                            { $eq: ["$band", "2.4 GHz"] },
                            "$signalDbm",
                            "$$REMOVE"
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

                avgBand5: {
                    $avg: {
                        $cond: [
                            {
                                $eq: [
                                    "$band",
                                    "5 GHz"
                                ]
                            },
                            "$signalDbm",
                            "$$REMOVE"
                        ]
                    }
                },

                maxBand5: {
                    $max: {
                        $cond: [
                            { $eq: ["$band", "5 GHz"] },
                            "$signalDbm",
                            "$$REMOVE"
                        ]
                    }
                },

                minBand5: {
                    $min: {
                        $cond: [
                            { $eq: ["$band", "5 GHz"] },
                            "$signalDbm",
                            "$$REMOVE"
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
                },

                avgBand6: {
                    $avg: {
                        $cond: [
                            {
                                $eq: [
                                    "$band",
                                    "6 GHz"
                                ]
                            },
                            "$signalDbm",
                            "$$REMOVE"
                        ]
                    }
                },

                maxBand6: {
                    $max: {
                        $cond: [
                            { $eq: ["$band", "6 GHz"] },
                            "$signalDbm",
                            "$$REMOVE"
                        ]
                    }
                },

                minBand6: {
                    $min: {
                        $cond: [
                            { $eq: ["$band", "6 GHz"] },
                            "$signalDbm",
                            "$$REMOVE"
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
                    $round: [
                        "$averageSignal",
                        1
                    ]
                },

                strongestSignal: 1,
                weakestSignal: 1,

                avgAccuracyMeters: {
                    $cond: [
                        { $gt: ["$accuracySampleCount", 0] },
                        "$avgAccuracyMeters",
                        null
                    ]
                },

                accuracySampleCount: 1,
                missingAccuracyCount: 1,
                coarseAccuracyCount: 1,

                frequencyDistribution: [
                    {
                        band: {
                            $literal: "2.4 GHz"
                        },
                        count: "$band24",
                        averageSignal: {
                            $cond: [
                                { $gt: ["$band24", 0] },
                                { $round: ["$avgBand24", 1] },
                                null
                            ]
                        },
                        strongestSignal: {
                            $cond: [
                                { $gt: ["$band24", 0] },
                                "$maxBand24",
                                null
                            ]
                        },
                        weakestSignal: {
                            $cond: [
                                { $gt: ["$band24", 0] },
                                "$minBand24",
                                null
                            ]
                        }
                    },
                    {
                        band: {
                            $literal: "5 GHz"
                        },
                        count: "$band5",
                        averageSignal: {
                            $cond: [
                                { $gt: ["$band5", 0] },
                                { $round: ["$avgBand5", 1] },
                                null
                            ]
                        },
                        strongestSignal: {
                            $cond: [
                                { $gt: ["$band5", 0] },
                                "$maxBand5",
                                null
                            ]
                        },
                        weakestSignal: {
                            $cond: [
                                { $gt: ["$band5", 0] },
                                "$minBand5",
                                null
                            ]
                        }
                    },
                    {
                        band: {
                            $literal: "6 GHz"
                        },
                        count: "$band6",
                        averageSignal: {
                            $cond: [
                                { $gt: ["$band6", 0] },
                                { $round: ["$avgBand6", 1] },
                                null
                            ]
                        },
                        strongestSignal: {
                            $cond: [
                                { $gt: ["$band6", 0] },
                                "$maxBand6",
                                null
                            ]
                        },
                        weakestSignal: {
                            $cond: [
                                { $gt: ["$band6", 0] },
                                "$minBand6",
                                null
                            ]
                        }
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
    ];
}

/**
 * Formats per-cell average GPS accuracy without intermediate rounding drift
 * and computes the overall mapped accuracy summary in O(cells).
 */
export function summarizeAndFormatHeatmapCells(rawCells = []) {
    if (!Array.isArray(rawCells) || rawCells.length === 0) {
        return {
            heatmap: [],
            accuracySummary: {
                averageAccuracyMeters: null,
                withAccuracyCount: 0,
                withoutAccuracyCount: 0,
                coarseAccuracyCount: 0
            }
        };
    }

    let weightedAccuracySum = 0;
    let withAccuracyCount = 0;
    let withoutAccuracyCount = 0;
    let coarseAccuracyCount = 0;

    const heatmap = rawCells.map((cell) => {
        const totalObservations =
            typeof cell.totalObservations === "number" && Number.isFinite(cell.totalObservations)
                ? Math.max(0, Math.trunc(cell.totalObservations))
                : 0;

        const hasValidAvg =
            typeof cell.avgAccuracyMeters === "number" &&
            Number.isFinite(cell.avgAccuracyMeters) &&
            cell.avgAccuracyMeters >= 0;

        const cellWithAccuracy =
            typeof cell.accuracySampleCount === "number" && Number.isFinite(cell.accuracySampleCount)
                ? Math.max(0, Math.trunc(cell.accuracySampleCount))
                : hasValidAvg
                    ? totalObservations
                    : 0;

        const cellWithoutAccuracy =
            typeof cell.missingAccuracyCount === "number" && Number.isFinite(cell.missingAccuracyCount)
                ? Math.max(0, Math.trunc(cell.missingAccuracyCount))
                : Math.max(0, totalObservations - cellWithAccuracy);

        const cellCoarseCount =
            typeof cell.coarseAccuracyCount === "number" && Number.isFinite(cell.coarseAccuracyCount)
                ? Math.max(0, Math.trunc(cell.coarseAccuracyCount))
                : 0;

        const validCell = hasValidAvg && cellWithAccuracy > 0;
        const normalizedWithCount = validCell ? cellWithAccuracy : 0;
        const normalizedWithoutCount = validCell ? cellWithoutAccuracy : totalObservations;
        const normalizedCoarseCount = validCell ? cellCoarseCount : 0;
        const roundedAvgAccuracy = validCell ? roundOneDecimal(cell.avgAccuracyMeters) : null;

        if (validCell) {
            weightedAccuracySum += cell.avgAccuracyMeters * normalizedWithCount;
            withAccuracyCount += normalizedWithCount;
        }
        withoutAccuracyCount += normalizedWithoutCount;
        coarseAccuracyCount += normalizedCoarseCount;

        return {
            ...cell,
            avgAccuracyMeters: roundedAvgAccuracy,
            accuracySampleCount: normalizedWithCount,
            missingAccuracyCount: normalizedWithoutCount,
            coarseAccuracyCount: normalizedCoarseCount
        };
    });

    return {
        heatmap,
        accuracySummary: {
            averageAccuracyMeters:
                withAccuracyCount > 0
                    ? roundOneDecimal(weightedAccuracySum / withAccuracyCount)
                    : null,
            withAccuracyCount,
            withoutAccuracyCount,
            coarseAccuracyCount
        }
    };
}
