package com.fongmi.android.tv.player.exo;

import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;

import android.net.Uri;
import android.os.StatFs;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory;
import androidx.media3.exoplayer.offline.DownloadHelper;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.Downloader;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 当前播放集的临时磁盘预取。
 *
 * <p>它与 ExoPlayer 的内存前向缓冲相互独立：主播放器只负责保证流畅播放，本类在
 * 主播放器稳定后用 Media3 Downloader 把同一清晰度的剩余媒体写进共享 SimpleCache。
 * 切集或退出播放页时只移除本次请求写入的资源，不影响正式下载目录。</p>
 */
public final class PlaybackCache {

    private static final long MIN_FREE_SPACE = 1024L * 1024 * 1024;
    private static final long STORAGE_CHECK_BYTES = 8L * 1024 * 1024;
    private static final long STORAGE_CHECK_INTERVAL_MS = 2000;
    private static final long PROGRESS_LOG_INTERVAL_MS = 10000;
    private static final float PROGRESS_LOG_STEP = 5f;
    private static final long REFOCUS_THRESHOLD_MS = 10000;
    private static final AtomicInteger ACTIVE_INSTANCES = new AtomicInteger();

    private final ExecutorService executor;
    private final Listener listener;
    private Session session;
    private boolean released;
    private long stateVersion;

