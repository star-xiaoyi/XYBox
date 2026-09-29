package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.Douban;
import com.fongmi.android.tv.databinding.AdapterRecommendRankCardBinding;

import java.util.ArrayList;
import java.util.List;

public class RecommendRankCardAdapter extends RecyclerView.Adapter<RecommendRankCardAdapter.ViewHolder> {

    public interface OnOpenListener {
        void onOpen(int index);
    }

    public static class Group {
        public final int index;
        public final String title;
        public final List<Douban.Item> items;

        public Group(int index, String title, List<Douban.Item> items) {
            this.index = index;
            this.title = title;
            this.items = items;
        }
    }

    private final RecommendAdapter.OnClickListener mItemListener;
    private final OnOpenListener mOpenListener;
    private final List<Group> mItems = new ArrayList<>();
    private int mCardWidth;

    public RecommendRankCardAdapter(RecommendAdapter.OnClickListener itemListener, OnOpenListener openListener) {
        mItemListener = itemListener;
        mOpenListener = openListener;
    }

    public void setItems(List<Group> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setCardWidth(int width) {
        if (width == mCardWidth) return;
        mCardWidth = width;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterRecommendRankCardBinding binding = AdapterRecommendRankCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
        if (mCardWidth > 0) params.width = mCardWidth;
        binding.getRoot().setLayoutParams(params);
        binding.list.setLayoutManager(new LinearLayoutManager(parent.getContext()));
        binding.list.setItemAnimator(null);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Group group = mItems.get(position);
        holder.binding.title.setText(group.title);
        holder.binding.list.setAdapter(new RecommendMiniRankAdapter(mItemListener, group.items));
        holder.binding.more.setOnClickListener(view -> mOpenListener.onOpen(group.index));
        holder.binding.title.setOnClickListener(view -> mOpenListener.onOpen(group.index));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterRecommendRankCardBinding binding;

        ViewHolder(AdapterRecommendRankCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
