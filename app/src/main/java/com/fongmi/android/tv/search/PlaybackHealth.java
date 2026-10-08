package com.fongmi.android.tv.search;

/** Video-specific health gate. READY alone does not prove that a film is watchable. */
public final class PlaybackHealth {

    private static final long VIDEO_TRACK_GRACE_MS = 5_000;
    private static final long FIRST_FRAME_GRACE_MS = 10_000;
    private static final long PROGRESS_GRACE_MS = 7_000;
    private static final long MIN_PROGRESS_MS = 750;
    private static final long ABSOLUTE_MIN_DURATION_MS = 45_000;

    private long readyAt = -1;
    private long frameAt = -1;
    private long suspendedAt = -1;
    private long baselinePosition;
    private boolean healthy;
    private boolean failed;

    public void begin(long position) {
        readyAt = -1;
        frameAt = -1;
        suspendedAt = -1;
        baselinePosition = Math.max(0, position);
        healthy = false;
        failed = false;
    }

    public void ready(long now, long position) {
        if (readyAt < 0) {
            readyAt = now;
            baselinePosition = Math.max(0, position);
            if (suspendedAt >= 0) suspendedAt = now;
        }
    }

    public void firstFrame(long now, long position) {
        if (frameAt >= 0) return;
        frameAt = now;
        baselinePosition = Math.max(0, position);
        if (suspendedAt >= 0) suspendedAt = now;
    }

    /** Pauses, seeks, buffering and lifecycle gaps must not consume the watchability grace period. */
    public void suspend(long now) {
        if (!healthy && !failed && suspendedAt < 0) suspendedAt = now;
    }

    public void resume(long now) {
        if (suspendedAt < 0) return;
        long gap = Math.max(0, now - suspendedAt);
        if (readyAt >= 0) readyAt += gap;
        if (frameAt >= 0) frameAt += gap;
        suspendedAt = -1;
    }

    public String reason(long now, boolean requested, boolean excluded, boolean ready, boolean buffering,
                         boolean hasVideo, long position) {
        if (healthy || failed) return "";
        if (!requested || excluded || !ready || buffering || readyAt < 0) {
            suspend(now);
            return "";
        }
        resume(now);
        if (!hasVideo && now - readyAt >= VIDEO_TRACK_GRACE_MS) return fail("missing-video-track");
        if (hasVideo && frameAt < 0 && now - readyAt >= FIRST_FRAME_GRACE_MS) return fail("first-frame-timeout");
        if (frameAt >= 0) {
            if (Math.max(0, position) - baselinePosition >= MIN_PROGRESS_MS) {
                healthy = true;
                return "";
            }
            if (now - frameAt >= PROGRESS_GRACE_MS) return fail("frozen-playback");
        }
        return "";
    }

    public boolean isHealthy() {
        return healthy;
    }

    public boolean isFailed() {
        return failed;
    }

    private String fail(String reason) {
        failed = true;
        return reason;
    }

    public static boolean suspiciousDuration(long actual, long expected) {
        if (actual <= 0) return false;
        if (actual < ABSOLUTE_MIN_DURATION_MS) return true;
        return expected >= 5 * 60_000L && actual < Math.max(60_000L, expected / 2);
    }
}
