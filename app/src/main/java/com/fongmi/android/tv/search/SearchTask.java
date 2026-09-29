package com.fongmi.android.tv.search;

import android.os.SystemClock;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.utils.PauseExecutor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多站点并发搜索，每个站点的结果连同接口耗时逐条回调到主线程。
 * <p>
 * 不走 SiteViewModel.search 那个 LiveData：postValue 在主线程来不及消费时会只保留最后一次，
 * 二十个站点几乎同时返回，中间的结果会被静默丢掉。
 */
public class SearchTask {

    /** 超过这个时间还没回来的站点不再等，界面按搜索结束处理；迟到的结果照样并进来。 */
    private static final long MAX_TIME = TimeUnit.SECONDS.toMillis(15);

    public interface Callback {

        void onResult(List<Vod> items, long cost);

        void onFinish();
    }

    private final PauseExecutor executor;
    private final AtomicBoolean finished;
    private final AtomicInteger pending;
    private final Callback callback;
    private final Runnable timeout;
    private final int total;
    private volatile boolean cancelled;

    public static SearchTask start(List<Site> sites, String keyword, boolean quick, Callback callback) {
        return new SearchTask(sites, keyword, quick, callback);
    }

    private SearchTask(List<Site> sites, String keyword, boolean quick, Callback callback) {
        this.executor = new PauseExecutor(Math.max(1, Math.min(20, sites.size())));
        this.pending = new AtomicInteger(sites.size());
        this.finished = new AtomicBoolean();
        this.timeout = this::finish;
        this.callback = callback;
        this.total = sites.size();
        if (sites.isEmpty()) App.post(timeout);
        else App.post(timeout, MAX_TIME);
        for (Site site : sites) executor.execute(() -> search(site, keyword, quick));
    }

    private void search(Site site, String keyword, boolean quick) {
        long start = SystemClock.elapsedRealtime();
        List<Vod> items = Collections.emptyList();
        try {
            if (!cancelled) items = SiteViewModel.search(site, keyword, quick).getList();
        } catch (Throwable ignored) {
        }
        long cost = SystemClock.elapsedRealtime() - start;
        boolean last = pending.decrementAndGet() == 0;
        List<Vod> result = items;
        App.post(() -> {
            if (cancelled) return;
            if (!result.isEmpty()) callback.onResult(result, cost);
            if (last) finish();
        });
    }

    private void finish() {
        if (cancelled || !finished.compareAndSet(false, true)) return;
        App.removeCallbacks(timeout);
        callback.onFinish();
    }

    public int getTotal() {
        return total;
    }

    public int getDone() {
        return total - pending.get();
    }

    public boolean isFinished() {
        return finished.get();
    }

    public void pause() {
        executor.pause();
    }

    public void resume() {
        executor.resume();
    }

    public void cancel() {
        cancelled = true;
        App.removeCallbacks(timeout);
        executor.shutdownNow();
    }
}
