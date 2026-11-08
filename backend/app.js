import express from "express";
import cors from "cors";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import mongoose from "mongoose";

import observationRoutes from "./src/routes/observation.routes.js";
import qualityRoutes from "./src/routes/quality.routes.js";
import performanceRoutes from "./src/routes/performance.routes.js";

import {
    getHealth
} from "./src/controllers/health.controller.js";

const app = express();

// Hide Express technology information
app.disable("x-powered-by");

// Security headers
app.use(helmet());

// CORS CONFIGURATION
const allowedOrigins = process.env.CORS_ORIGIN
    ? process.env.CORS_ORIGIN
        .split(",")
        .map(origin => origin.trim())
        .filter(Boolean)
    : process.env.NODE_ENV === "production"
        ? []
        : ["http://localhost:3000"];

app.use(cors({
    origin: (origin, callback) => {
        // Allow native Android and server-to-server requests
        if (!origin) {
            return callback(null, true);
        }

        // Allow only explicitly configured frontend origins
        if (allowedOrigins.includes(origin)) {
            return callback(null, true);
        }

        return callback(null, false);
    },

    methods: ["GET", "POST", "PUT", "PATCH", "DELETE"],

    allowedHeaders: [
        "Content-Type",
        "Authorization"
    ],

    maxAge: 600,
    optionsSuccessStatus: 204
}));

// Request body parsing
app.use(express.json({
    limit: "1mb"
}));

app.use(express.urlencoded({
    extended: true,
    limit: "1mb"
}));

// API RATE LIMITING
const apiLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,

    limit: 100,

    standardHeaders: "draft-8",

    legacyHeaders: false,

    message: {
        success: false,
        message: "Too many requests. Please try again later."
    }
});

app.use("/api", apiLimiter);

// Root endpoint
app.get("/", (req, res) => {
    res.status(200).json({
        success: true,
        name: "WiFiVision API",
        version: "1.0.0",
        message: "Network intelligence platform is running."
    });
});

// Health endpoint
app.get("/api/health", getHealth);

// API routes
app.use("/api/observations", observationRoutes);
app.use("/api/quality", qualityRoutes);
app.use("/api/performance", performanceRoutes);

// 404 handler
app.use((req, res) => {
    res.status(404).json({
        success: false,
        message: "API endpoint not found."
    });
});

// GLOBAL ERROR HANDLER
app.use((err, req, res, next) => {
    if (res.headersSent) {
        return next(err);
    }

    // Malformed JSON
    if (
        err instanceof SyntaxError &&
        err.status === 400 &&
        "body" in err
    ) {
        return res.status(400).json({
            success: false,
            message: "Invalid JSON request body."
        });
    }

    // Request body too large
    if (err.type === "entity.too.large") {
        return res.status(413).json({
            success: false,
            message: "Request body is too large."
        });
    }

    // Mongoose validation errors
    if (err instanceof mongoose.Error.ValidationError) {
        return res.status(400).json({
            success: false,
            message: "Database validation failed."
        });
    }

    // Mongoose invalid object ID
    if (err instanceof mongoose.Error.CastError) {
        return res.status(400).json({
            success: false,
            message: "Invalid resource identifier."
        });
    }

    // MongoDB duplicate key
    if (err.code === 11000) {
        return res.status(409).json({
            success: false,
            message: "A record with this information already exists."
        });
    }

    // Log internal details on the server only
    console.error("Server error:", {
        message: err.message,
        path: req.originalUrl,
        method: req.method
    });

    return res.status(500).json({
        success: false,
        message: "Internal server error."
    });
});

export default app;