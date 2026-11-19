# Enzi Music

A YouTube Music client for Windows and Android, built with Rust, Tauri and Svelte. It's the music companion to [Enzi](https://github.com/Enzizy/Enzi): on Windows, the notch and its floating player pick up whatever Enzi Music is playing, album art included.

## Download

Get the Windows installer or the Android APK from the [latest release](https://github.com/Enzizy/Enzi-Music/releases/latest). The Windows app updates itself; the Android app tells you when a new APK is out.

## Features

- **Playback:** gapless audio through libmpv, with loudness normalization and crossfade
- **Search and browse:** songs, albums, artists, playlists and your home feed
- **Your account:** sign in with Google, with several accounts and switching between them
- **Your library:** playlists, liked songs, saved albums and artists, uploads, and history
- **Lyrics:** synced lyrics with auto-scroll and click-to-jump, word by word where available
- **Queue:** radio and autoplay, drag to reorder, restored across restarts
- **Effects:** tempo, pitch and reverb, with presets like slowed + reverb and nightcore
- **Music videos:** optional, played where the artwork sits
- **Local music:** play your own files
- **Look:** a monochrome default, seven more themes, light and dark, custom accent colors and fonts, a theme that adapts to the playing cover, and your own background image with dim and blur
- **Windows:** mini player, theater mode, media keys, global hotkeys, taskbar buttons, system tray and start on login
- **Android:** a phone layout, background playback with lock-screen and notification controls, and Google sign-in

## Build

You need Rust, Node.js and pnpm. Install the frontend first:

```bash
cd ui && pnpm install
```

### Windows

You also need the Visual Studio C++ build tools. libmpv isn't in the repo: put `libmpv-2.dll` in `src-tauri/`, and an `mpv.lib` import library built from it in `.libmpv/`. The steps are in `.github/workflows/release.yml`.

```bash
cargo tauri build
```

### Android

You also need the Android SDK, NDK 28, JDK 17 and the `aarch64-linux-android` Rust target. Put libmpv's Android `.so` files in `src-tauri/gen/android/app/src/main/jniLibs/arm64-v8a/`; the release workflow shows where they come from. Then build the frontend and run the script, which writes the APK to `target/android/`:

```bash
cd ui && pnpm build
```

```powershell
./scripts/build-android-test.ps1
```

Without `src-tauri/gen/android/keystore.properties` the APK is signed with Android's debug key, so it won't install over a release build.

## Your Google account

Enzi Music uses YouTube's private API rather than an official one, so it can stop working when YouTube changes something, and using it may go against YouTube's terms. Sign-in stores your Google session in a local database (in `%APPDATA%\com.enzi.music` on Windows, in the app's private storage on Android), so treat it like a password.

## Credits and license

Enzi Music is a modified version of [Limusic](https://github.com/SimoHypers/limusic) by SimoHypers, which grew out of [Metrolist](https://github.com/mostafaalagamy/Metrolist). The playback engine, YouTube client and most of the interface come from that project.

Changes from Limusic: renamed and rebranded with a monochrome look, ported to Android, custom background images, English only, and without Linux and macOS builds, Discord status, Last.fm and ListenBrainz scrobbling, Listen Together, Spotify import, romanization or the donation links.

Made by [Zhyronne](https://zhyronne.workwithoddly.com) ([GitHub](https://github.com/Enzizy)).

Licensed under the GNU General Public License v3.0 or later, the same as Limusic. See [LICENSE](LICENSE).
