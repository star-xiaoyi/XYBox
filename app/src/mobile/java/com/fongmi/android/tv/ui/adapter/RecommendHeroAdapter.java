package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.Douban;
import com.fongmi.android.tv.databinding.AdapterRecommendHeroBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecommendHeroAdapter extends RecyclerView.Adapter<RecommendHeroAdapter.ViewHolder> {

    private final RecommendAdapter.OnClickListener mListener;
    private final List<Douban.Item> mItems = new ArrayList<>();
    private int mCardWidth;

    public RecommendHeroAdapter(RecommendAdapter.OnClickListener listener) {
        mListener = listener;
    }

    public void setItems(List<Douban.Item> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setCardWidth(int cardWidth) {
        if (cardWidth == mCardWidth) return;
        mCardWidth = cardWidth;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size() > 1 ? Integer.MAX_VALUE : mItems.size();
    }

    public int getLogicalCount() {
        return mItems.size();
    }

    public int getStartPosition() {
        if (mItems.size() < 2) return 0;
        int middle = Integer.MAX_VALUE / 2;
        return middle - middle % mItems.size();
    }

    public int toLogicalPosition(int position) {
        return mItems.isEmpty() ? 0 : Math.floorMod(position, mItems.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterRecommendHeroBinding binding = AdapterRecommendHeroBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.posterFrame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 12 * view.getResources().getDisplayMetrics().density);
            }
        });
        binding.posterFrame.setClipToOutline(true);
        return new ViewHolder(binding);
    }

    private void bindSize(AdapterRecommendHeroBinding binding) {
        ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
        float density = binding.getRoot().getResources().getDisplayMetrics().density;
        int densityCard = (int) (142 * density + 0.5f);
        params.width = Math.max(1, mCardWidth > 0 ? mCardWidth : densityCard);
        binding.getRoot().setLayoutParams(params);
        int posterHeight = Math.round(params.width * 1.46f);
        ViewGroup.LayoutParams posterParams = binding.posterFrame.getLayoutParams();
        posterParams.height = posterHeight;
        binding.posterFrame.setLayoutParams(posterParams);
        ViewGroup.LayoutParams rootParams = binding.getRoot().getLayoutParams();
        rootParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.getRoot().setLayoutParams(rootParams);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        bindSize(holder.binding);
        Douban.Item item = mItems.get(toLogicalPosition(position));
        holder.binding.title.setText(item.getTitle());
        holder.binding.brief.setText(item.getBrief());
        holder.binding.rating.setVisibility(item.getRating() > 0 ? View.VISIBLE : View.GONE);
        if (item.getRating() > 0) holder.binding.rating.setText(String.format(Locale.getDefault(), "豆瓣 %.1f", item.getRating()));
        holder.binding.image.setContentDescription(item.getTitle());
        ImgUtil.rect(item.getTitle(), item.getPic(), holder.binding.image);
        View.OnClickListener listener = view -> mListener.onItemClick(item);
        // 只让真实海报接收点击，缩放后的空白和隐藏文字不参与命中。
        holder.binding.posterFrame.setOnClickListener(listener);
        holder.binding.posterFrame.setContentDescription(item.getTitle());
    }

    public Douban.Item get(int position) {
        return mItems.isEmpty() || position < 0 ? null : mItems.get(toLogicalPosition(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterRecommendHeroBinding binding;

        ViewHolder(@NonNull AdapterRecommendHeroBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
