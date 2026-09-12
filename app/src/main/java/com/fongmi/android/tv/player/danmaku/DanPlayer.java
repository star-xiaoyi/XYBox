package com.fongmi.android.tv.player.danmaku;

import android.os.SystemClock;
import android.view.View;

import androidx.media3.common.Player;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Logger;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;

public class DanPlayer implements DrawHandler.Callback {

    private static final float NORMAL_SPEED_FACTOR = 1.2f;
    private static final int DEFAULT_TEXT_HEIGHT_DP = 20;
    private static final int UNLIMITED_LINES = -1;
    private static final int UNKNOWN_LINES = -2;
    private static final byte UPDATE_METHOD_FIXED_HANDLER = 2;
    private static final long DEFAULT_FRAME_INTERVAL_MS = 16;
    private static final long DELAYED_FRAME_INTERVAL_MS = 25;
    private static final long STALLED_FRAME_INTERVAL_MS = 50;
    private final ExecutorService executor;
    private final DanmakuContext context;
    private final AtomicBoolean driftSamplePending;
    private final AtomicInteger renderLogBudget;
    private volatile Parser parser;
    private volatile String sourceUrl = "";
    private IDanmakuView view;
    private Players player;
    private Sync sync;
    private float textScale;
    private float opacity;
    private float speedFactor;
    private int areaPercent;
    private int appliedMaxLines = UNKNOWN_LINES;
    private long frameWindowStart;
    private long previousFrameTime;
    private long frameIntervalTotal;
    private long maxFrameInterval;
    private long lastDriftSampleRequest;
    private int frameIntervalCount;
    private int delayedFrameCount;
    private int stalledFrameCount;
    private volatile boolean timerDriftReady;
    private volatile long timerDrift;

