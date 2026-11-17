import express from "express";

import {
    getInternetPerformance,
    getPerformanceHistory,
    getPerformanceAnalytics,
    getPerformanceTrendsController
} from "../controllers/performance.controller.js";
import { protect } from "../middleware/auth.middleware.js";
import { createDistributedLimiter } from "../utils/rateLimiter.js";

const router = express.Router();

// Restrict expensive outbound speed/packet-loss probes (IP + authenticated user)
const performanceTestLimiter = createDistributedLimiter({
    name: "performance-test",
    windowMs: 15 * 60 * 1000,
    limit: 5,
    keyGenerator: (req) => String(req.user?._id || req.ip || "unknown"),
    message: "Too many performance tests. Please try again after 15 minutes."
});

// Disallow triggering state-changing network probes via GET
router.get("/", (req, res) => {
    res.setHeader("Allow", "POST");
    return res.status(405).json({
        success: false,
        message: "Method not allowed. Use authenticated POST /api/performance to run a test."
    });
});

// Run and save a new internet performance test (authenticated user only)
router.post(
    "/",
    protect,
    performanceTestLimiter,
    getInternetPerformance
);

// Retrieve authenticated user's historical measurements
router.get("/history", protect, getPerformanceHistory);

// Retrieve authenticated user's performance analytics
router.get("/analytics", protect, getPerformanceAnalytics);

// Retrieve authenticated user's chronological performance trends
router.get("/trends", protect, getPerformanceTrendsController);

export default router;
