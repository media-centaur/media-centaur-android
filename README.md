# Media Centaur TV

The Android TV app for [Media Centaur](https://github.com/media-centaur/media-centaur):
a WebView shell showing the host's UI, with an embedded libmpv that the host
drives over a WebSocket speaking mpv's JSON IPC. The TV renders; the host
decides.

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

Everything goes through `mc-tv` (`~/scripts/agents/mc-tv`), which runs each tool
under this repository's `mise.toml`:

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

`./gradlew assembleDebug` from a shell inside the repository works the same
way, because `mise` activates the toolchain there.

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
