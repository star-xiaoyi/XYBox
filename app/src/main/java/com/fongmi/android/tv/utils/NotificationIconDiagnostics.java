package com.fongmi.android.tv.utils;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.utils.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Records icon provenance once per notification source/type; never records media or text. */
public final class NotificationIconDiagnostics {
    private static final Set<String> RECORDED = Collections.synchronizedSet(new HashSet<>());

    private NotificationIconDiagnostics() {}

    public static void record(Context context, String source, Notification notification) {
        try {
            String small = describe(context, notification.getSmallIcon());
            String large = describe(context, notification.getLargeIcon());
            if (!RECORDED.add(source + small + large)) return;
            Logger.d("NotificationIcon source=" + source + " version=" + BuildConfig.VERSION_NAME
                    + " small=" + small + " large=" + large
                    + " appIcon=" + resource(context, context.getApplicationInfo().icon)
                    + " template=" + notification.extras.getString("android.template", "none"));
            recordApplicationIcon(context, source);
            android.content.Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch != null) {
                android.content.pm.ActivityInfo entry = launch.resolveActivityInfo(context.getPackageManager(), 0);
                if (entry != null) Logger.d("NotificationLauncherIcon source=" + source
                        + " resource=" + resource(context, entry.getIconResource())
                        + " separateFromApp=" + (entry.getIconResource() != context.getApplicationInfo().icon));
            }
        } catch (Exception error) {
            Logger.d("NotificationIcon source=" + source + " diagnosticsError=" + error.getClass().getSimpleName());
        }
    }

    private static String describe(Context context, Icon icon) {
        if (icon == null) return "none";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "frameworkIcon(api<28)";
        int type = icon.getType();
        return type == Icon.TYPE_RESOURCE ? "resource(" + resource(context, icon.getResId()) + ")" : "type=" + type;
    }

    private static String resource(Context context, int id) {
        if (id == 0) return "none";
        try { return context.getResources().getResourceName(id); }
        catch (Exception ignored) { return Integer.toHexString(id); }
    }

    private static void recordApplicationIcon(Context context, String source) {
        Drawable original = context.getApplicationInfo().loadIcon(context.getPackageManager());
        if (original == null) return;
        Drawable icon = original.getConstantState() == null ? original
                : original.getConstantState().newDrawable(context.getResources()).mutate();
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Rect oldBounds = new Rect(icon.getBounds());
        try {
            icon.setBounds(0, 0, 64, 64);
            icon.draw(new Canvas(bitmap));
            Rect opaque = new Rect();
            Rect ink = new Rect();
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    if (Color.alpha(pixel) > 16) opaque.union(x, y, x + 1, y + 1);
                    if (Color.alpha(pixel) > 200 && Color.red(pixel) < 96
                            && Color.green(pixel) < 96 && Color.blue(pixel) < 96)
                        ink.union(x, y, x + 1, y + 1);
                }
            }
            Logger.d("NotificationAppIcon source=" + source + " loadedType=" + original.getClass().getSimpleName()
                    + " canvas=64x64 opaque=" + opaque.toShortString() + " darkInk=" + ink.toShortString()
                    + " inkWidthRatio=" + ink.width() / 64f + " sample=app_loaded_icon_not_system_ui");
        } finally {
            icon.setBounds(oldBounds);
            bitmap.recycle();
        }
    }
}
