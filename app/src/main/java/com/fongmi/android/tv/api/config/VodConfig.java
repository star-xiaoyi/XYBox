package com.fongmi.android.tv.api.config;

import android.text.TextUtils;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.search.SourceIdentity;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** All enabled documents form one catalog; resolver settings remain scoped to their document. */
public class VodConfig {
    private static class Loader { static final VodConfig INSTANCE = new VodConfig(); }
    private final AtomicInteger generation = new AtomicInteger();
    private volatile List<Site> sites = Collections.emptyList();
    private volatile Map<String, Context> contexts = Collections.emptyMap();
    private volatile List<Doh> doh = Collections.emptyList();
    private volatile List<Rule> rules = Collections.emptyList();
    private volatile List<String> ads = Collections.emptyList();
    private volatile Config config;
    private volatile Site home;
    private volatile Context playback;
    private volatile boolean isLoading, retryAfterNetwork;

    private static final class Context {
        final Config config;
        final List<Site> sites = new ArrayList<>();
        final List<Parse> parses = new ArrayList<>();
        final List<String> flags;
        final List<Rule> rules;
        final List<Doh> doh;
        final List<String> ads;
        final List<JsonElement> headers;
        final List<String> hosts, proxy;
        Parse parse;
        Context(Config config, JsonObject document) {
            this.config = config;
            JsonObject object = document;
            for (int depth = 0; depth < 6 && object.has("video") && object.get("video").isJsonObject(); depth++) object = object.getAsJsonObject("video");
            String jar = Json.safeString(object, "spider");
            for (JsonElement element : Json.safeListElement(object, "sites")) {
                Site site = Site.objectFrom(element);
                if (site.getKey().isEmpty() || site.getApi().isEmpty()) continue;
                site.setKey(SourceIdentity.key(config, site.getKey()));
                site.setSourceId(config.getId());
                site.setSourceName(config.getDesc());
                site.setApi(UrlUtil.convert(site.getApi()));
                site.setExt(UrlUtil.convert(site.getExt()));
                site.setJar(site.getJar().isEmpty() ? jar : site.getJar());
                if (!sites.contains(site)) sites.add(site.trans().sync());
            }
            for (JsonElement element : Json.safeListElement(object, "parses")) {
                Parse item = Parse.objectFrom(element);
                item.setSourceJar(jar);
                if (!parses.contains(item)) parses.add(item);
                if (item.getName().equals(config.getParse())) parse = item;
            }
            if (!parses.isEmpty()) {
                Parse god = Parse.god(); god.setSourceJar(jar); parses.add(0, god);
            }
            if (parse == null) parse = parses.isEmpty() ? new Parse() : parses.get(0);
            for (Parse item : parses) item.setActivated(parse);
            flags = Json.safeListString(object, "flags");
            rules = Rule.arrayFrom(object.getAsJsonArray("rules"));
            doh = Doh.arrayFrom(object.getAsJsonArray("doh"));
            ads = Json.safeListString(object, "ads");
            headers = Json.safeListElement(object, "headers");
            hosts = Json.safeListString(object, "hosts");
            proxy = Json.safeListString(object, "proxy");
        }
    }

    private VodConfig() { }
    public static VodConfig get() { return Loader.INSTANCE; }
    public static int getCid() { return get().getConfig().getId(); }
    public static String getUrl() { return get().getConfig().getUrl(); }
    public static String getDesc() { return get().getConfig().getDesc(); }
    public static int getHomeIndex() { return get().getSites().indexOf(get().getHome()); }
    public static boolean hasParse() { return !get().getParses().isEmpty(); }
    public static boolean isEnabled(Config value) { return !Prefers.getBoolean("vod_disabled_" + SourceIdentity.prefix(value), false); }
    public static void setEnabled(Config value, boolean enabled) {
        Prefers.put("vod_disabled_" + SourceIdentity.prefix(value), !enabled);
    }
    public static boolean isSiteEnabled(Site site) { return !Prefers.getBoolean("vod_site_disabled_" + site.getKey(), false); }
    public static void setSiteEnabled(Site site, boolean enabled) { Prefers.put("vod_site_disabled_" + site.getKey(), !enabled); }
    /** A pure list for the editor: disabled stations remain editable, without loading their plugins. */
    public static List<Site> configuredSites(Config config) {
        List<Site> result = new ArrayList<>();
        try {
            JsonObject object = Json.parse(config.getJson()).getAsJsonObject();
            for (int depth = 0; depth < 6 && object.has("video") && object.get("video").isJsonObject(); depth++) object = object.getAsJsonObject("video");
            for (JsonElement element : Json.safeListElement(object, "sites")) {
                Site site = Site.objectFrom(element);
                if (site.getKey().isEmpty() || site.getApi().isEmpty()) continue;
                site.setKey(SourceIdentity.key(config, site.getKey()));
                site.setSourceId(config.getId()); site.setSourceName(config.getDesc());
                if (!result.contains(site)) result.add(site);
            }
        } catch (Exception ignored) { }
        return result;
    }
    public static void load(Config value, Callback callback) { get().config(value).load(callback); }
    public static void loadValidated(Config value, String json, Callback callback) {
        try { VodConfigProbe.countSites(json); value.json(json).save(); get().load(callback); }
        catch (Exception error) { App.post(() -> callback.error(error.getMessage())); }
    }
    public VodConfig init() { config = Config.vod(); return this; }
    public VodConfig config(Config value) { config = value; return this; }
    public VodConfig clear() {
        generation.incrementAndGet(); sites = Collections.emptyList(); contexts = Collections.emptyMap();
        doh = Collections.emptyList(); rules = Collections.emptyList(); ads = Collections.emptyList();
        home = null; playback = null; isLoading = false; return this;
    }

