import mongoose from "mongoose";
import connectDB from "../config/db.js";

export function createHealthHandler(connectFn = connectDB) {
    return async function getHealthHandler(req, res) {
        const skipAutoConnectForUnitTest =
            connectFn === connectDB && process.env.NODE_ENV === "test";

        if (
            mongoose.connection.readyState !== 1 &&
            process.env.MONGODB_URI &&
            !skipAutoConnectForUnitTest
        ) {
            try {
                await connectFn();
            } catch {
                // Report degraded health cleanly below without crashing or leaking DB internals
            }
        }

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
}

export const getHealth = createHealthHandler();
