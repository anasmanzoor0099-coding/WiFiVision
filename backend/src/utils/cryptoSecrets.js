import crypto from "node:crypto";

const MIN_SECRET_LENGTH = 32;
const UNAVAILABLE_BSSID = "02:00:00:00:00:00";
const BSSID_MAC_REGEX = /^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/;

function resolveServerSecret(envVarName, devFallbackLabel) {
  const value = process.env[envVarName];
  if (typeof value === "string" && value.trim().length >= MIN_SECRET_LENGTH) {
    return value.trim();
  }

  if (process.env.NODE_ENV === "production") {
    throw new Error(
      `${envVarName} must be configured with at least ${MIN_SECRET_LENGTH} characters in production`
    );
  }

  // Deterministic non-production derivation scoped strictly to dev/test environments
  const base =
    process.env.JWT_SECRET && process.env.JWT_SECRET.length >= 16
      ? process.env.JWT_SECRET
      : "wifivision_nonprod_dev_seed_only_do_not_use_in_prod";
  return crypto
    .createHmac("sha256", base)
    .update(devFallbackLabel)
    .digest("hex");
}

export function getBssidHmacSecret() {
  return resolveServerSecret("BSSID_HMAC_SECRET", "wifivision:bssid:dev");
}

export function getDeviceCredentialPepper() {
  return resolveServerSecret("DEVICE_TOKEN_PEPPER", "wifivision:device:dev");
}

export function normalizeBssid(rawBssid) {
  if (typeof rawBssid !== "string") return null;
  const cleaned = rawBssid.trim().toUpperCase();
  if (!cleaned || cleaned === UNAVAILABLE_BSSID) return null;
  if (!BSSID_MAC_REGEX.test(cleaned)) return null;
  return cleaned;
}

export function hashBssid(rawBssid) {
  const normalized = normalizeBssid(rawBssid);
  if (!normalized) return null;
  return crypto
    .createHmac("sha256", getBssidHmacSecret())
    .update(normalized)
    .digest("hex");
}

export function hashPairingCode(code) {
  const normalized = String(code || "")
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, "");
  return crypto
    .createHmac("sha256", getDeviceCredentialPepper())
    .update(`pair:${normalized}`)
    .digest("hex");
}

export function hashDeviceSecret(secret) {
  return crypto
    .createHmac("sha256", getDeviceCredentialPepper())
    .update(`device:${String(secret || "")}`)
    .digest("hex");
}

export function safeTimingEqualHex(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const bufA = Buffer.from(a, "utf8");
  const bufB = Buffer.from(b, "utf8");
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}
