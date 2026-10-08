package com.fongmi.android.tv.search;

/** A missing site during startup is a pending catalog, rather than an offline-only film. */
public final class DetailLoadPolicy {
    private static final long MAX_CONFIG_WAIT_MS = 12000;
    private long waitingSince = -1;

    public static boolean shouldInitializeCatalog(boolean initialized, boolean loading, boolean hasSites) {
        return !initialized && !loading && !hasSites;
    }

    public static boolean isDiscoveryEntry(String id) {
        return id != null && (id.isEmpty() || id.startsWith("msearch:"));
    }

    public static boolean canShowEmpty(boolean detailPending, boolean configurationLoading,
                                       boolean searchRunning, boolean searchHasMore, boolean online) {
        return !detailPending && (!online || !configurationLoading && !searchRunning && !searchHasMore);
    }

    public boolean awaitConfiguration(long now, boolean offlineEntry, boolean playableLocal,
                                      boolean online, boolean siteReady, boolean loading) {
        return awaitConfiguration(now, offlineEntry, playableLocal, online, siteReady, loading, false);
    }

    public boolean awaitConfiguration(long now, boolean offlineEntry, boolean playableLocal,
                                      boolean online, boolean siteReady, boolean loading, boolean discoveryEntry) {
        if (offlineEntry || playableLocal || !online || siteReady || !loading) {
            reset();
            return false;
        }
        if (waitingSince < 0) waitingSince = now;
        // A title-only entry has no site to fall back to until the initial catalog finishes.
        // Its remote configuration requests own their timeout; an empty search is not a fallback.
        return discoveryEntry || now - waitingSince < MAX_CONFIG_WAIT_MS;
    }

    public void reset() {
        waitingSince = -1;
    }
}
