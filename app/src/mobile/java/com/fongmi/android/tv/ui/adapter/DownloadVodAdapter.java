package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.AdapterVodBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

/** 缓存列表页的剧集网格，沿用观看记录的卡片与长按删除交互。 */
public class DownloadVodAdapter extends RecyclerView.Adapter<DownloadVodAdapter.ViewHolder> {

    private final List<Download.Group> mItems = new ArrayList<>();
    private final OnClickListener mListener;
    private int width, height;
    private boolean delete;

    public interface OnClickListener {

        void onItemClick(Download.Group item);

        void onItemDelete(Download.Group item);

        boolean onLongClick();
    }

    public DownloadVodAdapter(OnClickListener listener) {
        this.mListener = listener;
    }

    public void setSize(int[] size) {
        this.width = size[0];
        this.height = size[1];
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
        ViewHolder holder = new ViewHolder(AdapterVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.getRoot().getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Download.Group item = mItems.get(position);
        holder.binding.name.setText(item.getVodName());
        holder.binding.site.setVisibility(View.GONE);
        holder.binding.remark.setVisibility(delete ? View.GONE : View.VISIBLE);
        holder.binding.delete.setVisibility(delete ? View.VISIBLE : View.GONE);
        int active = item.getActiveCount();
        holder.binding.remark.setText(active > 0
                ? ResUtil.getString(R.string.download_running_count, String.valueOf(active))
                : ResUtil.getString(R.string.download_count, String.valueOf(item.getDoneCount())));
        ImgUtil.loadVod(item.getVodName(), item.getVodPic(), holder.binding.image);
        holder.binding.getRoot().setOnLongClickListener(view -> mListener.onLongClick());
        holder.binding.getRoot().setOnClickListener(view -> {
            if (isDelete()) mListener.onItemDelete(item);
            else mListener.onItemClick(item);
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterVodBinding binding;

        ViewHolder(@NonNull AdapterVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
