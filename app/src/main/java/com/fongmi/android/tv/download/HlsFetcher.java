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
    private static final int MAX_RETRY = 3;
    public static final String INDEX = "index.m3u8";

    private final Map<String, String> headers;
    private final Progress progress;
    private final File dir;

    private final List<Item> items = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final Map<String, Long> cursor = new HashMap<>();
    private int keyIndex;
    private int mapIndex;
    private double seconds;

    public HlsFetcher(Map<String, String> headers, File dir, Progress progress) {
        this.headers = headers == null ? new HashMap<>() : headers;
        this.progress = progress;
        this.dir = dir;
    }

    /** @return 可直接交给播放器的本地 index.m3u8 */
    public File download(String url) throws Exception {
        dir.mkdirs();
        Playlist playlist = fetchMedia(url, 0);
        parse(playlist.url, playlist.text);
        if (items.isEmpty()) throw new Exception("播放列表里没有分片");
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

    private void fetchItems() throws Exception {
        long done = 0;
        int index = 0;
        long[] window = {System.currentTimeMillis(), 0, 0}; // 窗口起点 / 窗口字节 / 当前速度
        long[] flight = new long[1];
        // 续传时先把已有分片点清楚、按真实进度报一次，
        // 否则界面会从 0% 重新爬一遍，看起来像是白下了
        for (Item item : items) {
            File target = new File(dir, item.name);
            if (!target.exists() || target.length() == 0) continue;
            done += target.length();
            ++index;
        }
        report(index, done, 0);
        done = 0;
        index = 0;
        for (Item item : items) {
            if (progress.isCancelled()) throw new Http.CancelException();
            File target = new File(dir, item.name);
            if (target.exists() && target.length() > 0) {
                // 已经在盘上的分片直接跳过，进度没变化就不必再报
                done += target.length();
                ++index;
                continue;
            }
            IOException last = null;
            final long base = done;
            final int current = index;
            for (int retry = 0; retry < MAX_RETRY; retry++) {
                if (progress.isCancelled()) throw new Http.CancelException();
                try {
                    flight[0] = 0;
                    Http.download(item.url, headers, target, item.range, bytes -> {
                        if (progress.isCancelled()) return false;
                        flight[0] += bytes;
                        tick(window, bytes);
                        report(current, base + flight[0], window[2]);
                        return true;
                    });
                    last = null;
                    break;
                } catch (Http.CancelException e) {
                    throw e;
                } catch (IOException e) {
                    last = e;
                    Logger.e(TAG, e);
                    sleep(500L * (retry + 1));
                }
            }
            // 分片失败必须让整个任务失败，不能吞掉——否则会拼出一个缺片的残缺视频还报成功
            if (last != null) throw new Exception("分片 " + (index + 1) + "/" + items.size() + " 下载失败：" + last.getMessage());
            done += target.length();
            report(++index, done, window[2]);
        }
    }

    /** 每秒结算一次速度，避免每读一块就算一次抖得没法看。 */
    private void tick(long[] window, long bytes) {
        window[1] += bytes;
        long now = System.currentTimeMillis();
        long elapsed = now - window[0];
        if (elapsed < 1000) return;
        window[2] = window[1] * 1000 / elapsed;
        window[0] = now;
        window[1] = 0;
    }

    private long lastReport;
    private int lastPercent;

    private void report(int index, long done, long speed) {
        long now = System.currentTimeMillis();
        if (now - lastReport < 400 && index < items.size()) return;
        lastReport = now;
        int percent = items.isEmpty() ? 0 : (int) (index * 100L / items.size());
        // 进度只许往前，任何时候都不该让用户看见百分比倒退
        lastPercent = percent = Math.max(percent, lastPercent);
        progress.onProgress(percent, done, 0, index, items.size(), speed);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
