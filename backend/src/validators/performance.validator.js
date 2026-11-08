import { z } from "zod";

const performanceSchema = z.object({
    latencyMs: z.number()
        .finite()
        .min(0),

    medianLatencyMs: z.number()
        .finite()
        .min(0),

    jitterMs: z.number()
        .finite()
        .min(0),

    downloadMbps: z.number()
        .finite()
        .min(0),

    uploadMbps: z.number()
        .finite()
        .min(0),

    packetLoss: z.object({
        totalPackets: z.number()
            .int()
            .min(1),

        successfulPackets: z.number()
            .int()
            .min(0),

        lostPackets: z.number()
            .int()
            .min(0),

        packetLossPercentage: z.number()
            .finite()
            .min(0)
            .max(100),

        averageResponseMs: z.number()
            .finite()
            .min(0)
            .nullable(),

        measurementType: z.string()
            .min(1)
    }).superRefine((data, context) => {
        if (
            data.successfulPackets + data.lostPackets !==
            data.totalPackets
        ) {
            context.addIssue({
                code: "custom",
                message: "Successful and lost packets must equal total packets.",
                path: ["totalPackets"]
            });
        }

        const expectedLoss =
            (data.lostPackets / data.totalPackets) * 100;

        if (
            Math.abs(
                data.packetLossPercentage - expectedLoss
            ) > 0.01
        ) {
            context.addIssue({
                code: "custom",
                message: "Packet loss percentage does not match packet counts.",
                path: ["packetLossPercentage"]
            });
        }
    }),

    source: z.enum([
        "backend",
        "android",
        "web"
    ]),

    testServer: z.string()
        .min(1),

    measuredAt: z.coerce.date()
});

export default performanceSchema;