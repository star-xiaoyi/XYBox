package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingOperationBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class SettingOperationActivity extends BaseActivity {

    private ActivitySettingOperationBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingOperationActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingOperationBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.header.setTitle(getString(R.string.setting_operation));
        mBinding.header.setBackdropView(mBinding.glassContent);
        mBinding.header.setRenderingEnabled(true);
        mBinding.glassContent.refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.header.setOnClickListener(v -> finish());
    }
}
