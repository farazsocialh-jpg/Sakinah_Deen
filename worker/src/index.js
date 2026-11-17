/**
 * ImanCore AI Scholar proxy (Cloudflare Worker).
 * The deployed route/name stays sakinah-ai-proxy so existing app builds keep working.
 *
 * The Android/iOS apps POST a Gemini-shaped request body here and read the SSE stream back.
 * The upstream provider credential lives only in this Worker's environment
 * (`GEMINI_API_KEY` secret), so no key is ever compiled into the mobile binaries.
 *
 * Security posture
 *  - Optional shared client token (`CLIENT_TOKEN` secret) — constant-time compared.
 *  - Request body, prompt and output-token caps so one client cannot drain the quota.
 *  - Per-client (IP + token) sliding-window rate limit. This is per-isolate, best effort:
 *    put Cloudflare Rate Limiting / WAF rules in front of it for a hard global guarantee.
 *  - Structured `{ error: { code, message, retryAfter? } }` responses only. Upstream error
 *    bodies and stack traces are never relayed to the client.
 *  - CORS origin is locked to `ALLOWED_ORIGIN` when that variable is set.
 *
 * Deploy:
 *   cd worker
 *   npx wrangler secret put GEMINI_API_KEY     # never commit the value
 *   npx wrangler deploy
 *
 * Then point the app at the Worker URL with AI_PROXY_URL (see .env.example).
 */

const MAX_BODY_BYTES = 256 * 1024;
const MAX_PROMPT_CHARS = 16_000;
const MAX_CONTENTS = 40;
const MAX_OUTPUT_TOKENS = 2048;
const DEFAULT_MODEL = "gemini-2.0-flash";
const DEFAULT_RATE_LIMIT_PER_MINUTE = 20;
const RATE_WINDOW_MS = 60_000;

/** clientKey -> { count, resetAt } — per isolate, reset on eviction. */
const rateBuckets = new Map();

const ERROR_STATUS = {
  METHOD_NOT_ALLOWED: 405,
  UNAUTHORIZED: 401,
  NOT_CONFIGURED: 503,
  BODY_TOO_LARGE: 413,
  INVALID_JSON: 400,
  INVALID_REQUEST: 400,
  PROMPT_TOO_LARGE: 413,
  RATE_LIMITED: 429,
  UPSTREAM_RATE_LIMITED: 429,
  UPSTREAM_UNAVAILABLE: 502,
  UPSTREAM_ERROR: 502,
};

function corsHeaders(env) {
  const allowed = env && env.ALLOWED_ORIGIN ? env.ALLOWED_ORIGIN : "*";
  return {
    "access-control-allow-origin": allowed,
    "access-control-allow-methods": "POST, OPTIONS",
    "access-control-allow-headers": "content-type, x-sakinah-client",
    "access-control-max-age": "86400",
    vary: "origin",
  };
}

function withHeaders(response, env) {
  const headers = new Headers(response.headers);
  for (const [key, value] of Object.entries(corsHeaders(env))) headers.set(key, value);
  headers.set("x-content-type-options", "nosniff");
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

/** Structured, stable error envelope. Never includes upstream detail or a stack trace. */
function errorResponse(code, message, env, extraHeaders) {
  const status = ERROR_STATUS[code] || 400;
  const headers = {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    ...(extraHeaders || {}),
  };
  return withHeaders(
    new Response(JSON.stringify({ error: { code, message } }), { status, headers }),
    env
  );
}

/** Constant-time-ish comparison so token checks do not leak prefix matches. */
function tokensMatch(provided, expected) {
  if (typeof provided !== "string" || typeof expected !== "string") return false;
  if (provided.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < provided.length; i += 1) {
    diff |= provided.charCodeAt(i) ^ expected.charCodeAt(i);
  }
  return diff === 0;
}

function clientKey(request) {
  const ip =
    request.headers.get("cf-connecting-ip") ||
    request.headers.get("x-forwarded-for") ||
    "unknown";
  const token = request.headers.get("x-sakinah-client") || "";
  return `${ip}|${token.slice(0, 12)}`;
}

/** Returns { allowed, retryAfter } for the current minute window. */
function checkRateLimit(request, env, now) {
  const limit = Number(env.RATE_LIMIT_PER_MINUTE) || DEFAULT_RATE_LIMIT_PER_MINUTE;
  const key = clientKey(request);
  const bucket = rateBuckets.get(key);
  if (!bucket || bucket.resetAt <= now) {
    rateBuckets.set(key, { count: 1, resetAt: now + RATE_WINDOW_MS });
    // Opportunistic cleanup so the map cannot grow without bound in a long-lived isolate.
    if (rateBuckets.size > 5000) {
      for (const [k, v] of rateBuckets) {
        if (v.resetAt <= now) rateBuckets.delete(k);
      }
    }
    return { allowed: true, retryAfter: 0 };
  }
  if (bucket.count >= limit) {
    return { allowed: false, retryAfter: Math.max(1, Math.ceil((bucket.resetAt - now) / 1000)) };
  }
  bucket.count += 1;
  return { allowed: true, retryAfter: 0 };
}

/** Validates and normalises the Gemini-shaped payload; returns an error code or null. */
function validatePayload(payload) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return "INVALID_REQUEST";
  }
  if (!Array.isArray(payload.contents) || payload.contents.length === 0) {
    return "INVALID_REQUEST";
  }
  if (payload.contents.length > MAX_CONTENTS) {
    return "PROMPT_TOO_LARGE";
  }
  let totalChars = 0;
  for (const content of payload.contents) {
    if (!content || typeof content !== "object") return "INVALID_REQUEST";
    const parts = content.parts;
    if (!Array.isArray(parts) || parts.length === 0) return "INVALID_REQUEST";
    for (const part of parts) {
      if (!part || typeof part.text !== "string") return "INVALID_REQUEST";
      totalChars += part.text.length;
    }
  }
  if (totalChars > MAX_PROMPT_CHARS) return "PROMPT_TOO_LARGE";
  return null;
}

