package com.fongmi.android.tv.ui.adapter;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterRecommendChannelBinding;

public class RecommendChannelAdapter extends RecyclerView.Adapter<RecommendChannelAdapter.ViewHolder> {

    public interface OnClickListener {
        void onChannelClick(int position);
    }

    private final OnClickListener mListener;
    private final String[] mItems;
    private int mSelected;

    public RecommendChannelAdapter(String[] items, OnClickListener listener) {
        mItems = items;
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
        return new ViewHolder(AdapterRecommendChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        boolean selected = position == mSelected;
        holder.binding.getRoot().setActivated(selected);
        holder.binding.text.setText(mItems[position]);
        holder.binding.text.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), selected ? R.color.black : R.color.text_secondary));
        holder.binding.text.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        holder.binding.indicator.setVisibility(View.GONE);
        holder.binding.getRoot().setOnClickListener(view -> {
            int current = holder.getAdapterPosition();
            if (current != RecyclerView.NO_POSITION) mListener.onChannelClick(current);
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterRecommendChannelBinding binding;

        ViewHolder(AdapterRecommendChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
