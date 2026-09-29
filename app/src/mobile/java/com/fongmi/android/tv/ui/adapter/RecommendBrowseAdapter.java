package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.Douban;
import com.fongmi.android.tv.databinding.AdapterRecommendBrowseBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class RecommendBrowseAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private View mHeader;
    private boolean mHeaderVisible = true;
    public void setHeader(View header) { mHeader = header; notifyDataSetChanged(); }
    public int getHeaderCount() { return mHeader != null && mHeaderVisible ? 1 : 0; }
    public void setHeaderVisible(boolean visible) {
        if (visible == mHeaderVisible) return;
        mHeaderVisible = visible;
        if (mHeader != null) { if (visible) notifyItemInserted(0); else notifyItemRemoved(0); }
    }
    @Override public int getItemViewType(int position) { return getHeaderCount() == 1 && position == 0 ? 1 : 0; }
    private final RecommendAdapter.OnClickListener mListener;
    private final List<Douban.Item> mItems = new ArrayList<>();

    public RecommendBrowseAdapter(RecommendAdapter.OnClickListener listener) {
        mListener = listener;
    }

    public void setItems(List<Douban.Item> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void addItems(List<Douban.Item> items) {
        int start = mItems.size();
        mItems.addAll(items);
        notifyItemRangeInserted(start + getHeaderCount(), items.size());
    }

    @Override
    public int getItemCount() {
        return mItems.size() + getHeaderCount();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 1) return new RecyclerView.ViewHolder(mHeader) {};
        return new ViewHolder(AdapterRecommendBrowseBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        if (getItemViewType(position) == 1) return;
        ViewHolder holder = (ViewHolder) rawHolder;
        Douban.Item item = mItems.get(position - getHeaderCount());
        holder.binding.name.setText(item.getTitle());
        holder.binding.brief.setText(item.getBrief());
        holder.binding.rating.setVisibility(item.getRating() > 0 ? View.VISIBLE : View.GONE);
        if (item.getRating() > 0) holder.binding.rating.setText(holder.itemView.getContext().getString(R.string.recommend_score, item.getRating()));
        holder.binding.image.setContentDescription(item.getTitle());
        ImgUtil.rect(item.getTitle(), item.getPic(), holder.binding.image);
        holder.binding.getRoot().setOnClickListener(view -> mListener.onItemClick(item));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterRecommendBrowseBinding binding;

        ViewHolder(AdapterRecommendBrowseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
