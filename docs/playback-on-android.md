# Playback on Android TV: what is known

Facts about libmpv on Android TV as measured on the owner's panel, with
the sources that explain them. Every number here was measured; every
claim about mpv cites the manual or the binding's source. Add to this
file whenever a measurement or a reading settles a question; the host
repository's campaign file records the decisions taken from these facts.

Last verified 2026-10-22. Test device: Sony Bravia XR-42A90K, Android 12
(API 31), MediaTek MT5895, 32-bit (`armeabi-v7a`), WebView 154, a 4K
panel whose Android UI runs at a 1080p override, on WiFi (`eth0` present,
unplugged). Embedded player: `dev.jdtech.mpv:libmpv:1.0.0` = mpv 0.41.0,
FFmpeg 9.0.2, libplacebo 7.360.1 **built with Vulkan disabled**, Lua
enabled (`buildscripts/scripts/libplacebo.sh`, `mpv.sh`,
`include/depinfo.sh` in <https://github.com/jarnedemeulemeester/libmpv-android>).

## The two render paths

mpv on Android renders one of two ways. The player's instance options
choose; `vo` can also be switched at runtime over the channel
(`set_property vo …` before `loadfile`, verified).

| | `vo=gpu` (`gpu-context=android`, EGL) | `vo=mediacodec_embed` |
|---|---|---|
| Who draws the frame | mpv's GPU renderer into the app's `SurfaceView` | the MediaCodec decoder straight into the `SurfaceView` |
| Layer size the compositor sees | **1920×1080** on this panel (the view's size under the 1080p UI override; `android-surface-size=1920x1080`), upscaled by the HWC to 3840×2160 | **3840×2160**, the video's own size |
| Colour the compositor sees | dataspace `UNKNOWN`, no HDR metadata; mpv's `video-target-params`: bt.709, gamma 2.2, sig-peak 1.0, rgba16f — **HDR is tone-mapped to SDR on the Mali** | dataspace **`BT2020_ITU_PQ`**, `hdr metadata types=3` — a native HDR10 layer |
| OSD, subtitles, filters | yes (mpv draws them) | **no** — the manual: "many of mpv's features (subtitle rendering, OSD/OSC, video filters, etc) are not available with this driver" |
| mpv properties | `video-target-params`, `osd-dimensions` valid | `video-target-params` unavailable; `osd-dimensions` reports the view (1920×1080) and means nothing |
| Decode | `hwdec=mediacodec` (`aimagereader` interop) | `hwdec=mediacodec` (required) |
| Measured on a 95 Mbit/s 4K HDR remux | real time, 0 dropped / 0 delayed frames, avsync 0.00003 | real time, 0 dropped frames |
| `adb exec-out screencap` | shows the frame | shows the frame (the surface is composited, not protected) |

**HDR passthrough through mpv's GPU path is impossible on Android in
mpv 0.41.** The manual's `--target-colorspace-hint` ("HDR passthrough")
is "supported on Wayland, D3D11 and winvk contexts" and requires
`--vo=gpu-next`; the only Android GPU context is `android` ("Android/EGL",
`--gpu-context` list), and this libmpv build has no Vulkan anyway. Fixing
the surface at 3840×2160 (`SurfaceHolder.setFixedSize`) would restore
resolution on the GPU path, not HDR, and would cost a 4K tone-map per
frame on a 2022 Mali (unmeasured).

**Dolby Vision.** Profile 7 (UHD Blu-ray remux, full enhancement layer):
mpv logs "Dolby Vision enhancement-layer playback is not supported" and
decodes the HEVC base layer, so the TV receives HDR10 on either path
(`BT2020_ITU_PQ`, no DV signalling in SurfaceFlinger or logcat). This
is mpv's limitation everywhere, the host's desktop mpv included. DV
would need the decoder configured as `video/dolby-vision`, which mpv
does not do.

**Audio.** DTS-HD MA 5.1 is decoded by FFmpeg and output as 16-bit PCM,
6 channels, 48 kHz through `ao=audiotrack` (`audio-params/format=s32p`
in, `audio-out-params/format=s16` out). mpv logs an audio underrun at
each start.

Bitstream passthrough is possible in principle and untested on this
panel: `ao_audiotrack.c` (mpv 0.41) opens the track with
`ENCODING_IEC61937` for an spdif format (`af_fmt_is_spdif`), logging
"spdif passthrough not supported by API" when the device refuses; the
manual's `--audio-spdif=<codecs>` takes `ac3`, `dts`, `dts-hd`, `eac3`,
`truehd` and "works for both classic S/PDIF and HDMI" ("receiver and OS
support varies"). What the link can carry is physics, not software:
optical S/PDIF carries PCM 2.0, AC3 and the DTS core (lossy 5.1) only;
DTS-HD MA and TrueHD (lossless) need HDMI eARC. The owner's speakers are
on the TV's optical output, so on that setup "full fidelity" is the
DTS core or AC3 bitstream passed through untouched, and lossless audio
becomes possible only with an eARC receiver. Whether the Sony passes an
app's IEC61937 stream to optical, or re-encodes multichannel PCM to AC3
itself, is the measurement to make (`--audio-spdif=ac3,dts` over the
channel, then the receiver's own display).

## A page over the native video keeps the video HDR

Measured 2026-10-22 with a build that left the WebView visible and
transparent during a viewing, a label injected into the page while a
4K HDR remux played on `mediacodec_embed`: SurfaceFlinger composited
both layers in hardware. The video layer stayed `composition
type=DEVICE`, `dataspace=BT2020_ITU_PQ`, `hdr metadata types=3`; the
page layer (1920×1080, `blend=PREMULTIPLIED`) was also `DEVICE`; no
client (GPU) composition was forced and `frame-drop-count` stayed 0.
The first three attempts showed the label over **black** on the panel
and in `screencap`, with the page's computed backgrounds already
`rgba(0, 0, 0, 0)`. Three opaque paints in the app were covering the
player's surface, and all three had to go (verified one window at a
time, the owner watching the panel):

1. the root `FrameLayout`'s `android:background="@android:color/black"`
   and the WebView's own XML black background — any View that paints
   opaque pixels over the SurfaceView's bounds hides it, because the
   window's surface sits above the player's;
2. the theme's `android:windowBackground` black (now transparent; the
   host-address screen carries its own black background instead);
3. **Chromium's base background colour.** A WebView's transparency is
   set only by `WebView.setBackgroundColor(Color.TRANSPARENT)` in code;
   an XML `android:background` never reaches Chromium, which then
   paints its own base colour — a dark grey under the app's dark
   theme (algorithmic darkening), not black, which is what gave it away
   in the capture.

With those gone (and `window.setFormat(PixelFormat.TRANSLUCENT)` set
explicitly), the label sits over the playing 4K HDR video on the panel
and in `screencap`; the video layer stays `DEVICE`-composited and
`BT2020_ITU_PQ`. So the host's UI can be the TV's OSD over the native
video without touching the picture, which is how Kodi and
ExoPlayer-based players draw menus over HDR video. What the WebView
costs while idle over video is unmeasured.

## How the surface reaches mpv

`MPVLib.attachSurface(surface)` sets the `wid` option to the Surface
pointer (`libmpv/src/main/cpp/render.cpp`: `mpv_set_option(…, "wid",
MPV_FORMAT_INT64, …)`); `detachSurface` sets it to 0. That is exactly
what `mediacodec_embed` needs (manual: "Requires `--hwdec=mediacodec`
… along with `--vo=mediacodec_embed` and `--wid=(intptr_t)(*android.view.Surface)`"),
so both paths share one surface and one attach sequence. The GPU path
additionally needs `android-surface-size` on `surfaceChanged`.

## The long-lived instance

The host's mpv is a fresh process per viewing; the TV's libmpv lives for
the app's life. Everything a viewing sets persists into the next one
unless something resets it:

* **`keep-open=yes` leaves `pause=yes` at a file's end**, and the next
  `loadfile` starts paused. Seen on the panel on 2026-10-22: every
  viewing after an `eof` was a still image. The host owns the viewing's
  options and must send its starting state explicitly (the campaign's
  open decision 5: per-file `loadfile` options, which mpv restores at
  the file's end).
* **A JSON boolean must reach libmpv as a boolean.** `set_property pause
  false` sent as the string `"false"` is rejected with `-4`
  (`MPV_ERROR_OPTION_FORMAT`): mpv's flag options take `yes`/`no` or the
  native type. Fixed in `bridge/Ipc.kt` (the JSON value's type picks the
  binding's setter); the unit test pins it.
* `change-list script-opts append k=v` on every viewing accumulates
  entries with the same key; which one a script reads is unverified.

## Throughput on this panel

The TV's shell has no `curl` or `wget`; toybox `nc` and `dd` work:

```
printf 'GET /media/<presence>?t=<token> HTTP/1.0\r\nHost: x\r\n\r\n' | nc <host> 4003 | dd bs=65536 count=16384 of=/dev/null
```

| Measure | Result |
|---|---|
| 1 GiB from the host's media endpoint over the TV's WiFi | 38.7 s ≈ 27.8 MB/s ≈ 220 Mbit/s |
| 20 s timed fetch, same path | 208 Mbit/s |
| `adb push` of 200 MB (a proxy with protocol overhead) | 158 Mbit/s |
| Heaviest file in the owner's library | 95 Mbit/s average (`video-bitrate` peaks 96 Mbit/s) |
| mpv cache once full (150 MiB, `demuxer-readahead-secs=20`) | 13.4–13.8 s of a 95 Mbit/s file; `cache-speed` throttles to the file's rate; `paused-for-cache` never |

WiFi jitter over a full-length viewing is unmeasured (samples were 40 s).

## What to sample, and how

Over the channel (`get_property`, replies by `request_id`):
`pause core-idle time-pos cache-speed demuxer-cache-duration paused-for-cache
video-bitrate hwdec-current video-codec video-params/{w,h,primaries,gamma}
video-out-params/{primaries,gamma} video-target-params/{w,h,primaries,gamma,sig-peak,pixelformat}
frame-drop-count decoder-frame-drop-count vo-delayed-frame-count avsync
audio-codec-name audio-params/{format,channel-count} audio-out-params/{format,channel-count,samplerate} current-ao`.
`display-fps` and `mistimed-frame-count` are unavailable on this panel.

On the device: `dumpsys SurfaceFlinger` (the app's layer: `dataspace=`,
`hdr metadata types=`, `sourceCrop`, `displayFrame`; `HWC Support:
hdr10=true hlg=true dv=true`), `dumpsys display` (`mIsHdrLayerPresent`
stayed `false` even with the BT2020_PQ layer; not a reliable signal),
`logcat` (`VCodecDrv … HDR10PLUS_INFO` lines show the MediaTek decoder
reading HDR metadata).

The rig does all of this in one TV window:
`mc-tv-flow perf tv [--set vo=mediacodec_embed] <movie-id>[:seek]...`,
against files linked into the simulated host with
`mc-sim-host stage <file> <name> <year>` (symlinks; the owner's host is
never touched). Every window ends with `mc-tv hdmi`.

## References

* mpv 0.41 manual, `DOCS/man/vo.rst` (`mediacodec_embed`),
  `DOCS/man/options.rst` (`--wid` on Android, `--android-surface-size`,
  `--gpu-context`, `--target-colorspace-hint`, `--hwdec=mediacodec`).
* libmpv-android: `libmpv/src/main/cpp/render.cpp` (surface → `wid`),
  `event.cpp` (events carry no `end-file` reason), `MPVLib.kt`.
* How other Android TV players get native HDR: the decoder renders to
  its own surface and the application's UI is a separate layer the
  compositor stacks on top (Kodi's MediaCodec-to-Surface path; ExoPlayer
  with a `SurfaceView` and `SubtitleView`). mpv-android and Findroid use
  the GPU path and have the same SDR limitation measured here.
