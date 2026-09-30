# Claude AMOLED

LSPosed module targeting the official Claude for Android client (`com.anthropic.claude`). Converts the dark theme into an opaque `#000000` AMOLED black palette across Jetpack Compose surfaces, system bars, and WebView artifact sandboxes.

## Technical Architecture

- **Jetpack Compose ColorScheme**: Intercepts Compose theme initialization and patches 64-bit color values (`ULong`) for background, surface, and surface container tiers to pure black (`0xFF00000000000000L`).
- **Surface Elevation & Borders**: Retains surface borders and container elevation distinctions so input pills (model selector, upgrades, speech-to-text) remain visually defined over black backdrops.
- **WebView / Artifact Tokens**: Patches internal CSS tokens (`mad.a`) responsible for rendering Claude artifact previews, enforcing `#000000` background styling inside the embedded web runtime.
- **System Bars**: Automatically forces status bar and navigation bar decor views to pure black on activity creation.
- **Dynamic Hooking**: Scans loaded classes via `ClassLoader.loadClass` to identify `ColorScheme` constructors by signature, maintaining compatibility across minor Anthropic client updates.

## Prerequisites

- Android 8.0+ (API 26-36).
- Working LSPosed / Xposed framework.
- Java JDK 17.
- Android SDK Build-Tools (34.0.0+) and Android API 34 platform jar (`android.jar`).

## Building from Source

```bash
git clone https://github.com/rainyluna/claude-amoled.git
cd claude-amoled
chmod +x build.sh
./build.sh
```

### Build Pipeline
1. `javac` compiles standalone Xposed stubs in `stubs/`.
2. `javac` compiles `src/com/vertigo/claudeamoled/HookEntry.java` against `android.jar` and compiled stubs.
3. `d8` converts classes into `classes.dex` targeting API 34.
4. `aapt2` compiles and links package resources and manifest.
5. `zip` packages DEX and assets into unaligned APK.
6. `zipalign` 4-byte aligns the package.
7. `apksigner` signs using debug RSA-2048 keys.
8. Output artifact: `claude-amoled.apk`.

## Installation

```bash
adb install -r claude-amoled.apk
```
Enable the module in LSPosed Manager, confirm `com.anthropic.claude` is checked in scope, then force-stop and launch Claude:
```bash
adb shell am force-stop com.anthropic.claude
adb shell am start -n com.anthropic.claude/.MainActivity
```
