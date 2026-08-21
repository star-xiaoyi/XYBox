package com.fongmi.android.tv.download;

import android.text.TextUtils;

import com.github.catvod.utils.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 直链视频缓存。服务端支持 Range 时开多个连接分段并发拉，
 * 单连接经常被服务端限速，分段是把带宽吃满最直接的办法；不支持 Range 就退回单连接。
 * <p>
 * 分段不是一开始切完就不管了：先跑完的线程会去抢别人剩下的后半段接着下。
 * 各条连接的实际速度差得很远，固定切分的结局是收尾阶段只剩最慢的一条在爬，
 * 前面几条早就闲着——抢活是把那段尾巴摊平的唯一办法，主流下载器都这么干。
 */
public class FileFetcher {

    private static final String TAG = "FileFetcher";
    /** 小文件分段没意义，握手开销比省下来的时间还多。 */
    private static final long MIN_SPLIT = 8L * 1024 * 1024;
    /** 剩这么点就不值得再抢了，新建连接的代价比省下的时间还大。 */
    private static final long MIN_STEAL = 4L * 1024 * 1024;
    /** 单段总共能试多少次。有进展就重置退避次数，靠这个兜底防止原地打转。 */
    private static final int MAX_ATTEMPT = 50;
    private static final int BUFFER = 128 * 1024;

    private final Map<String, String> headers;
    private final Progress progress;
    private final int threads;
    private final File dir;

    private final AtomicLong doneBytes = new AtomicLong();
    private final AtomicLong window = new AtomicLong();
    private final java.util.concurrent.atomic.AtomicInteger inflight = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger retries = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger throttled = new java.util.concurrent.atomic.AtomicInteger();
    /** 抢活和读写分段表共用一把锁，只有一把就不用操心加锁顺序。 */
    private final ReentrantLock chunkLock = new ReentrantLock();
    /** 报进度用 tryLock：抢不到就跳过这次，绝不让下载线程互相等。 */
    private final ReentrantLock reporting = new ReentrantLock();
    private final List<Chunk> chunks = new ArrayList<>();
    private final List<Chunk> pending = new ArrayList<>();
    private long windowStart = System.currentTimeMillis();
    private long lastReport;
    private long lastFlush;
    private long total;
    private long speed;
    /** ETag 或 Last-Modified，用来确认几条连接拿到的是同一份文件。 */
    private String validator;
    private File meta;

    public FileFetcher(Map<String, String> headers, File dir, int threads, Progress progress) {
        this.headers = headers == null ? new HashMap<>() : headers;
        this.threads = Math.max(1, threads);
        this.progress = progress;
        this.dir = dir;
    }

    public File download(String url) throws Exception {
        dir.mkdirs();
        File target = new File(dir, "video" + extension(url));
        long[] probe = probe(url);
        total = probe[0];
        boolean split = probe[1] == 1 && total >= MIN_SPLIT && threads > 1;
        DownloadLog.d("file 开工 大小=%s 支持Range=%b 模式=%s 线程=%d 标识=%s",
                DownloadLog.size(total), probe[1] == 1, split ? "分段" : "单连接", threads, validator);
        long began = System.currentTimeMillis();
        if (split) parallel(url, target);
        else single(url, target);
        DownloadLog.d("file 收工 用时=%dms 均速=%s 分段=%d 重试=%d 限流=%d",
                System.currentTimeMillis() - began, DownloadLog.rate(doneBytes.get(), System.currentTimeMillis() - began),
                chunks.size(), retries.get(), throttled.get());
        if (!target.exists() || target.length() == 0) throw new Exception("下载结果为空文件");
        progress.onProgress(100, target.length(), target.length(), 0, 0, 0);
        return target;
    }

    /**
     * 单连接：服务端不认 Range，或者文件本来就小。
     * <p>
     * 这条路把 .part 的长度直接当成"已下好的字节数"，而分段那条路的 .part 是预分配满长度、
     * 中间全是洞的——两者绝不能共用同一个文件，否则续传会从文件尾巴接着下，
     * 最后把一个大半是零的稀疏文件当成完整视频交出去。
     */
    private void single(String url, File target) throws Exception {
        clear(new File(target.getAbsolutePath() + ".mpart"), new File(target.getAbsolutePath() + ".meta"));
        File part = new File(target.getAbsolutePath() + ".part");
        doneBytes.set(part.exists() ? part.length() : 0);
        IOException last = null;
        for (int attempt = 0; ; attempt++) {
            if (progress.isCancelled()) throw new Http.CancelException();
            try {
                Http.download(url, headers, target, null, new Http.Counter() {
                    @Override
                    public boolean onRead(int bytes) {
                        if (progress.isCancelled()) return false;
                        doneBytes.addAndGet(bytes);
                        window.addAndGet(bytes);
                        report(false);
                        return true;
                    }

                    @Override
                    public void onTotal(long value) {
                        if (value > 0) total = value;
                    }
                });
                last = null;
                break;
            } catch (Http.CancelException e) {
                throw e;
            } catch (IOException e) {
                last = e;
                Logger.e(TAG, e);
                long wait = Http.backoff(e, attempt);
                if (wait < 0) break;
                Http.sleep(wait);
            }
        }
        if (last != null) throw new Exception("下载失败：" + last.getMessage());
    }

