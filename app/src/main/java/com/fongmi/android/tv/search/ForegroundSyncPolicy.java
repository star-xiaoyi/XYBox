package com.fongmi.android.tv.search;

/** Cloud progress may delay foreground playback briefly, but cannot block it indefinitely. */
public final class ForegroundSyncPolicy {
    public static final long MAX_WAIT_MS = 8_000;
    private long waitingSince = -1;

    public void begin(long now, boolean waiting) {
        waitingSince = waiting ? now : -1;
    }

    public long remaining(long now) {
        return waitingSince < 0 ? 0 : Math.max(0, MAX_WAIT_MS - Math.max(0, now - waitingSince));
    }

    public boolean expire(long now) {
        if (waitingSince < 0 || remaining(now) > 0) return false;
        finish();
        return true;
    }

    public void finish() {
        waitingSince = -1;
    }
}
