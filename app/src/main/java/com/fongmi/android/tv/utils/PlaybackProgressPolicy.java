package com.fongmi.android.tv.utils;

/** 播放进度的时间和版本规则；时间代表实际观看，而不是页面恢复或上传时刻。 */
public final class PlaybackProgressPolicy {
    private PlaybackProgressPolicy() { }

    public static boolean shouldRecord(boolean playing, boolean awaitingCloud, long previous, long current) {
        return playing && !awaitingCloud && current >= 0 && current != previous;
    }

    public static boolean canWrite(long knownVersion, long storedVersion) {
        return storedVersion <= knownVersion;
    }

    public static boolean canApplyDownloaded(long downloadedVersion, long currentVersion) {
        return downloadedVersion >= currentVersion;
    }
}
