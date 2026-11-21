import { test, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import mongoose from "mongoose";

import connectDB, {
    resetDbCacheForTest,
    sanitizeDbErrorMessage
} from "../src/config/db.js";
import { createHealthHandler } from "../src/controllers/health.controller.js";

let originalReadyState;
let originalMongoUri;

beforeEach(() => {
    originalReadyState = mongoose.connection.readyState;
    originalMongoUri = process.env.MONGODB_URI;
    resetDbCacheForTest();
});

afterEach(() => {
    mongoose.connection.readyState = originalReadyState;
    if (originalMongoUri === undefined) {
        delete process.env.MONGODB_URI;
    } else {
        process.env.MONGODB_URI = originalMongoUri;
    }
    resetDbCacheForTest();
});

function createMockRes() {
    const res = {
        statusCode: 200,
        body: null,
        status(code) {
            this.statusCode = code;
            return this;
        },
        json(payload) {
            this.body = payload;
            return this;
        }
    };
    return res;
}

test("sanitizeDbErrorMessage redacts MongoDB URI credentials", () => {
    const raw =
        "bad auth connecting to mongodb+srv://adminUser:SuperSecret99@cluster0.example.mongodb.net/wifivision";
    const safe = sanitizeDbErrorMessage(raw);
    assert.equal(safe.includes("SuperSecret99"), false);
    assert.equal(safe.includes("adminUser"), false);
    assert.match(safe, /mongodb\+srv:\/\/<redacted>@cluster0\.example\.mongodb\.net\/wifivision/);
});

test("connectDB deduplicates concurrent cold-start connections via shared cache", async () => {
    let connectCalls = 0;
    const mockConn = { readyState: 0, name: "wifivision", host: "mock-atlas" };

    const mockConnect = async () => {
        connectCalls += 1;
        await new Promise((resolve) => setTimeout(resolve, 25));
        mockConn.readyState = 1;
        return { connection: mockConn };
    };

    const [c1, c2, c3] = await Promise.all([
        connectDB({
            connect: mockConnect,
            connection: mockConn,
            uri: "mongodb+srv://user:pass@cluster.example.mongodb.net/wifivision",
            silent: true
        }),
        connectDB({
            connect: mockConnect,
            connection: mockConn,
            uri: "mongodb+srv://user:pass@cluster.example.mongodb.net/wifivision",
            silent: true
        }),
        connectDB({
            connect: mockConnect,
            connection: mockConn,
            uri: "mongodb+srv://user:pass@cluster.example.mongodb.net/wifivision",
            silent: true
        })
    ]);

    assert.equal(connectCalls, 1);
    assert.equal(c1.connection, mockConn);
    assert.equal(c2.connection, mockConn);
    assert.equal(c3.connection, mockConn);

    // Warm follow-up call skips connectFn when readyState === 1
    await connectDB({
        connect: mockConnect,
        connection: mockConn,
        uri: "mongodb+srv://user:pass@cluster.example.mongodb.net/wifivision",
        silent: true
    });
    assert.equal(connectCalls, 1);
});

test("connectDB resets failed promise cache and recovers cleanly on retry", async () => {
    let attempts = 0;
    const mockConn = { readyState: 0, name: "wifivision", host: "mock-atlas" };

    const flakyConnect = async () => {
        attempts += 1;
        if (attempts === 1) {
            throw new Error("Server selection timed out");
        }
        mockConn.readyState = 1;
        return { connection: mockConn };
    };

    await assert.rejects(
        () =>
            connectDB({
                connect: flakyConnect,
                connection: mockConn,
                uri: "mongodb+srv://user:pass@cluster.example.mongodb.net/wifivision",
                silent: true
            }),
        (err) => err.code === "DB_UNAVAILABLE"
    );

    const recovered = await connectDB({
        connect: flakyConnect,
        connection: mockConn,
        uri: "mongodb+srv://user:pass@cluster.example.mongodb.net/wifivision",
        silent: true
    });
    assert.equal(attempts, 2);
    assert.equal(recovered.connection.readyState, 1);
});

test("createHealthHandler connects lazily on cold start and returns 200 healthy", async () => {
    process.env.MONGODB_URI = "mongodb+srv://user:pass@cluster.example.mongodb.net/wifivision";
    mongoose.connection.readyState = 0;

    let calls = 0;
    const handler = createHealthHandler(async () => {
        calls += 1;
        mongoose.connection.readyState = 1;
    });

    const res = createMockRes();
    await handler({}, res);

    assert.equal(calls, 1);
    assert.equal(res.statusCode, 200);
    assert.equal(res.body.success, true);
    assert.equal(res.body.status, "healthy");
    assert.equal(res.body.database, "connected");
});

test("createHealthHandler degrades gracefully to 503 when DB connection fails", async () => {
    process.env.MONGODB_URI = "mongodb+srv://user:pass@cluster.example.mongodb.net/wifivision";
    mongoose.connection.readyState = 0;

    const handler = createHealthHandler(async () => {
        throw new Error("mongodb+srv://secretUser:secretPass@cluster.example.mongodb.net failure");
    });

    const res = createMockRes();
    await handler({}, res);

    assert.equal(res.statusCode, 503);
    assert.equal(res.body.success, false);
    assert.equal(res.body.status, "degraded");
    assert.equal(res.body.database, "disconnected");
    assert.equal(JSON.stringify(res.body).includes("secretPass"), false);
});

test("createHealthHandler skips connectFn when already connected", async () => {
    process.env.MONGODB_URI = "mongodb+srv://user:pass@cluster.example.mongodb.net/wifivision";
    mongoose.connection.readyState = 1;

    let calls = 0;
    const handler = createHealthHandler(async () => {
        calls += 1;
    });

    const res = createMockRes();
    await handler({}, res);

    assert.equal(calls, 0);
    assert.equal(res.statusCode, 200);
    assert.equal(res.body.database, "connected");
});
