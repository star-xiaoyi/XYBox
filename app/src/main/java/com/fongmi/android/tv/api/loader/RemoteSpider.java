package com.fongmi.android.tv.api.loader;

import com.github.catvod.crawler.Spider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** No source classes or native libraries are loaded by this main-process facade. */
public final class RemoteSpider extends Spider {
    private final JSONObject site;
    public RemoteSpider(String key, String api, String ext, String jar) { site = SourceBridge.get().site(key, api, ext, jar); }
    private String call(String op, JSONObject args) throws Exception {
        try { return execute(op, args); }
        catch (Exception error) {
            // A worker restart, a saturated pool or a stale generation says nothing about the
            // site itself: retry once after reconnecting instead of benching a healthy source.
            if (SourceUnavailableException.isInfrastructure(error) && !Thread.currentThread().isInterrupted()) {
                try { return execute(op, args); }
                catch (Exception retry) { error = retry; }
            }
            if (!Thread.currentThread().isInterrupted() && !SourceUnavailableException.isInfrastructure(error)) {
                com.fongmi.android.tv.bean.Site source = com.fongmi.android.tv.api.config.VodConfig.get().getSite(site.optString("key"));
                if (!source.isEmpty()) com.fongmi.android.tv.search.SiteHealth.failed(source);
            }
            throw error;
        }
    }

    private String execute(String op, JSONObject args) throws Exception {
        return SourceBridge.get().call(site, op, args).optString("value", "");
    }
    @Override public String homeContent(boolean filter) throws Exception { return call("home", new JSONObject().put("filter", filter)); }
    @Override public String homeVideoContent() throws Exception { return call("homeVideo", new JSONObject()); }
    @Override public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return call("category", new JSONObject().put("tid", tid).put("pg", pg).put("filter", filter).put("extend", new JSONObject(extend)));
    }
    @Override public String detailContent(List<String> ids) throws Exception { return call("detail", new JSONObject().put("ids", new JSONArray(ids))); }
    @Override public String searchContent(String key, boolean quick) throws Exception { return call("search", new JSONObject().put("query", key).put("quick", quick)); }
    @Override public String searchContent(String key, boolean quick, String pg) throws Exception { return call("search", new JSONObject().put("query", key).put("quick", quick).put("pg", pg)); }
    @Override public String playerContent(String flag, String id, List<String> flags) throws Exception {
        return call("player", new JSONObject().put("flag", flag).put("id", id).put("flags", new JSONArray(flags)));
    }
    @Override public String liveContent(String url) throws Exception { return call("live", new JSONObject().put("url", url)); }
    @Override public String action(String action) throws Exception { return call("action", new JSONObject().put("action", action)); }
    @Override public boolean manualVideoCheck() throws Exception { return Boolean.parseBoolean(call("manualVideoCheck", new JSONObject())); }
    @Override public boolean isVideoFormat(String url) throws Exception { return Boolean.parseBoolean(call("isVideoFormat", new JSONObject().put("url", url))); }
    @Override public Object[] proxyLocal(Map<String, String> params) throws Exception { return SourceBridge.get().proxy(params); }
}
