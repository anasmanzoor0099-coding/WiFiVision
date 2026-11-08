import mongoose from "mongoose";

const networkObservationSchema = new mongoose.Schema(
    {
        ssid: {
            type: String,
            trim: true,
            maxlength: 100,
            default: "Hidden Network"
        },

        bssid: {
            type: String,
            trim: true,
            maxlength: 17,
            default: null
        },

        signalDbm: {
            type: Number,
            required: true,
            min: -127,
            max: 0
        },

        frequencyMhz: {
            type: Number,
            required: true,
            min: 1
        },

        band: {
            type: String,
            enum: ["2.4 GHz", "5 GHz", "6 GHz", "Unknown"],
            default: "Unknown"
        },

        security: {
            type: String,
            enum: ["Open", "WEP", "WPA", "WPA2", "WPA3", "Unknown"],
            default: "Unknown"
        },

        signalQuality: {
            type: Number,
            min: 0,
            max: 4
        },

        location: {
            latitude: {
                type: Number,
                min: -90,
                max: 90
            },
            longitude: {
                type: Number,
                min: -180,
                max: 180
            },
            accuracyMeters: {
                type: Number,
                min: 0
            }
        },

        source: {
            type: String,
            enum: ["android", "web"],
            default: "android"
        },

        observedAt: {
            type: Date,
            default: Date.now
        }
    },
    {
        timestamps: true,
        versionKey: false
    }
);

networkObservationSchema.index({ observedAt: -1 });
networkObservationSchema.index({
    "location.latitude": 1,
    "location.longitude": 1
});

const NetworkObservation = mongoose.model(
    "NetworkObservation",
    networkObservationSchema
);

export default NetworkObservation;