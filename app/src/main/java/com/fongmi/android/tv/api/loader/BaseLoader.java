package com.fongmi.android.tv.api.loader;

import android.text.TextUtils;

import com.fongmi.android.tv.App;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import dalvik.system.DexClassLoader;

public class BaseLoader {

    private final JarLoader jarLoader;
    private final PyLoader pyLoader;
    private final JsLoader jsLoader;
    private final Map<String, Spider> localSites = new java.util.concurrent.ConcurrentHashMap<>();

    private static class Loader {
        static volatile BaseLoader INSTANCE = new BaseLoader();
    }

    public static BaseLoader get() {
        return Loader.INSTANCE;
    }

    private BaseLoader() {
        this.jarLoader = new JarLoader();
        this.pyLoader = new PyLoader();
        this.jsLoader = new JsLoader();
    }

    public void clear() {
        if (!App.isSourceProcess()) { localSites.clear(); this.jarLoader.clear(); SourceBridge.get().clear(); return; }
        localSites.clear();
        this.jarLoader.clear();
        this.pyLoader.clear();
        this.jsLoader.clear();
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        boolean js = api.contains(".js");
        boolean py = api.contains(".py");
        boolean csp = api.startsWith("csp_");
        if (!App.isSourceProcess()) {
            // 网盘站的插件要在播放页上弹二维码登录，隔离进程里没有页面给它用；按用户的决定，
            // 这类 JAR 插件保留主进程扫码兼容；自动选源仍排除网盘站点。
            if (csp && hostedInMain(key)) {
                Spider spider = jarLoader.getSpider(key, api, ext, jar);
                if (spider instanceof SpiderNull) throw new IllegalStateException("Source initialization failed: " + api, jarLoader.lastFailure());
                localSites.put(key, spider);
                return spider;
            }
            return js || py || csp ? new RemoteSpider(key, api, ext, jar) : new SpiderNull();
        }
        Spider spider;
        if (py) spider = pyLoader.getSpider(key, api, ext);
        else if (js) spider = jsLoader.getSpider(key, api, ext, jar);
        else if (csp) spider = jarLoader.getSpider(key, api, ext, jar);
        else spider = new SpiderNull();
        if (spider instanceof SpiderNull && (js || csp)) throw new IllegalStateException("Source initialization failed: " + api,
                js ? jsLoader.lastFailure() : jarLoader.lastFailure());
        if (!(spider instanceof SpiderNull)) localSites.put(key, spider);
        return spider;
    }

    /** 主进程托管的站点：名字、插件特征或运行期标记认出来的网盘站。 */
    private boolean hostedInMain(String key) {
        Site site = VodConfig.get().getSite(key);
        return !site.isEmpty() && site.isCloudDrive();
    }

    public Spider getSpider(Map<String, String> params) {
        if (!params.containsKey("siteKey")) return new SpiderNull();
        if (App.isSourceProcess()) return localSites.getOrDefault(params.get("siteKey"), new SpiderNull());
        Site site = VodConfig.get().getSite(params.get("siteKey"));
        if (!site.isEmpty()) return site.spider();
        return new SpiderNull();
    }

    public void setRecent(String key, String api, String jar) {
        if (!App.isSourceProcess()) {
            if (api.startsWith("csp_") && localSites.containsKey(key)) jarLoader.setRecent(Util.md5(jar));
            SourceBridge.get().setRecent(key, api, jar); return;
        }
        boolean js = api.contains(".js");
        boolean py = api.contains(".py");
        boolean csp = api.startsWith("csp_");
        if (js) jsLoader.setRecent(key);
        else if (py) pyLoader.setRecent(key);
        else if (csp) jarLoader.setRecent(Util.md5(jar));
    }

    public Object[] proxyLocal(Map<String, String> params) {
        if (!App.isSourceProcess()) {
            // Process ownership is independent of JAR ownership: cloud and ordinary sites can share a JAR.
            // The HTTP handler sets this from the URL path, not from user-supplied headers/query data.
            String owner = params.get(com.github.catvod.Proxy.OWNER), siteKey = params.get("siteKey");
            if (com.github.catvod.Proxy.MAIN.equals(owner) || !com.github.catvod.Proxy.SOURCES.equals(owner)
                    && siteKey != null && localSites.containsKey(siteKey)) return jarLoader.proxyInvoke(params);
            try { return SourceBridge.get().proxy(params); }
            catch (Exception error) { throw new IllegalStateException("Isolated source proxy failed", error); }
        }
        if ("js".equals(params.get("do"))) {
            return jsLoader.proxyInvoke(params);
        } else if ("py".equals(params.get("do"))) {
            return pyLoader.proxyInvoke(params);
        } else {
            return jarLoader.proxyInvoke(params);
        }
    }

    public void parseJar(String jar, boolean recent) {
        if (TextUtils.isEmpty(jar)) return;
        if (!App.isSourceProcess()) { SourceBridge.get().registerJar(jar, recent); return; }
        jarLoader.parseJar(Util.md5(jar), jar);
        if (recent) jarLoader.setRecent(Util.md5(jar));
    }

    public DexClassLoader dex(String jar) {
        if (!App.isSourceProcess()) throw new IllegalStateException("Source dex must remain in :sources");
        return jarLoader.dex(jar);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        return jsonExt("", key, jxs, url);
    }

    public JSONObject jsonExt(String jar, String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        if (!App.isSourceProcess()) return SourceBridge.get().parse(jar, "jsonExt",
                new JSONObject().put("key", key).put("parsers", parserEntries(jxs)).put("url", url));
        return jarLoader.jsonExt(jar, key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        return jsonExtMix("", flag, key, name, jxs, url);
    }

    public JSONObject jsonExtMix(String jar, String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        if (!App.isSourceProcess()) return SourceBridge.get().parse(jar, "jsonExtMix",
                new JSONObject().put("flag", flag).put("key", key).put("name", name).put("parsers", parserEntries(jxs)).put("url", url));
        return jarLoader.jsonExtMix(jar, flag, key, name, jxs, url);
    }

    private org.json.JSONArray parserEntries(Map<String, ?> values) throws Exception {
        org.json.JSONArray entries = new org.json.JSONArray();
        for (Map.Entry<String, ?> entry : values.entrySet())
            entries.put(new JSONObject().put("key", entry.getKey()).put("value", entry.getValue() instanceof Map ? new JSONObject((Map<?, ?>) entry.getValue()) : entry.getValue()));
        return entries;
    }
}
