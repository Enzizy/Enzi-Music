# Enzi Music

A desktop YouTube Music client for Windows, built with Rust and Tauri. It's the music companion to [Enzi](https://github.com/Enzizy/Enzi): the notch and its floating player pick up whatever Enzi Music is playing through Windows media controls.

## Features

- **Playback:** gapless audio through libmpv, with loudness normalization
- **Search and browse:** songs, albums, artists, playlists and your home feed
- **Your account:** sign in with YouTube Music, with several accounts and switching between them
- **Your library:** playlists, liked songs, saved albums and artists, uploads, and history
- **Lyrics:** synced lyrics with auto-scroll and click-to-jump, word by word where available
- **Queue:** radio and automix, drag to reorder, restored across restarts
- **Effects:** tempo, pitch and reverb, with saved presets
- **Music videos:** optional, played where the artwork sits
- **Mini player and theater mode**
- **Local music:** play your own files
- **Discord status:** artwork and a live progress bar on your profile
- **Windows integration:** media keys, taskbar buttons, system tray and start on login
- **Themes:** accent palettes, custom colors and fonts, and a theme that adapts to the playing cover

## Build

You need Rust, Node.js, pnpm and the Visual Studio C++ build tools.

libmpv isn't in the repo. Put `libmpv-2.dll` in `src-tauri/`, and an `mpv.lib` import library built from it in `.libmpv/`. The steps are in `.github/workflows/release.yml`.

```bash
cd ui && pnpm install
```

```bash
cargo tauri build
```

## Your Google account

Enzi Music uses YouTube's private API rather than an official one, so it can stop working when YouTube changes something, and using it may go against YouTube's terms. Sign-in stores your Google session in a local database in `%APPDATA%\com.enzi.music`, so treat that file like a password.

## Credits and license

Enzi Music is a modified version of [Limusic](https://github.com/SimoHypers/limusic) by SimoHypers, which grew out of [Metrolist](https://github.com/mostafaalagamy/Metrolist). The playback engine, YouTube client and most of the interface come from that project.

Changes from Limusic: renamed and rebranded, built for Windows only, and without Last.fm and ListenBrainz scrobbling, Listen Together, Spotify import or the donation links.

Licensed under the GNU General Public License v3.0 or later, the same as Limusic. See [LICENSE](LICENSE).
