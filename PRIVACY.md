# Privacy

Last updated: September 18, 2026

Pointer is an independent Android client. It does not operate a backend,
collect analytics, show ads, or include a crash-reporting service.

## Data sent to Cursor

Pointer sends requests directly from your device to `https://api.cursor.com`
using the Cursor API key you provide. Depending on the feature you use, those
requests can include:

- your API key as HTTP Basic authentication;
- prompts and follow-up messages;
- images or files you deliberately attach;
- agent, repository, branch, environment, and machine identifiers; and
- approvals or declines sent as follow-up messages.

Cursor's terms and privacy policy govern data processed by Cursor. Pointer's
maintainers do not receive this traffic.

## Data stored on your device

The API key is stored with Android Keystore-backed encrypted preferences.
Pointer refuses to save a key when secure storage is unavailable.

Pointer also stores app preferences and local working data, including:

- conversation and inbox caches;
- account, repository, worker, and machine metadata returned by Cursor;
- unsent drafts and queued prompts;
- local folders, pins, chat names, icons, and machine aliases; and
- active-run and notification state.

These caches are held in Pointer's private app storage but are not individually
encrypted. Conversation-cache entries omit attachment bytes. Drafts and queued
prompts may contain attachment data until they are sent or removed.

Android cloud backup and device-to-device transfer are disabled for Pointer.
A rooted device, malware with elevated privileges, or someone with access to
an unlocked device may still be able to read app data.

## Background activity and notifications

Pointer may maintain a foreground connection while an agent is running and may
periodically check active runs with Android WorkManager. Notifications can
contain chat names, response previews, or decision prompts unless notification
content is hidden by your Android lock-screen settings.

## Retention and deletion

Cached data remains in private app storage until Pointer replaces it, the user
removes it through an available app control, or Android app data is cleared.
Uninstalling Pointer removes its app storage. Revoking a key in the Cursor
dashboard prevents that key from authorizing future API requests.

## Your choices

- Use a dedicated, revocable Cursor API key.
- Do not attach files containing credentials or other sensitive information.
- Hide Pointer notification content on the lock screen.
- Revoke the API key and clear Pointer's app data when you stop using it.

## Changes

Material privacy changes will be documented in release notes and reflected in
this file before a release is published.
