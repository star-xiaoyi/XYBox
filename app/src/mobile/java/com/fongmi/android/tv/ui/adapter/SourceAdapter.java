package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterSourceBinding;
import com.fongmi.android.tv.search.TitleKey;
import com.fongmi.android.tv.search.VodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 详情页的片源列表。当前播放的源固定在第一个，其余按 {@link VodSource#RANK} 排：
 * 实测越快越靠前，测速失败和播放失败过的垫底。
 */
public class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.ViewHolder> {

    private static final long FAST = 1024 * 1024;
    private static final long MEDIUM = 400 * 1024;

    private final OnClickListener mListener;
    private final List<VodSource> mItems;
    private VodSource mCurrent;

    public SourceAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(VodSource item);
    }

    public void clear() {
        mItems.clear();
        mCurrent = null;
        notifyDataSetChanged();
    }

    public void addAll(List<VodSource> items) {
        mItems.addAll(items);
        sort();
    }

    public void add(VodSource item) {
        mItems.add(item);
        sort();
    }

    public boolean contains(String key, String id) {
        return find(key, id) != null;
    }

    public VodSource find(String key, String id) {
        for (VodSource item : mItems) if (item.same(key, id)) return item;
        return null;
    }

    public VodSource getCurrent() {
        return mCurrent;
    }

    public void setCurrent(VodSource current) {
        mCurrent = current;
        sort();
    }

    public boolean isCurrent(VodSource item) {
        return item == mCurrent;
    }

    /** 除当前源之外、按排序先后的全部源。 */
    public List<VodSource> getRanked() {
        List<VodSource> items = new ArrayList<>(mItems);
        items.remove(mCurrent);
        Collections.sort(items, VodSource.RANK);
        return items;
    }

    /**
     * 下一个可以自动切过去的源：不是当前的、没播挂过的、站点允许换源的。
     * year 大于 0 时只认年份明确且对得上的，用来防同名翻拍抢先。
     */
    public VodSource next(int year) {
        for (VodSource item : getRanked()) {
            // 网盘源仍可手动选择，但不参加自动切源，避免突然弹出登录二维码。
            if (item.isBroken() || item.getSite().isCloudDrive() || !item.getSite().isChangeable()) continue;
            int itemYear = TitleKey.year(item.getVod().getVodYear());
            if (year > 0 && (itemYear == 0 || !TitleKey.sameYear(year, itemYear))) continue;
            return item;
        }
        return null;
    }

    public boolean isTesting() {
        for (VodSource item : mItems) if (item.getState() == VodSource.TESTING) return true;
        return false;
    }

    public boolean hasTested() {
        for (VodSource item : mItems) if (item.getState() == VodSource.OK) return true;
        return false;
    }

    public void sort() {
        List<VodSource> items = getRanked();
        mItems.clear();
        if (mCurrent != null) mItems.add(mCurrent);
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VodSource item = mItems.get(position);
        boolean current = item == mCurrent;
        holder.binding.getRoot().setActivated(current);
        holder.binding.getRoot().setAlpha(!current && (item.isBroken() || item.isFailed()) ? 0.5f : 1f);
        holder.binding.name.setText(item.getSiteName());
        holder.binding.status.setText(getStatus(holder.itemView.getContext(), item, current));
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    /**
     * 第二行：更新状态 · 测速结果。速度按能不能流畅播 1080p 分绿、橙、红三档。
     * 没轮到测速的只显示更新状态，连更新状态都没有才写"未测速"，保证每个胶囊都是两行、高度一致。
     */
    private CharSequence getStatus(Context context, VodSource item, boolean current) {
        String status = "";
        int color = 0;
        if (current) {
            status = context.getString(R.string.source_playing);
        } else if (item.isBroken()) {
            status = context.getString(R.string.source_broken);
            color = R.color.source_slow;
        } else if (item.getState() == VodSource.OK) {
            status = formatSpeed(item.getSpeed());
            color = item.getSpeed() >= FAST ? R.color.source_fast : item.getSpeed() >= MEDIUM ? R.color.source_medium : R.color.source_slow;
        } else if (item.getState() == VodSource.TESTING) {
            status = context.getString(R.string.source_testing);
        } else if (item.getState() == VodSource.TIMEOUT) {
            status = context.getString(R.string.source_timeout);
            color = R.color.source_slow;
        } else if (item.getState() == VodSource.FAIL) {
            status = context.getString(R.string.source_fail);
            color = R.color.source_slow;
        } else if (item.getState() == VodSource.SKIP) {
            status = context.getString(R.string.source_skip);
        }
        String remarks = item.getVod().getVodRemarks();
        if (remarks.isEmpty() && status.isEmpty()) status = context.getString(R.string.source_idle);
        SpannableStringBuilder builder = new SpannableStringBuilder(remarks);
        if (!remarks.isEmpty() && !status.isEmpty()) builder.append(" · ");
        int start = builder.length();
        builder.append(status);
        if (color != 0) builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, color)), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    private static String formatSpeed(long speed) {
        if (speed >= FAST) return String.format(Locale.ROOT, "%.1fMB/s", speed / 1024f / 1024f);
        return String.format(Locale.ROOT, "%dKB/s", Math.max(1, speed / 1024));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSourceBinding binding;

        ViewHolder(@NonNull AdapterSourceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
