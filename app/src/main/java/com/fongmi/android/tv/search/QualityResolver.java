package com.fongmi.android.tv.search;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Url;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.ParseJob;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Resolves a probe with the owning configuration's playback parser, without touching playback. */
public final class QualityResolver {
    public static final class Address {
        public final String url;
        public final Map<String, String> headers;
        Address(String url, Map<String, String> headers) {
            this.url = url; this.headers = new HashMap<>(headers);
            boolean hasUa = false; for (String key : this.headers.keySet()) hasUa |= key.equalsIgnoreCase("User-Agent");
            if (!hasUa) this.headers.put("User-Agent", com.fongmi.android.tv.Setting.getUa().isEmpty()
                    ? com.fongmi.android.tv.player.exo.ExoUtil.getUa() : com.fongmi.android.tv.Setting.getUa());
        }
    }
    private QualityResolver() {}
    public static Address resolve(Result original, String url) throws Exception {
        Result result = Result.objectFrom(original.toString());
        result.setUrl(Url.create().add(url));
        if (result.hasMsg()) throw new IllegalStateException(result.getMsg());
        if (result.getParse() != 1 && result.getJx() != 1) return new Address(result.getRealUrl(), result.getHeaders());
        boolean useParse = !VodConfig.get().getSourceParses(result.getKey()).isEmpty()
                && ((result.getPlayUrl().isEmpty() && VodConfig.get().getFlags(result.getKey()).contains(result.getFlag())) || result.getJx() == 1);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Address> resolved = new AtomicReference<>();
        ParseJob job = ParseJob.create(new ParseCallback() {
            @Override public void onParseSuccess(Map<String, String> headers, String value, String from) {
                Map<String, String> merged = new HashMap<>(result.getHeaders()); merged.putAll(headers);
                resolved.set(new Address(value, merged)); latch.countDown();
            }
            @Override public void onParseError() { latch.countDown(); }
        });
        try {
            job.start(result, useParse);
            if (!latch.await(8000, TimeUnit.MILLISECONDS)) throw new TimeoutException("播放地址解析超时");
            if (resolved.get() == null) throw new IllegalStateException("未解析到视频地址");
            return resolved.get();
        } finally { job.stop(); }
    }
}
