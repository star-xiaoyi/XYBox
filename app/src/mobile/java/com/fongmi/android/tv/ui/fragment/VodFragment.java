package com.fongmi.android.tv.ui.fragment;
import com.github.catvod.utils.Logger;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Hot;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Suggest;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.databinding.FragmentVodBinding;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.impl.FilterCallback;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.KeepActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.airbnb.lottie.LottieAnimationView;
import com.fongmi.android.tv.ui.adapter.HistoryCardAdapter;
import com.fongmi.android.tv.ui.adapter.SearchSuggestionAdapter;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.FilterDialog;
import com.fongmi.android.tv.ui.dialog.LastWatchToast;
import com.fongmi.android.tv.ui.dialog.LinkDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.WebDAVSyncManager;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;
import com.google.android.material.appbar.AppBarLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Response;

public class VodFragment extends BaseFragment implements SiteCallback, FilterCallback, TypeAdapter.OnClickListener, ConfigCallback {

    private FragmentVodBinding mBinding;
    private SiteViewModel mViewModel;
    private TypeAdapter mAdapter;
    private HistoryCardAdapter mHistoryAdapter;
    private SearchSuggestionAdapter mSuggestionAdapter;
    private Runnable mRunnable;
    private Runnable mSuggestRunnable;
    private List<String> mHots;
    private Result mResult;
    private int mAppBarOffset;
    private String mSuggestedKeyword = "";
    private boolean mSearchEditing;
    private boolean mSearchResultsVisible;
    private boolean mSearchViewReady;
    private boolean mSearchHeaderExpanded;
    private boolean mHeaderAnimationReady;
    private int mSuggestionGeneration;

    public static VodFragment newInstance() {
        return new VodFragment();
    }

