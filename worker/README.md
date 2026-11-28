# ImanCore AI Scholar proxy

Cloudflare Worker (deployed as `sakinahdeen`) that keeps the model provider key server-side.
The mobile apps only know `AI_PROXY_URL` and receive Gemini's SSE stream through this proxy.
The public route is the custom domain **ai.insightsltd.com** (`[[routes]]` in `wrangler.toml`).

## Deploy

```bash
cd worker
npx wrangler secret put GEMINI_API_KEY              # paste the key when prompted; never commit it
npx wrangler secret put ADMIN_NOTIFY_TOKEN          # protects /notify
npx wrangler secret put FCM_SERVICE_ACCOUNT_JSON    # Firebase service account JSON
npx wrangler deploy
```

All three secrets must be set: the proxy cannot answer without `GEMINI_API_KEY`, and `/notify`
returns a configuration error unless both the admin token and the service account are present.
Then point the app at `https://ai.insightsltd.com` with `AI_PROXY_URL` (see `.env.example`) and
rebuild. With no URL configured the app shows a "not configured" message instead of a network
error.

The nisab price feed (`NISAB_RATE_URL`) is unrelated to this Worker; when it is blank the app
falls back to the cached rate or its built-in estimate.

## Configuration

| Variable | Kind | Default | Purpose |
| --- | --- | --- | --- |
| `GEMINI_API_KEY` | secret | — | Upstream credential. Missing → `503 NOT_CONFIGURED`. |
| `GEMINI_MODEL` | var | `gemini-3.6-flash` | Model id used upstream (must match `wrangler.toml`). |
| `CLIENT_TOKEN` | secret | unset | When set, every request must send it as `x-sakinah-client`. Compared in constant time. |
| `ADMIN_NOTIFY_TOKEN` | secret | — | Required as `x-admin-token` for `/notify`. Missing → `500 ADMIN_NOT_CONFIGURED`. |
| `FCM_SERVICE_ACCOUNT_JSON` | secret | — | Firebase service account used for HTTP v1 push. Missing → `500 FCM_NOT_CONFIGURED`. |
| `ALLOWED_ORIGIN` | var | unset | Comma-separated CORS origin allowlist. Unset means **no** browser origin is allowed; `*` is ignored. Native mobile clients send no `Origin` and are unaffected. |
| `RATE_LIMITER` | rate limit binding | — | Proxy limit, declared in `wrangler.toml` (default 20 requests / 60 s per client key). |
| `NOTIFY_RATE_LIMITER` | rate limit binding | — | `/notify` limit, declared in `wrangler.toml` (default 10 requests / 60 s per IP). |

Clients may send an opaque `x-installation-id` (e.g. a UUID generated once per install, at most
64 URL-safe characters) so rate limiting is per installation rather than per shared IP. The
header is optional; requests without it fall back to an IP-only key. It is never derived from
`CLIENT_TOKEN`.

## Abuse and cost protections

- **Auth**: optional shared `CLIENT_TOKEN`; unset means the endpoint is public, so set it for
  any deployment that is not just your own devices. `/notify` always requires
  `x-admin-token` (`ADMIN_NOTIFY_TOKEN` secret).
- **CORS**: closed by default. Only origins in `ALLOWED_ORIGIN` receive
  `access-control-allow-origin` (the exact request origin is echoed — never `*`), and a
  request carrying a non-allowlisted `Origin` is answered `403 ORIGIN_NOT_ALLOWED` before any
  handler runs. Preflights follow the same rule.
- **Body size**: 256 KiB hard cap on the proxy, 16 KiB on `/notify`, counted in UTF-8 bytes via
  `TextEncoder().encode(body).byteLength` and rejected from `content-length` before buffering.
- **Prompt cap**: 16,000 characters total and at most 40 turns (`contents`).
- **Output cap**: `generationConfig.maxOutputTokens` is clamped to 2048; the client cannot
  raise it.
- **Rate limit**: Cloudflare's native Rate Limiting bindings, keyed per IP + per-installation
  id on the proxy and per IP on `/notify` (checked before the admin token so the endpoint
  cannot be used to guess tokens at full rate). Over the limit answers `429 RATE_LIMITED` with
  `retry-after`. Counters live in Cloudflare, not in isolate memory, so they survive isolate
  eviction and apply across isolates; they are approximate per Cloudflare location. Tune the
  thresholds in `wrangler.toml` (`[[ratelimits]]`).
- **Constant-time secrets**: `CLIENT_TOKEN` and `ADMIN_NOTIFY_TOKEN` are compared over
  HMAC-SHA-256 digests with a fixed 32-byte loop, so neither content nor length is leaked by
  timing.
- **No leakage**: upstream bodies, FCM responses and stack traces are never relayed to the
  client. Structured console logs record status codes only (`upstream_http_error`,
  `upstream_unreachable`, `rate_limited_*`, `fcm_*`), never bodies, prompts or keys. Errors use
  a stable envelope: `{ "error": { "code": "...", "message": "..." } }`.

### Error codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| `METHOD_NOT_ALLOWED` | 405 | Not a POST. |
| `ORIGIN_NOT_ALLOWED` | 403 | Browser `Origin` is not in `ALLOWED_ORIGIN`. |
| `UNAUTHORIZED` | 401 | Missing client token on the proxy, or missing admin token on `/notify`. |
| `FORBIDDEN` | 403 | Incorrect `ADMIN_NOTIFY_TOKEN`. |
| `NOT_CONFIGURED` | 503 | `GEMINI_API_KEY` is unset. |
| `ADMIN_NOT_CONFIGURED` | 500 | `ADMIN_NOTIFY_TOKEN` is unset. |
| `FCM_NOT_CONFIGURED` | 500 | `FCM_SERVICE_ACCOUNT_JSON` is unset. |
| `BODY_TOO_LARGE` | 413 | Request over 256 KiB (16 KiB on `/notify`). |
| `INVALID_JSON` | 400 | Body is not JSON. |
| `INVALID_REQUEST` | 400 | No `contents`/`parts`/`text`, invalid `/notify` fields, or unknown path. |
| `PROMPT_TOO_LARGE` | 413 | Prompt over the character or turn budget. |
| `RATE_LIMITED` | 429 | Per-client or per-IP limit hit; honour `retry-after`. |
| `UPSTREAM_RATE_LIMITED` | 429 | The model provider rate limited the proxy. |
| `UPSTREAM_ERROR` / `UPSTREAM_UNAVAILABLE` | 502 | Provider error or unreachable. |
| `FCM_ERROR` | 502 | Firebase push failed (including oauth/service-account errors). |

## Tests

```bash
cd worker
npm test        # node --test: payload validation, quotas, rate limit, error envelope
```

The tests exercise the exported helpers and the fetch handler directly; no network, no
Cloudflare account and no API key are needed.
