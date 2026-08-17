package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.CastMember;
import com.fongmi.android.tv.databinding.ItemCastBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 演职人员列表适配器
 * 用于在 RecyclerView 中展示演员或导演列表
 */
public class CastAdapter extends RecyclerView.Adapter<CastAdapter.ViewHolder> {
    
    private final List<CastMember> items;
    private final OnClickListener listener;
    
    /**
     * 点击事件监听器接口
     */
    public interface OnClickListener {
        void onCastClick(CastMember member);
    }
    
    /**
     * 构造函数
     * @param listener 点击事件监听器
     */
    public CastAdapter(OnClickListener listener) {
        this.items = new ArrayList<>();
        this.listener = listener;
    }
    
    /**
     * 添加演职人员列表
     * @param members 演职人员列表
     */
    public void addAll(List<CastMember> members) {
        items.clear();
        items.addAll(members);
        notifyDataSetChanged();
    }
    
    /**
     * 清空列表
     */
    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }
    
    /**
     * 获取列表项数量
     * @return 项数量
     */
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    /**
     * 创建 ViewHolder
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCastBinding binding = ItemCastBinding.inflate(
            LayoutInflater.from(parent.getContext()), 
            parent, 
            false
        );
        return new ViewHolder(binding);
    }
    
    /**
     * 绑定数据到 ViewHolder
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CastMember member = items.get(position);
        holder.binding.name.setText(member.getName());
        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onCastClick(member);
            }
        });
    }
    
    /**
     * ViewHolder 类
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCastBinding binding;
        
        ViewHolder(@NonNull ItemCastBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
