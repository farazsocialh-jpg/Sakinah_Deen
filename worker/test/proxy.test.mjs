import assert from "node:assert/strict";
import test from "node:test";

import gate, {
  MAX_PROMPT_CHARS,
  checkRateLimit,
  clampGenerationConfig,
  tokensMatch,
  validatePayload,
} from "../src/index.js";

function request(headers = {}) {
  return new Request("https://proxy.example/", {
    method: "POST",
    headers: { "cf-connecting-ip": "203.0.113.7", ...headers },
  });
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

test("tokensMatch rejects wrong, empty and partial tokens", () => {
  assert.equal(tokensMatch("abc123", "abc123"), true);
  assert.equal(tokensMatch("abc124", "abc123"), false);
  assert.equal(tokensMatch("abc", "abc123"), false);
  assert.equal(tokensMatch(undefined, "abc123"), false);
});

test("checkRateLimit allows the configured budget then asks for a retry", () => {
  const env = { RATE_LIMIT_PER_MINUTE: "2" };
  const req = request({ "cf-connecting-ip": "198.51.100.1" });
  const start = 1_000_000;

  assert.equal(checkRateLimit(req, env, start).allowed, true);
  assert.equal(checkRateLimit(req, env, start + 10).allowed, true);
  const blocked = checkRateLimit(req, env, start + 20);
  assert.equal(blocked.allowed, false);
  assert.ok(blocked.retryAfter >= 1);
  // The window resets after a minute.
  assert.equal(checkRateLimit(req, env, start + 60_001).allowed, true);
});

test("missing configuration returns a structured, non-leaky error", async () => {
  const response = await gate.fetch(request(), {});
  assert.equal(response.status, 503);
  const body = await response.json();
  assert.equal(body.error.code, "NOT_CONFIGURED");
  assert.equal(typeof body.error.message, "string");
  assert.equal(response.headers.get("access-control-allow-origin"), "*");
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
