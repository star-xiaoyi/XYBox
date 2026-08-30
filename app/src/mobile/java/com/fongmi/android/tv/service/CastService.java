package com.fongmi.android.tv.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.CastManager;
import com.github.catvod.utils.Logger;

/**
 * 投屏期间的保活服务。
 * <p>
 * 投给电视的地址很多时候指向手机自己的 NanoHTTPD（本地文件走 /file、爬虫源走 /proxy），也就是说
 * 整场播放电视都在向手机拉流。之前手机侧既没有前台服务也没有 WifiLock，App 一进后台被冻结、
 * 或 Wi-Fi 进省电休眠，供流立刻断，表现就是"手机放一边或划掉后台，电视就卡住"。
 * <p>
 * 这里在投屏期间起一个前台服务 + WifiLock + WakeLock 把这条链路按住，顺便给用户一个能直接
 * 暂停/退出投屏的常驻通知。
 */
public class CastService extends Service {

    private static final String CHANNEL_ID = "cast_channel";
    private static final int NOTIFICATION_ID = 1003;

    private static final String ACTION_TOGGLE = "com.xybox.app.cast.TOGGLE";
    private static final String ACTION_STOP = "com.xybox.app.cast.STOP";

    private static CastService instance;

    private NotificationManager manager;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;

    public static void start() {
        if (instance != null) {
            instance.refresh();
            return;
        }
        try {
            Intent intent = new Intent(App.get(), CastService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) App.get().startForegroundService(intent);
            else App.get().startService(intent);
        } catch (Exception e) {
            // Android 12+ 后台不允许拉前台服务；此时投屏照样能用，只是少了保活和常驻通知
            Logger.e("CastService", e);
        }
    }

    public static void update() {
        if (instance != null) instance.refresh();
    }

    public static void stop() {
        if (instance != null) instance.quit();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        acquireLocks();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 通知栏按钮走的是 startForegroundService，系统要求这一轮必须先 startForeground，
        // 否则处理完动作直接停服务会被判成"启动了前台服务却没上前台"而崩溃
        startForeground(NOTIFICATION_ID, build());
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(action)) CastManager.get().toggle();
        else if (ACTION_STOP.equals(action)) CastManager.get().stop();
        if (!CastManager.get().isCasting()) quit();
        return START_STICKY;
    }

    /**
     * WifiLock 防 Wi-Fi 休眠（这是"走远/放一会儿就卡"最常见的原因），WakeLock 防 CPU 睡死导致
     * NanoHTTPD 的 socket 线程被挂起。两个锁都只在投屏期间持有。
     */
    private void acquireLocks() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
                wifiLock = wifi.createWifiLock(mode, "XYBox:cast");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (power != null) {
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XYBox:cast");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Exception e) {
            Logger.e("CastService", e);
        }
    }

    private void releaseLocks() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception e) {
            Logger.e("CastService", e);
        }
        wifiLock = null;
        wakeLock = null;
    }

    private void refresh() {
        if (manager != null) manager.notify(NOTIFICATION_ID, build());
    }

    private void quit() {
        instance = null;
        releaseLocks();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.cast_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification build() {
        CastManager cast = CastManager.get();
        String name = cast.getVideo() == null ? "" : cast.getVideo().getName();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_control_cast)
                .setContentTitle(getString(R.string.cast_notify_title, cast.getDeviceName()))
                .setContentIntent(content())
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (!TextUtils.isEmpty(name)) builder.setContentText(name);
        builder.addAction(0, getString(cast.isPlaying() ? R.string.cast_pause : R.string.cast_play), service(ACTION_TOGGLE));
        builder.addAction(0, getString(R.string.cast_exit), service(ACTION_STOP));
        return builder.build();
    }

    private PendingIntent content() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        return PendingIntent.getActivity(this, 0, intent == null ? new Intent() : intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent service(String action) {
        Intent intent = new Intent(this, CastService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return PendingIntent.getForegroundService(this, action.hashCode(), intent, flags);
        return PendingIntent.getService(this, action.hashCode(), intent, flags);
    }
}
