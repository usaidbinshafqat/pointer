# Contributing

Pointer is an unofficial Cloud Agents client. Keep it that way: no bundled API keys, no personal accounts, no sample machines from a maintainer’s laptop.

## Setup

JDK 17+, Android SDK. Then `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

## Rules of thumb

- Talk to `https://api.cursor.com` only. Prefer documented `/v1` Cloud Agents endpoints.
- Do not add analytics, crash reporters, or network logging that could capture an API key.
- Do not copy Cursor’s official app icons or claim affiliation with Anysphere.
- User-visible copy stays lowercase to match the app chrome.

Pull requests should include a short note on what you verified (unit tests and/or a debug install).
