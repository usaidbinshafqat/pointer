# Open-source readiness

This document is the prioritized checklist for publishing Pointer's source and
shipping signed Android APKs. It consolidates the legal, security, code-quality,
release, and documentation audits performed in September 2026.

Pointer is an independent, unofficial Android client for Cursor Cloud Agents.
It must not imply affiliation with or endorsement by Anysphere.

## Current assessment

The cleanup branch is close to being suitable for a public **source release**.
It is not yet ready for a broadly distributed **release APK**.

Do not make the repository public while the old `main` branch is the default.
That branch still contains outdated branding and maintainer-specific material.
Merge `cursor/opensource-cleanup-e263` into `main` first.

## P0 — required before making the repository public

### 1. Publish the cleaned tree

- Merge `cursor/opensource-cleanup-e263` into `main`.
- Confirm `main` contains `LICENSE`, `README.md`, `SECURITY.md`, and
  `CONTRIBUTING.md`.
- Confirm `PLAN.md`, `TODOS.md`, personal machine names, local paths, API keys,
  APKs, keystores, and `local.properties` are absent from the current tree.
- Decide whether historical maintainer paths and machine names are acceptable
  in Git history. Rewrite or squash history only if a zero-personal-history
  policy is required.

**Done when:** the exact commit that will become public has passed a full-tree
and Git-history secret scan.

### 2. Fail closed when secure key storage is unavailable

`SettingsRepository` currently falls back from `EncryptedSharedPreferences` to
ordinary `MODE_PRIVATE` preferences if Android Keystore initialization fails.
That silently contradicts the encrypted-storage promise.

- Remove the plaintext fallback.
- Show a blocking, actionable error if secure storage is unavailable.
- Ensure legacy plaintext API-key migration securely removes the old value.
- Remove or privatize `CursorAppViewModel.saveSettings()`, which can bypass the
  verify-before-save API-key flow.
- Add tests for successful storage, migration, and secure-storage failure.

**Done when:** no code path can persist a Cursor API key outside encrypted
storage, and all user-facing key writes pass `GET /v1/me` verification.

### 3. Add required public documentation

- Add `PRIVACY.md` covering local caches, prompts, attachments, API keys,
  notifications, background polling, retention, and deletion.
- Add `DISCLAIMER.md` covering unofficial status, beta/API instability,
  account and rate-limit risk, and the user's responsibility to follow Cursor's
  terms.
- Expand `SECURITY.md` with a real reporting channel, supported versions,
  secure-storage behavior, notification exposure, and local unencrypted cache
  details.
- Correct the API-key setup link in `README.md` to Cursor Dashboard → API Keys.
- Document the use and instability of `/v0/private-workers` and
  `/v0/agents/{id}/conversation`, plus best-effort rename endpoint probing.
- Add the full Anysphere/Cursor trademark disclaimer to the in-app About area.

**Done when:** a user can understand what leaves the phone, what remains on the
phone, what is unofficial, and how to report a vulnerability without reading
the source.

### 4. Add continuous integration

Create `.github/workflows/ci.yml` for pushes to `main` and pull requests:

```text
./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace --no-daemon
```

Use JDK 17, validate the Gradle wrapper, grant only `contents: read`, and upload
the debug APK as a short-lived workflow artifact. Never expose release signing
secrets to pull requests and never use `pull_request_target`.

**Done when:** every pull request must compile and pass unit tests before merge.

## P1 — required before distributing a public APK

### 5. Establish permanent release signing

- Generate one dedicated Pointer release keystore and back it up offline.
- Store its base64 value and passwords only in GitHub Actions secrets.
- Read signing values from environment variables in CI and an ignored
  `keystore.properties` file locally.
- Commit only `keystore.properties.example`.
- Never publish an unsigned or debug-signed APK as a release.

Losing or changing this key prevents installed APKs from receiving updates.

### 6. Add an automated GitHub Release pipeline

Create `.github/workflows/release.yml` for tags matching `v*.*.*`:

1. Verify the tag matches `versionName`.
2. Run unit tests.
3. Decode the keystore into the runner's temporary directory.
4. Build and verify the signed release APK.
5. Publish `pointer-X.Y.Z.apk` and its SHA-256 checksum.
6. Remove temporary signing material even if the job fails.

Start with GitHub Releases. Defer Play Store and F-Droid distribution until the
privacy policy, release process, and signing lineage have been proven.

