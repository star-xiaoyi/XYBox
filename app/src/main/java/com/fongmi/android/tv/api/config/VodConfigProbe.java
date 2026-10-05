package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Reads configuration documents only. No database, preferences or active loader changes. */
public final class VodConfigProbe {
    private static final long TIMEOUT_MS = 20000;

    public static Result test(String input, String tag) throws Exception {
        long started = System.nanoTime();
        long deadline = started + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
        return read(normalize(input), tag, deadline, started, new HashSet<>(), "", 0);
    }

    public static String normalize(String value) {
        String url = value == null ? "" : value.trim();
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getRawAuthority() != null
                    && !uri.getRawAuthority().isEmpty()) return url;
            if ((scheme.equals("file") || scheme.equals("assets"))
                    && ((uri.getPath() != null && !uri.getPath().isEmpty()) || uri.getRawAuthority() != null)) return url;
        } catch (Exception ignored) { }
        throw new IllegalArgumentException("请输入完整的 http://、https://、file:// 或 assets:// 配置地址");
    }

    private static Result read(String url, String tag, long deadline, long started,
                               Set<String> visited, String name, int depth) throws Exception {
        if (depth >= 6 || !visited.add(url)) throw new IllegalArgumentException("地址合集循环引用或嵌套过多");
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0) throw new java.net.SocketTimeoutException("测试超时，请稍后重试");
        String json = Decoder.testJson(UrlUtil.convert(url), tag, remaining);
        JsonObject object = object(json);
        if (object.has("msg")) throw new IllegalArgumentException("接口提示：" + string(object, "msg"));
        if (object.has("urls")) {
            JsonElement entries = object.get("urls");
            if (!entries.isJsonArray()) throw new IllegalArgumentException("地址合集格式不正确");
            for (JsonElement entry : entries.getAsJsonArray()) {
                if (!entry.isJsonObject()) continue;
                JsonObject child = entry.getAsJsonObject();
                String target = string(child, "url");
                if (target.isEmpty()) continue;
                target = normalize(UrlUtil.resolve(url, target));
                return read(target, tag, deadline, started, visited, string(child, "name"), depth + 1);
            }
            throw new IllegalArgumentException("地址合集中没有配置地址");
        }
        int count = countSites(object);
        return new Result(url, object.toString(), name, count,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), depth > 0);
    }

    public static int countSites(String json) {
        return countSites(object(json));
    }

    private static JsonObject object(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element.isJsonObject()) return element.getAsJsonObject();
        } catch (Exception ignored) { }
        throw new IllegalArgumentException("返回内容不是点播配置，请检查地址是否指向网页或播放链接");
    }

    private static int countSites(JsonObject object) {
        checkArrays(object);
        for (int depth = 0; object.has("video"); depth++) {
            if (depth >= 6 || !object.get("video").isJsonObject()) throw new IllegalArgumentException("点播配置格式不正确");
            object = object.getAsJsonObject("video");
            checkArrays(object);
        }
        JsonElement sites = object.get("sites");
        if (sites == null || !sites.isJsonArray()) throw new IllegalArgumentException("配置中没有点播站点");
        Set<String> keys = new HashSet<>();
        for (JsonElement entry : sites.getAsJsonArray()) {
            if (!entry.isJsonObject()) continue;
            JsonObject site = entry.getAsJsonObject();
            String key = string(site, "key");
            if (key.isEmpty() || string(site, "api").isEmpty()) continue;
            keys.add(key);
        }
        if (keys.isEmpty()) throw new IllegalArgumentException("配置中没有可识别的点播站点");
        return keys.size();
    }

    private static void checkArrays(JsonObject object) {
        for (String key : new String[]{"sites", "parses", "rules", "doh", "headers", "flags", "hosts", "proxy", "ads"}) {
            if (object.has(key) && !object.get(key).isJsonArray()) throw new IllegalArgumentException("配置中的 " + key + " 列表格式不正确");
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString().trim() : "";
    }

    public static final class Result {
        public final String url;
        public final String json;
        public final String name;
        public final int siteCount;
        public final long elapsedMs;
        public final boolean fromCollection;

        private Result(String url, String json, String name, int siteCount, long elapsedMs, boolean fromCollection) {
            this.url = url;
            this.json = json;
            this.name = name;
            this.siteCount = siteCount;
            this.elapsedMs = elapsedMs;
            this.fromCollection = fromCollection;
        }
    }
}
