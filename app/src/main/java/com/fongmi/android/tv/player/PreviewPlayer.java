package com.fongmi.android.tv.player;

import android.graphics.Bitmap;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.github.catvod.utils.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

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
 * 触摸事件仍会做很短的防抖，但真正交给播放器的是手指对应的毫秒位置，不再向下
 * 取整，也不再只取目标之前的关键帧。这样预览帧和主播放器松手后的 seek 目标一致。
 */
public class PreviewPlayer implements Player.Listener, VideoFrameMetadataListener, CacheDataSource.EventListener {

    private static final int FRAME_BUCKET_MS = 100;
    private static final long EXACT_FRAME_TOLERANCE_MS = 50;
    private static final long VALID_FRAME_TOLERANCE_MS = 120;
    private static final long LOCAL_SEEK_INTERVAL = 32;
    private static final long LOCAL_FRAME_TIMEOUT = 500;
    private static final long NETWORK_REQUEST_SLICE_MS = 280;
    private static final long NETWORK_FRAME_TIMEOUT = 4000;
    private static final long SEEK_DEBOUNCE = 48;
    private static final long IDLE_RELEASE = 10000;
    private static final int FRAME_CACHE_BYTES = 16 * 1024 * 1024;
    private static final int FRAME_CACHE_WIDTH = 320;

    private final Runnable idleRelease;
    private final Runnable pendingSeek;
    private final Runnable forcedSeek;
    private final Runnable frameWatchdog;
    private final LruCache<Integer, Frame> frameCache;
    private final AtomicLong cachedBytesRead;
    private ExoPlayer player;
    private TextureView view;
    private Callback callback;
    private MediaItem item;
    private TrackSelectionParameters trackParameters;
    private String url;
    private volatile long activePositionMs;
    private long renderedPositionMs;
    private long renderedActualPositionMs;
    private long pendingPositionMs;
    private long requestedPositionMs;
    private long shownRequestPositionMs;
    private long shownActualPositionMs;
    private boolean localSource;
    private boolean pendingSeekScheduled;
    private boolean awaitingFrame;
    private boolean activeExact;
    private volatile long frameTimestampOffsetUs;
    private volatile int seekGeneration;
    private long activeSeekStartedMs;
    private long activeCachedBytesStart;
    private int activeRetryCount;

    public PreviewPlayer() {
        this.idleRelease = this::releasePlayer;
        this.pendingSeek = () -> {
            pendingSeekScheduled = false;
            dispatchPendingSeek(false);
        };
        this.forcedSeek = () -> dispatchPendingSeek(true);
        this.frameWatchdog = this::onFrameTimeout;
        this.cachedBytesRead = new AtomicLong();
        this.frameCache = new LruCache<Integer, Frame>(FRAME_CACHE_BYTES) {
            @Override
            protected int sizeOf(@NonNull Integer key, @NonNull Frame value) {
                return value.bitmap.getByteCount();
            }
        };
        resetPositions();
    }

    public void attach(TextureView view, Callback callback) {
        this.callback = callback;
        this.view = view;
    }

    /** 换片源：地址没变就什么都不做，变了就把旧的放掉，下次拖动时按新地址重建。 */
    public void setSource(String url, MediaItem item, TrackSelectionParameters trackParameters) {
        boolean sameSource = TextUtils.equals(url, this.url) && Objects.equals(item, this.item);
        this.trackParameters = trackParameters;
        if (sameSource) {
            if (player != null && trackParameters != null) player.setTrackSelectionParameters(previewTrackParameters());
            return;
        }
        releasePlayer();
        frameCache.evictAll();
        this.url = url;
        this.item = item;
        this.localSource = isLocal(item);
        this.frameTimestampOffsetUs = C.TIME_UNSET;
        this.requestedPositionMs = C.TIME_UNSET;
        this.shownRequestPositionMs = C.TIME_UNSET;
        this.shownActualPositionMs = C.TIME_UNSET;
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
        create(normalize(position));
    }

