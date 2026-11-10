//! Android stand-in for the mini player window: Android apps get one window.

use tauri::AppHandle;

pub const LABEL: &str = "mini";

pub fn open(_app: &AppHandle) -> Result<(), String> {
    Err("The mini player isn't available on Android".into())
}

pub fn set_compact(_app: &AppHandle, _compact: bool) -> Result<(), String> {
    Err("The mini player isn't available on Android".into())
}

pub fn save_position(_app: &AppHandle) {}

pub fn close(_app: &AppHandle) {}
