package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterSearchGroupBinding;
import com.fongmi.android.tv.search.VodGroup;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 聚合搜索结果：一部片一行。
 * <p>
 * 已经显示出来的行不再挪位置，后到的站点只会让对应行的"N 个源"变多，新片接在末尾，
 * 免得正要点的那一行被挤走。
 */
public class SearchGroupAdapter extends RecyclerView.Adapter<SearchGroupAdapter.ViewHolder> {

    private static final Object PAYLOAD_INFO = new Object();

    private final OnClickListener mListener;
    private final List<VodGroup> mItems;

    public SearchGroupAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(VodGroup item);

        /** 这一行没有简介，滑进屏幕时通知外面去补拉一次详情。 */
        void onFill(VodGroup item);
    }

    public void setItems(List<VodGroup> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void addAll(List<VodGroup> items) {
        if (items.isEmpty()) return;
        int position = mItems.size();
        mItems.addAll(items);
        notifyItemRangeInserted(position, items.size());
    }

    /** 只刷文字，不重新加载海报，否则每来一个站点整行图片都会闪一下。 */
    public void update(Collection<VodGroup> items) {
        for (VodGroup item : items) {
            int position = mItems.indexOf(item);
            if (position >= 0) notifyItemChanged(position, PAYLOAD_INFO);
        }
    }

    public void update(VodGroup item) {
        int position = mItems.indexOf(item);
        if (position >= 0) notifyItemChanged(position);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) onBindViewHolder(holder, position);
        else setInfo(holder, mItems.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VodGroup item = mItems.get(position);
        setInfo(holder, item);
        ImgUtil.rect(item.getName(), item.getPic(), holder.binding.image);
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
        if (!item.isFolder() && !item.hasContent() && !item.isFilled()) mListener.onFill(item);
    }

    private void setInfo(ViewHolder holder, VodGroup item) {
        holder.binding.name.setText(item.getName());
        setText(holder.binding.meta, item.getMeta());
        setText(holder.binding.actor, item.getActor().isEmpty() ? "" : holder.itemView.getContext().getString(R.string.search_actor, item.getActor()));
        setText(holder.binding.content, item.getContent());
        setText(holder.binding.remark, item.getRemarks());
        // 网盘文件夹没有"几个源"的说法，直接标出是哪个站点的
        if (item.isFolder()) holder.binding.count.setText(item.first().getSiteName());
        else holder.binding.count.setText(holder.itemView.getContext().getString(R.string.search_source_count, item.size()));
    }

    private void setText(android.widget.TextView view, String text) {
        view.setText(text);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchGroupBinding binding;

        ViewHolder(@NonNull AdapterSearchGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
