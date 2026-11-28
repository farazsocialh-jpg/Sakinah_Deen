/**
 * ImanCore AI Scholar proxy (Cloudflare Worker).
 * The deployed route/name is sakinahdeen (custom domain ai.insightsltd.com).
 *
 * The Android/iOS apps POST a Gemini-shaped request body here and read the SSE stream back.
 * The upstream provider credential lives only in this Worker's environment
 * (`GEMINI_API_KEY` secret), so no key is ever compiled into the mobile binaries.
 *
 * Security posture
 *  - Optional shared client token (`CLIENT_TOKEN` secret), compared in constant time over
 *    HMAC-SHA-256 digests so neither content nor length is leaked by timing.
 *  - Request body, prompt and output-token caps so one client cannot drain the quota.
 *  - Rate limiting uses Cloudflare's native Rate Limiting bindings (`RATE_LIMITER` for the
 *    proxy, `NOTIFY_RATE_LIMITER` for /notify). Counters are kept by Cloudflare, not in
 *    isolate memory, so limits hold across isolates. Limits are approximate per Cloudflare
 *    location and are configured in wrangler.toml.
 *  - CORS is closed by default: only origins listed in `ALLOWED_ORIGIN` (comma separated)
 *    receive CORS headers. With it unset no browser origin is allowed at all; native
 *    mobile clients send no Origin header and are unaffected. `*` is never emitted.
 *  - Structured `{ error: { code, message, retryAfter? } }` responses only. Upstream error
 *    bodies and stack traces are never relayed to the client.
 *
 * Deploy:
 *   cd worker
 *   npx wrangler secret put GEMINI_API_KEY     # never commit the value
 *   npx wrangler secret put ADMIN_NOTIFY_TOKEN  # protects /notify
 *   npx wrangler secret put FCM_SERVICE_ACCOUNT_JSON  # Firebase service account JSON
 *   npx wrangler deploy
 *
 * Then point the app at the Worker URL with AI_PROXY_URL (see .env.example).
 */

const MAX_BODY_BYTES = 256 * 1024;
const MAX_NOTIFY_BODY_BYTES = 16 * 1024;
const MAX_PROMPT_CHARS = 16_000;
const MAX_CONTENTS = 40;
const MAX_OUTPUT_TOKENS = 2048;
// Gemini 2.0 Flash was shut down on 2026-06-01. 3.6 Flash is the supported
// generateContent/streamGenerateContent replacement; keep wrangler.toml in sync.
const DEFAULT_MODEL = "gemini-3.6-flash";
const RATE_LIMIT_WINDOW_SECONDS = 60;

const ERROR_STATUS = {
  METHOD_NOT_ALLOWED: 405,
  ORIGIN_NOT_ALLOWED: 403,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_CONFIGURED: 503,
  ADMIN_NOT_CONFIGURED: 500,
  FCM_NOT_CONFIGURED: 500,
  BODY_TOO_LARGE: 413,
  INVALID_JSON: 400,
  INVALID_REQUEST: 400,
  PROMPT_TOO_LARGE: 413,
  RATE_LIMITED: 429,
  UPSTREAM_RATE_LIMITED: 429,
  UPSTREAM_UNAVAILABLE: 502,
  UPSTREAM_ERROR: 502,
  FCM_ERROR: 502,
};

/* ------------------------------------------------------------------ */
/* CORS — explicit allowlist, no wildcard                              */
/* ------------------------------------------------------------------ */

/** `ALLOWED_ORIGIN` is a comma-separated allowlist; unset means "no browser origins". */
function allowedOrigins(env) {
  const raw = env && typeof env.ALLOWED_ORIGIN === "string" ? env.ALLOWED_ORIGIN : "";
  return raw
    .split(",")
    .map((origin) => origin.trim())
    .filter((origin) => origin !== "" && origin !== "*");
}

/** Native mobile clients send no Origin header; browsers always do. */
function isOriginAllowed(origin, env) {
  if (!origin) return true;
  return allowedOrigins(env).includes(origin);
}

/**
 * CORS headers for one request. The ACAO value is only ever the exact, allowlisted request
 * origin — never `*`, and never a value supplied by the caller when it is not allowlisted.
 */
