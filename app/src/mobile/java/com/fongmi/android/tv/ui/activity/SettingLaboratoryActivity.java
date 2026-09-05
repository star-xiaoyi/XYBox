package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.ActivitySettingLaboratoryBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class SettingLaboratoryActivity extends BaseActivity {

    private ActivitySettingLaboratoryBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingLaboratoryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingLaboratoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.header.setTitle(getString(R.string.setting_laboratory));
        mBinding.header.setBackdropView(mBinding.content);
        mBinding.header.setRenderingEnabled(true);
        mBinding.liquidGlassNavigationSwitch.setChecked(Setting.isLiquidGlassNavigation());
    }

    @Override
    protected void initEvent() {
        mBinding.header.setOnClickListener(v -> finish());
        mBinding.glassShowcase.setOnClickListener(v -> LiquidGlassShowcaseActivity.start(this));
        mBinding.liquidGlassNavigationSwitch.setOnClickListener(this::setLiquidGlassNavigation);
        mBinding.liquidGlassNavigation.setOnClickListener(v -> mBinding.liquidGlassNavigationSwitch.performClick());
    }

    private void setLiquidGlassNavigation(View view) {
        Setting.putLiquidGlassNavigation(mBinding.liquidGlassNavigationSwitch.isChecked());
    }
}
