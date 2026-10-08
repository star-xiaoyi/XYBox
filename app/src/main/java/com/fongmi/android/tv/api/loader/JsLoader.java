package com.fongmi.android.tv.api.loader;
import com.github.catvod.utils.Logger;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private String recent;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> revisions = new ConcurrentHashMap<>();
    private final ThreadLocal<Throwable> failure = new ThreadLocal<>();
    public Throwable lastFailure() { return failure.get(); }

    public JsLoader() {
        spiders = new ConcurrentHashMap<>();
    }

    public void clear() {
        for (Spider spider : spiders.values()) App.execute(spider::destroy);
        spiders.clear();
        revisions.clear();
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            return getSpiderLocked(key, api, ext, jar);
        }
    }

    private Spider getSpiderLocked(String key, String api, String ext, String jar) {
        failure.remove();
        try {
            String revision = com.github.catvod.utils.Util.md5(api + "\n" + ext + "\n" + jar);
            if (spiders.containsKey(key) && revision.equals(revisions.get(key))) return spiders.get(key);
            Spider spider = new com.fongmi.quickjs.crawler.Spider(key, api, BaseLoader.get().dex(jar));
            spider.init(App.get(), ext);
            Spider old = spiders.get(key);
            spiders.put(key, spider);
            revisions.put(key, revision);
            if (old != null) App.execute(old::destroy);
            return spider;
        } catch (Throwable e) {
            failure.set(e);
            Logger.e("Error", e);
            return new SpiderNull();
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            if (!params.containsKey("siteKey")) return spiders.get(recent).proxyLocal(params);
            return BaseLoader.get().getSpider(params).proxyLocal(params);
        } catch (Throwable e) {
            Logger.e("Error", e);
            return null;
        }
    }
}
