package com.fongmi.android.tv.ui.custom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * 只播放一遍的慢速片名滚动。
 *
 * 进入详情页时自动播放一次，播放完停顿后回到开头；单击时可从头重播。
 * 不使用系统无限 marquee，避免长片名一直移动而干扰阅读。
 */
public class OneShotMarqueeTextView extends AppCompatTextView {

    private static final long AUTO_DELAY = 700L;
    private static final long END_PAUSE = 650L;
    private static final float SPEED_DP_PER_SECOND = 18f;

    private final Runnable mStart = this::startScroll;
    private final Runnable mReset = () -> scrollTo(0, 0);
    private ValueAnimator mAnimator;

    public OneShotMarqueeTextView(Context context) {
        this(context, null);
    }

    public OneShotMarqueeTextView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public OneShotMarqueeTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSingleLine(true);
        setEllipsize(null);
        setHorizontallyScrolling(true);
    }

    /** 进入详情页时稍作停顿再播放，先让用户看清标题开头。 */
    public void playOnce() {
        schedule(AUTO_DELAY);
    }

    /** 用户单击后立即从头重播。 */
    public void replayOnce() {
        schedule(0L);
    }

    private void schedule(long delay) {
        removeCallbacks(mStart);
        removeCallbacks(mReset);
        cancelAnimator();
        scrollTo(0, 0);
        postDelayed(mStart, delay);
    }

    private void startScroll() {
        Layout layout = getLayout();
        if (layout == null || layout.getLineCount() == 0 || getWidth() == 0) return;
        int viewport = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int distance = Math.max(0, (int) Math.ceil(layout.getLineWidth(0) - viewport));
        if (distance == 0) return;

        float pixelsPerSecond = SPEED_DP_PER_SECOND * getResources().getDisplayMetrics().density;
        long duration = Math.max(2800L, (long) (distance * 1000f / pixelsPerSecond));
        mAnimator = ValueAnimator.ofInt(0, distance);
        mAnimator.setDuration(duration);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(animation -> scrollTo((int) animation.getAnimatedValue(), 0));
        mAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) postDelayed(mReset, END_PAUSE);
            }
        });
        mAnimator.start();
    }

    private void cancelAnimator() {
        if (mAnimator == null) return;
        mAnimator.cancel();
        mAnimator = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mStart);
        removeCallbacks(mReset);
        cancelAnimator();
        scrollTo(0, 0);
        super.onDetachedFromWindow();
    }
}
