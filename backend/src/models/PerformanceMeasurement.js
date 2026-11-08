import mongoose from "mongoose";

const performanceMeasurementSchema = new mongoose.Schema(
    {
        latencyMs: {
            type: Number,
            required: true,
            min: 0
        },

        medianLatencyMs: {
            type: Number,
            required: true,
            min: 0
        },

        jitterMs: {
            type: Number,
            required: true,
            min: 0
        },

        downloadMbps: {
            type: Number,
            required: true,
            min: 0
        },

        uploadMbps: {
            type: Number,
            required: true,
            min: 0
        },

        packetLoss: {
            totalPackets: {
                type: Number,
                required: true,
                min: 1
            },

            successfulPackets: {
                type: Number,
                required: true,
                min: 0
            },

            lostPackets: {
                type: Number,
                required: true,
                min: 0
            },

            packetLossPercentage: {
                type: Number,
                required: true,
                min: 0,
                max: 100
            },

            averageResponseMs: {
                type: Number,
                default: null,
                min: 0
            },

            measurementType: {
                type: String,
                default: "HTTP application-level request success"
            }
        },

        source: {
            type: String,
            enum: ["backend", "android", "web"],
            default: "backend"
        },

        testServer: {
            type: String,
            default: "Cloudflare"
        },

        measuredAt: {
            type: Date,
            default: Date.now
        }
    },
    {
        timestamps: true,
        versionKey: false
    }
);

// Historical performance indexes
performanceMeasurementSchema.index({
    measuredAt: -1
});

performanceMeasurementSchema.index({
    source: 1,
    measuredAt: -1
});

const PerformanceMeasurement = mongoose.model(
    "PerformanceMeasurement",
    performanceMeasurementSchema
);

export default PerformanceMeasurement;