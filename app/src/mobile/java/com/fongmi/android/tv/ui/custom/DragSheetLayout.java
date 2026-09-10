package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * 包住详情卡片滚动区的一层，负责"整张卡片拖出去"的手势。
 *
 * 竖屏：内容滚到顶时向下拖；横屏：向右拖。
 *
 * 关键点是横屏下线路 / 剧集 / 站点资源这些横向列表：父容器的
 * onInterceptTouchEvent 比子 view 先收到 MOVE，一到 slop 就把事件抢走了，
 * RecyclerView 根本来不及调 requestDisallowInterceptTouchEvent。
 * 所以改成按落点判断——手指按在能横向滚动的控件上，这一整轮手势都不拦截。
 */
public class DragSheetLayout extends FrameLayout {

    private final int mSlop;
    private float mDownX;
    private float mDownY;
    private boolean mBlocked;

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

    /** 落点下面有没有能横向滚动的控件（横向 RecyclerView、HorizontalScrollView 等） */
    private boolean onScroller(View view, float x, float y) {
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != VISIBLE) continue;
            float cx = x - child.getLeft() + group.getScrollX();
            float cy = y - child.getTop() + group.getScrollY();
            if (cx < 0 || cy < 0 || cx > child.getWidth() || cy > child.getHeight()) continue;
            if (child.canScrollHorizontally(1) || child.canScrollHorizontally(-1)) return true;
            if (onScroller(child, cx, cy)) return true;
        }
        return false;
    }

    /**
     * 落点所在的纵向滚动区能否继续向上滚。
     * 主滚动区被固定资料区包在更深的布局层级里，不能再只检查唯一的直属子 View；
     * 否则手指在演职人员及其下方内容回滚时，会被误判成拖动整张详情卡。
     */
    private boolean canScrollUp(View view, float x, float y) {
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != VISIBLE) continue;
            float cx = x - child.getLeft() + group.getScrollX();
            float cy = y - child.getTop() + group.getScrollY();
            if (cx < 0 || cy < 0 || cx > child.getWidth() || cy > child.getHeight()) continue;
            if (child instanceof NestedScrollView && child.canScrollVertically(-1)) return true;
            if (canScrollUp(child, cx, cy)) return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                mBlocked = land() && onScroller(this, mDownX, mDownY);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mBlocked) return false;
                float dx = event.getX() - mDownX;
                float dy = event.getY() - mDownY;
                boolean hit = land() ? dx > mSlop && dx > Math.abs(dy) : !canScrollUp(this, mDownX, mDownY) && dy > mSlop && dy > Math.abs(dx);
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
