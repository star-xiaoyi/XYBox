package com.fongmi.android.tv.ui.activity;
import com.github.catvod.utils.Logger;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.ActivityPrivacyAgreementBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class PrivacyAgreementActivity extends BaseActivity {

    private ActivityPrivacyAgreementBinding mBinding;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityPrivacyAgreementBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        // 隐私协议页面初始化完成
    }

    @Override
    protected void initEvent() {
        if (mBinding != null) {
            if (mBinding.agreeButton != null) {
                mBinding.agreeButton.setOnClickListener(this::onAgree);
            }
            if (mBinding.disagreeButton != null) {
                mBinding.disagreeButton.setOnClickListener(this::onDisagree);
            }
        }
    }

    private void onAgree(View view) {
        // 用户同意协议
        Setting.setPrivacyAgreed(true);
        
        // 创建通知渠道（此时才请求通知权限）
        com.fongmi.android.tv.utils.Notify.createChannel();
        
        // 跳转到主界面，清除任务栈避免用户通过任务管理器回到协议页面
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void onDisagree(View view) {
        // 用户不同意协议，退出应用
        try {
            // 清除隐私协议状态（可选，确保下次启动重新询问）
            Setting.setPrivacyAgreed(false);
            
            // 优雅地退出应用
            finishAffinity();
            
            // 延迟退出，让 Activity 完成销毁
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                System.exit(0);
            }, 100);
        } catch (Exception e) {
            Logger.e("Error", e);
            // 备选退出方案
            System.exit(0);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 禁用返回键，用户必须做出选择
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onDisagree(null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        // 清理 binding 引用
        mBinding = null;
        super.onDestroy();
    }
}

