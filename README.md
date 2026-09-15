# Claude AMOLED (LSPosed Module)

An LSPosed / Xposed module that transforms the official **Claude for Android** (`com.anthropic.claude`) dark theme into a true **Pitch Black AMOLED (#000000)** experience.

Designed specifically for OLED / AMOLED displays to maximize battery life, reduce eye strain in low-light environments, and provide high contrast without breaking Jetpack Compose layouts.

---

## 🌟 Features

- **True Pitch Black AMOLED (`#000000`)**: Replaces dark grey backgrounds with pure black, turning off pixels on OLED displays.
- **Floating Surfaces & Clean Contrast**: Bottom input container, cards, and modal dialogs are styled with pure black backgrounds and subtle borders, keeping button pills (like model selector, upgrades, dictation) crisp and interactive.
- **Edge-to-Edge System Bar Integration**: Automatically applies pure black to the status bar and navigation bar decor views.
- **WebView & Artifact Blackout**: Patches internal CSS tokens (`mad.a`) for artifact previews and sandboxes to pure black.
- **Dynamic & Update Resilient**: Uses a combination of direct hooks and dynamic runtime detection to remain compatible across future Claude updates.
- **Zero Configuration**: Uses Android LSPosed modern metadata scope (`xposedscope`) to pre-select Claude automatically in LSPosed Manager.

---

## 📱 Requirements

- Android 8.0+ (API 26+) up to Android 16+
- Root access (KernelSU, APatch, or Magisk)
- Zygisk-LSPosed or compatible modern Xposed framework
- Official **Claude** app installed (`com.anthropic.claude`)

---

## 🚀 Installation

1. Download the latest `claude-amoled.apk` from the [Releases](https://github.com/rainyluna/claude-amoled/releases) page.
2. Install the APK on your rooted device.
3. Open **LSPosed Manager**:
   - Tap the module notification or navigate to Modules.
   - Enable **Claude AMOLED**.
   - Verify that **Claude** (`com.anthropic.claude`) is checked in the module's scope (pre-selected by default).
4. Force close and relaunch the Claude app.
5. Make sure Dark Mode is active in the Claude app settings or system-wide. Enjoy pure black AMOLED!

---

## 🛠️ Building from Source

The repository includes standalone Xposed stubs and an automated build script:

```bash
git clone https://github.com/rainyluna/claude-amoled.git
cd claude-amoled
./build.sh
```

The script compiles the stubs, converts sources to DEX via `d8`, compiles resources via `aapt2`, aligns with `zipalign`, and signs the output as `claude-amoled.apk`.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
