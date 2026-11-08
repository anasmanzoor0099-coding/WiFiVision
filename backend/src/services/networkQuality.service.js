// WiFiVision Network Quality Intelligence Service

const getSignalScore = (signalDbm) => {
    if (signalDbm >= -50) return 100;
    if (signalDbm >= -60) return 85;
    if (signalDbm >= -67) return 70;
    if (signalDbm >= -70) return 55;
    if (signalDbm >= -80) return 35;
    if (signalDbm >= -90) return 15;

    return 5;
};

const getSignalLevelScore = (signalQuality) => {
    if (signalQuality === undefined || signalQuality === null) {
        return 50;
    }

    return Math.round((signalQuality / 4) * 100);
};

const getBandScore = (band) => {
    const supportedBands = ["2.4 GHz", "5 GHz", "6 GHz"];

    return supportedBands.includes(band) ? 100 : 50;
};

const getSecurityScore = (security) => {
    const scores = {
        WPA3: 100,
        WPA2: 90,
        WPA: 65,
        WEP: 30,
        Open: 20,
        Unknown: 50
    };

    return scores[security] ?? 50;
};

const classifyQuality = (score) => {
    if (score >= 85) return "Excellent";
    if (score >= 70) return "Good";
    if (score >= 50) return "Fair";

    return "Poor";
};

export const calculateNetworkQuality = ({
    signalDbm,
    signalQuality,
    band,
    security
}) => {
    const signalScore = getSignalScore(signalDbm);

    const signalLevelScore =
        getSignalLevelScore(signalQuality);

    const bandScore = getBandScore(band);

    const securityScore = getSecurityScore(security);

    const qualityScore = Math.round(
        signalScore * 0.70 +
        signalLevelScore * 0.15 +
        bandScore * 0.05 +
        securityScore * 0.10
    );

    return {
        score: qualityScore,
        classification: classifyQuality(qualityScore),

        components: {
            signalScore,
            signalLevelScore,
            bandScore,
            securityScore
        },

        details: {
            signalDbm,
            band: band || "Unknown",
            security: security || "Unknown"
        }
    };
};