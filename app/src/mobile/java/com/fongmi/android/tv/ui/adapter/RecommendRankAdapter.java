package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.Douban;
import com.fongmi.android.tv.databinding.AdapterRecommendRankBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecommendRankAdapter extends RecyclerView.Adapter<RecommendRankAdapter.ViewHolder> {

    private final RecommendAdapter.OnClickListener mListener;
    private final List<Douban.Item> mItems = new ArrayList<>();

    public RecommendRankAdapter(RecommendAdapter.OnClickListener listener) {
        mListener = listener;
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
        return new ViewHolder(AdapterRecommendRankBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Douban.Item item = mItems.get(position);
        int rank = item.getRank() > 0 ? item.getRank() : position + 1;
        holder.binding.rank.setText(String.format(Locale.getDefault(), "%02d", rank));
        holder.binding.title.setText(item.getTitle());
        holder.binding.brief.setText(item.getBrief());
        holder.binding.rating.setVisibility(item.getRating() > 0 ? View.VISIBLE : View.GONE);
        if (item.getRating() > 0) holder.binding.rating.setText(holder.itemView.getContext().getString(R.string.recommend_score, item.getRating()));
        holder.binding.image.setContentDescription(item.getTitle());
        ImgUtil.rect(item.getTitle(), item.getPic(), holder.binding.image);
        holder.binding.getRoot().setOnClickListener(view -> mListener.onItemClick(item));
        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(18f);
        holder.itemView.animate().alpha(1f).translationY(0f).setDuration(240).setStartDelay(Math.min(position, 6) * 30L).start();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterRecommendRankBinding binding;

        ViewHolder(@NonNull AdapterRecommendRankBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
