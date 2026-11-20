import assert from "node:assert/strict";
import test from "node:test";

import gate, {
  MAX_PROMPT_CHARS,
  allowedOrigins,
  checkNotifyRateLimit,
  checkRateLimit,
  clampGenerationConfig,
  isOriginAllowed,
  tokensMatch,
  validatePayload,
} from "../src/index.js";

function request(headers = {}, path = "/") {
  return new Request(`https://proxy.example${path}`, {
    method: "POST",
    headers: { "cf-connecting-ip": "203.0.113.7", ...headers },
  });
}

/** Stand-in for Cloudflare's Rate Limiting binding: allows `limit` hits per key. */
function fakeLimiter(limit) {
  const counts = new Map();
  return {
    counts,
    async limit({ key }) {
      const used = (counts.get(key) || 0) + 1;
      counts.set(key, used);
      return { success: used <= limit };
    },
  };
}

const validPayload = {
  contents: [{ role: "user", parts: [{ text: "What is the nisab of gold?" }] }],
  generationConfig: { maxOutputTokens: 999999 },
};

test("validatePayload accepts a well-formed Gemini request", () => {
  assert.equal(validatePayload(validPayload), null);
});

test("validatePayload rejects missing contents, non-text parts and huge prompts", () => {
  assert.equal(validatePayload(null), "INVALID_REQUEST");
  assert.equal(validatePayload({}), "INVALID_REQUEST");
  assert.equal(validatePayload({ contents: [] }), "INVALID_REQUEST");
  assert.equal(validatePayload({ contents: [{ parts: [{ text: 1 }] }] }), "INVALID_REQUEST");
  assert.equal(
    validatePayload({ contents: [{ parts: [{ text: "x".repeat(MAX_PROMPT_CHARS + 1) }] }] }),
    "PROMPT_TOO_LARGE"
  );
  const manyTurns = Array.from({ length: 41 }, () => ({ parts: [{ text: "hi" }] }));
  assert.equal(validatePayload({ contents: manyTurns }), "PROMPT_TOO_LARGE");
});

test("clampGenerationConfig caps the output budget instead of trusting the client", () => {
  assert.equal(clampGenerationConfig(validPayload).generationConfig.maxOutputTokens, 2048);
  assert.equal(
    clampGenerationConfig({ contents: [], generationConfig: { maxOutputTokens: 100 } })
      .generationConfig.maxOutputTokens,
    100
  );
  assert.equal(clampGenerationConfig({ contents: [] }).generationConfig.maxOutputTokens, 2048);
});

test("tokensMatch is constant-time over digests and rejects wrong, empty and partial tokens", async () => {
  assert.equal(await tokensMatch("abc123", "abc123"), true);
  assert.equal(await tokensMatch("abc124", "abc123"), false);
  assert.equal(await tokensMatch("abc", "abc123"), false);
  assert.equal(await tokensMatch("a".repeat(10_000), "abc123"), false);
  assert.equal(await tokensMatch(undefined, "abc123"), false);
  assert.equal(await tokensMatch("abc123", ""), false);
  assert.equal(await tokensMatch("abc123", undefined), false);
});

/* ---------------------------- CORS ---------------------------- */

test("CORS defaults to no browser origin and never emits a wildcard", () => {
  assert.deepEqual(allowedOrigins({}), []);
  assert.deepEqual(allowedOrigins({ ALLOWED_ORIGIN: "*" }), []);
  assert.equal(isOriginAllowed("https://evil.example", {}), false);
  // Native clients send no Origin header and must keep working.
  assert.equal(isOriginAllowed(null, {}), true);
});

test("ALLOWED_ORIGIN is a comma-separated allowlist", () => {
  const env = { ALLOWED_ORIGIN: "https://app.example, https://admin.example" };
  assert.deepEqual(allowedOrigins(env), ["https://app.example", "https://admin.example"]);
  assert.equal(isOriginAllowed("https://app.example", env), true);
  assert.equal(isOriginAllowed("https://admin.example", env), true);
  assert.equal(isOriginAllowed("https://evil.example", env), false);
});

