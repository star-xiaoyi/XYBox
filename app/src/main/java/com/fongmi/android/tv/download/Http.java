package com.fongmi.android.tv.download;

import android.text.TextUtils;

import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/** 单个 HTTP 资源的下载，负责断点续传、限速统计和取消。 */
final class Http {

    private static final int BUFFER = 128 * 1024;
    private static volatile OkHttpClient client;

    private Http() {
    }

    /**
     * 下载专用的 client：并发拉分片时连接池要够大，否则每个分片都要重新握手，
     * 光 TLS 往返就把速度吃掉了。沿用 App 的 DoH / 代理设置。
     * <p>
     * 显式退回 HTTP/1.1 不是保守，而是这里必须这么做：源站普遍上了 h2，
     * 而 h2 会把所有并发请求复用到同一条 TCP 连接上——开 8 个线程实际只有一根管子，
     * 加线程只是把它切成更多流，带宽一点没多，每个流的首字节等待反而被拖长。
     * 下载要的恰恰是多条真实连接各占一份带宽。
     * <p>
     * 实测同一个源：握手那几秒 8 条独立连接并存时跑到 4.8M/s，
     * 一旦 ALPN 谈出 h2 合并成单连接就掉到 1.5M/s，而且那条一断 8 个分片一起阵亡。
     */
    static OkHttpClient client() {
        if (client != null) return client;
        synchronized (Http.class) {
            if (client == null) {
                OkHttpClient.Builder builder = OkHttp.client(TimeUnit.SECONDS.toMillis(30)).newBuilder()
                        .connectionPool(new ConnectionPool(64, 5, TimeUnit.MINUTES))
                        .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                        .retryOnConnectionFailure(true);
                if (DownloadLog.ENABLED) builder.eventListenerFactory(DownloadLog.Probe.FACTORY);
                client = builder.build();
            }
        }
        return client;
    }

