# Releasing Pointer

GitHub Releases are the initial distribution channel. Do not publish an APK
manually or use a debug signing key for a public release.

## Permanent signing identity

Generate one dedicated release keystore:

```bash
keytool -genkeypair -v \
  -keystore pointer-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias pointer \
  -dname "CN=Pointer, OU=OSS, O=Pointer contributors"
```

Keep at least two encrypted offline backups. Losing the key prevents existing
installations from receiving updates. Never commit the keystore or passwords.

Configure these GitHub Actions secrets:

- `POINTER_KEYSTORE_BASE64`
- `POINTER_KEYSTORE_PASSWORD`
- `POINTER_KEY_ALIAS`
- `POINTER_KEY_PASSWORD`

Create the base64 value without line breaks:

```bash
base64 < pointer-release.jks | tr -d '\n'
```

For a local signed build, copy `keystore.properties.example` to the ignored
`keystore.properties`, use an absolute keystore path, and fill in the values.

## Prepare a release

1. Start from a green `main`.
2. Update `versionName` and increment `versionCode` in
   `app/build.gradle.kts`. Never reuse a version code.
3. Move relevant `CHANGELOG.md` entries from `Unreleased` into a dated release
   section.
4. Run:

   ```bash
   ./gradlew :app:testDebugUnitTest :app:assembleDebug
   ./gradlew :app:assembleRelease
   ```

5. Verify the release APK locally with Android's `apksigner`.
6. Install it on a clean physical device and verify API-key setup, inbox
   loading, chat streaming, notifications, and an upgrade from the previous
   signed release.
7. Merge the release change to `main`.

## Publish

Create an annotated tag matching `versionName` exactly:

```bash
git tag -a v0.7.0 -m "pointer 0.7.0"
git push origin v0.7.0
```

The release workflow validates the version, builds with the permanent key,
verifies the signature, creates a SHA-256 checksum, and uploads both files to a
GitHub Release.

After publishing:

1. Download the uploaded APK and checksum from GitHub.
2. Verify the checksum and signing-certificate fingerprint.
3. Test a fresh installation and an update from the prior public APK.
4. Confirm release notes contain the unofficial-client disclaimer and any
   security or migration notes.

If a bad artifact was installed by anyone, do not reuse its version code.
Publish a corrected patch release.

## Build identities

- Release: `app.pointer.android`
- Debug: `app.pointer.android.debug`

They install side by side and do not share API keys or app data. A future Play
Store build may have a different signing lineage; document that before adding
another distribution channel.
