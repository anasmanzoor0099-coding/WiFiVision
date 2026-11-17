import crypto from "node:crypto";
import PairingCode from "../models/PairingCode.js";
import DeviceCredential from "../models/DeviceCredential.js";
import User from "../models/User.js";
import { hashDeviceSecret, hashPairingCode } from "../utils/cryptoSecrets.js";
import {
  createPairingCodeSchema,
  deviceIdParamSchema,
  pairDeviceSchema,
} from "../validators/device.validator.js";

const PAIRING_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
const PAIRING_CODE_LENGTH = 8;
const PAIRING_TTL_MS = 5 * 60 * 1000;
const DEVICE_CREDENTIAL_TTL_MS = 90 * 24 * 60 * 60 * 1000;

function generateRandomPairingCode() {
  let code = "";
  for (let i = 0; i < PAIRING_CODE_LENGTH; i++) {
    code += PAIRING_ALPHABET[crypto.randomInt(0, PAIRING_ALPHABET.length)];
  }
  return code;
}

function buildDeviceToken(deviceId, rawSecretHex) {
  return `wvd_${deviceId.toString()}_${rawSecretHex}`;
}

// POST /api/devices/pairing-code (Authenticated user)
export const createPairingCode = async (req, res, next) => {
  try {
    const validation = createPairingCodeSchema.safeParse(req.body || {});
    if (!validation.success) {
      return res.status(400).json({
        success: false,
        message: "Invalid pairing platform specified.",
      });
    }

    const platform = validation.data.platform || "any";
    const now = new Date();
    const expiresAt = new Date(now.getTime() + PAIRING_TTL_MS);
    const plainCode = generateRandomPairingCode();
    const codeHash = hashPairingCode(plainCode);

    // Expire any previous unconsumed pairing codes for this user cleanly
    await PairingCode.updateMany(
      { userId: req.user._id, consumedAt: null },
      { $set: { consumedAt: now } }
    );

    await PairingCode.create({
      userId: req.user._id,
      codeHash,
      platform,
      expiresAt,
      consumedAt: null,
    });

    return res.status(201).json({
      success: true,
      pairingCode: plainCode,
      platform,
      expiresAt: expiresAt.toISOString(),
      expiresInSeconds: Math.floor(PAIRING_TTL_MS / 1000),
    });
  } catch (error) {
    return next(error);
  }
};

// POST /api/devices/pair (Android or Windows companion app)
export const pairDevice = async (req, res, next) => {
  try {
    const validation = pairDeviceSchema.safeParse(req.body || {});
    if (!validation.success) {
      return res.status(400).json({
        success: false,
        message: "Please provide a valid pairing code and device details.",
      });
    }

    const { pairingCode, deviceName, platform: requestedPlatform } =
      validation.data;
    const codeHash = hashPairingCode(pairingCode);
    const now = new Date();

    // Atomic single-use consumption prevents concurrent double-pairing
    const consumedSession = await PairingCode.findOneAndUpdate(
      {
        codeHash,
        consumedAt: null,
        expiresAt: { $gt: now },
      },
      {
        $set: { consumedAt: now },
      },
      {
        returnDocument: "before",
      }
    );

    if (!consumedSession) {
      return res.status(401).json({
        success: false,
        message: "Pairing code is invalid, expired, or already used.",
      });
    }

    const effectivePlatform = requestedPlatform || "android";
    if (
      consumedSession.platform &&
      consumedSession.platform !== "any" &&
      consumedSession.platform !== effectivePlatform
    ) {
      return res.status(403).json({
        success: false,
        message: `Pairing code is restricted to ${consumedSession.platform} devices.`,
      });
    }

    const owner = await User.findById(consumedSession.userId).select(
      "_id isEmailVerified"
    );
    if (!owner || !owner.isEmailVerified) {
      return res.status(401).json({
        success: false,
        message: "Account eligible for pairing no longer exists or is unverified.",
      });
    }

    const rawSecretHex = crypto.randomBytes(32).toString("hex");
    const secretHash = hashDeviceSecret(rawSecretHex);
    const expiresAt = new Date(now.getTime() + DEVICE_CREDENTIAL_TTL_MS);
    const defaultLabel =
      effectivePlatform === "windows" ? "Windows Companion" : "Android Companion";

    const credential = await DeviceCredential.create({
      userId: owner._id,
      deviceName: deviceName || defaultLabel,
      platform: effectivePlatform,
      secretHash,
      scopes: ["observations:write"],
      status: "active",
      revokedAt: null,
      expiresAt,
      lastUsedAt: now,
    });

    return res.status(201).json({
      success: true,
      message: `${
        effectivePlatform === "windows" ? "Windows" : "Android"
      } device paired successfully.`,
      device: {
        deviceId: credential._id.toString(),
        deviceName: credential.deviceName,
        platform: credential.platform,
        scopes: credential.scopes,
        expiresAt: credential.expiresAt.toISOString(),
      },
      deviceToken: buildDeviceToken(credential._id, rawSecretHex),
    });
  } catch (error) {
    return next(error);
  }
};

