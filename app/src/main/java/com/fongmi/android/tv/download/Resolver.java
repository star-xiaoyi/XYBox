package com.fongmi.android.tv.download;

import android.text.TextUtils;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.ParseJob;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 把一集的站源地址解析成真正可下载的直链，和播放走的是同一套站源逻辑：
 * 先取 playerContent，需要解析的再跑一遍解析器（含 WebView 嗅探）。
 */
public class Resolver {

    /** 解析出来的可下载地址。 */
    public static class Address {

        private final String url;
        private final Map<String, String> headers;

        public Address(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers == null ? new HashMap<>() : headers;
        }

        public String getUrl() {
            return url;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public boolean isHls() {
            String lower = url.toLowerCase();
            int query = lower.indexOf('?');
            String path = query == -1 ? lower : lower.substring(0, query);
            return path.endsWith(".m3u8") || path.endsWith(".m3u");
        }
    }

    public static Address resolve(Download item) throws Exception {
        Result result;
        try {
            result = SiteViewModel.getPlayer(item.getSiteKey(), item.getFlag(), item.getEpisodeUrl());
        } catch (Throwable e) {
            throw new Exception("取播放地址失败：" + message(e));
        }
        if (result == null) throw new Exception("站源没有返回播放地址");
        if (result.hasMsg()) throw new Exception(result.getMsg());
        if (result.getUrl() == null || result.getUrl().isEmpty()) throw new Exception("站源没有返回播放地址");
        if (result.getParse() != 1 && result.getJx() != 1) {
            String url = result.getRealUrl();
            if (TextUtils.isEmpty(url)) throw new Exception("站源没有返回播放地址");
            return new Address(url, result.getHeaders());
        }
        return parse(result);
    }

    /**
     * 需要解析的源：复用播放器那套 ParseJob，回调在主线程，这里用闩锁把工作线程挂住等结果。
     */
    private static Address parse(Result result) throws Exception {
        boolean useParse = VodConfig.hasParse() && ((result.getPlayUrl().isEmpty() && VodConfig.get().getFlags().contains(result.getFlag())) || result.getJx() == 1);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Address> holder = new AtomicReference<>();
        ParseJob job = ParseJob.create(new ParseCallback() {
            @Override
            public void onParseSuccess(Map<String, String> headers, String url, String from) {
                holder.set(new Address(url, headers));
                latch.countDown();
            }

            @Override
            public void onParseError() {
                latch.countDown();
            }
        });
        try {
            job.start(result, useParse);
            if (!latch.await(Constant.TIMEOUT_PARSE_DEF + Constant.TIMEOUT_PARSE_WEB, TimeUnit.MILLISECONDS)) throw new Exception("解析超时");
        } finally {
            job.stop();
        }
        Address address = holder.get();
        if (address == null || TextUtils.isEmpty(address.getUrl())) throw new Exception("解析失败，该源可能不支持缓存");
        return address;
    }

    private static String message(Throwable e) {
        String message = e.getMessage();
        if (!TextUtils.isEmpty(message)) return message;
        return e.getClass().getSimpleName();
    }
}
