package com.enzi.music

import android.app.Activity
import android.content.Intent
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.Plugin

@InvokeArg
class LoginArgs {
    var addAccount: Boolean = false
}

/** Opens [LoginActivity] when Rust asks for sign-in. Registered from `src-tauri/src/android/login.rs`. */
@TauriPlugin
class LoginPlugin(private val activity: Activity) : Plugin(activity) {
    @Command
    fun open(invoke: Invoke) {
        val args = invoke.parseArgs(LoginArgs::class.java)
        activity.startActivity(
            Intent(activity, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_ADD_ACCOUNT, args.addAccount)
        )
        invoke.resolve()
    }
}
