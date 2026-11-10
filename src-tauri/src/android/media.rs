//! Android stand-in for the OS media controls. The lock screen and notification controls will come
//! from an Android MediaSession; until then every push is a no-op, as when souvlaki fails to start.

use std::sync::OnceLock;

use tauri::AppHandle;

pub struct MediaHandle;

impl MediaHandle {
    pub fn set_metadata(&self, _title: &str, _artist: &str, _album: Option<&str>, _cover: Option<&str>) {}
    pub fn set_album(&self, _album: &str) {}
    pub fn set_duration(&self, _secs: f64, _speed: f64) {}
    pub fn set_playback(&self, _playing: bool, _pos: f64, _speed: f64) {}
    pub fn set_volume(&self, _volume: i64) {}
}

pub fn spawn(_app: AppHandle) -> Option<MediaHandle> {
    None
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
