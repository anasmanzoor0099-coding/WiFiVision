import { z } from "zod";
import mongoose from "mongoose";

export const createPairingCodeSchema = z.object({
  platform: z.enum(["android", "windows", "any"]).optional(),
});

export const pairDeviceSchema = z.object({
  pairingCode: z
    .string()
    .trim()
    .transform((val) => val.toUpperCase().replace(/[\s-]+/g, ""))
    .refine(
      (val) => /^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6,8}$/.test(val),
      "Invalid pairing code format."
    ),
  platform: z.enum(["android", "windows"]).optional(),
  deviceName: z
    .string()
    .trim()
    .min(1, "Device name cannot be empty.")
    .max(60, "Device name cannot exceed 60 characters.")
    .refine((val) => !/[\x00-\x1F\x7F]/.test(val), {
      message: "Device name contains invalid control characters.",
    })
    .optional(),
});

export const deviceIdParamSchema = z.string().refine(
  (id) => mongoose.Types.ObjectId.isValid(id),
  "Invalid device identifier."
);
