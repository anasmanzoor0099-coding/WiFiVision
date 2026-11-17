/**
 * Distributed rate limiter & replay-nonce store backed by Upstash Redis REST API
 * in production, with explicit in-memory fallback allowed only in local dev / tests.
 */

const memoryWindowStore = new Map();
const memoryNonceStore = new Map();

function pruneExpiredMemoryEntries(nowMs) {
  for (const [key, entry] of memoryWindowStore.entries()) {
    if (entry.resetAtMs <= nowMs) {
      memoryWindowStore.delete(key);
    }
  }
  for (const [key, expiresAtMs] of memoryNonceStore.entries()) {
    if (expiresAtMs <= nowMs) {
      memoryNonceStore.delete(key);
    }
  }
}

export function getUpstashConfig() {
  const url = process.env.UPSTASH_REDIS_REST_URL?.trim()?.replace(/\/+$/, "");
  const token = process.env.UPSTASH_REDIS_REST_TOKEN?.trim();
  if (!url || !token) return null;
  return { url, token };
}

export function resetInMemorySecurityStoresForTest() {
  memoryWindowStore.clear();
  memoryNonceStore.clear();
}

async function runUpstashPipeline(commands, fetcher) {
  const activeFetch = fetcher || globalThis.fetch;
  const config = getUpstashConfig();
  if (!config) {
    throw new Error("Upstash Redis configuration missing");
  }

  const response = await activeFetch(`${config.url}/pipeline`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${config.token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(commands),
    signal: AbortSignal.timeout(4000),
  });

  if (!response.ok) {
    throw new Error(`Upstash REST error HTTP ${response.status}`);
  }

  const payload = await response.json();
  if (!Array.isArray(payload)) {
    throw new Error("Unexpected Upstash REST pipeline response");
  }

  for (const item of payload) {
    if (item && typeof item === "object" && item.error) {
      throw new Error(`Upstash command error: ${item.error}`);
    }
  }

  return payload.map((item) => item?.result);
}

async function incrementUpstashWindow(key, windowMs, fetcher) {
  const [countRaw, pttlRaw] = await runUpstashPipeline(
    [
      ["INCR", key],
      ["PTTL", key],
    ],
    fetcher
  );

  const count = Number(countRaw);
  let ttlMs = Number(pttlRaw);

  if (!Number.isFinite(ttlMs) || ttlMs < 0) {
    await runUpstashPipeline([["PEXPIRE", key, String(windowMs)]], fetcher);
    ttlMs = windowMs;
  }

  return {
    count,
    resetAtMs: Date.now() + Math.max(1000, ttlMs),
  };
}

function incrementMemoryWindow(key, windowMs) {
  const nowMs = Date.now();
  if (memoryWindowStore.size > 5000) {
    pruneExpiredMemoryEntries(nowMs);
  }

  const existing = memoryWindowStore.get(key);
  if (!existing || existing.resetAtMs <= nowMs) {
    const fresh = { count: 1, resetAtMs: nowMs + windowMs };
    memoryWindowStore.set(key, fresh);
    return fresh;
  }

  existing.count += 1;
  return existing;
}

export function createDistributedLimiter({
  name,
  windowMs,
  limit,
  message = "Too many requests. Please try again later.",
  keyGenerator = (req) => req.ip || "unknown",
  fetcher,
}) {
  return async function distributedRateLimiter(req, res, next) {
    const isProduction = process.env.NODE_ENV === "production";
    const upstashConfig = getUpstashConfig();

    const identifier = String(keyGenerator(req) || "unknown");
    const bucketKey = `wv:rl:${name}:${identifier}`;

    try {
      let state;

      if (upstashConfig) {
        state = await incrementUpstashWindow(bucketKey, windowMs, fetcher);
      } else if (isProduction) {
        return res.status(503).json({
          success: false,
          message:
            "Rate limiting service is not configured. Request blocked safely.",
        });
      } else {
        state = incrementMemoryWindow(bucketKey, windowMs);
      }

      const remaining = Math.max(0, limit - state.count);
      const resetSeconds = Math.max(
        1,
        Math.ceil((state.resetAtMs - Date.now()) / 1000)
      );

      res.setHeader("RateLimit-Limit", String(limit));
      res.setHeader("RateLimit-Remaining", String(remaining));
      res.setHeader("RateLimit-Reset", String(resetSeconds));

      if (state.count > limit) {
        res.setHeader("Retry-After", String(resetSeconds));
        return res.status(429).json({
          success: false,
          message,
          retryAfter: resetSeconds,
        });
      }

      return next();
    } catch {
      if (isProduction) {
        return res.status(503).json({
          success: false,
          message:
            "Rate limiting service temporarily unavailable. Please try again shortly.",
        });
      }

      // In explicit non-production mode only, fallback safely when a test simulates outage
      const state = incrementMemoryWindow(bucketKey, windowMs);
      if (state.count > limit) {
        return res.status(429).json({ success: false, message });
      }
      return next();
    }
  };
}

/**
 * Consumes a replay nonce atomically. Returns true if fresh, false if replayed.
 * Fails closed in production if Upstash Redis is missing or unavailable.
 */
export async function consumeReplayNonce(
  nonceKey,
  ttlMs = 6 * 60 * 1000,
  fetcher
) {
  const isProduction = process.env.NODE_ENV === "production";
  const upstashConfig = getUpstashConfig();
  const fullKey = `wv:nonce:${nonceKey}`;

  if (upstashConfig) {
    const [setResult] = await runUpstashPipeline(
      [["SET", fullKey, "1", "PX", String(ttlMs), "NX"]],
      fetcher
    );
    return setResult === "OK";
  }

  if (isProduction) {
    throw new Error("Replay protection requires Upstash Redis in production");
  }

  const nowMs = Date.now();
  if (memoryNonceStore.size > 5000) {
    pruneExpiredMemoryEntries(nowMs);
  }

  const existingExpiry = memoryNonceStore.get(fullKey);
  if (existingExpiry && existingExpiry > nowMs) {
    return false;
  }

  memoryNonceStore.set(fullKey, nowMs + ttlMs);
  return true;
}