    /**
     * 拖动中调用。短节流只限制请求频率，不改变最后交给播放器的毫秒位置。
     * <p>
     * 网络帧尚未出来时只保留最后一个目标，不继续向 ExoPlayer 堆 seek。否则长视频上
     * 每个 ACTION_MOVE 都可能落到不同位置，HLS 分片会被每秒取消、重开几十次，
     * 最后一帧只能等手停下来以后才真正开始加载。
     */
    public void seek(long position) {
        App.removeCallbacks(idleRelease);
        if (item == null || view == null) return;
        position = normalize(position);
        requestedPositionMs = position;
        Frame cached = findExactFrame(position);
        if (cached != null) {
            pendingPositionMs = C.TIME_UNSET;
            App.removeCallbacks(pendingSeek, forcedSeek);
            pendingSeekScheduled = false;
            showFrame(cached);
            return;
        }
        if (callback != null) {
            shownRequestPositionMs = C.TIME_UNSET;
            shownActualPositionMs = C.TIME_UNSET;
            callback.onPreviewLoading();
        }
        if (player == null) {
            create(position);
            return;
        }
        if (Math.abs(position - activePositionMs) <= EXACT_FRAME_TOLERANCE_MS) {
            pendingPositionMs = C.TIME_UNSET;
            App.removeCallbacks(pendingSeek, forcedSeek);
            pendingSeekScheduled = false;
            return;
        }
        pendingPositionMs = position;
        // 普通更新是节流而不是防抖：连续拖动时也要稳定刷新，不能因为 ACTION_MOVE
        // 一直重置 48ms 计时器而等到手指停下才动。完整本地媒体可以更高频。
        if (!pendingSeekScheduled && !awaitingFrame) {
            pendingSeekScheduled = true;
            App.post(pendingSeek, localSource ? LOCAL_SEEK_INTERVAL : SEEK_DEBOUNCE);
        }
        // 在线请求最多占用一个很短的时间片。继续拖动时，到时间就把旧请求替换为
        // 最新目标；手指停下后则不再取消最后一次加载。这样既不会每个 MOVE 都重开
        // HLS 分片，也不会像 beta6 那样一次 BUFFERING 卡住后几十秒都不更新。
        if (!localSource && awaitingFrame) {
            long elapsed = SystemClock.elapsedRealtime() - activeSeekStartedMs;
            App.post(forcedSeek, Math.max(0, NETWORK_REQUEST_SLICE_MS - elapsed));
        }
    }

    /** 松手时调用。不立刻放掉——用户往往会连着拖好几次，留一会儿省得反复重建。 */
    public void idle() {
        App.post(idleRelease, IDLE_RELEASE);
    }

    /** 记录松手瞬间预览窗实际显示的是哪一帧，便于真机日志直接判断偏差来自哪里。 */
    public void finish(long position) {
        position = normalize(position);
        seek(position);
        String shown = shownRequestPositionMs == C.TIME_UNSET
                ? "none"
                : shownRequestPositionMs + "/" + shownActualPositionMs;
        Logger.i("Preview: scrub-stop targetMs=" + position + " shownRequest/actualMs=" + shown
                + " liveRequest/actualMs=" + valueOf(renderedPositionMs) + "/" + valueOf(renderedActualPositionMs)
                + " activeMs=" + valueOf(activePositionMs) + " pendingMs=" + valueOf(pendingPositionMs)
                + " local=" + localSource);
        idle();
    }

    /** 主播放器正在重新缓冲时立即让出网络和解码器，帧缓存与片源信息仍然保留。 */
    public void suspend() {
        releasePlayer();
    }

    public void release() {
        releasePlayer();
        frameCache.evictAll();
        requestedPositionMs = C.TIME_UNSET;
        shownRequestPositionMs = C.TIME_UNSET;
        shownActualPositionMs = C.TIME_UNSET;
        item = null;
        url = null;
    }

    /** 闲置时只释放解码器；已显示过的帧留到换片源或退出页面，回拖才能立即命中。 */
    private void releasePlayer() {
        App.removeCallbacks(idleRelease, pendingSeek, forcedSeek, frameWatchdog);
        cacheCurrentFrame();
        activePositionMs = C.TIME_UNSET;
        renderedPositionMs = C.TIME_UNSET;
        renderedActualPositionMs = C.TIME_UNSET;
        pendingPositionMs = C.TIME_UNSET;
        pendingSeekScheduled = false;
        awaitingFrame = false;
        activeExact = false;
        activeRetryCount = 0;
        if (player == null) return;
        player.removeListener(this);
        player.clearVideoFrameMetadataListener(this);
        player.clearVideoTextureView(view);
        player.release();
        player = null;
    }

    private void resetPositions() {
        activePositionMs = C.TIME_UNSET;
        renderedPositionMs = C.TIME_UNSET;
        renderedActualPositionMs = C.TIME_UNSET;
        pendingPositionMs = C.TIME_UNSET;
        requestedPositionMs = C.TIME_UNSET;
        shownRequestPositionMs = C.TIME_UNSET;
        shownActualPositionMs = C.TIME_UNSET;
        pendingSeekScheduled = false;
        frameTimestampOffsetUs = C.TIME_UNSET;
        seekGeneration = 0;
        awaitingFrame = false;
        activeExact = false;
        activeRetryCount = 0;
    }

