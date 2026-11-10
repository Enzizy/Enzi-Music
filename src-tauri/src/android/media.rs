//! Android media session. What's playing goes to `PlaybackService` (Kotlin, in gen/android), which
//! keeps the app alive in the background and draws the media notification and lock-screen
//! controls. Presses come back through `MediaBridge.onAction`, implemented at the bottom of this
//! file, into the same [`AppState`] calls the desktop media controls make.

use std::sync::mpsc::{channel, Sender};
use std::sync::{Arc, OnceLock};

use jni::objects::{JClass, JString};
use jni::sys::jdouble;
use jni::JNIEnv;
use serde_json::json;
use tauri::plugin::{Builder, PluginHandle, TauriPlugin};
use tauri::{AppHandle, Manager, Wry};

use crate::state::AppState;

/// For `MediaBridge.onAction`, which arrives from Java with nothing else to go on.
static APP: OnceLock<AppHandle> = OnceLock::new();

struct Session(PluginHandle<Wry>);

/// The plugin that carries updates to Kotlin. Registered on the builder, before setup runs.
pub fn init() -> TauriPlugin<Wry> {
    Builder::new("media-session")
        .setup(|app, api| {
            let handle = api.register_android_plugin("com.enzi.music", "MediaSessionPlugin")?;
            app.manage(Session(handle));
            Ok(())
        })
        .build()
}

enum MediaUpdate {
    Metadata { title: String, artist: String, album: Option<String>, cover: Option<String> },
    Album(String),
    Duration(f64),
    Playback { playing: bool, pos: f64 },
}

/// Same interface as the desktop handle. Calls into Kotlin wait for an answer, so they run on a
/// thread of their own rather than on whichever task reported the change.
pub struct MediaHandle {
    tx: Sender<MediaUpdate>,
}

impl MediaHandle {
    pub fn set_metadata(&self, title: &str, artist: &str, album: Option<&str>, cover: Option<&str>) {
        let _ = self.tx.send(MediaUpdate::Metadata {
            title: title.to_owned(),
            artist: artist.to_owned(),
            album: album.map(str::to_owned),
            cover: cover.map(str::to_owned),
        });
    }

    pub fn set_album(&self, album: &str) {
        let _ = self.tx.send(MediaUpdate::Album(album.to_owned()));
    }

    // Listening seconds, like the desktop controls and the UI.
    pub fn set_duration(&self, secs: f64, speed: f64) {
        let _ = self.tx.send(MediaUpdate::Duration(secs / speed));
    }

    pub fn set_playback(&self, playing: bool, pos: f64, speed: f64) {
        let _ = self.tx.send(MediaUpdate::Playback { playing, pos: pos / speed });
    }

    /// Android's own volume keys set the system level; there is no per-app one to mirror.
    pub fn set_volume(&self, _volume: i64) {}
}

pub fn spawn(app: AppHandle) -> Option<MediaHandle> {
    let _ = APP.set(app.clone());
    let Some(session) = app.try_state::<Session>() else {
        tracing::warn!("media session plugin missing, so no background playback");
        return None;
    };
    let plugin = session.0.clone();
    let (tx, rx) = channel::<MediaUpdate>();
    let spawned = std::thread::Builder::new().name("media-session".into()).spawn(move || {
        let (mut title, mut artist) = (String::new(), String::new());
        let (mut album, mut cover): (Option<String>, Option<String>) = (None, None);
        let mut duration = 0.0;
        let send = |command: &str, payload: serde_json::Value| {
            if let Err(e) = plugin.run_mobile_plugin::<serde_json::Value>(command, payload) {
                tracing::warn!(error = %e, command, "media session update failed");
            }
        };
        while let Ok(update) = rx.recv() {
            match update {
                MediaUpdate::Metadata { title: t, artist: a, album: al, cover: c } => {
                    (title, artist, album, cover) = (t, a, al, c);
                    duration = 0.0; // not known until mpv reports it
                }
                MediaUpdate::Album(al) => album = Some(al),
                MediaUpdate::Duration(secs) => duration = secs,
                MediaUpdate::Playback { playing, pos } => {
                    send("playback", json!({ "playing": playing, "position": pos.max(0.0) }));
                    continue;
                }
            }
            send(
                "metadata",
                json!({
                    "title": title,
                    "artist": artist,
                    "album": album,
                    "cover": cover,
                    "duration": duration,
                }),
            );
        }
    });
    match spawned {
        Ok(_) => Some(MediaHandle { tx }),
        Err(e) => {
            tracing::warn!(error = %e, "media session thread spawn failed");
            None
        }
    }
}

/// Same as the desktop version: ask for a cover big enough to show anywhere larger than a row.
pub(crate) fn cover_url(url: &str) -> String {
    static WH: OnceLock<regex::Regex> = OnceLock::new();
    static S: OnceLock<regex::Regex> = OnceLock::new();
    let wh = WH.get_or_init(|| regex::Regex::new(r"=w\d+-h\d+").expect("static regex"));
    let s = S.get_or_init(|| regex::Regex::new(r"=s\d+").expect("static regex"));
    if wh.is_match(url) {
        wh.replace(url, "=w512-h512").into_owned()
    } else if s.is_match(url) {
        s.replace(url, "=s512").into_owned()
    } else {
        url.to_owned()
    }
}

/// `MediaBridge.onAction(action, value)`: a press on the notification, lock screen or headphones.
#[no_mangle]
pub extern "system" fn Java_com_enzi_music_MediaBridge_onAction(
    mut env: JNIEnv,
    _class: JClass,
    action: JString,
    value: jdouble,
) {
    let Ok(action) = env.get_string(&action).map(String::from) else { return };
    let Some(app) = APP.get().cloned() else { return };
    tauri::async_runtime::spawn(async move {
        let Some(state) = app.try_state::<Arc<AppState>>() else { return };
        let state = state.inner().clone();
        match action.as_str() {
            "play" => state.resume_or_toggle().await,
            "pause" | "stop" => {
                let _ = state.player.pause();
            }
            "next" => state.next_in_queue().await,
            "previous" => state.prev_in_queue().await,
            "seek" => {
                let _ = state.player.seek(value * state.player.speed());
            }
            other => tracing::warn!(action = other, "unknown media action"),
        }
    });
}
