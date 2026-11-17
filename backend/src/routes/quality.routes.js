import express from "express";

import {
    getNetworkQuality
} from "../controllers/quality.controller.js";
import { protect } from "../middleware/auth.middleware.js";

const router = express.Router();

// Get authenticated user's network quality intelligence
router.get("/", protect, getNetworkQuality);

export default router;
