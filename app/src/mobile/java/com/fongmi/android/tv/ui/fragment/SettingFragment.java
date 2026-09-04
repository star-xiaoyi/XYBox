package com.fongmi.android.tv.ui.fragment;
import com.github.catvod.utils.Logger;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.BuildConfig;
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
import com.fongmi.android.tv.ui.custom.LiquidGlassNavigationView;
import com.fongmi.android.tv.ui.dialog.AboutDialog;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.ProxyDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.dialog.SyncSettingsDialog;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThemeUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.MaterialColors;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingFragment extends BaseFragment implements ConfigCallback, SiteCallback, LiveCallback, ProxyCallback {

    private FragmentSettingBinding mBinding;
    private String[] size;
    private int type;
    private boolean searchActive;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
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
        setSourceHintText(mBinding.vodUrl, VodConfig.getDesc(), R.string.source_hint_setting);
        setSourceHintText(mBinding.liveUrl, LiveConfig.getDesc(), R.string.source_hint_live);
        mBinding.versionText.setText(getString(R.string.setting_version) + " " + BuildConfig.VERSION_NAME);

        setOtherText();
        setCacheText();
        String[] quotes = getResources().getStringArray(R.array.motivational_quotes);
        int randomIndex = new java.util.Random().nextInt(quotes.length);
        mBinding.marquee.setText(quotes[randomIndex]);
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.proxyText.setText(getProxy(Setting.getProxy()));
        mBinding.incognitoSwitch.setChecked(Setting.isIncognito());
        mBinding.liveTabVisibleSwitch.setChecked(Setting.isLiveTabVisible());
        mBinding.historyVisibleSwitch.setChecked(Setting.isHistoryVisible());
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[Setting.getSize()]);
        mBinding.themeText.setText(getThemeNames()[Setting.getThemeMode()]);
        mBinding.accentText.setText(getAccentNames()[Setting.getAccentColor()]);
        mBinding.accentPreview.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(ThemeUtil.getAccentColorResource())));
        setLiveSettingsVisibility();
    }

    private String[] getThemeNames() {
        return new String[]{getString(R.string.setting_theme_system), getString(R.string.setting_theme_light), getString(R.string.setting_theme_dark)};
    }

    private String[] getAccentNames() {
        return new String[]{getString(R.string.setting_accent_yellow), getString(R.string.setting_accent_blue), getString(R.string.setting_accent_green), getString(R.string.setting_accent_purple)};
    }

    private void setLiveSettingsVisibility() {
        if (searchActive) {
            filterSettings(mBinding.searchInput.getText().toString());
            return;
        }
        boolean isLiveTabVisible = !Setting.isLiveTabVisible(); // 注意：这里取反，因为开关是"隐藏直播"
        mBinding.liveContainer.setVisibility(isLiveTabVisible ? View.VISIBLE : View.GONE);
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.syncSettings.setOnClickListener(this::onSyncSettings);
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.proxy.setOnClickListener(this::onProxy);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.webdav.setOnClickListener(this::onWebDAV);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.about.setOnClickListener(this::onAbout);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.version.setOnLongClickListener(this::onVersionDev);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.incognitoSwitch.setOnClickListener(this::setIncognito);
        mBinding.liveTabVisibleSwitch.setOnClickListener(this::setLiveTabVisible);
        mBinding.historyVisibleSwitch.setOnClickListener(this::setHistoryVisible);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.theme.setOnClickListener(this::setTheme);
        mBinding.accent.setOnClickListener(this::setAccent);
        mBinding.operation.setOnClickListener(this::onOperation);
        mBinding.laboratory.setOnClickListener(view -> com.fongmi.android.tv.ui.activity.SettingLaboratoryActivity.start(requireActivity()));
        mBinding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                filterSettings(text == null ? "" : text.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    public void toggleSearch() {
        if (mBinding == null) return;
        if (searchActive) closeSearch();
        else openSearch();
    }

    public boolean closeSearchIfActive() {
        if (!searchActive) return false;
        closeSearch();
        return true;
    }

    public boolean isSearchActive() {
        return searchActive;
    }

    private void openSearch() {
        searchActive = true;
        mBinding.normalHeader.setVisibility(View.GONE);
        mBinding.searchHeader.setVisibility(View.VISIBLE);
        mBinding.searchInput.requestFocus();
        getRoot().setGlassAction(LiquidGlassNavigationView.ACTION_CLOSE, true);
        InputMethodManager manager = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) mBinding.searchInput.post(() -> manager.showSoftInput(mBinding.searchInput, InputMethodManager.SHOW_IMPLICIT));
    }

    private void closeSearch() {
        searchActive = false;
        mBinding.searchInput.setText("");
        mBinding.searchInput.clearFocus();
        mBinding.searchHeader.setVisibility(View.GONE);
        mBinding.normalHeader.setVisibility(View.VISIBLE);
        getRoot().setGlassAction(LiquidGlassNavigationView.ACTION_SEARCH, true);
        InputMethodManager manager = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(mBinding.searchInput.getWindowToken(), 0);
    }

    private void filterSettings(String rawQuery) {
        String query = rawQuery.trim().toLowerCase(Locale.ROOT);
        boolean filtering = !query.isEmpty();
        View[][] groups = getSearchGroups();
        ViewGroup[] cards = {mBinding.sourceCard, mBinding.appearanceCard, mBinding.playbackCard, mBinding.networkCard, mBinding.storageCard};
        boolean anyMatch = false;

        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            boolean groupMatch = false;
            for (View row : groups[groupIndex]) {
                boolean visible = !filtering || matches(row, query);
                if (row == mBinding.live) visible &= !Setting.isLiveTabVisible();
                if (row == mBinding.live) mBinding.liveContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
                else row.setVisibility(visible ? View.VISIBLE : View.GONE);
                groupMatch |= visible;
            }
            cards[groupIndex].setVisibility(groupMatch ? View.VISIBLE : View.GONE);
            setDividersVisible(cards[groupIndex], !filtering);
            anyMatch |= groupMatch;
        }

        mBinding.sourceTip.setVisibility(filtering ? View.GONE : View.VISIBLE);
        mBinding.searchEmpty.setVisibility(filtering && !anyMatch ? View.VISIBLE : View.GONE);
    }

    private View[][] getSearchGroups() {
        return new View[][]{
                {mBinding.vod, mBinding.live},
                {mBinding.theme, mBinding.accent, mBinding.size, mBinding.historyVisible, mBinding.liveTabVisible, mBinding.laboratory},
                {mBinding.player, mBinding.operation, mBinding.incognito},
                {mBinding.webdav, mBinding.syncSettings, mBinding.doh, mBinding.proxy},
                {mBinding.cache, mBinding.backup, mBinding.restore, mBinding.version, mBinding.about}
        };
    }

    private boolean matches(View view, String query) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.toString().toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (matches(group.getChildAt(i), query)) return true;
            }
        }
        return false;
    }

    private void setDividersVisible(ViewGroup group, boolean visible) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getClass() == View.class && child.getId() == View.NO_ID) {
                child.setVisibility(visible ? View.VISIBLE : View.GONE);
            } else if (child instanceof ViewGroup && child != mBinding.live) {
                setDividersVisible((ViewGroup) child, visible);
            }
        }
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
                    if (mBinding != null && mBinding.vodUrl != null) {
                        mBinding.vodUrl.setText(config.getDesc());
                    }
                    break;
                case 1:
                    Notify.progress(getActivity());
                    LiveConfig.load(config, getCallback(1));
                    if (mBinding != null && mBinding.liveUrl != null) {
                        mBinding.liveUrl.setText(config.getDesc());
                    }
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
                        setSourceHintText(mBinding.vodUrl, VodConfig.getDesc(), R.string.source_hint_setting);
                        break;
                    case 1:
                        setSourceHintText(mBinding.liveUrl, LiveConfig.getDesc(), R.string.source_hint_live);
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
                setSourceHintText(mBinding.vodUrl, VodConfig.getDesc(), R.string.source_hint_setting);
                setSourceHintText(mBinding.liveUrl, LiveConfig.getDesc(), R.string.source_hint_live);
                        break;
            case 1:
                setCacheText();
                Notify.dismiss();
                RefreshEvent.config();
                setSourceHintText(mBinding.liveUrl, LiveConfig.getDesc(), R.string.source_hint_live);
                break;
            case 2:
                setCacheText();
                Notify.dismiss();
                        break;
        }
    }

    private void setSourceHintText(TextView textView, String desc, int hintStringRes) {
        if (TextUtils.isEmpty(desc)) {
            SpannableString spannable = new SpannableString(getString(hintStringRes));
            spannable.setSpan(new RelativeSizeSpan(0.8f), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            int color = MaterialColors.getColor(textView, com.google.android.material.R.attr.colorOnSurfaceVariant);
            spannable.setSpan(new ForegroundColorSpan(color), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(spannable);
        } else {
            textView.setText(desc);
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

    private void setIncognito(View view) {
        boolean isChecked = !Setting.isIncognito();
        Setting.putIncognito(isChecked);
        // 不需要再次调用 setChecked，因为点击已经触发了状态变化
    }

    private void setLiveTabVisible(View view) {
        boolean isChecked = !Setting.isLiveTabVisible();
        Setting.putLiveTabVisible(isChecked);
        // 发送刷新事件，通知主界面更新导航栏
        RefreshEvent.config();
        // 更新直播设置项的可见性
        setLiveSettingsVisibility();
        // 不需要再次调用 setChecked，因为点击已经触发了状态变化
    }

    private void setHistoryVisible(View view) {
        boolean isChecked = !Setting.isHistoryVisible();
        Setting.putHistoryVisible(isChecked);
        // 发送刷新事件，通知首页更新历史记录显示
        RefreshEvent.history();
        // 不需要再次调用 setChecked，因为点击已经触发了状态变化
    }

    private void onOperation(View view) {
        com.fongmi.android.tv.ui.activity.SettingOperationActivity.start(requireActivity());
    }

    private void setSize(View view) {
        new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.setting_size).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(size, Setting.getSize(), (dialog, which) -> {
            mBinding.sizeText.setText(size[which]);
            Setting.putSize(which);
            RefreshEvent.size();
            dialog.dismiss();
        }).show();
    }

    private void setTheme(View view) {
        String[] names = getThemeNames();
        new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.setting_theme).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(names, Setting.getThemeMode(), (dialog, which) -> {
            if (which != Setting.getThemeMode()) {
                Setting.putThemeMode(which);
                mBinding.themeText.setText(names[which]);
                ThemeUtil.applyNightMode();
            }
            dialog.dismiss();
        }).show();
    }

    private void setAccent(View view) {
        String[] names = getAccentNames();
        new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.setting_accent).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(names, Setting.getAccentColor(), (dialog, which) -> {
            if (which != Setting.getAccentColor()) {
                Setting.putAccentColor(which);
                dialog.dismiss();
                requireActivity().recreate();
            } else {
                dialog.dismiss();
            }
        }).show();
    }

    private void setDoh(View view) {
        new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.setting_doh).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDohList(), getDohIndex(), (dialog, which) -> {
            setDoh(VodConfig.get().getDoh().get(which));
            dialog.dismiss();
        }).show();
    }

    private void setDoh(Doh doh) {
        Source.get().stop();
        OkHttp.get().setDoh(doh);
        Notify.progress(getActivity());
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
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
        mBinding.proxyText.setText(getProxy(proxy));
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
        setSourceHintText(mBinding.vodUrl, VodConfig.getDesc(), R.string.source_hint_setting);
        setSourceHintText(mBinding.liveUrl, LiveConfig.getDesc(), R.string.source_hint_live);
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
