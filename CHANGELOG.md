# Changelog

All notable user-facing changes to Pointer are documented here. This project
uses [Semantic Versioning](https://semver.org/) and the structure from
[Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added

- Bulk “mark as read” action for selected inbox chats.

## [0.0.1] - 2026-09-20

First public release.

### Added

- Native Android inbox for starting and managing Cursor Cloud Agents.
- Live run streaming, follow-up messages, cancellation, queued prompts, and
  completion or decision notifications.
- Cloud, Remote Control, and `agent worker` environments.
- Image and file attachments, markdown responses, todos, and per-file change
  summaries.
- Search, filters, pins, folders, local chat names and icons, plus multi-select
  archive and permanent-delete actions with explicit confirmation.
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

- Hide stale connection and chat errors after later data succeeds.
- Preserve drafts and attachments while navigating.
- Keep the composer above Android system navigation and make expansion visible
  even for short drafts.
- Recover active runs and notification actions after process recreation.

[Unreleased]: https://github.com/usaidbinshafqat/pointer/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/usaidbinshafqat/pointer/releases/tag/v0.0.1
