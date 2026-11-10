//! Google sign-in on Android. `LoginActivity` (Kotlin, in gen/android) is the sign-in screen; this
//! opens it and takes back the YouTube cookies it captures, then signs in the same way the desktop
//! login window does ([`crate::session::finish_sign_in`]).

use std::sync::{Arc, OnceLock};

use jni::objects::{JClass, JString};
use jni::JNIEnv;
use serde_json::json;
use tauri::plugin::{Builder, PluginHandle, TauriPlugin};
use tauri::{AppHandle, Emitter, Manager, Wry};

use crate::state::AppState;

/// For `LoginBridge.onCookies`, which arrives from Java with nothing else to go on.
static APP: OnceLock<AppHandle> = OnceLock::new();

struct Login(PluginHandle<Wry>);

pub fn init() -> TauriPlugin<Wry> {
    Builder::new("google-login")
        .setup(|app, api| {
            let _ = APP.set(app.clone());
            let handle = api.register_android_plugin("com.enzi.music", "LoginPlugin")?;
            app.manage(Login(handle));
            Ok(())
        })
        .build()
}

pub fn open(app: &AppHandle, add_account: bool) {
    let Some(login) = app.try_state::<Login>() else {
        let _ = app.emit("login-error", "The sign-in screen isn't available");
        return;
    };
    if let Err(e) = login.0.run_mobile_plugin::<serde_json::Value>("open", json!({ "addAccount": add_account })) {
        let _ = app.emit("login-error", format!("Couldn't open the sign-in screen: {e}"));
    }
}

/// `LoginBridge.onCookies(cookie)`: the sign-in screen reached YouTube Music signed in.
#[no_mangle]
pub extern "system" fn Java_com_enzi_music_LoginBridge_onCookies(
    mut env: JNIEnv,
    _class: JClass,
    cookie: JString,
) {
    let Ok(cookie) = env.get_string(&cookie).map(String::from) else { return };
    let Some(app) = APP.get().cloned() else { return };
    tauri::async_runtime::spawn(async move {
        let Some(state) = app.try_state::<Arc<AppState>>() else { return };
        let state = state.inner().clone();
        crate::session::finish_sign_in(&app, &state, cookie).await;
    });
}
