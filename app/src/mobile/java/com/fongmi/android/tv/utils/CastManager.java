package com.fongmi.android.tv.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.cast.dlna.dmc.DLNACastManager;
import com.android.cast.dlna.dmc.control.DeviceControl;
import com.android.cast.dlna.dmc.control.OnDeviceControlListener;
import com.android.cast.dlna.dmc.control.ServiceActionCallback;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.CastService;

import org.fourthline.cling.support.lastchange.EventedValue;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportState;

import kotlin.Unit;

/**
 * 投屏会话。
 * <p>
 * 以前 {@link com.fongmi.android.tv.ui.dialog.CastDialog} 自己持有 DeviceControl，弹窗一关就
 * disconnect + unbindCastService，等于"发射后不管"：电视收不到任何后续指令，手机也拿不到电视的
 * 播放状态，换集只能重新走一遍设备发现。这里把连接从弹窗里抽出来变成全局单例，投屏期间一直活着，
 * 手机端才有可能当遥控器用。
 * <p>
 * bindCastService 的绑定计数放在这里统一管：设备发现（弹窗）和投屏会话都要用同一个 UPnP 服务，
 * 谁都不能擅自 unbind——弹窗关闭时若正在投屏，服务必须留着。
 */
public class CastManager {

    /** 电视端进度轮询间隔。DLNA 每次 GetPositionInfo 都是一次网络往返，1s 已经够跟手了。 */
    private static final long POLL_INTERVAL = 1000;

    private DeviceControl control;
    private org.fourthline.cling.model.meta.Device<?, ?, ?> device;
    private CastVideo video;
    private String deviceName;
    private Listener listener;

    private boolean casting;
    private boolean playing;
    /** 通知栏上一次渲染时的播放态，用来避免每秒进度轮询都去重建通知。 */
    private boolean notified;
    private long position;
    private long duration;
    /** 本地刚下过指令、电视还没跟上的这段时间里，不让轮询结果把 UI 拽回旧值。 */
    private long ignorePollUntil;

    private int bindCount;

    private final Runnable mPoll = this::poll;

    private static class Loader {
        static volatile CastManager INSTANCE = new CastManager();
    }

    public static CastManager get() {
        return Loader.INSTANCE;
    }

    /** 需要 UPnP 服务的地方（设备发现、投屏会话）都走这对方法，别直接 bind/unbind。 */
    public void bind() {
        if (bindCount++ == 0) DLNACastManager.INSTANCE.bindCastService(App.get());
    }

    public void unbind() {
        if (bindCount > 0 && --bindCount == 0 && !casting) DLNACastManager.INSTANCE.unbindCastService(App.get());
    }

    public boolean isCasting() {
        return casting;
    }

    public boolean isPlaying() {
        return playing;
    }

    public long getPosition() {
        return position;
    }

    public long getDuration() {
        return duration;
    }

    public String getDeviceName() {
        return deviceName == null ? "" : deviceName;
    }

