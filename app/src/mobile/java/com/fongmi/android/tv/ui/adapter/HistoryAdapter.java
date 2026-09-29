package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.AdapterHistoryBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<History> mItems;
    private boolean delete;

    public HistoryAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(History item);

        void onItemDelete(History item);

        boolean onLongClick();
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
        notifyItemRangeChanged(0, mItems.size());
    }

    public void addAll(List<History> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        com.fongmi.android.tv.utils.WebDAVSyncManager.get().markHistoriesDeleted(new ArrayList<>(mItems));
        mItems.clear();
        setDelete(false);
        notifyDataSetChanged();
        History.delete(VodConfig.getCid());
    }

    public void remove(History item) {
        int index = mItems.indexOf(item);
        if (index == -1) return;
        mItems.remove(index);
        notifyItemRemoved(index);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        History item = mItems.get(position);
        holder.binding.name.setText(item.getVodName());
        CharSequence time = android.text.format.DateUtils.getRelativeTimeSpanString(item.getCreateTime(), System.currentTimeMillis(), 60000);
        holder.binding.site.setText(item.getSiteName().isEmpty() ? time : item.getSiteName() + " · " + time);
        holder.binding.site.setVisibility(View.VISIBLE);
        holder.binding.remark.setVisibility(View.VISIBLE);
        long positionMs = Math.max(0, item.getPosition()), durationMs = Math.max(0, item.getDuration());
        int progress = durationMs > 0 ? (int) Math.min(100, positionMs * 100 / durationMs) : 0;
        holder.binding.progress.setProgress(progress);
        String positionText = android.text.format.DateUtils.formatElapsedTime(positionMs / 1000);
        String durationText = durationMs > 0 ? android.text.format.DateUtils.formatElapsedTime(durationMs / 1000) : "--:--";
        holder.binding.progressText.setText(positionText + " / " + durationText + (durationMs > 0 ? " · " + progress + "%" : ""));
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        holder.binding.remark.setText(ResUtil.getString(R.string.vod_last, item.getVodRemarks()));
        ImgUtil.loadVod(item.getVodName(), item.getVodPic(), holder.binding.image);
        setClickListener(holder.binding.getRoot(), item);
    }

    private void setClickListener(View root, History item) {
        root.setOnLongClickListener(view -> mListener.onLongClick());
        root.setOnClickListener(view -> {
            if (isDelete()) mListener.onItemDelete(item);
            else mListener.onItemClick(item);
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterHistoryBinding binding;

        ViewHolder(@NonNull AdapterHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
