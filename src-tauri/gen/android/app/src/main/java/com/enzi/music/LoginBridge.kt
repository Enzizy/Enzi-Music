package com.enzi.music

/** The YouTube session from [LoginActivity], handed to Rust (`src-tauri/src/android/login.rs`). */
object LoginBridge {
    /** `cookie` is a `Cookie` header for music.youtube.com, with SAPISID in it. */
    @JvmStatic
    external fun onCookies(cookie: String)
}
