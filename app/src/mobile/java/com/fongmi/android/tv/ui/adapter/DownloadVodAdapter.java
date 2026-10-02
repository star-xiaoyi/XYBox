package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.AdapterDownloadVodBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/** Film rows show the actual completed and pending episodes beside a small poster. */
public class DownloadVodAdapter extends RecyclerView.Adapter<DownloadVodAdapter.ViewHolder> {

    private final List<Download.Group> mItems = new ArrayList<>();
    private final OnClickListener mListener;
    private boolean delete;

    public interface OnClickListener {

        void onItemClick(Download.Group item);

        void onItemDelete(Download.Group item);

        boolean onLongClick();
    }

    public DownloadVodAdapter(OnClickListener listener) {
        this.mListener = listener;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
        notifyItemRangeChanged(0, mItems.size());
    }

    public void setItems(List<Download.Group> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return mItems.isEmpty();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDownloadVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Download.Group item = mItems.get(position);
        holder.binding.name.setText(item.getVodName());
        holder.binding.delete.setVisibility(delete ? View.VISIBLE : View.GONE);
        int active = item.getActiveCount();
        int ready = item.getDoneCount();
        holder.binding.ready.setText(ready > 0 ? "已缓存 " + ready + " 集 · " + item.episodeSummary(true) : "暂无可播放缓存");
        holder.binding.pending.setText(active > 0 ? "未完成 · " + item.episodeSummary(false) : "全部缓存完成，可离线观看");
        holder.binding.pending.setVisibility(active > 0 || ready > 0 ? View.VISIBLE : View.GONE);
        holder.binding.delete.setOnClickListener(v -> mListener.onItemDelete(item));
        ImgUtil.loadVod(item.getVodName(), item.getVodPic(), holder.binding.image);
        holder.binding.getRoot().setOnLongClickListener(view -> mListener.onLongClick());
        holder.binding.getRoot().setOnClickListener(view -> {
            if (isDelete()) mListener.onItemDelete(item);
            else mListener.onItemClick(item);
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDownloadVodBinding binding;

        ViewHolder(@NonNull AdapterDownloadVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
