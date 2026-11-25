import NetworkObservation from "../models/NetworkObservation.js";
import { calculateNetworkQuality } from "../services/networkQuality.service.js";
import {
    deduplicateSessionObservations,
    resolveLatestSessionScope
} from "./observation.controller.js";

export const getNetworkQuality = async (req, res, next) => {
    try {
        const isLatestOnly =
            req.query?.scope === "latest" ||
            req.query?.latestSession === "true";

        let observations = [];
        let latestSessionId = null;
        let latestSessionAt = null;

        if (isLatestOnly) {
            const latestScope = await resolveLatestSessionScope(req.user._id);
            latestSessionId = latestScope.sessionId;
            latestSessionAt = latestScope.sessionTimestamp;
            if (latestScope.hasSession) {
                const rawObs = await NetworkObservation.find(latestScope.match)
                    .select("+bssidHash -bssid")
                    .sort({ observedAt: -1 })
                    .limit(100)
                    .lean();
                observations = deduplicateSessionObservations(rawObs);
            }
        } else {
            observations = await NetworkObservation.find({
                userId: req.user._id
            })
                .select("-bssid -bssidHash")
                .sort({ observedAt: -1 })
                .limit(100)
                .lean();
        }

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
                scanSessionId: observation.scanSessionId || null,
                isConnected: Boolean(observation.isConnected),
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
            latestSessionId,
            latestSessionAt,
            networks: results
        });

    } catch (error) {
        next(error);
    }
};