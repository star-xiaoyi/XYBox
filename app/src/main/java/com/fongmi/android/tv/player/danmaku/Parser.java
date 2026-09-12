package com.fongmi.android.tv.player.danmaku;

import com.github.catvod.utils.Logger;

import com.fongmi.android.tv.bean.DanmakuData;
import com.fongmi.android.tv.utils.ResUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.Duration;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.android.AndroidFileSource;
import master.flame.danmaku.danmaku.util.DanmakuUtils;

public class Parser extends BaseDanmakuParser {

    private static final Pattern XML = Pattern.compile("p=\"([^\"]+)\"[^>]*>([^<]+)<");
    private static final Pattern TXT = Pattern.compile("\\[(.*?)\\](.*)");
    private static final float MIN_FACTOR = 0.1f;
    // 设置中 1.0x 对应 durationFactor=1.2，因此这里用 120dp/s 得到实际 100dp/s。
    private static final int NOMINAL_SCROLL_SPEED_DP_PER_SECOND = 120;
    private final Object durationLock = new Object();
    private final List<ScrollItem> scrollItems = new ArrayList<>();
    private int viewportWidth;
    private float textScale;
    private float speedFactor;
    private volatile float baseTextHeight;
    private volatile int parsedCount;

    public Parser(int viewportWidth, float textScale, float speedFactor) {
        this.viewportWidth = viewportWidth;
        this.textScale = Math.max(MIN_FACTOR, textScale);
        this.speedFactor = Math.max(MIN_FACTOR, speedFactor);
    }

