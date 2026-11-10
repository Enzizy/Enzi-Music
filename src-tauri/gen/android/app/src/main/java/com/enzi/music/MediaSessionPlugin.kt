package com.enzi.music

import android.app.Activity
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.Plugin

@InvokeArg
class MetadataArgs {
    var title: String = ""
    var artist: String = ""
    var album: String? = null
    var cover: String? = null
    /** Seconds; 0 while mpv hasn't reported the length yet. */
    var duration: Double = 0.0
}

@InvokeArg
class PlaybackArgs {
    var playing: Boolean = false
    /** Seconds. */
    var position: Double = 0.0
}

/** What Rust tells Android about the track. Registered from `src-tauri/src/android/media.rs`. */
@TauriPlugin
class MediaSessionPlugin(private val activity: Activity) : Plugin(activity) {
    @Command
    fun metadata(invoke: Invoke) {
        val args = invoke.parseArgs(MetadataArgs::class.java)
        PlaybackService.setMetadata(activity, args.title, args.artist, args.album, args.cover, args.duration)
        invoke.resolve()
    }

    @Command
    fun playback(invoke: Invoke) {
        val args = invoke.parseArgs(PlaybackArgs::class.java)
        PlaybackService.setPlayback(activity, args.playing, args.position)
        invoke.resolve()
    }
}
