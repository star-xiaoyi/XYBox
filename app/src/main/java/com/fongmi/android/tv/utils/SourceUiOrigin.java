package com.fongmi.android.tv.utils;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Tracks source helper classes as well as spider entry points, including obfuscated JAR helpers. */
public final class SourceUiOrigin {
    private static final CopyOnWriteArrayList<WeakReference<ClassLoader>> loaders = new CopyOnWriteArrayList<>();
    private static final Map<String, Boolean> classes = new ConcurrentHashMap<>();

    private SourceUiOrigin() { }

    public static synchronized void register(ClassLoader loader) {
        if (loader == null) return;
        for (WeakReference<ClassLoader> ref : loaders) {
            if (ref.get() == loader) return;
            if (ref.get() == null) loaders.remove(ref);
        }
        loaders.add(new WeakReference<>(loader));
        classes.clear();
    }

    public static String findCaller() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String name = frame.getClassName();
            if (name.startsWith("com.github.catvod.spider.") || name.startsWith("com.github.catvod.parser.")
                    || name.startsWith("com.fongmi.quickjs.") || name.startsWith("com.whl.quickjs.")) return name;
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.")
                    || name.startsWith("androidx.") || name.startsWith("dalvik.")) continue;
            if (classes.computeIfAbsent(name, SourceUiOrigin::isSourceClass)) return name;
        }
        return null;
    }

    private static boolean isSourceClass(String name) {
        for (WeakReference<ClassLoader> ref : loaders) {
            ClassLoader loader = ref.get();
            if (loader == null) continue;
            try {
                // Parent-loaded host classes remain trusted. Merely resolving a class never runs its initializer.
                if (Class.forName(name, false, loader).getClassLoader() == loader) return true;
            } catch (ClassNotFoundException | LinkageError ignored) { }
        }
        return false;
    }
}
