import "dotenv/config";

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import jwt from "jsonwebtoken";
import mongoose from "mongoose";
import request from "supertest";

const originalNodeEnv = process.env.NODE_ENV;
const originalJwtSecret = process.env.JWT_SECRET;
const originalJwtExpiresIn = process.env.JWT_EXPIRES_IN;
const originalSmtpUser = process.env.SMTP_USER;
const originalSmtpPass = process.env.SMTP_PASS;

process.env.NODE_ENV = "test";
if (!process.env.JWT_SECRET) {
  process.env.JWT_SECRET = "wifivision_phase2_test_secret_key_change_in_prod";
}

const { default: app } = await import("../app.js");
const { default: connectDB } = await import("../src/config/db.js");
const { default: User } = await import("../src/models/User.js");
const {
  hashToken,
  parseExpiresInMs,
  generateAuthToken,
  getAuthCookieOptions,
} = await import("../src/utils/auth.utils.js");
const { sendVerificationEmail } = await import(
  "../src/services/email.service.js"
);
const { createPasswordSchema } = await import(
  "../src/validators/auth.validator.js"
);

const runId = Date.now();
const testEmailPrefix = `wifivision_auth_test_${runId}`;
const primaryEmail = `${testEmailPrefix}_primary@example.com`;
const unverifiedEmail = `${testEmailPrefix}_unverified@example.com`;
const validPassword = "WiFiVision!2026";

before(async () => {
  await connectDB();
});

after(async () => {
  try {
    if (mongoose.connection.readyState === 1) {
      await User.deleteMany({
        email: { $regex: `^${testEmailPrefix}` },
      });
    }
  } finally {
    process.env.NODE_ENV = originalNodeEnv;
    if (originalJwtSecret === undefined) {
      delete process.env.JWT_SECRET;
    } else {
      process.env.JWT_SECRET = originalJwtSecret;
    }
    if (originalJwtExpiresIn === undefined) {
      delete process.env.JWT_EXPIRES_IN;
    } else {
      process.env.JWT_EXPIRES_IN = originalJwtExpiresIn;
    }
    if (originalSmtpUser === undefined) {
      delete process.env.SMTP_USER;
    } else {
      process.env.SMTP_USER = originalSmtpUser;
    }
    if (originalSmtpPass === undefined) {
      delete process.env.SMTP_PASS;
    } else {
      process.env.SMTP_PASS = originalSmtpPass;
    }

    await mongoose.disconnect();
  }
});

test("JWT and cookie expiry default to 24 hours and honor JWT_EXPIRES_IN", () => {
  const savedExpires = process.env.JWT_EXPIRES_IN;
  try {
    delete process.env.JWT_EXPIRES_IN;

    assert.equal(parseExpiresInMs(), 24 * 60 * 60 * 1000);
    const defaultCookieOpts = getAuthCookieOptions();
    assert.equal(defaultCookieOpts.maxAge, 24 * 60 * 60 * 1000);

    const fakeUser = {
      _id: new mongoose.Types.ObjectId(),
      email: "expiry-check@example.com",
      tokenVersion: 0,
    };

    const defaultToken = generateAuthToken(fakeUser);
    const defaultDecoded = jwt.verify(defaultToken, process.env.JWT_SECRET, {
      issuer: "wifivision",
      audience: "wifivision-users",
    });
    assert.equal(defaultDecoded.exp - defaultDecoded.iat, 24 * 60 * 60);

    process.env.JWT_EXPIRES_IN = "2h";
    assert.equal(getAuthCookieOptions().maxAge, 2 * 60 * 60 * 1000);

    const customToken = generateAuthToken(fakeUser);
    const customDecoded = jwt.verify(customToken, process.env.JWT_SECRET, {
      issuer: "wifivision",
      audience: "wifivision-users",
    });
    assert.equal(customDecoded.exp - customDecoded.iat, 2 * 60 * 60);
  } finally {
    if (savedExpires === undefined) {
      delete process.env.JWT_EXPIRES_IN;
    } else {
      process.env.JWT_EXPIRES_IN = savedExpires;
    }
  }
});

