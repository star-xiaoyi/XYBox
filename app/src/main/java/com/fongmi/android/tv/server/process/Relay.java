package com.fongmi.android.tv.server.process;

import android.text.TextUtils;
import android.util.Base64;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.net.OkHttp;

import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 视频流中转。
 * <p>
 * 存在的理由有两个：一是投屏地址交给电视或浏览器去拉时，播放器解析出来的 Referer/UA 全丢了，
 * 需要鉴权头的源直接 403；二是浏览器拉外站的流会被跨域拦下。让流统一从手机转一道，两个问题
 * 一起解决——头由手机补上，地址对浏览器来说就是同源的。
 * <p>
 * HLS 要额外处理：m3u8 里套着一堆分片和密钥地址，它们同样需要头、同样跨域，所以播放列表必须
 * 拆开逐行改写，把每个地址也指回中转。子列表递归走同一条路径。
 */
public class Relay implements Process {

    /** 注册表只在投屏期间有意义，留几条够用，多了是内存垃圾。 */
    private static final int MAX = 8;

    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    private static final AtomicInteger SEQ = new AtomicInteger();

    private static class Item {

        private final String url;
        private final Headers headers;

        Item(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = Headers.of(headers);
        }
    }

    /**
     * 把带头的上游地址登记下来，换成一个不带头就能拉的本地地址。
     *
     * @return 形如 {@code http://手机IP:9978/relay/3} 的地址
     */
    public static synchronized String register(String url, Map<String, String> headers) {
        String token = String.valueOf(SEQ.incrementAndGet());
        ITEMS.put(token, new Item(url, headers == null ? new LinkedHashMap<>() : headers));
        while (ITEMS.size() > MAX) ITEMS.remove(ITEMS.keySet().iterator().next());
        return Server.get().getAddress() + "/relay/" + token;
    }

    public static synchronized void clear() {
        ITEMS.clear();
    }

    private static synchronized Item find(String token) {
        return ITEMS.get(token);
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String url) {
        return url.startsWith("/relay");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String url, Map<String, String> files) {
        try {
            // /relay/<token> 或 /relay/<token>/<base64 绝对地址>
            String path = url.substring("/relay".length());
            if (path.startsWith("/")) path = path.substring(1);
            if (path.isEmpty()) return Nano.error(NanoHTTPD.Response.Status.NOT_FOUND, "no token");
            int slash = path.indexOf('/');
            String token = slash < 0 ? path : path.substring(0, slash);
            Item item = find(token);
            if (item == null) return Nano.error(NanoHTTPD.Response.Status.NOT_FOUND, "expired");
            String target = slash < 0 ? item.url : decode(path.substring(slash + 1));
            if (TextUtils.isEmpty(target)) return Nano.error(NanoHTTPD.Response.Status.NOT_FOUND, "bad target");
            return fetch(session, token, item, target);
        } catch (Throwable e) {
            return Nano.error(e.getMessage() == null ? "relay failed" : e.getMessage());
        }
    }

    private NanoHTTPD.Response fetch(NanoHTTPD.IHTTPSession session, String token, Item item, String target) throws Exception {
        Request.Builder builder = new Request.Builder().url(target).headers(item.headers);
        // Range 不自己算，原样转给上游，再把上游的 206 原样带回来，拖进度就正常了
        String range = session.getHeaders().get("range");
        boolean playlist = isPlaylist(target);
        if (!TextUtils.isEmpty(range) && !playlist) builder.addHeader("Range", range);
        Response res = OkHttp.client().newCall(builder.build()).execute();
        // 上游可能重定向过，后续相对地址要按最终地址解析
        String base = res.request().url().toString();
        String mime = res.header("Content-Type", "");
        if (playlist || isPlaylist(base) || isPlaylistMime(mime)) {
            byte[] body = res.body().bytes();
            res.close();
            String text = new String(body, java.nio.charset.StandardCharsets.UTF_8);
            if (text.startsWith("#EXTM3U")) return playlist(rewrite(text, base, token));
            // 后缀骗人的情况：内容不是播放列表就当普通数据回去
            return cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, empty(mime) ? "application/octet-stream" : mime, new java.io.ByteArrayInputStream(body), body.length));
        }
        return stream(res, mime);
    }

    /** 透传非播放列表内容，保留状态码和 Range 相关响应头。 */
    private NanoHTTPD.Response stream(Response res, String mime) {
        NanoHTTPD.Response.Status status = NanoHTTPD.Response.Status.lookup(res.code());
        if (status == null) status = res.code() == 206 ? NanoHTTPD.Response.Status.PARTIAL_CONTENT : NanoHTTPD.Response.Status.OK;
        long length = res.body().contentLength();
        InputStream is = res.body().byteStream();
        NanoHTTPD.Response out = length >= 0
                ? NanoHTTPD.newFixedLengthResponse(status, empty(mime) ? "video/mp4" : mime, is, length)
                : NanoHTTPD.newChunkedResponse(status, empty(mime) ? "video/mp4" : mime, is);
        copy(res, out, "Content-Range");
        copy(res, out, "Accept-Ranges");
        if (empty(res.header("Accept-Ranges"))) out.addHeader("Accept-Ranges", "bytes");
        return cors(out);
    }

    private NanoHTTPD.Response playlist(String text) {
        NanoHTTPD.Response out = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/vnd.apple.mpegurl", text);
        return cors(out);
    }

    /**
     * 逐行改写播放列表：URI 行本身、以及 #EXT-X-KEY / #EXT-X-MAP / #EXT-X-MEDIA 里的
     * {@code URI="..."} 属性，都要先解析成绝对地址再指回中转。
     */
    private String rewrite(String text, String base, String token) {
        StringBuilder sb = new StringBuilder();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                sb.append('\n');
            } else if (line.startsWith("#")) {
                sb.append(rewriteAttr(line, base, token)).append('\n');
            } else {
                sb.append(proxy(line, base, token)).append('\n');
            }
        }
        return sb.toString();
    }

    private String rewriteAttr(String line, String base, String token) {
        int i = line.indexOf("URI=\"");
        if (i < 0) return line;
        int start = i + 5;
        int end = line.indexOf('"', start);
        if (end < 0) return line;
        String uri = line.substring(start, end);
        return line.substring(0, start) + proxy(uri, base, token) + line.substring(end);
    }

    private String proxy(String ref, String base, String token) {
        String abs = resolve(ref, base);
        if (abs == null) return ref;
        return Server.get().getAddress() + "/relay/" + token + "/" + encode(abs);
    }

    private String resolve(String ref, String base) {
        try {
            return new URL(new URL(base), ref).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isPlaylist(String url) {
        String path = url.toLowerCase();
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        return path.endsWith(".m3u8") || path.endsWith(".m3u");
    }

    private static boolean isPlaylistMime(String mime) {
        String m = mime == null ? "" : mime.toLowerCase();
        return m.contains("mpegurl") || m.contains("m3u8");
    }

    private static boolean empty(String s) {
        return s == null || s.isEmpty();
    }

    private static void copy(Response from, NanoHTTPD.Response to, String name) {
        String value = from.header(name);
        if (!empty(value)) to.addHeader(name, value);
    }

    private static NanoHTTPD.Response cors(NanoHTTPD.Response res) {
        res.addHeader("Access-Control-Allow-Origin", "*");
        return res;
    }

    private static String encode(String s) {
        return Base64.encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String decode(String s) {
        try {
            return new String(Base64.decode(s, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
