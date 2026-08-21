package com.fongmi.android.tv.model;
import com.github.catvod.utils.Logger;

import android.text.TextUtils;

import androidx.collection.ArrayMap;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Url;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Response;

public class SiteViewModel extends ViewModel {

    /** 离线缓存的伪站源 key：详情页拿到它就走本地文件，不再联网。 */
    public static final String DOWNLOAD_KEY = "download_agent";

    public MutableLiveData<Episode> episode;
    public MutableLiveData<Result> result;
    public MutableLiveData<Result> player;
    public MutableLiveData<Result> search;
    public MutableLiveData<Result> action;
    private ExecutorService executor;

    public SiteViewModel() {
        this.episode = new MutableLiveData<>();
        this.result = new MutableLiveData<>();
        this.player = new MutableLiveData<>();
        this.search = new MutableLiveData<>();
        this.action = new MutableLiveData<>();
    }

    public void setEpisode(Episode value) {
        episode.setValue(value);
    }

    public void homeContent() {
        execute(result, "首页", () -> {
            Site site = VodConfig.get().getHome();
            if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                String homeContent = spider.homeContent(true);
                SpiderDebug.log(homeContent);
                Result result = Result.fromJson(homeContent);
                if (!result.getList().isEmpty()) return result;
                String homeVideoContent = spider.homeVideoContent();
                SpiderDebug.log(homeVideoContent);
                result.setList(Result.fromJson(homeVideoContent).getList());
                return result;
            } else if (site.getType() == 4) {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("filter", "true");
                String homeContent = call(site.fetchExt(), params);
                SpiderDebug.log(homeContent);
                return Result.fromJson(homeContent);
            } else {
                Response response = OkHttp.newCall(site.getApi(), site.getHeaders()).execute();
                String homeContent = response.body().string();
                SpiderDebug.log(homeContent);
                response.close();
                return fetchPic(site, Result.fromType(site.getType(), homeContent));
            }
        });
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        execute(result, "分类", () -> {
            Site site = VodConfig.get().getSite(key);
            if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                String categoryContent = spider.categoryContent(tid, page, filter, extend);
                SpiderDebug.log(categoryContent);
                return Result.fromJson(categoryContent);
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                if (site.getType() == 1 && !extend.isEmpty()) params.put("f", App.gson().toJson(extend));
                if (site.getType() == 4) params.put("ext", Util.base64(App.gson().toJson(extend), Util.URL_SAFE));
                params.put("ac", site.getType() == 0 ? "videolist" : "detail");
                params.put("t", tid);
                params.put("pg", page);
                String categoryContent = call(site, params);
                SpiderDebug.log(categoryContent);
                return Result.fromType(site.getType(), categoryContent);
            }
        });
    }

    public void detailContent(String key, String id) {
        execute(result, "详情", () -> {
            Site site = VodConfig.get().getSite(key);
            if (DOWNLOAD_KEY.equals(key)) {
                return offlineDetail(id);
            } else if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                String detailContent = spider.detailContent(Arrays.asList(id));
                SpiderDebug.log(detailContent);
                Result result = Result.fromJson(detailContent);
                if (!result.getList().isEmpty()) result.getList().get(0).setVodFlags();
                if (!result.getList().isEmpty()) Source.get().parse(result.getList().get(0).getVodFlags());
                return result;
            } else if (site.isEmpty() && "push_agent".equals(key)) {
                Vod vod = new Vod();
                vod.setVodId(id);
                vod.setVodName(id);
                vod.setVodPic(ResUtil.getString(R.string.push_image));
                vod.setVodFlags(Flag.create(ResUtil.getString(R.string.push), id));
                Source.get().parse(vod.getVodFlags());
                return Result.vod(vod);
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("ac", site.getType() == 0 ? "videolist" : "detail");
                params.put("ids", id);
                String detailContent = call(site, params);
                SpiderDebug.log(detailContent);
                Result result = Result.fromType(site.getType(), detailContent);
                if (!result.getList().isEmpty()) result.getList().get(0).setVodFlags();
                if (!result.getList().isEmpty()) Source.get().parse(result.getList().get(0).getVodFlags());
                return result;
            }
        });
    }

    public void playerContent(String key, String flag, String id) {
        execute(player, "播放地址", () -> {
            Source.get().stop();
            return getPlayer(key, flag, id);
        });
    }

    /**
     * 阻塞式取播放地址。播放走 {@link #playerContent}，离线缓存的地址解析直接复用这里，
     * 两边共用同一套站源逻辑，避免下载和播放拿到的地址不一致。
     */
    public static Result getPlayer(String key, String flag, String id) throws Exception {
        Site site = VodConfig.get().getSite(key);
        // 本地缓存文件不能丢给爬虫去解析，认出 file:// 就直接当播放地址用
        if (DOWNLOAD_KEY.equals(key) || (id != null && id.startsWith("file://"))) {
            Result result = new Result();
            result.setParse(0);
            result.setFlag(flag);
            result.setUrl(Url.create().add(id));
            return result;
        } else if (site.getType() == 3) {
            Spider spider = site.recent().spider();
            String playerContent = spider.playerContent(flag, id, VodConfig.get().getFlags());
            SpiderDebug.log(playerContent);
            Result result = Result.fromJson(playerContent);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setUrl(Source.get().fetch(result));
            result.setHeader(site.getHeader());
            result.setKey(key);
            return result;
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("play", id);
            params.put("flag", flag);
            String playerContent = call(site, params);
            SpiderDebug.log(playerContent);
            Result result = Result.fromJson(playerContent);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setUrl(Source.get().fetch(result));
            result.setHeader(site.getHeader());
            result.setKey(key);
            return result;
        } else if (site.isEmpty() && "push_agent".equals(key)) {
            Result result = new Result();
            result.setParse(0);
            result.setFlag(flag);
            result.setUrl(Url.create().add(id));
            result.setUrl(Source.get().fetch(result));
            return result;
        } else {
            Url url = Url.create().add(id);
            Result result = new Result();
            result.setUrl(url);
            result.setFlag(flag);
            result.setHeader(site.getHeader());
            result.setPlayUrl(site.getPlayUrl());
            result.setParse(Sniffer.isVideoFormat(url.v()) && result.getPlayUrl().isEmpty() ? 0 : 1);
            result.setUrl(Source.get().fetch(result));
            result.setKey(key);
            SpiderDebug.log(result.toString());
            return result;
        }
    }

    private static Result offlineDetail(String groupKey) {
        return offlineResult(groupKey);
    }

    /**
     * 把这部剧已缓存好的集拼成一条播放源，供详情页当作一条普通线路挂上去。
     * 不分线路——用户可能这个源下几集、那个源下几集，对他来说就是一部剧一个集列表，
     * 同一集在多个源都缓存过时只留一份。
     */
    public static Result offlineResult(String groupKey) {
        List<Download> playable = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (Download item : Download.getByGroup(groupKey)) {
            if (!item.isPlayable()) continue;
            String key = Download.episodeKey(item.getEpisodeName());
            if (keys.contains(key)) continue;
            keys.add(key);
            playable.add(item);
        }
        if (playable.isEmpty()) return Result.empty();
        StringBuilder urls = new StringBuilder();
        for (Download item : playable) {
            if (urls.length() > 0) urls.append("#");
            urls.append(item.getEpisodeName()).append("$").append("file://").append(item.getLocalPath());
        }
        Download head = playable.get(0);
        Flag flag = Flag.create(ResUtil.getString(R.string.download_flag));
        flag.createEpisode(urls.toString());
        List<Flag> flags = new ArrayList<>();
        flags.add(flag);
        Vod vod = new Vod();
        vod.setVodId(groupKey);
        vod.setVodName(head.getVodName());
        vod.setVodPic(head.getVodPic());
        vod.setVodContent(head.getVodContent());
        vod.setVodYear(head.getVodYear());
        vod.setVodArea(head.getVodArea());
        vod.setTypeName(head.getVodType());
        vod.setVodFlags(flags);
        return Result.vod(vod);
    }

    public void action(String key, String action) {
        execute(this.action, "动作", () -> {
            Site site = VodConfig.get().getSite(key);
            if (site.getType() == 3) return Result.fromJson(site.recent().spider().action(action));
            if (site.getType() == 4) return Result.fromJson(OkHttp.string(action));
            return Result.empty();
        });
    }

    public void searchContent(Site site, String keyword, boolean quick) throws Throwable {
        if (site.getType() == 3) {
            if (quick && !site.isQuickSearch()) return;
            String searchContent = site.spider().searchContent(Trans.t2s(keyword), quick);
            SpiderDebug.log(site.getName() + "," + searchContent);
            post(site, Result.fromJson(searchContent));
        } else {
            if (quick && !site.isQuickSearch()) return;
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("wd", Trans.t2s(keyword));
            params.put("quick", String.valueOf(quick));
            String searchContent = call(site, params);
            SpiderDebug.log(site.getName() + "," + searchContent);
            post(site, fetchPic(site, Result.fromType(site.getType(), searchContent)));
        }
    }

    public void searchContent(Site site, String keyword, String page) {
        execute(result, "搜索", () -> {
            if (site.getType() == 3) {
                String searchContent = site.spider().searchContent(Trans.t2s(keyword), false, page);
                SpiderDebug.log(site.getName() + "," + searchContent);
                Result result = Result.fromJson(searchContent);
                for (Vod vod : result.getList()) vod.setSite(site);
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("wd", Trans.t2s(keyword));
                params.put("pg", page);
                String searchContent = call(site, params);
                SpiderDebug.log(site.getName() + "," + searchContent);
                Result result = fetchPic(site, Result.fromType(site.getType(), searchContent));
                for (Vod vod : result.getList()) vod.setSite(site);
                return result;
            }
        });
    }

    private static String call(Site site, ArrayMap<String, String> params) throws IOException {
        if (!site.getExt().isEmpty()) params.put("extend", site.getExt());
        Call get = OkHttp.newCall(site.getApi(), site.getHeaders(), params);
        Call post = OkHttp.newCall(site.getApi(), site.getHeaders(), OkHttp.toBody(params));
        Response response = (site.getExt().length() <= 1000 ? get : post).execute();
        String result = response.body().string();
        response.close();
        return result;
    }

    private Result fetchPic(Site site, Result result) throws Exception {
        if (site.getType() > 2 || result.getList().isEmpty() || !result.getList().get(0).getVodPic().isEmpty()) return result;
        ArrayList<String> ids = new ArrayList<>();
        if (site.getCategories().isEmpty()) for (Vod item : result.getList()) ids.add(item.getVodId());
        else for (Vod item : result.getList()) if (site.getCategories().contains(item.getTypeName())) ids.add(item.getVodId());
        if (ids.isEmpty()) return result.clear();
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("ac", site.getType() == 0 ? "videolist" : "detail");
        params.put("ids", TextUtils.join(",", ids));
        Response response = OkHttp.newCall(site.getApi(), site.getHeaders(), params).execute();
        result.setList(Result.fromType(site.getType(), response.body().string()).getList());
        response.close();
        return result;
    }

    private void post(Site site, Result result) {
        if (result.getList().isEmpty()) return;
        for (Vod vod : result.getList()) vod.setSite(site);
        this.search.postValue(result);
    }

    private void execute(MutableLiveData<Result> result, String tag, Callable<Result> callable) {
        if (executor != null) executor.shutdownNow();
        executor = Executors.newFixedThreadPool(2);
        executor.execute(() -> {
            try {
                if (Thread.interrupted()) return;
                result.postValue(executor.submit(callable).get(Constant.TIMEOUT_VOD, TimeUnit.MILLISECONDS));
            } catch (Throwable e) {
                if (e instanceof InterruptedException || Thread.interrupted()) return;
                // 确保在发生任何异常时都返回结果，避免界面一直显示加载中
                if (e.getCause() instanceof ExtractException) {
                    result.postValue(Result.error(e.getCause().getMessage()));
                } else if (e instanceof java.util.concurrent.TimeoutException) {
                    result.postValue(Result.error("加载超时，请重试"));
                } else {
                    result.postValue(Result.empty());
                }
                Logger.e("Error", e);
            }
        });
    }

    @Override
    protected void onCleared() {
        if (executor != null) executor.shutdownNow();
    }
}
