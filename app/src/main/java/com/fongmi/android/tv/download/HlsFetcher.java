package com.fongmi.android.tv.download;

import android.text.TextUtils;

import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

/**
 * HLS 缓存：分片原样落盘 + 改写成本地路径的 index.m3u8。
 * 不做转码合并，因此 AES-128 加密流、fMP4 分片、BYTERANGE 都能原样播放。
 */
public class HlsFetcher {

    private static final String TAG = "HlsFetcher";
    private static final Pattern ATTR = Pattern.compile("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)");
    private static final int MAX_REDIRECT = 3;
    /** 收尾阶段慢到这个地板以下就换连接。健康连接随便都有几百 K，压到这条线只可能是卡住了。 */
    private static final long STALL_FLOOR = 48 * 1024;
    /** 观察这么久才下判断，避免刚建连还没起速就被误杀。 */
    private static final long STALL_AFTER = 4000;
    /** 同一分片最多掐几次，掐够了就认命让它慢慢下完。 */
    private static final int MAX_STALL_ABORT = 3;
    public static final String INDEX = "index.m3u8";

    private final Map<String, String> headers;
    private final Progress progress;
    private final int threads;
    private final File dir;

    private final List<Item> items = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final Map<String, Long> cursor = new HashMap<>();
    private final AtomicInteger doneCount = new AtomicInteger();
    private final AtomicInteger inflight = new AtomicInteger();
    private final AtomicInteger retries = new AtomicInteger();
    private final AtomicInteger throttled = new AtomicInteger();
    private final AtomicLong doneBytes = new AtomicLong();
    private final AtomicLong window = new AtomicLong();
    /** 报进度用 tryLock 而不是 synchronized：抢不到就跳过这次，绝不让下载线程互相等。 */
    private final ReentrantLock reporting = new ReentrantLock();
    private long windowStart = System.currentTimeMillis();
    private long lastReport;
    private int lastPercent;
    private long speed;
    private int keyIndex;
    private int mapIndex;
    private double seconds;

    public HlsFetcher(Map<String, String> headers, File dir, int threads, Progress progress) {
        this.headers = headers == null ? new HashMap<>() : headers;
        this.threads = Math.max(1, threads);
        this.progress = progress;
        this.dir = dir;
    }

    /** @return 可直接交给播放器的本地 index.m3u8 */
    public File download(String url) throws Exception {
        dir.mkdirs();
        Playlist playlist = fetchMedia(url, 0);
        parse(playlist.url, playlist.text);
        if (items.isEmpty()) throw new Exception("播放列表里没有分片");
        // 非 http 的地址（skd:// 之类的 DRM 密钥、data:）根本下不了，
        // 早点说清楚，别等到发请求时抛个 IllegalArgumentException 把线程带走
        for (Item item : items) if (!item.url.startsWith("http")) throw new Exception("不支持的分片地址：" + item.url);
        writeIndex();
        fetchItems();
        return new File(dir, INDEX);
    }

    /** 主列表逐层下钻，直到拿到真正的媒体列表。 */
    private Playlist fetchMedia(String url, int depth) throws Exception {
        String text = Http.string(url, headers);
        if (TextUtils.isEmpty(text) || !text.contains("#EXTM3U")) throw new Exception("不是有效的 m3u8");
        if (!text.contains("#EXT-X-STREAM-INF")) return new Playlist(url, text);
        if (depth >= MAX_REDIRECT) throw new Exception("m3u8 嵌套过深");
        checkSplitAudio(text);
        String best = pickVariant(url, text);
        if (TextUtils.isEmpty(best)) throw new Exception("主列表里没有可用清晰度");
        return fetchMedia(best, depth + 1);
    }

