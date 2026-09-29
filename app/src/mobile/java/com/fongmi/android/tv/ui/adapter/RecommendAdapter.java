package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.Douban;
import com.fongmi.android.tv.databinding.AdapterRecommendBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class RecommendAdapter extends RecyclerView.Adapter<RecommendAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Douban.Item> mItems;
    private final boolean mShowPositionRank;

    public RecommendAdapter(OnClickListener listener) {
        this(listener, false);
    }

    public RecommendAdapter(OnClickListener listener, boolean showPositionRank) {
        mListener = listener;
        mItems = new ArrayList<>();
        mShowPositionRank = showPositionRank;
    }

    public interface OnClickListener {

        void onItemClick(Douban.Item item);
    }

    public void setItems(List<Douban.Item> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterRecommendBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Douban.Item item = mItems.get(position);
        holder.binding.name.setText(item.getTitle());
        holder.binding.brief.setText(item.getBrief());
        int rank = item.getRank() > 0 ? item.getRank() : mShowPositionRank ? position + 1 : 0;
        holder.binding.rank.setVisibility(rank > 0 ? View.VISIBLE : View.GONE);
        holder.binding.rating.setVisibility(item.getRating() > 0 ? View.VISIBLE : View.GONE);
        if (rank > 0) holder.binding.rank.setText("#" + rank);
        if (item.getRating() > 0) holder.binding.rating.setText(holder.itemView.getContext().getString(R.string.recommend_score, item.getRating()));
        holder.binding.image.setContentDescription(item.getTitle());
        ImgUtil.rect(item.getTitle(), item.getPic(), holder.binding.image);
        holder.binding.getRoot().setOnClickListener(view -> mListener.onItemClick(item));
        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationX(22f);
        holder.itemView.animate().alpha(1f).translationX(0f).setDuration(260).setStartDelay(Math.min(position, 5) * 35L).start();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterRecommendBinding binding;

        ViewHolder(@NonNull AdapterRecommendBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
