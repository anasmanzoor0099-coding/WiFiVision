import express from "express";
import cors from "cors";
import helmet from "helmet";
import mongoose from "mongoose";
import cookieParser from "cookie-parser";

import connectDB from "./src/config/db.js";
import { createDistributedLimiter } from "./src/utils/rateLimiter.js";
import observationRoutes from "./src/routes/observation.routes.js";
import qualityRoutes from "./src/routes/quality.routes.js";
import performanceRoutes from "./src/routes/performance.routes.js";
import authRoutes from "./src/routes/auth.routes.js";
import deviceRoutes from "./src/routes/device.routes.js";

import { getHealth } from "./src/controllers/health.controller.js";

const app = express();

app.disable("x-powered-by");

// Required behind Vercel / reverse proxies for accurate client IP rate limiting
app.set("trust proxy", 1);

app.use(
    helmet({
        referrerPolicy: {
            policy: "strict-origin-when-cross-origin"
        }
    })
);

// CORS CONFIGURATION
const configuredOrigins = process.env.CORS_ORIGIN
    ? process.env.CORS_ORIGIN
        .split(",")
        .map(origin => origin.trim())
        .filter(Boolean)
    : [];

const developmentOrigins = [
    "http://localhost:3000",
    "http://localhost:3001",
    "http://127.0.0.1:3000",
    "http://127.0.0.1:3001"
];

const allowedOrigins = [
    ...new Set([
        ...configuredOrigins,
        ...(process.env.NODE_ENV === "production"
            ? []
            : developmentOrigins)
    ])
];

app.use(cors({
    origin: (origin, callback) => {
        if (!origin || allowedOrigins.includes(origin)) {
            return callback(null, true);
        }

        console.warn("CORS blocked origin:", origin);

        return callback(null, false);
    },

    credentials: true,

    methods: [
        "GET",
        "POST",
        "PUT",
        "PATCH",
        "DELETE",
        "OPTIONS"
    ],

    allowedHeaders: [
        "Content-Type",
        "Authorization"
    ],

    maxAge: 600,
    optionsSuccessStatus: 204
}));

// REQUEST BODY PARSING
app.use(express.json({
    limit: "1mb"
}));

app.use(express.urlencoded({
    extended: true,
    limit: "1mb"
}));

// COOKIE PARSING
app.use(cookieParser());

// NOSQL OPERATOR & PROTOTYPE KEY INJECTION GUARD (Express 5 safe)
const FORBIDDEN_KEYS = new Set(["__proto__", "constructor", "prototype"]);

function hasUnsafeKey(value, depth = 0) {
    if (depth > 8 || value === null || typeof value !== "object") {
        return false;
    }

    if (Array.isArray(value)) {
        return value.some((item) => hasUnsafeKey(item, depth + 1));
    }

    for (const key of Object.keys(value)) {
        if (
            key.includes("$") ||
            key.includes(".") ||
            FORBIDDEN_KEYS.has(key)
        ) {
            return true;
        }
        if (hasUnsafeKey(value[key], depth + 1)) {
            return true;
        }
    }

    return false;
}

app.use((req, res, next) => {
    if (hasUnsafeKey(req.body) || hasUnsafeKey(req.query)) {
        return res.status(400).json({
            success: false,
            message: "Invalid request parameter or payload key."
        });
    }

    return next();
});

// SERVERLESS DB CONNECTION ENSURE (for DB-backed routes when running without server.js)
const ensureDatabaseConnection = async (req, res, next) => {
    if (
        mongoose.connection.readyState === 1 ||
        !process.env.MONGODB_URI
    ) {
        return next();
    }

    try {
        await connectDB();
        return next();
    } catch (error) {
        return next(error);
    }
};

// API RATE LIMITING
const apiLimiter = createDistributedLimiter({
    name: "global-api",
    windowMs: 15 * 60 * 1000,
    limit: 120,
    message: "Too many requests. Please try again later."
});

app.use("/api", apiLimiter);

// ROOT ENDPOINT
app.get("/", (req, res) => {
    res.status(200).json({
        success: true,
        name: "WiFiVision API",
        version: "1.0.0",
        message: "Network intelligence platform is running."
    });
});

// HEALTH ENDPOINT
app.get("/api/health", getHealth);

// AUTHENTICATION & DEVICE PAIRING ROUTES
app.use("/api/auth", ensureDatabaseConnection, authRoutes);
app.use("/api/devices", ensureDatabaseConnection, deviceRoutes);

// PRIVATE TELEMETRY & DIAGNOSTIC ROUTES
app.use("/api/observations", ensureDatabaseConnection, observationRoutes);
app.use("/api/quality", ensureDatabaseConnection, qualityRoutes);
app.use("/api/performance", ensureDatabaseConnection, performanceRoutes);

// 404 HANDLER
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

    if (err.type === "entity.too.large") {
        return res.status(413).json({
            success: false,
            message: "Request body is too large."
        });
    }

    if (err instanceof mongoose.Error.ValidationError) {
        return res.status(400).json({
            success: false,
            message: "Database validation failed."
        });
    }

    if (err instanceof mongoose.Error.CastError) {
        return res.status(400).json({
            success: false,
            message: "Invalid resource identifier."
        });
    }

    if (err.code === 11000) {
        return res.status(409).json({
            success: false,
            message: "A record with this information already exists."
        });
    }

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