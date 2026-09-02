package com.fongmi.android.tv.service;
import com.github.catvod.utils.Logger;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.text.TextUtils;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.DecoratedMediaCustomViewStyle;
import androidx.media.session.MediaButtonReceiver;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.receiver.ActionReceiver;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PlaybackService extends Service {

    private Map<String, Bitmap> cache;
    private Set<String> loading;
    private Handler handler;
    private static Players player;
    private static PlaybackService instance;
    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            if (!nonNull()) return;
            Notify.show(buildNotification());
            handler.postDelayed(this, 1000);
        }
    };

    public static void start(Players player) {
        PlaybackService.player = player;
        ContextCompat.startForegroundService(App.get(), new Intent(App.get(), PlaybackService.class));
    }

    public static void stop() {
        App.get().stopService(new Intent(App.get(), PlaybackService.class));
    }

    public static boolean isRunning() {
        return instance != null;
    }

    private boolean isNull() {
        return Objects.isNull(player) || Objects.isNull(player.getSession());
    }

    private boolean nonNull() {
        return Objects.nonNull(player) && Objects.nonNull(player.getSession());
    }

    private NotificationManagerCompat getManager() {
        return NotificationManagerCompat.from(this);
    }

    private MediaMetadataCompat getMetadata() {
        return isNull() ? null : player.getSession().getController().getMetadata();
    }

    private String getTitle() {
        String title = getMetadata() == null ? null : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        return TextUtils.isEmpty(title) ? null : title;
    }

    private String getArtist() {
        String artist = getMetadata() == null ? null : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        return TextUtils.isEmpty(artist) ? null : artist;
    }

    private String getArtUri() {
        String artUri = getMetadata() == null ? null : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
        return TextUtils.isEmpty(artUri) ? "" : artUri;
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Notify.DEFAULT);
        // 划掉播放通知不等于明确停止播放，避免误发 STOP 导致视频页退出。
        builder.setOngoing(true);
        // 背景与按钮颜色交给 SystemUI 按深浅主题处理，避免浅色通知上出现白色低对比按钮。
        builder.setColorized(false);
        builder.setOnlyAlertOnce(true);
        builder.setShowWhen(false);
        // 系统媒体标题保留一份即可；副标题、进度和按钮全部由紧凑布局负责。
        builder.setContentTitle(getTitle());
        builder.setSmallIcon(R.drawable.ic_logo);
        builder.setCategory(NotificationCompat.CATEGORY_TRANSPORT);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if (nonNull()) builder.setContentIntent(player.getSession().getController().getSessionActivity());
        // 不设置 LargeIcon：部分厂商会在 LargeIcon 右上角强制叠加应用圆形角标。
        // 海报改为 RemoteViews 内的普通图片，就不会出现那个小圆图标。
        if (nonNull()) builder.setStyle(new DecoratedMediaCustomViewStyle().setMediaSession(player.getSession().getSessionToken()));
        RemoteViews views = createRemoteViews();
        builder.setCustomContentView(views);
        // 展开态也复用同一份紧凑布局，避免系统记住“展开”状态后留下大片空白。
        builder.setCustomBigContentView(views);
        loadArtwork();
        return builder.build();
    }

    private RemoteViews createRemoteViews() {
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_playback_compact);
        views.setTextViewText(R.id.artist, getArtist());
        Bitmap art = getArtwork();
        if (art == null || art.isRecycled()) views.setImageViewResource(R.id.art, R.drawable.ic_notify_art);
        else views.setImageViewBitmap(R.id.art, art);
        views.setImageViewResource(R.id.play, nonNull() && player.isPlaying() ? R.drawable.ic_notify_pause : R.drawable.ic_notify_play);
        views.setOnClickPendingIntent(R.id.play, ActionReceiver.getPendingIntent(this, nonNull() && player.isPlaying() ? ActionEvent.PAUSE : ActionEvent.PLAY));
        views.setOnClickPendingIntent(R.id.prev, ActionReceiver.getPendingIntent(this, ActionEvent.PREV));
        views.setOnClickPendingIntent(R.id.next, ActionReceiver.getPendingIntent(this, ActionEvent.NEXT));
        long duration = Math.max(player.getDuration(), 0);
        long position = Math.max(player.getPosition(), 0);
        int max = (int) Math.min(duration, Integer.MAX_VALUE);
        int progress = (int) Math.min(position, max);
        views.setProgressBar(R.id.progress, Math.max(max, 1), progress, duration <= 0);
        return views;
    }

    private Bitmap getArtwork() {
        String artUri = getArtUri();
        Bitmap cached = cache.get(artUri);
        if (cached != null && !cached.isRecycled()) return cached;
        Bitmap metadataArt = getMetadata() == null ? null : getMetadata().getBitmap(MediaMetadataCompat.METADATA_KEY_ART);
        return metadataArt != null && !metadataArt.isRecycled() ? metadataArt : null;
    }

    private void loadArtwork() {
        String artUri = getArtUri();
        if (!artUri.isEmpty() && !cache.containsKey(artUri) && loading.add(artUri)) App.execute(() -> glide(artUri));
    }

    private void glide(String artUri) {
        try {
            int size = Math.round(96 * getResources().getDisplayMetrics().density);
            cache.put(artUri, Glide.with(this).asBitmap().load(ImgUtil.getUrl(artUri)).override(size, size).skipMemoryCache(false).dontAnimate().signature(ImgUtil.getSignature(artUri)).submit().get());
            Notify.show(buildNotification());
        } catch (Exception e) {
            Logger.e("Error", e);
        } finally {
            loading.remove(artUri);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (event.isUpdate()) Notify.show(buildNotification());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        cache = new HashMap<>();
        loading = java.util.Collections.synchronizedSet(new HashSet<>());
        handler = new Handler(Looper.getMainLooper());
        EventBus.getDefault().register(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (nonNull()) MediaButtonReceiver.handleIntent(player.getSession(), intent);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, Notify.ID, buildNotification(), type);
        handler.removeCallbacks(progressTask);
        handler.postDelayed(progressTask, 1000);
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        handler.removeCallbacks(progressTask);
        getManager().cancel(Notify.ID);
        stopForeground(true);
        if (instance == this) instance = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
