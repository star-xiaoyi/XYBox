package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterDownloadEpisodeBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 选集缓存面板里的剧集格子：可勾选，并显示这一集当前的缓存状态。 */
public class DownloadEpisodeAdapter extends RecyclerView.Adapter<DownloadEpisodeAdapter.ViewHolder> {

    private final List<Episode> items = new ArrayList<>();
    private final OnClickListener listener;
    private Map<String, Download> states;
    private Set<String> selected;

    public interface OnClickListener {
        void onEpisodeClick(Episode item);
    }

    public DownloadEpisodeAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Episode> items, Set<String> selected, Map<String, Download> states) {
        this.items.clear();
        this.items.addAll(items);
        this.selected = selected;
        this.states = states;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDownloadEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode item = items.get(position);
        Download state = states == null ? null : states.get(Download.episodeKey(item.getName()));
        holder.binding.text.setText(item.getName());
        holder.binding.getRoot().setActivated(selected != null && selected.contains(item.getName()));
        if (state == null) {
            holder.binding.state.setVisibility(View.GONE);
        } else {
            holder.binding.state.setVisibility(View.VISIBLE);
            holder.binding.state.setText(text(holder, state));
        }
        holder.binding.getRoot().setOnClickListener(v -> listener.onEpisodeClick(item));
    }

    private String text(ViewHolder holder, Download state) {
        switch (state.getStatus()) {
            case Download.STATUS_DONE:
                return holder.string(R.string.download_state_done);
            case Download.STATUS_RUNNING:
                return state.getProgress() + "%";
            case Download.STATUS_PAUSED:
                return holder.string(R.string.download_state_paused);
            case Download.STATUS_ERROR:
                return holder.string(R.string.download_state_error);
            default:
                return holder.string(R.string.download_state_pending);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDownloadEpisodeBinding binding;

        ViewHolder(@NonNull AdapterDownloadEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        String string(int resId) {
            return itemView.getContext().getString(resId);
        }
    }
}
