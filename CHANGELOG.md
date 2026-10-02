# Changelog

All notable user-facing changes to Pointer are documented here. This project
uses [Semantic Versioning](https://semver.org/) and the structure from
[Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [0.0.1] - 2026-10-01

First public release. Pointer is unofficial and not affiliated with Anysphere.
Install the signed APK from GitHub Releases and use your own Cursor API key.

### Added

- Native Android inbox for starting and managing Cursor Cloud Agents.
- Live run streaming, follow-up messages, cancellation, queued prompts, and
  completion or decision notifications.
- Cloud, Remote Control, and `agent worker` environments.
- Image and file attachments, markdown responses, todos, and per-file change
  summaries.
- Search, filters, pins, folders, local chat names and icons, plus multi-select
  mark-as-read, archive, and permanent-delete actions with explicit
  confirmation.
- Long-press a user or agent message to copy it.
- Material You, light/dark themes, accessibility text sizing, haptics, and Fira
  Sans appearance choices.
- Public privacy, security, API-use, contribution, architecture, and release
  documentation.
- Continuous integration and signed GitHub Release automation.

### Security

- Refuse to store an API key when Android Keystore-backed encrypted storage is
  unavailable.
- Store the API key in Android Keystore-backed encrypted preferences, disable
  app backup, protect the Settings screen from screenshots, and avoid
  third-party analytics or network logging.
- Keep lock-screen notification previews private.

### Changed

- Use regular Fira Sans as the default text weight.
- Reduce open-chat polling and avoid unnecessary refreshes after appearance
  changes.

### Fixed

- Hide leftover chat errors after the same turn recovers or a later message
  succeeds, including friendly network failures.
- Draw the follow-up composer over the chat with a transparent surround so
  only the rounded input stays filled.
- Preserve drafts and attachments while navigating.
- Keep the composer above Android system navigation and make expansion visible
  even for short drafts.
- Recover active runs and notification actions after process recreation.

[Unreleased]: https://github.com/usaidbinshafqat/pointer/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/usaidbinshafqat/pointer/releases/tag/v0.0.1
