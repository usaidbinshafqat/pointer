# API usage

Pointer sends requests directly to `https://api.cursor.com` using the API key
supplied by the user. The key is passed as the username in HTTP Basic
authentication with an empty password.

## Endpoints

The main integration uses the documented Cloud Agents `/v1` API:

- account and model lookup;
- list, inspect, create, archive, and permanently delete agents;
- list, create, inspect, cancel, and stream runs; and
- list connected repositories.

Chat lifecycle actions use the documented `POST /v1/agents/{agentId}/archive`
and `DELETE /v1/agents/{agentId}` routes. Pointer always asks for confirmation,
shows archive and permanent delete as separate choices, and warns that permanent
deletion cannot be undone. Multi-select performs one request per selected chat
and preserves chats whose individual request fails.

Pointer also currently depends on versioned or incompletely documented routes:

- `GET /v0/private-workers` for connected workers;
- `GET /v0/agents/{agentId}/conversation` for conversation history; and
- several best-effort rename request shapes.

Contributors must treat these routes as unstable. New undocumented routes
require an explanation in code and in this file. Do not add website scraping,
browser-session tokens, or requests to unrelated hosts.

## Streaming and polling

Run output arrives through server-sent events. Pointer parses status, assistant,
thinking, tool-call, result, interaction, and heartbeat events, while ignoring
unknown event types.

REST polling is a recovery mechanism rather than the primary live transport:

- active chats are checked more frequently;
- idle chats are checked less frequently;
- conversation history is not repeatedly fetched when the remote fingerprint
  has not changed; and
- Android WorkManager may periodically check persisted active runs.

Transient network and stream failures should retry without creating permanent
error bubbles. HTTP authentication, validation, and terminal run errors remain
visible.

## Rate limits and errors

The API provider controls quotas and rate limits. Code should honor `429` and
temporary `5xx` responses, avoid fan-out requests where cached data is
sufficient, and use bounded backoff.

Never log an Authorization header, full API key, prompt attachment, or raw
response that might contain credentials. User-facing errors must pass through
the existing sanitization logic.

## Compatibility

Cursor may change beta or undocumented APIs without notice. Keep parsing
forward-compatible, ignore unknown JSON keys/events, and ensure a partial
failure does not erase usable cached data.
