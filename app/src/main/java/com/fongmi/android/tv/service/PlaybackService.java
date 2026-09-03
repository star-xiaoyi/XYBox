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

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.DecoratedMediaCustomViewStyle;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
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
            updateNotification();
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

    private NotificationCompat.Action buildNotificationAction(@DrawableRes int icon, @StringRes int title, String action) {
        return new NotificationCompat.Action(icon, getString(title), ActionReceiver.getPendingIntent(this, action));
    }

    private NotificationCompat.Action getPlayPauseAction() {
        if (nonNull() && player.isPlayRequested()) return buildNotificationAction(R.drawable.ic_notify_pause, androidx.media3.ui.R.string.exo_controls_pause_description, ActionEvent.PAUSE);
        return buildNotificationAction(R.drawable.ic_notify_play, androidx.media3.ui.R.string.exo_controls_play_description, ActionEvent.PLAY);
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
        builder.setOngoing(true);
        builder.setColorized(false);
        builder.setOnlyAlertOnce(true);
        builder.setShowWhen(false);
        builder.setContentTitle(getTitle());
        builder.setContentText(getArtist());
        builder.setSmallIcon(R.drawable.ic_logo);
        builder.setCategory(NotificationCompat.CATEGORY_TRANSPORT);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if (nonNull()) builder.setContentIntent(player.getSession().getController().getSessionActivity());
        if (nonNull()) {
            builder.setStyle(new DecoratedMediaCustomViewStyle()
                    .setMediaSession(player.getSession().getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
            builder.addAction(buildNotificationAction(R.drawable.ic_notify_prev, androidx.media3.ui.R.string.exo_controls_previous_description, ActionEvent.PREV));
            builder.addAction(getPlayPauseAction());
            builder.addAction(buildNotificationAction(R.drawable.ic_notify_next, androidx.media3.ui.R.string.exo_controls_next_description, ActionEvent.NEXT));
            builder.setCustomContentView(createRemoteViews(R.layout.notification_playback_compact, false));
            builder.setCustomBigContentView(createRemoteViews(R.layout.notification_playback_expanded, true));
        }
        Bitmap artwork = getArtwork();
        if (artwork != null && !artwork.isRecycled()) builder.setLargeIcon(artwork);
        loadArtwork();
        return builder.build();
    }

    private RemoteViews createRemoteViews(int layout, boolean expanded) {
        RemoteViews views = new RemoteViews(getPackageName(), layout);
        long duration = Math.max(player.getDuration(), 0);
        long position = Math.max(player.getPosition(), 0);
        boolean hasProgress = duration > 0;
        views.setViewVisibility(R.id.progress_group, hasProgress ? android.view.View.VISIBLE : android.view.View.GONE);
        if (expanded) {
            String artist = getArtist();
            views.setViewVisibility(R.id.artist, TextUtils.isEmpty(artist) ? android.view.View.GONE : android.view.View.VISIBLE);
            views.setTextViewText(R.id.artist, artist);
        } else {
            boolean playRequested = player.isPlayRequested();
            views.setImageViewResource(R.id.play, playRequested ? R.drawable.ic_notify_pause : R.drawable.ic_notify_play);
            views.setOnClickPendingIntent(R.id.play, ActionReceiver.getPendingIntent(this, playRequested ? ActionEvent.PAUSE : ActionEvent.PLAY));
        }
        if (hasProgress) {
            int max = (int) Math.min(duration, Integer.MAX_VALUE);
            int progress = (int) Math.min(position, max);
            views.setProgressBar(R.id.progress, Math.max(max, 1), progress, false);
            views.setTextViewText(R.id.position, player.stringToTime(position));
            views.setTextViewText(R.id.duration, player.stringToTime(duration));
        }
        return views;
    }

    private void showNotification() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, Notify.ID, buildNotification(), type);
    }

    private void updateNotification() {
        getManager().notify(Notify.ID, buildNotification());
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
            // ExoPlayer 只能在创建它的主线程读取状态，通知也统一回主线程重建。
            App.post(this::updateNotification);
        } catch (Exception e) {
            Logger.e("Error", e);
        } finally {
            loading.remove(artUri);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (event.isUpdate()) updateNotification();
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
        showNotification();
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
