package com.fongmi.android.tv.player;
import com.github.catvod.utils.Logger;

import static androidx.media3.common.Player.COMMAND_SET_SPEED_AND_PITCH;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.impl.SessionCallback;
import com.fongmi.android.tv.player.danmaku.DanPlayer;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import master.flame.danmaku.ui.widget.DanmakuView;

public class Players implements Player.Listener, ParseCallback {

    private static final String TAG = Players.class.getSimpleName();

    public static final int SOFT = 0;
    public static final int HARD = 1;
    public static final int AUTO = 2;
    public static final int MPV = 3;

    private final StringBuilder builder;
    private final Formatter formatter;
    private final Runnable runnable;

    private Map<String, String> headers;
    private MediaSessionCompat session;
    private List<Danmaku> danmakus;
    private ExoPlayer exoPlayer;
    private DanPlayer danPlayer;
    private ParseJob parseJob;
    private PlayerView view;
    private VideoSize size;
    private List<Sub> subs;
    private String format;
    private String tag;
    private String key;
    private String url;
    private Drm drm;
    private Sub sub;

    private int decode;
    private int retry;
    private int networkRetry;

    public static Players create(Activity activity) {
        Players player = new Players(activity);
        Server.get().setPlayer(player);
        return player;
    }

    private Players(Activity activity) {
        decode = Setting.getDecode();
        builder = new StringBuilder();
        runnable = () -> ErrorEvent.timeout(tag);
        formatter = new Formatter(builder, Locale.getDefault());
        createSession(activity);
    }

