package com.fongmi.android.tv.download;

import android.net.TrafficStats;
import android.os.Process;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;

import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

/**
 * 智能模式的全局带宽调度器。
 *
 * <p>Android 不允许普通应用给别的进程设置网络优先级，但能读取整机和本 UID 的流量计数。
 * 两者之差代表其他应用正在使用的流量。这里用两秒滑动窗口识别持续竞争，避免短视频 App
 * 一次突发预加载就让缓存长时间卡在低速；没有竞争时完全不介入，吞吐与极速模式一致。</p>
 */
final class DownloadGovernor {

    private static final long KIB = 1024L;
    private static final long MIB = 1024L * KIB;
    private static final long SAMPLE_INTERVAL_MS = 500L;
    private static final long LOG_INTERVAL_MS = 5000L;
    private static final long BACKGROUND_WARMUP_MS = 6000L;
    private static final int WINDOW_SAMPLES = 4;
    private static final long ACTIVE_SAMPLE_RATE = 128L * KIB;
    private static final long ENTER_AVERAGE_RATE = 256L * KIB;
    private static final long EXIT_AVERAGE_RATE = 96L * KIB;
    private static final long MIN_SHARE_RATE = 6L * MIB;
    private static final long MAX_SHARE_RATE = 16L * MIB;
    private static final long CAPACITY_SHARE_PERCENT = 35L;
    /** 下载已经低于计划保留值时不再叠加限速，连续两个窗口确认，避免一次抖动来回切换。 */
    private static final long SOURCE_RELEASE_PERCENT = 75L;
    private static final int SOURCE_SLOW_WINDOWS = 2;

    private static final long[] otherWindow = new long[WINDOW_SAMPLES];
    private static final long[] appWindow = new long[WINDOW_SAMPLES];
    private static final long[] downloadWindow = new long[WINDOW_SAMPLES];
    private static long rate = MIN_SHARE_RATE;
    private static long targetRate = MIN_SHARE_RATE;
    private static long learnedCapacity;
    private static long sampledDownloadBytes;
    private static long backgroundAt;
    private static long nextPermitNanos;
    private static long sampleAt;
    private static long totalRx;
    private static long totalTx;
    private static long uidRx;
    private static long uidTx;
    private static long lastLog;
    private static int windowIndex;
    private static int windowCount;
    private static int quietWindows;
    private static int sourceSlowWindows;
    private static boolean countersReady;
    private static boolean competing;
    private static boolean sourceLimited;
    private static boolean foregroundKnown;
    private static boolean foreground;

    private DownloadGovernor() {
    }

    /** 每读到一块网络数据就预约全局额度；极速模式完全绕过。 */
    static void acquire(int bytes) {
        if (bytes <= 0 || !Setting.isDownloadSmartMode()) return;
        long delay;
        synchronized (DownloadGovernor.class) {
            sampledDownloadBytes += bytes;
            long nowMillis = System.currentTimeMillis();
            sample(nowMillis);
            // 空闲时不走令牌桶，也不做纳秒级休眠，和极速模式保持同样的下载路径。
            if (!competing) {
                nextPermitNanos = 0L;
                return;
            }
            long now = System.nanoTime();
            // 长时间没任务时不要把旧欠账带进下一次下载。
            if (nextPermitNanos == 0 || nextPermitNanos < now - 1_000_000_000L) nextPermitNanos = now;
            delay = Math.max(0L, nextPermitNanos - now);
            long cost = Math.max(1L, bytes * 1_000_000_000L / Math.max(1L, rate));
            nextPermitNanos = Math.max(nextPermitNanos, now) + cost;
        }
        if (delay > 0) LockSupport.parkNanos(delay);
    }

    /** 设置变化或队列清空后丢弃上一轮网络环境的判断。 */
    static synchronized void reset() {
        rate = MIN_SHARE_RATE;
        targetRate = MIN_SHARE_RATE;
        learnedCapacity = 0L;
        sampledDownloadBytes = 0L;
        backgroundAt = 0L;
        nextPermitNanos = 0L;
        sampleAt = 0L;
        lastLog = 0L;
        windowIndex = 0;
        windowCount = 0;
        quietWindows = 0;
        sourceSlowWindows = 0;
        countersReady = false;
        competing = false;
        sourceLimited = false;
        foregroundKnown = false;
        foreground = false;
        Arrays.fill(otherWindow, 0L);
        Arrays.fill(appWindow, 0L);
        Arrays.fill(downloadWindow, 0L);
    }

