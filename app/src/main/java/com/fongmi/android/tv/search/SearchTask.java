package com.fongmi.android.tv.search;

import android.os.SystemClock;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.utils.PauseExecutor;
import com.github.catvod.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Search every site shallowly before paging or trying aliases. A timed-out plugin loses
 * its worker pool, but cannot prevent later sites/configurations from running.
 */
public class SearchTask {

    private static final int BATCH_SIZE = 24;
    private static final long MAX_TIME = 8_000;
    private static final int DISCOVERY_PAGES = 2;
    private static final int DISCOVERY_QUERIES = 3;

    public interface Callback {
        void onResult(List<Vod> items, long cost);
        void onFinish();
        default void onProgress() {}
        default void onSiteComplete(Site site, boolean failed) {}
    }

    private final int workers;
    private final int batchSize;
    private final long maxTime;
    private final boolean background;
    private final List<PauseExecutor> executors = new ArrayList<>();
    private final List<SearchWork> works = new ArrayList<>();
    private final List<Request> requests = new ArrayList<>();
    private final Set<String> timedOutSites = ConcurrentHashMap.newKeySet();
    private final Set<String> completedSites = new HashSet<>();
    private final Set<String> checkedSites = new HashSet<>();
    private final Map<String, Boolean> siteFailures = new HashMap<>();
    private final String healthKeyword;
    private final boolean quick;
    private final Callback callback;
    private final SourceDiscovery discovery;
    private final Runnable timeout = this::finish;
    private volatile boolean cancelled;
    private PauseExecutor executor;
    private int offset, total, delivered, batch;
    private boolean finished, started, paused;

    public static SearchTask start(List<Site> sites, String keyword, boolean quick, Callback callback) {
        return new SearchTask(sites, keyword, quick, callback, false, null);
    }

    public static SearchTask background(List<Site> sites, String keyword, Callback callback) {
        return new SearchTask(sites, keyword, false, callback, true, null);
    }

    public static SearchTask discovery(List<Site> sites, SourceDiscovery discovery, boolean background, Callback callback) {
        String keyword = discovery.queries().isEmpty() ? "" : discovery.queries().get(0);
        return new SearchTask(sites, keyword, false, callback, background, discovery);
    }

    private SearchTask(List<Site> sites, String keyword, boolean quick, Callback callback,
                       boolean background, SourceDiscovery discovery) {
        this.background = background;
        this.workers = background ? 2 : 6;
        this.batchSize = background ? 64 : BATCH_SIZE;
        this.maxTime = background ? 20_000 : MAX_TIME;
        this.healthKeyword = keyword;
        this.quick = quick;
        this.callback = callback;
        this.discovery = discovery;
        List<Site> ordered = SiteHealth.order(sites, keyword);
        if (discovery == null) {
            for (Site site : ordered) requests.add(new Request(site, keyword, 1, true, true));
        } else {
            List<String> queries = discovery.queries();
            int queryCount = Math.min(DISCOVERY_QUERIES, queries.size());
            // Phase order matters: all 300 page-one calls precede any page-two/alias work.
            for (int query = 0; query < queryCount; query++) {
                for (int page = 1; page <= DISCOVERY_PAGES; page++) {
                    boolean last = query == queryCount - 1 && page == DISCOVERY_PAGES;
                    for (Site site : ordered)
                        requests.add(new Request(site, queries.get(query), page, last, query == 0 && page == 1));
                }
            }
        }
        // Defer so the caller owns this task before even an empty plan can finish.
        App.post(this::searchMore);
    }

    public void searchMore() {
        if (cancelled || paused || started && (!finished || !hasMore())) return;
        started = true;
        finished = false;
        delivered = 0;
        total = Math.min(batchSize, requests.size() - offset);
        int token = ++batch;
        works.clear();
        executor = new PauseExecutor(workers);
        executors.add(executor);
        int end = offset + total;
        for (int i = offset; i < end; i++) {
            SearchWork work = new SearchWork(requests.get(i), token);
            works.add(work);
            executor.execute(work);
        }
        offset = end;
        if (total == 0) App.post(timeout); else App.post(timeout, maxTime);
        callback.onProgress();
    }

    private static final class Request {
        final Site site;
        final String keyword;
        final int page;
        final boolean last;
        final boolean primary;

        Request(Site site, String keyword, int page, boolean last, boolean primary) {
            this.site = site;
            this.keyword = keyword;
            this.page = page;
            this.last = last;
            this.primary = primary;
        }