    public void load(Callback callback) {
        final int token = generation.incrementAndGet();
        isLoading = true;
        App.execute(() -> {
            List<Config> enabled = new ArrayList<>();
            for (Config item : Config.getAll(0)) if (!item.isEmpty() && isEnabled(item)) enabled.add(item);
            Config preferred = getConfig();
            if (!preferred.isEmpty() && isEnabled(preferred) && !enabled.contains(preferred)) enabled.add(0, preferred);
            Map<String, Context> loaded = new LinkedHashMap<>();
            List<Config> refresh = new ArrayList<>();
            for (Config item : enabled) {
                if (token != generation.get()) return;
                String json = item.getJson();
                try {
                    if (TextUtils.isEmpty(json)) {
                        VodConfigProbe.Result tested = VodConfigProbe.test(item.getUrl(), "vod-pool-" + token + "-" + item.getId());
                        // Address collections have no runtime site identity; resolve before saving.
                        item.url(tested.url).json(tested.json).save(); json = tested.json;
                    } else refresh.add(item);
                    loaded.put(SourceIdentity.prefix(item), decode(item, json));
                } catch (Throwable error) { Logger.e("VodPool: configuration unavailable " + item.getDesc(), error); }
            }
            App.post(() -> {
                if (token != generation.get()) return;
                install(loaded); isLoading = false;
                retryAfterNetwork = sites.isEmpty() && !enabled.isEmpty();
                if (sites.isEmpty() && !enabled.isEmpty()) callback.error("所有点播配置暂时不可用，请检测配置后重试");
                else callback.success();
                refreshDocuments(refresh, token);
            });
        });
    }

    private static Context decode(Config item, String json) {
        VodConfigProbe.countSites(json);
        return new Context(item, Json.parse(json).getAsJsonObject());
    }

    /** Cached documents become usable immediately; slow/dead addresses never block that path. */
    private void refreshDocuments(List<Config> values, int token) {
        if (values.isEmpty()) return;
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newFixedThreadPool(Math.min(3, values.size()));
        for (Config item : values) worker.execute(() -> {
            try {
                VodConfigProbe.Result result = VodConfigProbe.test(item.getUrl(), "vod-refresh-" + token + "-" + item.getId());
                if (token != generation.get() || !item.getUrl().equals(result.url) || item.getJson().equals(result.json)) return;
                Context updated = decode(item, result.json);
                App.post(() -> {
                    if (token != generation.get() || !isEnabled(item) || Config.find(item.getId()) == null) return;
                    item.json(result.json).save();
                    Map<String, Context> next = new LinkedHashMap<>(contexts);
                    next.put(SourceIdentity.prefix(item), updated);
                    install(next); RefreshEvent.config();
                });
            } catch (Throwable error) { Logger.w("VodPool: keeping cached configuration " + item.getDesc()); }
        });
        worker.shutdown();
    }