    /** 多连接分段：整个文件预分配好，每段各写各的区间，进度记在 .meta 里以便续传。 */
    private void parallel(String url, File target) throws Exception {
        clear(new File(target.getAbsolutePath() + ".part"));
        File part = new File(target.getAbsolutePath() + ".mpart");
        meta = new File(target.getAbsolutePath() + ".meta");
        chunks.addAll(restore(part));
        try (RandomAccessFile raf = new RandomAccessFile(part, "rw")) {
            raf.setLength(total);
        }
        for (Chunk chunk : chunks) {
            doneBytes.addAndGet(chunk.done);
            if (!chunk.isDone()) pending.add(chunk);
        }
        report(true);
        if (!pending.isEmpty()) {
            // 线程数按设置来，不按现有段数：续传回来的段可能比现在的设置少，
            // 多出来的线程会去抢活自己切段，抢不到的当场退出，不会白占着
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicReference<Exception> error = new AtomicReference<>();
            for (int i = 0; i < threads; i++) pool.execute(() -> work(url, part, error, latch));
            latch.await();
            pool.shutdownNow();
            if (progress.isCancelled()) throw new Http.CancelException();
            if (error.get() != null) throw error.get();
        }
        for (Chunk chunk : chunks) if (!chunk.isDone()) throw new Exception("分段下载不完整");
        flush(true);
        clear(meta);
        if (target.exists()) target.delete();
        if (!part.renameTo(target)) throw new Exception("重命名失败：" + target.getName());
    }

    /** 一个线程一辈子：领一段、下完、再领下一段，领不到活就退出。 */
    private void work(String url, File part, AtomicReference<Exception> error, CountDownLatch latch) {
        try {
            Chunk chunk;
            while ((chunk = next()) != null) {
                if (progress.isCancelled() || error.get() != null) return;
                if (!fetchChunk(url, part, chunk, error)) return;
            }
        } catch (Throwable e) {
            // 非 IO 的意外也必须记下来，不然 latch 照样减到零，残缺的文件会被当成下载成功
            Logger.e(TAG, e);
            error.compareAndSet(null, e instanceof Exception ? (Exception) e : new Exception(e));
        } finally {
            latch.countDown();
        }
    }

    /** 先领没人做的，都领完了就去抢剩得最多的那段。 */
    private Chunk next() {
        chunkLock.lock();
        try {
            while (!pending.isEmpty()) {
                Chunk chunk = pending.remove(0);
                if (!chunk.isDone()) return chunk;
            }
            return steal();
        } finally {
            chunkLock.unlock();
        }
    }

    /**
     * 把剩得最多那段的后一半切出来自己下。
     * <p>
     * 受害者读到的 done 可能比这里看到的略新，于是它已经写过了切点后的一点点数据，
     * 抢到手的线程会把那几十 KB 重下一遍——同样的字节写回同样的位置，文件是对的，
     * 代价只是进度里多算一个缓冲区，比为此加一把锁拖慢整条下载路径划算。
     */
    private Chunk steal() {
        Chunk victim = null;
        long most = 0;
        for (Chunk chunk : chunks) {
            long left = chunk.remaining();
            if (left <= most) continue;
            most = left;
            victim = chunk;
        }
        if (victim == null || most < MIN_STEAL) return null;
        long end = victim.end;
        long mid = victim.start + victim.done + most / 2;
        victim.end = mid - 1;
        Chunk chunk = new Chunk(mid, end);
        // 插在受害者后面而不是丢到末尾：.meta 是按顺序校验首尾相接的，乱序会让下次续传整个作废
        chunks.add(chunks.indexOf(victim) + 1, chunk);
        return chunk;
    }

