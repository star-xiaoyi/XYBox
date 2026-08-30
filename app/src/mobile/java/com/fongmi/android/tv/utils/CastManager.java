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
import com.fongmi.android.tv.server.process.Pc;
import com.fongmi.android.tv.server.process.Relay;
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
 * 会话状态（是否投屏中、进度、播放态）和常驻通知都留在这一层，具体怎么把指令送出去由
 * {@link Transport} 决定：电视走 DLNA，电脑走浏览器。上层的遥控界面只认这个类，换传输不用改。
 * <p>
 * bindCastService 的绑定计数放在这里统一管：设备发现（弹窗）和 DLNA 投屏会话都要用同一个 UPnP
 * 服务，谁都不能擅自 unbind——弹窗关闭时若正在投屏，服务必须留着。浏览器投屏不碰 UPnP。
 */
public class CastManager implements Pc.Listener {

    /** 电视端进度轮询间隔。DLNA 每次 GetPositionInfo 都是一次网络往返，1s 已经够跟手了。 */
    private static final long POLL_INTERVAL = 1000;

    private Transport transport;
    private CastVideo video;
    private String deviceName;
    private Listener listener;

    private boolean casting;
    private boolean playing;
    /** 通知栏上一次渲染时的播放态，用来避免每秒进度轮询都去重建通知。 */
    private boolean notified;
    private long position;
    private long duration;
    /** 本地刚下过指令、对端还没跟上的这段时间里，不让回报把 UI 拽回旧值。 */
    private long ignorePollUntil;

    private int bindCount;

    private static class Loader {
        static volatile CastManager INSTANCE = new CastManager();
    }

    public static CastManager get() {
        return Loader.INSTANCE;
    }

    private CastManager() {
        Pc.setListener(this);
    }

    /** 需要 UPnP 服务的地方（设备发现、DLNA 投屏会话）都走这对方法，别直接 bind/unbind。 */
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
     * 连上 DLNA 设备并投第一集。连接成功前不算投屏中，失败时把状态清干净，避免 UI 卡在假的投屏态。
     */
    public void connect(org.fourthline.cling.model.meta.Device<?, ?, ?> device, String name, CastVideo video, Callback callback) {
        this.deviceName = name;
        DlnaTransport t = new DlnaTransport(device);
        this.transport = t;
        t.connect(video, callback);
    }

    /** 投到 PC 浏览器：没有连接过程，写完状态等浏览器自己来取。 */
    public void connectBrowser(String name, CastVideo video, Callback callback) {
        this.deviceName = name;
        this.transport = new BrowserTransport();
        push(video, callback);
    }

    /** 换集：已经连着的设备直接换片，不用再走发现流程。 */
    public void cast(CastVideo video, Callback callback) {
        if (transport == null) return;
        push(video, callback);
    }

    private void push(CastVideo video, Callback callback) {
        this.video = video;
        transport.push(relay(video), video, callback);
    }

    /**
     * 决定这次要不要让流从手机转一道。
     * <p>
     * 浏览器必须转：拉外站的流会被跨域拦下。电视只在源带 Referer/UA 时才转——现在能正常投的
     * 片源保持直连，既不平白增加手机的流量和发热，也不会因为中转有 bug 把好功能改坏。
     * 地址本来就指向手机自己的服务（本地文件）时不用转。
     */
    private String relay(CastVideo video) {
        String url = video.getUrl();
        if (url.startsWith(Server.get().getAddress())) return url;
        if (!transport.needRelay() && !video.needRelay()) return url;
        return Relay.register(url, video.getHeaders());
    }

    private void onStarted() {
        casting = true;
        // 对端多半是回头向手机的 NanoHTTPD 拉流，投屏期间这个服务必须活着
        Server.get().start();
        CastService.start();
        transport.startPoll();
        notifyChanged();
    }

    public void play() {
        if (transport == null) return;
        transport.play();
        playing = true;
        hold();
        notifyChanged();
    }

    public void pause() {
        if (transport == null) return;
        transport.pause();
        playing = false;
        hold();
        notifyChanged();
    }

    public void toggle() {
        if (playing) pause();
        else play();
    }

    public void seekTo(long ms) {
        if (transport == null) return;
        transport.seek(Math.max(ms, 0));
        position = Math.max(ms, 0);
        hold();
        notifyChanged();
    }

    public void setVolume(int volume) {
        if (transport != null) transport.setVolume(volume);
    }

    /** DLNA 没有通用的倍速控制，只有浏览器这条路支持。 */
    public void setSpeed(float speed) {
        if (transport != null) transport.setSpeed(speed);
    }

    /** 结束投屏：停掉对端播放、断连、收掉常驻通知。 */
    public void stop() {
        if (transport != null) transport.stop();
        disconnect();
    }

    /** 只断连接不发 stop，用于对端已经自己断了的情况。 */
    public void disconnect() {
        boolean wasCasting = casting;
        Transport t = transport;
        if (t != null) {
            t.stopPoll();
            t.release();
        }
        reset();
        Relay.clear();
        CastService.stop();
        if (wasCasting) {
            if (t != null && t.needBind()) unbind();
            notifyChanged();
        }
    }

    private void reset() {
        casting = false;
        playing = false;
        position = 0;
        duration = 0;
        transport = null;
        video = null;
    }

    private void hold() {
        ignorePollUntil = System.currentTimeMillis() + 1500;
    }

    private boolean settled() {
        return System.currentTimeMillis() > ignorePollUntil;
    }