test("Email service uses safe fallback in test/dev and requires config in production", async () => {
  const savedEnv = process.env.NODE_ENV;
  const savedUser = process.env.SMTP_USER;
  const savedPass = process.env.SMTP_PASS;

  try {
    process.env.NODE_ENV = "test";
    const testResult = await sendVerificationEmail(primaryEmail, "123456");
    assert.equal(testResult.fallback, true);
    assert.equal(testResult.messageId, "test-fallback");

    delete process.env.SMTP_USER;
    delete process.env.SMTP_PASS;
    process.env.NODE_ENV = "development";
    const devResult = await sendVerificationEmail(primaryEmail, "123456");
    assert.equal(devResult.fallback, true);
    assert.equal(devResult.messageId, "dev-fallback");

    process.env.NODE_ENV = "production";
    await assert.rejects(
      () => sendVerificationEmail(primaryEmail, "123456"),
      /Email service is not configured/
    );
  } finally {
    process.env.NODE_ENV = savedEnv;
    if (savedUser === undefined) delete process.env.SMTP_USER;
    else process.env.SMTP_USER = savedUser;
    if (savedPass === undefined) delete process.env.SMTP_PASS;
    else process.env.SMTP_PASS = savedPass;
  }
});

test("GET /api/auth/me rejects unauthenticated, invalid, and expired sessions with 401", async () => {
  const unauthRes = await request(app).get("/api/auth/me");
  assert.equal(unauthRes.status, 401);
  assert.equal(unauthRes.body.success, false);

  const malformedRes = await request(app)
    .get("/api/auth/me")
    .set("Cookie", "wifivision_token=not-a-valid-jwt");
  assert.equal(malformedRes.status, 401);

  const expiredToken = jwt.sign(
    {
      sub: new mongoose.Types.ObjectId().toString(),
      email: primaryEmail,
    },
    process.env.JWT_SECRET,
    {
      expiresIn: -10,
      issuer: "wifivision",
      audience: "wifivision-users",
    }
  );
  const expiredRes = await request(app)
    .get("/api/auth/me")
    .set("Cookie", `wifivision_token=${expiredToken}`);
  assert.equal(expiredRes.status, 401);

  const missingUserToken = jwt.sign(
    {
      sub: new mongoose.Types.ObjectId().toString(),
      email: "deleted@example.com",
    },
    process.env.JWT_SECRET,
    {
      expiresIn: "1h",
      issuer: "wifivision",
      audience: "wifivision-users",
    }
  );
  const missingUserRes = await request(app)
    .get("/api/auth/me")
    .set("Cookie", `wifivision_token=${missingUserToken}`);
  assert.equal(missingUserRes.status, 401);
});

test("Auth endpoints validate payloads consistently using Zod schemas", async () => {
  const startBad = await request(app)
    .post("/api/auth/register/start")
    .send({ name: "A", email: "invalid-email" });
  assert.equal(startBad.status, 400);
  assert.equal(startBad.body.success, false);

  const verifyBad = await request(app)
    .post("/api/auth/register/verify")
    .send({ email: primaryEmail, code: "12ab" });
  assert.equal(verifyBad.status, 400);
  assert.equal(verifyBad.body.success, false);

  const resendBad = await request(app)
    .post("/api/auth/resend-code")
    .send({ email: "not-an-email" });
  assert.equal(resendBad.status, 400);
  assert.equal(resendBad.body.success, false);

  const loginBad = await request(app)
    .post("/api/auth/login")
    .send({ email: "bad", password: "" });
  assert.equal(loginBad.status, 400);
  assert.equal(loginBad.body.success, false);

  assert.equal(
    createPasswordSchema.safeParse({ password: "Aa1!" + "x".repeat(75) }).success,
    false
  );
  assert.equal(
    createPasswordSchema.safeParse({ password: "weak" }).success,
    false
  );
  assert.equal(
    createPasswordSchema.safeParse({ password: validPassword }).success,
    true
  );
});

test("protect middleware rejects unverified users even with an otherwise valid JWT", async () => {
  const unverifiedUser = await User.create({
    name: "Unverified User",
    email: unverifiedEmail,
    isEmailVerified: false,
  });

  const token = generateAuthToken(unverifiedUser);
  const meRes = await request(app)
    .get("/api/auth/me")
    .set("Cookie", `wifivision_token=${token}`);

  assert.equal(meRes.status, 403);
  assert.equal(meRes.body.success, false);
});

