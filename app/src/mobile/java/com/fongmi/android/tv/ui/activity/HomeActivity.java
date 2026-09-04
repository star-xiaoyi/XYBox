package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
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
    private FragmentStateManager mManager;
    private ActivityHomeBinding mBinding;
    private int mTopInset;
    private int mBottomInset;
    private int currentPosition;
    private int orientation;
    private int windowWidthDp;
    private boolean bottomNavigationVisible = true;
    private boolean glassNavigationEnabled;

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
        // 确保通知渠道已创建
        com.fongmi.android.tv.utils.Notify.createChannel();

        applyWindowInsets();
        orientation = getResources().getConfiguration().orientation;
        windowWidthDp = ResUtil.getWindowWidthDp(this);
        currentPosition = savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_POSITION, 0);
        // Updater.create().release().start(this); // 移除自动检查更新，只在点击版本号时检查
        initFragment(savedInstanceState);
        Server.get().start();
        initConfig();
        setNavigation();
        mBinding.glassNavigation.setBackdropView(mBinding.container);
        applyNavigationMode();
        mBinding.navigation.setSelectedItemId(currentPosition == 1 ? R.id.setting : R.id.vod);
        mBinding.glassNavigation.setSelectedItemId(currentPosition == 1 ? R.id.setting : R.id.vod);
        setSettingsChrome(currentPosition == 1);
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
            mBinding.navigation.setPadding(0, 0, 0, mBottomInset);
            applyContainerPadding();
            return insets;
        });
    }

    /**
     * 底栏在时由底栏自己垫手势条；底栏隐藏时（比如搜索结果页）由内容区垫，
     * 垫出来的那块是页面背景色，所以看不到系统那条白色矩形。
     */
    private void applyContainerPadding() {
        if (mBinding == null) return;
        boolean navVisible = mBinding.navigation.getVisibility() == View.VISIBLE || mBinding.glassNavigation.getVisibility() == View.VISIBLE;
        mBinding.container.setPadding(0, mTopInset, 0, navVisible ? 0 : mBottomInset);
    }

    @Override
    protected void initEvent() {
        mBinding.navigation.setOnItemSelectedListener(this);
        mBinding.navigation.findViewById(R.id.live).setOnLongClickListener(this::addShortcut);
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
                if (position == 0) return VodFragment.newInstance();
                if (position == 1) return SettingFragment.newInstance();
                return null;
            }
        };
        if (savedInstanceState == null) mManager.change(0);
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
        mBinding.navigation.getMenu().findItem(R.id.vod).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.setting).setVisible(true);
        boolean liveVisible = LiveConfig.hasUrl() && !Setting.isLiveTabVisible();
        mBinding.navigation.getMenu().findItem(R.id.live).setVisible(liveVisible);
        mBinding.glassNavigation.setLiveVisible(liveVisible);
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
        currentPosition = position;
        setSettingsChrome(position == 1);
        mManager.change(position);
        int itemId = position == 1 ? R.id.setting : R.id.vod;
        mBinding.glassNavigation.setSelectedItemId(itemId);
        updateGlassActionForCurrentPage();
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
    }

    public void setBottomNavigationVisible(boolean visible) {
        if (mBinding == null) return;
        boolean show = visible || currentPosition == 1;
        bottomNavigationVisible = show;
        updateNavigationVisibility();
    }

    private void updateNavigationVisibility() {
        boolean showLegacy = bottomNavigationVisible && !glassNavigationEnabled;
        boolean showGlass = bottomNavigationVisible && glassNavigationEnabled;
        mBinding.navigation.setVisibility(showLegacy ? View.VISIBLE : View.GONE);
        mBinding.glassNavigation.setVisibility(showGlass ? View.VISIBLE : View.GONE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBinding.container.getLayoutParams();
        params.removeRule(RelativeLayout.ABOVE);
        if (showLegacy) params.addRule(RelativeLayout.ABOVE, R.id.navigation);
        mBinding.container.setLayoutParams(params);
        applyContainerPadding();
        mBinding.glassNavigation.setRenderingEnabled(showGlass);
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

    public boolean isGlassNavigationEnabled() {
        return glassNavigationEnabled;
    }

    public void setGlassAction(int action, boolean visible) {
        if (mBinding != null) mBinding.glassNavigation.setAction(action, visible && glassNavigationEnabled);
    }

    private void updateGlassActionForCurrentPage() {
        if (!glassNavigationEnabled || mManager == null) return;
        if (currentPosition == 1) {
            SettingFragment fragment = (SettingFragment) mManager.getFragment(1);
            boolean searching = fragment != null && fragment.isSearchActive();
            setGlassAction(searching ? LiquidGlassNavigationView.ACTION_CLOSE : LiquidGlassNavigationView.ACTION_SEARCH, true);
        } else {
            VodFragment fragment = getVodFragment();
            if (fragment == null) setGlassAction(LiquidGlassNavigationView.ACTION_NONE, false);
            else fragment.syncGlassAction();
        }
    }

    private VodFragment getVodFragment() {
        return mManager == null ? null : (VodFragment) mManager.getFragment(0);
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
        if (item.getItemId() == R.id.setting) {
            change(1);
            return true;
        }
        if (item.getItemId() == R.id.vod) {
            change(0);
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
        if (itemId == R.id.setting && currentPosition != 1) {
            mBinding.navigation.setOnItemSelectedListener(null);
            mBinding.navigation.setSelectedItemId(R.id.setting);
            mBinding.navigation.setOnItemSelectedListener(this);
            change(1);
        } else if (itemId == R.id.vod && currentPosition != 0) {
            mBinding.navigation.setOnItemSelectedListener(null);
            mBinding.navigation.setSelectedItemId(R.id.vod);
            mBinding.navigation.setOnItemSelectedListener(this);
            change(0);
        }
    }

    @Override
    public void onGlassContextAction() {
        if (currentPosition == 1) {
            SettingFragment fragment = (SettingFragment) mManager.getFragment(1);
            if (fragment != null) fragment.toggleSearch();
        } else {
            VodFragment fragment = getVodFragment();
            if (fragment != null) fragment.performGlassAction();
        }
    }

    @Override
    public void onGlassContextLongAction() {
        if (currentPosition != 0) return;
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

    protected boolean handleBack() {
        return true;
    }

    @Override
    protected void onBackPress() {
        if (!mBinding.navigation.getMenu().findItem(R.id.vod).isVisible()) {
            setNavigation();
        } else if (mManager.isVisible(1)) {
            SettingFragment fragment = (SettingFragment) mManager.getFragment(1);
            if (fragment != null && fragment.closeSearchIfActive()) return;
            mBinding.navigation.setSelectedItemId(R.id.vod);
        } else if (mManager.canBack(0)) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBinding != null) applyNavigationMode();
    }

    @Override
    protected void onPause() {
        if (mBinding != null) mBinding.glassNavigation.setRenderingEnabled(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
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
