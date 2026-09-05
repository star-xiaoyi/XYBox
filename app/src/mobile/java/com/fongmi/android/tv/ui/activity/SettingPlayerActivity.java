package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.ActivitySettingPlayerBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SettingsPlayerGlassContentView;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

public class SettingPlayerActivity extends BaseActivity {

    private ActivitySettingPlayerBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPlayerActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPlayerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.header.setTitle(getString(R.string.setting_player));
        mBinding.header.setBackdropView(mBinding.glassContent);
        mBinding.header.setRenderingEnabled(true);
        mBinding.glassContent.refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.header.setOnClickListener(v -> finish());
        mBinding.glassContent.setOnNetworkSettingListener(new SettingsPlayerGlassContentView.OnNetworkSettingListener() {
            @Override
            public void onDohSelected(int index) {
                if (index < 0 || index >= VodConfig.get().getDoh().size()) return;
                Doh doh = VodConfig.get().getDoh().get(index);
                Source.get().stop();
                OkHttp.get().setDoh(doh);
                Setting.putDoh(doh.toString());
                reloadVodConfig();
            }

            @Override
            public void onProxySaved(String proxy) {
                Source.get().stop();
                Setting.putProxy(proxy);
                OkHttp.selector().clear();
                OkHttp.get().setProxy(proxy);
                reloadVodConfig();
            }
        });
    }

    private void reloadVodConfig() {
        Notify.progress(this);
        VodConfig.load(Config.vod(), new Callback() {
            @Override
            public void success(String result) {
                Notify.show(result);
                mBinding.glassContent.refresh();
            }

            @Override
            public void success() {
                Notify.dismiss();
                mBinding.glassContent.refresh();
            }

            @Override
            public void error() {
                Notify.dismiss();
            }
        });
    }
}
