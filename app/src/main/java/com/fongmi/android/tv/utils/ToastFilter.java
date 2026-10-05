package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 静默源 JAR 发出的状态 Toast。
 *
 * <p>源是动态加载的，某些源在代理 m3u8、加载弹幕或重新解析时会直接调用
 * {@link Toast}，这类调用不会经过 {@link Notify}，所以普通的提示去重无法拦截。
 * Android 的 Toast 最终都通过 Toast.sService 进入通知服务；这里在应用进程内包一层
 * 代理。播放页中源脚本发出的 Toast（包括无文字的自定义进度条）全部静默，应用自身的
 * 用户操作提示仍照常显示。</p>
 */
public final class ToastFilter {

    private static volatile boolean installed;
    private static final Map<Activity, Boolean> playbackPages = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile long lastBlockedLogAt;

    private ToastFilter() {
    }

    public static void install() {
        if (installed || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        boolean toast = installService(Toast.class, "Toast");
        boolean notification = installService(NotificationManager.class, "Notification");
        installed = toast || notification;
        Logger.d("ToastFilter: installed toast=" + toast + " notification=" + notification);
    }

    /** Activity 生命周期切换时 App.activity() 会短暂为空，播放页主动声明状态才能封住这个窗口。 */
    public static void enterPlayback(Activity activity) {
        playbackPages.put(activity, true);
        Logger.d("ToastFilter: playbackPages=" + playbackPages.size());
    }

    public static void leavePlayback(Activity activity) {
        playbackPages.remove(activity);
        Logger.d("ToastFilter: playbackPages=" + playbackPages.size());
    }

    private static boolean installService(Class<?> owner, String label) {
        try {
            Field serviceField = owner.getDeclaredField("sService");
            serviceField.setAccessible(true);

            Object service = serviceField.get(null);
            if (service == null) {
                Method getter = owner.getDeclaredMethod("getService");
                getter.setAccessible(true);
                service = getter.invoke(null);
            }
            if (service == null) return false;

            Class<?> serviceType = Class.forName("android.app.INotificationManager");
            if (!serviceType.isInstance(service)) return false;

            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    serviceType.getClassLoader(),
                    new Class<?>[]{serviceType},
                    new ServiceHandler(service));
            serviceField.set(null, proxy);
            return true;
        } catch (Throwable e) {
            // 部分 ROM 会收紧隐藏 API 反射，单项失败时不影响另一项和播放主链路。
            Logger.e("ToastFilter: " + label + " install failed", e);
            return false;
        }
    }

    private static boolean shouldSuppress(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String value = text.trim();

        // 源的广告过滤统计（例如“饭：已过滤视频中广告(14条)”）。
        if (value.contains("广告") && (value.contains("过滤") || value.matches(".*\\(\\d+条\\).*"))) return true;

        // Danmaku belongs to the provider's background work. Success and failure are both silent.
        if (value.contains("弹幕")) return true;

        // 解析器的成功来源提示属于内部状态，播放页不需要显示。
        if (value.contains("解析来自") || value.contains("解析成功")) return true;
        return false;
    }

    private static boolean isPlaybackPage() {
        Activity activity = App.activity();
        return !playbackPages.isEmpty() || activity != null && "com.fongmi.android.tv.ui.activity.VideoActivity".equals(activity.getClass().getName());
    }

    /** A source may use a text Toast or a custom Toast containing only a progress view. */
    private static boolean isSourceCaller() {
        return SourceUiOrigin.findCaller() != null;
    }

    /** All first-party player feedback is routed through Notify; every other playback Toast is external. */
    private static boolean isTrustedAppToastCaller() {
        for (StackTraceElement item : Thread.currentThread().getStackTrace()) {
            if (item.getClassName().startsWith("com.fongmi.android.tv.utils.Notify")) return true;
        }
        return false;
    }