    /**
     * 音视频分轨的流只抓视频那一路会得到一个没声音的文件，与其悄悄产出哑片，不如直接说不支持。
     */
    private void checkSplitAudio(String text) throws Exception {
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (!line.startsWith("#EXT-X-MEDIA")) continue;
            if (!"AUDIO".equalsIgnoreCase(attr(line, "TYPE"))) continue;
            if (!TextUtils.isEmpty(attr(line, "URI"))) throw new Exception("该源音视频分轨，暂不支持缓存");
        }
    }

    /** 主列表里挑码率最高的一路。 */
    private String pickVariant(String base, String text) {
        long bandwidth = -1;
        String picked = null;
        long pending = -1;
        boolean expect = false;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                String value = attr(line, "BANDWIDTH");
                pending = TextUtils.isEmpty(value) ? 0 : parseLong(value);
                expect = true;
            } else if (!line.startsWith("#") && expect) {
                if (pending > bandwidth) {
                    bandwidth = pending;
                    picked = resolve(base, line);
                }
                expect = false;
            }
        }
        return picked;
    }

    private void parse(String base, String text) throws Exception {
        boolean end = false;
        long[] pending = null;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-SESSION-KEY")) {
                lines.add(rewriteKey(base, line));
            } else if (line.startsWith("#EXT-X-MAP")) {
                lines.add(rewriteMap(base, line));
            } else if (line.startsWith("#EXT-X-BYTERANGE")) {
                // 分片切成独立文件后区间信息就没用了，不写进本地列表
                pending = parseRange(line.substring(line.indexOf(':') + 1), null);
            } else if (line.startsWith("#")) {
                if (line.startsWith("#EXT-X-ENDLIST")) end = true;
                if (line.startsWith("#EXTINF")) seconds += duration(line);
                lines.add(line);
            } else {
                String url = resolve(base, line);
                if (pending != null) pending = withOffset(url, pending);
                String name = String.format(Locale.US, "seg%05d%s", items.size(), extension(url));
                items.add(new Item(url, name, pending));
                lines.add(name);
                pending = null;
            }
        }
        if (!end) lines.add("#EXT-X-ENDLIST");
    }

    private String rewriteKey(String base, String line) {
        String uri = attr(line, "URI");
        if (TextUtils.isEmpty(uri)) return line;
        String name = "key" + (keyIndex++) + ".bin";
        items.add(new Item(resolve(base, uri), name, null));
        return line.replace("\"" + uri + "\"", "\"" + name + "\"");
    }

    private String rewriteMap(String base, String line) {
        String uri = attr(line, "URI");
        if (TextUtils.isEmpty(uri)) return line;
        String url = resolve(base, uri);
        String range = attr(line, "BYTERANGE");
        long[] bytes = TextUtils.isEmpty(range) ? null : withOffset(url, parseRange(range, null));
        String name = "init" + (mapIndex++) + extension(url);
        items.add(new Item(url, name, bytes));
        return "#EXT-X-MAP:URI=\"" + name + "\"";
    }

    private void writeIndex() {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append("\n");
        Path.write(new File(dir, INDEX), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 并发拉分片。串行下载时每片都要重新走一遍 DNS/TLS/首字节等待，
     * 单片才几百 KB，RTT 直接把带宽吃光——并发是这里唯一有意义的提速手段。
     */
    private void fetchItems() throws Exception {
        List<Item> todo = new ArrayList<>();
        for (Item item : items) {
            File target = new File(dir, item.name);
            // 续传时先把已有分片点清楚，进度才不会从 0% 重新爬一遍
            if (target.exists() && target.length() > 0) {
                doneCount.incrementAndGet();
                doneBytes.addAndGet(target.length());
            } else {
                todo.add(item);
            }
        }
        report(true);
        if (todo.isEmpty()) return;
        int workers = Math.min(threads, todo.size());
        DownloadLog.d("hls 开工 分片=%d 待下=%d 线程=%d", items.size(), todo.size(), workers);
        long began = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch latch = new CountDownLatch(todo.size());
        AtomicReference<Exception> error = new AtomicReference<>();
        for (Item item : todo) pool.execute(() -> fetchItem(item, error, latch));
        latch.await();
        pool.shutdownNow();
        DownloadLog.d("hls 收工 用时=%dms 下载=%s 均速=%s 重试=%d 限流=%d",
                System.currentTimeMillis() - began, DownloadLog.size(doneBytes.get()),
                DownloadLog.rate(doneBytes.get(), System.currentTimeMillis() - began), retries.get(), throttled.get());
        if (progress.isCancelled()) throw new Http.CancelException();
        if (error.get() != null) throw error.get();
        // 逐个对账。只看"有没有人报错"是不够的：线程可能因为取消标记中途翻回来、
        // 或者撞上非 IO 的意外而悄悄退场，latch 照样减到零。
        // 少一片就是花屏或直接播不了，宁可报失败也不能交一个残缺的视频出去
        for (Item item : items) {
            File target = new File(dir, item.name);
            if (!target.exists() || target.length() == 0) throw new Exception("分片缺失：" + item.name);
        }
    }

    private void fetchItem(Item item, AtomicReference<Exception> error, CountDownLatch latch) {
        try {
            if (progress.isCancelled() || error.get() != null) return;
            File target = new File(dir, item.name);
            IOException last = null;
            // 掐掉龟速连接的次数。掐够了就认命让它慢慢下完——
            // 真遇到源站给这一片就是这么慢的情况，反复重连只会更糟
            int aborts = 0;
            for (int attempt = 0; ; attempt++) {
                if (progress.isCancelled() || error.get() != null) return;
                boolean[] stalled = {false};
                long[] mark = {System.currentTimeMillis(), 0};
                boolean watch = aborts < MAX_STALL_ABORT;
                try {
                    inflight.incrementAndGet();
                    Http.download(item.url, headers, target, item.range, bytes -> {
                        if (progress.isCancelled() || error.get() != null) return false;
                        doneBytes.addAndGet(bytes);
                        window.addAndGet(bytes);
                        mark[1] += bytes;
                        if (watch && stalling(mark)) {
                            stalled[0] = true;
                            return false;
                        }
                        report(false);
                        return true;
                    });
                    last = null;
                    break;
                } catch (Http.CancelException e) {
                    if (!stalled[0]) return;
                    // 主动掐的，不是用户取消：换条连接重来，.part 会接着续，已下的字节不浪费
                    aborts++;
                    last = new IOException("连接龟速已重连");
                    retries.incrementAndGet();
                    DownloadLog.d("hls 龟速掐断 %s 已下=%s 第%d次", item.name, DownloadLog.size(mark[1]), aborts);
                    Http.sleep(200);
                } catch (IOException e) {
                    last = e;
                    Logger.e(TAG, e);
                    retries.incrementAndGet();
                    if (e instanceof Http.CodeException && ((Http.CodeException) e).isThrottled()) throttled.incrementAndGet();
                    long wait = Http.backoff(e, attempt);
                    DownloadLog.d("hls 分片失败 %s 第%d次 退避=%dms 原因=%s", item.name, attempt + 1, wait, e);
                    if (wait < 0) break;
                    Http.sleep(wait);
                } finally {
                    inflight.decrementAndGet();
                }
                // 掐断重连不该算进失败预算，否则尾巴上卡两下就把整集判死
                if (stalled[0]) attempt--;
            }
            // 分片失败必须让整个任务失败，不能吞掉——否则会拼出一个缺片的残缺视频还报成功
            if (last != null) error.compareAndSet(null, new Exception("分片下载失败：" + last.getMessage()));
            else {
                doneCount.incrementAndGet();
                report(true);
            }
        } catch (Throwable e) {
            // 分片地址不是 http 时 Request.Builder 抛的是 IllegalArgumentException，
            // 不接住的话线程直接死掉，还会顺着默认异常处理器把整个 App 带崩
            Logger.e(TAG, e);
            error.compareAndSet(null, e instanceof Exception ? (Exception) e : new Exception(e));
        } finally {
            latch.countDown();
        }
    }

    /**
     * 这条连接是不是在龟速滴水。
     * <p>
     * 只在收尾阶段管：剩的分片比线程还少时，队列已经空了，每个掉队的分片都直接顶在总耗时上，
     * 而且此时带宽是富余的，掐掉重连的代价很小。下载途中不管——那会儿每条连接分到的带宽本来就少，
     * 拿绝对速度去判会误伤一大片，反而把好好的连接掐得到处重连。
     * <p>
     * OkHttp 的读超时只在"完全没数据"时触发，几 KB/s 的滴水永远不超时，所以必须自己盯。
     */
    private boolean stalling(long[] mark) {
        if (items.size() - doneCount.get() > threads) return false;
        long span = System.currentTimeMillis() - mark[0];
        if (span < STALL_AFTER) return false;
        return mark[1] * 1000 / span < STALL_FLOOR;
    }

    /**
     * 多个线程都会往这里报数：每 400ms 出一次进度，每秒结算一次速度。
     * 抢不到锁说明已经有线程在报了，直接放弃这一次——报进度要给下载让路，
     * 用 synchronized 会让所有下载线程排队等写库，速度直接掉一截。
     */
    private void report(boolean force) {
        if (!reporting.tryLock()) return;
        try {
            long now = System.currentTimeMillis();
            if (!force && now - lastReport < 400) return;
            lastReport = now;
            long elapsed = now - windowStart;
            if (elapsed >= 1000) {
                speed = window.getAndSet(0) * 1000 / elapsed;
                windowStart = now;
                DownloadLog.d("hls 秒报 已下=%d/%d 在飞=%d/%d 速度=%s 重试=%d 限流=%d",
                        doneCount.get(), items.size(), inflight.get(), threads,
                        DownloadLog.size(speed) + "/s", retries.get(), throttled.get());
            }
            int done = doneCount.get();
            int percent = items.isEmpty() ? 0 : (int) (done * 100L / items.size());
            // 进度只许往前，任何时候都不该让用户看见百分比倒退
            lastPercent = percent = Math.max(percent, lastPercent);
            progress.onProgress(percent, doneBytes.get(), 0, done, items.size(), speed);
        } finally {
            reporting.unlock();
        }
    }

    /** 播放列表里所有 EXTINF 之和，秒。播本地 m3u8 时元数据读不出时长，靠它兜底。 */
    public long getDuration() {
        return (long) seconds;
    }

    private static double duration(String line) {
        try {
            String value = line.substring(line.indexOf(':') + 1);
            int comma = value.indexOf(',');
            return Double.parseDouble((comma == -1 ? value : value.substring(0, comma)).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** BYTERANGE 省略偏移时接着同一个资源上一段的末尾算。 */
    private long[] withOffset(String url, long[] range) {
        if (range == null) return null;
        if (range[1] < 0) range[1] = cursor.containsKey(url) ? cursor.get(url) : 0;
        cursor.put(url, range[1] + range[0]);
        return range;
    }

    private static long[] parseRange(String value, long[] def) {
        try {
            String text = value.trim();
            int at = text.indexOf('@');
            long length = Long.parseLong(at == -1 ? text : text.substring(0, at));
            long offset = at == -1 ? -1 : Long.parseLong(text.substring(at + 1));
            return new long[]{length, offset};
        } catch (Exception e) {
            return def;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String attr(String line, String key) {
        Matcher matcher = ATTR.matcher(line);
        while (matcher.find()) {
            if (!key.equalsIgnoreCase(matcher.group(1))) continue;
            String value = matcher.group(2);
            if (value == null) return "";
            return value.startsWith("\"") ? value.substring(1, value.length() - 1) : value;
        }
        return "";
    }

    private static String resolve(String base, String relative) {
        HttpUrl url = HttpUrl.parse(base);
        HttpUrl resolved = url == null ? null : url.resolve(relative);
        return resolved == null ? relative : resolved.toString();
    }

    private static String extension(String url) {
        try {
            HttpUrl parsed = HttpUrl.parse(url);
            String path = parsed == null ? url : parsed.encodedPath();
            int dot = path.lastIndexOf('.');
            if (dot == -1 || path.length() - dot > 6) return ".ts";
            return path.substring(dot);
        } catch (Exception e) {
            return ".ts";
        }
    }

    private static class Item {

        private final String url;
        private final String name;
        private final long[] range;

        Item(String url, String name, long[] range) {
            this.url = url;
            this.name = name;
            this.range = range;
        }
    }

    private static class Playlist {

        private final String url;
        private final String text;

        Playlist(String url, String text) {
            this.url = url;
            this.text = text;
        }
    }
}
