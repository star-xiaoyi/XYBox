package com.fongmi.android.tv.ui.custom;

import android.text.Spannable;
import android.text.method.BaseMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * 只负责点开 ClickableSpan，不接管滚动。
 * 演职人员超出固定资料区时由外层 NestedScrollView 统一处理上下滑动，
 * 避免 TextView 自己滚动而与整块资料区的手势互相争抢。
 */
public class LinkMovement extends BaseMovementMethod {

    private static LinkMovement sInstance;

    public static MovementMethod getInstance() {
        if (sInstance == null) sInstance = new LinkMovement();
        return sInstance;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_DOWN) return false;
        if (widget.getLayout() == null) return false;
        int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
        int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();
        int line = widget.getLayout().getLineForVertical(y);
        // 点到行尾空白处不算命中，否则最后一行右边一大片都会触发最后一个人名
        if (x > widget.getLayout().getLineWidth(line)) return false;
        int offset = widget.getLayout().getOffsetForHorizontal(line, x);
        ClickableSpan[] links = buffer.getSpans(offset, offset, ClickableSpan.class);
        if (links.length == 0) return false;
        if (action == MotionEvent.ACTION_UP) links[0].onClick(widget);
        return true;
    }
}
