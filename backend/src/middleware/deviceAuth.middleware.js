import mongoose from "mongoose";
import DeviceCredential from "../models/DeviceCredential.js";
import User from "../models/User.js";
import { hashDeviceSecret, safeTimingEqualHex } from "../utils/cryptoSecrets.js";
import { consumeReplayNonce } from "../utils/rateLimiter.js";

const DEVICE_TOKEN_REGEX = /^wvd_([0-9a-fA-F]{24})_([0-9a-fA-F]{64})$/;
const NONCE_REGEX = /^[A-Za-z0-9_-]{16,64}$/;
const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000;

export function parseBearerDeviceToken(authHeader) {
  if (typeof authHeader !== "string" || !authHeader.startsWith("Bearer ")) {
    return null;
  }
  const rawToken = authHeader.slice("Bearer ".length).trim();
  const match = rawToken.match(DEVICE_TOKEN_REGEX);
  if (!match) return null;
  return {
    deviceId: match[1],
    rawSecret: match[2],
  };
}

export function requireDeviceCredential(requiredScope = "observations:write") {
  return async function authenticateDeviceCredential(req, res, next) {
    try {
      const parsed = parseBearerDeviceToken(req.headers.authorization);
      if (!parsed) {
        return res.status(401).json({
          success: false,
          code: "DEVICE_PAIRING_REQUIRED",
          message:
            "Device pairing required. Generate a pairing code in web Settings and pair this Android device before uploading.",
        });
      }

      const rawTimestamp = req.headers["x-wifivision-timestamp"];
      const rawNonce = req.headers["x-wifivision-nonce"];

      if (
        typeof rawTimestamp !== "string" ||
        !/^\d{10,16}$/.test(rawTimestamp.trim())
      ) {
        return res.status(401).json({
          success: false,
          message: "Missing or invalid device request timestamp.",
        });
      }

      const timestampMs = Number(rawTimestamp.trim());
      if (
        !Number.isFinite(timestampMs) ||
        Math.abs(Date.now() - timestampMs) > MAX_CLOCK_SKEW_MS
      ) {
        return res.status(401).json({
          success: false,
          message: "Device request timestamp expired or out of sync.",
        });
      }

      if (typeof rawNonce !== "string" || !NONCE_REGEX.test(rawNonce.trim())) {
        return res.status(401).json({
          success: false,
          message: "Missing or invalid device request nonce.",
        });
      }

      if (!mongoose.Types.ObjectId.isValid(parsed.deviceId)) {
        return res.status(401).json({
          success: false,
          message: "Invalid device credential.",
        });
      }

      const credential = await DeviceCredential.findById(parsed.deviceId).select(
        "+secretHash"
      );

      if (
        !credential ||
        credential.status !== "active" ||
        credential.revokedAt !== null ||
        !credential.expiresAt ||
        credential.expiresAt.getTime() <= Date.now()
      ) {
        return res.status(401).json({
          success: false,
          code: "DEVICE_CREDENTIAL_REVOKED",
          message:
            "Device credential is expired or revoked. Please re-pair this device in Settings.",
        });
      }

      const expectedHash = hashDeviceSecret(parsed.rawSecret);
      if (!safeTimingEqualHex(expectedHash, credential.secretHash)) {
        return res.status(401).json({
          success: false,
          message: "Invalid device credential.",
        });
      }

      if (requiredScope && !credential.scopes.includes(requiredScope)) {
        return res.status(403).json({
          success: false,
          message: "Device credential lacks required scope.",
        });
      }

      const owner = await User.findById(credential.userId).select(
        "_id isEmailVerified"
      );
      if (!owner || !owner.isEmailVerified) {
        return res.status(401).json({
          success: false,
          message: "Paired account is no longer active.",
        });
      }

      let nonceFresh;
      try {
        nonceFresh = await consumeReplayNonce(
          `${parsed.deviceId}:${rawNonce.trim()}`,
          6 * 60 * 1000
        );
      } catch {
        return res.status(503).json({
          success: false,
          message:
            "Replay-protection service temporarily unavailable. Please retry.",
        });
      }

      if (!nonceFresh) {
        return res.status(401).json({
          success: false,
          code: "REPLAY_DETECTED",
          message: "Replayed device request rejected.",
        });
      }

      await DeviceCredential.updateOne(
        { _id: credential._id },
        { $set: { lastUsedAt: new Date() } }
      );

      req.device = {
        deviceId: credential._id,
        userId: credential.userId,
        deviceName: credential.deviceName,
        platform: credential.platform || "android",
        scopes: credential.scopes,
      };

      return next();
    } catch (error) {
      return next(error);
    }
  };
}
