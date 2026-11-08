import { z } from "zod";

const observationSchema = z.object({
    ssid: z.string()
        .trim()
        .max(100)
        .optional(),

    bssid: z.string()
        .trim()
        .max(17)
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
        latitude: z.number().min(-90).max(90),
        longitude: z.number().min(-180).max(180),
        accuracyMeters: z.number().min(0).optional()
    }).optional(),

    source: z.enum([
        "android",
        "web"
    ]).optional(),

    observedAt: z.coerce.date().optional()
});

export default observationSchema;