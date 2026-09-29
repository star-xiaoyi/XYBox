package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.View;
import android.view.Gravity;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import androidx.appcompat.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.github.catvod.utils.Logger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.Douban;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.databinding.ActivityRecommendFilterBinding;
import com.fongmi.android.tv.impl.FilterCallback;
import com.fongmi.android.tv.ui.adapter.FilterAdapter;
import com.fongmi.android.tv.ui.adapter.RecommendAdapter;
import com.fongmi.android.tv.ui.adapter.RecommendBrowseAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 电影、电视剧、动漫和综艺共用的豆瓣分类筛选页。 */
public class RecommendFilterActivity extends BaseActivity implements RecommendAdapter.OnClickListener, FilterCallback {

    private static final String EXTRA_CHANNEL = "channel";
    private static final String[] TAGS = {"电影", "电视剧", "动画", "综艺"};

    private final Map<Integer, Map<String, String>> mChannelFilters = new LinkedHashMap<>();
    private ActivityRecommendFilterBinding mBinding;
    private String[] mChannels;
    private boolean mFiltersVisible = true;
    private RecommendBrowseAdapter mAdapter;
    private int mChannel;
    private int mGeneration;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(2);
    private final List<Douban.Item> mItems = new ArrayList<>();
    private Future<?> mRequest;
    private int mOffset;
    private boolean mLoading;
    private boolean mHasMore;
    private boolean mFailed;

