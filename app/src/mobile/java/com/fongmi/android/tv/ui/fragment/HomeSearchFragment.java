package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.airbnb.lottie.LottieAnimationView;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentHomeSearchBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.adapter.VodAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.utils.PauseExecutor;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchResultOptimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Search results embedded below the home page search bar. */
public class HomeSearchFragment extends BaseFragment implements CustomScroller.Callback,
        CollectAdapter.OnClickListener, VodAdapter.OnClickListener {

    private static final String ARG_KEYWORD = "keyword";
    private FragmentHomeSearchBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private RecordAdapter mRecordAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private PauseExecutor mExecutor;
    private final List<Site> mSites = new ArrayList<>();
    private int mGeneration;
    private boolean mResultReady;

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
        mScroller = new CustomScroller(this);
        setRecyclerView();
        setViewModel();
        setViewType(Setting.getViewType(ViewType.GRID));
        mRecordAdapter = new RecordAdapter(new RecordAdapter.OnClickListener() {
            @Override
            public void onItemClick(String text) {
            }

            @Override
            public void onDataChanged(int size) {
            }
        });
        String keyword = getArguments() == null ? "" : getArguments().getString(ARG_KEYWORD, "");
        if (!TextUtils.isEmpty(keyword)) search(keyword);
    }

    private void setRecyclerView() {
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.search.observe(getViewLifecycleOwner(), result -> {
            if (mBinding == null) return;
            String keyword = getKeyword();
            List<Vod> optimized = SearchResultOptimizer.optimize(result.getList(), keyword);
            if (optimized.isEmpty()) return;
            if (mCollectAdapter.getPosition() == 0) mSearchAdapter.addAll(optimized);
            mCollectAdapter.add(Collect.create(optimized));
            mCollectAdapter.add(optimized);
            if (mSearchAdapter.getItemCount() > 0) showResults();
        });
        mViewModel.result.observe(getViewLifecycleOwner(), result -> {
            if (mBinding == null) return;
            boolean same = !result.getList().isEmpty() && mCollectAdapter.getActivated().getSite().equals(result.getList().get(0).getSite());
            if (same) mCollectAdapter.getActivated().getList().addAll(result.getList());
            if (same) mSearchAdapter.addAll(result.getList());
            mScroller.endLoading(result);
        });
    }

    private String getKeyword() {
        return getArguments() == null ? "" : getArguments().getString(ARG_KEYWORD, "");
    }

    public void search(String keyword) {
        if (mBinding == null || TextUtils.isEmpty(keyword)) return;
        getArguments().putString(ARG_KEYWORD, keyword);
        int generation = ++mGeneration;
        mResultReady = false;
        mScroller.reset();
        mSearchAdapter.clear();
        mCollectAdapter.clear();
        mBinding.emptyLayout.getRoot().setVisibility(View.GONE);
        mBinding.searchProgress.getRoot().setVisibility(View.VISIBLE);
        if (mExecutor != null) mExecutor.shutdownNow();
        mSites.clear();
        for (Site site : VodConfig.get().getSites()) if (site.isSearchable()) mSites.add(site);
        Site home = VodConfig.get().getHome();
        if (mSites.contains(home)) {
            mSites.remove(home);
            mSites.add(0, home);
        }
        if (mSites.isEmpty()) {
            finishSearch(generation);
            return;
        }
        mExecutor = new PauseExecutor(Math.min(20, mSites.size()));
        AtomicInteger pending = new AtomicInteger(mSites.size());
        for (Site site : mSites) {
            mExecutor.execute(() -> {
                try {
                    mViewModel.searchContent(site, keyword, false);
                } catch (Throwable ignored) {
                } finally {
                    if (pending.decrementAndGet() == 0) App.post(() -> finishSearch(generation), 300);
                }
            });
        }
        App.post(() -> {
            if (mBinding != null && generation == mGeneration) mRecordAdapter.add(keyword);
        }, 250);
    }

    private void finishSearch(int generation) {
        if (mBinding == null || generation != mGeneration) return;
        mBinding.searchProgress.getRoot().setVisibility(View.GONE);
        boolean empty = mSearchAdapter.getItemCount() == 0;
        mBinding.emptyLayout.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            LottieAnimationView animation = mBinding.emptyLayout.getRoot().findViewById(R.id.lottieAnimation);
            if (animation != null) animation.playAnimation();
        } else {
            notifyResultReady();
        }
    }

    private void showResults() {
        mBinding.searchProgress.getRoot().setVisibility(View.GONE);
        mBinding.emptyLayout.getRoot().setVisibility(View.GONE);
        notifyResultReady();
    }

    private void notifyResultReady() {
        if (mResultReady) return;
        mResultReady = true;
        if (getParentFragment() instanceof VodFragment) {
            ((VodFragment) getParentFragment()).onHomeSearchResultsReady();
        }
    }

    private void setViewType(int viewType) {
        if (mBinding == null) return;
        int count = Math.max(1, Product.getColumn(requireContext()) - 1);
        mSearchAdapter.setViewType(viewType, count);
        mSearchAdapter.setSize(Product.getSpec(requireContext(), ResUtil.dp2px(128 + count * 16), count));
        ((GridLayoutManager) mBinding.recycler.getLayoutManager()).setSpanCount(mSearchAdapter.isGrid() ? count : 1);
    }

    public void toggleView() {
        setViewType(mSearchAdapter.isGrid() ? ViewType.LIST : ViewType.GRID);
    }

    public boolean isGrid() {
        return mSearchAdapter == null || mSearchAdapter.isGrid();
    }

    @Override
    public void onItemClick(int position, Collect item) {
        mBinding.recycler.scrollToPosition(0);
        mCollectAdapter.setActivated(position);
        mSearchAdapter.setAll(item.getList());
        mScroller.setPage(item.getPage());
        mScroller.setLoading(false);
        mScroller.setEnable(0);
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isFolder()) FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public void onLoadMore(String page) {
        if (mBinding == null) return;
        Collect activated = mCollectAdapter.getActivated();
        if ("all".equals(activated.getSite().getKey())) return;
        mViewModel.searchContent(activated.getSite(), getKeyword(), page);
        activated.setPage(Integer.parseInt(page));
        mScroller.setLoading(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mExecutor != null) mExecutor.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mExecutor != null) mExecutor.pause();
    }

    @Override
    public void onDestroyView() {
        mGeneration++;
        if (mExecutor != null) mExecutor.shutdownNow();
        mBinding = null;
        super.onDestroyView();
    }
}
