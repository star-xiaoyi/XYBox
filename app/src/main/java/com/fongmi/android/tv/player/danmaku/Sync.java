package com.fongmi.android.tv.player.danmaku;

import android.os.SystemClock;

import master.flame.danmaku.danmaku.model.AbsDanmakuSync;

/**
 * DFM 会在自己的绘制线程调用这个同步器，Media3 Player 则只能在主线程访问。
 * 主线程把播放器状态写成不可变快照；绘制线程只读取快照，并用单调时钟外推播放位置。
 */
public class Sync extends AbsDanmakuSync {

    private volatile Snapshot snapshot = new Snapshot(0, false, 1.0f, SystemClock.uptimeMillis());
    private long lastSyncTime;

    public void update(long position, boolean playing, float speed) {
        snapshot = new Snapshot(Math.max(0, position), playing, Math.max(0.1f, speed), SystemClock.uptimeMillis());
    }

    @Override
    public long getUptimeMillis() {
        Snapshot current = snapshot;
        if (!current.playing) return current.position;
        long elapsed = Math.max(0, SystemClock.uptimeMillis() - current.sampleTime);
        return current.position + Math.round(elapsed * current.speed);
    }

    @Override
    public int getSyncState() {
        long current = SystemClock.uptimeMillis();
        if (current - lastSyncTime < 1000) return SYNC_STATE_HALT;
        lastSyncTime = current;
        return snapshot.playing ? SYNC_STATE_PLAYING : SYNC_STATE_HALT;
    }

    @Override
    public long getThresholdTimeMills() {
        return 1000L;
    }

    private static final class Snapshot {

        private final long position;
        private final boolean playing;
        private final float speed;
        private final long sampleTime;

        private Snapshot(long position, boolean playing, float speed, long sampleTime) {
            this.position = position;
            this.playing = playing;
            this.speed = speed;
            this.sampleTime = sampleTime;
        }
    }
}
