package com.fongmi.android.tv.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;

import androidx.core.graphics.drawable.IconCompat;

import com.fongmi.android.tv.R;
import com.github.catvod.utils.Logger;

/** Notification-only pixels: fixed transparent canvas, with padding baked into the artwork. */
public final class NotificationSmallIcon {
    private static IconCompat cached;

    private NotificationSmallIcon() {}

    public static synchronized IconCompat get(Context context) {
        if (cached != null) return cached;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_notification_mark, options);
        if (bitmap == null) {
            Logger.d("NotificationSmallIcon rasterDecodeFailed=true");
            return IconCompat.createWithResource(context, R.drawable.ic_notification_small);
        }
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        cached = IconCompat.createWithBitmap(bitmap);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        Rect ink = new Rect();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Color.alpha(pixels[y * width + x]) > 16) ink.union(x, y, x + 1, y + 1);
            }
        }
        Logger.d("NotificationSmallIcon source=ic_notification_mark canvas=" + width + "x" + height
                + " ink=" + ink.toShortString() + " padding=baked density=none submittedType=bitmap");
        return cached;
    }
}
