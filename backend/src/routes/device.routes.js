import express from "express";
import { protect } from "../middleware/auth.middleware.js";
import { requireDeviceCredential } from "../middleware/deviceAuth.middleware.js";
import { createDistributedLimiter } from "../utils/rateLimiter.js";
import {
  createPairingCode,
  listDevices,
  pairDevice,
  revokeDevice,
  rotateDeviceCredential,
} from "../controllers/device.controller.js";

const router = express.Router();

const pairingCodeLimiter = createDistributedLimiter({
  name: "device-pairing-code",
  windowMs: 15 * 60 * 1000,
  limit: 10,
  keyGenerator: (req) => String(req.user?._id || req.ip || "unknown"),
  message: "Too many pairing code requests. Please try again later.",
});

const pairExchangeLimiter = createDistributedLimiter({
  name: "device-pair-exchange",
  windowMs: 15 * 60 * 1000,
  limit: 10,
  message: "Too many device pairing attempts. Please try again later.",
});

const deviceRotateLimiter = createDistributedLimiter({
  name: "device-rotate",
  windowMs: 15 * 60 * 1000,
  limit: 15,
  keyGenerator: (req) => String(req.device?.deviceId || req.ip || "unknown"),
  message: "Too many credential rotation requests. Please try again later.",
});

router.post(
  "/pairing-code",
  protect,
  pairingCodeLimiter,
  createPairingCode
);

router.post("/pair", pairExchangeLimiter, pairDevice);

router.get("/", protect, listDevices);

router.post(
  "/rotate",
  requireDeviceCredential("observations:write"),
  deviceRotateLimiter,
  rotateDeviceCredential
);

router.delete("/:deviceId", protect, revokeDevice);

export default router;
