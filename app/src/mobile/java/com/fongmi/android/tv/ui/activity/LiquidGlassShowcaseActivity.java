package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityLiquidGlassShowcaseBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class LiquidGlassShowcaseActivity extends BaseActivity {

    private ActivityLiquidGlassShowcaseBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, LiquidGlassShowcaseActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLiquidGlassShowcaseBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.header.setTitle(getString(R.string.glass_showcase_title));
    }

    @Override
    protected void initEvent() {
        mBinding.header.setOnClickListener(v -> finish());
    }
}
