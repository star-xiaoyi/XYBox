package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.Douban;
import com.fongmi.android.tv.databinding.AdapterRecommendRankMiniBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecommendMiniRankAdapter extends RecyclerView.Adapter<RecommendMiniRankAdapter.ViewHolder> {

    private final RecommendAdapter.OnClickListener mListener;
    private final List<Douban.Item> mItems = new ArrayList<>();

    public RecommendMiniRankAdapter(RecommendAdapter.OnClickListener listener, List<Douban.Item> items) {
        mListener = listener;
        if (items != null) mItems.addAll(items.subList(0, Math.min(4, items.size())));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterRecommendRankMiniBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Douban.Item item = mItems.get(position);
        holder.binding.rank.setText(String.valueOf(position + 1));
        holder.binding.title.setText(item.getTitle());
        holder.binding.brief.setText(item.getRating() > 0
                ? String.format(Locale.getDefault(), holder.itemView.getContext().getString(R.string.recommend_score), item.getRating())
                : item.getBrief());
        holder.binding.image.setContentDescription(item.getTitle());
        ImgUtil.rect(item.getTitle(), item.getPic(), holder.binding.image);
        holder.binding.getRoot().setOnClickListener(view -> mListener.onItemClick(item));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterRecommendRankMiniBinding binding;

        ViewHolder(AdapterRecommendRankMiniBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