    static synchronized boolean isThrottling() {
        return Setting.isDownloadSmartMode() && competing;
    }

    private static void sample(long now) {
        if (sampleAt != 0 && now - sampleAt < SAMPLE_INTERVAL_MS) return;
        boolean currentForeground = App.isForeground();
        if (!foregroundKnown || foreground != currentForeground) {
            foregroundKnown = true;
            foreground = currentForeground;
            backgroundAt = foreground ? 0L : now;
            competing = false;
            sourceLimited = false;
            sourceSlowWindows = 0;
            nextPermitNanos = 0L;
            countersReady = false;
            clearDetectionWindow();
            if (now - lastLog >= SAMPLE_INTERVAL_MS) {
                log(now, 0L, 0L, 0L, foreground ? "进入前台，全速保护" : "进入后台，预热测速");
            }
        }
        long currentTotalRx = TrafficStats.getTotalRxBytes();
        long currentTotalTx = TrafficStats.getTotalTxBytes();
        long currentUidRx = TrafficStats.getUidRxBytes(Process.myUid());
        long currentUidTx = TrafficStats.getUidTxBytes(Process.myUid());
        boolean supported = currentTotalRx != TrafficStats.UNSUPPORTED
                && currentTotalTx != TrafficStats.UNSUPPORTED
                && currentUidRx != TrafficStats.UNSUPPORTED
                && currentUidTx != TrafficStats.UNSUPPORTED;
        if (!supported) {
            // 没有计数就无法判断其他 App；保持与极速相同，不凭空把用户锁在固定低速。
            countersReady = false;
            competing = false;
            nextPermitNanos = 0L;
            sampleAt = now;
            sampledDownloadBytes = 0L;
            if (now - lastLog >= LOG_INTERVAL_MS) log(now, 0L, 0L, 0L, "计数不可用，全速");
            return;
        }
        if (!countersReady || currentTotalRx < totalRx || currentTotalTx < totalTx
                || currentUidRx < uidRx || currentUidTx < uidTx) {
            countersReady = true;
            sampledDownloadBytes = 0L;
            remember(now, currentTotalRx, currentTotalTx, currentUidRx, currentUidTx);
            return;
        }

        long elapsed = Math.max(1L, now - sampleAt);
        long otherRx = Math.max(0L, (currentTotalRx - totalRx) - (currentUidRx - uidRx));
        long otherTx = Math.max(0L, (currentTotalTx - totalTx) - (currentUidTx - uidTx));
        long otherRate = (otherRx + otherTx) * 1000L / elapsed;
        long appBytes = Math.max(0L, currentUidRx - uidRx) + Math.max(0L, currentUidTx - uidTx);
        long appRate = appBytes * 1000L / elapsed;
        long downloadRate = sampledDownloadBytes * 1000L / elapsed;
        sampledDownloadBytes = 0L;
        pushWindow(otherRate, appRate, downloadRate);
        long averageOther = average(otherWindow);
        long averageApp = average(appWindow);
        long averageDownload = average(downloadWindow);
        int activeSamples = activeSamples();
        boolean changed = false;

        // 只从未限速阶段学习实际下载能力；限速后的低吞吐绝不能反过来压低目标。
        if (!competing && windowCount == WINDOW_SAMPLES) {
            learnedCapacity = Math.max(learnedCapacity, averageDownload);
        }

        // 前台始终全速，避免 127.0.0.1 内部中转流量被整机计数误认为其他应用。
        // 后台先完整预热六秒，让 CDN 和连接池爬到正常速度，再决定是否让出带宽。
        boolean warmedUp = backgroundAt > 0L && now - backgroundAt >= BACKGROUND_WARMUP_MS;
        boolean otherBusy = warmedUp && windowCount == WINDOW_SAMPLES
                && activeSamples >= 2 && averageOther >= ENTER_AVERAGE_RATE;
        if (foreground) {
            if (competing || sourceLimited) changed = true;
            competing = false;
            sourceLimited = false;
            quietWindows = 0;
            sourceSlowWindows = 0;
            nextPermitNanos = 0L;
        } else if (!competing && otherBusy) {
            targetRate = clamp(learnedCapacity * CAPACITY_SHARE_PERCENT / 100L,
                    MIN_SHARE_RATE, MAX_SHARE_RATE);
            // 当前下载自己都跑不到计划保留值，再套令牌桶没有意义，只会让抖动更严重。
            // 保持全速并持续观察；一旦链路恢复到目标以上，下个窗口会自动开始让速。
            if (averageDownload <= targetRate) {
                if (!sourceLimited) changed = true;
                sourceLimited = true;
                sourceSlowWindows = 0;
                nextPermitNanos = 0L;
            } else {
                competing = true;
                sourceLimited = false;
                quietWindows = 0;
                sourceSlowWindows = 0;
                nextPermitNanos = 0L;
                rate = targetRate;
                changed = true;
            }
        } else if (competing) {
            if (averageDownload < rate * SOURCE_RELEASE_PERCENT / 100L) sourceSlowWindows++;
            else sourceSlowWindows = 0;
            if (activeSamples == 0 && averageOther <= EXIT_AVERAGE_RATE) quietWindows++;
            else quietWindows = 0;
            if (sourceSlowWindows >= SOURCE_SLOW_WINDOWS) {
                competing = false;
                sourceLimited = true;
                nextPermitNanos = 0L;
                changed = true;
            } else if (quietWindows >= WINDOW_SAMPLES) {
                competing = false;
                sourceLimited = false;
                nextPermitNanos = 0L;
                changed = true;
            }
        } else if (sourceLimited && !otherBusy) {
            sourceLimited = false;
            sourceSlowWindows = 0;
            changed = true;
        }

        remember(now, currentTotalRx, currentTotalTx, currentUidRx, currentUidTx);
        if (changed || now - lastLog >= LOG_INTERVAL_MS) {
            String state;
            if (foreground) state = "前台保护，全速";
            else if (!warmedUp) state = "后台预热测速，全速";
            else if (competing) state = "后台持续竞争，让出带宽";
            else if (sourceLimited) state = "后台有竞争，但下载已自然低速，全速";
            else state = "后台网络空闲，全速";
            log(now, averageOther, averageApp, averageDownload, state);
        }
    }

