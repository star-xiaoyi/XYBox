package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.AdapterDownloadBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 缓存任务列表：一行一集，实时进度、可暂停/继续/重试/删除。 */
public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

    private final List<Download> mItems = new ArrayList<>();
    private final OnClickListener mListener;

    public interface OnClickListener {

        void onItemToggle(Download item);

        void onItemDelete(Download item);
    }

    public DownloadAdapter(OnClickListener listener) {
        this.mListener = listener;
    }

    public void setItems(List<Download> items) {
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
        return new ViewHolder(AdapterDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Download item = mItems.get(position);
        holder.binding.name.setText(item.getVodName() + "  " + item.getEpisodeName());
        holder.binding.state.setText(state(item));
        holder.binding.progress.setProgress(item.isDone() ? 100 : item.getProgress());
        holder.binding.action.setImageResource(item.isRunning() || item.isPending() ? R.drawable.ic_notify_pause : item.isError() ? R.drawable.ic_action_retry : R.drawable.ic_notify_play);
        holder.binding.action.setVisibility(item.isDone() ? View.GONE : View.VISIBLE);
        ImgUtil.loadVod(item.getVodName(), item.getVodPic(), holder.binding.image);
        holder.binding.action.setOnClickListener(v -> mListener.onItemToggle(item));
        holder.binding.delete.setOnClickListener(v -> mListener.onItemDelete(item));
    }

    private String state(Download item) {
        switch (item.getStatus()) {
            case Download.STATUS_DONE:
                return ResUtil.getString(R.string.download_state_done);
            case Download.STATUS_RUNNING:
                return join(ResUtil.getString(R.string.download_state_running), item.getProgress() + "%", speed(item.getSpeed()));
            case Download.STATUS_PAUSED:
                return join(ResUtil.getString(R.string.download_state_paused), item.getProgress() + "%");
            case Download.STATUS_ERROR:
                return join(ResUtil.getString(R.string.download_state_error), item.getErrorMsg());
            default:
                return ResUtil.getString(R.string.download_state_pending);
        }
    }

    private String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(part);
        }
        return sb.toString();
    }

    private String speed(long speed) {
        if (speed <= 0) return "";
        if (speed < 1024) return speed + " B/s";
        if (speed < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB/s", speed / 1024d);
        return String.format(Locale.getDefault(), "%.1f MB/s", speed / (1024d * 1024d));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDownloadBinding binding;

        ViewHolder(@NonNull AdapterDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
