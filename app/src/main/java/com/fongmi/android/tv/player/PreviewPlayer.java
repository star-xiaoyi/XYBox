package com.fongmi.android.tv.player;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.LruCache;
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
    private static final long SEEK_DEBOUNCE = 100;
    private static final long SEEK_FORCE_DELAY = 180;
    private static final long IDLE_RELEASE = 10000;
    private static final int FRAME_CACHE_BYTES = 8 * 1024 * 1024;
    private static final int FRAME_CACHE_WIDTH = 320;

    private final Runnable idleRelease;
    private final Runnable pendingSeek;
    private final Runnable forcedSeek;
    private final LruCache<Integer, Bitmap> frameCache;
    private ExoPlayer player;
    private TextureView view;
    private Callback callback;
    private MediaItem item;
    private String url;
    private int bucket;
    private int renderedBucket;
    private int pendingBucket;
    private int requestedBucket;
    private boolean forcedWhileBuffering;

    public PreviewPlayer() {
        this.idleRelease = this::releasePlayer;
        this.pendingSeek = () -> dispatchPendingSeek(false);
        this.forcedSeek = () -> dispatchPendingSeek(true);
        this.frameCache = new LruCache<Integer, Bitmap>(FRAME_CACHE_BYTES) {
            @Override
            protected int sizeOf(@NonNull Integer key, @NonNull Bitmap value) {
                return value.getByteCount();
            }
        };
        this.bucket = -1;
        this.renderedBucket = -1;
        this.pendingBucket = -1;
        this.requestedBucket = -1;
    }

    public void attach(TextureView view, Callback callback) {
        this.callback = callback;
        this.view = view;
    }

    /** 换片源：地址没变就什么都不做，变了就把旧的放掉，下次拖动时按新地址重建。 */
    public void setSource(String url, MediaItem item) {
        if (TextUtils.equals(url, this.url)) return;
        releasePlayer();
        frameCache.evictAll();
        this.url = url;
        this.item = item;
        this.requestedBucket = -1;
        if (callback != null) callback.onPreviewReset();
    }

    /**
     * 提前把预览播放器建起来。控制栏一露面就调，等用户真按到进度条时首帧已经解出来了。
     * <p>
     * 不这么做的话，第一次拖动要现场建连接、下 m3u8、下首片段再解码，实测要一两秒才出画面。
     */
    public void prepare(long position) {
        App.removeCallbacks(idleRelease);
        if (player != null || item == null || view == null) return;
        // 直接从当前播放位置 prepare，避免先从 0 开始加载、随后又取消并 seek。
        create(toBucket(position));
    }

    /**
     * 拖动中调用。目标位置先按 5 秒取整，再用 100ms 防抖合并触摸事件。
     * <p>
     * 网络帧尚未出来时只保留最后一个目标，不继续向 ExoPlayer 堆 seek。否则长视频上
     * 每个 ACTION_MOVE 都可能跨过一个 5 秒格，HLS 分片会被每秒取消、重开几十次，
     * 最后一帧只能等手停下来以后才真正开始加载。
     */
    public void seek(long position) {
        App.removeCallbacks(idleRelease);
        if (item == null || view == null) return;
        int key = toBucket(position);
        requestedBucket = key;
        Bitmap cached = frameCache.get(key);
        if (cached != null) {
            pendingBucket = -1;
            App.removeCallbacks(pendingSeek, forcedSeek);
            if (callback != null) callback.onPreviewFrame(cached);
            return;
        }
        if (callback != null) callback.onPreviewLoading();
        if (player == null) {
            create(key);
            return;
        }
        if (key == bucket) {
            pendingBucket = -1;
            App.removeCallbacks(pendingSeek, forcedSeek);
            return;
        }
        pendingBucket = key;
        App.post(pendingSeek, SEEK_DEBOUNCE);
        // 手指在一个位置停稳后，允许打断一次仍未完成的旧请求，免得最终画面还要
        // 排在已经过时的 HLS 分片后面。每轮缓冲最多抢占一次，不会退回连续 seek 风暴。
        App.post(forcedSeek, SEEK_FORCE_DELAY);
    }

    /** 松手时调用。不立刻放掉——用户往往会连着拖好几次，留一会儿省得反复重建。 */
    public void idle() {
        App.post(idleRelease, IDLE_RELEASE);
    }

    public void release() {
        releasePlayer();
        frameCache.evictAll();
        requestedBucket = -1;
        item = null;
        url = null;
    }

    /** 闲置时只释放解码器；已显示过的帧留到换片源或退出页面，回拖才能立即命中。 */
    private void releasePlayer() {
        App.removeCallbacks(idleRelease, pendingSeek, forcedSeek);
        cacheCurrentFrame();
        bucket = -1;
        renderedBucket = -1;
        pendingBucket = -1;
        forcedWhileBuffering = false;
        if (player == null) return;
        player.removeListener(this);
        player.clearVideoTextureView(view);
        player.release();
        player = null;
    }

    private int toBucket(long position) {
        return (int) (Math.max(0, position) / STEP);
    }

    private void dispatchPendingSeek(boolean force) {
        if (player == null || pendingBucket < 0) return;
        boolean buffering = player.getPlaybackState() == Player.STATE_BUFFERING;
        // 普通防抖不打断网络加载；手指停稳 180ms 后可以抢占一次过时请求。
        if (buffering && (!force || forcedWhileBuffering)) return;
        if (buffering) forcedWhileBuffering = true;
        int key = pendingBucket;
        pendingBucket = -1;
        if (key == bucket) return;
        App.removeCallbacks(pendingSeek, forcedSeek);
        // 当前画面已经真正显示过，跳走前存下来；之后往回拖不再访问网络。
        cacheCurrentFrame();
        bucket = key;
        player.seekTo(key * (long) STEP);
    }

    private void create(int initialBucket) {
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
        bucket = initialBucket;
        renderedBucket = -1;
        requestedBucket = initialBucket;
        pendingBucket = -1;
        forcedWhileBuffering = false;
        // 把目标位置作为初始播放点，prepare 只发起一次正确位置的加载。
        player.setMediaItem(item, initialBucket * (long) STEP);
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
    public void onPlaybackStateChanged(int playbackState) {
        if (playbackState != Player.STATE_READY) return;
        forcedWhileBuffering = false;
        if (pendingBucket < 0) return;
        // 当前帧已完成加载，立即跳到拖动期间记录的最新位置。
        App.removeCallbacks(pendingSeek, forcedSeek);
        App.post(pendingSeek);
    }

    @Override
    public void onRenderedFirstFrame() {
        // bucket 是播放器正在请求的位置；只有收到这个回调后，它才真的出现在 TextureView 上。
        renderedBucket = bucket;
        cacheCurrentFrame();
    }

    /**
     * TextureView 上已经出现的帧压到 320px 宽后放进 8MB LRU。本方法只在首帧渲染完成、
     * seek 离开当前帧或释放解码器时调用，不跟随手指高频截图。
     */
    private void cacheCurrentFrame() {
        if (view == null || !view.isAvailable() || renderedBucket < 0 || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        int width = Math.min(FRAME_CACHE_WIDTH, view.getWidth());
        int height = Math.max(1, Math.round(width * view.getHeight() / (float) view.getWidth()));
        Bitmap bitmap = view.getBitmap(width, height);
        if (bitmap == null) return;
        int key = renderedBucket;
        frameCache.put(key, bitmap);
        if (key == requestedBucket && callback != null) callback.onPreviewFrame(bitmap);
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

        /** 命中内存缓存或新帧完成渲染，直接覆盖到预览窗。 */
        void onPreviewFrame(Bitmap bitmap);

        /** 当前目标没有缓存，先露出 TextureView 的上一帧等待网络更新。 */
        void onPreviewLoading();

        /** 换片源时清掉上一集留下的占位图。 */
        void onPreviewReset();

        void onPreviewFail();
    }
}