    private long normalize(long position) {
        return Math.max(0, position);
    }

    private boolean isLocal(MediaItem value) {
        if (value == null || value.localConfiguration == null) return false;
        String scheme = value.localConfiguration.uri.getScheme();
        return scheme == null || scheme.isEmpty() || "file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme);
    }

    private int toBucket(long position) {
        return (int) Math.min(Integer.MAX_VALUE, (position + FRAME_BUCKET_MS / 2) / FRAME_BUCKET_MS);
    }

    private Frame findExactFrame(long position) {
        Frame frame = frameCache.get(toBucket(position));
        return frame != null && frame.exact
                && Math.abs(frame.requestPositionMs - position) <= EXACT_FRAME_TOLERANCE_MS
                && Math.abs(frame.actualPositionMs - position) <= VALID_FRAME_TOLERANCE_MS ? frame : null;
    }

    private void showFrame(Frame frame) {
        shownRequestPositionMs = frame.requestPositionMs;
        shownActualPositionMs = frame.actualPositionMs;
        if (callback != null) callback.onPreviewFrame(frame.bitmap);
    }

    private String valueOf(long value) {
        return value == C.TIME_UNSET ? "none" : String.valueOf(value);
    }

    private void dispatchPendingSeek(boolean force) {
        if (player == null || pendingPositionMs == C.TIME_UNSET) return;
        // 所有片源都以“真正渲染出帧”为完成信号，READY 不能代表 TextureView 已更新。
        // 在线源只有时间片到期后才能强制替换旧请求；本地源由短 watchdog 释放。
        if (awaitingFrame && (!force || localSource)) return;
        long position = pendingPositionMs;
        pendingPositionMs = C.TIME_UNSET;
        if (Math.abs(position - activePositionMs) <= EXACT_FRAME_TOLERANCE_MS) return;
        App.removeCallbacks(pendingSeek, forcedSeek);
        pendingSeekScheduled = false;
        // 当前画面已经真正显示过，跳走前存下来；之后往回拖不再访问网络。
        cacheCurrentFrame();
        renderedPositionMs = C.TIME_UNSET;
        renderedActualPositionMs = C.TIME_UNSET;
        activePositionMs = position;
        activeExact = true;
        awaitingFrame = true;
        activeRetryCount = 0;
        activeSeekStartedMs = SystemClock.elapsedRealtime();
        activeCachedBytesStart = cachedBytesRead.get();
        seekGeneration++;
        player.setSeekParameters(SeekParameters.EXACT);
        player.seekTo(position);
        armFrameWatchdog();
    }

    private void create(long initialPositionMs) {
        // 预览这一路不跟随解码设置：软解在 seek 密集时更慢，统一交给系统自动选
        player = new ExoPlayer.Builder(App.get())
                .setLoadControl(buildLoadControl())
                .setTrackSelector(buildTrackSelector())
                .setRenderersFactory(ExoUtil.buildRenderersFactory(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF))
                .setMediaSourceFactory(ExoUtil.buildMediaSourceFactory(this))
                .build();
        // 预览和主播放器必须落在同一个毫秒目标，不能先展示附近关键帧再二次纠正。
        player.setSeekParameters(SeekParameters.EXACT);
        player.setPlayWhenReady(false);
        player.setVolume(0);
        player.addListener(this);
        player.setVideoFrameMetadataListener(this);
        player.setVideoTextureView(view);
        activePositionMs = initialPositionMs;
        renderedPositionMs = C.TIME_UNSET;
        renderedActualPositionMs = C.TIME_UNSET;
        requestedPositionMs = initialPositionMs;
        pendingPositionMs = C.TIME_UNSET;
        pendingSeekScheduled = false;
        activeExact = true;
        awaitingFrame = true;
        activeRetryCount = 0;
        activeSeekStartedMs = SystemClock.elapsedRealtime();
        activeCachedBytesStart = cachedBytesRead.get();
        frameTimestampOffsetUs = C.TIME_UNSET;
        seekGeneration++;
        // 把目标位置作为初始播放点，prepare 只发起一次正确位置的加载。
        player.setMediaItem(item, initialPositionMs);
        player.prepare();
        armFrameWatchdog();
    }

