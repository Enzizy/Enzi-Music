//! Android stand-in for the system tray: a phone has none, and the app is never hidden to it.

use tauri::AppHandle;

pub fn available() -> bool {
    false
}

pub fn set_available(_val: bool) {}

pub fn show_main(_app: &AppHandle) {}

pub fn set_main_visible(_app: &AppHandle, _visible: bool) {}

pub fn init(_app: &AppHandle) -> tauri::Result<()> {
    Ok(())
}

pub fn set_playing(_app: &AppHandle, _playing: bool) {}

pub fn set_icon(_app: &AppHandle, _icon: &tauri::image::Image<'_>) {}
