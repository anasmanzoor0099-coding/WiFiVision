import { test } from "node:test";
import assert from "node:assert/strict";
import request from "supertest";
import mongoose from "mongoose";

import app from "../app.js";

// TEST 1: Root API endpoint
test("Should return WiFiVision API information", async () => {
    const response = await request(app).get("/");

    assert.equal(response.status, 200);
    assert.equal(response.body.success, true);
    assert.equal(response.body.name, "WiFiVision API");
    assert.equal(response.body.version, "1.0.0");
});

// TEST 2: Health endpoint with disconnected database
test("Should report degraded status when database is disconnected", async () => {
    const originalState = mongoose.connection.readyState;

    try {
        mongoose.connection.readyState = 0;

        const response = await request(app)
            .get("/api/health");

        assert.equal(response.status, 503);
        assert.equal(response.body.success, false);
        assert.equal(response.body.status, "degraded");
        assert.equal(response.body.database, "disconnected");
    } finally {
        mongoose.connection.readyState = originalState;
    }
});

// TEST 3: Health endpoint with connected database
test("Should report healthy status when database is connected", async () => {
    const originalState = mongoose.connection.readyState;

    try {
        mongoose.connection.readyState = 1;

        const response = await request(app)
            .get("/api/health");

        assert.equal(response.status, 200);
        assert.equal(response.body.success, true);
        assert.equal(response.body.status, "healthy");
        assert.equal(response.body.database, "connected");
    } finally {
        mongoose.connection.readyState = originalState;
    }
});

// TEST 4: Unknown endpoint
test("Should return 404 for unknown API endpoints", async () => {
    const response = await request(app)
        .get("/api/unknown-endpoint");

    assert.equal(response.status, 404);
    assert.equal(response.body.success, false);
});

// TEST 5: Security header
test("Should hide the Express technology header", async () => {
    const response = await request(app).get("/");

    assert.equal(
        response.headers["x-powered-by"],
        undefined
    );
});

// TEST 6: Reject malformed JSON
test("Should reject malformed JSON with status 400", async () => {
    const response = await request(app)
        .post("/api/observations")
        .set("Content-Type", "application/json")
        .send('{"signalDbm": -55, invalid}');

    assert.equal(response.status, 400);
    assert.equal(response.body.success, false);
    assert.equal(
        response.body.message,
        "Invalid JSON request body."
    );
});

// TEST 7: Reject oversized request body
test("Should reject oversized JSON with status 413", async () => {
    const oversizedPayload = JSON.stringify({
        data: "A".repeat(1_100_000)
    });

    const response = await request(app)
        .post("/api/observations")
        .set("Content-Type", "application/json")
        .send(oversizedPayload);

    assert.equal(response.status, 413);
    assert.equal(response.body.success, false);
    assert.equal(
        response.body.message,
        "Request body is too large."
    );
});