    static Headers headers(Map<String, String> headers) {
        Headers.Builder builder = new Headers.Builder();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (TextUtils.isEmpty(entry.getKey()) || entry.getValue() == null) continue;
                builder.set(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    static String string(String url, Map<String, String> headers) throws IOException {
        Request request = new Request.Builder().url(url).headers(headers(headers)).build();
        try (Response response = client().newCall(request).execute()) {
            if (!response.isSuccessful()) throw new CodeException(response.code());
            return response.body().string();
        }
    }

    /**
     * 地址没有 .m3u8 后缀不代表不是 HLS，很多源是伪装路径，
     * 扒开头一小段看有没有 #EXTM3U 比只看后缀靠谱。
     */
    static boolean isPlaylist(String url, Map<String, String> headers) {
        Request request = new Request.Builder().url(url).headers(headers(headers)).header("Range", "bytes=0-1023").build();
        try (Response response = client().newCall(request).execute()) {
            if (!response.isSuccessful()) return false;
            String type = response.header("Content-Type");
            if (type != null && (type.contains("mpegurl") || type.contains("m3u"))) return true;
            return response.body().string().contains("#EXTM3U");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 下载到目标文件，支持 Range 续传。
     *
     * @param range   资源自带的字节区间（HLS BYTERANGE），为 null 表示整份下载
     * @param counter 每读到一块回调一次，用于全局进度/速度统计；返回 false 表示取消
     * @return 本次实际写入的字节数
     */
    static long download(String url, Map<String, String> headers, File target, long[] range, Counter counter) throws IOException {
        File part = new File(target.getAbsolutePath() + ".part");
        long from = part.exists() ? part.length() : 0;
        // 有 BYTERANGE 的分片长度已知，续传到长度就说明这块早就下完了
        if (range != null && from >= range[0]) {
            rename(part, target);
            return 0;
        }
        Request.Builder builder = new Request.Builder().url(url).headers(headers(headers));
        if (range != null) {
            long start = range[1] + from;
            long end = range[1] + range[0] - 1;
            builder.header("Range", "bytes=" + start + "-" + end);
        } else if (from > 0) {
            builder.header("Range", "bytes=" + from + "-");
        }
        Request request = builder.build();
        try (Response response = client().newCall(request).execute()) {
            int code = response.code();
            if (code != 200 && code != 206) throw new CodeException(code);
            // 服务端不认 Range 就整份重来，否则会把响应体接在半截文件后面拼成坏文件
            boolean append = from > 0 && code == 206;
            if (from > 0 && !append) from = 0;
            // BYTERANGE 分片遇到不支持 Range 的服务端：整份下来自己切出需要的那一段
            long skip = range != null && code == 200 ? range[1] : 0;
            long limit = range != null && code == 200 ? range[0] : -1;
            if (counter != null) counter.onTotal(total(response, from, append));
            if (part.getParentFile() != null) part.getParentFile().mkdirs();
            long written = 0;
            try (InputStream in = response.body().byteStream(); RandomAccessFile out = new RandomAccessFile(part, "rw")) {
                out.setLength(append ? from : 0);
                out.seek(append ? from : 0);
                byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (counter != null && !counter.onRead(read)) throw new CancelException();
                    int offset = 0;
                    int count = read;
                    if (skip > 0) {
                        int drop = (int) Math.min(skip, count);
                        skip -= drop;
                        offset += drop;
                        count -= drop;
                    }
                    if (limit >= 0) count = (int) Math.min(count, limit - written);
                    if (count <= 0) {
                        if (limit >= 0 && written >= limit) break;
                        continue;
                    }
                    out.write(buffer, offset, count);
                    written += count;
                    if (limit >= 0 && written >= limit) break;
                }
            }
            rename(part, target);
            return written;
        }
    }

    private static void rename(File part, File target) throws IOException {
        if (target.exists()) target.delete();
        if (!part.renameTo(target)) throw new IOException("重命名失败：" + target.getName());
    }

    /** 206 时 Content-Length 只是剩余部分，要把已续传的字节加回来才是总长度。 */
    private static long total(Response response, long from, boolean append) {
        long length = response.body() == null ? -1 : response.body().contentLength();
        if (length <= 0) return 0;
        return append ? from + length : length;
    }

    interface Counter {

        boolean onRead(int bytes);

        /** 响应头给出的资源总长度（已含续传掉的部分），拿不到时为 0。 */
        default void onTotal(long total) {
        }
    }

    private static final int MAX_RETRY = 5;
    private static final int MAX_RETRY_THROTTLED = 6;

    /**
     * 这次失败后该睡多久，返回负数表示别再试了。
     * <p>
     * 并发调高以后 429/403 会变成常态，那不是"源挂了"而是"你太快了"，
     * 判整集失败太粗暴——多给几次机会并指数退避，等源站放行。
     * <p>
     * 普通错误也不能只给三次：连接数越多，撞上 connection abort 这类瞬断的概率越高
     * （实测 8 条连接 6 次、16 条连接 12 次，全是瞬断），而它们重试一次基本就过。
     * 三次封顶会让一集在快下完时因为几次抖动前功尽弃。
     */
    static long backoff(IOException e, int attempt) {
        boolean throttled = e instanceof CodeException && ((CodeException) e).isThrottled();
        if (attempt + 1 >= (throttled ? MAX_RETRY_THROTTLED : MAX_RETRY)) return -1;
        return throttled ? Math.min(8000L, 1000L << attempt) : 500L * (attempt + 1);
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static class CancelException extends IOException {

        CancelException() {
            super("已取消");
        }
    }

    /** 带状态码的失败，调用方靠它区分"源站限流"和"真的挂了"，退避策略完全不同。 */
    static class CodeException extends IOException {

        private final int code;

        CodeException(int code) {
            super("HTTP " + code);
            this.code = code;
        }

        /**
         * 限流类响应：再快也没用，只能等。403 也算进来是因为不少源站
         * 并发超标时直接返回 403 而不是 429，用 429 的退避方式重试反而更容易活。
         */
        boolean isThrottled() {
            return code == 429 || code == 503 || code == 403;
        }
    }
}