function corsHeaders(env, origin) {
  const headers = {
    vary: "origin",
    "access-control-allow-methods": "POST, OPTIONS",
    "access-control-allow-headers": "content-type, x-sakinah-client, x-installation-id, x-admin-token",
    "access-control-max-age": "600",
  };
  if (origin && isOriginAllowed(origin, env)) {
    headers["access-control-allow-origin"] = origin;
  }
  return headers;
}

function withHeaders(response, env, origin) {
  const headers = new Headers(response.headers);
  for (const [key, value] of Object.entries(corsHeaders(env, origin))) headers.set(key, value);
  headers.set("x-content-type-options", "nosniff");
  headers.set("referrer-policy", "no-referrer");
  headers.set("x-frame-options", "DENY");
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

function jsonResponse(body, status) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

/** Structured, stable error envelope. Never includes upstream detail or a stack trace. */
function errorResponse(code, message, extraHeaders) {
  const status = ERROR_STATUS[code] || 400;
  const response = jsonResponse({ error: { code, message } }, status);
  if (extraHeaders) {
    const headers = new Headers(response.headers);
    for (const [key, value] of Object.entries(extraHeaders)) headers.set(key, value);
    return new Response(response.body, { status, headers });
  }
  return response;
}

/* ------------------------------------------------------------------ */
/* Constant-time secret comparison                                     */
/* ------------------------------------------------------------------ */

let hmacKeyPromise;

/**
 * Per-isolate random HMAC key. Both sides of a comparison are digested with it, so equal
 * inputs always compare equal while the digests reveal nothing about the secret. The compare
 * loop then always runs over a fixed 32 bytes, independent of input length or content.
 */
function hmacKey() {
  if (!hmacKeyPromise) {
    hmacKeyPromise = crypto.subtle.importKey(
      "raw",
      crypto.getRandomValues(new Uint8Array(32)),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"]
    );
  }
  return hmacKeyPromise;
}

async function digest(value) {
  const key = await hmacKey();
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value));
  return new Uint8Array(signature);
}

/** Constant-time comparison: no early exit, no length-dependent loop bounds. */
async function tokensMatch(provided, expected) {
  if (typeof provided !== "string" || typeof expected !== "string" || expected === "") {
    return false;
  }
  const [a, b] = await Promise.all([digest(provided), digest(expected)]);
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) {
    diff |= a[i] ^ b[i];
  }
  return diff === 0;
}

/* ------------------------------------------------------------------ */
/* Rate limiting — Cloudflare native bindings                          */
/* ------------------------------------------------------------------ */

function clientIp(request) {
  return (
    request.headers.get("cf-connecting-ip") ||
    request.headers.get("x-forwarded-for") ||
    "unknown"
  );
}

const INSTALLATION_ID_HEADER = "x-installation-id";
const MAX_INSTALLATION_ID_LENGTH = 64;

/**
 * App-generated, per-installation id. It is opaque and non-secret: never derive it from
 * CLIENT_TOKEN, whose shared prefix would make every installation compete for one budget.
 */
function installationId(request) {
  const raw = request.headers.get(INSTALLATION_ID_HEADER) || "";
  const id = raw.trim();
  if (id.length === 0 || id.length > MAX_INSTALLATION_ID_LENGTH) return "";
  // Keep the rate-limit key bounded and free of control characters / header injection.
  return /^[A-Za-z0-9._:-]+$/.test(id) ? id : "";
}

/** Anonymous proxy: IP plus a per-installation id keeps one install from exhausting a shared IP. */
function clientKey(request) {
  const id = installationId(request);
  return id ? `${clientIp(request)}|install:${id}` : `${clientIp(request)}|ip`;
}

/** Minimal structured logging: status/codes only, never bodies, prompts, tokens or keys. */
function logStructured(level, event, status) {
  console[level](JSON.stringify({ event, status }));
}

/**
 * Calls a Cloudflare Rate Limiting binding. A missing or failing binding fails open so a
 * binding problem cannot take the app offline; the binding itself is declared in wrangler.toml
 * and every deploy attaches it.
 */
