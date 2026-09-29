package com.fongmi.android.tv.search;

import android.os.SystemClock;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Source;
import com.github.catvod.net.OkHttp;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;

/**
 * 片源实测：取第一集的真实播放地址，m3u8 就顺着列表拉一段分片，按实际下载速度打分。
 * <p>
 * 只下载分片开头的一小段（最多 512KB、最多 4 秒），五个源加起来也就两三兆流量。
 * 需要解析、嗅探或者会被磁力/P2P 提取器接管的地址一律不测（SKIP），
 * 否则测速本身就会拉起下载引擎或者打开隐藏网页。
 */
public class SpeedTest {

    private static final ByteString M3U8 = ByteString.encodeUtf8("#EXTM3U");
    private static final long BUDGET = TimeUnit.SECONDS.toMillis(15);
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(6);
    private static final long READ_TIME = TimeUnit.SECONDS.toMillis(4);
    private static final int LIMIT = 512 * 1024;

    public interface Callback {

        void onTested(VodSource source);
    }

    private final List<Runnable> expires;
    private final ExecutorService executor;
    private final Callback callback;
    private boolean released;

    public SpeedTest(Callback callback) {
        this.executor = Executors.newFixedThreadPool(3);
        this.expires = new ArrayList<>();
        this.callback = callback;
    }

    /** 主线程调用。站点接口卡死时线程收不回来，由到点的 expire 把它记成超时。 */
    public void test(VodSource source) {
        if (released || source.getState() != VodSource.IDLE) return;
        // 网盘接口取详情或播放地址时可能弹登录二维码，自动测速不应产生交互。
        if (source.getSite().isCloudDrive()) {
            source.setResult(VodSource.SKIP, 0);
            callback.onTested(source);
            return;
        }
        source.setState(VodSource.TESTING);
        Runnable expire = () -> finish(source, VodSource.TIMEOUT, 0);
        expires.add(expire);
        App.post(expire, BUDGET);
        executor.execute(() -> {
            long[] result = run(source);
            App.post(() -> {
                App.removeCallbacks(expire);
                expires.remove(expire);
                finish(source, (int) result[0], result[1]);
            });
        });
    }

    private void finish(VodSource source, int state, long speed) {
        if (released || source.getState() != VodSource.TESTING) return;
        source.setResult(state, speed);
        callback.onTested(source);
    }

    public void release() {
        released = true;
        for (Runnable expire : expires) App.removeCallbacks(expire);
        expires.clear();
        executor.shutdownNow();
    }

    private static long[] run(VodSource source) {
        try {
            Site site = source.getSite();
            Vod vod = detail(site, source.getVodId());
            if (vod == null) return result(VodSource.FAIL, 0);
            boolean cloudLine = false;
            for (Flag flag : vod.getVodFlags()) {
                // 普通资源站也会混入网盘线路；在请求 playerContent 前跳过，避免爬虫弹登录二维码。
                if (flag.isCloudDrive()) {
                    cloudLine = true;
                    continue;
                }
                if (flag.getEpisodes().isEmpty()) continue;
                Episode episode = flag.getEpisodes().get(0);
                Result player = SiteViewModel.probePlayer(site, flag.getFlag(), episode.getUrl());
                String url = player.getUrl().v();
                if (!isDirect(player, url)) return result(VodSource.SKIP, 0);
                return probe(url, player.getHeaders());
            }
            return result(cloudLine ? VodSource.SKIP : VodSource.FAIL, 0);
        } catch (InterruptedIOException e) {
            return result(VodSource.TIMEOUT, 0);
        } catch (Throwable e) {
            return result(VodSource.FAIL, 0);
        }
    }

    private static Vod detail(Site site, String id) throws Exception {
        Result result = SiteViewModel.detail(site, id, false);
        return result.getList().isEmpty() ? null : result.getList().get(0);
    }

    private static boolean isDirect(Result player, String url) {
        if (TextUtils.isEmpty(url) || !url.startsWith("http")) return false;
        if (player.getParse() == 1 || player.getJx() == 1 || !player.getPlayUrl().isEmpty()) return false;
        return !Source.get().isExtract(url);
    }

    /** 最多跟两层：多码率主列表 → 子列表 → 分片。 */
    private static long[] probe(String url, Map<String, String> headers) throws Exception {
        OkHttpClient client = OkHttp.client(TIMEOUT);
        Headers header = Headers.of(headers);
        for (int depth = 0; depth < 3 && url != null; depth++) {
            long start = SystemClock.elapsedRealtime();
            Request request = new Request.Builder().url(url).headers(header).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return result(VodSource.FAIL, 0);
                BufferedSource body = response.body().source();
                if (!isPlaylist(response, body)) {
                    long speed = measure(body, start);
                    return speed > 0 ? result(VodSource.OK, speed) : result(VodSource.FAIL, 0);
                }
                url = pick(body.readUtf8(), response.request().url());
            }
        }
        return result(VodSource.FAIL, 0);
    }

    private static boolean isPlaylist(Response response, BufferedSource body) throws Exception {
        String type = response.header("Content-Type", "");
        if (type != null && type.toLowerCase().contains("mpegurl")) return true;
        BufferedSource peek = body.peek();
        // 容忍开头的 BOM 和空白
        for (int i = 0; i < 16 && peek.request(1); i++) {
            byte b = peek.getBuffer().getByte(0);
            if (b != (byte) 0xEF && b != (byte) 0xBB && b != (byte) 0xBF && b != ' ' && b != '\r' && b != '\n' && b != '\t') break;
            peek.skip(1);
        }
        return peek.rangeEquals(0, M3U8);
    }

    /** 片头常插几段广告，走的是另一个 CDN，取列表中间那段才代表正片的速度。 */
    private static String pick(String playlist, HttpUrl base) {
        List<String> segments = new ArrayList<>();
        boolean variant = false;
        for (String raw : playlist.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-STREAM-INF")) variant = true;
            if (line.startsWith("#")) continue;
            if (variant) return resolve(base, line);
            segments.add(line);
        }
        return segments.isEmpty() ? null : resolve(base, segments.get(segments.size() / 2));
    }

    private static String resolve(HttpUrl base, String path) {
        HttpUrl url = base.resolve(path);
        return url == null ? null : url.toString();
    }

    /** 从发出请求算起，含首包延迟在内的实际下载速度（字节/秒）。 */
    private static long measure(BufferedSource body, long start) throws Exception {
        Buffer buffer = new Buffer();
        long total = 0;
        while (total < LIMIT && SystemClock.elapsedRealtime() - start < READ_TIME) {
            long read = body.read(buffer, 16 * 1024);
            if (read == -1) break;
            total += read;
            buffer.clear();
        }
        long elapsed = Math.max(1, SystemClock.elapsedRealtime() - start);
        return total * 1000 / elapsed;
    }

    private static long[] result(int state, long speed) {
        return new long[]{state, speed};
    }
}
