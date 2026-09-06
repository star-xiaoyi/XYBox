package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * 降低下拉刷新灵敏度的 SwipeRefreshLayout。
 *
 * 默认的 SwipeRefreshLayout 只要检测到向下滑动就会拦截事件，
 * 导致用户在首页左右滑动切换分类时，轻微的纵向分量也会触发下拉刷新。
 * 这里在横向位移明显大于纵向位移时判定为翻页/横滑，主动放弃拦截，
 * 把事件交还给 ViewPager，避免误触发下拉。
 */
public class CustomSwipeRefreshLayout extends SwipeRefreshLayout {

    private final int mTouchSlop;
    private float mStartX;
    private float mStartY;
    private boolean mHorizontalDrag;
    private boolean mRefreshGestureBlocked;
    private View mRefreshGestureExclusionView;
    private final int[] mRefreshLocation = new int[2];
    private final int[] mExclusionLocation = new int[2];

    public CustomSwipeRefreshLayout(@NonNull Context context) {
        super(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public CustomSwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /** 从这个 View 内开始的整段手势只交给子 View，不允许转成下拉刷新。 */
    public void setRefreshGestureExclusionView(@Nullable View view) {
        mRefreshGestureExclusionView = view;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mRefreshGestureBlocked = isInsideRefreshGestureExclusion(event);
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mRefreshGestureBlocked = false;
        }
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mRefreshGestureBlocked) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mStartX = event.getX();
                mStartY = event.getY();
                mHorizontalDrag = false;
                break;
            case MotionEvent.ACTION_MOVE:
                // 已经判定为横滑则持续放弃拦截
                if (mHorizontalDrag) return false;
                float dx = Math.abs(event.getX() - mStartX);
                float dy = Math.abs(event.getY() - mStartY);
                // 横向位移明显占优且超过阈值，认为是左右滑动，不触发下拉
                if (dx > dy && dx > mTouchSlop) {
                    mHorizontalDrag = true;
                    return false;
                }
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mRefreshGestureBlocked) return false;
        return super.onTouchEvent(event);
    }

    private boolean isInsideRefreshGestureExclusion(MotionEvent event) {
        View exclusion = mRefreshGestureExclusionView;
        if (exclusion == null || !exclusion.isShown() || exclusion.getWidth() == 0 || exclusion.getHeight() == 0) return false;
        // MotionEvent 是刷新容器坐标，统一换成窗口坐标后再与目标 View 比较。
        getLocationInWindow(mRefreshLocation);
        exclusion.getLocationInWindow(mExclusionLocation);
        float windowX = mRefreshLocation[0] + event.getX();
        float windowY = mRefreshLocation[1] + event.getY();
        return windowX >= mExclusionLocation[0]
                && windowX < mExclusionLocation[0] + exclusion.getWidth()
                && windowY >= mExclusionLocation[1]
                && windowY < mExclusionLocation[1] + exclusion.getHeight();
    }
}