    /** 浏览器每秒上报一次它的实际播放情况。 */
    @Override
    public void onPcReport(long position, long duration, boolean playing, boolean ended) {
        App.post(() -> {
            if (!casting || !(transport instanceof BrowserTransport)) return;
            if (duration > 0) this.duration = duration;
            if (settled()) {
                this.position = position;
                this.playing = playing;
                // 只同步值，不回推指令：用户在电脑上自己点的播放/暂停，再命令它一次就打架了
                Pc.syncPlaying(playing);
            }
            if (ended && this.duration > 0) {
                // 浏览器有真的 ended 事件，不像 DLNA 要靠猜。把状态摆成"停在末尾"，
                // VideoActivity 那套自动切集的判定就能直接复用。
                this.position = this.duration;
                this.playing = false;
            }
            notifyChanged();
        });
    }

    /** 用户在电脑页面上点了某一集，转给持有剧集列表的界面去切。 */
    @Override
    public void onPcSelect(int index) {
        App.post(() -> {
            if (!casting) return;
            Listener l = listener;
            if (l != null) l.onCastSelect(index);
        });
    }

    private void notifyChanged() {
        // 通知栏只关心播放/暂停，进度每秒都在变，不能跟着刷否则通知一直重建
        if (casting && playing != notified) CastService.update();
        notified = playing;
        Listener l = listener;
        if (l != null) l.onCastChanged();
    }

    // ==================== 传输层 ====================

    private interface Transport {

        /** @param url 实际交给对端的地址（可能已经过中转），{@code video} 只用于取名字和起播位置 */
        void push(String url, CastVideo video, Callback callback);

        void play();

        void pause();

        void seek(long ms);

        void setVolume(int volume);

        void setSpeed(float speed);

        void stop();

        void release();

        /** 只有拉模式（DLNA）需要轮询，浏览器是自己报上来的。 */
        void startPoll();

        void stopPoll();

        /** 是否必须走中转（浏览器因跨域必须走）。 */
        boolean needRelay();

        /** 是否占用了 UPnP 服务的绑定计数。 */
        boolean needBind();
    }

    private class DlnaTransport implements Transport {

        private final org.fourthline.cling.model.meta.Device<?, ?, ?> device;
        private DeviceControl control;

        private final Runnable mPoll = this::poll;

        DlnaTransport(org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
            this.device = device;
        }

        void connect(CastVideo video, Callback callback) {
            bind();
            control = DLNACastManager.INSTANCE.connectDevice(device, new OnDeviceControlListener() {
                @Override
                public void onConnected(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> d) {
                    CastManager.this.push(video, callback);
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

        @Override
        public void push(String url, CastVideo video, Callback callback) {
            if (control == null) return;
            control.setAVTransportURI(url, video.getName(), new ServiceActionCallback<Unit>() {
                @Override
                public void onSuccess(Unit unit) {
                    App.post(() -> {
                        if (control == null) return;
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
                        if (!casting) {
                            release();
                            reset();
                        }
                        if (callback != null) callback.onCastFailure(msg);
                    });
                }
            });
        }

        @Override
        public void play() {
            if (control != null) control.play("1", null);
        }

        @Override
        public void pause() {
            if (control != null) control.pause(null);
        }

        @Override
        public void seek(long ms) {
            if (control != null) control.seek(ms, null);
        }

        @Override
        public void setVolume(int volume) {
            if (control != null) control.setVolume(volume, null);
        }

        @Override
        public void setSpeed(float speed) {
            // DLNA 的 TransportPlaySpeed 各家实现差异太大，多数电视直接忽略，不如不做
        }

        @Override
        public void stop() {
            if (control != null) control.stop(null);
        }

        @Override
        public void release() {
            control = null;
            DLNACastManager.INSTANCE.disconnectDevice(device);
        }

        @Override
        public boolean needRelay() {
            return false;
        }

        @Override
        public boolean needBind() {
            return true;
        }

        @Override
        public void startPoll() {
            App.removeCallbacks(mPoll);
            App.post(mPoll, POLL_INTERVAL);
        }

        @Override
        public void stopPoll() {
            App.removeCallbacks(mPoll);
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
                        if (settled()) position = pos;
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
                        if (settled()) playing = state == TransportState.PLAYING;
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
    }

    /**
     * 浏览器传输：手机这边只是把"该放什么"写进 {@link Pc}，浏览器每秒来取。
     * 因此没有连接失败的概念——投屏一定"成功"，页面没打开就只是没人来取而已。
     */
    private class BrowserTransport implements Transport {

        @Override
        public void push(String url, CastVideo video, Callback callback) {
            long start = Math.max(video.getPosition(), 0);
            Pc.load(url, video.getName(), start);
            position = start;
            duration = 0;
            playing = true;
            onStarted();
            if (callback != null) callback.onCastSuccess();
        }

        @Override
        public void play() {
            Pc.setPlaying(true);
        }

        @Override
        public void pause() {
            Pc.setPlaying(false);
        }

        @Override
        public void seek(long ms) {
            Pc.seek(ms);
        }

        @Override
        public void setVolume(int volume) {
        }

        @Override
        public void setSpeed(float speed) {
            Pc.setSpeed(speed);
        }

        @Override
        public void stop() {
            Pc.stop();
        }

        @Override
        public void release() {
            Pc.stop();
        }

        @Override
        public boolean needRelay() {
            return true;
        }

        @Override
        public boolean needBind() {
            return false;
        }

        @Override
        public void startPoll() {
        }

        @Override
        public void stopPoll() {
        }
    }

    public interface Listener {

        /** 投屏状态、进度、播放/暂停有任何变化都会回调，在主线程。 */
        void onCastChanged();

        /** 接收端（目前只有浏览器）请求切到第 index 集，在主线程。 */
        void onCastSelect(int index);
    }

    public interface Callback {

        void onCastSuccess();

        void onCastFailure(String msg);
    }
}