    private void install(Map<String, Context> values) {
        String oldHome = getHome().getKey();
        String oldPlayback = playback == null ? "" : SourceIdentity.prefix(playback.config);
        List<Site> combined = new ArrayList<>();
        List<Rule> allRules = new ArrayList<>(); List<String> allAds = new ArrayList<>();
        List<Doh> allDoh = new ArrayList<>(); List<JsonElement> allHeaders = new ArrayList<>();
        for (Context value : values.values()) {
            for (Site site : value.sites) if (isSiteEnabled(site)) combined.add(site);
            allRules.addAll(value.rules); allAds.addAll(value.ads);
            allDoh.addAll(value.doh); allHeaders.addAll(value.headers);
            setHosts(value.hosts); setProxy(value.proxy);
        }
        contexts = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        sites = Collections.unmodifiableList(combined); rules = allRules; ads = allAds; doh = allDoh;
        setHeaders(allHeaders);
        home = getSite(oldHome);
        if (home.isEmpty()) {
            for (Context value : values.values()) {
                Site saved = getSite(SourceIdentity.key(value.config, value.config.getHome()));
                if (!saved.isEmpty()) { home = saved; break; }
            }
        }
        if (home.isEmpty()) home = combined.isEmpty() ? new Site() : combined.get(0);
        playback = values.get(oldPlayback);
        if (playback == null) playback = context(home.getKey());
        if (!home.isEmpty()) {
            Context anchor = context(home.getKey());
            if (anchor != null) config = anchor.config;
            for (Site item : combined) item.setActivated(home);
        }
        Logger.i("VodPool: enabled=" + values.size() + " sites=" + combined.size());
    }

    public synchronized void recoverIfNeeded() {
        if (!retryAfterNetwork || isLoading || !getSites().isEmpty()) return;
        load(new Callback() {
            @Override public void success() { RefreshEvent.config(); RefreshEvent.video(); }
        });
    }
    private Context context(String key) {
        if (key == null || !key.startsWith("xy:") || key.length() < 36) return null;
        return contexts.get(key.substring(0, 36));
    }
    public void activate(String key) { Context value = context(key); if (value != null) playback = value; }
    public List<String> getFlags(String key) {
        Context value = context(key); return value == null ? getFlags() : value.flags;
    }
    public List<Parse> getSourceParses(String key) {
        Context value = context(key); return value == null ? getParses() : value.parses;
    }
    public Parse getSourceParse(String key) {
        Context value = context(key); return value == null ? getParse() : value.parse;
    }
    public Parse getSourceParse(String key, String name) {
        for (Parse item : getSourceParses(key)) if (item.getName().equals(name)) return item;
        return null;
    }
    public List<Doh> getDoh() {
        List<Doh> result = Doh.get(App.get()); result.removeAll(doh); result.addAll(doh); return result;
    }
    public void setDoh(List<Doh> value) { doh = value; }
    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> value) { rules = value == null ? Collections.emptyList() : value; }
    public List<Site> getSites() { return sites; }
    public List<Parse> getParses() { return playback == null ? Collections.emptyList() : playback.parses; }
    public List<Parse> getParses(int type) {
        List<Parse> result = new ArrayList<>(); for (Parse item : getParses()) if (item.getType() == type) result.add(item); return result;
    }
    public List<Parse> getParses(int type, String flag) {
        List<Parse> result = new ArrayList<>(); for (Parse item : getParses(type)) if (item.getExt().getFlag().contains(flag)) result.add(item);
        return result.isEmpty() ? getParses(type) : result;
    }
    public void setHeaders(List<JsonElement> value) { OkHttp.responseInterceptor().setHeaders(value); }
    public List<String> getFlags() { return playback == null ? Collections.emptyList() : playback.flags; }
    public void setHosts(List<String> value) { OkHttp.dns().addAll(value); }
    public void setProxy(List<String> value) { OkHttp.selector().addAll(value); }
    public List<String> getAds() { return ads; }
    public Config getConfig() { return config == null ? Config.vod() : config; }
    public Parse getParse() { return playback == null ? new Parse() : playback.parse; }
    public Site getHome() { return home == null ? new Site() : home; }
    public Parse getParse(String name) { for (Parse item : getParses()) if (item.getName().equals(name)) return item; return null; }
    public Site getSite(String key) {
        for (Site item : sites) if (item.getKey().equals(key)) return item;
        // Legacy references only resolve in their original configuration, never in an arbitrary document.
        return getSite(getConfig().getId(), key);
    }
    public Site getSite(int cid, String key) {
        for (Site item : sites) if (item.getKey().equals(key)) return item;
        for (Site item : sites) if (item.getSourceId() == cid && SourceIdentity.original(item.getKey()).equals(key)) return item;
        return new Site();
    }
    public void setParse(Parse value) {
        if (playback == null) return;
        playback.parse = value; playback.config.parse(value.getName()).save();
        for (Parse item : getParses()) item.setActivated(value);
    }
    public void setHome(Site value) {
        if (value == null || value.isEmpty()) return;
        home = value; Context anchor = context(value.getKey());
        if (anchor != null) {
            config = anchor.config; anchor.config.home(value.getKey()).save();
            Prefers.put("config_0", anchor.config.getUrl());
        }
        for (Site item : sites) item.setActivated(value);
    }
}
