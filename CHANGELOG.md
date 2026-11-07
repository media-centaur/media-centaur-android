# Changelog

User-facing release notes for the Media Centaur TV app. Each `## v<version>`
section is the text of that release's GitHub Release page; the release
workflow refuses a tag without one. Internal changes stay in the git history.

## v0.4.0 — 2026-11-08

### New

- **The app wears the centaur.** The launcher icon is Media Centaur's centaur in cream on navy, shaped to whatever your launcher uses (circle, squircle, themed); the TV's banner shows the centaur beside the name.
- **The phone browses upright and plays in landscape.** On a phone the app now stays portrait while you browse and turns to landscape for the length of a film or an episode, as video apps do; a tablet follows how it is held. A TV is unchanged. The page reaches into the camera cutout, so nothing is wasted at the edge. With Media Centaur 2.13.0 the phone also gets a bottom bar, finger-sized controls and Back and ten-second skips on the player.

## v0.3.0 — 2026-11-07

### New

- **Your Media Centaur install knows which device this is.** When the app connects, it tells the install its model, Android version and app version, and whether it is a TV, phone or tablet. Settings → Players and Status → Playback show them, so two players with similar names can be told apart. It needs Media Centaur 2.12.0.

## v0.2.0 — 2026-11-07

### New

- **Install it on your phone.** The app now installs and opens on an Android phone as well as a TV, from the same APK, and plays your library over Wi-Fi. Pair it once with your Media Centaur install, as on a TV. The phone's status and navigation bars are hidden while the app is open; swipe from the edge to show them for a moment.
- **Fill width.** When you choose **Fill width** in the player's menu, the picture is enlarged to the screen's width, for films with black bars inside the picture itself. It needs Media Centaur 2.11.0.

### Improved

- **The screen stays on.** While the app is in front, the TV or phone no longer dims or sleeps partway through a film.

### Fixed

- **Videos the device cannot decode in hardware now play.** Some files, such as AV1 on a phone without an AV1 decoder or some older MPEG-4 releases, played their sound over a black picture. They now play, decoded in software.
- **The app keeps playing through a theme change.** Switching between light and dark mode, or resizing the app's window, restarted the app and stopped the film. The film now keeps playing.
- **Opening the app again no longer opens a second copy.** In some cases, opening the app while it was already running started a second copy behind the first. Both took turns controlling playback, and the screen could go black. Now the open app is brought to the front.

## v0.1.2 — 2026-10-25

### Fixed

- **Back leaves the app.** Pressing Back from the main menu now returns to the TV's home screen, as in any other TV app; before, Back only moved between the page and the menu. On the pairing page, Back leaves straight away.

## v0.1.1 — 2026-10-24

### Fixed

- **The picture keeps its shape.** A 4:3 episode, or any video that is not the screen's 16:9, was stretched to fill the screen. It now plays at its own proportions, as large as fits, with black bars at the sides or above and below.

## v0.1.0 — 2026-10-23

### New

- **First release.** An Android TV app for a Media Centaur install on the same network: it shows the install's own pages, driven by the TV's remote, and plays the library's files with its built-in player at the panel's native resolution and HDR. Pair it once from the install's Settings → Players; progress, resume, the next episode and the finish prompt work as they do on the computer, under the profile picked on the TV.
