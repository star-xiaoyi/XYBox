package com.fongmi.android.tv.utils;

import android.app.Activity;

import androidx.annotation.ColorRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;

public final class ThemeUtil {

    private ThemeUtil() {
    }

    public static void applyNightMode() {
        int mode;
        if (Setting.getThemeMode() == Setting.THEME_LIGHT) mode = AppCompatDelegate.MODE_NIGHT_NO;
        else if (Setting.getThemeMode() == Setting.THEME_DARK) mode = AppCompatDelegate.MODE_NIGHT_YES;
        else mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static void applyAccent(Activity activity) {
        activity.getTheme().applyStyle(getAccentStyle(), true);
    }

    @StyleRes
    private static int getAccentStyle() {
        if (Setting.getAccentColor() == Setting.ACCENT_BLUE) return R.style.ThemeOverlay_XYBox_Accent_Blue;
        if (Setting.getAccentColor() == Setting.ACCENT_GREEN) return R.style.ThemeOverlay_XYBox_Accent_Green;
        if (Setting.getAccentColor() == Setting.ACCENT_PURPLE) return R.style.ThemeOverlay_XYBox_Accent_Purple;
        return R.style.ThemeOverlay_XYBox_Accent_Yellow;
    }

    @ColorRes
    public static int getAccentColorResource() {
        if (Setting.getAccentColor() == Setting.ACCENT_BLUE) return R.color.accent_blue;
        if (Setting.getAccentColor() == Setting.ACCENT_GREEN) return R.color.accent_green;
        if (Setting.getAccentColor() == Setting.ACCENT_PURPLE) return R.color.accent_purple;
        return R.color.accent_yellow;
    }
}