    /** @return false 表示整个任务该停了（取消或已有别的段失败） */
    private boolean fetchChunk(String url, File part, Chunk chunk, AtomicReference<Exception> error) {
        IOException last = null;
        int attempt = 0;
        for (int round = 0; round < MAX_ATTEMPT; round++) {
            if (progress.isCancelled() || error.get() != null) return false;
            long before = chunk.done;
            try {
                readChunk(url, part, chunk);
                if (chunk.isDone()) return true;
                // 流干净地断在半路：OkHttp 不报错，但这段确实没下完。
                // 不当失败处理的话这段就没人认领了，最后整集判"分段下载不完整"白下一场
                last = new IOException("连接提前结束");
            } catch (Http.CancelException e) {
                return false;
            } catch (IOException e) {
                last = e;
                Logger.e(TAG, e);
                retries.incrementAndGet();
                if (e instanceof Http.CodeException && ((Http.CodeException) e).isThrottled()) throttled.incrementAndGet();
            }
            // 这轮真下到了东西就把退避次数清零：一段几百 MB，中途撞上一次网络抖动很正常，
            // 按累计次数封顶会让整集在最后关头前功尽弃
            if (chunk.done > before) {
                DownloadLog.d("file 分段中断但有进展 %d..%d 已下=%s 重试重新计数", chunk.start, chunk.end, DownloadLog.size(chunk.done));
                attempt = 0;
                Http.sleep(500);
                continue;
            }
            long wait = Http.backoff(last, attempt++);
            DownloadLog.d("file 分段失败 %d..%d 第%d次 退避=%dms 原因=%s", chunk.start, chunk.end, attempt, wait, last);
            if (wait < 0) break;
            Http.sleep(wait);
        }
        error.compareAndSet(null, new Exception("分段下载失败：" + last.getMessage()));
        return false;
    }

