package com.fongmi.android.tv.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.WebDAVSyncJobService;
import com.fongmi.android.tv.utils.WebDAVSyncManager;
import com.github.catvod.utils.Logger;

/** Keeps the final WebDAV upload alive for the few seconds after the app leaves Recents. */
public class WebDAVSyncService extends Service {

    private static final String CHANNEL_ID = "webdav_sync";
    private static final int NOTIFICATION_ID = 9528;
    private final Object workerLock = new Object();
    private boolean workerRunning;
    private boolean runAgain;
    private int latestStartId;

    public static void start() {
        if (!WebDAVSyncManager.get().hasPendingSync()) return;
        try {
            ContextCompat.startForegroundService(App.get(), new Intent(App.get(), WebDAVSyncService.class));
        } catch (RuntimeException e) {
            Logger.w("WebDAV: 无法启动即时同步服务，将交给系统重试: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        synchronized (workerLock) {
            latestStartId = startId;
            runAgain = true;
            if (workerRunning) return START_REDELIVER_INTENT;
            workerRunning = true;
        }
        App.execute(this::drainPendingSync);
        return START_REDELIVER_INTENT;
    }

    private void drainPendingSync() {
        WebDAVSyncManager manager = WebDAVSyncManager.get();
        WebDAVSyncManager.SyncResult result = null;
        int attempts = 0;
        while (attempts++ < 3) {
            synchronized (workerLock) {
                runAgain = false;
            }
            if (!manager.hasPendingSync()) break;
            result = manager.syncNow();
            synchronized (workerLock) {
                if (!runAgain && (!result.success || !manager.hasPendingSync())) break;
            }
        }

        boolean needsRetry = manager.hasPendingSync();
        int completedStartId;
        synchronized (workerLock) {
            if (runAgain) {
                App.execute(this::drainPendingSync);
                return;
            }
            workerRunning = false;
            completedStartId = latestStartId;
        }
        if (needsRetry) WebDAVSyncJobService.scheduleImmediate();
        if (stopSelfResult(completedStartId)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        }
    }

    private void startAsForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent contentIntent = launch == null ? null : PendingIntent.getActivity(
                this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("正在同步观看记录")
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "云同步", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("在应用退出后完成观看记录同步");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
