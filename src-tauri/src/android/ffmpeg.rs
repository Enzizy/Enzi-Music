//! FFmpeg, inside libmpv, reaches Android's audio output and codecs through Java, so it needs the
//! app's Java VM (and its Context) before mpv starts. Without it there is no sound.

use std::ffi::{c_int, c_void};

#[link(name = "avcodec")]
extern "C" {
    fn av_jni_set_java_vm(vm: *mut c_void, log_ctx: *mut c_void) -> c_int;
    fn av_jni_set_android_app_ctx(app_ctx: *mut c_void, log_ctx: *mut c_void) -> c_int;
}

pub fn hand_over_java_vm() {
    let Some(ctx) = tao::platform::android::prelude::main_android_context() else {
        tracing::error!("no Android context yet, so libmpv can't reach Java");
        return;
    };
    // SAFETY: both pointers come from the running activity and outlive the app process.
    let (vm, app) = unsafe {
        (
            av_jni_set_java_vm(ctx.java_vm, std::ptr::null_mut()),
            av_jni_set_android_app_ctx(ctx.context_jobject, std::ptr::null_mut()),
        )
    };
    if vm < 0 || app < 0 {
        tracing::error!(vm, app, "FFmpeg refused the Java VM or app context");
    }
}