    public DanPlayer() {
        context = DanmakuContext.create();
        executor = Executors.newCachedThreadPool();
        driftSamplePending = new AtomicBoolean();
        renderLogBudget = new AtomicInteger();
        // DFM 默认跟随 Choreographer。部分手机播放视频时会在 60/120Hz 间动态切换，
        // 内部时钟的最小步长无法同步切换，最终表现为一阵快、一阵慢。
        // 固定由高优先级 Handler 以 16ms 调度，速度只跟真实经过时间前进。
        context.updateMethod = UPDATE_METHOD_FIXED_HANDLER;
        context.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3).setDanmakuMargin(ResUtil.dp2px(8));
        setStyle(Setting.getDanmakuSize(), Setting.getDanmakuOpacity(), Setting.getDanmakuSpeed(), Setting.getDanmakuArea());
    }

    public void setView(IDanmakuView view) {
        view.setDrawingThreadType(IDanmakuView.THREAD_TYPE_HIGH_PRIORITY);
        view.enableDanmakuDrawingCache(true);
        view.setCallback(this);
        View renderView = view.getView();
        this.view = view;
        renderView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left == oldRight - oldLeft && bottom - top == oldBottom - oldTop) return;
            Parser current = parser;
            if (current != null) current.updateStyle(right - left, textScale, speedFactor);
            applyMaximumLines("layout");
        });
        renderView.post(() -> {
            applyMaximumLines("view");
            Logger.i("DanmakuState: renderer=" + renderView.getClass().getSimpleName()
                    + " rendererThread=high_priority drawingCache=true hardwareAccelerated=" + view.isHardwareAccelerated());
        });
    }

    public void setPlayer(Players player) {
        this.player = player;
        context.setDanmakuSync(sync = new Sync());
    }

    /** 只能由播放器主线程调用；DFM 绘制线程永远不直接访问 Media3 Player。 */
    public void updatePlayerSnapshot(long position, boolean playing, float speed) {
        if (sync != null) sync.update(position, playing, speed);
    }

    private boolean isDanmakuPrepared() {
        return view != null && view.isPrepared();
    }

    public void seekTo(long time) {
        executor.execute(() -> {
            if (isDanmakuPrepared()) view.seekTo(time);
            if (isDanmakuPrepared()) view.hide();
        });
    }

    public void play() {
        executor.execute(() -> {
            if (isDanmakuPrepared()) view.resume();
        });
    }

    public void pause() {
        executor.execute(() -> {
            if (isDanmakuPrepared()) view.pause();
        });
    }

    public void stop() {
        executor.execute(() -> {
            if (isDanmakuPrepared()) view.stop();
        });
    }

    public void release() {
        executor.execute(() -> {
            if (isDanmakuPrepared()) view.release();
        });
    }

    public void setDanmaku(Danmaku item) {
        String nextUrl = item.getUrl();
        if (nextUrl.equals(sourceUrl)) {
            Logger.i("DanmakuSource: reuse url=" + nextUrl);
            return;
        }
        sourceUrl = nextUrl;
        executor.execute(() -> {
            if (!nextUrl.equals(sourceUrl)) return;
            view.release();
            parser = null;
            renderLogBudget.set(3);
            if (item.isEmpty()) {
                Logger.i("DanmakuSource: cleared");
                return;
            }
            Logger.i("DanmakuSource: loading url=" + item.getUrl());
            Loader loader = new Loader(item);
            if (loader.getDataSource() == null) {
                if (nextUrl.equals(sourceUrl)) sourceUrl = "";
                Logger.i("DanmakuSource: failed url=" + item.getUrl());
                return;
            }
            if (!nextUrl.equals(sourceUrl)) return;
            Parser next = new Parser(view.getWidth(), textScale, speedFactor);
            parser = next;
            view.prepare(next.load(loader.getDataSource()), context);
        });
    }

    public void setTextSize(float size) {
        setStyle(size, opacity, speedFactor, areaPercent);
    }

    public void setStyle(float size, float opacity, float speed, int area) {
        float nextTextScale = Math.max(0.1f, size);
        float nextOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
        float nextSpeedFactor = Math.max(0.1f, speed);
        int nextAreaPercent = Math.max(1, Math.min(100, area));
        boolean textScaleChanged = Float.compare(this.textScale, nextTextScale) != 0;
        boolean speedChanged = Float.compare(this.speedFactor, nextSpeedFactor) != 0;

        this.textScale = nextTextScale;
        this.opacity = nextOpacity;
        this.speedFactor = nextSpeedFactor;
        this.areaPercent = nextAreaPercent;
        synchronized (context) {
            context.setScrollSpeedFactor(this.speedFactor)
                    .setDanmakuTransparency(this.opacity)
                    .setScaleTextSize(this.textScale);
        }
        Parser current = parser;
        // 透明度和显示区域不会改变滚动路程；不要因此遍历、重算整份弹幕。
        if (current != null && (textScaleChanged || speedChanged)) {
            current.updateStyle(getViewWidth(), this.textScale, this.speedFactor);
        }
        int maxLines = applyMaximumLines("settings");
        renderLogBudget.set(3);
        logConfig("settings", maxLines);
    }

    private int applyMaximumLines(String reason) {
        // 100% 必须交给弹幕 View 自己使用完整高度；固定成某个行数会让实际区域随字号变化。
        if (areaPercent >= 100) {
            if (appliedMaxLines != UNLIMITED_LINES) {
                context.setMaximumLines(null);
                appliedMaxLines = UNLIMITED_LINES;
            }
            if (!"settings".equals(reason)) logConfig(reason, UNLIMITED_LINES);
            return UNLIMITED_LINES;
        }
        int height = getViewHeight();
        if (height <= 0) return appliedMaxLines;
        Parser current = parser;
        float baseTextHeight = current == null ? 0 : current.getBaseTextHeight();
        if (baseTextHeight <= 0) baseTextHeight = ResUtil.dp2px(DEFAULT_TEXT_HEIGHT_DP);
        // A row occupies both its measured text height and the configured gap to the next row.
        // Ignoring the gap makes 60% calculate almost as many rows as the full screen.
        int lineHeight = Math.max(1, Math.round(baseTextHeight * textScale));
        int lineMargin = Math.max(0, context.margin);
        int areaHeight = Math.max(1, Math.round(height * areaPercent / 100.0f));
        int maxLines = Math.max(1, (areaHeight + lineMargin) / (lineHeight + lineMargin));
        if (appliedMaxLines != maxLines) {
            setMaximumLines(maxLines);
            appliedMaxLines = maxLines;
        }
        if (!"settings".equals(reason)) logConfig(reason, maxLines);
        return maxLines;
    }

    private void setMaximumLines(int maxLines) {
        HashMap<Integer, Integer> lines = new HashMap<>();
        lines.put(BaseDanmaku.TYPE_FIX_TOP, maxLines);
        lines.put(BaseDanmaku.TYPE_SCROLL_RL, maxLines);
        lines.put(BaseDanmaku.TYPE_SCROLL_LR, maxLines);
        lines.put(BaseDanmaku.TYPE_FIX_BOTTOM, maxLines);
        context.setMaximumLines(lines);
    }

    private int getViewWidth() {
        return view == null ? 0 : view.getWidth();
    }

    private int getViewHeight() {
        return view == null ? 0 : view.getHeight();
    }

    private void logConfig(String reason, int maxLines) {
        String lines = formatLines(maxLines);
        int lineHeight = getLineHeight();
        int lineMargin = Math.max(0, context.margin);
        int coveredHeight = maxLines < 0 ? getViewHeight()
                : maxLines * lineHeight + Math.max(0, maxLines - 1) * lineMargin;
        Logger.i(String.format(Locale.US,
                "DanmakuConfig: reason=%s size=%.2f opacity=%.2f speed=%.2fx durationFactor=%.3f area=%d%% view=%dx%d maxLines=%s lineHeightPx=%d lineMarginPx=%d coveredHeightPx=%d",
                reason, textScale, opacity, NORMAL_SPEED_FACTOR / speedFactor, speedFactor,
                areaPercent, getViewWidth(), getViewHeight(), lines, lineHeight, lineMargin, coveredHeight));
    }

    private int getLineHeight() {
        Parser current = parser;
        float baseTextHeight = current == null ? 0 : current.getBaseTextHeight();
        if (baseTextHeight <= 0) baseTextHeight = ResUtil.dp2px(DEFAULT_TEXT_HEIGHT_DP);
        return Math.max(1, Math.round(baseTextHeight * textScale));
    }

    private String formatLines(int lines) {
        return lines == UNLIMITED_LINES ? "unlimited" : lines == UNKNOWN_LINES ? "pending" : String.valueOf(lines);
    }

    public void check(int state) {
        if (state == Player.STATE_BUFFERING) pause();
        else if (state == Player.STATE_READY) ready();
    }

    /**
     * 主播放器 seek 后也会重新进入 READY，但弹幕本身并没有重新 prepare。
     * 这里只恢复已经定位好的弹幕，不能再走 prepared()/start(position)，否则会把源里的
     * “弹幕加载成功”等开场提示当成新一轮弹幕再次播放。
     */
    private void ready() {
        App.post(() -> {
            boolean playing = player.isPlaying();
            executor.execute(() -> {
                if (!isDanmakuPrepared()) return;
                if (playing) view.resume();
                else view.pause();
                view.show();
            });
        });
    }

    @Override
    public void prepared() {
        App.post(() -> {
            logFrameScheduler();
            boolean playing = player.isPlaying();
            long position = player.getPosition();
            int maxLines = applyMaximumLines("prepared");
            Parser current = parser;
            Logger.i("DanmakuState: prepared count=" + (current == null ? 0 : current.getParsedCount())
                    + " positionMs=" + position + " playing=" + playing + " maxLines="
                    + (maxLines == UNLIMITED_LINES ? "unlimited" : maxLines));
            executor.execute(() -> {
                if (!isDanmakuPrepared()) return;
                // 缓冲期间也要先把弹幕时钟定位到当前视频位置。旧逻辑直接 pause，
                // 会让弹幕停在 0 秒，播放恢复后再突然跳到续播位置。
                view.start(position);
                if (!playing) view.pause();
                view.show();
            });
        });
    }

    private void logFrameScheduler() {
        View renderView = view == null ? null : view.getView();
        float refreshRate = renderView == null || renderView.getDisplay() == null
                ? 60.0f : renderView.getDisplay().getRefreshRate();
        Logger.i(String.format(Locale.US,
                "DanmakuFrame: scheduler=fixed_handler displayRefreshRate=%.2f configuredIntervalMs=%d",
                refreshRate, DEFAULT_FRAME_INTERVAL_MS));
    }

    @Override
    public void updateTimer(DanmakuTimer danmakuTimer) {
        long now = SystemClock.uptimeMillis();
        requestTimerDriftSample(now, danmakuTimer.currMillisecond);
        if (frameWindowStart == 0 || (previousFrameTime > 0 && now - previousFrameTime >= 1000)) {
            frameWindowStart = now;
            frameIntervalTotal = 0;
            maxFrameInterval = 0;
            frameIntervalCount = 0;
            delayedFrameCount = 0;
            stalledFrameCount = 0;
        } else if (previousFrameTime > 0) {
            long interval = now - previousFrameTime;
            frameIntervalTotal += interval;
            maxFrameInterval = Math.max(maxFrameInterval, interval);
            frameIntervalCount++;
            if (interval >= DELAYED_FRAME_INTERVAL_MS) delayedFrameCount++;
            if (interval >= STALLED_FRAME_INTERVAL_MS) stalledFrameCount++;
        }
        previousFrameTime = now;
        long elapsed = now - frameWindowStart;
        if (elapsed < 5000 || frameIntervalCount == 0) return;
        Logger.i(String.format(Locale.US,
                "DanmakuFrame: callbackFps=%.1f averageIntervalMs=%.1f maxIntervalMs=%d delayedFrames=%d stalledFrames=%d timerDriftMs=%s",
                frameIntervalCount * 1000.0f / elapsed,
                frameIntervalTotal / (float) frameIntervalCount,
                maxFrameInterval, delayedFrameCount, stalledFrameCount,
                timerDriftReady ? String.valueOf(timerDrift) : "pending"));
        frameWindowStart = now;
        frameIntervalTotal = 0;
        maxFrameInterval = 0;
        frameIntervalCount = 0;
        delayedFrameCount = 0;
        stalledFrameCount = 0;
    }

    private void requestTimerDriftSample(long now, long danmakuPosition) {
        if (now - lastDriftSampleRequest < 1000 || !driftSamplePending.compareAndSet(false, true)) return;
        lastDriftSampleRequest = now;
        App.post(() -> {
            try {
                if (player == null) return;
                long playerPosition = player.getPosition();
                if (playerPosition < 0) return;
                updatePlayerSnapshot(playerPosition, player.isPlaying(), player.getSpeed());
                timerDrift = playerPosition - danmakuPosition;
                timerDriftReady = true;
            } finally {
                driftSamplePending.set(false);
            }
        });
    }

    @Override
    public void danmakuShown(BaseDanmaku baseDanmaku) {
        if (baseDanmaku.getType() != BaseDanmaku.TYPE_SCROLL_RL && baseDanmaku.getType() != BaseDanmaku.TYPE_SCROLL_LR) return;
        int budget;
        do {
            budget = renderLogBudget.get();
            if (budget <= 0) return;
        } while (!renderLogBudget.compareAndSet(budget, budget - 1));
        float travel = getViewWidth() + baseDanmaku.paintWidth;
        float pixelsPerSecond = baseDanmaku.getDuration() <= 0 ? 0 : travel * 1000.0f / baseDanmaku.getDuration();
        Logger.i(String.format(Locale.US,
                "DanmakuRender: index=%d type=%d textWidthPx=%.1f durationMs=%d averageSpeedPxPerSec=%.1f top=%.1f bottom=%.1f view=%dx%d",
                baseDanmaku.index, baseDanmaku.getType(), baseDanmaku.paintWidth, baseDanmaku.getDuration(),
                pixelsPerSecond, baseDanmaku.getTop(), baseDanmaku.getBottom(), getViewWidth(), getViewHeight()));
    }

    @Override
    public void drawingFinished() {
    }
}
