package com.fongmi.android.tv.ui.base;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;

public abstract class BaseEpisodeHolder extends RecyclerView.ViewHolder {

    public BaseEpisodeHolder(@NonNull View itemView) {
        super(itemView);
    }

    public abstract void initView(Episode item);

    protected void bindCacheMark(TextView text, Episode episode) {
        boolean cached = episode.isCached();
        text.setCompoundDrawablePadding(cached ? ResUtil.dp2px(4) : 0);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(text, null, null,
                cached ? ContextCompat.getDrawable(text.getContext(), R.drawable.ic_episode_cached) : null, null);
        if (cached) TextViewCompat.setCompoundDrawableTintList(text, ContextCompat.getColorStateList(text.getContext(), R.color.chip_text));
        text.setContentDescription(episode.getName() + (cached ? "，已缓存，可离线播放" : ""));
    }
}
