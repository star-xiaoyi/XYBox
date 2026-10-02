package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.download.DownloadManager;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.receiver.ShortcutReceiver;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.FragmentStateManager;
import com.fongmi.android.tv.ui.custom.LiquidGlassNavigationView;
import com.fongmi.android.tv.ui.fragment.RecommendFragment;
import com.fongmi.android.tv.ui.fragment.SettingFragment;
import com.fongmi.android.tv.ui.fragment.VodFragment;
import com.fongmi.android.tv.utils.CastManager;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.google.android.material.navigation.NavigationBarView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HomeActivity extends BaseActivity implements NavigationBarView.OnItemSelectedListener, LiquidGlassNavigationView.Listener {

    private static final String STATE_POSITION = "home_position";
    private static final int POSITION_RECOMMEND = 0;
    private static final int POSITION_VOD = 1;
    private static final int POSITION_SETTING = 2;
    private FragmentStateManager mManager;
    private ActivityHomeBinding mBinding;
    private int mTopInset;
    private int mBottomInset;
    private int currentPosition;
    private int orientation;
    private int windowWidthDp;
    private boolean bottomNavigationVisible = true;
    private boolean glassNavigationEnabled;
    private com.fongmi.android.tv.ai.VoiceHoldController aiVoice;
    private final com.fongmi.android.tv.ai.AiOrbDrawable aiOrb = new com.fongmi.android.tv.ai.AiOrbDrawable();
    private boolean aiRecording;
    private boolean aiThinking;
    private com.fongmi.android.tv.ai.VoiceEntryController voiceEntry;
    private final Updater automaticUpdater = Updater.create().release().auto();
    private boolean openVoiceAfterRecognition;
    private FrameLayout aiNavigationHost;
    private View aiNavigationBackdrop;
    private boolean navigationDisposed;
    private final Runnable navigationUpdate = this::applyNavigationVisibility;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        voiceEntry = new com.fongmi.android.tv.ai.VoiceEntryController(this);
        aiVoice = new com.fongmi.android.tv.ai.VoiceHoldController(this, text -> {
            if (currentPosition != POSITION_VOD || openVoiceAfterRecognition) {
                boolean openConversation = openVoiceAfterRecognition;
                openVoiceAfterRecognition = false;
                voiceEntry.start(text);
                if (openConversation) voiceEntry.openConversation();
                return kotlin.Unit.INSTANCE;
            }
            aiVoice.finishThinking(null);
            Fragment fragment = mManager.getFragment(POSITION_VOD);
            if (fragment instanceof com.fongmi.android.tv.ui.fragment.DiscoverFragment)
                ((com.fongmi.android.tv.ui.fragment.DiscoverFragment) fragment).acceptVoice(text);
            return kotlin.Unit.INSTANCE;
        });
        // 确保通知渠道已创建
        com.fongmi.android.tv.utils.Notify.createChannel();

        applyWindowInsets();
        orientation = getResources().getConfiguration().orientation;
        windowWidthDp = ResUtil.getWindowWidthDp(this);
        currentPosition = savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_POSITION, 0);
        bottomNavigationVisible = true;
        if (currentPosition == POSITION_VOD) getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        initFragment(savedInstanceState);
        Server.get().start();
        initConfig();
        setNavigation();
        mBinding.glassNavigation.setBackdropView(mBinding.container);
        View.OnLayoutChangeListener navLayout = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) com.github.catvod.utils.Logger.d("AiNavigation phase=layout view=" + v.getClass().getSimpleName()
                    + " measuredHeight=" + (b - t) + " windowHeight=" + mBinding.getRoot().getHeight()
                    + " reservedHeight=" + getBottomNavigationHeight());
            if (mManager != null && mManager.getFragment(POSITION_VOD) instanceof com.fongmi.android.tv.ui.fragment.DiscoverFragment)
                ((com.fongmi.android.tv.ui.fragment.DiscoverFragment) mManager.getFragment(POSITION_VOD)).updateNavigationHeight(getBottomNavigationHeight());
        };
        mBinding.glassNavigation.addOnLayoutChangeListener(navLayout);
        mBinding.navigation.addOnLayoutChangeListener(navLayout);
        applyNavigationMode();
        int itemId = getNavigationItemId(currentPosition);
        mBinding.navigation.setSelectedItemId(itemId);
        mBinding.glassNavigation.setSelectedItemId(itemId);
        setSettingsChrome(currentPosition == POSITION_SETTING);
        // 上次没跑完的离线缓存在这里续上，放到界面可见之后再拉前台服务，避免后台启动被系统拒绝
        App.execute(() -> DownloadManager.get().restore());
    }

    /**
     * 布局不吃系统栏内边距，手动把状态栏高度补给内容区、把手势条高度补给底栏，
     * 这样底栏底色会一直铺到屏幕最底部，系统小白条区域和底栏融为一体。
     */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            mTopInset = bars.top;
            mBottomInset = bars.bottom;
            mBinding.glassNavigation.setBottomInsetPixels(mBottomInset);
            if (mManager != null && mManager.getFragment(POSITION_VOD) instanceof com.fongmi.android.tv.ui.fragment.DiscoverFragment)
                ((com.fongmi.android.tv.ui.fragment.DiscoverFragment) mManager.getFragment(POSITION_VOD)).updateSystemInsets(mTopInset, mBottomInset);
            RelativeLayout.LayoutParams statusParams = (RelativeLayout.LayoutParams) mBinding.statusScrim.getLayoutParams();
            if (statusParams.height != mTopInset) {
                statusParams.height = mTopInset;
                mBinding.statusScrim.setLayoutParams(statusParams);
            }
            mBinding.navigation.setPadding(0, 0, 0, mBottomInset);
            applyContainerPadding();
            return insets;
        });
    }

    /**
     * 传统底栏隐藏时由内容区避让手势条；玻璃底栏模式始终让页面铺到屏幕底部，
     * 即使搜索时暂时隐藏底栏，小白条区域也继续保持沉浸。
     */
    private void applyContainerPadding() {
        if (mBinding == null) return;
        boolean navVisible = mBinding.navigation.getVisibility() == View.VISIBLE || mBinding.glassNavigation.getVisibility() == View.VISIBLE;
        // 首页和设置页在自己的顶栏内避让系统图标，背景可连续延伸到状态栏。
        int top = 0; // Each mobile page owns its status-bar inset.
        // 玻璃底栏模式本来就是让内容铺到系统手势条后面；搜索时隐藏底栏也继续保持这种沉浸，
        // 不再额外垫一整条导航栏保护区。传统底栏模式仍保留原来的安全内边距。
        int bottom = currentPosition == POSITION_VOD || navVisible || glassNavigationEnabled ? 0 : mBottomInset;
        mBinding.container.setPadding(0, top, 0, bottom);
    }

    @Override
    protected void initEvent() {
        mBinding.navigation.setOnItemSelectedListener(this);
        mBinding.navigation.findViewById(R.id.live).setOnLongClickListener(this::addShortcut);
        mBinding.navigation.findViewById(R.id.vod).setOnTouchListener((view, event) ->
                currentPosition != POSITION_VOD && aiVoice.touch(view, event));
        mBinding.glassNavigation.setListener(this);
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
                loadLive("file:/" + FileChooser.getPathFromUri(this, intent.getData()));
            } else {
                VideoActivity.push(this, intent.getData().toString());
            }
        }
    }

    private void initFragment(Bundle savedInstanceState) {
        mManager = new FragmentStateManager(mBinding.container, getSupportFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                if (position == POSITION_RECOMMEND) return RecommendFragment.newInstance();
                if (position == POSITION_VOD) return new com.fongmi.android.tv.ui.fragment.DiscoverFragment();
                if (position == POSITION_SETTING) return new com.fongmi.android.tv.ui.fragment.ProfileFragment();
                return null;
            }
        };
        if (savedInstanceState == null) {
            currentPosition = POSITION_RECOMMEND;
            mManager.change(POSITION_RECOMMEND);
        }
    }

    private void initConfig() {
        LiveConfig.get().init().load();
        VodConfig.get().init().load(getCallback());
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success(String result) {
                Notify.show(result);
            }

            @Override
            public void success() {
                checkAction(getIntent());
                RefreshEvent.config();
                RefreshEvent.video();
            }

            @Override
            public void error(String msg) {
                RefreshEvent.config();
                StateEvent.empty();
                // 断网时配置当然拉不下来，别把"配置获取失败"甩给用户，先说清是网络问题
                Notify.show(com.fongmi.android.tv.utils.Util.isNetworkAvailable() ? msg : getString(R.string.error_network));
            }
        };
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                openLive();
            }
        });
    }

    private void setNavigation() {
        mBinding.navigation.getMenu().findItem(R.id.recommend).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.vod).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.vod).setIcon(aiOrb);
        mBinding.navigation.getMenu().findItem(R.id.setting).setVisible(true);
        boolean liveVisible = LiveConfig.hasUrl() && !Setting.isLiveTabVisible();
        mBinding.navigation.getMenu().findItem(R.id.live).setVisible(liveVisible);
        mBinding.glassNavigation.setLiveVisible(liveVisible);
        refreshBackHandling();
    }

    private boolean openLive() {
        LiveActivity.start(this);
        return false;
    }

    private boolean addShortcut(View view) {
        ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(this, getString(R.string.nav_live)).setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher)).setIntent(new Intent(Intent.ACTION_VIEW, null, this, LiveActivity.class)).setShortLabel(getString(R.string.nav_live)).build();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, ShortcutReceiver.class).setAction(ShortcutReceiver.ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ShortcutManagerCompat.requestPinShortcut(this, info, pendingIntent.getIntentSender());
        return true;
    }

    public void change(int position) {
        if (position != POSITION_VOD) setAiThinking(false);
        if (currentPosition == POSITION_VOD && position != POSITION_VOD) {
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mBinding.container.getWindowToken(), 0);
        }
        if (position == POSITION_VOD) getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        currentPosition = position;
        bottomNavigationVisible = true;
        updateNavigationVisibility();
        if (position != POSITION_VOD) {
            VodFragment fragment = getVodFragment();
            if (fragment != null) fragment.dismissFilterPanel();
        }
        setSettingsChrome(position == POSITION_SETTING);
        mManager.change(position);
        if (position == POSITION_VOD && mManager.getFragment(position) instanceof com.fongmi.android.tv.ui.fragment.DiscoverFragment) {
            com.fongmi.android.tv.ui.fragment.DiscoverFragment page =
                    (com.fongmi.android.tv.ui.fragment.DiscoverFragment) mManager.getFragment(position);
            page.onPageSelected();
            mBinding.container.post(() -> {
                if (currentPosition != POSITION_VOD || isFinishing() || isDestroyed()) return;
                page.updateNavigationHeight(getBottomNavigationHeight());
            });
        }
        int itemId = getNavigationItemId(position);
        mBinding.glassNavigation.setSelectedItemId(itemId);
        updateGlassActionForCurrentPage();
        refreshBackHandling();
        if (position == POSITION_RECOMMEND) checkAutomaticUpdate();
    }

    private void setSettingsChrome(boolean settings) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (!night) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        // 同 BaseActivity：透明系统栏会招来系统自动垫的对比度 scrim，这里一并关掉
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        mBinding.getRoot().setBackgroundColor(getColor(R.color.screen_background));
        applyContainerPadding();
    }

    public void setBottomNavigationVisible(boolean visible) {
        if (mBinding == null) return;
        boolean show = visible || currentPosition == POSITION_SETTING || currentPosition == POSITION_VOD;
        bottomNavigationVisible = show;
        updateNavigationVisibility();
        refreshBackHandling();
    }

    private void updateNavigationVisibility() {
        if (mBinding == null || navigationDisposed) return;
        // AndroidView.factory can register its host from inside RelativeLayout.onMeasure.
        // That measure pass still holds the old sorted child array. Reparenting now would
        // replace a cached child's RelativeLayout.LayoutParams with FrameLayout.LayoutParams.
        // Coalesce updates and read the latest page/host only after the traversal returns.
        mBinding.getRoot().removeCallbacks(navigationUpdate);
        mBinding.getRoot().post(navigationUpdate);
    }

    private void applyNavigationVisibility() {
        if (mBinding == null || navigationDisposed || isFinishing() || isDestroyed()) return;
        if (mBinding.getRoot().isInLayout()) {
            mBinding.getRoot().post(navigationUpdate);
            return;
        }
        placeNavigation();
        boolean showLegacy = bottomNavigationVisible && !glassNavigationEnabled;
        boolean showGlass = bottomNavigationVisible && glassNavigationEnabled;
        mBinding.navigation.setVisibility(showLegacy ? View.VISIBLE : View.GONE);
        mBinding.glassNavigation.setVisibility(showGlass ? View.VISIBLE : View.GONE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBinding.container.getLayoutParams();
        params.removeRule(RelativeLayout.ABOVE);
        if (showLegacy && currentPosition != POSITION_VOD) params.addRule(RelativeLayout.ABOVE, R.id.navigation);
        mBinding.container.setLayoutParams(params);
        applyContainerPadding();
        mBinding.glassNavigation.setRenderingEnabled(showGlass);
        aiOrb.setRunning(showLegacy);
    }

    public int getBottomNavigationHeight() {
        View nav = glassNavigationEnabled ? mBinding.glassNavigation : mBinding.navigation;
        int measured = nav.getHeight();
        // Before layout, use the real capsule dimensions (52dp + 7dp padding on each side).
        // A transient/stale full-window measurement must never consume the conversation viewport.
        return measured > 0 && measured <= ResUtil.dp2px(120) + mBottomInset
                ? measured : ResUtil.dp2px(glassNavigationEnabled ? 66 : 56) + mBottomInset;
    }

    public void registerAiNavigationHost(FrameLayout host, View backdrop) {
        if (navigationDisposed) return;
        aiNavigationHost = host;
        aiNavigationBackdrop = backdrop;
        updateNavigationVisibility();
    }

    public void releaseAiNavigationHost(FrameLayout host) {
        if (aiNavigationHost != host) return;
        aiNavigationHost = null;
        aiNavigationBackdrop = null;
        if (!isDestroyed()) updateNavigationVisibility();
    }

    private void placeNavigation() {
        if (mBinding == null) return;
        boolean inAiCard = currentPosition == POSITION_VOD && aiNavigationHost != null;
        ViewGroup target = inAiCard ? aiNavigationHost : mBinding.getRoot();
        for (View nav : new View[]{mBinding.navigation, mBinding.glassNavigation}) {
            if (nav.getParent() != target) {
                if (nav.getParent() instanceof ViewGroup) ((ViewGroup) nav.getParent()).removeView(nav);
                if (inAiCard) {
                    target.addView(nav, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
                } else {
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    target.addView(nav, target.indexOfChild(mBinding.statusScrim), params);
                }
                com.github.catvod.utils.Logger.d("AiNavigation phase=reparent inAiCard=" + inAiCard
                        + " view=" + nav.getClass().getSimpleName() + " parent=" + target.getClass().getSimpleName()
                        + " params=" + nav.getLayoutParams().getClass().getName() + " deferred=true");
            }
            nav.animate().cancel();
            nav.setAlpha(1f);
            nav.setTranslationX(0f);
            nav.setTranslationY(0f);
            nav.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
        mBinding.glassNavigation.setVoiceGestureEnabled(currentPosition != POSITION_VOD);
        // The sampling source excludes the navigation itself, avoiding recursive page capture.
        mBinding.glassNavigation.setBackdropView(inAiCard ? aiNavigationBackdrop : mBinding.container);
    }

    private void applyNavigationMode() {
        boolean enabled = Setting.isLiquidGlassNavigation();
        boolean changed = glassNavigationEnabled != enabled;
        glassNavigationEnabled = enabled;
        mBinding.glassNavigation.setAccentColor(getColor(com.fongmi.android.tv.utils.ThemeUtil.getAccentColorResource()));
        updateNavigationVisibility();
        updateGlassActionForCurrentPage();
        VodFragment fragment = getVodFragment();
        if (changed && fragment != null) fragment.onNavigationModeChanged();
    }

    public void refreshNavigationMode() {
        applyNavigationMode();
    }

    public boolean isGlassNavigationEnabled() {
        return glassNavigationEnabled;
    }

    public int getSystemBarTopInset() { return mTopInset; }
    public int getSystemBarBottomInset() { return mBottomInset; }

    public void setFilterOverlayVisible(boolean visible) {
        if (mBinding == null) return;
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        float targetAlpha = visible ? (night ? 0.22f : 0.12f) : 0f;
        mBinding.statusScrim.animate().cancel();
        mBinding.statusScrim.animate().alpha(targetAlpha).setDuration(160L).start();
        refreshBackHandling();
    }

    public void setGlassAction(int action, boolean visible) {
        if (mBinding == null) return;
        // 暂时隐藏底栏旁的独立动作圆钮，只保留悬浮导航胶囊。
        mBinding.glassNavigation.setAction(LiquidGlassNavigationView.ACTION_NONE, false);
    }

    private void updateGlassActionForCurrentPage() {
        if (!glassNavigationEnabled) return;
        setGlassAction(LiquidGlassNavigationView.ACTION_NONE, false);
    }

    private VodFragment getVodFragment() {
        Fragment page = mManager == null ? null : mManager.getFragment(POSITION_VOD);
        return page instanceof VodFragment ? (VodFragment) page : null;
    }

    private RecommendFragment getRecommendFragment() {
        return mManager == null ? null : (RecommendFragment) mManager.getFragment(POSITION_RECOMMEND);
    }

    public void searchFromAi(String keyword) {
        searchFromAi(keyword, "");
    }

    public void searchFromAi(String keyword, String year) {
        mBinding.navigation.setSelectedItemId(R.id.recommend);
        mBinding.container.post(() -> {
            RecommendFragment fragment = getRecommendFragment();
            if (fragment != null) fragment.searchKeyword(keyword, year);
        });
    }

    public boolean onAiVoiceTouch(View view, android.view.MotionEvent event) {
        return aiVoice.touch(view, event);
    }

    public void onAiVoiceLongPress(View view, float x, float y) {
        aiVoice.startHold(view, x, y);
    }

    public void setAiRecording(boolean recording) {
        if (recording) openVoiceAfterRecognition = false;
        if (recording && voiceEntry != null) voiceEntry.cancel();
        aiRecording = recording;
        updateAiFace();
    }

    public void showVoiceThinking(String status) { if (aiVoice != null) aiVoice.showThinking(status); }
    public boolean isVoiceNavigationAvailable() {
        return currentPosition != POSITION_VOD && glassNavigationEnabled;
    }
    public void setVoiceNavigationState(boolean active, float progress, String status) {
        mBinding.glassNavigation.setVoiceState(active, progress, status);
    }
    @Override
    public void onGlassVoiceCancel() {
        openVoiceAfterRecognition = false;
        if (voiceEntry != null) voiceEntry.cancel();
        if (aiVoice != null) aiVoice.cancel();
    }
    @Override
    public void onGlassVoiceOpen() {
        if (voiceEntry != null && voiceEntry.openConversation()) return;
        openVoiceAfterRecognition = true;
        // Recognition can still be finishing before the first AI request exists.
        finishVoiceThinking(() -> {
            if (voiceEntry == null || !voiceEntry.openConversation()) onGlassNavigationSelected(R.id.vod);
        });
    }
    public com.fongmi.android.tv.ui.fragment.DiscoverFragment openVoiceConversation() {
        if (aiVoice != null) aiVoice.detachThinking();
        // The legacy selection callback cancels requests; this path keeps the active one.
        onGlassNavigationSelected(R.id.vod);
        Fragment fragment = mManager.getFragment(POSITION_VOD);
        return fragment instanceof com.fongmi.android.tv.ui.fragment.DiscoverFragment
                ? (com.fongmi.android.tv.ui.fragment.DiscoverFragment) fragment : null;
    }
    public void finishVoiceThinking(Runnable after) {
        finishVoiceThinking(false, after);
    }
    public void finishVoiceThinking(boolean found, Runnable after) {
        if (aiVoice != null) aiVoice.finishThinking(after, found);
        else if (after != null) after.run();
    }

    public void showAiVoiceResult(String input, String raw, com.fongmi.android.tv.ai.AiReply reply, String error, long askedAt) {
        mBinding.navigation.setSelectedItemId(R.id.vod);
        Fragment fragment = mManager.getFragment(POSITION_VOD);
        if (fragment instanceof com.fongmi.android.tv.ui.fragment.DiscoverFragment)
            ((com.fongmi.android.tv.ui.fragment.DiscoverFragment) fragment).acceptResolvedVoice(input, raw, reply, error, askedAt);
    }

    public void setAiThinking(boolean thinking) { aiThinking = thinking; updateAiFace(); }

    private void updateAiFace() {
        int mode = aiRecording ? 1 : aiThinking ? 2 : 0;
        aiOrb.setMode(mode);
        if (mBinding != null) mBinding.glassNavigation.setAiMode(mode);
    }

    @Override
    public boolean onGlassAiTouch(View view, android.view.MotionEvent event) {
        return currentPosition != POSITION_VOD && onAiVoiceTouch(view, event);
    }

    private int getNavigationItemId(int position) {
        if (position == POSITION_VOD) return R.id.vod;
        if (position == POSITION_SETTING) return R.id.setting;
        return R.id.recommend;
    }

    @Override
    public void onRefreshEvent(RefreshEvent event) {
        super.onRefreshEvent(event);
        if (event.getType().equals(RefreshEvent.Type.CONFIG)) setNavigation();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.getType() != ServerEvent.Type.PUSH) return;
        VideoActivity.push(this, event.getText());
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (mBinding.navigation.getSelectedItemId() == item.getItemId()) return false;
        if (voiceEntry != null) voiceEntry.cancel();
        if (item.getItemId() == R.id.recommend) {
            change(POSITION_RECOMMEND);
            return true;
        }
        if (item.getItemId() == R.id.setting) {
            change(POSITION_SETTING);
            return true;
        }
        if (item.getItemId() == R.id.vod) {
            change(POSITION_VOD);
            return true;
        }
        if (item.getItemId() == R.id.live) {
            if (LiveConfig.isEmpty()) {
                Notify.showCenter(R.string.error_no_live);
                return false;
            }
            return openLive();
        }
        return false;
    }

    @Override
    public void onGlassNavigationSelected(int itemId) {
        if (itemId == R.id.live) {
            if (LiveConfig.isEmpty()) Notify.showCenter(R.string.error_no_live);
            else openLive();
            return;
        }
        if (itemId == R.id.recommend && currentPosition != POSITION_RECOMMEND) {
            mBinding.navigation.setOnItemSelectedListener(null);
            mBinding.navigation.setSelectedItemId(R.id.recommend);
            mBinding.navigation.setOnItemSelectedListener(this);
            change(POSITION_RECOMMEND);
        } else if (itemId == R.id.setting && currentPosition != POSITION_SETTING) {
            mBinding.navigation.setOnItemSelectedListener(null);
            mBinding.navigation.setSelectedItemId(R.id.setting);
            mBinding.navigation.setOnItemSelectedListener(this);
            change(POSITION_SETTING);
        } else if (itemId == R.id.vod && currentPosition != POSITION_VOD) {
            mBinding.navigation.setOnItemSelectedListener(null);
            mBinding.navigation.setSelectedItemId(R.id.vod);
            mBinding.navigation.setOnItemSelectedListener(this);
            change(POSITION_VOD);
        }
    }

    @Override
    public void onGlassContextAction() {
        if (currentPosition == POSITION_SETTING) {
            ProfileSettingsActivity.start(this);
        } else if (currentPosition == POSITION_VOD) {
            VodFragment fragment = getVodFragment();
            if (fragment != null) fragment.performGlassAction();
        }
    }

    @Override
    public void onGlassContextLongAction() {
        if (currentPosition != POSITION_VOD) return;
        VodFragment fragment = getVodFragment();
        if (fragment != null) fragment.performGlassLongAction();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_POSITION, currentPosition);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        App.post(() -> checkWindow(newConfig), 100);
    }

    private void checkWindow(Configuration newConfig) {
        int newWindowWidthDp = ResUtil.getWindowWidthDp(this);
        if (orientation == newConfig.orientation && windowWidthDp == newWindowWidthDp) return;
        orientation = newConfig.orientation;
        windowWidthDp = newWindowWidthDp;
        RefreshEvent.video();
    }

    @Override
    protected boolean shouldInterceptBack() {
        if (mBinding == null || mManager == null) return false;
        if (!mBinding.navigation.getMenu().findItem(R.id.vod).isVisible()) return true;
        if (mManager.isVisible(POSITION_SETTING)) return true;
        if (mManager.isVisible(POSITION_VOD)) {
            VodFragment fragment = getVodFragment();
            return true;
        }
        if (mManager.isVisible(POSITION_RECOMMEND)) {
            RecommendFragment fragment = getRecommendFragment();
            return fragment != null && fragment.hasBackState();
        }
        return false;
    }

    @Override
    protected boolean shouldAnimatePredictiveBack() {
        if (mManager != null && mManager.isVisible(POSITION_VOD)) return false;
        RecommendFragment fragment = getRecommendFragment();
        if (mManager != null && mManager.isVisible(POSITION_RECOMMEND)
                && fragment != null && fragment.isEditingSearch()) return false;
        return super.shouldAnimatePredictiveBack();
    }

    @Override
    protected void onBackPress() {
        if (!mBinding.navigation.getMenu().findItem(R.id.vod).isVisible()) {
            setNavigation();
        } else if (mManager.isVisible(POSITION_SETTING)) {
            mBinding.navigation.setSelectedItemId(R.id.recommend);
        } else if (mManager.isVisible(POSITION_VOD)) {
            Fragment page = mManager.getFragment(POSITION_VOD);
            if (page instanceof com.fongmi.android.tv.ui.fragment.DiscoverFragment
                    && ((com.fongmi.android.tv.ui.fragment.DiscoverFragment) page).closeHistoryIfOpen()) return;
            mBinding.navigation.setSelectedItemId(R.id.recommend);
        } else if (mManager.isVisible(POSITION_RECOMMEND) && mManager.canBack(POSITION_RECOMMEND)) {
            super.onBackPress();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBinding != null) applyNavigationMode();
        // Lifecycle reaches RESUMED after this callback; defer the initial home check.
        if (mBinding != null) mBinding.getRoot().post(this::checkAutomaticUpdate);
    }

    private boolean canShowAutomaticUpdate() {
        return currentPosition == POSITION_RECOMMEND && !isFinishing() && !isDestroyed()
                && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED);
    }

    private void checkAutomaticUpdate() {
        if (canShowAutomaticUpdate()) automaticUpdater.start(this, this::canShowAutomaticUpdate);
    }

    @Override
    protected void onPause() {
        openVoiceAfterRecognition = false;
        if (voiceEntry != null) voiceEntry.cancel();
        aiOrb.setRunning(false);
        if (aiVoice != null) aiVoice.cancel();
        if (mBinding != null) mBinding.glassNavigation.setRenderingEnabled(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (voiceEntry != null) voiceEntry.cancel();
        navigationDisposed = true;
        if (mBinding != null) mBinding.getRoot().removeCallbacks(navigationUpdate);
        if (aiVoice != null) aiVoice.destroy();
        if (mBinding != null) {
            mBinding.glassNavigation.setRenderingEnabled(false);
            mBinding.glassNavigation.setBackdropView(null);
        }
        LiveConfig.get().clear();
        VodConfig.get().clear();
        OkHttp.get().clear();
        AppDatabase.backup();
        Source.get().exit();
        // 投屏时电视还在向这个 HTTP 服务拉流，首页销毁不能把它关掉，否则电视立刻卡死
        if (!CastManager.get().isCasting()) Server.get().stop();
        super.onDestroy();
    }
}
