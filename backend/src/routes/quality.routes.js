import express from "express";

import {
    getNetworkQuality
} from "../controllers/quality.controller.js";

const router = express.Router();

// Get network quality intelligence
router.get("/", getNetworkQuality);

export default router;