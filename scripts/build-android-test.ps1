# Builds an APK for 64-bit ARM phones. With src-tauri\gen\android\keystore.properties it is signed
# with the release key, the same as GitHub's releases, so it installs over them; without it, with
# Android's debug key, which installs fine but not over a release (or a release over it).
# Output: target\android\Enzi-Music_<version>_arm64.apk
#
# Needs the Android SDK, NDK 28, JDK 17, the aarch64-linux-android Rust target, the frontend
# already built into ui\build, and libmpv's .so files in src-tauri\gen\android\app\src\main\jniLibs.
#
# The Tauri CLI finishes by symlinking the Rust library into jniLibs, which Windows refuses without
# Developer Mode. So this lets the CLI build the library (it sets the Android package env the build
# needs), copies the library over itself, then packages with Gradle.
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "E:\Enzizy\android-sdk" }
$env:NDK_HOME = if ($env:NDK_HOME) { $env:NDK_HOME } else { "$env:ANDROID_HOME\ndk\28.2.13676358" }
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "E:\Enzizy\jdk-17.0.20.1+1" }
$env:CARGO_TARGET_DIR = "$root\target"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$lib = "$root\target\aarch64-linux-android\release\libapp_lib.so"
Set-Location $root
# The symlink at the end fails without Developer Mode; a fresh library is what counts. The CLI
# also prints its progress on stderr, which "Stop" would treat as a failure.
$ErrorActionPreference = "Continue"
npx --yes @tauri-apps/cli@2.11.5 android build --apk --target aarch64 --config '{\"build\":{\"beforeBuildCommand\":\"\"}}' 2>&1 |
    ForEach-Object { "$_" } | Where-Object { $_ -match '^\s*(error|Finished|Error)' } | Select-Object -Last 5
$ErrorActionPreference = "Stop"
if (-not (Test-Path $lib)) { throw "the Rust build produced no $lib" }

$android = "$root\src-tauri\gen\android"
$apkDir = "$android\app\build\outputs\apk\arm64\release"
Remove-Item "$apkDir\*.apk" -ErrorAction SilentlyContinue
Copy-Item $lib "$android\app\src\main\jniLibs\arm64-v8a\libapp_lib.so" -Force
Set-Location $android
.\gradlew.bat assembleArm64Release -x rustBuildArm64Release --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "Gradle failed" }

$version = (Get-Content "$root\src-tauri\tauri.conf.json" -Raw | ConvertFrom-Json).version
$outDir = "$root\target\android"
New-Item -ItemType Directory -Force $outDir | Out-Null
$out = "$outDir\Enzi-Music_${version}_arm64.apk"
if (Test-Path "$apkDir\app-arm64-release.apk") {
    # Gradle signed it with the release key (keystore.properties) and aligned it already.
    Copy-Item "$apkDir\app-arm64-release.apk" $out -Force
    $signedWith = "release key"
} else {
    $bt = Get-ChildItem "$env:ANDROID_HOME\build-tools" | Sort-Object Name | Select-Object -Last 1
    $aligned = "$outDir\aligned.apk"
    & "$($bt.FullName)\zipalign.exe" -f -p 4 "$apkDir\app-arm64-release-unsigned.apk" $aligned
    & "$($bt.FullName)\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android `
        --key-pass pass:android --ks-key-alias androiddebugkey --out $out $aligned
    Remove-Item $aligned
    Remove-Item "$out.idsig" -ErrorAction SilentlyContinue
    $signedWith = "debug key"
}
"Built $out ({0:N1} MB, signed with the $signedWith)" -f ((Get-Item $out).Length / 1MB)
