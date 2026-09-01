package com.fongmi.android.tv.player;

import android.text.TextUtils;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.exo.ExoUtil;

/**
 * 拖动进度条时的画面预览。
 * <p>
 * 用第二个 ExoPlayer 把帧直接渲染到预览窗的 TextureView 上，不做抽帧也不出 Bitmap。
 * 之前用 MediaMetadataRetriever 抽帧，在 m3u8/HLS 上基本抽不出任何东西——它走的是系统
 * MediaExtractor，只吃得下渐进式下载的 mp4 这类；而本项目的片源绝大多数是 m3u8，
 * 所以 0.3.4-beta1/beta2 的预览窗一直是空的。换成播放器之后，主播放器能放什么，
 * 预览就能出什么，天然覆盖 HLS、DRM、各种容器。
 * <p>
 * 代价是多一路解码，所以压到最小：不解音频、不要字幕、常驻暂停只 seek 不播，
 * 拖动时才创建，松手十秒没再拖就整个放掉。
 * <p>
 * seek 按 5 秒一格，同一格不重复 seek——手指划过整条进度条会经过成百上千个位置，
 * 每个都 seek 的话网络源根本跟不上。
 */
public class PreviewPlayer implements Player.Listener {

    private static final int STEP = 5000;
    private static final long IDLE_RELEASE = 10000;

    private final Runnable idleRelease;
    private ExoPlayer player;
    private TextureView view;
    private Callback callback;
    private MediaItem item;
    private String url;
    private int bucket;

    public PreviewPlayer() {
        this.idleRelease = this::release;
        this.bucket = -1;
    }

    public void attach(TextureView view, Callback callback) {
        this.callback = callback;
        this.view = view;
    }

    /** 换片源：地址没变就什么都不做，变了就把旧的放掉，下次拖动时按新地址重建。 */
    public void setSource(String url, MediaItem item) {
        if (TextUtils.equals(url, this.url)) return;
        this.url = url;
        this.item = item;
        release();
    }

    /**
     * 提前把预览播放器建起来。控制栏一露面就调，等用户真按到进度条时首帧已经解出来了。
     * <p>
     * 不这么做的话，第一次拖动要现场建连接、下 m3u8、下首片段再解码，实测要一两秒才出画面。
     */
    public void prepare(long position) {
        App.removeCallbacks(idleRelease);
        if (player != null || item == null || view == null) return;
        create();
        // 顺手把首帧落在当前播放位置：用户多半就从这儿开始拖
        seek(position);
    }

    /** 拖动中调用。首次会把预览播放器建起来，之后只在跨过 5 秒格子时才真的 seek。 */
    public void seek(long position) {
        App.removeCallbacks(idleRelease);
        if (item == null || view == null) return;
        if (player == null) create();
        int key = (int) (Math.max(0, position) / STEP);
        if (key == bucket) return;
        bucket = key;
        player.seekTo(key * (long) STEP);
    }

    /** 松手时调用。不立刻放掉——用户往往会连着拖好几次，留一会儿省得反复重建。 */
    public void idle() {
        App.post(idleRelease, IDLE_RELEASE);
    }

    public void release() {
        App.removeCallbacks(idleRelease);
        bucket = -1;
        if (player == null) return;
        player.removeListener(this);
        player.clearVideoTextureView(view);
        player.release();
        player = null;
    }

    private void create() {
        // 预览这一路不跟随解码设置：软解在 seek 密集时更慢，统一交给系统自动选
        player = new ExoPlayer.Builder(App.get())
                .setLoadControl(buildLoadControl())
                .setTrackSelector(buildTrackSelector())
                .setRenderersFactory(ExoUtil.buildRenderersFactory(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF))
                .setMediaSourceFactory(ExoUtil.buildMediaSourceFactory())
                .build();
        // 只要一帧画面，落到最近的关键帧就够；精确 seek 在网络源上慢到没法用
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        player.setPlayWhenReady(false);
        player.setVolume(0);
        player.addListener(this);
        player.setVideoTextureView(view);
        player.setMediaItem(item);
        player.prepare();
    }

    /**
     * 预览只要一张 160dp 宽的小图，所以反着来：多码率源强制挑最低的那一路，
     * 再把分辨率上限压到 640x360。主播放器那套 buildTrackSelector 是
     * setForceHighestSupportedBitrate，照抄过来等于用 1080P 的码率去解一张缩略图，
     * 网络源上慢得没法用。顺带关掉音频和字幕轨，一路都不解。
     */
    private DefaultTrackSelector buildTrackSelector() {
        DefaultTrackSelector selector = new DefaultTrackSelector(App.get());
        selector.setParameters(selector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setForceLowestBitrate(true)
                .setMaxVideoSize(640, 360));
        return selector;
    }

    /**
     * 缓冲区压到最小。预览是"seek 完出一帧就停"，攒缓冲毫无意义，
     * 而默认那套（还带用户的倍数设置）会先囤几十秒才肯出画面。
     */
    private DefaultLoadControl buildLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(1000, 5000, 500, 1000)
                .build();
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize size) {
        if (callback == null || size.width <= 0 || size.height <= 0) return;
        callback.onPreviewRatio(size.width / (float) size.height);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        // 预览出不来就收掉，绝不能影响正在放的那一路。
        // 不能在播放器自己的回调里直接 release，抛到下一个消息再做。
        if (callback != null) callback.onPreviewFail();
        App.post(idleRelease, 0);
    }

    public interface Callback {

        /** 视频宽高比，用来把预览窗调成片源的比例，否则 4:3 的片会被拉扁。 */
        void onPreviewRatio(float ratio);

        void onPreviewFail();
    }
}
