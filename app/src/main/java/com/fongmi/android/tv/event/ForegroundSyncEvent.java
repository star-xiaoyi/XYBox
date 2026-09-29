package com.fongmi.android.tv.event;

/** 一次前台同步已结束；页面可据此比较已落库的云端进度，再恢复播放。 */
public final class ForegroundSyncEvent {
    public final boolean success;
    public ForegroundSyncEvent(boolean success) { this.success = success; }
}