test("an allowlisted origin is echoed and an unknown origin is rejected", async () => {
  const env = { ALLOWED_ORIGIN: "https://app.example", GEMINI_API_KEY: "test-key" };

  const allowed = await gate.fetch(
    request({ origin: "https://app.example" }),
    // No upstream call is reached: the binding-less rate limit passes and the body is invalid.
    { ...env, GEMINI_API_KEY: "" }
  );
  assert.equal(allowed.headers.get("access-control-allow-origin"), "https://app.example");
  assert.equal(allowed.headers.get("vary"), "origin");

  const denied = await gate.fetch(request({ origin: "https://evil.example" }), env);
  assert.equal(denied.status, 403);
  assert.equal((await denied.json()).error.code, "ORIGIN_NOT_ALLOWED");
  assert.equal(denied.headers.get("access-control-allow-origin"), null);
});

test("preflight is answered only for allowlisted origins", async () => {
  const env = { ALLOWED_ORIGIN: "https://app.example" };

  const ok = await gate.fetch(
    new Request("https://proxy.example/", {
      method: "OPTIONS",
      headers: { origin: "https://app.example" },
    }),
    env
  );
  assert.equal(ok.status, 204);
  assert.equal(ok.headers.get("access-control-allow-origin"), "https://app.example");
  assert.match(ok.headers.get("access-control-allow-headers"), /x-admin-token/);

  const blocked = await gate.fetch(
    new Request("https://proxy.example/", {
      method: "OPTIONS",
      headers: { origin: "https://evil.example" },
    }),
    env
  );
  assert.equal(blocked.status, 403);
  assert.equal(blocked.headers.get("access-control-allow-origin"), null);
});

test("with no ALLOWED_ORIGIN configured, responses carry no CORS header", async () => {
  const response = await gate.fetch(request({ origin: "https://app.example" }), {});
  assert.equal(response.status, 403);
  assert.equal(response.headers.get("access-control-allow-origin"), null);
});

/* ------------------------ rate limiting ------------------------ */

test("checkRateLimit delegates to the native binding and stops at its budget", async () => {
  const env = { RATE_LIMITER: fakeLimiter(2) };
  const req = request({ "cf-connecting-ip": "198.51.100.1" });

  assert.deepEqual(await checkRateLimit(req, env), { allowed: true, retryAfter: 0 });
  assert.deepEqual(await checkRateLimit(req, env), { allowed: true, retryAfter: 0 });

  const blocked = await checkRateLimit(req, env);
  assert.equal(blocked.allowed, false);
  assert.ok(blocked.retryAfter >= 1);

  // A different client key has its own budget.
  const other = request({ "cf-connecting-ip": "198.51.100.2" });
  assert.equal((await checkRateLimit(other, env)).allowed, true);
});

test("checkNotifyRateLimit keys on the calling IP", async () => {
  const limiter = fakeLimiter(1);
  const env = { NOTIFY_RATE_LIMITER: limiter };
  const req = request({ "cf-connecting-ip": "198.51.100.9" }, "/notify");

  assert.equal((await checkNotifyRateLimit(req, env)).allowed, true);
  assert.equal((await checkNotifyRateLimit(req, env)).allowed, false);
  assert.ok([...limiter.counts.keys()].every((key) => key.startsWith("notify:")));
});

test("a missing rate limiter binding fails open instead of blocking the app", async () => {
  assert.deepEqual(await checkRateLimit(request(), {}), { allowed: true, retryAfter: 0 });
  assert.deepEqual(await checkNotifyRateLimit(request({}, "/notify"), {}), {
    allowed: true,
    retryAfter: 0,
  });
});

test("an exhausted proxy rate limit returns 429 with retry-after", async () => {
  const env = {
    GEMINI_API_KEY: "test-key",
    RATE_LIMITER: fakeLimiter(0),
  };
  const response = await gate.fetch(request(), env);
  assert.equal(response.status, 429);
  assert.equal((await response.json()).error.code, "RATE_LIMITED");
  assert.equal(response.headers.get("retry-after"), "60");
});

/* ------------------------- request gate ------------------------ */

test("missing configuration returns a structured, non-leaky error", async () => {
  const response = await gate.fetch(request(), {});
  assert.equal(response.status, 503);
  const body = await response.json();
  assert.equal(body.error.code, "NOT_CONFIGURED");
  assert.equal(typeof body.error.message, "string");
});

