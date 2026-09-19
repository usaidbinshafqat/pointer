# pointer

Unofficial Android client for [Cursor Cloud Agents](https://cursor.com/docs/cloud-agent). Pointer is an independent project: paste your own API key and steer agents from your phone.

**Pointer is not affiliated with, endorsed by, sponsored by, or associated
with Anysphere, Inc.** Cursor and related marks are trademarks of Anysphere.
This app talks directly to the [Cloud Agents API](https://cursor.com/docs/api)
with **your** key. See [DISCLAIMER.md](DISCLAIMER.md).

Application id: `app.pointer.android` (debug builds use `.debug`).

## Download

Download the latest signed APK and SHA-256 checksum from
[GitHub Releases](https://github.com/usaidbinshafqat/pointer/releases/latest).

Verify the checksum before installing:

```bash
shasum -a 256 -c pointer-0.0.1.apk.sha256
```

Then enable **Install unknown apps** for the browser or file manager you used,
or install over USB:

```bash
adb install -r pointer-0.0.1.apk
```

Official release APKs use `app.pointer.android`. Debug builds use
`app.pointer.android.debug`, install separately, and do not share app data.

## What it does

- Stores **your** API key on the device
- Lists agents, streams run output, and sends follow-ups
- Starts agents on cloud or on machines you already connected in Cursor (Remote Control or `agent worker`)

Pointer does not ship API keys, accounts, or sample machines. Names you see in the inbox (account, “Alex’s MacBook Pro”, repos) come from **your Cursor account via the API**, not from this repository.

## Your API key

1. Create a key in the Cursor Dashboard's API Keys area. Use a dedicated key you can revoke.
2. Install the app, open **settings**, paste the key, and tap **save and test**. Pointer calls `GET /v1/me` before storing it so a bad paste cannot replace a working key.

The key is stored in **encrypted SharedPreferences** (Android Keystore). It is sent only to `https://api.cursor.com` as HTTP Basic auth. App backup is disabled so the key is not copied into cloud or device-to-device backups. Settings blocks screenshots while that screen is open.

Never commit a key, put one in `local.properties`, or share a debug APK that you
already signed into. See [SECURITY.md](SECURITY.md) and
[PRIVACY.md](PRIVACY.md).

## API compatibility

Pointer primarily uses the documented `/v1` Cloud Agents API. Worker discovery
and conversation history currently depend on versioned or incompletely
documented `/v0` endpoints, so those features can break if Cursor changes them.
See [docs/API_USAGE.md](docs/API_USAGE.md).

## Machines (optional)

Cloud agents work with only an API key. To run on a computer you own:

```bash
agent login
agent worker start --name "my-laptop" --worker-dir /path/to/your/repo
```

In the app, set environment to **my machines** and pick that worker (or a Remote Control desktop Cursor already lists).

## Build

Install JDK 17 and an Android SDK through Android Studio or your platform's
package manager, then run:

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Install with USB debugging (`adb install -r …`) or copy the APK to the phone.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

The project structure is documented in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Release maintainers should follow
[RELEASING.md](RELEASING.md).

## License

MIT. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Fira Sans and Fira Code are
under the SIL Open Font License (bundled at
`app/src/main/assets/licenses/`).
