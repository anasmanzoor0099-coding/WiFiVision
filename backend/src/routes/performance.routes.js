import express from "express";
import rateLimit from "express-rate-limit";

import {
    getInternetPerformance,
    getPerformanceHistory,
    getPerformanceAnalytics,
    getPerformanceTrendsController
} from "../controllers/performance.controller.js";

const router = express.Router();

// Restrict expensive performance measurements
const performanceTestLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    limit: 5,
    standardHeaders: "draft-8",
    legacyHeaders: false,
    message: {
        success: false,
        message: "Too many performance tests. Please try again after 15 minutes."
    }
});

// Run and save a new internet performance test
router.get(
    "/",
    performanceTestLimiter,
    getInternetPerformance
);

// Retrieve historical measurements
router.get("/history", getPerformanceHistory);

// Retrieve performance analytics
router.get("/analytics", getPerformanceAnalytics);

// Retrieve chronological performance trends
router.get("/trends", getPerformanceTrendsController);

export default router;