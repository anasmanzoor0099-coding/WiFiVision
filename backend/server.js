import "dotenv/config";

import connectDB from "./src/config/db.js";
import mongoose from "mongoose";
import app from "./app.js";

const PORT = process.env.PORT || 5000;

let server;
let isShuttingDown = false;

// GRACEFUL SHUTDOWN
const shutdown = async (signal) => {
    if (isShuttingDown) return;

    isShuttingDown = true;

    console.log(`\n${signal} received. Shutting down WiFiVision...`);

    try {
        if (server) {
            await new Promise((resolve, reject) => {
                server.close((error) => {
                    if (error) reject(error);
                    else resolve();
                });
            });

            console.log("HTTP server closed successfully.");
        }

        await mongoose.disconnect();

        console.log("MongoDB disconnected successfully.");
        console.log("WiFiVision backend stopped safely.");

        process.exit(0);

    } catch (error) {
        console.error("Shutdown error:", error.message);
        process.exit(1);
    }
};

// START SERVER
const startServer = async () => {
    try {
        await connectDB();

        server = app.listen(PORT, () => {
            console.log("------------------------------------");
            console.log("       WiFiVision Backend");
            console.log("------------------------------------");
            console.log(`Server running on port ${PORT}`);
            console.log(`Health: http://localhost:${PORT}/api/health`);
            console.log(`Observations: http://localhost:${PORT}/api/observations`);
            console.log(`Quality: http://localhost:${PORT}/api/quality`);
            console.log(`Performance: http://localhost:${PORT}/api/performance`);
            console.log("------------------------------------");
        });

        process.on("SIGINT", () => shutdown("SIGINT"));
        process.on("SIGTERM", () => shutdown("SIGTERM"));

    } catch (error) {
        console.error("Backend startup failed:", error.message);
        await mongoose.disconnect();
        process.exit(1);
    }
};

startServer();