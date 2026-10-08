package com.fongmi.android.tv.search;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** One playback attempt at a time; user pause/seek and healthy playback never cause failover. */
public final class PlaybackPolicy {
    public static final long STALL_MS = 8000, STARTUP_MS = 12000, STARTUP_PATIENT_MS = 45000, SWITCH_GAP_MS = 15000;
    private static final long WINDOW_MS = 60000, FAILURE_MS = 120000;
    private final Deque<long[]> stalls = new ArrayDeque<>();
    private final Deque<Long> switches = new ArrayDeque<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private long startedAt, bufferingAt = -1, ignoreUntil, lastSwitch = -SWITCH_GAP_MS;
    private boolean started, confirmed;
    private int attempts, stallCount;
    private long stalledMs, longestStallMs, firstReadyMs = -1;

    /** A refused/expired address needs a new route; transient HTTP failures can reconnect in place. */
    public static boolean retryHttpStatus(int code) { return code == 408 || code == 429 || code >= 500 && code <= 599; }

    /**
     * The 127.0.0.1 video proxy is our own hop: a read abort there (a restarted source worker)
     * can reconnect in place, while the same error on a remote URL needs a new route.
     */
    public static boolean isLocalProxyUrl(String url) {
        return url != null && (url.contains("://127.0.0.1:") || url.contains("://localhost:"));
    }

    public void begin(long now) {
        finishBuffer(now, confirmed);
        started = true; confirmed = false; startedAt = now; bufferingAt = -1; attempts++;
    }

    public void buffering(long now, boolean requested, boolean excluded) {
        if (!requested || excluded || now < ignoreUntil) return;
        if (bufferingAt < 0) bufferingAt = now;
    }

    public long ready(long now) {
        long waited = finishBuffer(now, confirmed);
        confirmed = true;
        if (firstReadyMs < 0) firstReadyMs = Math.max(0, now - startedAt);
        return waited;
    }

    public void seek(long now) {
        finishBuffer(now, false); stalls.clear(); ignoreUntil = now + 3000;
    }

    public void suspend(long now) {
        finishBuffer(now, false); stalls.clear(); ignoreUntil = now + 1000;
        if (started && !confirmed) startedAt = now;
    }

    public void switching(long now) { finishBuffer(now, confirmed); started = false; }
    public void adjusted(long now) { finishBuffer(now, confirmed); stalls.clear(); ignoreUntil = now + 3000; }

    public String reason(long now, boolean requested, boolean excluded, boolean ready, boolean buffering, long bufferMs) {
        return reason(now, requested, excluded, ready, buffering, bufferMs, false);
    }

    /**
     * progressing 表示源确实在干活（还在嗅探，或媒体缓冲持续增长）：慢而活着的源多等一会，
     * 完全没进展的源仍按基础时限换掉。
     */
    public String reason(long now, boolean requested, boolean excluded, boolean ready, boolean buffering, long bufferMs, boolean progressing) {
        prune(now);
        if (!requested || excluded) { suspend(now); return ""; }
        if (!started || now < ignoreUntil) return "";
        if (buffering) buffering(now, true, false);
        if (!confirmed && now - startedAt >= STARTUP_MS && (!progressing || now - startedAt >= STARTUP_PATIENT_MS)) return "startup-timeout";
        if (confirmed && bufferingAt >= 0 && now - bufferingAt >= STALL_MS) return "long-buffering";
        long recentWait = 0;
        for (long[] stall : stalls) recentWait += stall[1];
        return confirmed && ready && bufferMs < 5000 && stalls.size() >= 2 && recentWait >= STALL_MS ? "repeated-buffering" : "";
    }

    public boolean canSwitch(long now, boolean manual, boolean hardFailure) {
        prune(now);
        return manual || switches.size() < 4 && (hardFailure || now - lastSwitch >= SWITCH_GAP_MS);
    }

    public void switched(String previous, long now) {
        if (!previous.isEmpty()) cooldowns.put(previous, now + FAILURE_MS);
        lastSwitch = now; switches.addLast(now);
    }

    public void failed(String route, long now) { if (!route.isEmpty()) cooldowns.put(route, now + FAILURE_MS); }
    public boolean available(String route, long now) { return cooldowns.getOrDefault(route, 0L) <= now; }

    public void episode(long now) {
        suspend(now); stalls.clear(); switches.clear(); cooldowns.clear();
        lastSwitch = now - SWITCH_GAP_MS; started = false; confirmed = false;
    }

    private long finishBuffer(long now, boolean count) {
        if (bufferingAt < 0) return 0;
        long elapsed = Math.max(0, now - bufferingAt); bufferingAt = -1;
        if (count && confirmed && elapsed >= 800) {
            stalls.addLast(new long[]{now, elapsed}); stallCount++; stalledMs += elapsed;
            longestStallMs = Math.max(longestStallMs, elapsed);
        }
        return elapsed;
    }

    private void prune(long now) {
        while (!stalls.isEmpty() && now - stalls.peekFirst()[0] > WINDOW_MS) stalls.removeFirst();
        while (!switches.isEmpty() && now - switches.peekFirst() > WINDOW_MS) switches.removeFirst();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public int attempts() { return attempts; }
    public int stallCount() { return stallCount; }
    public long stalledMs() { return stalledMs; }
    public long longestStallMs() { return longestStallMs; }
    public long firstReadyMs() { return firstReadyMs; }
}