    private void armFrameWatchdog() {
        App.post(frameWatchdog, localSource ? LOCAL_FRAME_TIMEOUT : NETWORK_FRAME_TIMEOUT);
    }

    private void onFrameTimeout() {
        if (!awaitingFrame) return;
        awaitingFrame = false;
        Logger.i("Preview: frame-timeout source=" + (localSource ? "local" : "network")
                + " state=" + (player == null ? "released" : player.getPlaybackState())
                + " activeMs=" + valueOf(activePositionMs) + " pendingMs=" + valueOf(pendingPositionMs));
        if (pendingPositionMs != C.TIME_UNSET) {
            pendingSeekScheduled = true;
            App.post(pendingSeek);
        } else if (!localSource && player != null && activeRetryCount == 0) {
            // 某些代理 HLS 会停在 BUFFERING 且既不报错也不出帧。最后目标只重建一次
            // 加载状态，防止画面永远冻结，同时避免源站故障时形成无限重试。
            activeRetryCount++;
            long position = activePositionMs;
            renderedPositionMs = C.TIME_UNSET;
            renderedActualPositionMs = C.TIME_UNSET;
            activeSeekStartedMs = SystemClock.elapsedRealtime();
            activeCachedBytesStart = cachedBytesRead.get();
            seekGeneration++;
            awaitingFrame = true;
            player.stop();
            player.setMediaItem(item, position);
            player.prepare();
            armFrameWatchdog();
        }
    }

    /**
     * 预览沿用主播放器当前实际清晰度，才能复用已经播放和后台预取的同一套 HLS/DASH
     * 分片。只关闭音频和字幕，避免多余解码。
     */
    private DefaultTrackSelector buildTrackSelector() {
        DefaultTrackSelector selector = new DefaultTrackSelector(App.get());
        selector.setParameters(previewTrackParameters());
        return selector;
    }

    private TrackSelectionParameters previewTrackParameters() {
        TrackSelectionParameters base = trackParameters == null
                ? TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
                : trackParameters;
        return base.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setForceLowestBitrate(false)
                .setForceHighestSupportedBitrate(true)
                .build();
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
        // READY 往往早于视频帧真正进入 TextureView。无论本地还是在线，此时马上发
        // 下一次 seek 都会把即将显示的帧取消，必须由 cacheRenderedFrame 接棒。
        if (awaitingFrame) return;
        if (pendingPositionMs == C.TIME_UNSET) return;
        // 当前帧已完成加载，立即跳到拖动期间记录的最新位置。
        App.removeCallbacks(pendingSeek, forcedSeek);
        pendingSeekScheduled = true;
        App.post(pendingSeek);
    }

