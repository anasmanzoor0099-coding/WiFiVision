import { z } from "zod";

const BSSID_MAC_REGEX = /^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/;

const observationSchema = z.object({
    ssid: z.string()
        .trim()
        .max(100)
        .refine((val) => !/[\x00-\x1F\x7F]/.test(val), {
            message: "SSID contains unsupported control characters."
        })
        .optional(),

    bssid: z.string()
        .trim()
        .max(17)
        .regex(BSSID_MAC_REGEX, "Invalid BSSID MAC format.")
        .optional(),

    signalDbm: z.number()
        .int()
        .min(-127)
        .max(0),

    frequencyMhz: z.number()
        .int()
        .positive(),

    band: z.enum([
        "2.4 GHz",
        "5 GHz",
        "6 GHz",
        "Unknown"
    ]).optional(),

    security: z.enum([
        "Open",
        "WEP",
        "WPA",
        "WPA2",
        "WPA3",
        "Unknown"
    ]).optional(),

    signalQuality: z.number()
        .int()
        .min(0)
        .max(4)
        .optional(),

    location: z.object({
        latitude: z.number().finite().min(-90).max(90),
        longitude: z.number().finite().min(-180).max(180),
        accuracyMeters: z.number().finite().min(0).optional()
    }).optional(),

    source: z.enum([
        "android",
        "windows",
        "web"
    ]).optional(),

    observedAt: z.coerce.date().optional()
});

export default observationSchema;