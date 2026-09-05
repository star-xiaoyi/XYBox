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
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Download;
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
import com.fongmi.android.tv.ui.activity.DownloadActivity;
import com.fongmi.android.tv.ui.activity.DownloadTaskActivity;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.KeepActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.DownloadCardAdapter;
import com.fongmi.android.tv.ui.adapter.HistoryCardAdapter;
import com.fongmi.android.tv.ui.adapter.SearchSuggestionAdapter;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.LastWatchToast;
import com.fongmi.android.tv.ui.dialog.LinkDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.custom.LiquidGlassNavigationView;
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
    private DownloadCardAdapter mDownloadAdapter;
    private boolean mDownloadTab;
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
    /** 当前是否允许显示悬浮按钮（空源时整体隐藏）。 */
    private boolean mFabEnabled;
    private int mContextAction = LiquidGlassNavigationView.ACTION_NONE;
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
        mBinding.swipeLayout.setProgressBackgroundColorSchemeColor(ResUtil.getThemeColor(getActivity(), com.google.android.material.R.attr.colorSurface));
        mBinding.swipeLayout.setColorSchemeColors(ResUtil.getThemeColor(getActivity(), com.google.android.material.R.attr.colorPrimary));
        initStartupState(); // 根据是否已有配置来设置初始状态
        setLogo();
        initHot();
        getHot();
        loadHistory();
        App.setAppLaunched();
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
        mBinding.history.setOnClickListener(view -> HistoryActivity.start(getActivity()));
        mBinding.historyMore.setOnClickListener(this::onHistory);
        mBinding.tabHistory.setOnClickListener(view -> setTab(false));
        mBinding.tabDownload.setOnClickListener(view -> setTab(true));
        mBinding.swipeLayout.setOnRefreshListener(this::onPullRefresh);
        mBinding.appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> mAppBarOffset = verticalOffset);
        // 顶栏高度不是一开始就有的：观看记录和分类行都是异步加载出来的，加载一次长高一次。
        // 居中的转圈和提示都以它为基准，所以它一变就立刻重算，否则圈会当众跳位置
        mBinding.appBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) applyCenterInset();
        });
        mBinding.swipeLayout.setOnChildScrollUpCallback((parent, child) -> {
            if (mAppBarOffset != 0) return true;
            TypeFragment fragment = getFragmentOrNull();
            return fragment != null && fragment.canScrollUp();
        });
        mBinding.filter.setOnLongClickListener(this::onLink);
        mBinding.filterPanel.setOnVisibilityChangedListener(this::onFilterPanelVisibilityChanged);
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
        if (!mSearchEditing && !mSearchResultsVisible) mBinding.hot.setHint(mSuggestedKeyword);
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
        checkRetry(result);
        checkEmptySource(); // 添加检查是否显示空源提示
        setRefreshing(false);
    }

    /**
     * 空源提示只在「用户压根没配过源」时出现。
     * 配过源但站点没加载出来（断网、源挂了）是另一回事，那种要给错误原因和重试，
     * 否则断个网就提示"还没有添加视频源"，会误导用户去重配。
     */
    private void checkEmptySource() {
        boolean isEmpty = !hasConfig();
        if (mBinding.emptySourceHint == null) return;
        mBinding.emptySourceHint.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (!isEmpty) return;
        centerInVisible(mBinding.emptySourceHint);
        mBinding.retryLayout.setVisibility(View.GONE);
        mBinding.emptySourceHint.setOnClickListener(this::onAddSource);
        if (mBinding.addSourceBtn != null) mBinding.addSourceBtn.setOnClickListener(this::onAddSource);
        hideFabButtons();
    }

    /** 是否存在保存过的配置地址。 */
    private boolean hasConfig() {
        try {
            Config config = VodConfig.get().getConfig();
            return config != null && config.getUrl() != null && !config.getUrl().isEmpty();
        } catch (Exception e) {
            return false;
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
        // 没有内容可展示时（没配源、或源没加载出来）不出悬浮按钮
        if (!hasConfig() || mAdapter.getItemCount() == 0) {
            hideFabButtons();
        } else {
            mFabEnabled = true;
            showActionFab(position);
        }
    }

    // 隐藏所有悬浮按钮的方法
    private void hideFabButtons() {
        mFabEnabled = false;
        setContextAction(LiquidGlassNavigationView.ACTION_NONE);
    }

    private void checkRetry() {
        checkRetry(null);
    }

    /**
     * 首页拉不到内容时不能只丢一个刷新图标，得说清楚是断网还是源挂了。
     * 完全没配过源的情况归空源提示管，这里不掺和。
     */
    private void checkRetry(Result result) {
        boolean show = mAdapter.getItemCount() == 0 && hasConfig();
        mBinding.retryLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        centerInVisible(mBinding.retryLayout);
        mBinding.retryText.setText(getRetryText(result));
    }

    /**
     * 把居中的提示挪回可视区中央。
     * <p>
     * 内容区挂的是 appbar 滚动行为，而顶栏整块可滚走，于是 Material 给了内容区整屏高度、
     * 再往下偏移一个顶栏——底边其实垂到了屏幕外。此时 layout_gravity="center" 居中的是那个
     * 垂出去的框，看起来就偏低了一个顶栏的高度。
     * <p>
     * 补一块等于顶栏高度的底部内边距，视图外框变高，居中后内容正好落回可视区中央。
     */
    /** 顶栏当前高度。分页里的转圈和空态要靠它把自己抬回可视区中央。 */
    public int getAppBarHeight() {
        return mBinding == null ? 0 : mBinding.appBar.getHeight();
    }

    /**
     * 把居中的转圈和提示挪回可视区中央——顶栏一测量出来就立刻应用，别等下次谁来调 showProgress。
     * <p>
     * 之前是在 showProgress 里现算的，而第一次调用发生在 initStartupState，那会儿顶栏还没测量、
     * 高度是 0，等于没挪；直到配置加载完再次 showProgress 才补上，圈就在半秒后当众跳了一下。
     * 用户看到的"两个重叠的圈"其实就是同一个圈跳了位置。
     * <p>
     * 两个圈的参照系不同：这个圈铺满整页（含顶栏那块），分页里的圈只铺顶栏以下，
     * 所以一个补顶部内边距、一个补底部，圆心才落在同一点。
     */
    private void applyCenterInset() {
        if (mBinding == null) return;
        int inset = mBinding.appBar.getHeight();
        View progress = mBinding.progress.getRoot();
        if (progress.getPaddingTop() != inset) progress.setPadding(0, inset, 0, 0);
        centerInVisible(mBinding.retryLayout);
        centerInVisible(mBinding.emptySourceHint);
    }

    private void centerInVisible(View view) {
        int bottom = mBinding.appBar.getHeight();
        if (bottom <= 0 || view.getPaddingBottom() == bottom) return;
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottom);
    }

    private String getRetryText(Result result) {
        if (result != null && result.hasMsg() && !TextUtils.isEmpty(result.getMsg())) return result.getMsg();
        return getString(isNetworkAvailable() ? R.string.error_source : R.string.error_network);
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager manager = (android.net.ConnectivityManager) App.get().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void onTop(View view) {
        getFragment().scrollToTop();
        // 回到顶部要连搜索框一起展开，否则只把列表拉到头、顶栏还是收起的
        mBinding.appBar.setExpanded(true, true);
        showActionFab();
    }

    /**
     * 内容区滚动回调。悬浮按钮挂在外层 CoordinatorLayout 上，收不到 ViewPager 里
     * RecyclerView 的嵌套滚动，所以由 TypeFragment 主动回传，这里手动切换按钮。
     */
    public void onContentScrolled(int dy, boolean canScrollUp) {
        if (mBinding == null || mSearchResultsVisible || !mFabEnabled) return;
        if (!canScrollUp) showActionFab();
        else if (dy > 0) showTopFab();
    }

    /** 显示「回到顶部」，把筛选/链接按钮收起。 */
    private void showTopFab() {
        if (mContextAction == LiquidGlassNavigationView.ACTION_TOP) return;
        setContextAction(LiquidGlassNavigationView.ACTION_TOP);
    }

    /** 回到列表顶部时恢复原来的筛选/链接按钮。 */
    private void showActionFab() {
        showActionFab(mBinding.pager.getCurrentItem());
    }

    private void showActionFab(int position) {
        if (!mFabEnabled) return;
        boolean hasFilter = position >= 0 && mAdapter.getItemCount() > position
                && !mAdapter.get(position).getFilters().isEmpty();
        setContextAction(hasFilter ? LiquidGlassNavigationView.ACTION_FILTER : LiquidGlassNavigationView.ACTION_LINK);
    }

    private void setContextAction(int action) {
        mContextAction = action;
        renderContextAction();
    }

    private void renderContextAction() {
        if (mBinding == null) return;
        HomeActivity activity = getActivity() instanceof HomeActivity ? (HomeActivity) getActivity() : null;
        boolean glass = activity != null && activity.isGlassNavigationEnabled();
        if (glass) {
            mBinding.top.setVisibility(View.GONE);
            mBinding.link.setVisibility(View.GONE);
            mBinding.filter.setVisibility(View.GONE);
            activity.setGlassAction(mContextAction, mContextAction != LiquidGlassNavigationView.ACTION_NONE);
            return;
        }
        if (activity != null) activity.setGlassAction(LiquidGlassNavigationView.ACTION_NONE, false);
        mBinding.top.setVisibility(mContextAction == LiquidGlassNavigationView.ACTION_TOP ? View.VISIBLE : View.GONE);
        mBinding.link.setVisibility(mContextAction == LiquidGlassNavigationView.ACTION_LINK ? View.VISIBLE : View.GONE);
        mBinding.filter.setVisibility(mContextAction == LiquidGlassNavigationView.ACTION_FILTER ? View.VISIBLE : View.GONE);
    }

    public void onNavigationModeChanged() {
        renderContextAction();
    }

    public void syncGlassAction() {
        HomeActivity activity = getActivity() instanceof HomeActivity ? (HomeActivity) getActivity() : null;
        if (activity != null) activity.setGlassAction(mContextAction, mContextAction != LiquidGlassNavigationView.ACTION_NONE);
    }

    public void performGlassAction() {
        if (mContextAction == LiquidGlassNavigationView.ACTION_FILTER) onFilter(mBinding.filter);
        else if (mContextAction == LiquidGlassNavigationView.ACTION_LINK) onLink(mBinding.link);
        else if (mContextAction == LiquidGlassNavigationView.ACTION_TOP) onTop(mBinding.top);
    }

    public void performGlassLongAction() {
        if (mContextAction == LiquidGlassNavigationView.ACTION_FILTER) onLink(mBinding.link);
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
        // 断网启动时配置压根没拉下来，站源列表是空的。这时候只重查内容，
        // 拿空站源再问一遍照样是"数据源没有返回内容"——用户只能杀后台重进，
        // 因为唯有重进才会重新加载配置。所以这里先把配置补回来。
        if (VodConfig.get().getSites().isEmpty()) reloadConfig();
        else homeContent();
    }

    /** 等价于重启 App 那一下：重新拉点播和直播配置，成功后再查内容。 */
    private void reloadConfig() {
        showProgress();
        mBinding.retryLayout.setVisibility(View.GONE);
        LiveConfig.get().init().load();
        VodConfig.get().init().load(new Callback() {
            @Override
            public void success() {
                RefreshEvent.config();
                RefreshEvent.video();
                homeContent();
            }

            @Override
            public void error(String msg) {
                hideProgress();
                checkRetry();
            }
        });
    }

    private void onFilter(View view) {
        if (mAdapter.getItemCount() > 0) {
            mBinding.filterPanel.show(
                    mAdapter.get(mBinding.pager.getCurrentItem()).getFilters(),
                    this,
                    mBinding.swipeLayout);
        }
    }

    private void onFilterPanelVisibilityChanged(boolean visible) {
        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) getActivity()).setFilterOverlayVisible(visible);
        }
    }

    private void enterSearchEditing() {
        if (mBinding == null) return;
        mSearchEditing = true;
        setSearchHeaderExpanded(true);
        setBottomNavigationVisible(false);
        hideFabButtons();
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
        if (TextUtils.isEmpty(keyword)) {
            keyword = mSuggestedKeyword == null ? "" : mSuggestedKeyword.trim();
            if (TextUtils.isEmpty(keyword)) return;
            mBinding.hot.setText(keyword);
            mBinding.hot.setSelection(keyword.length());
        }
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

    public void searchFromHome(String keyword) {
        if (mBinding == null || TextUtils.isEmpty(keyword)) return;
        mBinding.hot.setText(keyword);
        mBinding.hot.setSelection(keyword.length());
        submitHomeSearch();
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
        mBinding.retryLayout.setVisibility(View.GONE);
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
        restoreSuggestedHint();
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
            restoreSuggestedHint();
            if (mAdapter.getItemCount() > 0) setFabVisible(Math.max(0, mBinding.pager.getCurrentItem()));
        }
    }

    private void restoreSuggestedHint() {
        if (mBinding == null) return;
        mBinding.hot.setText("");
        mBinding.hot.setHint(TextUtils.isEmpty(mSuggestedKeyword) ? getString(R.string.search_keyword) : mSuggestedKeyword);
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
        if (mDownloadTab) DownloadActivity.start(getActivity());
        else HistoryActivity.start(getActivity());
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
                Notify.tip(result.message);
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
        mDownloadAdapter = new DownloadCardAdapter(item -> {
            // 只有还在下载、一集都没缓存好的，点进去也没得播，直接送去下载列表看进度
            if (item.getPlayable().isEmpty()) DownloadTaskActivity.start(getActivity());
            else VideoActivity.download(getActivity(), item);
        });
        mBinding.historyRecycler.setAdapter(mHistoryAdapter);
        setTab(false);
    }

    /**
     * 观看记录 / 离线缓存 二选一。文字固定跟随前景色（浅色黑、深色白），不跟主题色走，
     * 未选中的压低透明度、不加粗。取色必须用 Activity 上下文，
     * ResUtil 走的是 Application 资源，拿不到夜间模式会返回浅色值。
     */
    private void setTab(boolean download) {
        if (mBinding == null) return;
        mDownloadTab = download;
        int color = androidx.core.content.ContextCompat.getColor(mBinding.getRoot().getContext(), R.color.text_primary);
        mBinding.tabHistoryText.setTextColor(color);
        mBinding.tabDownloadText.setTextColor(color);
        mBinding.tabHistoryText.setAlpha(download ? 0.4f : 1f);
        mBinding.tabDownloadText.setAlpha(download ? 1f : 0.4f);
        mBinding.tabHistoryText.setTypeface(null, download ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        mBinding.tabDownloadText.setTypeface(null, download ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        mBinding.tabHistoryLine.setVisibility(download ? View.INVISIBLE : View.VISIBLE);
        mBinding.tabDownloadLine.setVisibility(download ? View.VISIBLE : View.INVISIBLE);
        mBinding.historyRecycler.setAdapter(download ? mDownloadAdapter : mHistoryAdapter);
        loadHistory();
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
        mBinding.historySection.setVisibility(View.VISIBLE);
        if (mDownloadTab) loadDownload();
        else loadWatchHistory();
        updateRefreshIndicatorOffset();
    }

    private void loadDownload() {
        List<Download.Group> groups = Download.group(Download.getAll());
        mBinding.historyRecycler.setVisibility(groups.isEmpty() ? View.GONE : View.VISIBLE);
        mDownloadAdapter.setItems(groups);
    }

    private void loadWatchHistory() {
        List<History> histories = History.get();
        boolean hasHistory = histories != null && !histories.isEmpty();
        mBinding.historyRecycler.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
        boolean firstItemChanged = mHistoryAdapter.setItems(histories);
        if (hasHistory && firstItemChanged) {
            mBinding.historyRecycler.stopScroll();
            androidx.recyclerview.widget.RecyclerView.LayoutManager manager = mBinding.historyRecycler.getLayoutManager();
            if (manager instanceof androidx.recyclerview.widget.LinearLayoutManager) {
                ((androidx.recyclerview.widget.LinearLayoutManager) manager).scrollToPositionWithOffset(0, 0);
            } else {
                mBinding.historyRecycler.scrollToPosition(0);
            }
        }
    }

    private void updateRefreshIndicatorOffset() {
        if (mBinding == null) return;
        mBinding.swipeLayout.post(() -> {
            if (mBinding == null) return;
            boolean historyVisible = mBinding.historySection.getVisibility() == View.VISIBLE;
            View anchor = historyVisible ? mBinding.historySection : mBinding.headerBar;
            int edge = anchor.getBottom();
            if (edge <= 0) return;
            if (historyVisible) {
                mBinding.swipeLayout.setProgressViewOffset(false, Math.max(0, edge - ResUtil.dp2px(40)), edge + ResUtil.dp2px(24));
            } else {
                // 首页历史关闭时锚点是搜索框所在的顶栏，本身高度很小，
                // 若仍减去指示器高度，指示器会落进搜索框内部；直接从搜索框下缘开始。
                mBinding.swipeLayout.setProgressViewOffset(false, edge, edge + ResUtil.dp2px(40));
            }
        });
    }

    private void showProgress() {
        mBinding.retryLayout.setVisibility(View.GONE);
        applyCenterInset();
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
        Config config = VodConfig.get().getConfig();
        String logo = config == null ? "" : config.getLogo();
        Glide.with(this).load(UrlUtil.convert(logo)).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).error(R.drawable.ic_logo).listener(getLogoListener()).into(mBinding.logo);
    }

    private RequestListener<Drawable> getLogoListener() {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                if (mBinding == null) return false;
                mBinding.logo.getLayoutParams().width = ResUtil.dp2px(24);
                mBinding.logo.getLayoutParams().height = ResUtil.dp2px(24);
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                if (mBinding == null) return false;
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
            case DOWNLOAD:
                if (mDownloadTab) loadHistory();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStateEvent(StateEvent event) {
        switch (event.getType()) {
            case EMPTY:
                hideProgress();
                checkEmptySource(); // 添加检查是否显示空源提示
                checkRetry(); // 配置加载失败时给出原因和重试，不然页面是一片空白
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
        if (mBinding.filterPanel.isPanelVisible()) {
            mBinding.filterPanel.dismiss();
            return false;
        }
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
        onFilterPanelVisibilityChanged(false);
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