// GET /api/devices (Authenticated user)
export const listDevices = async (req, res, next) => {
  try {
    const devices = await DeviceCredential.find({ userId: req.user._id })
      .select(
        "_id deviceName platform scopes status revokedAt expiresAt lastUsedAt createdAt"
      )
      .sort({ createdAt: -1 })
      .limit(50)
      .lean();

    return res.status(200).json({
      success: true,
      count: devices.length,
      devices: devices.map((d) => ({
        deviceId: d._id.toString(),
        deviceName: d.deviceName,
        platform: d.platform || "android",
        scopes: d.scopes,
        status: d.status,
        revokedAt: d.revokedAt,
        expiresAt: d.expiresAt,
        lastUsedAt: d.lastUsedAt,
        createdAt: d.createdAt,
      })),
    });
  } catch (error) {
    return next(error);
  }
};

// POST /api/devices/rotate (Paired Android or Windows device)
export const rotateDeviceCredential = async (req, res, next) => {
  try {
    const rawSecretHex = crypto.randomBytes(32).toString("hex");
    const secretHash = hashDeviceSecret(rawSecretHex);
    const now = new Date();
    const expiresAt = new Date(now.getTime() + DEVICE_CREDENTIAL_TTL_MS);

    const updated = await DeviceCredential.findOneAndUpdate(
      {
        _id: req.device.deviceId,
        userId: req.device.userId,
        status: "active",
        revokedAt: null,
      },
      {
        $set: {
          secretHash,
          expiresAt,
          lastUsedAt: now,
        },
      },
      {
        returnDocument: "after",
      }
    );

    if (!updated) {
      return res.status(401).json({
        success: false,
        message: "Device credential can no longer be rotated.",
      });
    }

    return res.status(200).json({
      success: true,
      message: "Device credential rotated successfully.",
      device: {
        deviceId: updated._id.toString(),
        deviceName: updated.deviceName,
        platform: updated.platform || "android",
        scopes: updated.scopes,
        expiresAt: updated.expiresAt.toISOString(),
      },
      deviceToken: buildDeviceToken(updated._id, rawSecretHex),
    });
  } catch (error) {
    return next(error);
  }
};

// DELETE /api/devices/:deviceId (Authenticated user)
export const revokeDevice = async (req, res, next) => {
  try {
    const idCheck = deviceIdParamSchema.safeParse(req.params.deviceId);
    if (!idCheck.success) {
      return res.status(400).json({
        success: false,
        message: "Invalid device identifier.",
      });
    }

    const revoked = await DeviceCredential.findOneAndUpdate(
      {
        _id: idCheck.data,
        userId: req.user._id,
      },
      {
        $set: {
          status: "revoked",
          revokedAt: new Date(),
        },
      },
      {
        returnDocument: "after",
      }
    );

    if (!revoked) {
      return res.status(404).json({
        success: false,
        message: "Device not found.",
      });
    }

    return res.status(200).json({
      success: true,
      message: "Device revoked immediately.",
      device: {
        deviceId: revoked._id.toString(),
        deviceName: revoked.deviceName,
        platform: revoked.platform || "android",
        status: revoked.status,
        revokedAt: revoked.revokedAt,
      },
    });
  } catch (error) {
    return next(error);
  }
};