        String siteId() {
            return site.getSourceId() + "\n" + site.getKey();
        }
    }

    private final class SearchWork implements Runnable {
        final Request request;
        final int token;
        final AtomicBoolean terminal = new AtomicBoolean();
        volatile boolean active, skipped;

        SearchWork(Request request, int token) {
            this.request = request;
            this.token = token;
        }

        @Override
        public void run() {
            active = true;
            search(this);
        }
    }

    private void search(SearchWork work) {
        if (cancelled || work.terminal.get() || Thread.currentThread().isInterrupted()) return;
        long start = SystemClock.elapsedRealtime();
        List<Vod> items = Collections.emptyList();
        boolean failed = false;
        if (!timedOutSites.contains(work.request.siteId()) && !SiteHealth.coolingDown(work.request.site)) {
            try {
                if (!cancelled) {
                    Result result = discovery == null
                            ? SiteViewModel.search(work.request.site, work.request.keyword, quick)
                            : SiteViewModel.search(work.request.site, work.request.keyword, false, work.request.page);
                    items = result.getList();
                }
            } catch (Throwable error) {
                failed = true;
            }
        } else { work.skipped = true; failed = true; }
        long cost = SystemClock.elapsedRealtime() - start;
        if (!work.terminal.compareAndSet(false, true)) return;
        List<Vod> result = items;
        boolean error = failed;
        App.post(() -> deliver(work, result, cost, error));
    }

    private void deliver(SearchWork work, List<Vod> result, long cost, boolean failed) {
        if (cancelled) return;
        Request request = work.request;
        if (!work.skipped) checkedSites.add(request.siteId());
        if (!work.skipped) SiteHealth.search(request.site, healthKeyword, cost, failed, !result.isEmpty());
        if (failed) siteFailures.put(request.siteId(), true);
        if (work.token == batch) delivered++;
        if (!result.isEmpty()) callback.onResult(result, cost);
        if (request.last) completeSite(request.site);
        if (cancelled || work.token != batch) return;
        callback.onProgress();
        if (delivered == total && !finished) finish();
    }

    private void completeSite(Site site) {
        String id = site.getSourceId() + "\n" + site.getKey();
        if (!completedSites.add(id)) return;
        callback.onSiteComplete(site, timedOutSites.contains(id) || siteFailures.getOrDefault(id, false));
    }

    private void finish() {
        if (cancelled || paused || finished) return;
        finished = true;
        App.removeCallbacks(timeout);
        List<Request> deferred = new ArrayList<>();
        int abandoned = 0;
        for (SearchWork work : new ArrayList<>(works)) {
            if (work.terminal.get() || !work.terminal.compareAndSet(false, true)) continue;
            if (!work.active) {
                deferred.add(work.request);
            } else {
                abandoned++;
                String id = work.request.siteId();
                checkedSites.add(id);
                timedOutSites.add(id);
                siteFailures.put(id, true);
                SiteHealth.search(work.request.site, healthKeyword, maxTime, true, false);
                if (work.request.last) completeSite(work.request.site);
            }
        }
        if (executor != null) executor.shutdownNow();
        requests.addAll(deferred);
        if (abandoned > 0) Logger.w("SourceSearch: isolated timed-out sites=" + abandoned + " deferred=" + deferred.size());
        callback.onProgress();
        callback.onFinish();
    }

    public int getTotal() { return total; }
    public int getCheckedSites() { return checkedSites.size(); }
    public int getDone() { return Math.min(total, delivered); }
    public int getRemaining() { return requests.size() - offset; }
    public boolean hasMore() { return offset < requests.size(); }
    public boolean isFinished() { return finished; }
    public boolean isBackground() { return background; }

    public void pause() {
        paused = true;
        if (executor != null) executor.pause();
        App.removeCallbacks(timeout);
    }

    public void resume() {
        if (cancelled || !paused) return;
        paused = false;
        if (executor != null) executor.resume();
        App.removeCallbacks(timeout);
        if (!started) searchMore();
        else if (!finished) {
            if (delivered == total) finish();
            else App.post(timeout, maxTime);
        }
    }

    public void cancel() {
        cancelled = true;
        App.removeCallbacks(timeout);
        for (SearchWork work : works) work.terminal.set(true);
        for (PauseExecutor value : executors) value.shutdownNow();
        executors.clear();
    }
}
