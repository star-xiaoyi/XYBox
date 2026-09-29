package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.airbnb.lottie.LottieAnimationView;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentHomeSearchBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.search.SearchTask;
import com.fongmi.android.tv.search.VodGroup;
import com.fongmi.android.tv.search.VodGrouper;
import com.fongmi.android.tv.search.VodSource;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.SearchGroupAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 首页搜索框下方的搜索结果，也是 App 里唯一的搜索结果页。
 * <p>
 * 所有站点并发搜，同名同年的合并成一行。站点是陆续返回的：先攒一小会儿再首次展示，
 * 让同名的那部排在第一；展示之后已有的行不再挪动，新片只接在末尾。
 */
public class HomeSearchFragment extends BaseFragment implements SearchTask.Callback, SearchGroupAdapter.OnClickListener {

    private static final String ARG_KEYWORD = "keyword";
    /** 已经搜到同名的片就不用再等，这个时间一到就展示。 */
    private static final long SHOW_EXACT = 1200;
    /** 没有同名的也最多等这么久，再久用户会以为卡住了。 */
    private static final long SHOW_ANY = 2500;

    private FragmentHomeSearchBinding mBinding;
    private SearchGroupAdapter mAdapter;
    private ExecutorService mFiller;
    private VodGrouper mGrouper;
    private Runnable mShowCheck;
    private SearchTask mTask;
    private long mStartTime;
    private int mGeneration;
    private boolean mShown;

    public static HomeSearchFragment newInstance(String keyword) {
        HomeSearchFragment fragment = new HomeSearchFragment();
        Bundle args = new Bundle();
        args.putString(ARG_KEYWORD, keyword);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentHomeSearchBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mShowCheck = this::checkShow;
        mFiller = Executors.newFixedThreadPool(3);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter = new SearchGroupAdapter(this));
        String keyword = getKeyword();
        if (!TextUtils.isEmpty(keyword)) search(keyword);
    }

    private String getKeyword() {
        return getArguments() == null ? "" : getArguments().getString(ARG_KEYWORD, "");
    }

    public void search(String keyword) {
        if (mBinding == null || TextUtils.isEmpty(keyword)) return;
        getArguments().putString(ARG_KEYWORD, keyword);
        stopSearch();
        mGeneration++;
        mShown = false;
        mAdapter.clear();
        mGrouper = new VodGrouper(keyword);
        mStartTime = SystemClock.elapsedRealtime();
        mBinding.recycler.scrollToPosition(0);
        mBinding.emptyLayout.getRoot().setVisibility(View.GONE);
        mBinding.searchProgress.getRoot().setVisibility(View.VISIBLE);
        mTask = SearchTask.start(getSites(), keyword, false, this);
        App.post(mShowCheck, SHOW_EXACT);
        App.post(mShowCheck, SHOW_ANY);
        updateStatus();
    }

    /** 可搜索的站点，首页站点排第一个。 */
    private List<Site> getSites() {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (site.isSearchable() && !site.isCloudDrive()) sites.add(site);
        Site home = VodConfig.get().getHome();
        if (sites.remove(home)) sites.add(0, home);
        return sites;
    }

    private void stopSearch() {
        App.removeCallbacks(mShowCheck);
        if (mTask != null) mTask.cancel();
        mTask = null;
    }

    @Override
    public void onResult(List<Vod> items, long cost) {
        if (mBinding == null) return;
        List<VodGroup> added = new ArrayList<>();
        Set<VodGroup> updated = new LinkedHashSet<>();
        mGrouper.add(items, cost, added, updated);
        if (mShown) {
            mAdapter.addAll(added);
            mAdapter.update(updated);
        } else {
            checkShow();
        }
        updateStatus();
    }

    @Override
    public void onFinish() {
        if (mBinding == null) return;
        updateStatus();
        checkShow();
    }

    private void checkShow() {
        if (mBinding == null || mShown || mTask == null) return;
        boolean finished = mTask.isFinished();
        long elapsed = SystemClock.elapsedRealtime() - mStartTime;
        if (mGrouper.isEmpty()) {
            if (finished) showEmpty();
        } else if (finished || elapsed >= SHOW_ANY || (elapsed >= SHOW_EXACT && mGrouper.hasExact())) {
            showResult();
        }
    }

    private void showResult() {
        mShown = true;
        App.removeCallbacks(mShowCheck);
        mAdapter.setItems(mGrouper.sorted());
        mBinding.searchProgress.getRoot().setVisibility(View.GONE);
        mBinding.emptyLayout.getRoot().setVisibility(View.GONE);
    }

    private void showEmpty() {
        mShown = true;
        App.removeCallbacks(mShowCheck);
        mBinding.searchProgress.getRoot().setVisibility(View.GONE);
        mBinding.emptyLayout.getRoot().setVisibility(View.VISIBLE);
        LottieAnimationView animation = mBinding.emptyLayout.getRoot().findViewById(R.id.lottieAnimation);
        if (animation != null) animation.playAnimation();
    }

    private void updateStatus() {
        boolean searching = mTask != null && !mTask.isFinished() && mTask.getTotal() > 0;
        mBinding.status.setVisibility(searching ? View.VISIBLE : View.GONE);
        if (searching) mBinding.status.setText(getString(R.string.search_progress, mTask.getDone(), mTask.getTotal()));
    }

    @Override
    public void onItemClick(VodGroup item) {
        if (item.isFolder()) {
            Vod vod = item.first().getVod();
            FolderActivity.start(requireActivity(), vod.getSiteKey(), Result.folder(vod));
        } else {
            VideoActivity.group(requireActivity(), item);
        }
    }

    /**
     * 多数站点的搜索接口不带简介，这一行滑进屏幕时向接口最快的那个源补拉一次详情。
     * 每部片只拉一次；换了关键词后迟到的结果直接丢掉。
     */
    @Override
    public void onFill(VodGroup item) {
        item.fill(null);
        VodSource source = item.best();
        int generation = mGeneration;
        mFiller.execute(() -> {
            Vod vod = null;
            try {
                Result result = SiteViewModel.detail(source.getSite(), source.getVodId(), false);
                if (!result.getList().isEmpty()) vod = result.getList().get(0);
            } catch (Throwable ignored) {
            }
            if (vod == null) return;
            Vod detail = vod;
            App.post(() -> {
                if (mBinding == null || generation != mGeneration) return;
                item.fill(detail);
                mAdapter.update(item);
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mTask != null) mTask.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mTask != null) mTask.pause();
    }

    @Override
    public void onDestroyView() {
        mGeneration++;
        stopSearch();
        if (mFiller != null) mFiller.shutdownNow();
        mBinding = null;
        super.onDestroyView();
    }
}
