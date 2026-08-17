package com.fongmi.android.tv;

import android.content.Context;

import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.utils.ResUtil;

public class Product {

    public static int getDeviceType() {
        return 1;
    }

    public static int getColumn(Context context) {
        int count = ResUtil.isLand(context) ? 7 : ResUtil.isExpanded(context) ? 6 : 5;
        return Math.max(1, count - Setting.getSize());
    }

    public static int getColumn(Context context, Style style) {
        return Math.max(1, style.isLand() ? getColumn(context) - 1 : getColumn(context));
    }

    public static int[] getSpec(Context context) {
        return getSpec(context, Style.rect());
    }

    public static int[] getSpec(Context context, Style style) {
        int column = getColumn(context, style);
        int space = ResUtil.dp2px(32) + ResUtil.dp2px(16 * (column - 1));
        if (style.isOval()) space += ResUtil.dp2px(column * 16);
        return getSpec(context, space, column, style);
    }

    public static int[] getSpec(Context context, int space, int column) {
        return getSpec(context, space, column, Style.rect());
    }

    private static int[] getSpec(Context context, int space, int column, Style style) {
        int base = Math.max(1, ResUtil.getScreenWidth(context) - space);
        int width = Math.max(1, base / Math.max(1, column));
        int height = (int) (width / style.getRatio());
        return new int[]{width, height};
    }

    public static int getEms() {
        return Math.min(ResUtil.getScreenWidth() / ResUtil.sp2px(20), 25);
    }
}