### 7. Create the first public version

- Add `CHANGELOG.md` using Keep a Changelog and Semantic Versioning.
- Add `RELEASING.md` with version bump, signing, tag, checksum, verification,
  rollback, and key-recovery procedures.
- Use `0.7.0` as the first public release name.
- Use a `versionCode` greater than 24; `0.6.9-debug` already uses 24.
- Test a clean install and an update signed with the same release key.
- Document that debug (`app.pointer.android.debug`) and release
  (`app.pointer.android`) installations do not share app data.

### 8. Complete licensing and artifact provenance

- Add a root `NOTICE` covering bundled fonts and distributed dependencies.
- Keep the complete Fira Sans and Fira Code OFL texts in the APK.
- Verify ownership and source files for all launcher/store artwork.
- Re-export `art/pointer-play-icon-512.png` as a real PNG or remove it.
- Delete the unused `ic_stat_cursor.xml` resource.

## P1 — important security and privacy hardening

### 9. Protect sensitive local data

The API key is the highest-priority secret, but drafts, queued prompts,
conversations, account email, repository names, workspace paths, and machine
metadata are also sensitive.

- Do not persist attachment base64 in plaintext drafts or queued prompts.
- Document which caches are unencrypted and how long they are retained.
- Add “remove API key” and “clear local data” actions.
- Clear key-derived in-memory state after key removal.
- Warn before attaching likely secret files such as `.env`.

### 10. Reduce lock-screen and recent-app exposure

- Mark run and decision notifications as private and provide generic public
  lock-screen text.
- Review whether agent chats should be hidden in the Android recent-app
  snapshot.
- Keep Settings protected with `FLAG_SECURE`.

## P2 — contributor and maintenance quality

### 11. Add contributor infrastructure

- Add `CODE_OF_CONDUCT.md`.
- Add issue forms for bugs, feature requests, and API breakages.
- Add a pull-request template requiring tests, UI evidence where relevant, an
  API-surface note, and confirmation that no secrets are included.
- Add `docs/ARCHITECTURE.md` and `docs/API_USAGE.md`.
- Make build instructions portable across Android Studio, Linux, macOS, and
  Windows.

### 12. Add high-value tests

The pure model logic is reasonably covered, but the most sensitive runtime
paths are not.

Prioritize:

1. `CursorApiClient` tests with MockWebServer for authentication, 401/403,
   sanitized errors, SSE parsing, reconnects, and terminal events.
2. `SettingsRepository` tests for encryption and migration.
3. Attachment encoding, limits, and secret-file warnings.
4. ViewModel tests for verify-before-save, polling, queueing, reconnects, and
   stale-error clearing.
5. Basic Compose/navigation tests for API-key setup and run submission.

### 13. Reduce architectural friction

- Split the large `CursorAppViewModel` by inbox, agent, and new-agent concerns.
- Extract the duplicated new-agent form shared by the sheet and full screen.
- Split `Models.kt` into API DTO, inbox, streaming, machine, and merge logic.
- Remove dead code such as deprecated helpers and unused singleton state.
- Document the historical `app.cursor.android` namespace versus the public
  `app.pointer.android` application ID. Consider a package rename separately;
  it is not required for the first release.

### 14. Stabilize the build

- Review alpha Compose, Material 3, AGP, and `security-crypto` dependencies.
- Prefer stable releases where they provide the required API.
- Add dependency update automation and Gradle dependency locking.
- Keep release minification disabled until R8 rules and release smoke tests are
  in place; then enable shrinking in a dedicated change.

## Release gate

A release is ready only when all of the following are true:

- The cleaned tree is on `main` and CI is green.
- Secret and personal-data scans find no release blockers.
- API keys cannot fall back to plaintext storage.
- Privacy, security, disclaimer, license, and release documentation is present.
- The APK is signed by the permanent Pointer release key.
- The tag, `versionName`, `versionCode`, changelog, APK name, and checksum agree.
- A clean install and an update have been tested on a physical device.
- The release notes clearly state that Pointer is unofficial and uses the
  user's own Cursor API key.

## Deliberately deferred

These should not block the first GitHub APK:

- Play Store or F-Droid publishing
- A full package/namespace rename
- Multi-module architecture
- R8/minification before keep rules and smoke tests exist
- Certificate pinning without stable pins published by the API provider
- A complete ViewModel rewrite
