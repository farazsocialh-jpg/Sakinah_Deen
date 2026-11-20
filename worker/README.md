# ImanCore AI Scholar proxy

Cloudflare Worker that keeps the model provider key server-side. The mobile apps only know
`AI_PROXY_URL` and receive Gemini's SSE stream through this proxy.

## Deploy

```bash
cd worker
npx wrangler secret put GEMINI_API_KEY   # paste the key when prompted; never commit it
npx wrangler deploy
```

Then set `AI_PROXY_URL` (env var or `keystore.properties`) to the deployed `*.workers.dev` URL
and rebuild. With no URL configured the app shows a "not configured" message instead of a
network error.

The nisab price feed (`NISAB_RATE_URL`) is unrelated to this Worker; when it is blank the app
falls back to the cached rate or its built-in estimate.

## Configuration

| Variable | Kind | Default | Purpose |
| --- | --- | --- | --- |
| `GEMINI_API_KEY` | secret | — | Upstream credential. Missing → `503 NOT_CONFIGURED`. |
| `GEMINI_MODEL` | var | `gemini-2.0-flash` | Model id used upstream. |
| `CLIENT_TOKEN` | secret | unset | When set, every request must send it as `x-sakinah-client`. Compared in constant time. |
| `ALLOWED_ORIGIN` | var | unset | Comma-separated CORS origin allowlist. Unset means **no** browser origin is allowed; `*` is ignored. Native mobile clients send no `Origin` and are unaffected. |
| `RATE_LIMITER` | rate limit binding | — | Proxy limit, declared in `wrangler.toml` (default 20 requests / 60 s per client key). |
| `NOTIFY_RATE_LIMITER` | rate limit binding | — | `/notify` limit, declared in `wrangler.toml` (default 10 requests / 60 s per IP). |

## Abuse and cost protections

- **Auth**: optional shared `CLIENT_TOKEN`; unset means the endpoint is public, so set it for
  any deployment that is not just your own devices. `/notify` always requires
  `x-admin-token` (`ADMIN_NOTIFY_TOKEN` secret).
- **CORS**: closed by default. Only origins in `ALLOWED_ORIGIN` receive
  `access-control-allow-origin` (the exact request origin is echoed — never `*`), and a
  request carrying a non-allowlisted `Origin` is answered `403 ORIGIN_NOT_ALLOWED` before any
  handler runs. Preflights follow the same rule.
- **Body size**: 256 KiB hard cap on the proxy, 16 KiB on `/notify`, rejected from
  `content-length` before buffering.
- **Prompt cap**: 16,000 characters total and at most 40 turns (`contents`).
- **Output cap**: `generationConfig.maxOutputTokens` is clamped to 2048; the client cannot
  raise it.
- **Rate limit**: Cloudflare's native Rate Limiting bindings, keyed per client (IP + token
  prefix) on the proxy and per IP on `/notify` (checked before the admin token so the endpoint
  cannot be used to guess tokens at full rate). Over the limit answers `429 RATE_LIMITED` with
  `retry-after`. Counters live in Cloudflare, not in isolate memory, so they survive isolate
  eviction and apply across isolates; they are approximate per Cloudflare location. Tune the
  thresholds in `wrangler.toml` (`[[ratelimits]]`).
- **Constant-time secrets**: `CLIENT_TOKEN` and `ADMIN_NOTIFY_TOKEN` are compared over
  HMAC-SHA-256 digests with a fixed 32-byte loop, so neither content nor length is leaked by
  timing.
- **No leakage**: upstream bodies and stack traces are never relayed. Errors use a stable
  envelope: `{ "error": { "code": "...", "message": "..." } }`.

### Error codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| `METHOD_NOT_ALLOWED` | 405 | Not a POST. |
| `ORIGIN_NOT_ALLOWED` | 403 | Browser `Origin` is not in `ALLOWED_ORIGIN`. |
| `UNAUTHORIZED` | 401 | Missing/incorrect client or admin token. |
| `NOT_CONFIGURED` | 503 | `GEMINI_API_KEY` is unset. |
| `BODY_TOO_LARGE` | 413 | Request over 256 KiB. |
| `INVALID_JSON` | 400 | Body is not JSON. |
| `INVALID_REQUEST` | 400 | No `contents`/`parts`/`text`, or unknown path. |
| `PROMPT_TOO_LARGE` | 413 | Prompt over the character or turn budget. |
| `RATE_LIMITED` | 429 | Per-client limit hit; honour `retry-after`. |
| `UPSTREAM_RATE_LIMITED` | 429 | The model provider rate limited the proxy. |
| `UPSTREAM_ERROR` / `UPSTREAM_UNAVAILABLE` | 502 | Provider error or unreachable. |

## Tests

```bash
cd worker
npm test        # node --test: payload validation, quotas, rate limit, error envelope
```

The tests exercise the exported helpers and the fetch handler directly; no network, no
Cloudflare account and no API key are needed.
