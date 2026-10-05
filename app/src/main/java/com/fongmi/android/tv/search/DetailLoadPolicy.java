package com.fongmi.android.tv.search;

/** A missing site during startup is a pending catalog, rather than an offline-only film. */
public final class DetailLoadPolicy {
    private static final long MAX_CONFIG_WAIT_MS = 12000;
    private long waitingSince = -1;

    public boolean awaitConfiguration(long now, boolean offlineEntry, boolean playableLocal,
                                      boolean online, boolean siteReady, boolean loading) {
        if (offlineEntry || playableLocal || !online || siteReady || !loading) {
            reset();
            return false;
        }
        if (waitingSince < 0) waitingSince = now;
        return now - waitingSince < MAX_CONFIG_WAIT_MS;
    }

    public void reset() {
        waitingSince = -1;
    }
}