    public CastVideo getVideo() {
        return video;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void removeListener(Listener listener) {
        if (this.listener == listener) this.listener = null;
    }

    /**
     * 连上设备并投第一集。连接成功前不算投屏中，失败时把状态清干净，避免 UI 卡在假的投屏态。
     */
    public void connect(org.fourthline.cling.model.meta.Device<?, ?, ?> device, String name, CastVideo video, Callback callback) {
        this.device = device;
        this.video = video;
        this.deviceName = name;
        bind();
        control = DLNACastManager.INSTANCE.connectDevice(device, new OnDeviceControlListener() {
            @Override
            public void onConnected(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> d) {
                push(video, callback);
            }

            @Override
            public void onDisconnected(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> d) {
                App.post(() -> {
                    if (casting) stop();
                });
            }

            @Override
            public void onAvTransportStateChanged(@NonNull TransportState state) {
                applyState(state);
            }

            @Override
            public void onEventChanged(@NonNull EventedValue<?> event) {
            }

            @Override
            public void onRendererVolumeChanged(int volume) {
            }

            @Override
            public void onRendererVolumeMuteChanged(boolean mute) {
            }
        });
    }

    /** 换集：已经连着的设备直接换 URI，不用再走发现流程。 */
    public void cast(CastVideo video, Callback callback) {
        if (control == null) return;
        this.video = video;
        push(video, callback);
    }

    private void push(CastVideo video, Callback callback) {
        control.setAVTransportURI(video.getUrl(), video.getName(), new ServiceActionCallback<Unit>() {
            @Override
            public void onSuccess(Unit unit) {
                App.post(() -> {
                    if (video.getPosition() > 0) control.seek(video.getPosition(), null);
                    control.play("1", null);
                    position = Math.max(video.getPosition(), 0);
                    duration = 0;
                    playing = true;
                    onStarted();
                    if (callback != null) callback.onCastSuccess();
                });
            }

            @Override
            public void onFailure(@NonNull String msg) {
                App.post(() -> {
                    if (!casting) reset();
                    if (callback != null) callback.onCastFailure(msg);
                });
            }
        });
    }

    private void onStarted() {
        casting = true;
        // 电视多半是回头向手机的 NanoHTTPD 拉流，投屏期间这个服务必须活着
        Server.get().start();
        CastService.start();
        startPoll();
        notifyChanged();
    }

    public void play() {
        if (control == null) return;
        control.play("1", null);
        playing = true;
        hold();
        notifyChanged();
    }

    public void pause() {
        if (control == null) return;
        control.pause(null);
        playing = false;
        hold();
        notifyChanged();
    }

    public void toggle() {
        if (playing) pause();
        else play();
    }

    public void seekTo(long ms) {
        if (control == null) return;
        control.seek(Math.max(ms, 0), null);
        position = Math.max(ms, 0);
        hold();
        notifyChanged();
    }

    public void setVolume(int volume) {
        if (control != null) control.setVolume(volume, null);
    }

    /** 结束投屏：停掉电视播放、断连、收掉常驻通知。 */
    public void stop() {
        if (control != null) control.stop(null);
        disconnect();
    }

    /** 只断连接不发 stop，用于电视端已经自己断了的情况。 */
    public void disconnect() {
        stopPoll();
        boolean wasCasting = casting;
        if (device != null) DLNACastManager.INSTANCE.disconnectDevice(device);
        reset();
        CastService.stop();
        if (wasCasting) {
            // 投屏期间那次 bind 由这里释放：走 unbind() 让计数正常回落
            unbind();
            notifyChanged();
        }
    }

    private void reset() {
        casting = false;
        playing = false;
        position = 0;
        duration = 0;
        control = null;
        device = null;
        video = null;
    }

    /**
     * 电视的状态变化事件（订阅推送）。这类事件比轮询快，但不是所有设备都发，所以两条路都留着。
     */
    private void applyState(TransportState state) {
        App.post(() -> {
            if (!casting) return;
            if (state == TransportState.PLAYING) playing = true;
            else if (state == TransportState.PAUSED_PLAYBACK) playing = false;
            else if (state == TransportState.STOPPED) playing = false;
            notifyChanged();
        });
    }

    private void hold() {
        ignorePollUntil = System.currentTimeMillis() + 1500;
    }

    private void startPoll() {
        App.removeCallbacks(mPoll);
        App.post(mPoll, POLL_INTERVAL);
    }

    private void stopPoll() {
        App.removeCallbacks(mPoll);
    }

    private void poll() {
        if (!casting || control == null) return;
        control.getPositionInfo(new ServiceActionCallback<PositionInfo>() {
            @Override
            public void onSuccess(PositionInfo info) {
                App.post(() -> {
                    if (!casting) return;
                    long dur = info.getTrackDurationSeconds() * 1000;
                    long pos = info.getTrackElapsedSeconds() * 1000;
                    if (dur > 0) duration = dur;
                    if (System.currentTimeMillis() > ignorePollUntil) position = pos;
                    notifyChanged();
                });
            }

            @Override
            public void onFailure(@NonNull String msg) {
            }
        });
        control.getTransportInfo(new ServiceActionCallback<TransportInfo>() {
            @Override
            public void onSuccess(TransportInfo info) {
                App.post(() -> {
                    if (!casting) return;
                    TransportState state = info.getCurrentTransportState();
                    if (System.currentTimeMillis() > ignorePollUntil) playing = state == TransportState.PLAYING;
                    notifyChanged();
                });
            }

            @Override
            public void onFailure(@NonNull String msg) {
            }
        });
        App.removeCallbacks(mPoll);
        App.post(mPoll, POLL_INTERVAL);
    }

    private void notifyChanged() {
        // 通知栏只关心播放/暂停，进度每秒都在变，不能跟着刷否则通知一直重建
        if (casting && playing != notified) CastService.update();
        notified = playing;
        Listener l = listener;
        if (l != null) l.onCastChanged();
    }

    public interface Listener {

        /** 投屏状态、进度、播放/暂停有任何变化都会回调，在主线程。 */
        void onCastChanged();
    }

    public interface Callback {

        void onCastSuccess();

        void onCastFailure(String msg);
    }
}
