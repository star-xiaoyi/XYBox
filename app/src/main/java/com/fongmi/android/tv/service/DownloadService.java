package com.fongmi.android.tv.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.download.DownloadManager;
import com.github.catvod.utils.Logger;

import java.util.Locale;

/**
 * 只负责前台通知和进程保活，队列逻辑全在 {@link DownloadManager}。
 */
public class DownloadService extends Service {

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static DownloadService instance;
    private NotificationManager manager;

    /** 有任务要跑时把服务拉起来。 */
    public static void ensure() {
        if (instance != null) {
            instance.refresh();
            return;
        }
        try {
            Intent intent = new Intent(App.get(), DownloadService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) App.get().startForegroundService(intent);
            else App.get().startService(intent);
        } catch (Exception e) {
            // Android 12+ 在后台不允许拉前台服务，此时任务照样在线程池里跑，只是没有常驻通知
            Logger.e("DownloadService", e);
        }
    }

    /** 进度变化时刷新通知内容，不重复拉服务。 */
    public static void update() {
        if (instance != null) instance.refresh();
    }

    /** 队列空了就把前台通知收掉。 */
    public static void done() {
        if (instance != null) instance.stop();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, build());
        if (!DownloadManager.get().isBusy()) stop();
        return START_STICKY;
    }

    private void refresh() {
        if (manager != null) manager.notify(NOTIFICATION_ID, build());
    }

    private void stop() {
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    /**
     * 通知栏排版：标题是片名，副标题是集名，正文写状态和速率，右侧带百分比进度条。
     * 一眼要能分清"在缓存哪部剧的哪一集、进度多少"。
     */
    private Notification build() {
        DownloadManager download = DownloadManager.get();
        Download item = download.getCurrent();
        int pending = download.getPendingCount();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_download)
                .setContentIntent(intent())
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (item == null) {
            builder.setContentTitle(getString(R.string.download_title));
            builder.setContentText(getString(R.string.download_pending));
            builder.setProgress(0, 0, true);
            return builder.build();
        }
        builder.setContentTitle(item.getVodName());
        builder.setContentText(text(item));
        if (!TextUtils.isEmpty(item.getEpisodeName())) builder.setSubText(item.getEpisodeName());
        if (pending > 0) builder.setContentInfo(getString(R.string.download_queued, String.valueOf(pending)));
        builder.setProgress(100, item.getProgress(), item.getProgress() <= 0);
        return builder.build();
    }

    /** 正文：状态 · 速率，速率还没算出来时就只留状态。 */
    private String text(Download item) {
        String state = getString(item.isRunning() ? R.string.download_state_running : R.string.download_state_pending);
        String speed = speed(item.getSpeed());
        return TextUtils.isEmpty(speed) ? state : state + "  ·  " + speed;
    }

    private String speed(long speed) {
        if (speed <= 0) return "";
        if (speed < 1024) return speed + " B/s";
        if (speed < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB/s", speed / 1024d);
        return String.format(Locale.getDefault(), "%.1f MB/s", speed / (1024d * 1024d));
    }

    /** 点通知回到应用。 */
    private PendingIntent intent() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) intent = new Intent();
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }
}
