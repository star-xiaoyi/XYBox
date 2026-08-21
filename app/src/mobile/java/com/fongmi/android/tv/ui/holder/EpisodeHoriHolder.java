package com.fongmi.android.tv.ui.holder;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterEpisodeHoriBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.base.BaseEpisodeHolder;
import com.fongmi.android.tv.utils.ResUtil;

public class EpisodeHoriHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeHoriBinding binding;

    public EpisodeHoriHolder(@NonNull AdapterEpisodeHoriBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    @Override
    public void initView(Episode item) {
        binding.text.setMaxEms(Product.getEms());
        binding.text.setSelected(item.isSelected());
        binding.text.setActivated(item.isActivated());
        binding.text.setText(item.getDesc().concat(item.getName()));
        setCached(item.isCached());
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }

    /** 已缓存的集在胶囊右侧挂一个小角标，着色跟着胶囊文字走。 */
    private void setCached(boolean cached) {
        binding.text.setCompoundDrawablePadding(cached ? ResUtil.dp2px(4) : 0);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(binding.text, null, null, cached ? ContextCompat.getDrawable(binding.text.getContext(), R.drawable.ic_episode_cached) : null, null);
        if (cached) TextViewCompat.setCompoundDrawableTintList(binding.text, ContextCompat.getColorStateList(binding.text.getContext(), R.color.chip_text));
    }
}
