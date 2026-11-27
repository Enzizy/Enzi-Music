package com.enzi.music

import android.app.Activity
import android.content.Context
import android.net.wifi.WifiManager
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

@InvokeArg
class CastDiscoveryArgs {
    var enabled: Boolean = false
}

/** What Rust tells Android about the track. Registered from `src-tauri/src/android/media.rs`. */
@TauriPlugin
class MediaSessionPlugin(private val activity: Activity) : Plugin(activity) {
    private var multicast: WifiManager.MulticastLock? = null

    @Command
    fun castDiscovery(invoke: Invoke) {
        try {
            if (invoke.parseArgs(CastDiscoveryArgs::class.java).enabled) {
                if (multicast == null) {
                    val wifi = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    multicast = wifi.createMulticastLock("EnziMusic:cast").apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }
            } else {
                multicast?.release()
                multicast = null
            }
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Could not change Wi-Fi discovery")
        }
    }

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
