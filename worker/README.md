# ImanCore AI Scholar proxy

Cloudflare Worker that keeps the model provider key server-side. The mobile apps only know
`AI_PROXY_URL` and receive Gemini's SSE stream through this proxy.

## Deploy

```bash
cd worker
npx wrangler secret put GEMINI_API_KEY   # paste the key when prompted; never commit it
npx wrangler deploy
```

Optional: `npx wrangler secret put CLIENT_TOKEN` and send it as the `x-sakinah-client` header
to stop anyone else from spending your model quota.

Then set `AI_PROXY_URL` (env var or `keystore.properties`) to the deployed `*.workers.dev` URL
and rebuild. With no URL configured the app shows a "not configured" message instead of a
network error.

The nisab price feed (`NISAB_RATE_URL`) is unrelated to this Worker; when it is blank the app
falls back to the cached rate or its built-in estimate.
