/**
 * ImanCore AI Scholar proxy (Cloudflare Worker).
 * The deployed route/name stays sakinah-ai-proxy so existing app builds keep working.
 *
 * The Android/iOS apps POST a Gemini-shaped request body here and read the SSE stream back.
 * The upstream provider credential lives only in this Worker's environment
 * (`GEMINI_API_KEY` secret), so no key is ever compiled into the mobile binaries.
 *
 * Deploy:
 *   cd worker
 *   npx wrangler secret put GEMINI_API_KEY     # never commit the value
 *   npx wrangler deploy
 *
 * Then point the app at the Worker URL with AI_PROXY_URL (see .env.example).
 */

const MAX_BODY_BYTES = 256 * 1024;
const DEFAULT_MODEL = "gemini-2.0-flash";

const CORS_HEADERS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "POST, OPTIONS",
  "access-control-allow-headers": "content-type, x-sakinah-client",
  "access-control-max-age": "86400",
};

function withCors(response) {
  const headers = new Headers(response.headers);
  for (const [key, value] of Object.entries(CORS_HEADERS)) headers.set(key, value);
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

function jsonError(message, status) {
  return withCors(
    new Response(JSON.stringify({ error: message }), {
      status,
      headers: { "content-type": "application/json; charset=utf-8" },
    })
  );
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return withCors(new Response(null, { status: 204 }));
    }
    if (request.method !== "POST") {
      return jsonError("Only POST is supported.", 405);
    }
    // Optional shared client token: set CLIENT_TOKEN as a secret to stop third-party reuse
    // of the owner's model quota. The app does not send it, so leave it unset by default.
    if (env.CLIENT_TOKEN) {
      if (request.headers.get("x-sakinah-client") !== env.CLIENT_TOKEN) {
        return jsonError("Unauthorized.", 401);
      }
    }
    if (!env.GEMINI_API_KEY) {
      return jsonError("The proxy is not configured (missing GEMINI_API_KEY).", 503);
    }

    const body = await request.text();
    if (body.length > MAX_BODY_BYTES) {
      return jsonError("Request body is too large.", 413);
    }
    let payload;
    try {
      payload = JSON.parse(body);
    } catch {
      return jsonError("Request body must be JSON.", 400);
    }
    if (!payload || typeof payload !== "object" || !payload.contents) {
      return jsonError("Request body must contain Gemini 'contents'.", 400);
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
        body,
      });
    } catch {
      return jsonError("The model provider could not be reached.", 502);
    }

    if (!upstream.ok) {
      const detail = await upstream.text().catch(() => "");
      return jsonError(
        `Model provider error (HTTP ${upstream.status})${detail ? `: ${detail.slice(0, 300)}` : ""}`,
        upstream.status === 429 ? 429 : 502
      );
    }

    return withCors(
      new Response(upstream.body, {
        status: 200,
        headers: {
          "content-type": "text/event-stream; charset=utf-8",
          "cache-control": "no-store",
        },
      })
    );
  },
};
