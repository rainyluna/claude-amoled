package com.vertigo.claudeamoled;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TARGET_PKG = "com.anthropic.claude";
    private static final String TAG = "[ClaudeAMOLED] ";

    // Compose Color for pure AMOLED black (0xFF000000)
    // Compose Color(val value: ULong) where sRGB is (argb << 32)
    private static final long COLOR_AMOLED_BLACK = 0xFF00000000000000L;

    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + "Initializing Claude AMOLED hook in " + lpparam.processName);

        final ClassLoader cl = lpparam.classLoader;

        // 1. Hook MainActivity to guarantee AMOLED window backgrounds & status bar
        hookWindow(cl);

        // 2. Hook direct known palette and theme classes for current build
        hookKnownClasses(cl);

        // 3. Hook dynamic theme and ColorScheme construction for update resilience
        hookDynamicScheme(cl);
    }

    /**
     * Checks if a Compose 64-bit Color long represents a dark grey background/surface.
     */
    private static boolean isDarkGreyColor(long colorVal) {
        if (colorVal == 0L) return false;
        long argb = (colorVal >>> 32) & 0xFFFFFFFFL;
        long a = (argb >>> 24) & 0xFF;
        long r = (argb >>> 16) & 0xFF;
        long g = (argb >>> 8) & 0xFF;
        long b = argb & 0xFF;

        // Matches dark greys (RGB <= 45, similar channels, opaque)
        return a >= 200 && r <= 45 && g <= 45 && b <= 45 && Math.abs(r - g) <= 8 && Math.abs(g - b) <= 8;
    }

    private void hookWindow(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    applyWindowBlack((Activity) param.thisObject);
                }
            });

            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    applyWindowBlack((Activity) param.thisObject);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook Activity window: " + t.getMessage());
        }
    }

    private static void applyWindowBlack(Activity activity) {
        if (activity == null) return;
        try {
            Window window = activity.getWindow();
            if (window != null) {
                window.setStatusBarColor(Color.BLACK);
                window.setNavigationBarColor(Color.BLACK);
                View decorView = window.getDecorView();
                if (decorView != null) {
                    decorView.setBackgroundColor(Color.BLACK);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void hookKnownClasses(final ClassLoader cl) {
        // --- 1. fq5 (Palette Color Constants) ---
        try {
            Class<?> fq5 = XposedHelpers.findClassIfExists("fq5", cl);
            if (fq5 != null) {
                XposedBridge.hookMethod(fq5.getDeclaredConstructor(), new XC_MethodHook() {}); // triggers clinit if needed
                patchFq5Fields(fq5);

                XposedHelpers.findAndHookMethod(fq5, "<clinit>", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        patchFq5Fields((Class<?>) param.thisObject);
                    }
                });
                XposedBridge.log(TAG + "Hooked fq5 palette");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on fq5: " + t.getMessage());
        }

        // --- 2. qul (Dark Theme Color Tokens) ---
        try {
            Class<?> qul = XposedHelpers.findClassIfExists("qul", cl);
            if (qul != null) {
                patchQulFields(qul);
                XposedHelpers.findAndHookMethod(qul, "<clinit>", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        patchQulFields((Class<?>) param.thisObject);
                    }
                });
                XposedBridge.log(TAG + "Hooked qul dark tokens");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on qul: " + t.getMessage());
        }

        // --- 3. lz2 & tz2 (Dark Theme Palette & Provider) ---
        try {
            Class<?> lz2 = XposedHelpers.findClassIfExists("lz2", cl);
            if (lz2 != null) {
                patchLz2Fields(lz2);
                XposedHelpers.findAndHookMethod(lz2, "<clinit>", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        patchLz2Fields((Class<?>) param.thisObject);
                    }
                });
                XposedBridge.log(TAG + "Hooked lz2");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on lz2: " + t.getMessage());
        }

        try {
            Class<?> tz2 = XposedHelpers.findClassIfExists("tz2", cl);
            if (tz2 != null) {
                patchTz2Fields(tz2);
                XposedHelpers.findAndHookMethod(tz2, "<clinit>", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        patchTz2Fields((Class<?>) param.thisObject);
                    }
                });
                XposedBridge.log(TAG + "Hooked tz2");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on tz2: " + t.getMessage());
        }

        // --- 4. rp5 (Theme Holder containing dark hj4 instance) ---
        try {
            Class<?> rp5 = XposedHelpers.findClassIfExists("rp5", cl);
            if (rp5 != null) {
                patchRp5(rp5);
                XposedHelpers.findAndHookMethod(rp5, "<clinit>", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        patchRp5((Class<?>) param.thisObject);
                    }
                });
                XposedBridge.log(TAG + "Hooked rp5");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on rp5: " + t.getMessage());
        }

        // --- 5. jnb.i (converts hj4 to Material 3 ColorScheme wp5) ---
        try {
            Class<?> jnb = XposedHelpers.findClassIfExists("jnb", cl);
            Class<?> hj4 = XposedHelpers.findClassIfExists("hj4", cl);
            if (jnb != null && hj4 != null) {
                XposedHelpers.findAndHookMethod(jnb, "i", hj4, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object colorScheme = param.getResult();
                        if (colorScheme != null) {
                            patchColorScheme(colorScheme);
                        }
                    }
                });
                XposedBridge.log(TAG + "Hooked jnb.i");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on jnb: " + t.getMessage());
        }

        // --- 6. mad (Webview / Artifact CSS variables) ---
        try {
            Class<?> mad = XposedHelpers.findClassIfExists("mad", cl);
            if (mad != null) {
                patchMadCss(mad);
                XposedHelpers.findAndHookMethod(mad, "<clinit>", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        patchMadCss((Class<?>) param.thisObject);
                    }
                });
                XposedBridge.log(TAG + "Hooked mad CSS variables");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on mad: " + t.getMessage());
        }
    }

    private static void patchFq5Fields(Class<?> clazz) {
        if (clazz == null) return;
        // Fields for dark grey tones: v, w, x, y, z, s, u, t, r
        String[] fields = {"v", "w", "x", "y", "z", "s", "u", "t", "r"};
        for (String fName : fields) {
            try {
                Field f = clazz.getDeclaredField(fName);
                f.setAccessible(true);
                f.setLong(null, COLOR_AMOLED_BLACK);
            } catch (Throwable ignored) {}
        }
    }

    private static void patchQulFields(Class<?> clazz) {
        if (clazz == null) return;
        String[] fields = {"n", "o", "p", "q", "r", "s", "E", "I", "H", "Q"};
        for (String fName : fields) {
            try {
                Field f = clazz.getDeclaredField(fName);
                f.setAccessible(true);
                f.setLong(null, COLOR_AMOLED_BLACK);
            } catch (Throwable ignored) {}
        }
    }

    private static void patchLz2Fields(Class<?> clazz) {
        if (clazz == null) return;
        String[] fields = {"N", "O", "P", "Q", "R", "S", "T", "U"};
        for (String fName : fields) {
            try {
                Field f = clazz.getDeclaredField(fName);
                f.setAccessible(true);
                f.setLong(null, COLOR_AMOLED_BLACK);
            } catch (Throwable ignored) {}
        }
    }

    private static void patchTz2Fields(Class<?> clazz) {
        if (clazz == null) return;
        String[] fields = {"O", "P", "Q", "R", "S", "T", "U"};
        for (String fName : fields) {
            try {
                Field f = clazz.getDeclaredField(fName);
                f.setAccessible(true);
                f.setLong(null, COLOR_AMOLED_BLACK);
            } catch (Throwable ignored) {}
        }
    }

    private static void patchRp5(Class<?> rp5Class) {
        if (rp5Class == null) return;
        try {
            // d is the dark theme hj4 instance
            Field dField = rp5Class.getDeclaredField("d");
            dField.setAccessible(true);
            Object darkTheme = dField.get(null);
            if (darkTheme != null) {
                patchObjectColors(darkTheme);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error patching rp5.d: " + t.getMessage());
        }
    }

    private static void patchObjectColors(Object obj) {
        if (obj == null) return;
        Class<?> cl = obj.getClass();
        while (cl != null && cl != Object.class) {
            for (Field f : cl.getDeclaredFields()) {
                if (f.getType() == long.class && !Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        long val = f.getLong(obj);
                        if (isDarkGreyColor(val)) {
                            f.setLong(obj, COLOR_AMOLED_BLACK);
                        }
                    } catch (Throwable ignored) {}
                }
            }
            cl = cl.getSuperclass();
        }
    }

    private static void patchColorScheme(Object colorScheme) {
        if (colorScheme == null) return;
        // In wp5 (Material3 ColorScheme):
        // background = n, surface = p, surfaceVariant = r,
        // surfaceBright = D, surfaceDim = E, surfaceContainer = F,
        // surfaceContainerHigh = G, surfaceContainerHighest = H,
        // surfaceContainerLow = I, surfaceContainerLowest = J
        String[] surfaceFields = {"n", "p", "r", "D", "E", "F", "G", "H", "I", "J"};
        Class<?> cl = colorScheme.getClass();
        for (String fieldName : surfaceFields) {
            try {
                Field f = cl.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.setLong(colorScheme, COLOR_AMOLED_BLACK);
            } catch (Throwable ignored) {}
        }
        // Also scan any remaining fields that might be dark grey
        patchObjectColors(colorScheme);
    }

    @SuppressWarnings("unchecked")
    private static void patchMadCss(Class<?> madClass) {
        if (madClass == null) return;
        try {
            Field aField = madClass.getDeclaredField("a");
            aField.setAccessible(true);
            Object mapObj = aField.get(null);
            if (mapObj instanceof Map) {
                Map map = (Map) mapObj;
                for (Object key : map.keySet()) {
                    Object val = map.get(key);
                    if (val instanceof String) {
                        String s = (String) val;
                        // Replace dark background rgba values with pure black
                        if (s.contains("rgba(48, 48, 46, 1)") || s.contains("rgba(38, 38, 36, 1)") || s.contains("rgba(20, 20, 19, 1)")) {
                            s = s.replace("rgba(48, 48, 46, 1)", "rgba(0, 0, 0, 1)")
                                 .replace("rgba(38, 38, 36, 1)", "rgba(0, 0, 0, 1)")
                                 .replace("rgba(20, 20, 19, 1)", "rgba(0, 0, 0, 1)");
                            map.put(key, s);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error patching mad CSS: " + t.getMessage());
        }
    }

    /**
     * Dynamic scanner to identify and hook ColorScheme and theme methods even after app updates.
     */
    private void hookDynamicScheme(final ClassLoader cl) {
        try {
            // Hook ClassLoader to catch classes as they are loaded
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Class<?> loadedClass = (Class<?>) param.getResult();
                    if (loadedClass == null) return;

                    String name = loadedClass.getName();
                    if (name.startsWith("android.") || name.startsWith("java.") || name.startsWith("kotlin.")) {
                        return;
                    }

                    // Check if class is ColorScheme (has toString with "ColorScheme(primary=")
                    try {
                        Method toStringMethod = loadedClass.getDeclaredMethod("toString");
                        if (toStringMethod != null && !Modifier.isAbstract(loadedClass.getModifiers())) {
                            // Check if constructor has ~48 long arguments
                            for (java.lang.reflect.Constructor<?> ctor : loadedClass.getDeclaredConstructors()) {
                                Class<?>[] params = ctor.getParameterTypes();
                                if (params.length >= 25 && params[0] == long.class) {
                                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                                        @Override
                                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                            patchColorScheme(param.thisObject);
                                        }
                                    });
                                    XposedBridge.log(TAG + "Dynamically hooked ColorScheme constructor: " + name);
                                    break;
                                }
                            }
                        }
                    } catch (NoSuchMethodException ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Dynamic scheme scanner note: " + t.getMessage());
        }
    }
}
