import mongoose from "mongoose";

export const getHealth = (req, res) => {
    const databaseConnected =
        mongoose.connection.readyState === 1;

    const status = databaseConnected
        ? "healthy"
        : "degraded";

    return res.status(databaseConnected ? 200 : 503).json({
        success: databaseConnected,
        status,
        service: "WiFiVision Backend",
        database: databaseConnected
            ? "connected"
            : "disconnected",
        timestamp: new Date().toISOString()
    });
};