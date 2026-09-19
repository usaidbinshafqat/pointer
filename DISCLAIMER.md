# Disclaimer

Pointer is an independent open-source project. It is not affiliated with,
endorsed by, sponsored by, or supported by Anysphere, Inc. “Cursor” and related
marks are trademarks of Anysphere. Pointer does not grant permission to use
Cursor logos or imply official status.

Users must provide their own Cursor API key and are responsible for complying
with [Cursor's terms](https://cursor.com/terms), acceptable-use rules, API
documentation, account limits, and any usage charges.

## API stability

Pointer uses Cursor's Cloud Agents API, which may change or be restricted.
Most operations use documented `/v1` endpoints. Some features currently depend
on versioned or incompletely documented endpoints:

- `GET /v0/private-workers` to list connected workers;
- `GET /v0/agents/{agentId}/conversation` to load conversation history; and
- best-effort agent rename requests across several possible routes.

These features may stop working without notice. Pointer does not scrape the
Cursor website, bundle credentials, resell Cursor access, or proxy requests
through a Pointer-operated server.

## Risk

An API key is a replayable credential until it is revoked. Use a dedicated key,
protect the device, and revoke the key if the device or build may be
compromised. Prompts and attachments are sent to Cursor under your account.

API polling and streaming can count toward Cursor limits. Pointer attempts to
avoid unnecessary requests and to back off after transient failures, but it
cannot guarantee account availability, API compatibility, or service limits.

The software is provided “AS IS” under the MIT License, without warranty. See
`LICENSE`, `PRIVACY.md`, and `SECURITY.md`.
