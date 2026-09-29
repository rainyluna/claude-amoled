package com.vertigo.claudeamoled;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
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

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + "Initializing Claude AMOLED hook in " + lpparam.processName);

        final ClassLoader cl = lpparam.classLoader;

        // 1. Hook MainActivity to guarantee AMOLED window backgrounds & status bar
        hookWindow(cl);

        // 2. Hook direct known palette and theme classes (both legacy and current build)
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

        // Matches dark greys (RGB <= 56, similar channels, opaque)
        return a >= 200 && r <= 56 && g <= 56 && b <= 56 && Math.abs(r - g) <= 8 && Math.abs(g - b) <= 8;
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
        // --- 1. Palette Color Constants: fq5 (v1.0), t58 (v1.260923.20) ---
        hookPaletteClass(cl, "fq5", new String[]{"v", "w", "x", "y", "z", "s", "u", "t", "r"});
        hookPaletteClass(cl, "t58", new String[]{"s", "t", "u", "v", "w", "x", "y", "z", "A", "B", "r"});

        // --- 2. Dark Theme Color Tokens: qul (v1.0), g4t (v1.260923.20) ---
        hookPaletteClass(cl, "qul", new String[]{"n", "o", "p", "q", "r", "s", "E", "I", "H", "Q"});
        hookPaletteClass(cl, "g4t", new String[]{"n", "o", "p", "q", "r", "s", "E", "F", "G", "H", "I", "Q"});

        // --- 3. Dark Theme Palette: lz2 (v1.0), c24 (v1.260923.20) ---
        hookPaletteClass(cl, "lz2", new String[]{"N", "O", "P", "Q", "R", "S", "T", "U"});
        hookPaletteClass(cl, "c24", new String[]{"b0", "c0", "d0", "e0", "f0", "g0", "h0", "i0"});

        // --- 4. Dark Theme Provider: tz2 (v1.0), i24 (v1.260923.20) ---
        hookPaletteClass(cl, "tz2", new String[]{"O", "P", "Q", "R", "S", "T", "U"});
        hookPaletteClass(cl, "i24", new String[]{"c0", "d0", "e0", "f0", "g0", "h0", "i0", "j0"});

        // --- 5. Theme Holder: rp5.d (v1.0), f58.d (v1.260923.20) ---
        hookThemeHolder(cl, "rp5");
        hookThemeHolder(cl, "f58");

        // --- 6. Design Tokens Theme Instance Constructor: hj4 (v1.0), zc6 (v1.260923.20) ---
        hookThemeInstanceConstructor(cl, "hj4");
        hookThemeInstanceConstructor(cl, "zc6");

        // --- 7. Scheme Converter: jnb.i(hj4) (v1.0), w48.n(zc6) (v1.260923.20) ---
        hookConverter(cl, "jnb", "i", "hj4");
        hookConverter(cl, "w48", "n", "zc6");

        // --- 8. Webview / Artifact CSS variables: mad (v1.0), imh (v1.260923.20) ---
        hookCssClass(cl, "mad");
        hookCssClass(cl, "imh");
    }

    private void hookPaletteClass(final ClassLoader cl, final String className, final String[] fieldNames) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz != null) {
                final Class<?> targetClass = clazz;
                patchStaticPalette(targetClass, fieldNames);
                try {
                    XposedHelpers.findAndHookMethod(clazz, "<clinit>", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            patchStaticPalette(targetClass, fieldNames);
                        }
                    });
                } catch (Throwable ignored) {}
                XposedBridge.log(TAG + "Hooked palette class: " + className);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on " + className + ": " + t.getMessage());
        }
    }

    private static void patchStaticPalette(Class<?> clazz, String[] fieldNames) {
        if (clazz == null) return;
        if (fieldNames != null) {
            for (String fName : fieldNames) {
                try {
                    Field f = clazz.getDeclaredField(fName);
                    f.setAccessible(true);
                    f.setLong(null, COLOR_AMOLED_BLACK);
                } catch (Throwable ignored) {}
            }
        }
        // Also scan static long fields for any additional dark greys
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == long.class) {
                try {
                    f.setAccessible(true);
                    long val = f.getLong(null);
                    if (isDarkGreyColor(val)) {
                        f.setLong(null, COLOR_AMOLED_BLACK);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private void hookThemeHolder(final ClassLoader cl, final String className) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz != null) {
                final Class<?> targetClass = clazz;
                patchThemeHolder(targetClass);
                try {
                    XposedHelpers.findAndHookMethod(clazz, "<clinit>", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            patchThemeHolder(targetClass);
                        }
                    });
                } catch (Throwable ignored) {}
                XposedBridge.log(TAG + "Hooked theme holder: " + className);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on " + className + ": " + t.getMessage());
        }
    }

    private static void patchThemeHolder(Class<?> holderClass) {
        if (holderClass == null) return;
        try {
            Field dField = holderClass.getDeclaredField("d");
            dField.setAccessible(true);
            Object darkTheme = dField.get(null);
            if (darkTheme != null) {
                patchObjectColors(darkTheme);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error patching " + holderClass.getName() + ".d: " + t.getMessage());
        }
    }

    private void hookThemeInstanceConstructor(final ClassLoader cl, final String className) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz != null) {
                for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object instance = param.thisObject;
                            if (instance != null) {
                                try {
                                    // Field 'b' is boolean isDarkTheme in both hj4 and zc6
                                    Field bField = instance.getClass().getDeclaredField("b");
                                    bField.setAccessible(true);
                                    if (bField.getBoolean(instance)) {
                                        patchObjectColors(instance);
                                    }
                                } catch (NoSuchFieldException e) {
                                    patchObjectColors(instance);
                                }
                            }
                        }
                    });
                }
                XposedBridge.log(TAG + "Hooked constructor of: " + className);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on " + className + " constructor: " + t.getMessage());
        }
    }

    private void hookConverter(final ClassLoader cl, final String converterClass, final String methodName, final String paramClass) {
        try {
            Class<?> convClazz = XposedHelpers.findClassIfExists(converterClass, cl);
            Class<?> pClazz = XposedHelpers.findClassIfExists(paramClass, cl);
            if (convClazz != null && pClazz != null) {
                XposedHelpers.findAndHookMethod(convClazz, methodName, pClazz, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object colorScheme = param.getResult();
                        if (colorScheme != null) {
                            patchColorScheme(colorScheme);
                        }
                    }
                });
                XposedBridge.log(TAG + "Hooked " + converterClass + "." + methodName + "(" + paramClass + ")");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on " + converterClass + "." + methodName + ": " + t.getMessage());
        }
    }

    private void hookCssClass(final ClassLoader cl, final String className) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz != null) {
                final Class<?> targetClass = clazz;
                patchMadCss(targetClass);
                try {
                    XposedHelpers.findAndHookMethod(clazz, "<clinit>", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            patchMadCss(targetClass);
                        }
                    });
                } catch (Throwable ignored) {}
                XposedBridge.log(TAG + "Hooked CSS class: " + className);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on " + className + ": " + t.getMessage());
        }
    }

    private static void patchObjectColors(Object obj) {
        patchObjectColors(obj, 0);
    }

    private static void patchObjectColors(Object obj, int depth) {
        if (obj == null || depth > 2) return;
        Class<?> cl = obj.getClass();
        String className = cl.getName();
        if (className.startsWith("android.") || className.startsWith("java.") || className.startsWith("kotlin.")) {
            return;
        }
        while (cl != null && cl != Object.class) {
            for (Field f : cl.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    if (f.getType() == long.class) {
                        long val = f.getLong(obj);
                        if (isDarkGreyColor(val)) {
                            f.setLong(obj, COLOR_AMOLED_BLACK);
                        }
                    } else if (!f.getType().isPrimitive() && depth < 2) {
                        Object nested = f.get(obj);
                        if (nested != null) {
                            patchObjectColors(nested, depth + 1);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cl = cl.getSuperclass();
        }
    }

    private static void patchColorScheme(Object colorScheme) {
        if (colorScheme == null) return;
        // In wp5 / l58 (Material3 ColorScheme):
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
    private static void patchMadCss(Class<?> cssClass) {
        if (cssClass == null) return;
        try {
            Field aField = cssClass.getDeclaredField("a");
            aField.setAccessible(true);
            Object mapObj = aField.get(null);
            if (mapObj instanceof Map) {
                Map map = (Map) mapObj;
                Map newMap = new LinkedHashMap(map);
                boolean modified = false;
                for (Object key : newMap.keySet()) {
                    Object val = newMap.get(key);
                    if (val instanceof String) {
                        String s = (String) val;
                        if (s.contains("rgba(48, 48, 46, 1)") || s.contains("rgba(38, 38, 36, 1)") || s.contains("rgba(20, 20, 19, 1)")) {
                            s = s.replace("rgba(48, 48, 46, 1)", "rgba(0, 0, 0, 1)")
                                 .replace("rgba(38, 38, 36, 1)", "rgba(0, 0, 0, 1)")
                                 .replace("rgba(20, 20, 19, 1)", "rgba(0, 0, 0, 1)");
                            newMap.put(key, s);
                            modified = true;
                        }
                    }
                }
                if (modified) {
                    try {
                        map.putAll(newMap);
                    } catch (Throwable t) {
                        aField.set(null, newMap);
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error patching CSS on " + cssClass.getName() + ": " + t.getMessage());
        }
    }

    /**
     * Dynamic scanner to identify and hook ColorScheme and theme methods even after future app updates.
     */
    private void hookDynamicScheme(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Class<?> loadedClass = (Class<?>) param.getResult();
                    if (loadedClass == null) return;

                    String name = loadedClass.getName();
                    if (name.startsWith("android.") || name.startsWith("java.") || name.startsWith("kotlin.")) {
                        return;
                    }

                    // Explicit late-loading check for target classes
                    if ("t58".equals(name)) {
                        patchStaticPalette(loadedClass, new String[]{"s", "t", "u", "v", "w", "x", "y", "z", "A", "B", "r"});
                    } else if ("g4t".equals(name)) {
                        patchStaticPalette(loadedClass, new String[]{"n", "o", "p", "q", "r", "s", "E", "F", "G", "H", "I", "Q"});
                    } else if ("c24".equals(name)) {
                        patchStaticPalette(loadedClass, new String[]{"b0", "c0", "d0", "e0", "f0", "g0", "h0", "i0"});
                    } else if ("i24".equals(name)) {
                        patchStaticPalette(loadedClass, new String[]{"c0", "d0", "e0", "f0", "g0", "h0", "i0", "j0"});
                    } else if ("f58".equals(name)) {
                        patchThemeHolder(loadedClass);
                    } else if ("imh".equals(name)) {
                        patchMadCss(loadedClass);
                    }

                    // Check if class is ColorScheme (has toString with "ColorScheme(primary=")
                    try {
                        Method toStringMethod = loadedClass.getDeclaredMethod("toString");
                        if (toStringMethod != null && !Modifier.isAbstract(loadedClass.getModifiers())) {
                            for (Constructor<?> ctor : loadedClass.getDeclaredConstructors()) {
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
