package com.fongmi.android.tv.download;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Protocol;
import okhttp3.Response;

/**
 * 下载埋点，专门用来查"速度上不去 / 忽高忽低"。
 * <p>
 * 只看总速率永远分不清问题出在哪：一条请求的时间可能花在 DNS、TLS 握手、等首字节，
 * 也可能真的在传数据，这四种情况的解法完全不同。所以必须把各阶段拆开记。
 * <p>
 * 过滤日志：adb logcat -s XYDL:D
 */
final class DownloadLog {

    static final String TAG = "XYDL";
    /**
     * 排查开关。设成 final false，R8 会把所有调用点连同字符串拼接一起消掉，正式包零开销。
     * 下次再查下载速度时改成 true 重新打包即可：adb logcat -s XYDL:D
     */
    static final boolean ENABLED = false;

    private DownloadLog() {
    }

    static void d(String format, Object... args) {
        if (ENABLED) Log.d(TAG, String.format(Locale.US, format, args));
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0fK", bytes / 1024d);
        return String.format(Locale.US, "%.1fM", bytes / (1024d * 1024d));
    }

    static String rate(long bytes, long millis) {
        if (millis <= 0) return "-";
        return size(bytes * 1000 / millis) + "/s";
    }

    /**
     * 逐请求记录各阶段耗时。conn=new 说明这次重新建了连接——
     * 如果绝大多数都是 new，那就是连接池没复用上，光握手就把带宽吃掉了。
     */
    static class Probe extends EventListener {

        static final Factory FACTORY = call -> new Probe();

        private long start;
        private long dnsStart;
        private long connectStart;
        private long dnsMs;
        private long connectMs;
        private long ttfbMs;
        private long bytes;
        private int code;
        private boolean fresh;
        private String host = "";
        private String proto = "";

        @Override
        public void callStart(@NonNull Call call) {
            start = System.currentTimeMillis();
            host = call.request().url().host();
        }

        @Override
        public void dnsStart(@NonNull Call call, @NonNull String domainName) {
            dnsStart = System.currentTimeMillis();
        }

        @Override
        public void dnsEnd(@NonNull Call call, @NonNull String domainName, @NonNull List<InetAddress> list) {
            dnsMs = System.currentTimeMillis() - dnsStart;
        }

        @Override
        public void connectStart(@NonNull Call call, @NonNull InetSocketAddress address, @NonNull Proxy proxy) {
            connectStart = System.currentTimeMillis();
            fresh = true;
        }

        @Override
        public void connectEnd(@NonNull Call call, @NonNull InetSocketAddress address, @NonNull Proxy proxy, Protocol protocol) {
            connectMs = System.currentTimeMillis() - connectStart;
        }

        @Override
        public void connectionAcquired(@NonNull Call call, @NonNull Connection connection) {
            proto = connection.protocol().toString();
        }

        @Override
        public void responseHeadersEnd(@NonNull Call call, @NonNull Response response) {
            code = response.code();
            ttfbMs = System.currentTimeMillis() - start;
        }

        @Override
        public void responseBodyEnd(@NonNull Call call, long byteCount) {
            bytes = byteCount;
        }

        @Override
        public void callEnd(@NonNull Call call) {
            log(null);
        }

        @Override
        public void callFailed(@NonNull Call call, @NonNull IOException e) {
            log(e);
        }

        private void log(IOException e) {
            long total = System.currentTimeMillis() - start;
            DownloadLog.d("req %s code=%d conn=%s proto=%s dns=%dms tls=%dms ttfb=%dms body=%s total=%dms rate=%s%s",
                    host, code, fresh ? "new" : "reuse", proto, dnsMs, connectMs, ttfbMs,
                    size(bytes), total, rate(bytes, total - ttfbMs),
                    e == null ? "" : " err=" + e);
        }
    }
}
