package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.Douban;
import com.fongmi.android.tv.databinding.ActivityRecommendListBinding;
import com.fongmi.android.tv.ui.adapter.RecommendAdapter;
import com.fongmi.android.tv.ui.adapter.RecommendListTabAdapter;
import com.fongmi.android.tv.ui.adapter.RecommendRankAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.Collections;
import java.util.List;

public class RecommendListActivity extends BaseActivity implements RecommendAdapter.OnClickListener {

    private static final String EXTRA_COLLECTION = "collection";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_COUNT = "count";
    private static final String[] COLLECTIONS = {
            "movie_weekly_best", "movie_hot_gaia", "tv_hot", "tv_animation", "tv_variety_show", "movie_top250"
    };
    private static final int[] TITLES = {
            R.string.recommend_weekly, R.string.recommend_movie_hot, R.string.recommend_tv_hot,
            R.string.recommend_animation_hot, R.string.recommend_variety_hot, R.string.recommend_top250
    };
    private static final int[] COUNTS = {10, 100, 100, 60, 100, 250};

    private ActivityRecommendListBinding mBinding;
    private RecommendRankAdapter mAdapter;
    private RecommendListTabAdapter mTabAdapter;
    private String mCollection;
    private int mCount;
    private int mSelected;
    private int mGeneration;

    public static void start(Activity activity, String collection, CharSequence title, int count) {
        Intent intent = new Intent(activity, RecommendListActivity.class);
        intent.putExtra(EXTRA_COLLECTION, collection);
        intent.putExtra(EXTRA_TITLE, title == null ? "" : title.toString());
        intent.putExtra(EXTRA_COUNT, count);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityRecommendListBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mCollection = getIntent().getStringExtra(EXTRA_COLLECTION);
        mCount = Math.max(1, getIntent().getIntExtra(EXTRA_COUNT, 100));
        mSelected = findCollection(mCollection);
        CharSequence[] titles = new CharSequence[TITLES.length];
        for (int i = 0; i < TITLES.length; i++) titles[i] = getString(TITLES[i]);
        CharSequence initialTitle = getIntent().getStringExtra(EXTRA_TITLE);
        mBinding.rankTitle.setText(initialTitle);
        mBinding.toolbar.setTitle("");
        mBinding.toolbar.setPrimaryActionVisible(false);
        mBinding.toolbar.setSecondaryActionVisible(false);
        mBinding.toolbar.attachContent(mBinding.content, mBinding.recycler);
        mBinding.rankTabs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mBinding.rankTabs.setAdapter(mTabAdapter = new RecommendListTabAdapter(titles, mSelected, this::selectCollection));
        mBinding.rankTabs.setItemAnimator(null);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, listColumns()));
        mBinding.recycler.setAdapter(mAdapter = new RecommendRankAdapter(this));
        mBinding.recycler.setItemAnimator(null);
        mBinding.rankTabs.post(() -> mBinding.rankTabs.scrollToPosition(mSelected));
        load();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setBackClickListener(view -> finish());
    }

    private void load() {
        int generation = ++mGeneration;
        mBinding.content.setEmpty(getString(R.string.recommend_error), view -> load());
        mBinding.content.showProgress();
        App.execute(() -> {
            List<Douban.Item> items = Collections.emptyList();
            try {
                items = Douban.fetch(mCollection, 0, mCount).getItems();
            } catch (Exception ignored) {
            }
            List<Douban.Item> result = items;
            App.post(() -> show(result, generation));
        });
    }

    private void show(List<Douban.Item> items, int generation) {
        if (mBinding == null || generation != mGeneration) return;
        if (items.isEmpty()) {
            mBinding.content.showEmpty();
        } else {
            mAdapter.setItems(items);
            ImgUtil.rect(items.get(0).getTitle(), items.get(0).getPic(), mBinding.rankHero);
            mBinding.content.showContent();
        }
    }

    private void selectCollection(int position) {
        if (position < 0 || position >= COLLECTIONS.length || position == mSelected) return;
        mSelected = position;
        mCollection = COLLECTIONS[position];
        mCount = COUNTS[position];
        mTabAdapter.setSelected(position);
        mBinding.rankTitle.setText(TITLES[position]);
        mBinding.rankTabs.smoothScrollToPosition(position);
        mBinding.recycler.scrollToPosition(0);
        load();
    }

    private int findCollection(String collection) {
        for (int i = 0; i < COLLECTIONS.length; i++) {
            if (COLLECTIONS[i].equals(collection)) return i;
        }
        return 0;
    }

    private int listColumns() {
        Configuration configuration = getResources().getConfiguration();
        return configuration.smallestScreenWidthDp >= 600
                || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ? 2 : 1;
    }

    @Override
    public void onItemClick(Douban.Item item) {
        VideoActivity.find(this, item.getTitle(), item.getPic(), item.getYear(), item.getRating());
    }

    @Override
    protected void onDestroy() {
        mGeneration++;
        mBinding = null;
        super.onDestroy();
    }
}
