package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;

/** Native counterpart of DialogCloseButton: 32 dp circular fill, 44 dp hit area. */
public final class DialogCloseButtonView extends FrameLayout {
    public DialogCloseButtonView(Context context) {
        super(context);
        setContentDescription("关闭弹窗");
        setClickable(true); setFocusable(true);
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_action_close);
        icon.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.text_primary)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int padding = ResUtil.dp2px(8);
        icon.setPadding(padding, padding, padding, padding);
        GradientDrawable fill = new GradientDrawable(); fill.setShape(GradientDrawable.OVAL);
        fill.setColor(context.getColor(R.color.surface_secondary));
        icon.setBackground(fill);
        addView(icon, new LayoutParams(ResUtil.dp2px(32), ResUtil.dp2px(32), Gravity.CENTER));
        GradientDrawable mask = new GradientDrawable(); mask.setShape(GradientDrawable.OVAL);
        mask.setColor(android.graphics.Color.WHITE);
        setForeground(new RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.separator)), null, mask));
    }
}
