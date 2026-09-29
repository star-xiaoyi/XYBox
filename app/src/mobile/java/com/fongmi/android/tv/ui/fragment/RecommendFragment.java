package com.fongmi.android.tv.ui.fragment;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.Douban;
import com.fongmi.android.tv.bean.Hot;
import com.fongmi.android.tv.bean.Suggest;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.FragmentRecommendBinding;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.activity.RecommendFilterActivity;
import com.fongmi.android.tv.ui.activity.RecommendListActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.RecommendAdapter;
import com.fongmi.android.tv.ui.adapter.RecommendChannelAdapter;
import com.fongmi.android.tv.ui.adapter.RecommendHeroAdapter;
import com.fongmi.android.tv.ui.adapter.RecommendRankCardAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.adapter.RecommendBrowseAdapter;
import com.fongmi.android.tv.ui.adapter.SearchSuggestionAdapter;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Logger;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecommendFragment extends BaseFragment implements RecommendAdapter.OnClickListener,
        RecommendChannelAdapter.OnClickListener {

    private static final int WEEKLY = 0;
    private static final int MOVIE = 1;
    private static final int TV = 2;
    private static final int TOP = 3;
    private static final int ANIMATION = 4;
    private static final int VARIETY = 5;

    private static final String[] COLLECTIONS = {
            "movie_weekly_best", "movie_hot_gaia", "tv_hot", "movie_top250", "tv_animation", "tv_variety_show"
    };
    private static final int[] COUNTS = {10, 24, 24, 24, 24, 24};
    private static final int[] FULL_COUNTS = {10, 100, 100, 250, 60, 100};
    private static final int[] SECTION_TITLES = {
            R.string.recommend_weekly, R.string.recommend_movie_hot, R.string.recommend_tv_hot,
            R.string.recommend_top250, R.string.recommend_animation_hot, R.string.recommend_variety_hot
    };
    private static final int[] CHANNEL_SECTION = {-1, MOVIE, TV, ANIMATION, VARIETY};
    private static final String[] CHANNEL_TAG = {"", "电影", "电视剧", "动画", "综艺"};

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final PagerSnapHelper mHeroSnap = new PagerSnapHelper();
    private final Runnable mAutoAdvance = this::advanceHero;
    private final List<Douban.Item> mCategoryItems = new ArrayList<>();
    private RecommendBrowseAdapter mCategoryAdapter;
    private SearchSuggestionAdapter mSuggestionAdapter;
    private Runnable mSuggestRunnable;
    private int mSuggestionGeneration;
    private String mSuggestedKeyword = "";
    private final List<String> mHotKeywords = new ArrayList<>();
    private final Runnable mRotateHint = this::rotateHint;
    private int mCategoryGeneration;
    private int mCategoryOffset;
    private boolean mCategoryLoading;
    private boolean mCategoryHasMore;
    private boolean mCategoryFailed;

    private FragmentRecommendBinding mBinding;
    private RecommendHeroAdapter mHeroAdapter;
    private RecommendRankCardAdapter mRankCardAdapter;
    private RecommendAdapter[] mAdapters;
    private RecommendChannelAdapter mChannelAdapter;
    private List<Douban.Item>[] mData;
    private View[] mSections;
    private int[] mDataVersion;
    private ExecutorService mExecutor;
    private int mGeneration;
    private int mPending;
    private int mSelectedChannel;
    private boolean mHeroRendered;
    private boolean mRanksDirty = true;
    private boolean mSearchVisible;
    private int mStatusBarInset;

    public static RecommendFragment newInstance() {
        return new RecommendFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentRecommendBinding.inflate(inflater, container, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void initView() {
        mData = (List<Douban.Item>[]) new List[COLLECTIONS.length];
        mAdapters = new RecommendAdapter[COLLECTIONS.length];
        mDataVersion = new int[COLLECTIONS.length];
        mSections = new View[]{mBinding.weeklySection, mBinding.movieSection, mBinding.tvSection,
                mBinding.topSection, mBinding.animationSection, mBinding.varietySection};
        setupHeader();
        setupHero();
        setupChannels();
        setupCategoryGrid();
        setupSuggestions();
        setupRankCards();
        setupSection(WEEKLY, mBinding.weeklyList);
        setupSection(MOVIE, mBinding.movieList);
        setupSection(TV, mBinding.tvList);
        setupSection(TOP, mBinding.topList);
        setupSection(ANIMATION, mBinding.animationList);
        setupSection(VARIETY, mBinding.varietyList);
        setupMoreActions();
        load();
    }

    private void setupHeader() {
        mBinding.headerBar.setBrandMode(true, "XY影视");
        mBinding.headerBar.setBackdropView(mBinding.homeBody);
        mBinding.headerBar.setHint(getString(R.string.search_keyword));
        mBinding.headerBar.setExpanded(false);
        mBinding.headerBar.clearSearchFocus();
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.contentContainer, (view, insets) -> {
            mStatusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            mBinding.headerBar.setStatusBarInset(mStatusBarInset);
            applyContentInsets();
            return insets;
        });
        ViewCompat.requestApplyInsets(mBinding.contentContainer);
    }

    private int contentTop() {
        // 推荐保留海报净空，其他分类贴近按钮行，不继承轮播的顶部间距。
        return mStatusBarInset + dp(mSelectedChannel == 0 ? 84 : 64);
    }

    private void applyContentInsets() {
        if (mBinding == null) return;
        mBinding.headerBar.setCompact(mSelectedChannel != 0 || mSearchVisible);
        positionCategoryHeader();
        mBinding.headerBar.setCategoryMode(mSelectedChannel != 0 && !mSearchVisible);
        mBinding.headerBar.setBackdropView(mSelectedChannel == 0 ? mBinding.homeBody : mBinding.categoryGrid);
        mBinding.homeBody.setPadding(0, contentTop(), 0, mSelectedChannel == 0 ? dp(24) : 0);
        mBinding.searchContent.setPadding(0, mStatusBarInset + dp(64), 0, 0);
        resizeCategoryGrid();
    }

    private void positionCategoryHeader() {
        boolean floating = mSelectedChannel != 0;
        ViewGroup target = floating ? mBinding.contentContainer : mBinding.homeBody;
        if (mBinding.categoryHeader.getParent() != target) {
            ((ViewGroup) mBinding.categoryHeader.getParent()).removeView(mBinding.categoryHeader);
            if (floating) target.addView(mBinding.categoryHeader, new android.widget.FrameLayout.LayoutParams(-1, dp(48)));
            else mBinding.homeBody.addView(mBinding.categoryHeader, 1, new android.widget.LinearLayout.LayoutParams(-1, dp(48)));
        }
        if (floating) {
            android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) mBinding.categoryHeader.getLayoutParams();
            params.topMargin = contentTop();
            mBinding.categoryHeader.setLayoutParams(params);
        }
        mBinding.categoryHeader.setVisibility(mSearchVisible ? View.GONE : View.VISIBLE);
    }

    private void setupHero() {
        mBinding.heroList.setItemAnimator(null);
        mBinding.heroList.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        mBinding.heroList.setAdapter(mHeroAdapter = new RecommendHeroAdapter(this));
        mHeroSnap.attachToRecyclerView(mBinding.heroList);
        mBinding.heroList.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override public boolean onInterceptTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) mHandler.removeCallbacks(mAutoAdvance);
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) scheduleAutoAdvance();
                return false;
            }
        });
        mBinding.heroList.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            updateHeroSizing(r - l);
            applyHeroMotion();
        });
        mBinding.heroList.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View view) { view.post(() -> applyHeroMotion()); }
            @Override public void onChildViewDetachedFromWindow(@NonNull View view) { }
        });
        mBinding.heroList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) mHandler.removeCallbacks(mAutoAdvance);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    applyHeroMotion();
                    scheduleAutoAdvance();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                applyHeroMotion();
            }
        });
    }

    private void updateHeroSizing(int width) {
        if (mBinding == null || width <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        float widthDp = width / density;
        int cardDp = Math.round(Math.max(142f, Math.min(190f, widthDp / 4.4f)));
        int card = dp(cardDp);
        mHeroAdapter.setCardWidth(card);
        mBinding.heroList.post(() -> {
            if (mBinding != null && mBinding.heroList.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                View snap = mHeroSnap.findSnapView(mBinding.heroList.getLayoutManager());
                if (snap != null) {
                    int[] distance = mHeroSnap.calculateDistanceToFinalSnap(mBinding.heroList.getLayoutManager(), snap);
                    if (distance != null && distance[0] != 0) mBinding.heroList.scrollBy(distance[0], 0);
                }
                applyHeroMotion();
            }
        });
        int side = Math.max(0, (width - card) / 2 - dp(7));
        mBinding.heroList.setPadding(side, 0, side, 0);
    }

    private void setupChannels() {
        mBinding.channel.setHasFixedSize(true);
        mBinding.channel.setItemAnimator(null);
        mBinding.channel.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        int[] names = {R.string.recommend_channel_featured, R.string.recommend_channel_movie,
                R.string.recommend_channel_tv, R.string.recommend_channel_animation, R.string.recommend_channel_variety};
        String[] channels = new String[names.length];
        for (int i = 0; i < names.length; i++) channels[i] = getString(names[i]);
        mBinding.channel.setAdapter(mChannelAdapter = new RecommendChannelAdapter(channels, this));
    }

    private void setupRankCards() {
        mBinding.rankCards.setItemAnimator(null);
        mBinding.rankCards.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        mBinding.rankCards.setAdapter(mRankCardAdapter = new RecommendRankCardAdapter(this, this::openList));
        mBinding.rankCards.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateRankCardSizing(r - l));
    }

    private void updateRankCardSizing(int width) {
        if (mBinding == null || width <= 0) return;
        int available = Math.max(1, width - dp(24));
        int visible = Math.max(2, available / dp(174));
        int card = Math.max(dp(148), (available - dp(10) * (visible - 1)) / visible);
        mRankCardAdapter.setCardWidth(card);
    }

    private void setupSection(int index, RecyclerView view) {
        RecommendAdapter adapter = new RecommendAdapter(this, index == WEEKLY || index == TOP);
        mAdapters[index] = adapter;
        view.setHasFixedSize(true);
        view.setItemAnimator(null);
        view.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        view.setAdapter(adapter);
    }

    private void setupMoreActions() {
        mBinding.weeklyMore.setOnClickListener(view -> openList(WEEKLY));
        mBinding.movieMore.setOnClickListener(view -> openList(MOVIE));
        mBinding.tvMore.setOnClickListener(view -> openList(TV));
        mBinding.topMore.setOnClickListener(view -> openList(TOP));
        mBinding.animationMore.setOnClickListener(view -> openList(ANIMATION));
        mBinding.varietyMore.setOnClickListener(view -> openList(VARIETY));
        mBinding.filterButton.setOnClickListener(view ->
                RecommendFilterActivity.start(requireActivity(), Math.max(0, mSelectedChannel - 1)));
    }

    @Override
    protected void initEvent() {
        mBinding.headerBar.setOnSearchFocusChangedListener(focused -> {
            if (!focused) { hideSuggestions(); return; }
            mHandler.removeCallbacks(mAutoAdvance);
            mBinding.headerBar.setExpanded(true);
            setBottomNavigationVisible(false);
            scheduleSuggestions(mBinding.headerBar.getQuery().trim());
        });
        mBinding.headerBar.setOnSearchSubmittedListener(this::submitSearch);
        mBinding.headerBar.setOnQueryChangedListener(query -> {
            if (mBinding.headerBar.hasSearchFocus()) scheduleSuggestions(query.trim());
        });
        mBinding.headerBar.setOnSearchBoundsChangedListener(this::alignSuggestions);
        mBinding.headerBar.setSearchClickListener(view -> {
            submitSearch();
        });
        mBinding.headerBar.setSearchBackClickListener(view -> closeSearch());
        mBinding.headerBar.setHistoryClickListener(view -> HistoryActivity.start(requireActivity()));
    }

    private void load() {
        if (mBinding == null) return;
        mGeneration++;
        if (mExecutor != null) mExecutor.shutdownNow();
        mExecutor = Executors.newFixedThreadPool(3);
        mPending = COLLECTIONS.length;
        mHeroRendered = false;
        for (int i = 0; i < mData.length; i++) {
            mData[i] = null;
            mAdapters[i].setItems(Collections.emptyList());
            mSections[i].setVisibility(View.GONE);
        }
        mHeroAdapter.setItems(Collections.emptyList());
        mRankCardAdapter.setItems(Collections.emptyList());
        mBinding.progressLayout.setEmpty(getString(R.string.recommend_error), view -> load());
        mBinding.progressLayout.showProgress();
        int generation = mGeneration;
        for (int i = 0; i < COLLECTIONS.length; i++) load(i, generation);
    }

    private void load(int index, int generation) {
        int version = ++mDataVersion[index];
        mExecutor.execute(() -> {
            List<Douban.Item> items = Collections.emptyList();
            try {
                items = Douban.fetch(COLLECTIONS[index], 0, COUNTS[index]).getItems();
            } catch (Exception ignored) {
            }
            List<Douban.Item> result = items;
            App.post(() -> show(index, result, generation, version));
        });
    }

    private void show(int index, List<Douban.Item> items, int generation, int version) {
        if (mBinding == null || generation != mGeneration || version != mDataVersion[index]) return;
        mPending--;
        setData(index, items);
        renderChannel();
    }

    private void setData(int index, List<Douban.Item> items) {
        mRanksDirty = true;
        mData[index] = items;
        mAdapters[index].setItems(items);
    }

    private void renderChannel() {
        if (mBinding == null) return;
        boolean anyVisible = false;
        for (int i = 0; i < mSections.length; i++) {
            boolean show = isSectionVisible(i) && !items(i).isEmpty();
            boolean wasVisible = mSections[i].getVisibility() == View.VISIBLE;
            mSections[i].setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                anyVisible = true;
                if (!wasVisible && mPending > 0) animateIn(mSections[i]);
            }
        }

        boolean featured = mSelectedChannel == 0;
        mBinding.filterButton.setVisibility(featured ? View.GONE : View.VISIBLE);
        mBinding.channelRow.setPadding(0, 0, featured ? 0 : dp(8), 0);
        mBinding.categoryBlur.setVisibility(View.GONE);
        mBinding.categoryBlur.setBackdropView(featured ? null : mBinding.categoryGrid);
        mBinding.categoryHeader.setElevation(featured ? 0 : dp(9));
        mBinding.heroContainer.setVisibility(featured ? View.VISIBLE : View.GONE);
        mBinding.categoryResults.setVisibility(featured ? View.GONE : View.VISIBLE);
        mBinding.scroll.setPadding(0, 0, 0, featured ? dp(92) : 0);
        applyContentInsets();
        if (featured && mPending == 0 && !mHeroRendered) renderFeaturedHero();
        renderRankCards();

        if (!featured || anyVisible || !rankGroups().isEmpty()) mBinding.progressLayout.showContent();
        else if (mPending == 0) mBinding.progressLayout.showEmpty();
        else mBinding.progressLayout.showProgress();
    }

    private void renderFeaturedHero() {
        List<Douban.Item> mixed = new ArrayList<>();
        int[] order = {MOVIE, TV, ANIMATION, VARIETY, TOP, WEEKLY};
        for (int round = 0; round < 2; round++) {
            for (int index : order) {
                List<Douban.Item> source = items(index);
                if (round < source.size()) addUnique(mixed, source.get(round));
                if (mixed.size() >= 10) break;
            }
        }
        mHeroRendered = true;
        if (mHotKeywords.isEmpty()) for (Douban.Item item : mixed) mHotKeywords.add(item.getTitle());
        if (mSuggestedKeyword.isEmpty() && !mixed.isEmpty()) {
            mSuggestedKeyword = mixed.get(0).getTitle();
            mBinding.headerBar.setHint(mSuggestedKeyword);
        }
        mHeroAdapter.setItems(mixed);
        mBinding.heroContainer.setVisibility(mixed.isEmpty() ? View.GONE : View.VISIBLE);
        if (!mixed.isEmpty()) {
            mBinding.heroList.scrollToPosition(mHeroAdapter.getStartPosition());
            mBinding.heroContainer.setAlpha(0f);
            mBinding.heroContainer.animate().alpha(1f).setDuration(360).start();
            mBinding.heroList.post(this::applyHeroMotion);
            scheduleAutoAdvance();
        }
    }

    private void addUnique(List<Douban.Item> target, Douban.Item item) {
        for (Douban.Item value : target) if (value.getId().equals(item.getId())) return;
        target.add(item);
    }

    private void renderRankCards() {
        if (mSelectedChannel == 0 && mRanksDirty) {
            mRankCardAdapter.setItems(rankGroups());
            mRanksDirty = false;
        }
        mBinding.rankCards.setVisibility(mSelectedChannel == 0 && mRankCardAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private List<RecommendRankCardAdapter.Group> rankGroups() {
        List<RecommendRankCardAdapter.Group> groups = new ArrayList<>();
        if (mData == null || mSelectedChannel != 0) return groups;
        int[] indexes = new int[]{WEEKLY, MOVIE, TV, ANIMATION, VARIETY, TOP};
        for (int index : indexes) {
            if (items(index).isEmpty()) continue;
            groups.add(new RecommendRankCardAdapter.Group(index, getString(SECTION_TITLES[index]), items(index)));
        }
        return groups;
    }

    private boolean isSectionVisible(int index) {
        return mSelectedChannel == 0;
    }

    private List<Douban.Item> items(int index) {
        return mData[index] == null ? Collections.emptyList() : mData[index];
    }

    private void animateIn(View view) {
        view.setAlpha(0f);
        view.setTranslationY(24f);
        view.animate().alpha(1f).translationY(0f).setDuration(320).start();
    }

    private void applyHeroMotion() {
        if (mBinding == null || mBinding.heroList.getWidth() == 0) return;
        float center = mBinding.heroList.getWidth() / 2f;
        for (int i = 0; i < mBinding.heroList.getChildCount(); i++) {
            View child = mBinding.heroList.getChildAt(i);
            float childCenter = (child.getLeft() + child.getRight()) / 2f;
            float fraction = Math.min(1f, Math.abs(center - childCenter) / Math.max(1f, child.getWidth() * 1.15f));
            float scale = 1f - 0.18f * fraction;
            View poster = child.findViewById(R.id.poster_frame);
            View title = child.findViewById(R.id.title);
            View brief = child.findViewById(R.id.brief);
            if (poster != null) {
                // 只缩放海报，缩放轴固定在海报中心，左右卡片便与中间卡片的水平中线对齐。
                poster.setScaleX(scale);
                poster.setScaleY(scale);
                poster.setAlpha(1f);
                poster.setElevation(0);
                poster.setCameraDistance(dp(1200));
                poster.setRotationY(0);
            }
            float infoAlpha = Math.max(0f, 1f - fraction * 5f);
            if (title != null) title.setAlpha(infoAlpha);
            if (brief != null) brief.setAlpha(infoAlpha);
            child.setScaleX(1f);
            child.setScaleY(1f);
            child.setTranslationY(0f);
            child.setAlpha(1f);
        }
    }

    private void scheduleAutoAdvance() {
        mHandler.removeCallbacks(mAutoAdvance);
        if (mBinding != null && isResumed() && !mSearchVisible && !mBinding.headerBar.hasSearchFocus() && mSelectedChannel == 0 && mHeroAdapter.getLogicalCount() > 1) mHandler.postDelayed(mAutoAdvance, 5200);
    }

    private void advanceHero() {
        if (mBinding == null || mSelectedChannel != 0 || mHeroAdapter.getLogicalCount() < 2) return;
        View snap = mHeroSnap.findSnapView(mBinding.heroList.getLayoutManager());
        int position = snap == null ? 0 : mBinding.heroList.getChildAdapterPosition(snap);
        mBinding.heroList.smoothScrollToPosition(Math.max(position, 0) + 1);
        scheduleAutoAdvance();
    }

    private void setupCategoryGrid() {
        mBinding.categoryGrid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        mBinding.categoryGrid.setItemAnimator(null);
        mBinding.categoryGrid.setAdapter(mCategoryAdapter = new RecommendBrowseAdapter(this));
        mBinding.contentContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> resizeCategoryGrid());
        mBinding.categoryGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                mBinding.categoryBlur.setScrollOffset(view.computeVerticalScrollOffset());
                GridLayoutManager layout = (GridLayoutManager) view.getLayoutManager();
                if (dy > 0 && layout != null && layout.findLastVisibleItemPosition() >= mCategoryAdapter.getItemCount() - 9)
                    loadCategory(false);
            }
        });
        mBinding.categoryStatus.setOnClickListener(v -> loadCategory(true));
    }

    private void resizeCategoryGrid() {
        if (mBinding == null || mSelectedChannel == 0 || mBinding.contentContainer.getHeight() == 0) return;
        int height = Math.max(dp(100), mBinding.contentContainer.getHeight());
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mBinding.categoryResults.getLayoutParams();
        params.topMargin = -contentTop();
        if (params.height != height) { params.height = height; mBinding.categoryResults.setLayoutParams(params); }
        // 底栏避让属于列表可滚动的末尾，不能在视口外留下固定空白。
        mBinding.categoryGrid.setPadding(dp(10), contentTop() + dp(58), dp(10), dp(88));
        int widthDp = Math.round(mBinding.contentContainer.getWidth() / getResources().getDisplayMetrics().density);
        GridLayoutManager layout = (GridLayoutManager) mBinding.categoryGrid.getLayoutManager();
        if (layout != null) layout.setSpanCount(Math.max(3, Math.min(8, widthDp / 120)));
    }

    private void resetCategory() {
        mBinding.categoryGrid.scrollToPosition(0);
        mBinding.categoryBlur.setScrollOffset(0);
        ++mCategoryGeneration;
        mCategoryLoading = false;
        mCategoryHasMore = true;
        mCategoryFailed = false;
        mCategoryOffset = 0;
        mCategoryItems.clear();
        mCategoryAdapter.setItems(Collections.emptyList());
        mBinding.categoryResults.showProgress();
        loadCategory(true);
    }

    private void loadCategory(boolean retry) {
        if (mBinding == null || mSelectedChannel == 0 || mCategoryLoading || !mCategoryHasMore || (mCategoryFailed && !retry)) return;
        mCategoryLoading = true;
        mCategoryFailed = false;
        if (mCategoryItems.isEmpty()) mBinding.categoryResults.showProgress();
        mBinding.categoryStatus.setVisibility(View.GONE);
        int generation = mCategoryGeneration, channel = mSelectedChannel, offset = mCategoryOffset;
        mExecutor.execute(() -> {
            List<Douban.Item> items = Collections.emptyList();
            boolean failed = false;
            try {
                items = Douban.search(CHANNEL_TAG[channel], "", "", "", "U", 0, offset, 24);
                Logger.d("HomeCategory: channel=" + channel + " start=" + offset + " count=" + items.size());
            } catch (Exception error) {
                failed = true;
                Logger.e("HomeCategory: channel=" + channel + " start=" + offset, error);
            }
            List<Douban.Item> result = items;
            boolean error = failed;
            App.post(() -> {
                if (mBinding == null || generation != mCategoryGeneration || channel != mSelectedChannel) return;
                mCategoryLoading = false;
                mCategoryFailed = error;
                if (!error) {
                    int before = mCategoryItems.size();
                    for (Douban.Item item : result) addUnique(mCategoryItems, item);
                    mCategoryOffset += result.size();
                    mCategoryHasMore = result.size() >= 24 && mCategoryItems.size() > before;
                    mCategoryAdapter.addItems(mCategoryItems.subList(before, mCategoryItems.size()));
                }
                if (mCategoryItems.isEmpty()) {
                    mBinding.categoryResults.setEmpty(error ? "加载失败，请重试" : "暂无影片", error ? v -> loadCategory(true) : null);
                    mBinding.categoryResults.showEmpty();
                } else {
                    mBinding.categoryResults.showContent();
                    mBinding.categoryStatus.setText("加载失败，点击重试");
                    mBinding.categoryStatus.setVisibility(error ? View.VISIBLE : View.GONE);
                    mBinding.categoryStatus.setClickable(error);
                    mBinding.categoryGrid.post(() -> {
                        if (mBinding != null && generation == mCategoryGeneration && !error && !mBinding.categoryGrid.canScrollVertically(1)) loadCategory(false);
                    });
                }
            });
        });
    }

    private void setupSuggestions() {
        mBinding.searchSuggestions.setItemAnimator(null);
        mBinding.searchSuggestions.setAdapter(mSuggestionAdapter = new SearchSuggestionAdapter(value -> {
            mBinding.headerBar.setQuery(value);
            submitSearch();
        }));
        mBinding.searchSuggestionPanel.setBackdropView(mBinding.homeBody);
        mHotKeywords.clear();
        mHotKeywords.addAll(Hot.get(Setting.getHot()));
        rotateHint();
        App.execute(() -> {
            try (Response response = OkHttp.newCall("https://api.web.360kan.com/v1/rank?cat=1",
                    okhttp3.Headers.of("Referer", "https://www.360kan.com/rank/general")).execute()) {
                if (!response.isSuccessful() || response.body() == null) return;
                List<String> fresh = Hot.get(response.body().string());
                App.post(() -> {
                    if (mBinding == null || fresh.isEmpty()) return;
                    mHotKeywords.clear(); mHotKeywords.addAll(fresh); rotateHint();
                });
            } catch (Exception ignored) { }
        });
    }

    private void rotateHint() {
        mHandler.removeCallbacks(mRotateHint);
        if (mBinding == null) return;
        if (isResumed() && !isHidden() && !mSearchVisible && !mBinding.headerBar.hasSearchFocus()) {
            List<String> choices = new ArrayList<>(mHotKeywords);
            choices.removeAll(Collections.singleton(mSuggestedKeyword));
            if (!choices.isEmpty()) {
                mSuggestedKeyword = choices.get(new java.util.Random().nextInt(choices.size()));
                mBinding.headerBar.setHint(mSuggestedKeyword);
            }
        }
        if (isResumed() && !isHidden()) mHandler.postDelayed(mRotateHint, 10000);
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) mHandler.removeCallbacks(mRotateHint); else rotateHint();
    }

    private void scheduleSuggestions(String keyword) {
        hideSuggestions();
        if (keyword.isEmpty()) return;
        int generation = mSuggestionGeneration;
        mSuggestRunnable = () -> mExecutor.execute(() -> {
            List<String> values = Collections.emptyList();
            try (Response response = OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + Uri.encode(keyword)).execute()) {
                if (response.isSuccessful() && response.body() != null) values = Suggest.get(response.body().string());
            } catch (Exception ignored) { }
            List<String> result = values;
            App.post(() -> {
                if (mBinding == null || generation != mSuggestionGeneration || !mBinding.headerBar.hasSearchFocus()
                        || !keyword.equals(mBinding.headerBar.getQuery().trim())) return;
                mSuggestionAdapter.setItems(result);
                mBinding.searchSuggestionPanel.setRenderingEnabled(!result.isEmpty());
                mBinding.searchSuggestionPanel.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
        mHandler.postDelayed(mSuggestRunnable, 250);
    }

    private void hideSuggestions() {
        ++mSuggestionGeneration;
        if (mSuggestRunnable != null) mHandler.removeCallbacks(mSuggestRunnable);
        if (mBinding == null || mSuggestionAdapter == null) return;
        mSuggestionAdapter.clear();
        mBinding.searchSuggestionPanel.setRenderingEnabled(false);
        mBinding.searchSuggestionPanel.setVisibility(View.GONE);
    }

    private void alignSuggestions(int left, int top, int right, int bottom) {
        if (mBinding == null) return;
        int[] location = new int[2];
        mBinding.contentContainer.getLocationInWindow(location);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mBinding.searchSuggestionPanel.getLayoutParams();
        params.width = Math.max(1, right - left);
        params.leftMargin = left - location[0];
        params.topMargin = bottom - location[1] + dp(4);
        mBinding.searchSuggestionPanel.setLayoutParams(params);
    }

    private void submitSearch() {
        if (mBinding == null) return;
        String keyword = mBinding.headerBar.getQuery().trim();
        if (keyword.isEmpty()) keyword = mSuggestedKeyword;
        if (keyword.isEmpty()) {
            mBinding.headerBar.requestSearchFocus();
            return;
        }
        hideSuggestions();
        mHandler.removeCallbacks(mAutoAdvance);
        mSearchVisible = true;
        applyContentInsets();
        mBinding.progressLayout.showContent();
        mBinding.headerBar.clearSearchFocus();
        mBinding.headerBar.setExpanded(true);
        mBinding.scroll.setVisibility(View.GONE);
        mBinding.searchContent.setVisibility(View.VISIBLE);
        setBottomNavigationVisible(false);
        HomeSearchFragment fragment = getSearchFragment();
        if (fragment == null) {
            fragment = HomeSearchFragment.newInstance(keyword);
            getChildFragmentManager().beginTransaction().replace(mBinding.searchContent.getId(), fragment, "recommend_search").commitNowAllowingStateLoss();
        } else fragment.search(keyword);
    }

    private void closeSearch() {
        if (mBinding == null) return;
        hideSuggestions();
        mBinding.headerBar.clearSearchFocus();
        if (mSearchVisible) {
            mSearchVisible = false;
            mBinding.searchContent.setVisibility(View.GONE);
            mBinding.scroll.setVisibility(View.VISIBLE);
        }
        mBinding.headerBar.setExpanded(false);
        applyContentInsets();
        mBinding.headerBar.setQuery("");
        setBottomNavigationVisible(true);
        scheduleAutoAdvance();
    }

    @Nullable
    private HomeSearchFragment getSearchFragment() {
        androidx.fragment.app.Fragment fragment = getChildFragmentManager().findFragmentByTag("recommend_search");
        return fragment instanceof HomeSearchFragment ? (HomeSearchFragment) fragment : null;
    }

    private void setBottomNavigationVisible(boolean visible) {
        if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).setBottomNavigationVisible(visible);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void openList(int index) {
        RecommendListActivity.start(requireActivity(), COLLECTIONS[index], getString(SECTION_TITLES[index]), FULL_COUNTS[index]);
    }

    @Override
    public void onChannelClick(int position) {
        if (position < 0 || position >= CHANNEL_SECTION.length || position == mSelectedChannel) return;
        mBinding.homeBody.animate().cancel();
        mBinding.homeBody.setAlpha(0.72f);
        mSelectedChannel = position;
        mChannelAdapter.setSelected(position);
        mBinding.channel.smoothScrollToPosition(position);
        mBinding.scroll.scrollTo(0, 0);
        mHandler.removeCallbacks(mAutoAdvance);
        renderChannel();
        mBinding.homeBody.animate().alpha(1f).setDuration(180).start();
        if (position != 0) resetCategory();
        else { ++mCategoryGeneration; mBinding.heroList.post(this::applyHeroMotion); scheduleAutoAdvance(); }
    }

    @Override
    public void onItemClick(Douban.Item item) {
        VideoActivity.find(requireActivity(), item.getTitle(), item.getPic(), item.getYear(), item.getRating());
    }

    @Override
    public void onResume() {
        super.onResume();
        rotateHint();
        if (mBinding != null) applyContentInsets();
        scheduleAutoAdvance();
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacks(mAutoAdvance);
        hideSuggestions();
        if (mBinding != null) mBinding.headerBar.setBackdropView(null);
        mHandler.removeCallbacks(mRotateHint);
        super.onPause();
    }

    public boolean hasBackState() {
        return mBinding != null && (mSearchVisible || mBinding.headerBar.hasSearchFocus());
    }

    public boolean isEditingSearch() {
        return mBinding != null && !mSearchVisible && mBinding.headerBar.hasSearchFocus();
    }

    @Override
    public boolean canBack() {
        if (!hasBackState()) return true;
        closeSearch();
        return false;
    }

    @Override
    public void onDestroyView() {
        mGeneration++;
        ++mCategoryGeneration;
        hideSuggestions();
        mHandler.removeCallbacks(mRotateHint);
        mBinding.searchSuggestionPanel.setBackdropView(null);
        mHandler.removeCallbacks(mAutoAdvance);
        setBottomNavigationVisible(true);
        mBinding.headerBar.setBackdropView(null);
        mBinding.categoryBlur.setBackdropView(null);
        if (mExecutor != null) mExecutor.shutdownNow();
        mExecutor = null;
        mHeroSnap.attachToRecyclerView(null);
        mHeroAdapter = null;
        mRankCardAdapter = null;
        mAdapters = null;
        mSections = null;
        mData = null;
        mBinding = null;
        super.onDestroyView();
    }
}