    private void createSession(Activity activity) {
        session = new MediaSessionCompat(activity, "TV");
        session.setCallback(SessionCallback.create(this));
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(App.get(), 0, new Intent(App.get(), activity.getClass()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        MediaControllerCompat.setMediaController(activity, session.getController());
    }

    public void init(PlayerView view) {
        releasePlayer();
        setPlayer(view);
        setMediaItem();
    }

    private void setPlayer(PlayerView view) {
        int playerEngine = Setting.getPlayerEngine();

        // 根据播放器引擎选择不同的播放器
        if (playerEngine == Players.MPV) {
            // MPV播放器 - 暂时使用ExoPlayer作为后备，等集成MPV后替换
            initExoPlayer(view, decode);
        } else {
            // ExoPlayer播放器 (软解/硬解/自动)
            initExoPlayer(view, decode);
        }
    }

    private void initExoPlayer(PlayerView view, int decodeMode) {
        int renderMode;
        if (decodeMode == HARD) {
            // 系统解码器优先，并保留 FFmpeg 作为设备不支持该音视频格式时的兜底。
            renderMode = EXTENSION_RENDERER_MODE_ON;
        } else if (decodeMode == SOFT) {
            // FFmpeg 扩展排在系统解码器前面。
            renderMode = EXTENSION_RENDERER_MODE_PREFER;
        } else {
            // 系统解码器优先，系统不支持时允许 FFmpeg 扩展接手。
            renderMode = EXTENSION_RENDERER_MODE_ON;
        }

        exoPlayer = new ExoPlayer.Builder(App.get())
            .setLoadControl(ExoUtil.buildLoadControl())
            .setTrackSelector(ExoUtil.buildTrackSelector())
            .setRenderersFactory(ExoUtil.buildRenderersFactory(renderMode))
            .setMediaSourceFactory(ExoUtil.buildMediaSourceFactory())
            .build();
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true);
        exoPlayer.addAnalyticsListener(new EventLogger());
        exoPlayer.setHandleAudioBecomingNoisy(true);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(this);
        view.setPlayer(exoPlayer);
        this.view = view;
    }

    public void setDanmakuView(DanmakuView view) {
        danPlayer = new DanPlayer();
        danPlayer.setPlayer(this);
        danPlayer.setView(view);
    }

    public ExoPlayer get() {
        return exoPlayer;
    }

    public MediaSessionCompat getSession() {
        return session;
    }

    public List<Danmaku> getDanmakus() {
        return danmakus;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : headers;
    }

    public void setSub(Sub sub) {
        this.sub = sub;
        setMediaItem();
    }

    public void setFormat(String format) {
        this.format = format;
        setMediaItem();
    }

    public String getKey() {
        return key != null ? key : url;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public void reset() {
        removeTimeoutCheck();
        retry = 0;
        networkRetry = 0;
    }

    public void clearMediaItems() {
        if (exoPlayer != null) exoPlayer.clearMediaItems();
    }

    public void clear() {
        danmakus = null;
        headers = null;
        format = null;
        subs = null;
        drm = null;
        url = null;
    }

    public String stringToTime(long time) {
        return Util.format(builder, formatter, time);
    }

    public int getVideoWidth() {
        return size == null ? 0 : size.width;
    }

    public int getVideoHeight() {
        return size == null ? 0 : size.height;
    }

    public float getSpeed() {
        return exoPlayer == null ? 1.0f : exoPlayer.getPlaybackParameters().speed;
    }

    public long getPosition() {
        return exoPlayer == null ? C.TIME_UNSET : exoPlayer.getCurrentPosition();
    }

    public long getDuration() {
        return exoPlayer == null ? -1 : exoPlayer.getDuration();
    }

    public long getBuffered() {
        return exoPlayer == null ? 0 : exoPlayer.getBufferedPosition();
    }

    public boolean retried() {
        return ++retry > 2;
    }

    public boolean haveTrack(int type) {
        return exoPlayer != null && ExoUtil.haveTrack(exoPlayer.getCurrentTracks(), type);
    }

    public boolean haveDanmaku() {
        if (danmakus != null) for (Danmaku danmaku : danmakus) if (danmaku.isSelected()) return true;
        return false;
    }

    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    /**
     * 用户是否要求播放器继续播放。isPlaying() 在 BUFFERING 时必定为 false，
     * 不能用来判断播放/暂停按钮，否则网络卡顿会被界面误显示成用户已经暂停。
     */
    public boolean isPlayRequested() {
        return exoPlayer != null && exoPlayer.getPlayWhenReady();
    }

    public boolean isReady() {
        return exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_READY;
    }

    public boolean isEnded() {
        return exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_ENDED;
    }

    public boolean isIdle() {
        return exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_IDLE;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public boolean isLive() {
        return getDuration() < TimeUnit.MINUTES.toMillis(1) || exoPlayer.isCurrentMediaItemLive();
    }

    public boolean isVod() {
        return getDuration() > TimeUnit.MINUTES.toMillis(1) && !exoPlayer.isCurrentMediaItemLive();
    }

    public boolean isHard() {
        return decode == HARD;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public String getSizeText() {
        return getVideoWidth() == 0 && getVideoHeight() == 0 ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String setSpeed(float speed) {
        if (exoPlayer == null || !exoPlayer.isCommandAvailable(COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        exoPlayer.setPlaybackParameters(exoPlayer.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.25f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? Setting.getSpeed() : 1;
        return setSpeed(speed);
    }

    public void toggleDecode() {
        // 循环切换：软解 -> 硬解 -> 自动 -> 软解
        if (decode == SOFT) decode = HARD;
        else if (decode == HARD) decode = AUTO;
        else decode = SOFT;
        Setting.putDecode(decode);
        init(view);
    }

    public void toggleDecodeWithMpv() {
        // 循环切换：软解 -> 硬解 -> 自动 -> MPV -> 软解
        if (decode == SOFT) decode = HARD;
        else if (decode == HARD) decode = AUTO;
        else if (decode == AUTO) decode = MPV;
        else decode = SOFT;
        Setting.putDecode(decode);
        init(view);
    }

    public String getPositionTime(long time) {
        return stringToTime(getPositionValue(time));
    }

    /** 和 getPositionTime 同一套钳制逻辑，只是返回毫秒值，给缩略图预览定位用。 */
    public long getPositionValue(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return time;
    }

    /** 给预览播放器用：同一路地址、同一套 header 和格式，但不带字幕。 */
    public androidx.media3.common.MediaItem getPreviewItem() {
        if (TextUtils.isEmpty(url)) return null;
        return ExoUtil.getMediaItem(getHeaders(), UrlUtil.uri(url), format, drm, new ArrayList<>(), decode);
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seek(long time) {
        seekTo(getPosition() + time);
    }

    public void seekTo(long time) {
        if (exoPlayer != null) exoPlayer.seekTo(time);
        if (danPlayer != null) danPlayer.seekTo(time);
    }

    public void seekToDefaultPosition() {
        if (exoPlayer != null) exoPlayer.seekToDefaultPosition();
        prepare();
    }

    public void prepare() {
        if (exoPlayer != null) exoPlayer.prepare();
    }

    public void play() {
        if (exoPlayer != null) exoPlayer.play();
        if (danPlayer != null) danPlayer.play();
    }

    public void pause() {
        if (exoPlayer != null) exoPlayer.pause();
        if (danPlayer != null) danPlayer.pause();
    }

    public void stop() {
        if (exoPlayer != null) exoPlayer.stop();
        if (danPlayer != null) danPlayer.stop();
        stopParse();
    }

    public void release() {
        stopParse();
        releasePlayer();
        session.release();
        removeTimeoutCheck();
        Server.get().setPlayer(null);
        App.execute(() -> Source.get().stop());
    }

    private void releasePlayer() {
        if (exoPlayer != null) exoPlayer.release();
        if (danPlayer != null) danPlayer.release();
        if (view != null) view.setPlayer(null);
        exoPlayer = null;
    }

    private void removeTimeoutCheck() {
        App.removeCallbacks(runnable);
    }

    public void start(Channel channel, long timeout) {
        if (channel.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(channel.getDrm().getUUID())) {
            ErrorEvent.drm(tag);
        } else if (channel.hasMsg()) {
            ErrorEvent.extract(tag, channel.getMsg());
        } else if (channel.getParse() == 1) {
            startParse(channel.result(), false);
        } else if (isIllegal(channel.getUrl())) {
            ErrorEvent.url(tag);
        } else {
            setMediaItem(channel, timeout);
        }
    }

    public void start(Result result, boolean useParse, long timeout) {
        if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) {
            ErrorEvent.drm(tag);
        } else if (result.hasMsg()) {
            ErrorEvent.extract(tag, result.getMsg());
        } else if (result.getParse() == 1 || result.getJx() == 1) {
            startParse(result, useParse);
        } else if (isIllegal(result.getRealUrl())) {
            ErrorEvent.url(tag);
        } else {
            setMediaItem(result, timeout);
        }
    }

    private void startParse(Result result, boolean useParse) {
        stopParse();
        drm = result.getDrm();
        subs = result.getSubs();
        format = result.getFormat();
        danmakus = result.getDanmaku();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    private Map<String, String> checkUa(Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) if (HttpHeaders.USER_AGENT.equalsIgnoreCase(header.getKey())) return headers;
        headers.put(HttpHeaders.USER_AGENT, Setting.getUa().isEmpty() ? ExoUtil.getUa() : Setting.getUa());
        return headers;
    }

    private List<Sub> checkSub(List<Sub> subs) {
        if (subs == null) subs = this.subs = new ArrayList<>();
        if (sub == null || subs.contains(sub)) return subs;
        subs.add(0, sub);
        return subs;
    }

    private void setMediaItem() {
        if (url != null) setMediaItem(headers, url, format, drm, subs, danmakus, Constant.TIMEOUT_PLAY);
    }

    public void setMediaItem(String url) {
        setMediaItem(new HashMap<>(), url);
    }

    private void setMediaItem(Map<String, String> headers, String url) {
        setMediaItem(headers, url, format, drm, subs, danmakus, Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(Channel channel, long timeout) {
        setMediaItem(channel.getHeaders(), channel.getUrl(), channel.getFormat(), channel.getDrm(), new ArrayList<>(), new ArrayList<>(), timeout);
    }

    private void setMediaItem(Result result, long timeout) {
        setMediaItem(result.getHeaders(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), result.getDanmaku(), timeout);
    }

    private void setMediaItem(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, List<Danmaku> danmakus, long timeout) {
        if (exoPlayer != null) exoPlayer.setMediaItem(ExoUtil.getMediaItem(this.headers = checkUa(headers), UrlUtil.uri(this.url = url), this.format = format, this.drm = drm, checkSub(this.subs = subs), decode));
        if (danPlayer != null) setDanmaku(this.danmakus = danmakus);
        App.post(runnable, timeout);
        PlayerEvent.prepare(tag);
        session.setActive(true);
        Logger.d(url);
        prepare();
    }

    private void setDanmaku(List<Danmaku> items) {
        setDanmaku(items == null || items.isEmpty() ? Danmaku.empty() : items.get(0));
    }

    public void setDanmaku(Danmaku item) {
        danPlayer.setDanmaku(item);
        if (danmakus == null) danmakus = new ArrayList<>();
        if (!item.isEmpty() && !danmakus.contains(item)) danmakus.add(0, item);
        for (int i = 0; i < danmakus.size(); i++) danmakus.get(i).setSelected(danmakus.get(i).getUrl().equals(item.getUrl()));
        // 应用弹幕大小设置
        danPlayer.setTextSize(Setting.getDanmakuSize());
    }

    public void setDanmakuSize(float size) {
        if (danPlayer != null) danPlayer.setTextSize(size);
    }

    public void resetTrack() {
        if (exoPlayer != null) ExoUtil.resetTrack(exoPlayer);
    }

    public void resetTrack(int type) {
        if (exoPlayer != null) ExoUtil.resetTrack(exoPlayer, type);
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) setTrack(track);
    }

    private void setTrack(Track item) {
        if (item.isSelected()) {
            if (item.getType() == C.TRACK_TYPE_VIDEO) ExoUtil.selectVideoQuality(exoPlayer, item.getGroup(), item.getTrack());
            else ExoUtil.selectTrack(exoPlayer, item.getGroup(), item.getTrack());
        } else {
            ExoUtil.deselectTrack(exoPlayer, item.getGroup(), item.getTrack());
        }
    }

    private void setPlaybackState(int state) {
        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        long position = Math.max(getPosition(), 0);
        float speed = state == PlaybackStateCompat.STATE_PLAYING ? getSpeed() : 0f;
        session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state, position, speed).build());
    }

    /**
     * setMetadata 通常发生在媒体尚未 READY 时，当时 duration 是 TIME_UNSET。
     * 准备完成后补写真实时长，系统媒体通知才能展示并驱动可拖动进度条。
     */
    private void updateMetadataDuration() {
        if (session == null) return;
        long duration = getDuration();
        MediaMetadataCompat metadata = session.getController().getMetadata();
        if (duration <= 0 || metadata == null || metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) == duration) return;
        session.setMetadata(new MediaMetadataCompat.Builder(metadata).putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration).build());
        ActionEvent.update();
    }

    private boolean isIllegal(String url) {
        Uri uri = UrlUtil.uri(url);
        String host = UrlUtil.host(uri);
        String scheme = UrlUtil.scheme(uri);
        if ("data".equals(scheme)) return false;
        return scheme.isEmpty() || "file".equals(scheme) ? !Path.exists(url) : host.isEmpty();
    }

    private MediaMetadataCompat.Builder putBitmap(MediaMetadataCompat.Builder builder, Drawable drawable) {
        try {
            return builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, ((BitmapDrawable) drawable).getBitmap());
        } catch (Exception ignored) {
            return builder;
        }
    }

    public void setMetadata(String title, String artist, String artUri, Drawable drawable) {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        session.setMetadata(putBitmap(builder, drawable).build());
        ActionEvent.update();
    }

    public void share(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            Bundle bundle = new Bundle();
            for (Map.Entry<String, String> entry : getHeaders().entrySet()) bundle.putString(entry.getKey(), entry.getValue());
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, getUrl());
            intent.putExtra("extra_headers", bundle);
            intent.putExtra("title", title);
            intent.putExtra("name", title);
            intent.setType("text/plain");
            activity.startActivity(Util.getChooser(intent));
        } catch (Exception e) {
            Logger.e("Error", e);
        }
    }

    public void choose(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            List<String> list = new ArrayList<>();
            for (Map.Entry<String, String> entry : getHeaders().entrySet()) list.addAll(Arrays.asList(entry.getKey(), entry.getValue()));
            Uri data = getUrl().startsWith("file://") || getUrl().startsWith("/") ? FileUtil.getShareUri(getUrl()) : Uri.parse(getUrl());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(data, "video/*");
            intent.putExtra("title", title);
            intent.putExtra("return_result", isVod());
            intent.putExtra("headers", list.toArray(new String[0]));
            if (isVod()) intent.putExtra("position", (int) getPosition());
            activity.startActivityForResult(Util.getChooser(intent), 1001);
        } catch (Exception e) {
            Logger.e("Error", e);
        }
    }

    public void checkData(Intent data) {
        try {
            if (data == null || data.getExtras() == null) return;
            int position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            if ("playback_completion".equals(endBy)) ActionEvent.next();
            if ("user".equals(endBy)) seekTo(position);
        } catch (Exception e) {
            Logger.e("Error", e);
        }
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        // 解析线路、跳过广告等属于源内部状态。后台回来或重连时可能反复解析，
        // 这些成功信息不再用底部 Toast 打扰用户；真正的解析失败仍走 ErrorEvent。
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        setMediaItem(headers, url);
    }

    @Override
    public void onParseError() {
        ErrorEvent.parse(tag);
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (!events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_PLAYER_ERROR)) return;
        updateMetadataDuration();
        switch (player.getPlaybackState()) {
            case Player.STATE_IDLE:
                setPlaybackState(events.contains(Player.EVENT_PLAYER_ERROR) ? PlaybackStateCompat.STATE_ERROR : PlaybackStateCompat.STATE_NONE);
                break;
            case Player.STATE_READY:
                networkRetry = 0;
                setPlaybackState(player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                break;
            case Player.STATE_BUFFERING:
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                break;
            case Player.STATE_ENDED:
                setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                break;
        }
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (danPlayer != null) danPlayer.check(state);
        PlayerEvent.state(tag, state);
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        this.size = videoSize;
        PlayerEvent.size(tag);
    }

    @Override
    public void onTracksChanged(@NonNull Tracks tracks) {
        if (tracks.isEmpty()) return;
        setTrack(Track.find(getKey()));
        PlayerEvent.track(tag);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Logger.e(error.errorCode + "," + url);
        // 使用友好的错误提示
        String friendlyMsg = new com.fongmi.android.tv.player.exo.ErrorMsgProvider().get(error);
        Logger.e("Error: " + friendlyMsg);
        
        if (isNetworkError(error.errorCode)) {
            // VPN 路由切换时连接可能连续失败几次。先保留当前 MediaItem 和播放位置原地重连，
            // 不要一两次缓冲失败就重新解析、换源甚至重建详情页。
            if (++networkRetry <= 5) App.post(this::prepare, Math.min(networkRetry * 700L, 2800L));
            else {
                networkRetry = 0;
                ErrorEvent.extract(tag, friendlyMsg);
            }
            return;
        }

        if (retried()) ErrorEvent.extract(tag, friendlyMsg);
        else switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW:
                seekToDefaultPosition();
                break;
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FAILED:
                toggleDecode();
                break;
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                {
                    String fallbackMimeType = ExoUtil.getMimeType(error.errorCode);
                    if (fallbackMimeType == null || ExoUtil.isMimeType(format, fallbackMimeType)) ErrorEvent.extract(tag, friendlyMsg);
                    else setFormat(fallbackMimeType);
                }
                break;
            default:
                ErrorEvent.extract(tag, friendlyMsg);
                break;
        }
    }

    private boolean isNetworkError(int errorCode) {
        return errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                || errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                || errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS;
    }
}
