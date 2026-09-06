# Installable build

`Manager-release.apk` — the R8-minified release build, ready to sideload.

| | |
|---|---|
| Package | `com.manager.app` |
| Version | 1.0 (versionCode 1) |
| Size | 3,065,059 bytes (2.93 MiB) |
| minSdk / targetSdk | 29 / 36 |
| ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Signature | v2 scheme, `CN=Manager Debug, O=Personal, C=US` |
| Signing cert SHA-256 | `21aae7670ce4f7c81a519415566a07df021c0f612151cab9b52c0a5b45101d42` |
| APK SHA-256 | `fbad75057806c8989dd6c4f25ba4714934fcc3870f31c2630a8b4d7ad7841911` |

## Installing on a Pixel

Over USB:

```bash
adb install -r release/Manager-release.apk
```

Or download the file from GitHub on the phone itself and open it; Android will ask you to
allow installs from your browser or file manager the first time.

Verify the download first if you like:

```bash
sha256sum Manager-release.apk
# fbad75057806c8989dd6c4f25ba4714934fcc3870f31c2630a8b4d7ad7841911
```

## After installing

Manager asks for nothing on first launch. Onboarding explains **Usage access** and links to the
Settings page for it — that one permission unlocks screen-time ranking and exact app sizes. Skip
it and the app still works; storage falls back to APK size on disk and says so in place.

`QUERY_ALL_PACKAGES` and `REQUEST_DELETE_PACKAGES` are granted at install time. Every uninstall
still goes through Android's own confirmation screen.

## About the signing key

Signed with the repository's `app/debug.keystore` so the artifact is directly installable. The
key is stable, so future builds install over this one with `adb install -r`. It is a throwaway
key committed to a public repo — fine for a personal sideload, not for distribution. Swap in your
own keystore before this goes anywhere else.

## Rebuilding

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Regenerate this copy with:

```bash
cp app/build/outputs/apk/release/app-release.apk release/Manager-release.apk
```
