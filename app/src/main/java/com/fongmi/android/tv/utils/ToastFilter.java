package com.fongmi.android.tv.utils;

import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.github.catvod.utils.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 静默源 JAR 发出的状态 Toast。
 *
 * <p>源是动态加载的，某些源在代理 m3u8、加载弹幕或重新解析时会直接调用
 * {@link Toast}，这类调用不会经过 {@link Notify}，所以普通的提示去重无法拦截。
 * Android 的 Toast 最终都通过 Toast.sService 进入通知服务；这里在应用进程内包一层
 * 代理，只丢弃可识别的源状态消息，真正的错误和用户操作提示仍照常显示。</p>
 */
public final class ToastFilter {

    private static volatile boolean installed;

    private ToastFilter() {
    }

    public static void install() {
        if (installed || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            Field serviceField = Toast.class.getDeclaredField("sService");
            serviceField.setAccessible(true);

            Object service = serviceField.get(null);
            if (service == null) {
                Method getter = Toast.class.getDeclaredMethod("getService");
                getter.setAccessible(true);
                service = getter.invoke(null);
            }
            if (service == null) return;

            Class<?> serviceType = Class.forName("android.app.INotificationManager");
            if (!serviceType.isInstance(service)) return;

            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    serviceType.getClassLoader(),
                    new Class<?>[]{serviceType},
                    new ServiceHandler(service));
            serviceField.set(null, proxy);
            installed = true;
            Logger.d("ToastFilter: installed");
        } catch (Throwable e) {
            // Toast 不是播放主链路。部分 ROM 会收紧隐藏 API 反射，失败时保持原行为。
            Logger.e("ToastFilter: install failed", e);
        }
    }

    private static boolean shouldSuppress(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String value = text.trim();

        // 源的广告过滤统计（例如“饭：已过滤视频中广告(14条)”）。
        if (value.contains("广告") && (value.contains("过滤") || value.matches(".*\\(\\d+条\\).*"))) return true;

        // 源在加载/解析弹幕时的成功状态；失败信息保留给用户。
        if (value.contains("弹幕")
                && (value.contains("加载") || value.contains("解析") || value.contains("获取"))
                && !containsFailure(value)) return true;

        // 解析器的成功来源提示属于内部状态，播放页不需要显示。
        if (value.contains("解析来自") || value.contains("解析成功")) return true;
        return false;
    }

    private static boolean containsFailure(String value) {
        return value.contains("失败") || value.contains("错误") || value.contains("无法") || value.contains("异常");
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
            if (("enqueueToast".equals(name) || "enqueueTextToast".equals(name))
                    && shouldSuppress(textFromArgs(name, args))) {
                return defaultValue(method.getReturnType());
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
