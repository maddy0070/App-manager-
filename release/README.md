# Installable build

`Manager-release.apk` — the R8-minified release build, ready to sideload.

| | |
|---|---|
| Package | `com.manager.app` |
| Version | 1.0 (versionCode 1) |
| Size | 3109264 bytes (2.97 MiB) |
| minSdk / targetSdk | 29 / 36 |
| ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Signature | v2 scheme, `CN=Manager Debug, O=Personal, C=US` |
| Signing cert SHA-256 | `21aae7670ce4f7c81a519415566a07df021c0f612151cab9b52c0a5b45101d42` |
| APK SHA-256 | `fe37d9447354ac71d0837383e6b1edef88729cf2a5ad0b73d72f0025324d122c` |

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
# fe37d9447354ac71d0837383e6b1edef88729cf2a5ad0b73d72f0025324d122c
```

## After installing

Onboarding is a short interactive story on sample data — open an app, drag its surface, select a
few, watch the field resolve. It asks for nothing and touches none of your real apps.

**Usage access** is explained where it matters instead: the dashboard carries a panel saying what
it unlocks (screen-time ranking and exact app sizes) with a link to the Settings page, and the
Usage screen has its own state for it. Without it the app still works; storage falls back to APK
size on disk and says so in place.

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

The APK checked in here is the output of a from-scratch release build of this exact commit.
Rebuilding it yourself will produce the same bytes of code but a different SHA-256: signing
timestamps and archive entry order are not deterministic, so the hash above identifies *this*
file rather than certifying a reproducible build.
