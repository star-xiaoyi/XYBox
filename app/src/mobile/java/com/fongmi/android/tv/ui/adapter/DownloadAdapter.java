package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.AdapterDownloadBinding;
import com.fongmi.android.tv.utils.UrlUtil;

import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Download> items;

    public interface OnClickListener {
        void onItemClick(Download item);
        void onActionClick(Download item);
    }

    public DownloadAdapter(OnClickListener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();
    }

    public void addAll(List<Download> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void update(Download download) {
        int position = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId() == download.getId()) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            items.set(position, download);
            notifyItemChanged(position);
        }
    }

    public void remove(Download download) {
        int position = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId() == download.getId()) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    public List<Download> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.initView(items.get(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDownloadBinding binding;

        ViewHolder(AdapterDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void initView(Download item) {
            binding.name.setText(item.getVodName());
            Glide.with(App.get()).load(UrlUtil.convert(item.getVodPic())).placeholder(R.drawable.ic_nav_vod).into(binding.cover);

            // 根据下载状态显示不同内容
            int status = item.getStatusInt();
            if (status == Download.STATUS_COMPLETED) {
                binding.status.setText(R.string.download_completed);
                binding.progress.setProgress(100);
                binding.progressText.setText(R.string.download_completed);
                binding.action.setImageResource(R.drawable.ic_action_delete);
            } else if (status == Download.STATUS_FAILED) {
                binding.status.setText(R.string.download_failed);
                binding.progress.setProgress(0);
                binding.progressText.setText(item.getStatus());
                binding.action.setImageResource(R.drawable.ic_action_delete);
            } else {
                binding.status.setText(R.string.downloading);
                binding.progress.setProgress(item.getProgress());
                String progressText = item.getProgress() + "%";
                if (item.getSpeed() > 0) {
                    progressText += " - " + formatSpeed(item.getSpeed());
                }
                binding.progressText.setText(progressText);
                binding.action.setImageResource(R.drawable.ic_action_delete);
            }

            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            binding.action.setOnClickListener(v -> listener.onActionClick(item));
        }

        private String formatSpeed(long speed) {
            if (speed < 1024) return speed + " B/s";
            if (speed < 1024 * 1024) return String.format("%.1f KB/s", speed / 1024.0);
            return String.format("%.1f MB/s", speed / (1024.0 * 1024.0));
        }
    }
}
