package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterRecommendListTabBinding;

public class RecommendListTabAdapter extends RecyclerView.Adapter<RecommendListTabAdapter.ViewHolder> {

    public interface OnClickListener {
        void onTabClick(int position);
    }

    private final CharSequence[] mItems;
    private final OnClickListener mListener;
    private int mSelected;

    public RecommendListTabAdapter(CharSequence[] items, int selected, OnClickListener listener) {
        mItems = items;
        mSelected = selected;
        mListener = listener;
    }

    public void setSelected(int position) {
        if (position == mSelected) return;
        int previous = mSelected;
        mSelected = position;
        notifyItemChanged(previous);
        notifyItemChanged(position);
    }

    @Override
    public int getItemCount() {
        return mItems.length;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterRecommendListTabBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.getRoot().setText(mItems[position]);
        holder.binding.getRoot().setActivated(position == mSelected);
        holder.binding.getRoot().setOnClickListener(view -> {
            int current = holder.getAdapterPosition();
            if (current != RecyclerView.NO_POSITION) mListener.onTabClick(current);
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterRecommendListTabBinding binding;

        ViewHolder(AdapterRecommendListTabBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
