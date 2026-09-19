# Security

## Reporting

If you find a vulnerability in Pointer, use the repository's
[private security advisory](https://github.com/usaidbinshafqat/pointer/security/advisories/new).
Do not file a public issue containing secrets, private conversation content, or
account details. Pointer maintainers will never ask for your full API key.

The latest public release receives security fixes. Older builds may be
unsupported.

## What Pointer stores

- Your Cursor API key is kept in encrypted SharedPreferences (Android Keystore).
- Pointer refuses to save an API key if secure storage is unavailable.
- Android backup and device-to-device transfer of app data are disabled.
- The key is sent only to `https://api.cursor.com` as HTTP Basic auth.
- Conversation cache on disk does not keep full image bytes.
- Conversation, account, repository, machine, draft, and queue caches are
  private app files but are not individually encrypted.

See [PRIVACY.md](PRIVACY.md) for the complete local-data and network summary.

## Threat model

Pointer protects its data from ordinary applications using Android's app
sandbox and Keystore. It cannot protect an API key or cached content from a
rooted device, a compromised OS, an unlocked device controlled by someone else,
or a malicious build of Pointer.

Run and decision notifications may contain response previews. Use Android's
lock-screen notification controls if that content is sensitive. Prompts and
attachments deliberately submitted through Pointer are processed by Cursor.

## What you should do

- Use a dedicated, revocable Cursor API key.
- Install release APKs only from this repository's GitHub Releases page and
  verify the published checksum.
- Never commit keys, `local.properties`, keystores, or a signed-in debug APK.
- Treat this as an unofficial client: API shape can change; revoke the key if you stop using the app.
