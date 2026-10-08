package com.fongmi.android.tv.search;

/** Successful discovery is reusable for a bounded time, never a permanent exclusion. */
public final class CandidateRefreshPolicy {
    public static final long PROBE_MS = 120_000, SEARCH_MS = 300_000, FAILURE_MS = 60_000;
    private CandidateRefreshPolicy() {}
    public static boolean checked(long until, long now) {
        return until != Long.MAX_VALUE && until > now;
    }
    public static long until(long now, boolean search, boolean failed) {
        return now + (failed ? FAILURE_MS : search ? SEARCH_MS : PROBE_MS);
    }
    public static long migrate(long until, long now) {
        return checked(until, now) ? Math.min(until, now + FAILURE_MS) : 0;
    }
}
