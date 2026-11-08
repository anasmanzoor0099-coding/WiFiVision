import express from "express";
import rateLimit from "express-rate-limit";

import {
    createObservation,
    getObservations,
    getObservationStats,
    getHeatmapData,
    getNetworkDensity
} from "../controllers/observation.controller.js";

const router = express.Router();

// Limit observation submissions
const observationLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    limit: 60,
    standardHeaders: "draft-8",
    legacyHeaders: false,
    message: {
        success: false,
        message: "Too many observations submitted. Please try again later."
    }
});

// Save observations
router.post(
    "/",
    observationLimiter,
    createObservation
);

// Retrieve observations
router.get("/", getObservations);

// Analytics
router.get("/stats", getObservationStats);

// Geographic heatmap
router.get("/heatmap", getHeatmapData);

// Geographic network density
router.get("/density", getNetworkDensity);

export default router;