    public PlaybackCache(Listener listener) {
        this.listener = listener;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "playback-cache");
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        ACTIVE_INSTANCES.incrementAndGet();
    }

    /** 自动清理器据此避开正在使用的 SimpleCache。 */
    public static boolean isPlaybackActive() {
        return ACTIVE_INSTANCES.get() > 0;
    }

    public void start(MediaItem item, TrackSelectionParameters parameters, long positionMs, long durationMs) {
        if (isFullyLocal(item)) {
            stop();
            Logger.i("PlaybackCache: local media is fully available, " + describe(item));
            notifyProgress(100f);
            return;
        }
        String unsupported = unsupportedReason(item);
        if (unsupported != null) {
            stop();
            Logger.i("PlaybackCache: skipped reason=" + unsupported + ", " + describe(item));
            notifyProgress(-1f);
            return;
        }
        Session old;
        Session current;
        Session matching = null;
        Downloader restart = null;
        boolean refocused = false;
        synchronized (this) {
            if (released) return;
            stateVersion++;
            if (session != null && session.matches(item)) {
                matching = session;
                refocused = session.shouldRefocus(positionMs, durationMs) && session.setFocus(positionMs, durationMs);
                if (refocused) {
                    session.cancelVersion++;
                    restart = session.downloader;
                }
                session.paused = false;
                queueDownloadLocked(session);
                old = null;
                current = null;
            } else {
                old = session;
                session = current = new Session(item, buildCacheDataSource(item), positionMs, durationMs);
            }
        }
        if (matching != null) {
            if (restart != null) restart.cancel();
            Logger.i("PlaybackCache: resumed focusMs=" + matching.focusStartMs + " refocused=" + refocused
                    + ", " + describe(item));
            return;
        }
        cancel(old);
        enqueueCleanup(old);
        notifyProgress(current, current.initialPercent());
        Logger.i("PlaybackCache: scheduled focusMs=" + current.focusStartMs + " durationMs=" + current.totalDurationMs
                + ", " + describe(item));
        if (!hasStorageReserve()) {
            synchronized (this) {
                if (session == current) {
                    current.lowStorage = true;
                    current.paused = true;
                }
            }
            Logger.w("PlaybackCache", "Prefetch skipped: less than 1 GB free storage");
            return;
        }
        prepare(current, parameters);
    }

    /**
     * 用户跳到远处后，让分段流的后台任务从新位置向结尾重新排队。已写入磁盘的旧分片
     * 不删除，Downloader 再遇到它们会直接命中缓存；渐进式单文件无法按时间换算字节，
     * 仍从文件头连续下载。
     */
    public void focus(long positionMs, long durationMs) {
        Session target;
        Downloader downloader;
        synchronized (this) {
            target = session;
            if (released || target == null || !target.segmented) return;
            if (!target.shouldRefocus(positionMs, durationMs) || !target.setFocus(positionMs, durationMs)) return;
            target.paused = true;
            target.cancelVersion++;
            downloader = target.downloader;
        }
        Logger.i("PlaybackCache: refocus after seek focusMs=" + target.focusStartMs + " durationMs=" + target.totalDurationMs);
        notifyProgress(target, target.initialPercent());
        if (downloader != null) downloader.cancel();
    }

    public void pause() {
        Downloader downloader;
        synchronized (this) {
            if (session == null || released) return;
            if (session.paused) return;
            session.paused = true;
            session.cancelVersion++;
            downloader = session.downloader;
        }
        Logger.i("PlaybackCache: paused to prioritize foreground playback/preview");
        if (downloader != null) downloader.cancel();
    }

    public synchronized void resume() {
        if (session == null || released || session.lowStorage || session.failed || session.complete) return;
        if (!session.paused) return;
        session.paused = false;
        Logger.i("PlaybackCache: resumed after foreground ready");
        queueDownloadLocked(session);
    }

    public void stop() {
        Session old;
        synchronized (this) {
            if (released) return;
            stateVersion++;
            old = session;
            session = null;
        }
        cancel(old);
        enqueueCleanup(old);
        if (old != null) Logger.i("PlaybackCache: stopped and cleanup queued");
    }

    public void release() {
        Session old;
        synchronized (this) {
            if (released) return;
            released = true;
            stateVersion++;
            old = session;
            session = null;
        }
        cancel(old);
        Logger.i("PlaybackCache: release requested");
        try {
            executor.execute(() -> {
                try {
                    cleanup(old);
                    // VideoActivity 会先释放主播放器和预览播放器，此时可以清掉本次页面中
                    // 由它们写入、但不属于 Downloader 选中轨道的零散分片。
                    CacheManager.get().clear();
                } finally {
                    ACTIVE_INSTANCES.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            ACTIVE_INSTANCES.decrementAndGet();
        }
        executor.shutdown();
    }

    private void prepare(Session target, TrackSelectionParameters parameters) {
        DownloadHelper helper;
        try {
            helper = new DownloadHelper.Factory()
                    .setDataSourceFactory(target.factory)
                    .setRenderersFactory(ExoUtil.buildRenderersFactory(EXTENSION_RENDERER_MODE_OFF))
                    .setTrackSelectionParameters(parameters)
                    .create(target.item);
        } catch (RuntimeException e) {
            synchronized (this) {
                if (session == target) target.failed = true;
            }
            Logger.e("PlaybackCache", e);
            return;
        }
        synchronized (this) {
            if (released || session != target) {
                helper.release();
                return;
            }
            target.helper = helper;
        }
        helper.prepare(new DownloadHelper.Callback() {
            @Override
            public void onPrepared(@NonNull DownloadHelper prepared, boolean tracksInfoAvailable) {
                synchronized (PlaybackCache.this) {
                    if (released || session != target) {
                        prepared.release();
                        return;
                    }
                    target.baseRequest = prepared.getDownloadRequest(target.id, null);
                    target.rebuildRequest();
                    target.prepared = true;
                    target.helper = null;
                    prepared.release();
                    Logger.i("PlaybackCache: manifest prepared segmented=" + target.segmented
                            + " focusMs=" + target.focusStartMs);
                    queueDownloadLocked(target);
                }
            }

            @Override
            public void onPrepareError(@NonNull DownloadHelper failed, @NonNull IOException error) {
                synchronized (PlaybackCache.this) {
                    if (session == target) target.failed = true;
                    target.helper = null;
                }
                failed.release();
                Logger.e("PlaybackCache", error);
            }
        });
    }

    private void queueDownloadLocked(Session target) {
        if (!target.prepared || target.paused || target.lowStorage || target.failed || target.complete || target.queued || released || session != target) return;
        target.queued = true;
        try {
            executor.execute(() -> download(target));
        } catch (RejectedExecutionException e) {
            target.queued = false;
        }
    }

    private void download(Session target) {
        Downloader downloader = null;
        int cancelVersion = -1;
        try {
            synchronized (this) {
                if (released || session != target || target.paused || target.lowStorage || target.failed || target.complete) {
                    target.queued = false;
                    return;
                }
                cancelVersion = target.cancelVersion;
                downloader = new DefaultDownloaderFactory(target.factory, Runnable::run).createDownloader(target.request);
                target.downloader = downloader;
            }
            Logger.i("PlaybackCache: download started focusMs=" + target.focusStartMs);
            final int downloadVersion = cancelVersion;
            downloader.download((contentLength, bytesDownloaded, percentDownloaded) ->
                    onProgress(target, downloadVersion, bytesDownloaded, percentDownloaded));
            boolean completed;
            synchronized (this) {
                completed = session == target && !target.paused && !target.lowStorage && target.cancelVersion == cancelVersion;
                if (completed) target.complete = true;
            }
            if (completed) {
                Logger.i("PlaybackCache: download complete");
                notifyProgress(target, 100f);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (this) {
                if (session == target && !target.paused && target.cancelVersion == cancelVersion) target.failed = true;
            }
        } catch (IOException e) {
            boolean fallback = false;
            synchronized (this) {
                if (session == target && !target.paused && target.cancelVersion == cancelVersion) {
                    // 少数 HLS 源/代理不接受按时间范围后产生的 Range 请求，会返回 416。
                    // 已有缓存不能因此作废：退回整集顺序下载一次，仍会跳过磁盘中已有分片。
                    if (target.segmented && target.focusStartMs > 0 && hasResponseCode(e, 416)) {
                        target.setFocus(0, target.totalDurationMs);
                        target.cancelVersion++;
                        fallback = true;
                    } else {
                        target.failed = true;
                    }
                }
            }
            if (fallback) Logger.w("PlaybackCache", "Time-range request returned 416; retrying full episode without deleting cached spans");
            else Logger.e("PlaybackCache", e);
        } catch (CancellationException e) {
            // pause()/focus()/stop() 主动 cancel 下载时，Media3 用 CancellationException
            // 结束当前任务。这是正常状态切换，不能标成 failed，否则 READY 后永远不会续传。
            boolean unexpected;
            synchronized (this) {
                unexpected = session == target && !target.paused && target.cancelVersion == cancelVersion;
                if (unexpected) target.failed = true;
            }
            if (unexpected) Logger.e("PlaybackCache", e);
        } catch (RuntimeException e) {
            synchronized (this) {
                if (session == target && !target.paused && target.cancelVersion == cancelVersion) target.failed = true;
            }
            Logger.e("PlaybackCache", e);
        } finally {
            synchronized (this) {
                target.downloader = null;
                target.queued = false;
                queueDownloadLocked(target);
            }
        }
    }

    private void onProgress(Session target, int downloadVersion, long bytesDownloaded, float percentDownloaded) {
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (this) {
            if (session != target || target.cancelVersion != downloadVersion) return;
        }
        if (percentDownloaded != C.PERCENTAGE_UNSET && !Float.isNaN(percentDownloaded)) {
            float absolutePercent;
            boolean changed;
            boolean log;
            synchronized (this) {
                if (session != target || target.cancelVersion != downloadVersion) return;
                absolutePercent = target.absolutePercent(percentDownloaded);
                target.percent = Math.max(target.percent, absolutePercent);
                changed = target.percent - target.notifiedPercent >= 0.1f || now - target.notifyTime >= 250;
                if (changed) {
                    target.notifiedPercent = target.percent;
                    target.notifyTime = now;
                }
                log = target.percent - target.loggedPercent >= PROGRESS_LOG_STEP
                        || now - target.logTime >= PROGRESS_LOG_INTERVAL_MS
                        || target.percent >= 99.9f;
                if (log) {
                    target.loggedPercent = target.percent;
                    target.logTime = now;
                }
            }
            if (changed) notifyProgress(target, target.percent);
            if (log) Logger.i("PlaybackCache: progress=" + Math.round(target.percent) + "% bytes=" + bytesDownloaded);
        }
        if (bytesDownloaded - target.storageCheckBytes < STORAGE_CHECK_BYTES && now - target.storageCheckTime < STORAGE_CHECK_INTERVAL_MS) return;
        target.storageCheckBytes = bytesDownloaded;
        target.storageCheckTime = now;
        if (hasStorageReserve()) return;
        Downloader downloader;
        synchronized (this) {
            if (session != target || target.lowStorage) return;
            target.lowStorage = true;
            target.paused = true;
            target.cancelVersion++;
            downloader = target.downloader;
        }
        Logger.w("PlaybackCache", "Prefetch stopped: preserving 1 GB free storage");
        if (downloader != null) downloader.cancel();
    }

    private void notifyProgress(Session target, float percent) {
        if (listener == null) return;
        App.post(() -> {
            synchronized (PlaybackCache.this) {
                if (released || session != target) return;
            }
            listener.onProgress(percent);
        });
    }

    private void notifyProgress(float percent) {
        if (listener == null) return;
        final long version;
        synchronized (this) {
            version = stateVersion;
        }
        App.post(() -> {
            synchronized (PlaybackCache.this) {
                if (released || stateVersion != version) return;
            }
            listener.onProgress(percent);
        });
    }

    private void enqueueCleanup(Session target) {
        if (target == null) return;
        try {
            executor.execute(() -> cleanup(target));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void cleanup(Session target) {
        if (target == null) return;
        try {
            new DefaultDownloaderFactory(target.factory, Runnable::run).createDownloader(target.request).remove();
            Logger.i("PlaybackCache: cleanup complete");
        } catch (Exception e) {
            // 渐进式资源通常直接以 URI 为 key；即使 Downloader 清理失败也尽量移除它。
            CacheManager.get().getCache().removeResource(target.uri.toString());
            Logger.e("PlaybackCache", e);
        }
    }

    private void cancel(Session target) {
        if (target == null) return;
        DownloadHelper helper;
        Downloader downloader;
        synchronized (this) {
            target.paused = true;
            target.cancelVersion++;
            helper = target.helper;
            downloader = target.downloader;
        }
        if (helper != null) helper.release();
        if (downloader != null) downloader.cancel();
    }

    private boolean hasStorageReserve() {
        try {
            StatFs stat = new StatFs(Path.exo().getAbsolutePath());
            return stat.getAvailableBytes() >= MIN_FREE_SPACE;
        } catch (Exception e) {
            // 无法读取容量时不能误伤正常播放；下载器自身的 IO 错误仍会安全终止任务。
            Logger.e("PlaybackCache", e);
            return true;
        }
    }

    private static boolean hasResponseCode(Throwable error, int code) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpDataSource.InvalidResponseCodeException
                    && ((HttpDataSource.InvalidResponseCodeException) current).responseCode == code) return true;
            current = current.getCause();
        }
        return false;
    }

    private static String unsupportedReason(MediaItem item) {
        if (item == null || item.localConfiguration == null) return "missing-media-item";
        if (item.localConfiguration.drmConfiguration != null) return "drm";
        Uri uri = item.localConfiguration.uri;
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return "non-http";
        if (item.mediaId.contains("***") || item.mediaId.contains("|||")) return "compound-source";
        return Util.inferContentTypeForUriAndMimeType(uri, item.localConfiguration.mimeType) == C.CONTENT_TYPE_RTSP ? "rtsp" : null;
    }

    public static boolean isFullyLocal(MediaItem item) {
        if (item == null || item.localConfiguration == null) return false;
        Uri uri = item.localConfiguration.uri;
        String scheme = uri.getScheme();
        if (!(scheme == null || scheme.isEmpty() || "file".equalsIgnoreCase(scheme))) return false;
        File file = new File(uri.getPath() == null ? uri.toString() : uri.getPath());
        if (!file.isFile()) return false;
        String lower = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".m3u8") && !lower.endsWith(".m3u")) return true;
        try {
            String path = file.getCanonicalPath();
            String root = Path.download().getCanonicalPath();
            return path.startsWith(root + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private static String describe(MediaItem item) {
        if (item == null || item.localConfiguration == null) return "source=none";
        Uri uri = item.localConfiguration.uri;
        int type = Util.inferContentTypeForUriAndMimeType(uri, item.localConfiguration.mimeType);
        String host = uri.getHost();
        String name = uri.getLastPathSegment();
        return "scheme=" + uri.getScheme() + " type=" + type
                + " host=" + (host == null ? "-" : host)
                + " name=" + (name == null ? "-" : name);
    }

    private static CacheDataSource.Factory buildCacheDataSource(MediaItem item) {
        Map<String, String> headers = new HashMap<>();
        for (String key : item.requestMetadata.extras.keySet()) {
            Object value = item.requestMetadata.extras.get(key);
            if (value != null) headers.put(key, value.toString());
        }
        HttpDataSource.Factory http = new OkHttpDataSource.Factory(OkHttp.client());
        http.setDefaultRequestProperties(headers);
        DataSource.Factory upstream = new DefaultDataSource.Factory(App.get(), http);
        return new CacheDataSource.Factory()
                .setCache(CacheManager.get().getCache())
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private static final class Session {

        private final String id = "playback-" + UUID.randomUUID();
        private final MediaItem item;
        private final Uri uri;
        private final String mimeType;
        private final CacheDataSource.Factory factory;
        private final int contentType;
        private final boolean segmented;
        private DownloadRequest baseRequest;
        private DownloadRequest request;
        private DownloadHelper helper;
        private Downloader downloader;
        private boolean prepared;
        private boolean paused;
        private boolean queued;
        private boolean complete;
        private boolean failed;
        private boolean lowStorage;
        private float percent = -1f;
        private float notifiedPercent = -1f;
        private long storageCheckBytes;
        private long storageCheckTime;
        private long notifyTime;
        private long logTime;
        private long focusStartMs;
        private long totalDurationMs;
        private float loggedPercent = -PROGRESS_LOG_STEP;
        private int cancelVersion;

        private Session(MediaItem item, CacheDataSource.Factory factory, long positionMs, long durationMs) {
            this.item = item;
            this.uri = item.localConfiguration.uri;
            this.mimeType = item.localConfiguration.mimeType;
            this.factory = factory;
            this.contentType = Util.inferContentTypeForUriAndMimeType(uri, mimeType);
            this.segmented = contentType == C.CONTENT_TYPE_DASH || contentType == C.CONTENT_TYPE_HLS || contentType == C.CONTENT_TYPE_SS;
            DownloadRequest.Builder builder = new DownloadRequest.Builder(id, uri);
            if (!TextUtils.isEmpty(mimeType)) builder.setMimeType(mimeType);
            this.baseRequest = this.request = builder.build();
            setFocus(positionMs, durationMs);
        }

        private boolean setFocus(long positionMs, long durationMs) {
            long duration = normalizedDuration(durationMs);
            long position = normalizedPosition(positionMs, duration);
            boolean changed = position != focusStartMs || duration != totalDurationMs;
            focusStartMs = position;
            totalDurationMs = duration;
            if (!changed) return false;
            complete = false;
            failed = false;
            percent = initialPercent();
            notifiedPercent = -1f;
            loggedPercent = percent - PROGRESS_LOG_STEP;
            logTime = 0;
            if (prepared) rebuildRequest();
            return true;
        }

        private boolean shouldRefocus(long positionMs, long durationMs) {
            long duration = normalizedDuration(durationMs);
            long position = normalizedPosition(positionMs, duration);
            if (duration != totalDurationMs) return true;
            long cachedEndMs = percent < 0 || duration <= 0
                    ? focusStartMs
                    : Math.round(duration * (percent / 100f));
            if (position >= focusStartMs && position <= cachedEndMs) return false;
            return Math.abs(position - focusStartMs) >= REFOCUS_THRESHOLD_MS;
        }

        private long normalizedDuration(long durationMs) {
            return Math.max(0, durationMs);
        }

        private long normalizedPosition(long positionMs, long durationMs) {
            if (!segmented) return 0;
            return Math.max(0, Math.min(positionMs, durationMs));
        }

        private void rebuildRequest() {
            DownloadRequest source = baseRequest;
            DownloadRequest.Builder builder = new DownloadRequest.Builder(id, source.uri);
            if (!TextUtils.isEmpty(source.mimeType)) builder.setMimeType(source.mimeType);
            if (!source.streamKeys.isEmpty()) builder.setStreamKeys(source.streamKeys);
            if (source.keySetId != null) builder.setKeySetId(source.keySetId);
            if (!TextUtils.isEmpty(source.customCacheKey)) builder.setCustomCacheKey(source.customCacheKey);
            if (source.data != null) builder.setData(source.data);
            if (source.byteRange != null) builder.setByteRange(source.byteRange.offset, source.byteRange.length);
            if (segmented && totalDurationMs > focusStartMs) {
                builder.setTimeRange(Util.msToUs(focusStartMs), Util.msToUs(totalDurationMs - focusStartMs));
            }
            request = builder.build();
        }

        private float initialPercent() {
            if (!segmented || totalDurationMs <= 0) return 0f;
            return Math.min(100f, focusStartMs * 100f / totalDurationMs);
        }

        private float absolutePercent(float rangePercent) {
            if (!segmented || totalDurationMs <= 0) return rangePercent;
            float start = initialPercent();
            return Math.min(100f, start + (100f - start) * (rangePercent / 100f));
        }

        private boolean matches(MediaItem other) {
            return other != null
                    && other.localConfiguration != null
                    && uri.equals(other.localConfiguration.uri)
                    && TextUtils.equals(mimeType, other.localConfiguration.mimeType);
        }
    }

    public interface Listener {
        void onProgress(float percent);
    }
}
