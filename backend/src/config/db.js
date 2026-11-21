import mongoose from "mongoose";

const GLOBAL_CACHE_KEY = "__wifivisionMongooseCache";

function getDbCache() {
    if (!globalThis[GLOBAL_CACHE_KEY]) {
        globalThis[GLOBAL_CACHE_KEY] = {
            conn: null,
            promise: null
        };
    }

    return globalThis[GLOBAL_CACHE_KEY];
}

export function sanitizeDbErrorMessage(message) {
    return String(message || "Unknown database connection error").replace(
        /mongodb(\+srv)?:\/\/[^@\s"'`]+@/gi,
        "mongodb$1://<redacted>@"
    );
}

export function resetDbCacheForTest() {
    const cache = getDbCache();
    cache.conn = null;
    cache.promise = null;
}

const connectDB = async (deps = {}) => {
    const connectFn =
        deps.connect ||
        ((uri, options) => mongoose.connect(uri, options));
    const connectionRef = deps.connection || mongoose.connection;
    const mongoURI =
        deps.uri !== undefined ? deps.uri : process.env.MONGODB_URI;
    const cache = getDbCache();

    // Reuse an already active connection cleanly
    if (connectionRef.readyState === 1) {
        cache.conn = connectionRef;
        return connectionRef;
    }

    if (!mongoURI || !String(mongoURI).trim()) {
        const missingError = new Error(
            "MONGODB_URI is missing from environment variables."
        );
        missingError.code = "DB_UNAVAILABLE";
        throw missingError;
    }

    // Reuse an external in-progress Mongoose connection if one was started elsewhere
    if (
        connectionRef.readyState === 2 &&
        !cache.promise &&
        typeof connectionRef.asPromise === "function"
    ) {
        return connectionRef.asPromise();
    }

    if (!cache.promise) {
        cache.promise = Promise.resolve()
            .then(() =>
                connectFn(mongoURI, {
                    serverSelectionTimeoutMS: 8000,
                    connectTimeoutMS: 8000,
                    socketTimeoutMS: 20000,
                    maxPoolSize: 10,
                    minPoolSize: 0,
                    maxIdleTimeMS: 30000,
                    bufferCommands: false
                })
            )
            .then((connectionResult) => {
                const resolvedConnection =
                    connectionResult?.connection ||
                    connectionResult ||
                    connectionRef;

                cache.conn = resolvedConnection;
                cache.promise = null;

                if (!deps.silent) {
                    console.log("------------------------------------");
                    console.log("MongoDB Connected Successfully");
                    console.log(
                        `Database: ${resolvedConnection?.name || "connected"}`
                    );
                    console.log(
                        `Host: ${resolvedConnection?.host || "atlas"}`
                    );
                    console.log("------------------------------------");
                }

                return connectionResult || connectionRef;
            })
            .catch((error) => {
                cache.promise = null;
                cache.conn = null;

                const safeMessage = sanitizeDbErrorMessage(error?.message);
                console.error("MongoDB Connection Failed:", safeMessage);

                const wrappedError =
                    error instanceof Error ? error : new Error(safeMessage);
                wrappedError.code = "DB_UNAVAILABLE";
                throw wrappedError;
            });
    }

    return cache.promise;
};

export default connectDB;
