//! Android stand-in for track-change notifications. The media notification that keeps playback
//! alive in the background will show the track instead.

use tauri::AppHandle;

pub fn track_changed(_app: &AppHandle, _title: &str, _artists: &str, _even_focused: bool) {}
