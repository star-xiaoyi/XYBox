package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;

/**
 * 固定胶囊轮廓的竖向音量/亮度指示器。
 *
 * 进度区域从底部向上填充，顶部始终是一条水平直线；仅最外层轮廓保留圆角，
 * 避免 LinearProgressIndicator 的圆头和终点圆点让进度看起来像另一根胶囊。
 */
public class VerticalLevelIndicator extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint levelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path capsule = new Path();
    private int progress;

    public VerticalLevelIndicator(Context context) {
        this(context, null);
    }

    public VerticalLevelIndicator(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VerticalLevelIndicator(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.black_60));
        levelPaint.setColor(ContextCompat.getColor(context, R.color.white_90));
    }

    public void setProgress(int progress) {
        int value = Math.max(0, Math.min(100, progress));
        if (this.progress == value) return;
        this.progress = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float radius = width / 2f;
        bounds.set(0, 0, width, height);
        canvas.drawRoundRect(bounds, radius, radius, trackPaint);

        capsule.reset();
        capsule.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(capsule);
        float levelTop = height * (1f - progress / 100f);
        canvas.drawRect(0, levelTop, width, height, levelPaint);
        canvas.restoreToCount(save);
    }
}
