package com.enzi.music

/**
 * Presses from the media notification, the lock screen and headphone buttons, handed straight to
 * Rust (`src-tauri/src/android/media.rs`). Not through the webview, which Android pauses in the
 * background.
 */
object MediaBridge {
    /** `action` is play, pause, next, previous, stop or seek; `value` is the seek target in seconds. */
    @JvmStatic
    external fun onAction(action: String, value: Double)
}
