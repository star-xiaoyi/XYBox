package com.fongmi.android.tv.ui.adapter;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.AdapterHistoryCardBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class HistoryCardAdapter extends RecyclerView.Adapter<HistoryCardAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<History> mItems;

    public HistoryCardAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {
        void onItemClick(History item);
    }

    public void setItems(List<History> items) {
        mItems.clear();
        if (items != null) {
            // 限制最多显示15条记录
            int count = Math.min(items.size(), 15);
            mItems.addAll(items.subList(0, count));
        }
        notifyDataSetChanged();
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
        return new ViewHolder(AdapterHistoryCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        History item = mItems.get(position);
        
        // 设置名称
        holder.binding.name.setText(item.getVodName());
        
        // 设置观看进度文本
        String remarkText = item.getVodRemarks();
        if (remarkText != null && !remarkText.isEmpty()) {
            holder.binding.remark.setText(ResUtil.getString(R.string.vod_last, remarkText));
            holder.binding.remark.setVisibility(View.VISIBLE);
        } else {
            holder.binding.remark.setVisibility(View.GONE);
        }
        
        // 设置观看进度条
        int progress = calculateProgress(item);
        holder.binding.progress.setProgress(progress);
        
        // 加载封面图片（使用中等尺寸）
        loadHistoryImage(item.getVodName(), item.getVodPic(), holder.binding.image);
        
        // 设置点击事件
        holder.binding.getRoot().setOnClickListener(view -> {
            if (mListener != null) {
                mListener.onItemClick(item);
            }
        });
    }

    /**
     * 加载观看记录封面图片（固定加载小尺寸图片）
     */
    private void loadHistoryImage(String text, String url, ImageView view) {
        view.setScaleType(ImageView.ScaleType.CENTER);
        if (url != null && !url.isEmpty()) {
            // 固定加载 90dp × 132dp 的图片尺寸，匹配卡片大小，节省流量
            int width = ResUtil.dp2px(90);
            int height = ResUtil.dp2px(132);
            
            Glide.with(App.get())
                .asBitmap()
                .load(ImgUtil.getUrl(url))
                .placeholder(R.drawable.ic_img_loading)
                .override(width, height)  // 明确指定加载尺寸
                .skipMemoryCache(true)  // 跳过内存缓存
                .dontAnimate()  // 不使用动画
                .signature(new ObjectKey(url + "_90x132"))  // 添加签名以区分不同尺寸
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Bitmap> target, boolean isFirstResource) {
                        view.setImageResource(R.drawable.ic_img_error);
                        view.setScaleType(ImageView.ScaleType.CENTER);
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Bitmap resource, @NonNull Object model, Target<Bitmap> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        return false;
                    }
                })
                .into(view);
        } else if (text != null && !text.isEmpty()) {
            ImgUtil.loadVod(text, "", view);
        } else {
            view.setImageResource(R.drawable.ic_img_error);
        }
    }

    /**
     * 计算观看进度百分比
     */
    private int calculateProgress(History item) {
        try {
            long duration = item.getDuration();
            long position = item.getPosition();
            
            if (duration > 0 && position > 0) {
                int progress = (int) ((position * 100) / duration);
                return Math.min(progress, 100);
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterHistoryCardBinding binding;

        ViewHolder(@NonNull AdapterHistoryCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
