package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.github.catvod.utils.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Sources may resolve media in the background, but cannot add their own status windows to the app. */
public final class SourceUiGuard {
    private static final Map<ViewGroup, Boolean> guarded = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<WindowManager, WeakReference<WindowManager>> managers = new WeakHashMap<>();

    private SourceUiGuard() { }

    public static void protectWindow(Activity activity, View root) {
        protect(activity.getWindow().getDecorView(), "decor");
        protect(activity.findViewById(android.R.id.content), "content");
        protect(root, "page");
        Logger.d("SourceUi: action=guard-installed page=" + activity.getClass().getSimpleName());
    }

    public static void protect(View view, String host) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        if (guarded.put(group, true) != null) return;
        group.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override public void onChildViewAdded(View parent, View child) {
                String caller = SourceUiOrigin.findCaller();
                if (caller == null) return;
                // Removing inside addView/measurement can corrupt the parent's traversal. Hide before any draw,
                // then detach after addView completes; a source's later removeView is harmless.
                child.setVisibility(View.GONE);
                child.setAlpha(0f);
                logBlocked("hierarchy-" + host, caller, child);
                parent.post(() -> {
                    if (child.getParent() == group) group.removeView(child);
                });
            }
            @Override public void onChildViewRemoved(View parent, View child) { }
        });
    }

    public static Object filterService(String name, Object service) {
        return Context.WINDOW_SERVICE.equals(name) && service instanceof WindowManager
                ? filterWindowManager((WindowManager) service) : service;
    }

    public static WindowManager filterWindowManager(WindowManager delegate) {
        if (delegate == null) return null;
        String caller = SourceUiOrigin.findCaller();
        if (caller == null || frameworkNeedsConcreteManager()) return delegate;
        synchronized (managers) {
            WeakReference<WindowManager> ref = managers.get(delegate);
            WindowManager existing = ref == null ? null : ref.get();
            if (existing != null) return existing;
            Map<View, Boolean> blockedViews = Collections.synchronizedMap(new WeakHashMap<>());
            WindowManager wrapper = (WindowManager) Proxy.newProxyInstance(WindowManager.class.getClassLoader(),
                    new Class<?>[]{WindowManager.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        View view = args != null && args.length > 0 && args[0] instanceof View ? (View) args[0] : null;
                        if ("addView".equals(name) && view != null) {
                            blockedViews.put(view, true);
                            String origin = SourceUiOrigin.findCaller();
                            logBlocked("window-manager", origin == null ? caller : origin, view);
                            return null;
                        }
                        // A status helper often updates/removes a window on its timer. Never throw for a blocked add.
                        if (view != null && blockedViews.containsKey(view) && ("updateViewLayout".equals(name)
                                || "removeView".equals(name) || "removeViewImmediate".equals(name))) return null;
                        try { return method.invoke(delegate, args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
            managers.put(delegate, new WeakReference<>(wrapper));
            return wrapper;
        }
    }

    private static boolean frameworkNeedsConcreteManager() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String name = frame.getClassName();
            // Window/Dialog setup casts to WindowManagerImpl internally. Preserve that setup contract.
            if (name.equals("android.view.Window") || name.equals("android.view.WindowManagerImpl")
                    || name.equals("android.app.Dialog") && frame.getMethodName().equals("<init>")) return true;
        }
        return false;
    }

    private static void logBlocked(String path, String caller, View view) {
        String text = textFrom(view, 0).replace('\n', ' ');
        if (text.length() > 160) text = text.substring(0, 160);
        Logger.i("SourceUi: action=blocked path=" + path + " caller=" + caller
                + " view=" + view.getClass().getName() + " text=" + text);
    }

    private static String textFrom(View view, int depth) {
        if (view == null || depth > 4) return "";
        if (view instanceof TextView) return String.valueOf(((TextView) view).getText());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < Math.min(group.getChildCount(), 12); i++) {
                String text = textFrom(group.getChildAt(i), depth + 1);
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }
}
