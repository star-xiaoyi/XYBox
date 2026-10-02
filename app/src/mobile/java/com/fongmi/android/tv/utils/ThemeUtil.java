package com.fongmi.android.tv.utils;

import androidx.annotation.ColorRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;

/** Fixed functional colors with independently chosen light/dark surfaces. */
public final class ThemeUtil {
    private ThemeUtil() { }

    public static void applyNightMode() {
        int mode;
        if (Setting.getThemeMode() == Setting.THEME_LIGHT) mode = AppCompatDelegate.MODE_NIGHT_NO;
        else if (Setting.getThemeMode() == Setting.THEME_DARK) mode = AppCompatDelegate.MODE_NIGHT_YES;
        else mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode);
    }

    @StyleRes
    public static int getBottomSheetTheme() { return R.style.BottomSheetDialog; }

    /** Common actions use blue; downloads, toggles and destructive actions override by purpose. */
    @ColorRes
    public static int getAccentColorResource() { return R.color.action_primary; }

}