/** Cost control: clamp the generation budget instead of trusting the client. */
function clampGenerationConfig(payload) {
  const config =
    payload.generationConfig && typeof payload.generationConfig === "object"
      ? payload.generationConfig
      : {};
  const requested = Number(config.maxOutputTokens);
  const capped = Number.isFinite(requested) && requested > 0
    ? Math.min(requested, MAX_OUTPUT_TOKENS)
    : MAX_OUTPUT_TOKENS;
  return {
    ...payload,
    generationConfig: { ...config, maxOutputTokens: capped },
  };
}

export default {
  async fetch(request, env) {
    const now = Date.now();
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return withHeaders(new Response(null, { status: 204 }), env);
    }
    if (request.method !== "POST") {
      return errorResponse("METHOD_NOT_ALLOWED", "Only POST is supported.", env);
    }
    if (url.pathname !== "/" && url.pathname !== "") {
      return errorResponse("INVALID_REQUEST", "Unknown endpoint.", env);
    }
    if (env.CLIENT_TOKEN && !tokensMatch(request.headers.get("x-sakinah-client"), env.CLIENT_TOKEN)) {
      return errorResponse("UNAUTHORIZED", "A valid client token is required.", env);
    }
    if (!env.GEMINI_API_KEY) {
      return errorResponse(
        "NOT_CONFIGURED",
        "The proxy is not configured (missing GEMINI_API_KEY).",
        env
      );
    }

    const rate = checkRateLimit(request, env, now);
    if (!rate.allowed) {
      return errorResponse(
        "RATE_LIMITED",
        "Too many requests. Please retry shortly.",
        env,
        { "retry-after": String(rate.retryAfter) }
      );
    }

    // Reject oversized bodies before buffering them.
    const declaredLength = Number(request.headers.get("content-length") || "0");
    if (declaredLength > MAX_BODY_BYTES) {
      return errorResponse("BODY_TOO_LARGE", "Request body is too large.", env);
    }
    const body = await request.text();
    if (body.length > MAX_BODY_BYTES) {
      return errorResponse("BODY_TOO_LARGE", "Request body is too large.", env);
    }

    let payload;
    try {
      payload = JSON.parse(body);
    } catch {
      return errorResponse("INVALID_JSON", "Request body must be JSON.", env);
    }

    const invalid = validatePayload(payload);
    if (invalid === "INVALID_REQUEST") {
      return errorResponse(
        invalid,
        "Request body must contain Gemini 'contents' with text parts.",
        env
      );
    }
    if (invalid === "PROMPT_TOO_LARGE") {
      return errorResponse(
        invalid,
        `Prompt is too long (limit ${MAX_PROMPT_CHARS} characters or ${MAX_CONTENTS} turns).`,
        env
      );
    }

    const model = env.GEMINI_MODEL || DEFAULT_MODEL;
    const upstreamUrl =
      `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}` +
      ":streamGenerateContent?alt=sse";

    let upstream;
    try {
      upstream = await fetch(upstreamUrl, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          // Sent as a header rather than a query parameter so the key never lands in logs.
          "x-goog-api-key": env.GEMINI_API_KEY,
        },
        body: JSON.stringify(clampGenerationConfig(payload)),
      });
    } catch {
      return errorResponse("UPSTREAM_UNAVAILABLE", "The model provider could not be reached.", env);
    }

    if (!upstream.ok) {
      // Log only the status: the provider body can contain prompt fragments or key hints.
      const retryAfter = upstream.headers.get("retry-after");
      const code = upstream.status === 429 ? "UPSTREAM_RATE_LIMITED" : "UPSTREAM_ERROR";
      return errorResponse(
        code,
        "The model provider rejected the request.",
        env,
        retryAfter ? { "retry-after": retryAfter } : undefined
      );
    }

    return withHeaders(
      new Response(upstream.body, {
        status: 200,
        headers: {
          "content-type": "text/event-stream; charset=utf-8",
          "cache-control": "no-store",
        },
      }),
      env
    );
  },
};

// Exported for the Node-based smoke test in worker/test/proxy.test.mjs.
export { checkRateLimit, validatePayload, clampGenerationConfig, tokensMatch, MAX_PROMPT_CHARS };