    private static boolean isTrustedAppNotificationCaller() {
        boolean trusted = false;
        for (StackTraceElement item : Thread.currentThread().getStackTrace()) {
            String name = item.getClassName();
            if (name.startsWith("com.fongmi.android.tv.service.PlaybackService")
                    || name.startsWith("com.fongmi.android.tv.service.DownloadService")
                    || name.startsWith("com.fongmi.android.tv.service.WebDAVSyncService")
                    || name.startsWith("com.fongmi.android.tv.service.CastService")
                    || name.startsWith("com.fongmi.android.tv.utils.Notify")) trusted = true;
        }
        return trusted;
    }

    private static String notificationText(Object[] args) {
        if (args == null) return "";
        for (Object arg : args) if (arg instanceof Notification) {
            Notification value = (Notification) arg;
            CharSequence title = value.extras == null ? null : value.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = value.extras == null ? null : value.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(text)) return title + " · " + text;
            if (!TextUtils.isEmpty(title)) return title.toString();
            if (!TextUtils.isEmpty(text)) return text.toString();
        }
        return "<custom-notification>";
    }

    private static String textFromArgs(String methodName, Object[] args) {
        if (args == null) return "";

        if ("enqueueTextToast".equals(methodName)) {
            for (Object arg : args) if (arg instanceof CharSequence) return arg.toString();
            return "";
        }

        if (!"enqueueToast".equals(methodName)) return "";
        // API 31、targetSdk 28 仍走 enqueueToast；第三个参数是 Toast.TN。
        for (Object arg : args) {
            String text = textFromToastToken(arg, 0);
            if (!TextUtils.isEmpty(text)) return text;
        }
        return "";
    }

    private static String textFromToastToken(Object token, int depth) {
        if (token == null || depth > 2) return "";
        if (token instanceof View) return textFromView((View) token, 0);

        Class<?> type = token.getClass();
        while (type != null) {
            for (String name : new String[]{"mNextView", "mView"}) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(token);
                    if (value instanceof WeakReference) value = ((WeakReference<?>) value).get();
                    String text = textFromToastToken(value, depth + 1);
                    if (!TextUtils.isEmpty(text)) return text;
                } catch (Throwable ignored) {
                    // 不同 Android/厂商版本的 Toast.TN 字段不同，继续尝试其它字段。
                }
            }
            type = type.getSuperclass();
        }
        return "";
    }

    private static String textFromView(View view, int depth) {
        if (view == null || depth > 3) return "";
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            return text == null ? "" : text.toString();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String text = textFromView(group.getChildAt(i), depth + 1);
                if (!TextUtils.isEmpty(text)) return text;
            }
        }
        return "";
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Void.TYPE || !type.isPrimitive()) return null;
        if (type == Boolean.TYPE) return false;
        if (type == Character.TYPE) return '\0';
        if (type == Byte.TYPE) return (byte) 0;
        if (type == Short.TYPE) return (short) 0;
        if (type == Integer.TYPE) return 0;
        if (type == Long.TYPE) return 0L;
        if (type == Float.TYPE) return 0f;
        if (type == Double.TYPE) return 0d;
        return null;
    }

    private static final class ServiceHandler implements InvocationHandler {

        private final Object delegate;

        private ServiceHandler(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.startsWith("enqueueNotification")
                    && (isSourceCaller() || isPlaybackPage() && !isTrustedAppNotificationCaller())) {
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastBlockedLogAt >= 1000) {
                    lastBlockedLogAt = now;
                    Logger.i("PlayerPopup: action=blocked origin=source-notification text=" + notificationText(args));
                }
                return defaultValue(method.getReturnType());
            }
            if ("enqueueToast".equals(name) || "enqueueTextToast".equals(name)) {
                String text = textFromArgs(name, args);
                boolean source = isSourceCaller();
                boolean externalPlayback = isPlaybackPage() && !isTrustedAppToastCaller();
                if (source || externalPlayback || shouldSuppress(text)) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (now - lastBlockedLogAt >= 1000) {
                        lastBlockedLogAt = now;
                        String detail = TextUtils.isEmpty(text) ? "<custom-view>" : text.replace('\n', ' ');
                        Logger.i("PlayerPopup: action=blocked origin=" + (source ? "source" : externalPlayback ? "external" : "provider-status")
                                + " text=" + detail);
                    }
                    return defaultValue(method.getReturnType());
                }
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
