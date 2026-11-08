import NetworkObservation from "../models/NetworkObservation.js";
import { calculateNetworkQuality } from "../services/networkQuality.service.js";

export const getNetworkQuality = async (req, res, next) => {
    try {
        const observations = await NetworkObservation.find()
            .select("-bssid")
            .sort({ observedAt: -1 })
            .limit(100)
            .lean();

        const results = observations.map((observation) => {
            const quality = calculateNetworkQuality({
                signalDbm: observation.signalDbm,
                signalQuality: observation.signalQuality,
                band: observation.band,
                security: observation.security
            });

            return {
                observationId: observation._id,
                ssid: observation.ssid,
                quality,
                observedAt: observation.observedAt
            };
        });

        const averageScore = results.length
            ? Math.round(
                results.reduce(
                    (total, item) => total + item.quality.score,
                    0
                ) / results.length
            )
            : null;

        return res.status(200).json({
            success: true,
            count: results.length,
            averageScore,
            networks: results
        });

    } catch (error) {
        next(error);
    }
};