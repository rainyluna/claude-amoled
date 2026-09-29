# Updating Claude AMOLED for New App Versions

When the Claude app updates (`com.anthropic.claude`), ProGuard/R8 obfuscation assigns new class and field names. Follow this step-by-step guide to update the module.

---

## Step 1: Pull the New APK

```bash
# 1. Get path of installed APK
adb shell pm path com.anthropic.claude

# 2. Pull base.apk
mkdir -p claude_analysis_new
adb pull <path_from_above>/base.apk claude_analysis_new/base.apk
```

---

## Step 2: Extract & Decompile

```bash
unzip -o claude_analysis_new/base.apk "*.dex" -d claude_analysis_new/
```

Decompile `MainActivity`:
```bash
/home/vertigo/phone/research/claude_analysis/jadx/bin/jadx --single-class "com.anthropic.claude.mainactivity.MainActivity" -d claude_analysis_new/decompiled claude_analysis_new/classes.dex
```

---

## Step 3: Key Tokens & Classes to Identify

Claude uses Jetpack Compose and custom theme tokens:

| Token Category | Previous (v1.0) | Current (v1.260923.20) | Identification Method |
|---|---|---|---|
| **Palette Constants** | `fq5` | `t58` | Class with constants `4280295455L` (`0xFF20201F`), `4280163869L` (`0xFF1E1E1D`), `4279900697L` (`0xFF1A1A19`), `4279571733L` (`0xFF151515`). |
| **Dark Theme Tokens** | `qul` | `g4t` | Fields assigning static colors from Palette class (e.g. `n = t58.u; o = t58.x;`). |
| **Dark Theme Palette** | `lz2` | `c24` | Static class holding dark palette fields (`b0`–`i0`). |
| **Dark Theme Provider**| `tz2` | `i24` | Singleton implementing theme interface, aliases dark fields from `c24`. |
| **Theme Holder** | `rp5` | `f58` | Class with `public static final zc6 c` (light) and `public static final zc6 d` (dark). |
| **Theme Instance** | `hj4` | `zc6` | Class instantiated with boolean `b` (`true` for dark). |
| **Scheme Converter** | `jnb.i(hj4)` | `w48.n(zc6)` | Method converting design tokens to Material 3 `ColorScheme` (`l58`). |
| **Material 3 Scheme** | `wp5` | `l58` | Class matching `ColorScheme(primary=` `toString()`. |
| **Artifact / CSS** | `mad` | `imh` | Class in `classes3.dex` containing `rgba(48, 48, 46, 1)` in map field `a`. |

---

## Step 4: Update HookEntry.java

Edit [`src/com/vertigo/claudeamoled/HookEntry.java`](src/com/vertigo/claudeamoled/HookEntry.java):
1. In `hookKnownClasses()`:
   - Add new palette class names to `hookPaletteClass`:
     ```java
     hookPaletteClass(cl, "NEW_CLASS", new String[]{...});
     ```
   - Add new theme holder to `hookThemeHolder(cl, "NEW_HOLDER")`.
   - Add new constructor hook: `hookThemeInstanceConstructor(cl, "NEW_TOKEN_CLASS")`.
   - Add new converter hook: `hookConverter(cl, "NEW_CONVERTER", "method", "NEW_TOKEN_CLASS")`.
   - Add new CSS class: `hookCssClass(cl, "NEW_CSS_CLASS")`.
2. In `hookDynamicScheme()`:
   - Add new classes to the `ClassLoader.loadClass` early/late interceptor.

---

## Step 5: Build, Install & Verify

1. Bump `versionCode` and `versionName` in `AndroidManifest.xml`.
2. Run build:
   ```bash
   ./build.sh
   ```
3. Install:
   ```bash
   adb install -r claude-amoled.apk
   adb shell am force-stop com.anthropic.claude
   adb shell am start -n com.anthropic.claude/.mainactivity.MainActivity
   ```
4. Check pixel blackness:
   ```bash
   adb shell screencap -p /sdcard/check.png && adb pull /sdcard/check.png .
   python3 -c "
   from PIL import Image
   import numpy as np
   arr = np.array(Image.open('check.png'))
   sub = arr[200:1500, 50:1000, :3]
   black = (sub[:,:,0]==0)&(sub[:,:,1]==0)&(sub[:,:,2]==0)
   print(f'Pure black: {np.mean(black)*100:.2f}%')
   "
   ```