    private void readChunk(String url, File part, Chunk chunk) throws IOException {
        long from = chunk.start + chunk.done;
        if (from > chunk.end) return;
        Request.Builder builder = new Request.Builder().url(url).headers(Http.headers(headers)).header("Range", "bytes=" + from + "-" + chunk.end);
        // 每条连接都各自解析域名、各自跟跳转，可能落到不同后端。If-Range 让服务端在
        // 文件对不上时退回 200，而我们只认 206，就不会把两份不同的片源拼成一个文件
        if (!TextUtils.isEmpty(validator)) builder.header("If-Range", validator);
        inflight.incrementAndGet();
        try (Response response = Http.client().newCall(builder.build()).execute()) {
            if (response.code() != 206) throw new Http.CodeException(response.code());
            // 服务端不支持 If-Range 时再用总长度兜一道：长度都不一样肯定不是同一份
            long length = length(response);
            if (length > 0 && length != total) throw new IOException("文件在下载途中变了");
            try (InputStream in = response.body().byteStream(); RandomAccessFile raf = new RandomAccessFile(part, "rw")) {
                raf.seek(from);
                byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (progress.isCancelled()) throw new Http.CancelException();
                    // 每轮都重算剩余量：这一段随时可能被别的线程抢走后半截
                    int count = (int) Math.min(read, chunk.remaining());
                    if (count <= 0) break;
                    raf.write(buffer, 0, count);
                    chunk.done += count;
                    doneBytes.addAndGet(count);
                    window.addAndGet(count);
                    report(false);
                    flush(false);
                    if (chunk.isDone()) break;
                }
            }
        } finally {
            inflight.decrementAndGet();
        }
    }

    /** 读回上次的分段进度；段拼不满整个文件（被清过、长度变了、格式对不上）就从头来。 */
    private List<Chunk> restore(File part) {
        if (!part.exists() || part.length() != total || meta == null || !meta.exists()) return split();
        try {
            List<Chunk> items = new ArrayList<>();
            long covered = 0;
            for (String raw : new String(read(meta), StandardCharsets.UTF_8).split("\n")) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) {
                    // 上次记下的文件标识，对不上说明源站换了片源，接着下会拼出一个花屏文件
                    if (!TextUtils.isEmpty(validator) && !line.substring(1).equals(validator)) return split();
                    continue;
                }
                String[] values = line.split(",");
                if (values.length != 3) return split();
                long start = Long.parseLong(values[0]);
                long end = Long.parseLong(values[1]);
                long done = Long.parseLong(values[2]);
                // 段必须从 0 开始首尾相接铺满整个文件，缺一块就等于文件里有个洞
                if (start != covered || end < start || end >= total) return split();
                if (done < 0 || done > end - start + 1) return split();
                Chunk chunk = new Chunk(start, end);
                chunk.done = done;
                items.add(chunk);
                covered = end + 1;
            }
            return covered == total && !items.isEmpty() ? items : split();
        } catch (Exception e) {
            return split();
        }
    }

    private List<Chunk> split() {
        List<Chunk> items = new ArrayList<>();
        long size = total / threads;
        for (int i = 0; i < threads; i++) {
            long start = i * size;
            long end = i == threads - 1 ? total - 1 : start + size - 1;
            items.add(new Chunk(start, end));
        }
        return items;
    }

    private void flush(boolean force) {
        if (meta == null) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastFlush < 1000) return;
        // 收尾那次必须落盘，中途抢不到锁就跳过——记进度不值得让下载线程排队
        if (force) chunkLock.lock();
        else if (!chunkLock.tryLock()) return;
        String text;
        try {
            if (!force && now - lastFlush < 1000) return;
            lastFlush = now;
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(validator)) sb.append('#').append(validator).append('\n');
            // done 可能比段长多出一个缓冲区（被抢活的线程收窄 end 之前刚好又写了一轮），
            // 落盘前夹回去，否则下次续传会因为"下的比段还长"判定文件不可信而整个重下
            for (Chunk chunk : chunks) sb.append(chunk.start).append(',').append(chunk.end).append(',').append(Math.min(chunk.done, chunk.length())).append('\n');
            text = sb.toString();
        } finally {
            chunkLock.unlock();
        }
        write(text);
    }

    /**
     * 写分段表。刻意不走 Path.write：那条路每次都 fork 一个 chmod 进程再 waitFor，
     * 这里一秒一次、每集一份，光起进程就能把下载线程拖住。
     * 先写临时文件再改名，进程被杀也不会留下一个空表让续传作废。
     */
    private void write(String text) {
        File temp = new File(meta.getAbsolutePath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Logger.e(TAG, e);
            return;
        }
        if (meta.exists()) meta.delete();
        if (!temp.renameTo(meta)) temp.delete();
    }

    private static byte[] read(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int read = 0;
            while (read < data.length) {
                int count = in.read(data, read, data.length - read);
                if (count == -1) break;
                read += count;
            }
        }
        return data;
    }

    private static void clear(File... files) {
        for (File file : files) if (file != null && file.exists()) file.delete();
    }

    /**
     * 探一下总长度和是否支持 Range：只要一个字节，代价很小，
     * 有的服务端不认 HEAD，所以用带 Range 的 GET 探。顺手记下文件标识给 If-Range 用。
     * <p>
     * 探测失败会退回单连接，而单连接和分段用的是两份互不相认的临时文件，
     * 一次网络抖动就把已经下了一大半的分段进度判死太亏，所以这里多试两次。
     */
    private long[] probe(String url) {
        for (int attempt = 0; ; attempt++) {
            if (progress.isCancelled()) return new long[]{0, 0};
            long[] result = probeOnce(url);
            if (result[1] == 1 || attempt >= 2) return result;
            Http.sleep(500L * (attempt + 1));
        }
    }

    private long[] probeOnce(String url) {
        Request request = new Request.Builder().url(url).headers(Http.headers(headers)).header("Range", "bytes=0-0").build();
        try (Response response = Http.client().newCall(request).execute()) {
            if (response.code() == 206) {
                validator = response.header("ETag");
                if (TextUtils.isEmpty(validator)) validator = response.header("Last-Modified");
                long length = length(response);
                return new long[]{Math.max(0, length), length > 0 ? 1 : 0};
            }
            long length = response.body() == null ? 0 : response.body().contentLength();
            return new long[]{Math.max(0, length), 0};
        } catch (Exception e) {
            return new long[]{0, 0};
        }
    }

    /** Content-Range 斜杠后面那截是资源总长度，拿不到就返回 0。 */
    private static long length(Response response) {
        String range = response.header("Content-Range");
        if (range == null) return 0;
        int slash = range.indexOf('/');
        if (slash == -1) return 0;
        try {
            return Long.parseLong(range.substring(slash + 1).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 每 400ms 出一次进度，每秒结算一次速度。
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
                DownloadLog.d("file 秒报 已下=%s/%s 分段=%d 在飞=%d/%d 速度=%s 重试=%d 限流=%d",
                        DownloadLog.size(doneBytes.get()), DownloadLog.size(total), chunks.size(),
                        inflight.get(), threads, DownloadLog.size(speed) + "/s", retries.get(), throttled.get());
            }
            long done = doneBytes.get();
            int percent = total > 0 ? (int) Math.min(99, done * 100 / total) : 0;
            progress.onProgress(percent, done, total, 0, 0, speed);
        } finally {
            reporting.unlock();
        }
    }

    private static String extension(String url) {
        try {
            HttpUrl parsed = HttpUrl.parse(url);
            String path = parsed == null ? url : parsed.encodedPath();
            int dot = path.lastIndexOf('.');
            if (dot == -1 || path.length() - dot > 6) return ".mp4";
            String ext = path.substring(dot).toLowerCase();
            return ext.matches("\\.(mp4|mkv|avi|mov|wmv|flv|webm|ts|m4v|mp3|m4a)") ? ext : ".mp4";
        } catch (Exception e) {
            return ".mp4";
        }
    }

    private static class Chunk {

        private final long start;
        /** 会被抢活的线程改小（只往前收，绝不越过已经写下去的位置）。 */
        private volatile long end;
        private volatile long done;

        Chunk(long start, long end) {
            this.start = start;
            this.end = end;
        }

        long remaining() {
            return end - (start + done) + 1;
        }

        long length() {
            return end - start + 1;
        }

        boolean isDone() {
            return remaining() <= 0;
        }
    }
}
