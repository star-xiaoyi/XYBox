package com.fongmi.android.tv.player;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.text.TextUtils;
import android.util.LruCache;

import com.fongmi.android.tv.App;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 拖动进度时的画面预览。
 * <p>
 * 按 5 秒一格取样，同一格只解码一次，结果放进内存 LRU。抽帧本身很贵（网络源尤其），
 * 所以手指滑动过程中只保留最后一次请求，中间跨过的格子直接丢掉不解。
 * <p>
 * 连续失败若干次就整体降级（unavailable），后面一律回调 null，
 * 由调用方退回纯时间显示——HLS、加密源、纯音频这些本来就抽不出帧，不能让它一直重试卡住。
 * <p>
 * MediaMetadataRetriever 不是线程安全的，所有对它的访问都压在同一个单线程 executor 上。
 */
public class PreviewLoader {

    private static final int STEP = 5000;
    private static final int MAX_FAIL = 3;
    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;

    private final LruCache<Integer, Bitmap> cache;
    private final ExecutorService executor;
    private final AtomicInteger pending;
    private MediaMetadataRetriever retriever;
    private Map<String, String> headers;
    private volatile boolean unavailable;
    private volatile String url;
    private boolean prepared;
    private int fail;

    public PreviewLoader() {
        this.cache = new LruCache<>(48);
        this.executor = Executors.newSingleThreadExecutor();
        this.pending = new AtomicInteger(-1);
        this.unavailable = true;
    }

    /** 换片源时调用，清掉上一集的缓存和降级标记。 */
    public void setSource(String url, Map<String, String> headers) {
        if (TextUtils.equals(url, this.url)) return;
        this.unavailable = TextUtils.isEmpty(url);
        this.url = url;
        cache.evictAll();
        executor.execute(() -> {
            releaseRetriever();
            this.headers = headers;
            this.fail = 0;
        });
    }

    /** 缓存命中会同步回调，未命中则解码完在主线程回调；已降级时回调 null。 */
    public void load(long time, Callback callback) {
        if (unavailable) {
            callback.onPreview(null);
            return;
        }
        int key = (int) (Math.max(0, time) / STEP);
        Bitmap hit = cache.get(key);
        if (hit != null) {
            callback.onPreview(hit);
            return;
        }
        pending.set(key);
        executor.execute(() -> {
            if (unavailable || pending.get() != key) return;
            Bitmap bitmap = decode(key);
            if (bitmap != null) cache.put(key, bitmap);
            if (pending.get() == key) App.post(() -> callback.onPreview(bitmap));
        });
    }

    /** 播放结束或页面销毁时调用。 */
    public void stop() {
        unavailable = true;
        url = null;
        cache.evictAll();
        executor.execute(this::releaseRetriever);
        executor.shutdown();
    }

    private Bitmap decode(int key) {
        try {
            if (!prepared) prepare();
            long us = key * (long) STEP * 1000;
            Bitmap bitmap = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ? retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, HEIGHT) : scale(retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC));
            if (bitmap == null) checkFail();
            else fail = 0;
            return bitmap;
        } catch (Throwable e) {
            checkFail();
            return null;
        }
    }

    private void prepare() throws Exception {
        String source = url;
        if (TextUtils.isEmpty(source)) throw new IllegalStateException();
        retriever = new MediaMetadataRetriever();
        // 带 header 的重载只认 URI，本地文件路径走单参数版本
        if (source.startsWith("http")) retriever.setDataSource(source, headers == null ? new HashMap<>() : headers);
        else retriever.setDataSource(source);
        prepared = true;
    }

    private Bitmap scale(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= WIDTH) return bitmap;
        int height = Math.max(1, bitmap.getHeight() * WIDTH / bitmap.getWidth());
        Bitmap result = Bitmap.createScaledBitmap(bitmap, WIDTH, height, true);
        if (result != bitmap) bitmap.recycle();
        return result;
    }

    private void checkFail() {
        if (++fail < MAX_FAIL) return;
        unavailable = true;
        releaseRetriever();
    }

    private void releaseRetriever() {
        prepared = false;
        if (retriever == null) return;
        try {
            retriever.release();
        } catch (Exception ignored) {
        }
        retriever = null;
    }

    public interface Callback {

        void onPreview(Bitmap bitmap);
    }
}
