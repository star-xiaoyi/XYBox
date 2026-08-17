package com.fongmi.android.tv.utils;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ViewProgressBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class Notify {

    public static final String DEFAULT = "default";
    public static final int ID = 9527;
    private AlertDialog mDialog;
    private Toast mToast;
    private Handler mHandler;

    private static class Loader {
        static volatile Notify INSTANCE = new Notify();
    }

    private static Notify get() {
        return Loader.INSTANCE;
    }

    public static void createChannel() {
        NotificationManagerCompat notifyMgr = NotificationManagerCompat.from(App.get());
        notifyMgr.createNotificationChannel(new NotificationChannelCompat.Builder(DEFAULT, NotificationManagerCompat.IMPORTANCE_LOW).setName("XMBOX").build());
    }

    public static String getError(int resId, Throwable e) {
        if (TextUtils.isEmpty(e.getMessage())) return ResUtil.getString(resId);
        return ResUtil.getString(resId) + "\n" + e.getMessage();
    }

    public static void show(Notification notification) {
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(App.get(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat.from(App.get()).notify(ID, notification);
    }

    public static void show(int resId) {
        if (resId != 0) show(ResUtil.getString(resId));
    }

    public static void show(String text) {
        get().makeText(text);
    }

    public static void showCenter(int resId) {
        if (resId != 0) showCenter(ResUtil.getString(resId));
    }

    public static void showCenter(String text) {
        get().makeTextCenter(text);
    }

    public static void progress(Context context) {
        dismiss();
        get().create(context);
    }

    public static void dismiss() {
        try {
            if (get().mDialog != null) get().mDialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    private void create(Context context) {
        ViewProgressBinding binding = ViewProgressBinding.inflate(LayoutInflater.from(context));
        mDialog = new MaterialAlertDialogBuilder(context).setView(binding.getRoot()).create();
        mDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        mDialog.show();
    }

    private void makeText(String message) {
        if (mToast != null) mToast.cancel();
        if (mHandler == null) mHandler = new Handler(Looper.getMainLooper());
        if (TextUtils.isEmpty(message)) return;
        mToast = new Toast(App.get());
        TextView view = (TextView) LayoutInflater.from(App.get()).inflate(R.layout.view_toast, null);
        view.setText(message);
        mToast.setView(view);
        mToast.setDuration(Toast.LENGTH_SHORT);
        mToast.show();
        
        // 1秒后取消Toast
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(() -> {
            if (mToast != null) mToast.cancel();
        }, 1000); // 1000毫秒 = 1秒
    }

    private void makeTextCenter(String message) {
        if (mToast != null) mToast.cancel();
        if (mHandler == null) mHandler = new Handler(Looper.getMainLooper());
        if (TextUtils.isEmpty(message)) return;
        mToast = new Toast(App.get());
        TextView view = (TextView) LayoutInflater.from(App.get()).inflate(R.layout.view_toast, null);
        view.setText(message);
        mToast.setView(view);
        mToast.setDuration(Toast.LENGTH_SHORT);
        mToast.setGravity(Gravity.CENTER, 0, 0);
        mToast.show();
        
        // 1秒后取消Toast
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(() -> {
            if (mToast != null) mToast.cancel();
        }, 1000); // 1000毫秒 = 1秒
    }
}
