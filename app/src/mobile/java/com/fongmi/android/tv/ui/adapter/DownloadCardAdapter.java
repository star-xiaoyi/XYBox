package com.fongmi.android.tv.ui.adapter;

import android.graphics.Bitmap;
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
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.AdapterDownloadCardBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

/** 首页「离线缓存」页签下的横向卡片，展示形式与观看记录一致，一张卡是一部剧。 */
public class DownloadCardAdapter extends RecyclerView.Adapter<DownloadCardAdapter.ViewHolder> {

    private final List<Download.Group> mItems = new ArrayList<>();
    private final OnClickListener mListener;

    public interface OnClickListener {
        void onItemClick(Download.Group item);
    }

    public DownloadCardAdapter(OnClickListener listener) {
        this.mListener = listener;
    }

    public void setItems(List<Download.Group> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items.size() > 15 ? items.subList(0, 15) : items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDownloadCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Download.Group item = mItems.get(position);
        holder.binding.name.setText(item.getVodName());
        int active = item.getActiveCount();
        if (active > 0) {
            holder.binding.remark.setText(ResUtil.getString(R.string.download_running_count, String.valueOf(active)));
            holder.binding.progress.setVisibility(View.VISIBLE);
            holder.binding.progress.setProgress(item.getProgress());
        } else {
            holder.binding.remark.setText(ResUtil.getString(R.string.download_count, String.valueOf(item.getDoneCount())));
            holder.binding.progress.setVisibility(View.GONE);
        }
        loadImage(item.getVodName(), item.getVodPic(), holder.binding.image);
        holder.binding.getRoot().setOnClickListener(view -> {
            if (mListener != null) mListener.onItemClick(item);
        });
    }

    /** 与观看记录同样固定加载 90x132，省流量也避免两处卡片图裁切不一致。 */
    private void loadImage(String text, String url, ImageView view) {
        view.setScaleType(ImageView.ScaleType.CENTER);
        if (url != null && !url.isEmpty()) {
            Glide.with(App.get())
                    .asBitmap()
                    .load(ImgUtil.getUrl(url))
                    .placeholder(R.drawable.ic_img_loading)
                    .override(ResUtil.dp2px(90), ResUtil.dp2px(132))
                    .dontAnimate()
                    .signature(new ObjectKey(url + "_90x132"))
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

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDownloadCardBinding binding;

        ViewHolder(@NonNull AdapterDownloadCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