test("wrong client token is rejected before any upstream call", async () => {
  const response = await gate.fetch(request({ "x-sakinah-client": "nope" }), {
    CLIENT_TOKEN: "secret-token",
    GEMINI_API_KEY: "test-key",
  });
  assert.equal(response.status, 401);
  assert.equal((await response.json()).error.code, "UNAUTHORIZED");
});

test("invalid JSON is rejected with a stable code", async () => {
  const response = await gate.fetch(
    new Request("https://proxy.example/", {
      method: "POST",
      body: "{not json",
      headers: { "cf-connecting-ip": "198.51.100.10" },
    }),
    { GEMINI_API_KEY: "test-key" }
  );
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error.code, "INVALID_JSON");
});

test("a JSON body without contents is rejected", async () => {
  const response = await gate.fetch(
    new Request("https://proxy.example/", {
      method: "POST",
      body: JSON.stringify({ hello: "world" }),
      headers: { "cf-connecting-ip": "198.51.100.11" },
    }),
    { GEMINI_API_KEY: "test-key" }
  );
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error.code, "INVALID_REQUEST");
});

test("non-POST methods are rejected", async () => {
  const response = await gate.fetch(
    new Request("https://proxy.example/", { method: "GET" }),
    { GEMINI_API_KEY: "test-key" }
  );
  assert.equal(response.status, 405);
  assert.equal((await response.json()).error.code, "METHOD_NOT_ALLOWED");
});

test("unknown endpoints are rejected", async () => {
  const response = await gate.fetch(request({}, "/admin"), { GEMINI_API_KEY: "test-key" });
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error.code, "INVALID_REQUEST");
});

/* --------------------------- /notify --------------------------- */

test("/notify is rate limited before the admin token is even compared", async () => {
  const env = {
    ADMIN_NOTIFY_TOKEN: "admin-secret",
    FCM_SERVICE_ACCOUNT_JSON: "{}",
    NOTIFY_RATE_LIMITER: fakeLimiter(0),
  };
  const response = await gate.fetch(
    request({ "x-admin-token": "guess" }, "/notify"),
    env
  );
  assert.equal(response.status, 429);
  assert.equal((await response.json()).error.code, "RATE_LIMITED");
});

test("/notify requires a valid admin token", async () => {
  const env = { ADMIN_NOTIFY_TOKEN: "admin-secret", FCM_SERVICE_ACCOUNT_JSON: "{}" };

  const missing = await gate.fetch(request({}, "/notify"), env);
  assert.equal(missing.status, 401);
  assert.equal((await missing.json()).error.code, "UNAUTHORIZED");

  const wrong = await gate.fetch(request({ "x-admin-token": "guess" }, "/notify"), env);
  assert.equal(wrong.status, 403);
  assert.equal((await wrong.json()).error.code, "FORBIDDEN");
});

test("/notify reports missing configuration without leaking details", async () => {
  const noToken = await gate.fetch(request({}, "/notify"), {});
  assert.equal(noToken.status, 500);
  assert.equal((await noToken.json()).error.code, "ADMIN_NOT_CONFIGURED");

  const noFcm = await gate.fetch(
    request({ "x-admin-token": "admin-secret" }, "/notify"),
    { ADMIN_NOTIFY_TOKEN: "admin-secret" }
  );
  assert.equal(noFcm.status, 500);
  assert.equal((await noFcm.json()).error.code, "FCM_NOT_CONFIGURED");
});

test("/notify rejects an oversized body and invalid payloads before calling FCM", async () => {
  const env = { ADMIN_NOTIFY_TOKEN: "admin-secret", FCM_SERVICE_ACCOUNT_JSON: "{}" };
  const headers = { "x-admin-token": "admin-secret" };

  const oversized = await gate.fetch(
    new Request("https://proxy.example/notify", {
      method: "POST",
      headers: { "content-length": String(64 * 1024), ...headers },
      body: "{}",
    }),
    env
  );
  assert.equal(oversized.status, 413);
  assert.equal((await oversized.json()).error.code, "BODY_TOO_LARGE");

  const emptyTitle = await gate.fetch(
    new Request("https://proxy.example/notify", {
      method: "POST",
      headers,
      body: JSON.stringify({ title: "  ", body: "hi" }),
    }),
    env
  );
  assert.equal(emptyTitle.status, 400);
  assert.equal((await emptyTitle.json()).error.code, "INVALID_REQUEST");
});
