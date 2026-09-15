package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (Throwable t) {
            return null;
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return new XC_MethodHook.Unhook();
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        return new XC_MethodHook.Unhook();
    }

    public static XC_MethodHook.Unhook findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        return new XC_MethodHook.Unhook();
    }

    public static XC_MethodHook.Unhook findAndHookConstructor(String className, ClassLoader classLoader, Object... parameterTypesAndCallback) {
        return new XC_MethodHook.Unhook();
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static long getLongField(Object obj, String fieldName) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            return f.getLong(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setLongField(Object obj, String fieldName, long value) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            f.setLong(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try {
            Field f = findField(clazz, fieldName);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field f = findField(clazz, fieldName);
            f.setAccessible(true);
            f.set(null, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static long getStaticLongField(Class<?> clazz, String fieldName) {
        try {
            Field f = findField(clazz, fieldName);
            f.setAccessible(true);
            return f.getLong(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setStaticLongField(Class<?> clazz, String fieldName, long value) {
        try {
            Field f = findField(clazz, fieldName);
            f.setAccessible(true);
            f.setLong(null, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        return null;
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return null;
    }

    public static Field findField(Class<?> clazz, String fieldName) {
        Class<?> cl = clazz;
        while (cl != null) {
            try {
                Field f = cl.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
            cl = cl.getSuperclass();
        }
        throw new RuntimeException(new NoSuchFieldException(fieldName));
    }
}