    private TypeFragment getFragment() {
        return (TypeFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    @Nullable
    private TypeFragment getFragmentOrNull() {
        if (mBinding == null || mBinding.pager.getAdapter() == null || mBinding.pager.getAdapter().getCount() == 0) return null;
        Object item = mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
        return item instanceof TypeFragment ? (TypeFragment) item : null;
    }

    private Site getSite() {
        return VodConfig.get().getHome();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        setRecyclerView();
        setViewModel();
        setupHistoryRecycler();
        mBinding.swipeLayout.setProgressBackgroundColorSchemeColor(0xFF1A1A1A);
        mBinding.swipeLayout.setColorSchemeColors(0xFFFFEB3B);
        initStartupState(); // 根据是否已有配置来设置初始状态
        setLogo();
        initHot();
        getHot();
        loadHistory();
        // 检查是否需要显示上次播放弹窗
        checkLastWatchDialog();
    }
    
    // 初始化启动状态：区分已有配置和无配置的情况
    private void initStartupState() {
        // 检查是否已经有保存的配置，添加空值检查
        boolean hasExistingConfig = false;
        try {
            Config config = VodConfig.get().getConfig();
            hasExistingConfig = config != null && 
                               config.getUrl() != null && 
                               !config.getUrl().isEmpty();
        } catch (Exception e) {
            // 如果获取配置时出错，认为没有配置
            hasExistingConfig = false;
        }
        
        if (hasExistingConfig) {
            // 已有配置：显示加载状态，确保不显示添加源提示
            showProgress();
            mBinding.emptySourceHint.setVisibility(View.GONE);
        } else {
            // 无配置：立即显示空源提示，不显示加载状态
            hideProgress();
            checkEmptySource();
        }
    }

    @Override
    protected void initEvent() {
        mBinding.hot.setOnFocusChangeListener((view, hasFocus) -> {
            mBinding.hot.setCursorVisible(hasFocus);
            if (hasFocus) enterSearchEditing();
        });
        mBinding.hot.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                submitHomeSearch();
                return true;
            }
            return false;
        });
        mBinding.hot.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!mSearchEditing || !mBinding.hot.hasFocus()) return;
                scheduleSearchSuggestions(editable.toString().trim());
            }
        });
        mBinding.top.setOnClickListener(this::onTop);
        mBinding.link.setOnClickListener(this::onLink);
        mBinding.logo.setOnClickListener(this::onLogo);
        mBinding.keep.setOnClickListener(this::onKeep);
        mBinding.retry.setOnClickListener(this::onRetry);
        mBinding.filter.setOnClickListener(this::onFilter);
        mBinding.search.setOnClickListener(this::onSearchAction);
        mBinding.searchBack.setOnClickListener(this::onSearchBack);
        mBinding.history.setOnClickListener(this::onHistory);
        mBinding.historyMore.setOnClickListener(this::onHistory);
        mBinding.swipeLayout.setOnRefreshListener(this::onPullRefresh);
        mBinding.appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> mAppBarOffset = verticalOffset);
        mBinding.swipeLayout.setOnChildScrollUpCallback((parent, child) -> {
            if (mAppBarOffset != 0) return true;
            TypeFragment fragment = getFragmentOrNull();
            return fragment != null && fragment.canScrollUp();
        });
        mBinding.filter.setOnLongClickListener(this::onLink);
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.type.smoothScrollToPosition(position);
                mAdapter.setActivated(position);
                setFabVisible(position);
            }
        });
        mBinding.getRoot().requestFocus();
        mBinding.hot.clearFocus();
        mBinding.hot.setCursorVisible(false);
        setSearchHeaderExpanded(false);
        mHeaderAnimationReady = true;
    }

    // 添加检查上次播放历史并显示弹窗的方法
    private void checkLastWatchDialog() {
        if (App.isAppJustLaunched()) {
            List<History> histories = History.get();
            if (!histories.isEmpty()) {
                App.setAppLaunched();
                App.post(() -> {
                    if (getActivity() != null) {
                        LastWatchToast.create(getActivity(), histories.get(0)).show();
                    }
                }, 1000);
            } else {
                App.setAppLaunched();
            }
        }
    }

    private void setRecyclerView() {
        mBinding.type.setHasFixedSize(true);
        mBinding.type.setItemAnimator(null);
        mBinding.type.setAdapter(mAdapter = new TypeAdapter(this));
        mBinding.searchSuggestions.setHasFixedSize(true);
        mBinding.searchSuggestions.setItemAnimator(null);
        mBinding.searchSuggestions.setAdapter(mSuggestionAdapter = new SearchSuggestionAdapter(this::onSuggestionClick));
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(getViewLifecycleOwner(), result -> setAdapter(mResult = result));
    }

    private void initHot() {
        mHots = Hot.get(Setting.getHot());
        App.post(mRunnable = this::updateHot, 0);
    }

    private void getHot() {
        OkHttp.newCall("https://api.web.360kan.com/v1/rank?cat=1", Headers.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                mHots = Hot.get(response.body().string());
            }
        });
    }

    private void updateHot() {
        App.post(mRunnable, TimeUnit.SECONDS.toMillis(10));
        if (mBinding == null || mHots.isEmpty()) return;
        mSuggestedKeyword = mHots.get(new Random().nextInt(mHots.size()));
        if (!mSearchEditing && !mSearchResultsVisible) mBinding.hot.setText(mSuggestedKeyword);
    }

    private Result handle(Result result) {
        List<Class> types = new ArrayList<>();
        for (Class type : result.getTypes()) if (result.getFilters().containsKey(type.getTypeId())) type.setFilters(result.getFilters().get(type.getTypeId()));
        for (String cate : getSite().getCategories()) for (Class type : result.getTypes()) if (cate.equals(type.getTypeName())) types.add(type);
        result.setTypes(types);
        return result;
    }

    private void setAdapter(Result result) {
        mAdapter.addAll(handle(result));
        mBinding.pager.getAdapter().notifyDataSetChanged();
        setFabVisible(0);
        hideProgress();
        checkRetry();
        checkEmptySource(); // 添加检查是否显示空源提示
        setRefreshing(false);
    }

    // 修改checkEmptySource方法，增强鲁棒性
    private void checkEmptySource() {
        // 检查是否有基础配置文件，添加空值检查
        boolean hasBaseConfig = false;
        try {
            Config config = VodConfig.get().getConfig();
            hasBaseConfig = config != null && 
                           config.getUrl() != null && 
                           !config.getUrl().isEmpty();
        } catch (Exception e) {
            hasBaseConfig = false;
        }
        
        // 检查是否有有效的站点配置
        boolean hasValidSites = false;
        boolean hasValidHome = false;
        try {
            hasValidSites = VodConfig.get().getSites().size() > 0;
            Site site = getSite();
            hasValidHome = site != null && site.getKey() != null && !site.getKey().isEmpty();
        } catch (Exception e) {
            hasValidSites = false;
            hasValidHome = false;
        }
        
        // 只有在完全没有配置文件或配置文件无效时才显示空源提示
        boolean isEmpty = !hasBaseConfig || (!hasValidSites || !hasValidHome);
        
        if (mBinding.emptySourceHint != null) {
            mBinding.emptySourceHint.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            if (isEmpty) {
                // 设置整个布局的点击事件
                mBinding.emptySourceHint.setOnClickListener(this::onAddSource);
                // 设置按钮的点击事件
                if (mBinding.addSourceBtn != null) {
                    mBinding.addSourceBtn.setOnClickListener(this::onAddSource);
                }
                // 空源状态下隐藏所有悬浮按钮
                hideFabButtons();
                // 启动Lottie动画
                try {
                    LottieAnimationView lottieView = mBinding.emptySourceHint.findViewById(R.id.lottieAnimation);
                    if (lottieView != null) {
                        lottieView.playAnimation();
                    }
                } catch (Exception e) {
                    // 忽略错误
                }
            }
        }
    }
    
    // 添加源按钮点击事件处理
    private void onAddSource(View view) {
        ConfigDialog.create(this).type(0).show();
    }
    
    // 实现ConfigCallback接口
    @Override
    public void setConfig(Config config) {
        android.util.Log.d("VodFragment", "setConfig called with: " + (config != null ? config.toString() : "null"));
        
        if (config == null || config.isEmpty()) {
            android.util.Log.d("VodFragment", "Config is null or empty, returning");
            return;
        }
        
        // 检查Fragment是否还在活动状态，增强检查
        if (!isValidFragmentState()) {
            android.util.Log.d("VodFragment", "Fragment state invalid, returning");
            return;
        }
        
        android.util.Log.d("VodFragment", "Fragment state valid, proceeding with config load");
        
        // 安全地隐藏空源提示
        try {
            if (mBinding != null && mBinding.emptySourceHint != null) {
                mBinding.emptySourceHint.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Logger.e("Error", e);
        }
        
        Notify.progress(getActivity());
        android.util.Log.d("VodFragment", "Calling VodConfig.load");
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                android.util.Log.d("VodFragment", "VodConfig.load success callback");
                // 双重检查Fragment是否还在活动状态
                if (!isValidFragmentState()) {
                    android.util.Log.d("VodFragment", "Fragment state invalid in success callback");
                    return;
                }
                
                try {
                    android.util.Log.d("VodFragment", "Success: dismissing notify and refreshing");
                    Notify.dismiss();
                    RefreshEvent.config();
                    RefreshEvent.video();
                    homeContent();
                } catch (Exception e) {
                    android.util.Log.e("VodFragment", "Error in success callback", e);
                    Logger.e("Error", e);
                }
            }
            
            @Override
            public void error(String msg) {
                android.util.Log.e("VodFragment", "VodConfig.load error: " + msg);
                // 双重检查Fragment是否还在活动状态
                if (!isValidFragmentState()) {
                    android.util.Log.d("VodFragment", "Fragment state invalid in error callback");
                    return;
                }
                
                try {
                    Notify.dismiss();
                    Notify.show(msg);
                    // 加载失败时重新显示空源提示
                    checkEmptySource();
                } catch (Exception e) {
                    android.util.Log.e("VodFragment", "Error in error callback", e);
                    Logger.e("Error", e);
                }
            }
        });
    }
    
    // 添加Fragment状态检查方法
    private boolean isValidFragmentState() {
        return getActivity() != null && 
               !getActivity().isFinishing() && 
               !getActivity().isDestroyed() && 
               isAdded() && 
               !isDetached() && 
               !isRemoving() &&
               getView() != null &&
               mBinding != null;
    }

    private void setFabVisible(int position) {
        // 检查是否为空源状态 - 使用与checkEmptySource相同的逻辑，添加空值检查
        boolean hasBaseConfig = false;
        boolean hasValidSites = false;
        boolean hasValidHome = false;
        
        try {
            Config config = VodConfig.get().getConfig();
            hasBaseConfig = config != null && 
                           config.getUrl() != null && 
                           !config.getUrl().isEmpty();
            
            hasValidSites = VodConfig.get().getSites().size() > 0;
            
            Site site = getSite();
            hasValidHome = site != null && site.getKey() != null && !site.getKey().isEmpty();
        } catch (Exception e) {
            hasBaseConfig = false;
            hasValidSites = false;
            hasValidHome = false;
        }
        
        boolean isEmpty = !hasBaseConfig || (!hasValidSites || !hasValidHome);
        
        if (isEmpty) {
            // 空源状态下隐藏所有悬浮按钮
            hideFabButtons();
        } else if (mAdapter.getItemCount() == 0) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.link.setVisibility(View.VISIBLE);
            mBinding.filter.setVisibility(View.GONE);
        } else if (!mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.link.setVisibility(View.GONE);
            mBinding.filter.show();
        } else if (position == 0 || mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.filter.setVisibility(View.GONE);
            mBinding.link.show();
        }
    }
    
    // 隐藏所有悬浮按钮的方法
    private void hideFabButtons() {
        mBinding.top.setVisibility(View.GONE);
        mBinding.link.setVisibility(View.GONE);
        mBinding.filter.setVisibility(View.GONE);
    }

    private void checkRetry() {
        mBinding.retry.setVisibility(mAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void onTop(View view) {
        getFragment().scrollToTop();
        mBinding.top.setVisibility(View.INVISIBLE);
        if (mBinding.filter.getVisibility() == View.INVISIBLE) mBinding.filter.show();
        else if (mBinding.link.getVisibility() == View.INVISIBLE) mBinding.link.show();
    }

    private boolean onLink(View view) {
        LinkDialog.create(this).show();
        return true;
    }

    private void onLogo(View view) {
        SiteDialog.create(this).change().show();
    }

    private void onKeep(View view) {
        KeepActivity.start(getActivity());
    }

    private void onRetry(View view) {
        homeContent();
    }

    private void onFilter(View view) {
        if (mAdapter.getItemCount() > 0) FilterDialog.create().filter(mAdapter.get(mBinding.pager.getCurrentItem()).getFilters()).show(this);
    }

    private void enterSearchEditing() {
        if (mBinding == null) return;
        mSearchEditing = true;
        setSearchHeaderExpanded(true);
        setBottomNavigationVisible(false);
        mBinding.search.setImageResource(R.drawable.ic_action_search);
        mBinding.appBar.setExpanded(true, true);
        scheduleSearchSuggestions(mBinding.hot.getText().toString().trim());
    }

    private void onSearchBack(View view) {
        if (mSearchResultsVisible) hideSearchContent();
        else cancelSearchEditing();
    }

    private void onSearchAction(View view) {
        HomeSearchFragment fragment = getHomeSearchFragment();
        if (mSearchResultsVisible && mSearchViewReady && !mSearchEditing && fragment != null) {
            fragment.toggleView();
            updateSearchActionIcon(fragment);
        } else {
            submitHomeSearch();
        }
    }

    private void submitHomeSearch() {
        if (mBinding == null) return;
        String keyword = mBinding.hot.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) return;
        mSearchEditing = false;
        mSearchViewReady = false;
        hideSearchSuggestions();
        mBinding.hot.setCursorVisible(false);
        Util.hideKeyboard(mBinding.hot);
        mBinding.hot.clearFocus();
        setSearchHeaderExpanded(true);
        showSearchContent();
        mBinding.search.setImageResource(R.drawable.ic_action_search);
        HomeSearchFragment fragment = getHomeSearchFragment();
        if (fragment == null) {
            fragment = HomeSearchFragment.newInstance(keyword);
            getChildFragmentManager().beginTransaction()
                    .replace(mBinding.searchContent.getId(), fragment, "home_search")
                    .commitNowAllowingStateLoss();
        } else {
            fragment.search(keyword);
        }
    }

    private void showSearchContent() {
        mSearchResultsVisible = true;
        mBinding.appBar.setExpanded(true, false);
        setHeaderPinned(true);
        mBinding.historySection.setVisibility(View.GONE);
        mBinding.historyRecycler.setVisibility(View.GONE);
        mBinding.type.setVisibility(View.GONE);
        mBinding.pager.setVisibility(View.GONE);
        mBinding.searchContent.setVisibility(View.VISIBLE);
        mBinding.emptySourceHint.setVisibility(View.GONE);
        mBinding.retry.setVisibility(View.GONE);
        mBinding.progress.getRoot().setVisibility(View.GONE);
        mBinding.swipeLayout.setEnabled(false);
        setBottomNavigationVisible(false);
        hideFabButtons();
    }

    private void hideSearchContent() {
        if (mBinding == null) return;
        mSearchEditing = false;
        mSearchResultsVisible = false;
        mSearchViewReady = false;
        hideSearchSuggestions();
        Util.hideKeyboard(mBinding.hot);
        mBinding.hot.clearFocus();
        mBinding.hot.setCursorVisible(false);
        mBinding.search.setImageResource(R.drawable.ic_action_search);
        mBinding.searchContent.setVisibility(View.GONE);
        mBinding.type.setVisibility(View.VISIBLE);
        mBinding.pager.setVisibility(View.VISIBLE);
        mBinding.swipeLayout.setEnabled(true);
        setBottomNavigationVisible(true);
        setHeaderPinned(false);
        setSearchHeaderExpanded(false);
        if (!TextUtils.isEmpty(mSuggestedKeyword)) mBinding.hot.setText(mSuggestedKeyword);
        loadHistory();
        checkRetry();
        checkEmptySource();
        if (mAdapter.getItemCount() > 0) setFabVisible(Math.max(0, mBinding.pager.getCurrentItem()));
    }

    private void cancelSearchEditing() {
        if (mBinding == null) return;
        mSearchEditing = false;
        hideSearchSuggestions();
        Util.hideKeyboard(mBinding.hot);
        mBinding.hot.clearFocus();
        mBinding.hot.setCursorVisible(false);
        if (mSearchResultsVisible) {
            HomeSearchFragment fragment = getHomeSearchFragment();
            if (mSearchViewReady && fragment != null) updateSearchActionIcon(fragment);
        } else {
            setBottomNavigationVisible(true);
            setSearchHeaderExpanded(false);
            if (!TextUtils.isEmpty(mSuggestedKeyword)) mBinding.hot.setText(mSuggestedKeyword);
        }
    }

    private void setSearchHeaderExpanded(boolean expanded) {
        if (mHeaderAnimationReady && mSearchHeaderExpanded != expanded) {
            TransitionManager.beginDelayedTransition(mBinding.headerBar, new AutoTransition().setDuration(180));
        }
        mSearchHeaderExpanded = expanded;
        int visibility = expanded ? View.GONE : View.VISIBLE;
        mBinding.logo.setVisibility(visibility);
        mBinding.keep.setVisibility(visibility);
        mBinding.history.setVisibility(visibility);
        mBinding.searchBack.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private void scheduleSearchSuggestions(String keyword) {
        if (mBinding == null) return;
        if (mSuggestRunnable != null) App.removeCallbacks(mSuggestRunnable);
        int generation = ++mSuggestionGeneration;
        if (TextUtils.isEmpty(keyword)) {
            mSuggestionAdapter.clear();
            mBinding.searchSuggestionPanel.setVisibility(View.GONE);
            return;
        }
        mSuggestRunnable = () -> requestSearchSuggestions(keyword, generation);
        App.post(mSuggestRunnable, 250);
    }

    private void requestSearchSuggestions(String keyword, int generation) {
        String url = "https://suggest.video.iqiyi.com/?if=mobile&key=" + Uri.encode(keyword);
        OkHttp.newCall(url).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<String> suggestions = Suggest.get(response.body().string());
                App.post(() -> showSearchSuggestions(keyword, generation, suggestions));
            }
        });
    }

    private void showSearchSuggestions(String keyword, int generation, List<String> suggestions) {
        if (mBinding == null || generation != mSuggestionGeneration) return;
        if (!mSearchEditing || !mBinding.hot.hasFocus()) return;
        if (!keyword.equals(mBinding.hot.getText().toString().trim())) return;
        mSuggestionAdapter.setItems(suggestions);
        mBinding.searchSuggestionPanel.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void hideSearchSuggestions() {
        mSuggestionGeneration++;
        if (mSuggestRunnable != null) App.removeCallbacks(mSuggestRunnable);
        if (mBinding == null || mSuggestionAdapter == null) return;
        mSuggestionAdapter.clear();
        mBinding.searchSuggestionPanel.setVisibility(View.GONE);
    }

    private void onSuggestionClick(String text) {
        if (mBinding == null) return;
        mBinding.hot.setText(text);
        mBinding.hot.setSelection(text.length());
        hideSearchSuggestions();
        submitHomeSearch();
    }

    private void setBottomNavigationVisible(boolean visible) {
        if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).setBottomNavigationVisible(visible);
    }

    private void setHeaderPinned(boolean pinned) {
        ViewGroup.LayoutParams layoutParams = mBinding.homeHeaderContent.getLayoutParams();
        if (!(layoutParams instanceof AppBarLayout.LayoutParams)) return;
        AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) layoutParams;
        params.setScrollFlags(pinned ? 0 : AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL);
        mBinding.homeHeaderContent.setLayoutParams(params);
    }

    @Nullable
    private HomeSearchFragment getHomeSearchFragment() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag("home_search");
        return fragment instanceof HomeSearchFragment ? (HomeSearchFragment) fragment : null;
    }

    public void onHomeSearchResultsReady() {
        if (mBinding == null || !mSearchResultsVisible) return;
        mSearchViewReady = true;
        HomeSearchFragment fragment = getHomeSearchFragment();
        if (fragment != null) updateSearchActionIcon(fragment);
    }

    private void updateSearchActionIcon(HomeSearchFragment fragment) {
        mBinding.search.setImageResource(fragment.isGrid() ? R.drawable.ic_action_list : R.drawable.ic_action_grid);
    }

    private void onHistory(View view) {
        HistoryActivity.start(getActivity());
    }

    private void onPullRefresh() {
        TypeFragment fragment = getFragmentOrNull();
        if (fragment != null) fragment.refreshContent();
        WebDAVSyncManager manager = WebDAVSyncManager.get();
        if (!manager.isConfigured()) {
            if (fragment == null) setRefreshing(false);
            return;
        }
        App.execute(() -> {
            WebDAVSyncManager.SyncResult result = manager.syncNow();
            App.post(() -> {
                if (mBinding == null) return;
                loadHistory();
                setRefreshing(false);
                Notify.show(result.message);
            });
        });
    }

    public boolean isRefreshing() {
        return mBinding != null && mBinding.swipeLayout.isRefreshing();
    }

    public void setRefreshing(boolean refreshing) {
        if (mBinding != null) mBinding.swipeLayout.setRefreshing(refreshing);
    }

    private void setupHistoryRecycler() {
        mBinding.historyRecycler.setLayoutManager(
            new androidx.recyclerview.widget.LinearLayoutManager(
                getContext(), 
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, 
                false
            )
        );
        mHistoryAdapter = new HistoryCardAdapter(item -> {
            VideoActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
        });
        mBinding.historyRecycler.setAdapter(mHistoryAdapter);
    }

    private void loadHistory() {
        if (mSearchResultsVisible) {
            mBinding.historySection.setVisibility(View.GONE);
            mBinding.historyRecycler.setVisibility(View.GONE);
            return;
        }
        // 检查是否显示历史记录
        if (!Setting.isHistoryVisible()) {
            mBinding.historySection.setVisibility(View.GONE);
            mBinding.historyRecycler.setVisibility(View.GONE);
            updateRefreshIndicatorOffset();
            return;
        }
        
        List<History> histories = History.get();
        boolean hasHistory = histories != null && !histories.isEmpty();
        mBinding.historySection.setVisibility(View.VISIBLE);
        mBinding.historyRecycler.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
        boolean firstItemChanged = mHistoryAdapter.setItems(histories);
        if (hasHistory) {
            if (firstItemChanged) {
                mBinding.historyRecycler.stopScroll();
                androidx.recyclerview.widget.RecyclerView.LayoutManager manager = mBinding.historyRecycler.getLayoutManager();
                if (manager instanceof androidx.recyclerview.widget.LinearLayoutManager) {
                    ((androidx.recyclerview.widget.LinearLayoutManager) manager).scrollToPositionWithOffset(0, 0);
                } else {
                    mBinding.historyRecycler.scrollToPosition(0);
                }
            }
        }
        updateRefreshIndicatorOffset();
    }

    private void updateRefreshIndicatorOffset() {
        if (mBinding == null) return;
        mBinding.swipeLayout.post(() -> {
            if (mBinding == null) return;
            View anchor = mBinding.historySection.getVisibility() == View.VISIBLE ? mBinding.historySection : mBinding.headerBar;
            int edge = anchor.getBottom();
            if (edge <= 0) return;
            mBinding.swipeLayout.setProgressViewOffset(false, Math.max(0, edge - ResUtil.dp2px(40)), edge + ResUtil.dp2px(24));
        });
    }

    private void showProgress() {
        mBinding.retry.setVisibility(View.GONE);
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void homeContent() {
        showProgress();
        setFabVisible(0);
        // 安全地隐藏空源提示
        try {
            if (mBinding != null && mBinding.emptySourceHint != null) {
                mBinding.emptySourceHint.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Logger.e("Error", e);
        }
        mAdapter.clear();
        mViewModel.homeContent();
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    public Result getResult() {
        return mResult == null ? new Result() : mResult;
    }

    private void setLogo() {
        Glide.with(App.get()).load(UrlUtil.convert(VodConfig.get().getConfig().getLogo())).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).error(R.drawable.ic_logo).listener(getListener()).into(mBinding.logo);
    }

    private RequestListener<Drawable> getListener() {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                mBinding.logo.getLayoutParams().width = ResUtil.dp2px(24);
                mBinding.logo.getLayoutParams().height = ResUtil.dp2px(24);
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                mBinding.logo.getLayoutParams().width = ResUtil.dp2px(36);
                mBinding.logo.getLayoutParams().height = ResUtil.dp2px(36);
                return false;
            }
        };
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case CONFIG:
                setLogo();
                break;
            case VIDEO:
            case SIZE:
                homeContent();
                break;
            case HISTORY:
                loadHistory();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStateEvent(StateEvent event) {
        switch (event.getType()) {
            case EMPTY:
                hideProgress();
                checkEmptySource(); // 添加检查是否显示空源提示
                break;
            case PROGRESS:
                showProgress();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        ReceiveDialog.create().event(event).show(this);
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        homeContent();
    }

    @Override
    public void onChanged() {
    }

    @Override
    public void onItemClick(int position, Class item) {
        mBinding.pager.setCurrentItem(position);
        mAdapter.setActivated(position);
    }

    @Override
    public void setFilter(String key, Value value) {
        getFragment().setFilter(key, value);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || requestCode != FileChooser.REQUEST_PICK_FILE) return;
        VideoActivity.file(getActivity(), FileChooser.getPathFromUri(getContext(), data.getData()));
    }

    @Override
    public boolean canBack() {
        if (mSearchResultsVisible) {
            hideSearchContent();
            return false;
        }
        if (mSearchEditing || mBinding.hot.hasFocus()) {
            cancelSearchEditing();
            return false;
        }
        if (mBinding.pager.getAdapter() == null) return true;
        if (mBinding.pager.getAdapter().getCount() == 0) return true;
        return getFragment().canBack();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mSearchResultsVisible) loadHistory();
    }

    @Override
    public void onDestroyView() {
        setBottomNavigationVisible(true);
        hideSearchSuggestions();
        super.onDestroyView();
        App.removeCallbacks(mRunnable);
        if (mSuggestRunnable != null) App.removeCallbacks(mSuggestRunnable);
        EventBus.getDefault().unregister(this);
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(position);
            return TypeFragment.newInstance(getSite().getKey(), type.getTypeId(), type.getStyle(), type.getExtend(true), "1".equals(type.getTypeFlag()));
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
