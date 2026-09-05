package com.fongmi.android.tv.ui.fragment;
import com.github.catvod.utils.Logger;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.FragmentSettingBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.impl.LiveCallback;
import com.fongmi.android.tv.impl.ProxyCallback;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.activity.SettingPlayerActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.SettingsGlassContentView;
import com.fongmi.android.tv.ui.custom.LiquidGlassNavigationView;
import com.fongmi.android.tv.ui.dialog.AboutDialog;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.ProxyDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThemeUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.WebDAVSyncManager;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingFragment extends BaseFragment implements ConfigCallback, SiteCallback, LiveCallback, ProxyCallback {

    private FragmentSettingBinding mBinding;
    private String[] size;
    private int type;
    private boolean searchActive;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private String getProxy(String proxy) {
        return proxy.isEmpty() ? getString(R.string.none) : UrlUtil.scheme(proxy);
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private HomeActivity getRoot() {
        return (HomeActivity) getActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setSourceText();
        mBinding.settingsContent.setVersion(getString(R.string.setting_version) + " " + BuildConfig.VERSION_NAME);
        setOtherText();
        setCacheText();
    }

    private void setOtherText() {
        mBinding.settingsContent.setDohOptions(getDohList(), getDohIndex());
        mBinding.settingsContent.setProxy(getProxy(Setting.getProxy()));
        mBinding.settingsContent.setProxyEditor(Setting.getProxy());
        mBinding.settingsContent.setWebDavEditor(
                Setting.getWebDAVUrl(), Setting.getWebDAVUsername(), Setting.getWebDAVPassword());
        mBinding.settingsContent.setIncognitoChecked(Setting.isIncognito());
        mBinding.settingsContent.setLiveTabVisibleChecked(Setting.isLiveTabVisible());
        mBinding.settingsContent.setHistoryVisibleChecked(Setting.isHistoryVisible());
        size = ResUtil.getStringArray(R.array.select_size);
        mBinding.settingsContent.setSizeOptions(size, Setting.getSize());
        mBinding.settingsContent.setThemeOptions(getThemeNames(), Setting.getThemeMode());
        mBinding.settingsContent.setAccentOptions(getAccentNames(), Setting.getAccentColor(), requireContext().getColor(ThemeUtil.getAccentColorResource()));
        setLiveSettingsVisibility();
    }

    private void setSourceText() {
        mBinding.settingsContent.setSourceDescriptions(
                getSourceText(VodConfig.getDesc(), R.string.source_hint_setting),
                getSourceText(LiveConfig.getDesc(), R.string.source_hint_live));
        Config vod = VodConfig.get().getConfig();
        Config live = LiveConfig.get().getConfig();
        mBinding.settingsContent.setSourceEditors(
                vod == null ? "" : vod.getName(), vod == null ? "" : vod.getUrl(),
                live == null ? "" : live.getName(), live == null ? "" : live.getUrl());
    }

    private String getSourceText(String desc, int hintStringRes) {
        return TextUtils.isEmpty(desc) ? getString(hintStringRes) : desc;
    }

    private String[] getThemeNames() {
        return new String[]{getString(R.string.setting_theme_system), getString(R.string.setting_theme_light), getString(R.string.setting_theme_dark)};
    }

    private String[] getAccentNames() {
        return new String[]{getString(R.string.setting_accent_yellow), getString(R.string.setting_accent_blue), getString(R.string.setting_accent_green), getString(R.string.setting_accent_purple)};
    }

    private void setLiveSettingsVisibility() {
        // 设置项表达的是“隐藏直播”，所以 true 时不再显示直播源配置。
        mBinding.settingsContent.setLiveVisible(!Setting.isLiveTabVisible());
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                if (mBinding != null) mBinding.settingsContent.setCache(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.settingsContent.setOnActionListener(this::onSettingAction);
        mBinding.settingsContent.setOnLongActionListener(this::onSettingLongAction);
        mBinding.settingsContent.setOnToggleListener(this::onSettingToggle);
        mBinding.settingsContent.setOnOptionListener(this::onSettingOption);
        mBinding.settingsContent.setOnEditorSaveListener(this::onInlineEditorSave);
        mBinding.settingsContent.setOnWebDavActionListener(this::onInlineWebDavAction);
        mBinding.settingsHeader.setOnQueryChangedListener(mBinding.settingsContent::setQuery);
        mBinding.settingsHeader.setOnSearchStateChangedListener(active -> {
            searchActive = active;
            getRoot().setGlassAction(active ? LiquidGlassNavigationView.ACTION_CLOSE : LiquidGlassNavigationView.ACTION_SEARCH, true);
        });
    }

    private void onSettingAction(int action) {
        switch (action) {
            case SettingsGlassContentView.ACTION_VOD_HOME: onVodHome(null); break;
            case SettingsGlassContentView.ACTION_LIVE_HOME: onLiveHome(null); break;
            case SettingsGlassContentView.ACTION_VOD_HISTORY: onVodHistory(null); break;
            case SettingsGlassContentView.ACTION_LIVE_HISTORY: onLiveHistory(null); break;
            case SettingsGlassContentView.ACTION_PLAYER: onPlayer(null); break;
            case SettingsGlassContentView.ACTION_OPERATION: onOperation(null); break;
            case SettingsGlassContentView.ACTION_SYNC: onSyncSettings(null); break;
            case SettingsGlassContentView.ACTION_CACHE: onCache(null); break;
            case SettingsGlassContentView.ACTION_BACKUP: onBackup(null); break;
            case SettingsGlassContentView.ACTION_RESTORE: onRestore(null); break;
            case SettingsGlassContentView.ACTION_LABORATORY:
                com.fongmi.android.tv.ui.activity.SettingLaboratoryActivity.start(requireActivity());
                break;
            case SettingsGlassContentView.ACTION_VERSION: onVersion(null); break;
            case SettingsGlassContentView.ACTION_ABOUT: onAbout(null); break;
        }
    }

    private void onSettingLongAction(int action) {
        switch (action) {
            case SettingsGlassContentView.ACTION_VERSION: onVersionDev(null); break;
        }
    }

    private void onSettingToggle(int action, boolean checked) {
        switch (action) {
            case SettingsGlassContentView.ACTION_INCOGNITO: setIncognito(checked); break;
            case SettingsGlassContentView.ACTION_LIVE_TAB_VISIBLE: setLiveTabVisible(checked); break;
            case SettingsGlassContentView.ACTION_HISTORY_VISIBLE: setHistoryVisible(checked); break;
        }
    }

    private void onSettingOption(int action, int index) {
        switch (action) {
            case SettingsGlassContentView.ACTION_THEME: setTheme(index); break;
            case SettingsGlassContentView.ACTION_ACCENT: setAccent(index); break;
            case SettingsGlassContentView.ACTION_SIZE: setSize(index); break;
            case SettingsGlassContentView.ACTION_DOH: setDoh(VodConfig.get().getDoh().get(index)); break;
        }
    }

    private void onInlineEditorSave(int action, String name, String value) {
        if (action == SettingsGlassContentView.ACTION_PROXY) {
            setProxy(value);
            mBinding.settingsContent.setProxyEditor(value);
            return;
        }
        if (action != SettingsGlassContentView.ACTION_VOD && action != SettingsGlassContentView.ACTION_LIVE) return;
        if (TextUtils.isEmpty(value)) {
            Notify.tip(getString(R.string.dialog_config_hint));
            return;
        }
        int configType = action == SettingsGlassContentView.ACTION_VOD ? 0 : 1;
        Config current = configType == 0 ? VodConfig.get().getConfig() : LiveConfig.get().getConfig();
        if (current != null && !TextUtils.equals(current.getUrl(), value)) {
            WebDAVSyncManager.get().markConfigDeleted(current);
        }
        Config target = Config.find(value, configType);
        target.name(name).update();
        WebDAVSyncManager.get().requestSync();
        setConfig(target);
    }

    private void onInlineWebDavAction(int action, String url, String username, String password) {
        if (TextUtils.isEmpty(url)) {
            mBinding.settingsContent.setWebDavStatus("请输入 WebDAV 服务器地址");
            return;
        }
        if (TextUtils.isEmpty(username)) {
            mBinding.settingsContent.setWebDavStatus("请输入用户名");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            mBinding.settingsContent.setWebDavStatus("请输入密码");
            return;
        }
        Setting.putWebDAVUrl(url);
        Setting.putWebDAVUsername(username);
        Setting.putWebDAVPassword(password);
        WebDAVSyncManager manager = WebDAVSyncManager.get();
        manager.reloadConfig();
        mBinding.settingsContent.setWebDavStatus(
                action == SettingsGlassContentView.WEBDAV_TEST ? "正在测试连接…" : "正在保存并同步…");
        App.execute(() -> {
            String result;
            try {
                result = action == SettingsGlassContentView.WEBDAV_TEST
                        ? manager.testConnectionWithMessage().message
                        : manager.syncNow().message;
            } catch (Exception e) {
                result = "操作失败：" + (e.getMessage() == null ? "请检查网络连接" : e.getMessage());
            }
            String message = result;
            App.post(() -> {
                if (mBinding == null) return;
                mBinding.settingsContent.setWebDavStatus(message);
                if (action == SettingsGlassContentView.WEBDAV_SAVE) RefreshEvent.config();
            });
        });
    }

    public void toggleSearch() {
        if (mBinding == null) return;
        mBinding.settingsHeader.toggleSearch();
    }

    public boolean closeSearchIfActive() {
        if (!searchActive) return false;
        mBinding.settingsHeader.closeSearch();
        return true;
    }

    public boolean isSearchActive() {
        return searchActive;
    }

    @Override
    public void setConfig(Config config) {
        // 添加Fragment状态检查，防止在无效状态下执行
        if (getActivity() == null || !isAdded() || isDetached()) return;
        
        // 如果URL为空，不进行任何操作
        if (config == null || config.isEmpty()) return;
        
        try {
            if (config.getUrl().startsWith("file") && !PermissionX.isGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
                    if (getActivity() != null && isAdded()) {
                        load(config);
                    }
                });
            } else {
                load(config);
            }
        } catch (Exception e) {
            Logger.e("Error", e);
        }
    }

    private void load(Config config) {
        // 再次检查Fragment状态，防止在异步回调中执行
        if (getActivity() == null || !isAdded() || isDetached()) return;
        
        try {
            switch (config.getType()) {
                case 0:
                    Notify.progress(getActivity());
                    VodConfig.load(config, getCallback(0));
                    if (mBinding != null) mBinding.settingsContent.setSourceDescriptions(
                            getSourceText(config.getDesc(), R.string.source_hint_setting),
                            getSourceText(LiveConfig.getDesc(), R.string.source_hint_live));
                    break;
                case 1:
                    Notify.progress(getActivity());
                    LiveConfig.load(config, getCallback(1));
                    if (mBinding != null) mBinding.settingsContent.setSourceDescriptions(
                            getSourceText(VodConfig.getDesc(), R.string.source_hint_setting),
                            getSourceText(config.getDesc(), R.string.source_hint_live));
                    break;
            }
        } catch (Exception e) {
            Logger.e("Error", e);
            Notify.dismiss();
        }
    }

    private Callback getCallback(int type) {
        return new Callback() {
            @Override
            public void success(String result) {
                // 检查Fragment是否还在活动状态
                if (getActivity() == null || !isAdded()) return;
                Notify.show(result);
            }

            @Override
            public void success() {
                // 检查Fragment是否还在活动状态
                if (getActivity() == null || !isAdded()) return;
                setConfig(type);
            }

            @Override
            public void error(String msg) {
                // 检查Fragment是否还在活动状态
                if (getActivity() == null || !isAdded()) return;
                Notify.show(msg);
                Notify.dismiss();
                switch (type) {
                    case 0:
                        setSourceText();
                        break;
                    case 1:
                        setSourceText();
                        break;
                    case 2:
                                        break;
                }
            }
        };
    }

    private void setConfig(int type) {
        switch (type) {
            case 0:
                setCacheText();
                Notify.dismiss();
                RefreshEvent.video();
                RefreshEvent.config();
                setSourceText();
                        break;
            case 1:
                setCacheText();
                Notify.dismiss();
                RefreshEvent.config();
                setSourceText();
                break;
            case 2:
                setCacheText();
                Notify.dismiss();
                        break;
        }
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        RefreshEvent.video();
    }

    @Override
    public void onChanged() {
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onSyncSettings(View view) {
        // 直接启动扫码进行设备绑定
        // 设置默认为双向同步模式
        Setting.putSyncMode(0);
        // 启动扫码Activity
        ScanActivity.start(requireActivity());
    }

    private void onVod(View view) {
        ConfigDialog.create(this).type(type = 0).show();
    }

    private void onLive(View view) {
        ConfigDialog.create(this).type(type = 1).show();
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create(this).type(type = 0).edit().show();
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create(this).type(type = 1).edit().show();
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create(this).all().show();
    }

    private void onLiveHome(View view) {
        LiveDialog.create(this).action().show();
    }

    private void onVodHistory(View view) {
        HistoryDialog.create(this).type(type = 0).show();
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create(this).type(type = 1).show();
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(requireActivity());
    }

    private void onVersion(View view) {
        Updater.create().force().release().start(getActivity());
    }
    
    private void onAbout(View view) {
        AboutDialog.show(this);
    }

    private boolean onVersionDev(View view) {
        Updater.create().force().dev().start(getActivity());
        return true;
    }

    private void setIncognito(boolean checked) {
        Setting.putIncognito(checked);
    }

    private void setLiveTabVisible(boolean checked) {
        Setting.putLiveTabVisible(checked);
        // 发送刷新事件，通知主界面更新导航栏
        RefreshEvent.config();
        // 更新直播设置项的可见性
        setLiveSettingsVisibility();
    }

    private void setHistoryVisible(boolean checked) {
        Setting.putHistoryVisible(checked);
        // 发送刷新事件，通知首页更新历史记录显示
        RefreshEvent.history();
    }

    private void onOperation(View view) {
        com.fongmi.android.tv.ui.activity.SettingOperationActivity.start(requireActivity());
    }

    private void setSize(int index) {
        if (index == Setting.getSize()) return;
        Setting.putSize(index);
        RefreshEvent.size();
    }

    private void setTheme(int index) {
        if (index == Setting.getThemeMode()) return;
        Setting.putThemeMode(index);
        ThemeUtil.applyNightMode();
    }

    private void setAccent(int index) {
        if (index == Setting.getAccentColor()) return;
        Setting.putAccentColor(index);
        requireActivity().recreate();
    }

    private void setDoh(Doh doh) {
        Source.get().stop();
        OkHttp.get().setDoh(doh);
        Notify.progress(getActivity());
        Setting.putDoh(doh.toString());
        mBinding.settingsContent.setDoh(doh.getName());
        VodConfig.load(Config.vod(), getCallback(0));
    }

    private void onProxy(View view) {
        ProxyDialog.create(this).show();
    }

    @Override
    public void setProxy(String proxy) {
        Source.get().stop();
        Setting.putProxy(proxy);
        OkHttp.selector().clear();
        OkHttp.get().setProxy(proxy);
        Notify.progress(getActivity());
        mBinding.settingsContent.setProxy(getProxy(proxy));
        mBinding.settingsContent.setProxyEditor(proxy);
        VodConfig.load(Config.vod(), getCallback(0));
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onWebDAV(View view) {
        com.fongmi.android.tv.ui.dialog.WebDAVDialog.create(this).show();
    }

    private void onBackup(View view) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> AppDatabase.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore(View view) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> RestoreDialog.create().show(getActivity(), new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                Notify.progress(getActivity());
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        LiveConfig.get().init().load();
        VodConfig.get().init().load(getCallback(0));
    }

    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.CONFIG) {
            // Config refresh handling
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        setSourceText();
        setCacheText();
        setOtherText();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || requestCode != FileChooser.REQUEST_PICK_FILE) return;
        setConfig(Config.find("file:/" + FileChooser.getPathFromUri(getContext(), data.getData()).replace(Path.rootPath(), ""), type));
    }
}