    @Override
    public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs, @NonNull Format format, MediaFormat mediaFormat) {
        if (player == null) return;
        long targetMs = activePositionMs;
        long offsetUs = frameTimestampOffsetUs;
        if (offsetUs == C.TIME_UNSET && targetMs != C.TIME_UNSET) {
            // VideoFrameMetadataListener 在播放器线程回调，不能从这里调用只能在主线程
            // 访问的 ExoPlayer#getCurrentPosition。新建预览播放器后的第一帧没有旧 seek
            // 可以串进来，直接用它和初始目标校准 HLS/DASH 的时间戳偏移。
            offsetUs = presentationTimeUs - targetMs * 1000L;
            frameTimestampOffsetUs = offsetUs;
        }
        if (offsetUs == C.TIME_UNSET) return;
        long actualFrameMs = (presentationTimeUs - offsetUs) / 1000L;
        if (Math.abs(actualFrameMs - targetMs) > VALID_FRAME_TOLERANCE_MS) return;
        int generation = seekGeneration;
        // 回调发生在视频渲染线程；等一帧后 TextureView 才真正包含这张图。期间若又 seek，
        // generation 会变化，旧帧既不显示也不进入缓存。
        App.post(() -> cacheRenderedFrame(generation, targetMs, actualFrameMs), 16);
    }

    private void cacheRenderedFrame(int generation, long targetMs, long actualFrameMs) {
        if (generation != seekGeneration || player == null || targetMs != activePositionMs) return;
        renderedPositionMs = targetMs;
        renderedActualPositionMs = actualFrameMs;
        awaitingFrame = false;
        activeRetryCount = 0;
        App.removeCallbacks(frameWatchdog);
        cacheCurrentFrame(targetMs, actualFrameMs, true);
        // seek 的新帧已经进入 TextureView。若之前为了零等待先盖了一张邻近缓存图，
        // 现在必须撤掉，否则真实画面虽已更新，用户看到的仍会是那张旧截图。
        // 用户可能已经回拖到一张内存截图，而播放器仍在完成上一目标；这种迟到帧可以
        // 留作缓存，但不能撤掉当前截图。只有它仍对应最新手指位置时才露出 TextureView。
        if (targetMs == requestedPositionMs) {
            shownRequestPositionMs = C.TIME_UNSET;
            shownActualPositionMs = C.TIME_UNSET;
            if (callback != null) callback.onPreviewLoading();
        }
        Format selected = player.getVideoFormat();
        long cacheBytes = Math.max(0, cachedBytesRead.get() - activeCachedBytesStart);
        Logger.i("Preview: frame targetMs=" + targetMs + " actualMs=" + actualFrameMs
                + " exact=true latencyMs=" + (SystemClock.elapsedRealtime() - activeSeekStartedMs)
                + " cacheReadBytes=" + cacheBytes + " track=" + describe(selected)
                + " pendingMs=" + valueOf(pendingPositionMs));
        if (pendingPositionMs != C.TIME_UNSET) {
            pendingSeekScheduled = true;
            App.post(pendingSeek);
        }
    }

    /**
     * TextureView 上已经出现的帧压到 320px 宽后放进 16MB LRU。本方法只在首帧渲染完成、
     * seek 离开当前帧或释放解码器时调用，不跟随手指高频截图。
     */
    private void cacheCurrentFrame() {
        if (view == null || !view.isAvailable() || renderedPositionMs == C.TIME_UNSET || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        long actualPositionMs = player == null ? renderedPositionMs : player.getCurrentPosition();
        if (Math.abs(actualPositionMs - renderedPositionMs) > VALID_FRAME_TOLERANCE_MS) return;
        cacheCurrentFrame(renderedPositionMs, actualPositionMs, activeExact);
    }

    private void cacheCurrentFrame(long requestPositionMs, long actualPositionMs, boolean exact) {
        if (view == null || !view.isAvailable() || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        int width = Math.min(FRAME_CACHE_WIDTH, view.getWidth());
        int height = Math.max(1, Math.round(width * view.getHeight() / (float) view.getWidth()));
        Bitmap bitmap = view.getBitmap(width, height);
        if (bitmap == null) return;
        Frame frame = new Frame(bitmap, requestPositionMs, actualPositionMs, exact);
        int key = toBucket(requestPositionMs);
        Frame existing = frameCache.get(key);
        if (existing == null || exact) frameCache.put(key, frame);
        long delta = actualPositionMs - requestPositionMs;
        if (Math.abs(delta) > 80) {
            Logger.i("Preview: rendered targetMs=" + requestPositionMs + " actualMs=" + actualPositionMs + " deltaMs=" + delta);
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        // 预览出不来就收掉，绝不能影响正在放的那一路。
        // 不能在播放器自己的回调里直接 release，抛到下一个消息再做。
        if (callback != null) callback.onPreviewFail();
        App.post(idleRelease, 0);
    }

    @Override
    public void onCachedBytesRead(long cacheSizeBytes, long cachedBytesRead) {
        this.cachedBytesRead.addAndGet(cachedBytesRead);
    }

    @Override
    public void onCacheIgnored(int reason) {
        Logger.i("Preview: disk-cache ignored reason=" + reason);
    }

    private String describe(Format format) {
        if (format == null) return "unknown";
        return format.width + "x" + format.height + "@" + format.bitrate;
    }

    public interface Callback {

        /** 视频宽高比，用来把预览窗调成片源的比例，否则 4:3 的片会被拉扁。 */
        void onPreviewRatio(float ratio);

        /** 命中内存缓存或新帧完成渲染，直接覆盖到预览窗。 */
        void onPreviewFrame(Bitmap bitmap);

        /** 露出 TextureView：既用于等待新帧，也用于新帧完成后撤掉临时截图。 */
        void onPreviewLoading();

        /** 换片源时清掉上一集留下的占位图。 */
        void onPreviewReset();

        void onPreviewFail();
    }

    private static final class Frame {

        private final Bitmap bitmap;
        private final long requestPositionMs;
        private final long actualPositionMs;
        private final boolean exact;

        private Frame(Bitmap bitmap, long requestPositionMs, long actualPositionMs, boolean exact) {
            this.bitmap = bitmap;
            this.requestPositionMs = requestPositionMs;
            this.actualPositionMs = actualPositionMs;
            this.exact = exact;
        }
    }
}
