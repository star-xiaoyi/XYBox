package com.fongmi.android.tv.search;

import android.os.SystemClock;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.utils.PauseExecutor;
import com.github.catvod.utils.Logger;
import java.util.*;

/** Search a small useful batch first; expand only on demand or when no match was found. */
public class SearchTask {
    private static final int BATCH_SIZE = 24;
    private static final long MAX_TIME = 8000;
    public interface Callback {
        void onResult(List<Vod> items, long cost);
        void onFinish();
        default void onProgress() {}
        default void onSiteComplete(Site site, boolean failed) {}
    }
    private final PauseExecutor executor;
    private final int batchSize;
    private final long maxTime;
    private final boolean background;
    private boolean stalled;
    private final List<Site> sites;
    private final String keyword;
    private final boolean quick;
    private final Callback callback;
    private final Runnable timeout = this::finish;
    private volatile boolean cancelled;
    private int offset, total, delivered, batch;
    private boolean finished, started, paused;
    public static SearchTask start(List<Site> sites, String keyword, boolean quick, Callback callback) {
        return new SearchTask(sites, keyword, quick, callback, false);
    }
    public static SearchTask background(List<Site> sites, String keyword, Callback callback) {
        return new SearchTask(sites, keyword, false, callback, true);
    }
    private SearchTask(List<Site> sites, String keyword, boolean quick, Callback callback, boolean background) {
        this.background = background; batchSize = background ? 64 : BATCH_SIZE; maxTime = background ? 20000 : MAX_TIME;
        executor = new PauseExecutor(background ? 2 : 6);
        this.sites = SiteHealth.order(sites, keyword); this.keyword = keyword; this.quick = quick; this.callback = callback;
        // Defer so the caller owns this task before even an empty batch can finish.
        App.post(this::searchMore);
    }
    public void searchMore() {
        if (cancelled || paused || started && (!finished || !hasMore())) return;
        started = true;
        finished = false; delivered = 0; total = Math.min(batchSize, sites.size() - offset); int token = ++batch;
        int end = offset + total;
        for (int i = offset; i < end; i++) { Site site = sites.get(i); executor.execute(new SearchWork(site, token)); }
        offset = end;
        if (total == 0) App.post(timeout); else App.post(timeout, maxTime);
        callback.onProgress();
    }
    private final class SearchWork implements Runnable {
        final Site site; final int token;
        SearchWork(Site site, int token) { this.site = site; this.token = token; }
        @Override public void run() { search(site, token); }
    }
    private void search(Site site, int token) {
        if (cancelled || Thread.currentThread().isInterrupted()) return;
        long start = SystemClock.elapsedRealtime(); List<Vod> items = Collections.emptyList(); boolean failed = false;
        try { if (!cancelled) items = SiteViewModel.search(site, keyword, quick).getList(); }
        catch (Throwable error) { failed = true; }
        long cost = SystemClock.elapsedRealtime() - start;
        List<Vod> result = items; boolean error = failed;
        App.post(() -> {
            if (cancelled) return;
            SiteHealth.search(site, keyword, cost, error, !result.isEmpty());
            if (stalled) { stalled = false; callback.onFinish(); }
            if (token == batch) delivered++;
            if (!result.isEmpty()) callback.onResult(result, cost);
            callback.onSiteComplete(site, error);
            if (cancelled || token != batch) return;
            callback.onProgress();
            if (delivered == total && !finished) finish();
        });
    }
    private void finish() {
        if (cancelled || paused || finished) return;
        finished = true; App.removeCallbacks(timeout);
        // A plugin ignoring interruption must not produce endless empty batch loops.
        stalled = background && delivered == 0 && executor.getActiveCount() == 2;
        if (stalled) Logger.w("SourceSearch: waiting for occupied crawler workers");
        // Discard unstarted slow-batch work. At most six crawler calls remain in flight.
        List<Runnable> deferred = new ArrayList<>(); executor.getQueue().drainTo(deferred);
        for (Runnable work : deferred) if (work instanceof SearchWork) sites.add(((SearchWork) work).site);
        callback.onFinish();
    }
    public int getTotal() { return total; }
    public int getDone() { return Math.min(total, delivered); }
    public int getRemaining() { return sites.size() - offset; }
    public boolean hasMore() { return !stalled && offset < sites.size(); }
    public boolean isFinished() { return finished; }
    public boolean isBackground() { return background; }
    public void pause() { paused = true; executor.pause(); App.removeCallbacks(timeout); }
    public void resume() {
        if (cancelled || !paused) return;
        paused = false; executor.resume(); App.removeCallbacks(timeout);
        if (!started) searchMore();
        else if (!finished) {
            // In-flight calls may have completed while paused. Deliver batch completion
            // only after returning, so callbacks cannot expand searches in the background.
            if (delivered == total) finish();
            else App.post(timeout, maxTime);
        }
    }
    public void cancel() { cancelled = true; App.removeCallbacks(timeout); executor.shutdownNow(); }
}
