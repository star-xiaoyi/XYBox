package com.fongmi.android.tv.ui.custom;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.appcompat.widget.AppCompatCheckBox;

public class CustomSwitch extends AppCompatCheckBox {
    
    private Paint trackPaint;
    private Paint thumbPaint;
    private RectF trackRect;
    private RectF thumbRect;
    
    private float thumbPosition = 0f; // 0 = 左边, 1 = 右边
    private int currentTrackColor;
    private int currentThumbColor;
    
    private static final int TRACK_COLOR_OFF = 0xFF555555; // 灰色
    private static final int TRACK_COLOR_ON = 0xFFFFEB3B;  // 黄色
    private static final int THUMB_COLOR_OFF = 0xFFFFFFFF; // 白色
    private static final int THUMB_COLOR_ON = 0xFF000000;  // 黑色
    
    private ValueAnimator animator;
    
    public CustomSwitch(Context context) {
        super(context);
        init();
    }
    
    public CustomSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public CustomSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // 隐藏默认的checkbox样式
        setButtonDrawable(null);
        
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        trackRect = new RectF();
        thumbRect = new RectF();
        
        currentTrackColor = TRACK_COLOR_OFF;
        currentThumbColor = THUMB_COLOR_OFF;
        
        // 监听状态变化
        setOnCheckedChangeListener((buttonView, isChecked) -> animateSwitch(isChecked));
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 固定尺寸：50dp × 30dp
        int width = (int) (50 * getResources().getDisplayMetrics().density);
        int height = (int) (30 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(width, height);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        float radius = height / 2f;
        
        // 绘制轨道
        trackRect.set(0, 0, width, height);
        trackPaint.setColor(currentTrackColor);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);
        
        // 计算小圆位置
        float thumbSize = height - 8 * getResources().getDisplayMetrics().density; // 22dp
        float padding = 4 * getResources().getDisplayMetrics().density;
        float thumbLeft = padding + thumbPosition * (width - thumbSize - 2 * padding);
        float thumbTop = padding;
        
        // 绘制小圆
        thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize);
        thumbPaint.setColor(currentThumbColor);
        canvas.drawOval(thumbRect, thumbPaint);
    }
    
    private void animateSwitch(boolean isChecked) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        
        float targetPosition = isChecked ? 1f : 0f;
        int targetTrackColor = isChecked ? TRACK_COLOR_ON : TRACK_COLOR_OFF;
        int targetThumbColor = isChecked ? THUMB_COLOR_ON : THUMB_COLOR_OFF;
        
        animator = ValueAnimator.ofFloat(thumbPosition, targetPosition);
        animator.setDuration(250); // 250ms动画时长
        
        final ArgbEvaluator colorEvaluator = new ArgbEvaluator();
        
        animator.addUpdateListener(animation -> {
            thumbPosition = (float) animation.getAnimatedValue();
            
            // 颜色渐变
            currentTrackColor = (int) colorEvaluator.evaluate(
                thumbPosition, TRACK_COLOR_OFF, TRACK_COLOR_ON
            );
            currentThumbColor = (int) colorEvaluator.evaluate(
                thumbPosition, THUMB_COLOR_OFF, THUMB_COLOR_ON
            );
            
            invalidate();
        });
        
        animator.start();
    }
    
    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        // 初始化时不播放动画
        if (!isAttachedToWindow()) {
            thumbPosition = checked ? 1f : 0f;
            currentTrackColor = checked ? TRACK_COLOR_ON : TRACK_COLOR_OFF;
            currentThumbColor = checked ? THUMB_COLOR_ON : THUMB_COLOR_OFF;
        }
    }
}