    private static void pushWindow(long otherRate, long appRate, long downloadRate) {
        otherWindow[windowIndex] = otherRate;
        appWindow[windowIndex] = appRate;
        downloadWindow[windowIndex] = downloadRate;
        windowIndex = (windowIndex + 1) % WINDOW_SAMPLES;
        if (windowCount < WINDOW_SAMPLES) windowCount++;
    }

    private static void clearDetectionWindow() {
        windowIndex = 0;
        windowCount = 0;
        quietWindows = 0;
        sourceSlowWindows = 0;
        sampledDownloadBytes = 0L;
        Arrays.fill(otherWindow, 0L);
        Arrays.fill(appWindow, 0L);
        Arrays.fill(downloadWindow, 0L);
    }

    private static long average(long[] values) {
        if (windowCount == 0) return 0L;
        long total = 0L;
        for (int i = 0; i < windowCount; i++) total += values[i];
        return total / windowCount;
    }

    private static int activeSamples() {
        int count = 0;
        for (int i = 0; i < windowCount; i++) if (otherWindow[i] >= ACTIVE_SAMPLE_RATE) count++;
        return count;
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void remember(long now, long currentTotalRx, long currentTotalTx, long currentUidRx, long currentUidTx) {
        sampleAt = now;
        totalRx = currentTotalRx;
        totalTx = currentTotalTx;
        uidRx = currentUidRx;
        uidTx = currentUidTx;
    }

    private static void log(long now, long otherRate, long appRate, long downloadRate, String state) {
        lastLog = now;
        DownloadLog.d("智能调速 状态=%s 场景=%s 近2秒(其他=%s/s 本应用=%s/s 下载=%s/s) 学习带宽=%s/s 上限=%s/s 目标=%s/s",
                state, foreground ? "前台" : "后台", DownloadLog.size(otherRate), DownloadLog.size(appRate),
                DownloadLog.size(downloadRate), DownloadLog.size(learnedCapacity),
                competing ? DownloadLog.size(rate) : "不限",
                competing ? DownloadLog.size(targetRate) : "不限");
    }
}