async function hitRateLimit(binding, key) {
  if (!binding || typeof binding.limit !== "function") return true;
  try {
    const { success } = await binding.limit({ key });
    return success !== false;
  } catch {
    return true;
  }
}

/** Returns { allowed, retryAfter } for the proxy route. */
async function checkRateLimit(request, env) {
  const allowed = await hitRateLimit(env.RATE_LIMITER, `proxy:${clientKey(request)}`);
  return { allowed, retryAfter: allowed ? 0 : RATE_LIMIT_WINDOW_SECONDS };
}

/** Returns { allowed, retryAfter } for the admin push route. */
async function checkNotifyRateLimit(request, env) {
  const allowed = await hitRateLimit(env.NOTIFY_RATE_LIMITER, `notify:${clientIp(request)}`);
  return { allowed, retryAfter: allowed ? 0 : RATE_LIMIT_WINDOW_SECONDS };
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

/**
 * Cost control plus Gemini 3.x compatibility: clamp the generation budget and
 * strip sampling parameters that 3.x ignores today and rejects in future
 * model generations. A low thinking level keeps scholar answers quick.
 */
function clampGenerationConfig(payload) {
  const config =
    payload.generationConfig && typeof payload.generationConfig === "object"
      ? payload.generationConfig
      : {};
  const requested = Number(config.maxOutputTokens);
  const capped = Number.isFinite(requested) && requested > 0
    ? Math.min(requested, MAX_OUTPUT_TOKENS)
    : MAX_OUTPUT_TOKENS;

  const safeConfig = { ...config };
  for (const key of [
    "temperature", "topP", "top_p", "topK", "top_k",
    "candidateCount", "candidate_count", "thinkingBudget", "thinking_budget",
  ]) {
    delete safeConfig[key];
  }
  safeConfig.thinkingConfig = {
    ...(safeConfig.thinkingConfig && typeof safeConfig.thinkingConfig === "object"
      ? safeConfig.thinkingConfig
      : {}),
    thinkingLevel: "low",
  };
  safeConfig.maxOutputTokens = capped;

  return { ...payload, generationConfig: safeConfig };
}

/* ------------------------------------------------------------------ */
/* FCM HTTP v1 helpers (Web Crypto only, no external dependencies)      */
/* ------------------------------------------------------------------ */

function base64UrlEncode(input) {
  const bytes = input instanceof Uint8Array ? input : new Uint8Array(input);
  let binary = "";
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToArrayBuffer(pem) {
  const base64 = String(pem)
    .replace(/-----BEGIN [A-Z ]+-----/g, "")
    .replace(/-----END [A-Z ]+-----/g, "")
    .replace(/\s+/g, "");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

async function createFcmJwt(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const encoder = new TextEncoder();
  const signingInput = `${base64UrlEncode(encoder.encode(JSON.stringify(header)))}.${base64UrlEncode(
    encoder.encode(JSON.stringify(payload))
  )}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    encoder.encode(signingInput)
  );
  return `${signingInput}.${base64UrlEncode(signature)}`;
}

async function getFcmAccessToken(serviceAccount) {
  const jwt = await createFcmJwt(serviceAccount);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }).toString(),
  });
  if (!response.ok) {
    logStructured("warn", "fcm_oauth_error", response.status);
    throw new Error("oauth token exchange failed");
  }
  const data = await response.json();
  if (!data || typeof data.access_token !== "string") {
    throw new Error("oauth token missing");
  }
  return data.access_token;
}

async function sendFcmNotification(env, { title, body, topic, data, dataOnly }) {
  let serviceAccount;
  try {
    serviceAccount = JSON.parse(env.FCM_SERVICE_ACCOUNT_JSON);
  } catch {
    throw new Error("invalid service account json");
  }
  if (
    !serviceAccount ||
    typeof serviceAccount.client_email !== "string" ||
    typeof serviceAccount.private_key !== "string" ||
    typeof serviceAccount.project_id !== "string"
  ) {
    throw new Error("invalid service account");
  }

  const accessToken = await getFcmAccessToken(serviceAccount);
  const payloadData = data && typeof data === "object" && !Array.isArray(data) ? data : {};
  const mergedData = { ...payloadData, title, body };
  const messageBody = dataOnly === true
    ? {
        message: {
          topic,
          data: mergedData,
          android: { priority: "high" },
        },
      }
    : {
        message: {
          topic,
          notification: { title, body },
          data: payloadData,
        },
      };
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(serviceAccount.project_id)}/messages:send`,
    {
      method: "POST",
      headers: {
        authorization: `Bearer ${accessToken}`,
        "content-type": "application/json",
      },
      body: JSON.stringify(messageBody),
    }
  );
  const result = await response.json().catch(() => ({}));
  if (!response.ok) {
    logStructured("warn", "fcm_send_error", response.status);
    throw new Error("fcm send failed");
  }
  const messageId = result && typeof result.name === "string" ? result.name : undefined;
  return { ok: true, messageId };
}

/* ------------------------------------------------------------------ */
/* Routes                                                              */
/* ------------------------------------------------------------------ */

async function handleNotify(request, env) {
  if (env.ADMIN_NOTIFY_TOKEN === undefined || env.ADMIN_NOTIFY_TOKEN === "") {
    return errorResponse(
      "ADMIN_NOT_CONFIGURED",
      "The notify endpoint is not configured (missing ADMIN_NOTIFY_TOKEN)."
    );
  }
  if (!env.FCM_SERVICE_ACCOUNT_JSON) {
    return errorResponse(
      "FCM_NOT_CONFIGURED",
      "The notify endpoint is not configured (missing FCM_SERVICE_ACCOUNT_JSON)."
    );
  }

  // Rate limited before the token is compared so the endpoint cannot be used as a
  // token-guessing oracle at full request rate.
  const rate = await checkNotifyRateLimit(request, env);
  if (!rate.allowed) {
    logStructured("warn", "rate_limited_notify", 429);
    return errorResponse("RATE_LIMITED", "Too many requests. Please retry shortly.", {
      "retry-after": String(rate.retryAfter),
    });
  }

  const providedToken = request.headers.get("x-admin-token");
  if (!providedToken) {
    return errorResponse("UNAUTHORIZED", "An admin token is required.");
  }
  if (!(await tokensMatch(providedToken, env.ADMIN_NOTIFY_TOKEN))) {
    return errorResponse("FORBIDDEN", "Invalid admin token.");
  }

  const declaredLength = Number(request.headers.get("content-length") || "0");
  if (declaredLength > MAX_NOTIFY_BODY_BYTES) {
    return errorResponse("BODY_TOO_LARGE", "Request body is too large.");
  }

  let bodyText;
  try {
    bodyText = await request.text();
  } catch {
    return errorResponse("INVALID_JSON", "Request body must be JSON.");
  }
  if (new TextEncoder().encode(bodyText).byteLength > MAX_NOTIFY_BODY_BYTES) {
    return errorResponse("BODY_TOO_LARGE", "Request body is too large.");
  }
  let payload;
  try {
    payload = JSON.parse(bodyText);
  } catch {
    return errorResponse("INVALID_JSON", "Request body must be JSON.");
  }
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return errorResponse("INVALID_REQUEST", "Request body must be a JSON object.");
  }
  if (typeof payload.title !== "string" || payload.title.trim() === "") {
    return errorResponse("INVALID_REQUEST", "title must be a non-empty string.");
  }
  if (typeof payload.body !== "string" || payload.body.trim() === "") {
    return errorResponse("INVALID_REQUEST", "body must be a non-empty string.");
  }
  const topic =
    typeof payload.topic === "string" && payload.topic.trim() !== ""
      ? payload.topic.trim()
      : "daily_reminders";
  const data =
    payload.data && typeof payload.data === "object" && !Array.isArray(payload.data)
      ? payload.data
      : {};
  const dataOnly = payload.dataOnly === true;
  if (payload.dataOnly !== undefined && typeof payload.dataOnly !== "boolean") {
    return errorResponse("INVALID_REQUEST", "dataOnly must be a boolean.");
  }

  try {
    const result = await sendFcmNotification(env, {
      title: payload.title,
      body: payload.body,
      topic,
      data,
      dataOnly,
    });
    return jsonResponse(result, 200);
  } catch {
    logStructured("error", "fcm_failure", 502);
    return errorResponse("FCM_ERROR", "Failed to send notification.");
  }
}

async function handleScholarProxy(request, env) {
  if (env.CLIENT_TOKEN && !(await tokensMatch(request.headers.get("x-sakinah-client"), env.CLIENT_TOKEN))) {
    return errorResponse("UNAUTHORIZED", "A valid client token is required.");
  }
  if (!env.GEMINI_API_KEY) {
    return errorResponse(
      "NOT_CONFIGURED",
      "The proxy is not configured (missing GEMINI_API_KEY)."
    );
  }

  const rate = await checkRateLimit(request, env);
  if (!rate.allowed) {
    logStructured("warn", "rate_limited_proxy", 429);
    return errorResponse("RATE_LIMITED", "Too many requests. Please retry shortly.", {
      "retry-after": String(rate.retryAfter),
    });
  }

  // Reject oversized bodies before buffering them.
  const declaredLength = Number(request.headers.get("content-length") || "0");
  if (declaredLength > MAX_BODY_BYTES) {
    return errorResponse("BODY_TOO_LARGE", "Request body is too large.");
  }
  const body = await request.text();
  if (new TextEncoder().encode(body).byteLength > MAX_BODY_BYTES) {
    return errorResponse("BODY_TOO_LARGE", "Request body is too large.");
  }

  let payload;
  try {
    payload = JSON.parse(body);
  } catch {
    return errorResponse("INVALID_JSON", "Request body must be JSON.");
  }

  const invalid = validatePayload(payload);
  if (invalid === "INVALID_REQUEST") {
    return errorResponse(
      invalid,
      "Request body must contain Gemini 'contents' with text parts."
    );
  }
  if (invalid === "PROMPT_TOO_LARGE") {
    return errorResponse(
      invalid,
      `Prompt is too long (limit ${MAX_PROMPT_CHARS} characters or ${MAX_CONTENTS} turns).`
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
    logStructured("warn", "upstream_unreachable", 0);
    return errorResponse("UPSTREAM_UNAVAILABLE", "The model provider could not be reached.");
  }

  if (!upstream.ok) {
    // Log only the status: the provider body can contain prompt fragments or key hints.
    const retryAfter = upstream.headers.get("retry-after");
    const code = upstream.status === 429 ? "UPSTREAM_RATE_LIMITED" : "UPSTREAM_ERROR";
    logStructured("warn", "upstream_http_error", upstream.status);
    return errorResponse(
      code,
      "The model provider rejected the request.",
      retryAfter ? { "retry-after": retryAfter } : undefined
    );
  }

  return new Response(upstream.body, {
    status: 200,
    headers: {
      "content-type": "text/event-stream; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

async function route(request, env) {
  const url = new URL(request.url);

  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204 });
  }
  if (request.method !== "POST") {
    return errorResponse("METHOD_NOT_ALLOWED", "Only POST is supported.");
  }
  if (url.pathname === "/notify") {
    return handleNotify(request, env);
  }
  if (url.pathname !== "/" && url.pathname !== "") {
    return errorResponse("INVALID_REQUEST", "Unknown endpoint.");
  }
  return handleScholarProxy(request, env);
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get("origin");

    // A browser origin that is not allowlisted never reaches a handler: no CORS headers are
    // attached to the rejection, so the browser also cannot read the response.
    if (!isOriginAllowed(origin, env)) {
      return withHeaders(
        errorResponse("ORIGIN_NOT_ALLOWED", "Origin not allowed."),
        env,
        origin
      );
    }

    const response = await route(request, env);
    return withHeaders(response, env, origin);
  },
};

// Exported for the Node-based smoke test in worker/test/proxy.test.mjs.
export {
  allowedOrigins,
  checkNotifyRateLimit,
  checkRateLimit,
  clientKey,
  clampGenerationConfig,
  installationId,
  isOriginAllowed,
  tokensMatch,
  validatePayload,
  MAX_BODY_BYTES,
  MAX_PROMPT_CHARS,
};
