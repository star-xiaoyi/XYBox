package com.fongmi.android.tv.service;
import com.github.catvod.utils.Logger;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
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
import java.util.Map;
import java.util.Objects;

public class PlaybackService extends Service {

    private Map<String, Bitmap> cache;
    private Bitmap defaultArt;
    private static Players player;

    public static void start(Players player) {
        ContextCompat.startForegroundService(App.get(), new Intent(App.get(), PlaybackService.class));
        PlaybackService.player = player;
    }

    public static void stop() {
        App.get().stopService(new Intent(App.get(), PlaybackService.class));
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
        if (nonNull() && player.isPlaying()) return buildNotificationAction(R.drawable.ic_notify_pause, androidx.media3.ui.R.string.exo_controls_pause_description, ActionEvent.PAUSE);
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

    private void setLargeIcon(NotificationCompat.Builder builder, Bitmap art) {
        if (art == null || art.isRecycled()) return;
        Bitmap swatch = Bitmap.createScaledBitmap(art, 1, 1, true);
        builder.setColor(swatch.getPixel(0, 0));
        builder.setLargeIcon(art);
        if (swatch != art) swatch.recycle();
    }

    private void addAction(NotificationCompat.Builder builder) {
        builder.addAction(buildNotificationAction(R.drawable.ic_notify_prev, androidx.media3.ui.R.string.exo_controls_previous_description, ActionEvent.PREV));
        builder.addAction(getPlayPauseAction());
        builder.addAction(buildNotificationAction(R.drawable.ic_notify_next, androidx.media3.ui.R.string.exo_controls_next_description, ActionEvent.NEXT));
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Notify.DEFAULT);
        builder.setOngoing(false);
        builder.setColorized(true);
        builder.setOnlyAlertOnce(true);
        builder.setContentText(getArtist());
        builder.setContentTitle(getTitle());
        builder.setSmallIcon(R.drawable.ic_logo);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setDeleteIntent(ActionReceiver.getPendingIntent(this, ActionEvent.STOP));
        if (nonNull()) builder.setContentIntent(player.getSession().getController().getSessionActivity());
        if (nonNull()) builder.setStyle(new MediaStyle().setMediaSession(player.getSession().getSessionToken()));
        addAction(builder);
        setArtwork(builder);
        return builder.build();
    }

    private void setArtwork(NotificationCompat.Builder builder) {
        setLargeIcon(builder, getDefaultArt());
        String artUri = getArtUri();
        if (cache.containsKey(artUri)) {
            setLargeIcon(builder, cache.get(artUri));
        } else if (!artUri.isEmpty()) {
            App.execute(() -> glide(builder, artUri));
        }
    }

    private Bitmap getDefaultArt() {
        if (defaultArt != null && !defaultArt.isRecycled()) return defaultArt;
        Drawable drawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher);
        if (drawable == null) return null;
        int size = Math.round(64 * getResources().getDisplayMetrics().density);
        defaultArt = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(new Canvas(defaultArt));
        return defaultArt;
    }

    private void glide(NotificationCompat.Builder builder, String artUri) {
        try {
            cache.put(artUri, Glide.with(this).asBitmap().load(ImgUtil.getUrl(artUri)).skipMemoryCache(false).dontAnimate().signature(ImgUtil.getSignature(artUri)).submit().get());
            setLargeIcon(builder, cache.get(artUri));
            Notify.show(builder.build());
        } catch (Exception e) {
            Logger.e("Error", e);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (event.isUpdate()) Notify.show(buildNotification());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        cache = new HashMap<>();
        EventBus.getDefault().register(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (nonNull()) MediaButtonReceiver.handleIntent(player.getSession(), intent);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, Notify.ID, buildNotification(), type);
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        getManager().cancel(Notify.ID);
        stopForeground(true);
        if (defaultArt != null && !defaultArt.isRecycled()) defaultArt.recycle();
        defaultArt = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
