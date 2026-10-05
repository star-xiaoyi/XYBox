package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.catvod.utils.Logger;

/** Source plugins provide media; the app owns the fixed playback controls. */
public class PlaybackActionLayout extends LinearLayout {
    private boolean inflated;
    public PlaybackActionLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override protected void onFinishInflate() { super.onFinishInflate(); inflated = true; }
    @Override public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (!inflated) { super.addView(child, index, params); return; }
        String label = child instanceof TextView ? ((TextView) child).getText().toString() : "";
        Logger.i("PlayerControls: ignored-added-control class=" + child.getClass().getSimpleName()
                + " label=" + label.replace('\n', ' ').substring(0, Math.min(40, label.length())));
    }
}
