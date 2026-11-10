fn main() {
    // tauri-build watches tauri.conf.json but not the icons it embeds, so editing a PNG here
    // leaves `generate_context!` emitting the old `default_window_icon` (window, tray, taskbar).
    println!("cargo:rerun-if-changed=icons");

    // Android builds leave out the desktop-only plugins, so their permissions can't be listed there.
    let capabilities = if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("android") {
        "./capabilities/android/*.json"
    } else {
        "./capabilities/desktop/*.json"
    };
    // libmpv for Android lives in the app's native-libs folder, which Gradle packs into the APK.
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("android") {
        let abi = match std::env::var("CARGO_CFG_TARGET_ARCH").as_deref() {
            Ok("aarch64") => "arm64-v8a",
            Ok("x86_64") => "x86_64",
            Ok("arm") => "armeabi-v7a",
            _ => "x86",
        };
        let dir = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("gen/android/app/src/main/jniLibs")
            .join(abi);
        println!("cargo:rustc-link-search=native={}", dir.display());
    }

    tauri_build::try_build(tauri_build::Attributes::new().capabilities_path_pattern(capabilities))
        .expect("failed to run tauri-build");
}
