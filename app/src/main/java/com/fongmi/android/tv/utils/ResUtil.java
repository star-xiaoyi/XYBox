package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.AnimRes;
import androidx.annotation.AttrRes;
import androidx.annotation.ArrayRes;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fongmi.android.tv.App;

public class ResUtil {

    public static DisplayMetrics getDisplayMetrics() {
        return getDisplayMetrics(App.get());
    }

    public static DisplayMetrics getDisplayMetrics(Context context) {
        return context.getResources().getDisplayMetrics();
    }

    public static WindowManager getWindowManager(Context context) {
        return (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static int getScreenWidth() {
        return getScreenWidth(App.get());
    }

    public static int getScreenWidth(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect rect = getWindowManager(context).getCurrentWindowMetrics().getBounds();
            return rect.width();
        } else {
            return getDisplayMetrics(context).widthPixels;
        }
    }

    public static int getScreenHeight() {
        return getScreenHeight(App.get());
    }

    public static int getScreenHeight(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect rect = getWindowManager(context).getCurrentWindowMetrics().getBounds();
            return rect.height();
        } else {
            return getDisplayMetrics(context).heightPixels;
        }
    }

    /**
     * 触摸点是否落在系统手势区（返回手势、底部小白条那一圈），落在里面就不抢这次触摸。
     *
     * 两个量必须在同一个坐标系里比。原来是 e.getRawX()/getRawY() 比 getScreenWidth()/
     * getScreenHeight()：前者带着窗口在屏幕上的偏移，后者只是窗口自身尺寸（不含偏移）。
     * 全屏时偏移为 0，两者恰好重合，所以一直没暴露；分屏/小窗时窗口被挪到屏幕中下部，
     * 窗口里任意一点的 raw 坐标都大于窗口尺寸，整个播放区都被判成"边缘"，
     * 于是每个手势回调第一行就 return，视频区触摸全灭（详情区是普通可点 View，不走这条路，所以还能点）。
     *
     * 现在把事件坐标换算成窗口内坐标，跟窗口自身尺寸比，全程不碰 raw 坐标。
     */
    public static boolean isEdge(View view, MotionEvent e, int edge) {
        View root = view.getRootView();
        int width = root.getWidth();
        int height = root.getHeight();
        if (width <= 0 || height <= 0) return false;
        int[] location = new int[2];
        view.getLocationInWindow(location);
        float x = e.getX() + location[0];
        float y = e.getY() + location[1];
        int left = edge, top = edge, right = edge, bottom = edge;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
        if (insets != null) {
            // 分屏/小窗时窗口的内侧边不归系统手势管，insets 报 0，这几条边整条让给播放器。
            // 上限仍取 edge，保证任何情况下都不会比全屏时让得更多。
            Insets gesture = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
            left = Math.min(edge, gesture.left);
            top = Math.min(edge, gesture.top);
            right = Math.min(edge, gesture.right);
            bottom = Math.min(edge, gesture.bottom);
        }
        return x < left || x > width - right || y < top || y > height - bottom;
    }

    public static boolean isLand(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isPad() {
        return App.get().getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    public static int getWindowWidthDp(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        if (configuration.screenWidthDp > 0) return configuration.screenWidthDp;
        return (int) (getScreenWidth(context) / getDisplayMetrics(context).density);
    }

    public static boolean isExpanded(Context context) {
        return getWindowWidthDp(context) >= 600;
    }

    public static int sp2px(int sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getDisplayMetrics());
    }

    public static int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getDisplayMetrics());
    }

    public static int getDrawable(String resId) {
        return App.get().getResources().getIdentifier(resId, "drawable", App.get().getPackageName());
    }

    public static String getString(@StringRes int resId) {
        return App.get().getResources().getString(resId);
    }

    public static String getString(@StringRes int resId, Object... formatArgs) {
        return App.get().getResources().getString(resId, formatArgs);
    }

    public static String[] getStringArray(@ArrayRes int resId) {
        return App.get().getResources().getStringArray(resId);
    }

    public static TypedArray getTypedArray(@ArrayRes int resId) {
        return App.get().getResources().obtainTypedArray(resId);
    }

    public static Drawable getDrawable(@DrawableRes int resId) {
        return ContextCompat.getDrawable(App.get(), resId);
    }

    public static int getColor(@ColorRes int resId) {
        return ContextCompat.getColor(App.get(), resId);
    }

    /** 解析当前 Activity 主题里的颜色属性，注意不能用 App.get()，那上面没挂主题覆盖层。 */
    public static int getThemeColor(Context context, @AttrRes int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.resourceId != 0 ? ContextCompat.getColor(context, value.resourceId) : value.data;
    }

    public static Animation getAnim(@AnimRes int resId) {
        return AnimationUtils.loadAnimation(App.get(), resId);
    }

    public static Display getDisplay(Context context) {
        return ContextCompat.getDisplayOrDefault(context);
    }

    public static int getTextWidth(String content, int size) {
        Paint paint = new Paint();
        paint.setTextSize(sp2px(size));
        return (int) paint.measureText(content);
    }
}
