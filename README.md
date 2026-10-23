# Media Centaur TV

The Android TV app for [Media Centaur](https://github.com/media-centaur/media-centaur):
a WebView shell showing the host's UI, with an embedded libmpv that the host
drives over a WebSocket speaking mpv's JSON IPC. The TV renders; the host
decides.

Users install it from the [latest release](https://github.com/media-centaur/media-centaur-android/releases/latest);
the wiki's [Android TV App](https://github.com/media-centaur/media-centaur/wiki/Android-TV-App)
page is the guide. This repository is named for its platform: a phone or
tablet variant would share this Gradle project and its libmpv.

This is the contributor guide. The contract both codebases follow is the
host repository's `docs/superpowers/specs/2026-10-22-remote-players-protocol-design.md`;
the rollout is its `campaigns/remote-players.md`. What is known about
libmpv on the TV itself — the two render paths, HDR, resolution, audio,
throughput, the long-lived instance's pitfalls, what to sample — is
[`docs/playback-on-android.md`](docs/playback-on-android.md); add to it
whenever a measurement settles a question.

## Toolchain

* [`mise`](https://mise.jdx.dev) reads `mise.toml`: Temurin JDK 21, `ANDROID_HOME`
  at `~/Android/Sdk`, and the SDK's tools on `PATH`. Nothing goes in a shell profile.
* The Android SDK is installed by `~/scripts/agents/mc-tv setup` (command-line
  tools, platform-tools, build-tools 36, platform 36, the emulator and the
  Android TV API 36 image). Google's SDK licences are accepted by that step.
* Gradle through the wrapper (`./gradlew`), Android Gradle Plugin 9.4 with its
  built-in Kotlin. Versions live in `gradle/libs.versions.toml`.

## Build, install, drive

A shell inside the repository has the toolchain (`mise` activates it), so the
Gradle wrapper is all a contributor needs:

```
./gradlew test assembleDebug          # what CI runs on every push
adb install -r app/build/outputs/apk/debug/media-centaur-tv-<version>-debug.apk
./gradlew assembleRelease             # unsigned without the MC_ANDROID_* variables below
```

The author's device rig, `mc-tv` (`~/scripts/agents/mc-tv` on the author's
machine, not part of this repository), wraps the same commands with install,
launch, key presses, screen capture, logs and the emulator:

```
mc-tv setup                     # one-time SDK install
mc-tv pair <ip:port> <code>     # one-time, from the TV's Developer options → Wireless debugging
mc-tv connect                   # every session; the address is remembered under ~/.config/mc-tv/
mc-tv build                     # assembleDebug
mc-tv install && mc-tv launch
mc-tv key DPAD_DOWN ENTER       # drive it
mc-tv shot                      # screencap to a PNG
mc-tv log [-f]                  # logcat filtered to the app's tag and libmpv's
mc-tv webview                   # forward the WebView's DevTools socket to 127.0.0.1:9222
mc-tv -e …                      # the same against the emulator (mc-tv emulator start)
```

## Release

A release is a tag; nothing is built or uploaded by hand.

1. Set `appVersionName` in `app/build.gradle.kts` (the one version source;
   `versionCode` is derived from it) and write the `## v<version>` section
   in `CHANGELOG.md` — the release workflow refuses a tag without one.
2. Commit, tag `v<version>`, push the tag.
3. `.github/workflows/release.yml` checks the tag against `appVersionName`,
   builds the signed universal APK (every ABI libmpv ships, one file for any
   TV), and publishes `media-centaur-tv-<version>-release.apk` with
   `SHA256SUMS` and the CHANGELOG section as the GitHub Release.

Signing is a property of the environment, not a build type of its own:
`assembleRelease` is signed when these four variables are set and unsigned
otherwise. CI takes them from repository secrets of the same names (the
keystore itself as `MC_ANDROID_KEYSTORE_BASE64`, decoded into
`MC_ANDROID_KEYSTORE` at run time):

| Variable | Meaning |
|---|---|
| `MC_ANDROID_KEYSTORE` | Path to the PKCS12 keystore |
| `MC_ANDROID_KEYSTORE_PASSWORD` | Its password |
| `MC_ANDROID_KEY_ALIAS` | The signing key's alias |
| `MC_ANDROID_KEY_PASSWORD` | The key's password |

The keystore is the app's identity: an APK signed with another key cannot
upgrade an installed one. The author holds it outside the repository
(`*.jks` is ignored) with a backup; a lost key means a new application id.

## Layout

Standard Android Gradle layout, one module (`app`), Kotlin sources under
`app/src/main/kotlin/app/mediacentaur/tv/`. The planned packages, from the
protocol spec: `pairing` (first-run screen), `shell` (the WebView),
`player` (one libmpv instance on a surface), `bridge` (the WebSocket and the
IPC ⇄ libmpv translation).

## Conventions

* Every log line carries the tag `MediaCentaur` (`MainActivity.TAG`); libmpv
  logs under `mpv`. `mc-tv log` filters on both.
* The app holds no model of titles, profiles or progress. Its persistent
  state is the host address and the cookie jar.
* Generic placeholders in tests and examples, never real titles (the host
  repository's rule).
