# Architecture

Pointer is a single-module Kotlin Android application using Jetpack Compose.

## Main layers

- `ui/` contains Compose screens and reusable components.
- `CursorAppViewModel` coordinates inbox, chat, composer, model, queue, and
  streaming state.
- `data/CursorApiClient` owns HTTPS and SSE communication with Cursor.
- `data/SettingsRepository` stores preferences and the encrypted API key.
- `data/CacheRepository` stores API response and conversation caches.
- `data/LocalStore` stores local-only folders, aliases, drafts, queues, and
  active-run state.
- `streaming/` manages foreground work, notifications, process recovery, and
  periodic checks.

## Data flow

```text
Compose screen
    ↓ user action / observed state
CursorAppViewModel
    ├── CursorApiClient ── HTTPS/SSE ── api.cursor.com
    ├── SettingsRepository
    ├── CacheRepository
    └── LocalStore
```

The ViewModel exposes state flows to Compose. Cached content is shown first
where possible, then refreshed from the API. Live run output is merged with
conversation history without replacing newer local content with stale data.

## Application identity

The public application ID is `app.pointer.android`; debug builds append
`.debug`. The Kotlin namespace remains `app.cursor.android` for historical
reasons. This is an internal implementation name and does not indicate
affiliation with Anysphere.

## Security boundaries

- API keys belong only in Android Keystore-backed encrypted preferences.
- App backup and cleartext network traffic are disabled.
- Network traffic is limited to `https://api.cursor.com`.
- Conversation and local-state caches are private app files but are not
  individually encrypted.
- No analytics, crash reporting, or HTTP body logging should be added without a
  separate privacy and security review.

## Testing

Pure state, parsing, merge, polling, and formatting behavior is covered by JVM
unit tests under `app/src/test`. Network-client, encrypted-storage, streaming,
and Compose integration coverage should be expanded before larger refactors.

Run the current verification suite with:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Refactoring direction

The current ViewModel and model file are intentionally documented as
contributor friction. Prefer incremental extraction into inbox, agent, API DTO,
streaming, and persistence concerns. Avoid a package-wide rewrite in the same
change as security or release work.
