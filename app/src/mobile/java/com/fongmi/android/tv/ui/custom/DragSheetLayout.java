package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * 包住详情卡片滚动区的一层，负责"整张卡片拖出去"的手势。
 *
 * 竖屏：内容滚到顶时向下拖；横屏：向右拖。
 * 剧集、线路那些横向 RecyclerView 和类型标签自己会 requestDisallowInterceptTouchEvent，
 * 所以在它们身上滑动仍然是滚列表，不会被这里抢走。
 *
 * 之所以要在这一层拦截而不是给某个 View 挂 OnTouchListener：NestedScrollView
 * 会在 onInterceptTouchEvent 里把滑动抢走，挂在它内部的监听器收不到后续 MOVE，
 * 表现就是卡片抖一下就不动了。
 */
public class DragSheetLayout extends FrameLayout {

    private final int mSlop;
    private float mDownX;
    private float mDownY;

    public DragSheetLayout(Context context) {
        this(context, null);
    }

    public DragSheetLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private boolean land() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean atTop() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof NestedScrollView) return child.getScrollY() == 0;
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - mDownX;
                float dy = event.getY() - mDownY;
                boolean hit = land() ? dx > mSlop && dx > Math.abs(dy) : atTop() && dy > mSlop && dy > Math.abs(dx);
                if (hit) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // 拦截成功后事件会走到这里，交给 Activity 挂的 OnTouchListener；
        // 这里必须返回 true，否则后续 MOVE / UP 不会再送过来。
        return true;
    }
}
