import express from "express";

import {
    createObservation,
    getObservations,
    getObservationStats,
    getHeatmapData,
    getNetworkDensity
} from "../controllers/observation.controller.js";
import { protect } from "../middleware/auth.middleware.js";
import { requireDeviceCredential } from "../middleware/deviceAuth.middleware.js";
import { createDistributedLimiter } from "../utils/rateLimiter.js";

const router = express.Router();

// IP-level guard before credential check
const observationIpLimiter = createDistributedLimiter({
    name: "observations-ip",
    windowMs: 15 * 60 * 1000,
    limit: 90,
    message: "Too many observation upload attempts from this IP. Please try again later."
});

// Per-device upload limit once credential is verified
const observationDeviceLimiter = createDistributedLimiter({
    name: "observations-device",
    windowMs: 15 * 60 * 1000,
    limit: 60,
    keyGenerator: (req) => String(req.device?.deviceId || req.ip || "unknown"),
    message: "Too many observations submitted for this device. Please try again later."
});

// Save observations (paired Android device credential required)
router.post(
    "/",
    observationIpLimiter,
    requireDeviceCredential("observations:write"),
    observationDeviceLimiter,
    createObservation
);

// Retrieve authenticated user's own observations
router.get("/", protect, getObservations);

// Authenticated user's analytics
router.get("/stats", protect, getObservationStats);

// Authenticated user's geographic heatmap
router.get("/heatmap", protect, getHeatmapData);

// Authenticated user's geographic network density
router.get("/density", protect, getNetworkDensity);

export default router;
