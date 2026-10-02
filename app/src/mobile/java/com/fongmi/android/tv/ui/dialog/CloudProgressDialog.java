package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

/** A local/cloud comparison with explicit, theme-accent-independent actions. */
public final class CloudProgressDialog {
    private CloudProgressDialog() {}

    public static AlertDialog show(Activity activity, String title, String local, String cloud,
                                   Runnable keepLocal, Runnable useCloud) {
        boolean dark = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int foreground = dark ? 0xFFF4F4F6 : 0xFF202124;
        int secondary = dark ? 0xFFB1B3BC : 0xFF717580;
        int surface = dark ? 0xFF292B31 : 0xFFF3F4F7;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 22), dp(activity, 24), dp(activity, 22), dp(activity, 20));
        content.setBackground(shape(activity, dark ? 0xFF1C1D22 : Color.WHITE, 24));
        content.addView(text(activity, "发现新的观看进度", 19, foreground, true));
        TextView name = text(activity, title, 14, secondary, false);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = dp(activity, 8);
        nameParams.bottomMargin = dp(activity, 20);
        content.addView(name, nameParams);
        content.addView(progress(activity, "本机进度", local, surface, foreground, secondary));
        LinearLayout.LayoutParams remoteParams = new LinearLayout.LayoutParams(-1, -2);
        remoteParams.topMargin = dp(activity, 8);
        content.addView(progress(activity, "云端最新进度", cloud, surface, foreground, secondary), remoteParams);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = dp(activity, 22);
        content.addView(actions, actionsParams);
        Button left = button(activity, "继续本机", surface, foreground);
        Button right = button(activity, "跳到云端", 0xFF1677FF, Color.WHITE);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, -2, 1);
        leftParams.setMarginEnd(dp(activity, 10));
        actions.addView(left, leftParams);
        actions.addView(right, new LinearLayout.LayoutParams(0, -2, 1));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll).setCancelable(false).create();
        left.setOnClickListener(v -> { dialog.dismiss(); keepLocal.run(); });
        right.setOnClickListener(v -> { dialog.dismiss(); useCloud.run(); });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int available = activity.getWindow().getDecorView().getWidth();
            if (available <= 0) available = activity.getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.min(dp(activity, 360), Math.max(dp(activity, 240), available - dp(activity, 40))),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private static LinearLayout progress(Activity activity, String label, String value, int surface, int foreground, int secondary) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        row.setBackground(shape(activity, surface, 14));
        row.addView(text(activity, label, 12, secondary, false));
        TextView position = text(activity, value, 16, foreground, true);
        position.setPadding(0, dp(activity, 5), 0, 0);
        row.addView(position);
        return row;
    }

    private static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static Button button(Activity activity, String label, int fill, int color) {
        Button view = new Button(activity);
        view.setAllCaps(false);
        view.setText(label);
        view.setTextSize(14);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(activity, 48));
        view.setMinimumHeight(dp(activity, 48));
        view.setPadding(dp(activity, 8), dp(activity, 12), dp(activity, 8), dp(activity, 12));
        view.setBackgroundTintList(null);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),
                shape(activity, fill, 14), shape(activity, Color.WHITE, 14)));
        return view;
    }

    private static GradientDrawable shape(Activity activity, int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(activity, radius));
        return background;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