    @Override
    public Danmakus parse() {
        String line;
        Pattern pattern = null;
        if (mDataSource == null) return null;
        List<DanmakuData> items = new ArrayList<>();
        int invalidCount = 0;
        AndroidFileSource source = (AndroidFileSource) mDataSource;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(source.data()))) {
            while ((line = br.readLine()) != null) {
                if (pattern == null) pattern = line.startsWith("<") ? XML : TXT;
                Matcher matcher = pattern.matcher(line);
                while (matcher.find() && matcher.groupCount() == 2) {
                    try {
                        items.add(new DanmakuData(matcher, mDispDensity));
                    } catch (Exception e) {
                        invalidCount++;
                        Logger.e("Error", e);
                    }
                }
            }
            Danmakus result = new Danmakus(IDanmakus.ST_BY_TIME);
            float textHeightSum = 0;
            int measuredCount = 0;
            for (int i = 0; i < items.size(); i++) {
                BaseDanmaku item;
                synchronized (mContext) {
                    item = mContext.mDanmakuFactory.createDanmaku(items.get(i).getType(), mContext);
                }
                if (item == null) {
                    invalidCount++;
                    continue;
                }
                if (item.getType() == BaseDanmaku.TYPE_SPECIAL) continue;
                DanmakuUtils.fillText(item, items.get(i).getText());
                item.textShadowColor = items.get(i).getShadow();
                item.textColor = items.get(i).getColor();
                item.flags = mContext.mGlobalFlagValues;
                item.textSize = items.get(i).getSize();
                item.setTime(items.get(i).getTime());
                item.setTimer(mTimer);
                item.index = i;
                if (mDisp != null) {
                    item.measure(mDisp, true);
                    float currentScale = Math.max(MIN_FACTOR, mContext.scaleTextSize);
                    if (item.paintHeight > 0) {
                        textHeightSum += item.paintHeight / currentScale;
                        measuredCount++;
                    }
                }
                if (isScrolling(item)) stabilizeDuration(item);
                synchronized (result.obtainSynchronizer()) {
                    result.addItem(item);
                }
            }
            parsedCount = result.size();
            baseTextHeight = measuredCount == 0 ? 0 : textHeightSum / measuredCount;
            synchronized (durationLock) {
                updateDurationsLocked();
            }
            Logger.i(String.format(Locale.US,
                    "DanmakuLoad: parsed=%d input=%d invalid=%d scrolling=%d viewport=%dx%d baseTextHeightPx=%.1f",
                    parsedCount, items.size(), invalidCount, getScrollItemCount(), mDispWidth, mDispHeight, baseTextHeight));
            return result;
        } catch (Exception e) {
            Logger.e("Error", e);
            Logger.i("DanmakuLoad: failed error=" + e.getClass().getSimpleName());
            return null;
        }
    }

    public void updateStyle(int viewportWidth, float textScale, float speedFactor) {
        synchronized (durationLock) {
            int nextViewportWidth = viewportWidth > 0 ? viewportWidth : this.viewportWidth;
            float nextTextScale = Math.max(MIN_FACTOR, textScale);
            float nextSpeedFactor = Math.max(MIN_FACTOR, speedFactor);
            boolean geometryChanged = this.viewportWidth != nextViewportWidth
                    || Float.compare(this.textScale, nextTextScale) != 0;
            boolean speedChanged = Float.compare(this.speedFactor, nextSpeedFactor) != 0;
            if (!geometryChanged && !speedChanged) return;
            this.viewportWidth = nextViewportWidth;
            this.textScale = nextTextScale;
            this.speedFactor = nextSpeedFactor;
            if (geometryChanged) updateDurationsLocked(true);
            else updateSpeedFactorLocked();
        }
    }

    public float getBaseTextHeight() {
        return baseTextHeight;
    }

    public int getParsedCount() {
        return parsedCount;
    }

    private void stabilizeDuration(BaseDanmaku item) {
        float currentScale = Math.max(MIN_FACTOR, mContext.scaleTextSize);
        float widthAtScaleOne = item.paintWidth / currentScale;
        // DFM 默认所有滚动弹幕共用同一时长，长文字会因此移动得更快。每条弹幕改用
        // 与“画布宽度 + 文字宽度”成正比的独立时长，才能得到稳定的横移速度。
        Duration duration = new Duration(1);
        item.setDuration(duration);
        synchronized (durationLock) {
            ScrollItem scrollItem = new ScrollItem(duration, widthAtScaleOne);
            scrollItems.add(scrollItem);
            updateDurationLocked(scrollItem);
        }
        if (mDisp != null) item.measure(mDisp, true);
    }

    private void updateDurationsLocked() {
        updateDurationsLocked(true);
    }

    private void updateDurationsLocked(boolean requestRemeasure) {
        long maxDuration = 0;
        for (ScrollItem item : scrollItems) {
            updateDurationLocked(item);
            maxDuration = Math.max(maxDuration, item.duration.value);
        }
        updateMaximumDurationLocked(maxDuration);
        if (requestRemeasure && mContext != null) mContext.mGlobalFlagValues.updateMeasureFlag();
    }

    private void updateSpeedFactorLocked() {
        long maxDuration = 0;
        for (ScrollItem item : scrollItems) {
            item.duration.setFactor(speedFactor);
            maxDuration = Math.max(maxDuration, item.duration.value);
        }
        updateMaximumDurationLocked(maxDuration);
    }

    private void updateMaximumDurationLocked(long maxDuration) {
        if (mContext == null || maxDuration <= 0) return;
        mContext.mDanmakuFactory.updateMaxDanmakuDuration();
        mContext.mDanmakuFactory.MAX_DANMAKU_DURATION = Math.max(
                mContext.mDanmakuFactory.MAX_DANMAKU_DURATION, maxDuration);
    }

    private void updateDurationLocked(ScrollItem item) {
        int width = Math.max(1, viewportWidth > 0 ? viewportWidth : mDispWidth);
        float textWidth = Math.max(0, item.widthAtScaleOne * textScale);
        float pixelsPerSecond = Math.max(1, ResUtil.dp2px(NOMINAL_SCROLL_SPEED_DP_PER_SECOND));
        long baseDuration = Math.max(1, Math.round((width + textWidth) * 1000.0f / pixelsPerSecond));
        item.duration.setValue(baseDuration);
        item.duration.setFactor(speedFactor);
    }

    private int getScrollItemCount() {
        synchronized (durationLock) {
            return scrollItems.size();
        }
    }

    private static boolean isScrolling(BaseDanmaku item) {
        return item.getType() == BaseDanmaku.TYPE_SCROLL_RL || item.getType() == BaseDanmaku.TYPE_SCROLL_LR;
    }

    private static final class ScrollItem {

        private final Duration duration;
        private final float widthAtScaleOne;

        private ScrollItem(Duration duration, float widthAtScaleOne) {
            this.duration = duration;
            this.widthAtScaleOne = widthAtScaleOne;
        }
    }
}