test("Full registration, OTP, resend cooldown, password setup, login, /me, and logout flow works end-to-end", async () => {
  const agent = request.agent(app);

  // Step 1: Start registration
  const startRes = await agent.post("/api/auth/register/start").send({
    name: "  Alex Mercer  ",
    email: `  ${primaryEmail.toUpperCase()}  `,
  });
  assert.equal(startRes.status, 200);
  assert.equal(startRes.body.success, true);
  assert.equal(startRes.body.email, primaryEmail);

  const createdUser = await User.findOne({ email: primaryEmail }).select(
    "+emailVerificationToken +emailVerificationExpires +emailVerificationLastSentAt"
  );
  assert.ok(createdUser);
  assert.equal(createdUser.name, "Alex Mercer");
  assert.equal(createdUser.isEmailVerified, false);
  assert.ok(createdUser.emailVerificationToken);

  // Resend cooldown enforced within 60s
  const cooldownRes = await agent
    .post("/api/auth/resend-code")
    .send({ email: primaryEmail });
  assert.equal(cooldownRes.status, 429);
  assert.ok(cooldownRes.body.retryAfter >= 1);

  // Bypass cooldown window in DB and resend code
  createdUser.emailVerificationLastSentAt = new Date(Date.now() - 65 * 1000);
  await createdUser.save();

  const resendRes = await agent
    .post("/api/auth/resend-code")
    .send({ email: primaryEmail });
  assert.equal(resendRes.status, 200);
  assert.equal(resendRes.body.success, true);

  // Set deterministic known OTP hash to verify exact OTP validation behavior
  const knownOtp = "654321";
  await User.updateOne(
    { _id: createdUser._id },
    {
      $set: {
        emailVerificationToken: hashToken(knownOtp),
        emailVerificationExpires: new Date(Date.now() + 5 * 60 * 1000),
      },
    }
  );

  // Wrong OTP rejected
  const wrongOtpRes = await agent
    .post("/api/auth/register/verify")
    .send({ email: primaryEmail, code: "000000" });
  assert.equal(wrongOtpRes.status, 400);

  // Correct OTP accepted and setup cookie issued
  const verifyRes = await agent
    .post("/api/auth/register/verify")
    .send({ email: primaryEmail, code: ` ${knownOtp} ` });
  assert.equal(verifyRes.status, 200);
  assert.equal(verifyRes.body.success, true);

  // Password setup session check
  const setupStatusRes = await agent.get("/api/auth/register/password-setup");
  assert.equal(setupStatusRes.status, 200);
  assert.equal(setupStatusRes.body.user.email, primaryEmail);
  assert.equal(setupStatusRes.body.user.name, "Alex Mercer");

  // Missing setup cookie returns 401
  const missingSetupCookieRes = await request(app)
    .post("/api/auth/register/create-password")
    .send({ password: validPassword });
  assert.equal(missingSetupCookieRes.status, 401);

  // Valid password completes account creation
  const createPwRes = await agent
    .post("/api/auth/register/create-password")
    .send({ password: validPassword });
  assert.equal(createPwRes.status, 201);
  assert.equal(createPwRes.body.success, true);
  assert.equal(createPwRes.body.user.isEmailVerified, true);

  // Wrong password login rejected
  const badLoginRes = await agent
    .post("/api/auth/login")
    .send({ email: primaryEmail, password: "WrongPassword!1" });
  assert.equal(badLoginRes.status, 401);

  // Correct login issues 24-hour auth cookie
  const savedExpires = process.env.JWT_EXPIRES_IN;
  delete process.env.JWT_EXPIRES_IN;
  const loginRes = await agent
    .post("/api/auth/login")
    .send({ email: primaryEmail, password: validPassword });
  if (savedExpires !== undefined) {
    process.env.JWT_EXPIRES_IN = savedExpires;
  }

  assert.equal(loginRes.status, 200);
  assert.equal(loginRes.body.success, true);
  assert.equal(loginRes.body.user.email, primaryEmail);
  assert.equal(loginRes.body.user.name, "Alex Mercer");
  assert.equal(loginRes.body.user.isEmailVerified, true);

  const setCookieHeader = loginRes.headers["set-cookie"]?.join("; ") || "";
  assert.match(setCookieHeader, /wifivision_token=/);
  assert.match(setCookieHeader, /Max-Age=86400/);

  // GET /api/auth/me returns real MongoDB user details
  const meRes = await agent.get("/api/auth/me");
  assert.equal(meRes.status, 200);
  assert.equal(meRes.body.success, true);
  assert.equal(meRes.body.user.id, String(createdUser._id));
  assert.equal(meRes.body.user.name, "Alex Mercer");
  assert.equal(meRes.body.user.email, primaryEmail);
  assert.equal(meRes.body.user.isEmailVerified, true);

  // Duplicate registration on verified email rejected cleanly
  const dupStartRes = await request(app)
    .post("/api/auth/register/start")
    .send({ name: "Alex Mercer", email: primaryEmail });
  assert.equal(dupStartRes.status, 409);

  // Logout revokes session cookie
  const logoutRes = await agent.post("/api/auth/logout");
  assert.equal(logoutRes.status, 200);
  assert.equal(logoutRes.body.success, true);

  const postLogoutMeRes = await agent.get("/api/auth/me");
  assert.equal(postLogoutMeRes.status, 401);
});