    public static void start(Activity activity, int channel) {
        Intent intent = new Intent(activity, RecommendFilterActivity.class);
        intent.putExtra(EXTRA_CHANNEL, channel);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityRecommendFilterBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mChannel = Math.max(0, Math.min(TAGS.length - 1, getIntent().getIntExtra(EXTRA_CHANNEL, 0)));
        if (savedInstanceState != null) {
            mChannel = Math.max(0, Math.min(TAGS.length - 1, savedInstanceState.getInt("selected_channel", mChannel)));
            mFiltersVisible = savedInstanceState.getBoolean("filters_visible", true);
            for (int channel = 0; channel < TAGS.length; channel++) {
                Bundle selected = savedInstanceState.getBundle("filters_" + channel);
                if (selected == null) continue;
                Map<String, String> values = new LinkedHashMap<>();
                for (String key : selected.keySet()) values.put(key, selected.getString(key, ""));
                mChannelFilters.put(channel, values);
            }
        }
        mChannels = new String[]{
                getString(R.string.recommend_channel_movie), getString(R.string.recommend_channel_tv),
                getString(R.string.recommend_channel_animation), getString(R.string.recommend_channel_variety)
        };
        mBinding.toolbar.setTitle(mChannels[mChannel]);
        mBinding.toolbar.setPrimaryActionVisible(true);
        updateFilterToggle();
        mBinding.toolbar.setSecondaryActionVisible(false);
        mBinding.toolbar.setImmersiveBottom(true);
        mBinding.toolbar.attachContent(mBinding.recycler, mBinding.recycler);
        ((ViewGroup) mBinding.filterOptions.getParent()).removeView(mBinding.filterOptions);
        mBinding.filterOptions.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        mBinding.filterOptions.setNestedScrollingEnabled(false);
        mBinding.filterOptions.setElevation(0);
        mBinding.filterOptions.setLayoutManager(new LinearLayoutManager(this));
        mBinding.filterOptions.setItemAnimator(null);
        showFilters();
        mAdapter = new RecommendBrowseAdapter(this);
        mAdapter.setHeader(mBinding.filterOptions);
        mAdapter.setHeaderVisible(mFiltersVisible);
        GridLayoutManager grid = new GridLayoutManager(this, gridColumns());
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return position < mAdapter.getHeaderCount() ? grid.getSpanCount() : 1;
            }
        });
        mBinding.recycler.setLayoutManager(grid);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter);
        mBinding.recycler.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (r-l != or-ol) grid.setSpanCount(gridColumns());
        });
        mBinding.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                GridLayoutManager layout = (GridLayoutManager) view.getLayoutManager();
                if (dy > 0 && layout != null && layout.findLastVisibleItemPosition() >= mAdapter.getItemCount() - 9) loadMore(false);
            }
        });
        mBinding.pageStatus.setOnClickListener(v -> loadMore(true));
        load();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setBackClickListener(view -> finish());
        mBinding.toolbar.setTitleClickListener(this::showChannels);
        mBinding.toolbar.setPrimaryActionClickListener(view -> {
            GridLayoutManager grid = (GridLayoutManager) mBinding.recycler.getLayoutManager();
            if (mFiltersVisible && grid.findFirstVisibleItemPosition() > 0) {
                mBinding.recycler.scrollToPosition(0);
                return;
            }
            mFiltersVisible = !mFiltersVisible;
            updateFilterToggle();
            if (mFiltersVisible) mBinding.recycler.scrollToPosition(0);
            mBinding.recycler.post(() -> {
                if (mBinding != null && !mBinding.recycler.canScrollVertically(1)) loadMore(false);
            });
        });
    }

    private void showChannels(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.START);
        for (int i = 0; i < mChannels.length; i++)
            menu.getMenu().add(0, i, i, mChannels[i]).setCheckable(true).setChecked(i == mChannel);
        menu.getMenu().setGroupCheckable(0, true, true);
        menu.setOnMenuItemClickListener(item -> { onChannelClick(item.getItemId()); return true; });
        menu.show();
    }

    private void updateFilterToggle() {
        mBinding.filterOptions.setVisibility(mFiltersVisible ? View.VISIBLE : View.GONE);
        if (mAdapter != null) mAdapter.setHeaderVisible(mFiltersVisible);
        mBinding.toolbar.setPrimaryAction(R.drawable.ic_filter_options, mFiltersVisible ? R.string.detail_collapse : R.string.detail_expand);
    }

    private void showFilters() {
        mBinding.filterOptions.setAdapter(new FilterAdapter(this, buildFilters()));
    }

    private List<Filter> buildFilters() {
        Map<String, String> selected = filters();
        List<Filter> result = new ArrayList<>();
        result.add(filter("genre", getString(R.string.recommend_filter_genre), selected.get("genre"), "",
                "全部", "剧情", "喜剧", "动作", "爱情", "科幻", "动画", "悬疑", "犯罪", "惊悚", "恐怖", "纪录片", "家庭", "儿童", "历史", "战争", "奇幻", "冒险", "武侠", "古装", "运动", "真人秀", "脱口秀"));
        result.add(filter("country", getString(R.string.recommend_filter_country), selected.get("country"), "",
                "全部", "中国大陆", "美国", "中国香港", "中国台湾", "日本", "韩国", "英国", "法国", "德国", "印度", "泰国", "俄罗斯", "加拿大", "澳大利亚", "其他"));
        result.add(filter("year", getString(R.string.recommend_filter_year), selected.get("year"), "",
                "全部", "2020年代", "2010年代", "2000年代", "90年代", "80年代", "70年代", "60年代", "更早"));
        result.add(filter("rating", getString(R.string.recommend_filter_rating), selected.get("rating"), "",
                "全部", "9分以上", "8分以上", "7分以上"));
        result.add(filter("sort", getString(R.string.recommend_filter_sort), selected.get("sort"), "U",
                "热门优先", "评分优先", "时间优先"));
        return result;
    }

    private Filter filter(String key, String name, String selected, String firstValue, String... labels) {
        List<Value> values = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            String value;
            if ("sort".equals(key)) value = i == 0 ? "U" : i == 1 ? "S" : "T";
            else if ("rating".equals(key)) value = i == 0 ? "" : String.valueOf(10 - i);
            else if ("year".equals(key)) value = yearValue(i);
            else value = i == 0 ? firstValue : labels[i];
            Value item = new Value(labels[i], value);
            item.setActivated(value.equals(selected));
            values.add(item);
        }
        return new Filter(key, name, values);
    }

    private String yearValue(int position) {
        switch (position) {
            case 1: return "2020,2029";
            case 2: return "2010,2019";
            case 3: return "2000,2009";
            case 4: return "1990,1999";
            case 5: return "1980,1989";
            case 6: return "1970,1979";
            case 7: return "1960,1969";
            case 8: return "1900,1959";
            default: return "";
        }
    }

    private Map<String, String> filters() {
        Map<String, String> values = mChannelFilters.get(mChannel);
        if (values != null) return values;
        values = new LinkedHashMap<>();
        values.put("genre", "");
        values.put("country", "");
        values.put("year", "");
        values.put("rating", "");
        values.put("sort", "U");
        mChannelFilters.put(mChannel, values);
        return values;
    }

    @Override
    public void setFilter(String key, Value value) {
        filters().put(key, value.isActivated() ? value.getV() : "sort".equals(key) ? "U" : "");
        load();
    }

    private void load() {
        ++mGeneration;
        if (mRequest != null) mRequest.cancel(true);
        mOffset = 0;
        mLoading = false;
        mHasMore = true;
        mFailed = false;
        mItems.clear();
        mAdapter.setItems(Collections.emptyList());
        mBinding.results.showContent();
        loadMore(true);
    }

    private void loadMore(boolean retry) {
        if (mBinding == null || mLoading || !mHasMore || (mFailed && !retry)) return;
        int generation = mGeneration, channel = mChannel, offset = mOffset;
        Map<String, String> selected = new LinkedHashMap<>(filters());
        mLoading = true;
        mFailed = false;
        mBinding.pageStatus.setText("正在加载…");
        mBinding.pageStatus.setVisibility(mItems.isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.pageStatus.setClickable(false);
        mRequest = mExecutor.submit(() -> {
            List<Douban.Item> items = Collections.emptyList();
            boolean failed = false;
            try {
                double rating = selected.get("rating").isEmpty() ? 0 : Double.parseDouble(selected.get("rating"));
                items = Douban.search(TAGS[channel], selected.get("genre"), selected.get("country"),
                        selected.get("year"), selected.get("sort"), rating, offset, 24);
                Logger.d("RecommendFilter: channel=" + channel + " start=" + offset + " filters=" + selected + " count=" + items.size());
            } catch (Exception error) {
                failed = true;
                Logger.e("RecommendFilter: channel=" + channel + " start=" + offset + " filters=" + selected, error);
            }
            List<Douban.Item> result = items;
            boolean error = failed;
            App.post(() -> show(result, generation, channel, error));
        });
    }

    private void show(List<Douban.Item> items, int generation, int channel, boolean failed) {
        if (mBinding == null || generation != mGeneration || channel != mChannel) return;
        mLoading = false;
        mFailed = failed;
        if (!failed) {
            int before = mItems.size();
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Douban.Item item : mItems) ids.add(item.getId());
            for (Douban.Item item : items) if (ids.add(item.getId())) mItems.add(item);
            mOffset += items.size();
            mHasMore = items.size() >= 24 && mItems.size() > before;
            mAdapter.addItems(mItems.subList(before, mItems.size()));
        }
        if (mItems.isEmpty()) {
            mBinding.results.showContent();
            mBinding.pageStatus.setText(failed ? "加载失败，点击重试" : "没有匹配影片，请调整筛选条件");
            mBinding.pageStatus.setVisibility(View.VISIBLE);
            mBinding.pageStatus.setClickable(failed);
        } else {
            mBinding.results.showContent();
            mBinding.pageStatus.setText("加载失败，点击重试");
            mBinding.pageStatus.setVisibility(failed ? View.VISIBLE : View.GONE);
            mBinding.pageStatus.setClickable(failed);
            mBinding.recycler.post(() -> {
                if (mBinding != null && generation == mGeneration && !failed && !mBinding.recycler.canScrollVertically(1)) loadMore(false);
            });
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void onChannelClick(int position) {
        if (position < 0 || position >= TAGS.length || position == mChannel) return;
        mChannel = position;
        mBinding.toolbar.setTitle(mChannels[mChannel]);
        showFilters();
        load();
    }

    @Override
    public void onItemClick(Douban.Item item) {
        VideoActivity.find(this, item.getTitle(), item.getPic(), item.getYear(), item.getRating());
    }

    private int gridColumns() {
        Configuration configuration = getResources().getConfiguration();
        int width = configuration.screenWidthDp;
        return Math.max(2, Math.min(8, width / 138));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("selected_channel", mChannel);
        outState.putBoolean("filters_visible", mFiltersVisible);
        for (Map.Entry<Integer, Map<String, String>> entry : mChannelFilters.entrySet()) {
            Bundle values = new Bundle();
            for (Map.Entry<String, String> value : entry.getValue().entrySet()) values.putString(value.getKey(), value.getValue());
            outState.putBundle("filters_" + entry.getKey(), values);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        mGeneration++;
        if (mRequest != null) mRequest.cancel(true);
        mExecutor.shutdownNow();
        mBinding = null;
        super.onDestroy();
    }
}
