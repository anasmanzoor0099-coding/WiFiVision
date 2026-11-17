import crypto from "node:crypto";
import jwt from "jsonwebtoken";

export function generateVerificationCode() {
  return crypto.randomInt(100000, 1000000).toString();
}

export function hashToken(token) {
  return crypto
    .createHash("sha256")
    .update(token)
    .digest("hex");
}

const DEFAULT_JWT_EXPIRES_IN = "24h";
const DEFAULT_COOKIE_MAX_AGE_MS = 24 * 60 * 60 * 1000;

export function parseExpiresInMs(expiresIn = process.env.JWT_EXPIRES_IN) {
  if (typeof expiresIn === "number" && Number.isFinite(expiresIn) && expiresIn > 0) {
    return expiresIn * 1000;
  }

  if (typeof expiresIn !== "string") {
    return DEFAULT_COOKIE_MAX_AGE_MS;
  }

  const trimmed = expiresIn.trim();
  if (!trimmed) {
    return DEFAULT_COOKIE_MAX_AGE_MS;
  }

  if (/^\d+$/.test(trimmed)) {
    const seconds = Number(trimmed);
    return seconds > 0 ? seconds * 1000 : DEFAULT_COOKIE_MAX_AGE_MS;
  }

  const match = trimmed.match(/^(\d+(?:\.\d+)?)\s*(ms|s|m|h|d|w|y)$/i);
  if (!match) {
    return DEFAULT_COOKIE_MAX_AGE_MS;
  }

  const value = Number(match[1]);
  const unit = match[2].toLowerCase();
  const multipliers = {
    ms: 1,
    s: 1000,
    m: 60 * 1000,
    h: 60 * 60 * 1000,
    d: 24 * 60 * 60 * 1000,
    w: 7 * 24 * 60 * 60 * 1000,
    y: 365.25 * 24 * 60 * 60 * 1000,
  };

  const ms = Math.floor(value * multipliers[unit]);
  return Number.isFinite(ms) && ms > 0 ? ms : DEFAULT_COOKIE_MAX_AGE_MS;
}

export function generateAuthToken(user) {
  if (!process.env.JWT_SECRET) {
    throw new Error("JWT_SECRET is not configured");
  }

  if (
    process.env.NODE_ENV === "production" &&
    process.env.JWT_SECRET.length < 32
  ) {
    throw new Error("JWT_SECRET must be at least 32 characters in production");
  }

  return jwt.sign(
    {
      sub: user._id.toString(),
      email: user.email,
      tokenVersion: typeof user.tokenVersion === "number" ? user.tokenVersion : 0,
    },
    process.env.JWT_SECRET,
    {
      algorithm: "HS256",
      expiresIn: process.env.JWT_EXPIRES_IN || DEFAULT_JWT_EXPIRES_IN,
      issuer: "wifivision",
      audience: "wifivision-users",
    }
  );
}

export function getAuthCookieOptions() {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    maxAge: parseExpiresInMs(process.env.JWT_EXPIRES_IN || DEFAULT_JWT_EXPIRES_IN),
  };
}