package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import com.fongmi.android.tv.R;

public class MarqueeLayout extends FrameLayout {

    private MarqueeTextView text;

    public MarqueeLayout(Context context) {
        super(context);
        initView();
    }

    public MarqueeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public MarqueeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        LayoutInflater.from(getContext()).inflate(R.layout.view_marquee, this);
        text = findViewById(R.id.text);
    }

    public void setText(String text) {
        this.text.setText(text);
    }

    public void setText(int resId) {
        this.text.setText(resId);
    }
} 